/*
 *  RankingQueryRunnerImpl.java
 *
 *  Copyright (c) 1995-2010, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 16 Nov 2011
 *
 *  $Id$
 */
package gate.mimir.search;

import gate.mimir.DocumentMetadataHelper;
import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.score.MimirScorer;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;

import org.apache.log4j.Logger;

/**
 * A QueryRunner implementation that can perform ranking.
 * This query runner has two modes of functioning: ranking and non-ranking, 
 * depending on whether a {@link MimirScorer} is provided  during construction
 * or not.
 * All documents are referred to using their rank (i.e. position in the list of 
 * results). When working in non-ranking mode, ranking order is the same as 
 * document ID order.
 */
public class RankingQueryRunnerImpl implements QueryRunner {
  
  
  /**
   * Constant used as a flag to mark then of a list of tasks.
   */
  private static final Runnable NO_MORE_TASKS = new Runnable(){ 
    public void run() {}
  };
  
  /**
   * The background thread implementation: simply collects {@link Runnable}s 
   * from the {@link RankingQueryRunnerImpl#backgroundTasks} queue and runs them. 
   */
  protected class BackgroundRunner implements Runnable {
    @Override
    public void run() {
      try {
        while(true) {
          Runnable job = backgroundTasks.take();
          if(job == NO_MORE_TASKS) break;
          else  job.run();
        }
      } catch(InterruptedException e) {
        Thread.currentThread().interrupt();
        e.printStackTrace();
      }
    }
  }
   
  
  /**
   * Collects the document hits (i.e. {@link Binding}s) for the documents 
   * between the two provided ranks (indexes in the {@link #documentsOrder} 
   * list. If ranking is not being performed ( {@link #documentsOrder} is
   * <code>null</null>, then the indexes are used against the 
   * {@link #documentIds} list.
   * 
   * This is the only actor that writes to the {@link #documentHits} list.
   */  
  protected class HitsCollector implements Runnable {
    /**
     * The starting rank
     */
    int start;
    
    /**
     * The ending rank
     */
    int end;
    
    public HitsCollector(int rangeStart, int rangeEnd) {
      this.start = rangeStart;
      this.end = rangeEnd;
    }
    
    @Override
    public void run() {
      int[] documentIndexes = null;
      if(ranking) {
        // we're ranking -> first calculate the range of documents in ID order
        documentIndexes = new int[end - start];
        for(int i = start; i < end; i++) {
          documentIndexes[i - start] = documentsOrder.getInt(i);
        }
        Arrays.sort(documentIndexes);
      }
      
      try {
        // see if we can get at the first document
        int docIndex = (documentIndexes != null ? documentIndexes[0] : start);
        int docId = documentIds.getInt(docIndex);
        if(queryExecutor.getLatestDocument() < 0 ||
           queryExecutor.getLatestDocument() >= docId) {
          // we need to 'scroll back' the executor: get a new executor
          QueryExecutor oldExecutor = queryExecutor;
          queryExecutor = queryExecutor.getQueryNode().getQueryExecutor(
                  queryEngine);
          oldExecutor.close();
        }
        for(int i = start; i < end; i++) {
          docIndex = (documentIndexes != null ? documentIndexes[i - start] : i);
          docId = documentIds.getInt(docIndex);
          int newDoc = queryExecutor.nextDocument(docId - 1);
          // sanity check
          if(newDoc == docId) {
            List<Binding> hits = new ObjectArrayList<Binding>();
            Binding aHit = queryExecutor.nextHit();
            while(aHit != null) {
              hits.add(aHit);
              aHit = queryExecutor.nextHit();
            }
            documentHits.set(docIndex, hits);
          } else {
            // we got the wrong document ID
            logger.error("Unexpected document ID returned by executor " +
            		"(got " + newDoc + " while expecting " + docId + "!");
          }
        }
      } catch(IOException e) {
        logger.error("Exception while restarting the query executor.", e);
        try {
          close();
        } catch(IOException e1) {
          logger.error("Exception while closing the query runner.", e1);
        }
      }
    }
  }
  
  
  /**
   * The first action started when a new {@link RankingQueryRunnerImpl} is 
   * created. It performs the following actions:
   * <ul>
   *   <li>collects all document IDs in 
   *   {@link RankingQueryRunnerImpl#documentIds}</li>
   *   <li>if ranking enabled
   *     <ul>
   *     <li>it collects all document scores
   *     </ul>
   *   </li>  
   *   <li>if ranking not enabled
   *     <ul>
   *       <li>it collects the document hits for the first 
   *       block of documents</li>
   *     </ul>
   *   </li>
   *   <li>If ranking enabled, after all document IDs are obtained, it starts 
   *   the work for ranking the first block of documents (which, upon 
   *   completion, will also start a background job to collect all the hits for 
   *   that block).</li>  
   * </ul>
   */
  protected class DocIdsCollector implements Runnable {
    @Override
    public void run() {
      try{
        // collect all documents and their scores
        if(ranking) scorer.wrap(queryExecutor);
        int docId = ranking ? scorer.nextDocument(-1) : queryExecutor.nextDocument(-1);
        while(docId >= 0) {
          // enlarge the hits list
          if(ranking){
            documentScores.add(scorer.score());
            documentHits.add(null);
          } else {
            // not scoring: also collect the hits for the first block of documents
            if(docId < docBlockSize) {
              ObjectList<Binding> hits = new ObjectArrayList<Binding>();
              Binding hit = queryExecutor.nextHit();
              while(hit != null) {
                hits.add(hit);
                hit = queryExecutor.nextHit();
              }
              documentHits.add(hits);
            } else {
              documentHits.add(null);
            }
          }
          // and store the new doc ID
          documentIds.add(docId);
          docId = ranking ? scorer.nextDocument(-1) : queryExecutor.nextDocument(-1);
        }
        allDocIdsCollected = true;
        if(ranking) {
          // now rank the first batch of documents
          // this will also start a second background job to collect the hits
          rankDocuments(docBlockSize -1);
        }
      }catch (IOException e) {
        logger.error("Exception while collecting document IDs", e);
        try {
          close();
        } catch(IOException e1) {
          logger.error("Exception while closing, after exception.", e1);
        }
      }
    }
  }
  
  /**
   * Shared logger instance.
   */
  protected static Logger logger =  Logger.getLogger(RankingQueryRunnerImpl.class);
  
  /**
   * The {@link QueryExecutor} for the query being run.
   */
  protected QueryExecutor queryExecutor;
  
  /**
   * The QueryEngine we run inside.
   */
  protected QueryEngine queryEngine;
  
  /**
   * The {@link MimirScorer} to be used for ranking documents.
   */
  protected MimirScorer scorer;
  
  /**
   * Flag set to <code>true</code> when ranking is being performed, or 
   * <code>false</code> otherwise.
   */
  final boolean ranking;

  /**
   * The number of documents to be ranked (of have their hits collected) as a 
   * block.
   */
  protected int docBlockSize;
  
  /**
   * The document IDs for the documents found to contain hits. This list is
   * sorted in ascending documentID order.
   */
  protected IntList documentIds;
  
  /**
   * If scoring is enabled ({@link #scorer} is not <code>null</code>), this list
   * contains the scores for the documents found to contain hits. This list is 
   * aligned to {@link #documentIds}.   
   */
  protected DoubleArrayList documentScores;
  
  /**
   * The sets of hits for each returned document. This data structure is lazily 
   * built, so some elements may be null. This list is aligned to 
   * {@link #documentIds}.   
   */
  protected ObjectList<List<Binding>> documentHits;

  /**
   * The order the documents should be returned in (elements in this list are 
   * indexes in {@link #documentIds}).
   */
  protected IntList documentsOrder;
  
  /**
   * Data structure holding references to {@link Future}s that are currently 
   * working (or have worked) on collecting hits for a range of document 
   * indexes.
   */
  protected SortedMap<int[], Future<?>> hitCollectors;
  
  /**
   * The background thread used for collecting hits.
   */
  protected Thread runningThread;
  
  /**
   * A queue with tasks to be executed by the background thread. 
   */
  protected BlockingQueue<Runnable> backgroundTasks;
  
  /**
   * Flag used to mark that all results documents have been counted.
   */
  protected volatile boolean allDocIdsCollected = false;
  
  /**
   * Creates a query runner in ranking mode.
   * @param qNode the {@link QueryNode} for the query being executed.
   * @param scorer the {@link MimirScorer} to use for ranking.
   * @param qEngine the {@link QueryEngine} used for executing the queries.
   * @throws IOException
   */
  public RankingQueryRunnerImpl(QueryExecutor executor, MimirScorer scorer) throws IOException {
    this.queryExecutor = executor;
    this.scorer = scorer;
    ranking = scorer != null;
    queryEngine = queryExecutor.getQueryEngine();
    docBlockSize = queryEngine.getDocumentBlockSize();
    documentIds = new IntArrayList();
    documentHits = new ObjectArrayList<List<Binding>>();
    if(scorer != null) {
      documentScores = new DoubleArrayList();
      documentsOrder = new IntArrayList(docBlockSize);
    }
    hitCollectors = new Object2ObjectAVLTreeMap<int[], Future<?>>(
        new Comparator<int[]>(){
          @Override
          public int compare(int[] o1, int[] o2) { return o1[0] - o2[0]; }
        });
    // start the background thread
    backgroundTasks = new LinkedBlockingQueue<Runnable>();
    Runnable backgroundRunner = new BackgroundRunner();
    //get a thread from the executor, if one exists
    if(queryEngine.getExecutor() != null){
      try {
        queryEngine.getExecutor().execute(backgroundRunner);
      } catch(RejectedExecutionException e) {
        logger.warn("Could not allocate a new background thread", e);
        throw new RejectedExecutionException(
          "System overloaded, please try again later."); 
      }
    }else{
      Thread theThread = new Thread(backgroundRunner, getClass().getName());
      theThread.setDaemon(true);
      theThread.start();
    }

    // queue a job for collecting all document ids
    try {
      if(!ranking) {
        // if not ranking, the doc IDs collector will all collect the
        // hits for the first docBlockSize number of documents
        FutureTask<Object> future = new FutureTask<Object>(new DocIdsCollector(), null);
        synchronized(hitCollectors) {
          hitCollectors.put(new int[]{0, docBlockSize}, future);
        }
        backgroundTasks.put(future);
      } else {
        backgroundTasks.put(new DocIdsCollector());
      }
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Could not queue a background task.", e);
    }
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCount()
   */
  @Override
  public int getDocumentsCount() {
    if(allDocIdsCollected) return documentIds.size();
    else return -1;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getCurrentDocumentsCount()
   */
  @Override
  public int getDocumentsCurrentCount() {
    return documentIds.size();
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentID(int)
   */
  @Override
  public int getDocumentID(int rank) throws IndexOutOfBoundsException, IOException {
    return documentIds.getInt(getDocumentIndex(rank));
  }
  
  @Override
  public double getDocumentScore(int rank) throws IndexOutOfBoundsException, IOException {
    return (documentScores != null) ? 
        documentScores.getDouble(getDocumentIndex(rank)) : 
        DEFAULT_SCORE;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentHits(int)
   */
  @Override
  public List<Binding> getDocumentHits(int rank) throws IndexOutOfBoundsException, IOException {
    int documentIndex = getDocumentIndex(rank);
    List<Binding> hits = documentHits.get(documentIndex);
    if(hits == null) {
      // hits not collected yet
      try {
        // find the Future working on it, or start a new one, 
        // then wait for it to complete
        collectHits(new int[]{rank, rank + 1}).get();
        hits = documentHits.get(documentIndex);
      } catch(Exception e) {
        logger.error("Exception while waiting for hits collection", e);
        throw new RuntimeException(
          "Exception while waiting for hits collection", e); 
      }
    }
    return hits;
  }
  
  /**
   * Given a document rank, return its index in the {@link #documentIds} list.
   * If ranking is not being performed, then the rank is interpreted as an index 
   * against the {@link #documentIds} list and is simply returned. 
   * @param rank
   * @return
   * @throws IOException, IndexOutOfBoundsException 
   */
  protected int getDocumentIndex(int rank) throws IOException, 
      IndexOutOfBoundsException {
    int maxIndex = documentIds.size();
    if(rank >= maxIndex) throw new IndexOutOfBoundsException(
      "Document index too large (" + rank + " > " + maxIndex + ".");
    if(documentsOrder != null) {
      // we're in ranking mode
      if(rank >= documentsOrder.size()) {
        // document exists, but has not been ranked yet
        rankDocuments(rank);
      }
      return documentsOrder.getInt(rank);
    } else {
      return rank;
    }
  }
  
  /**
   * Ranks some more documents (i.e. adds more entries to the 
   * {@link #documentsOrder} list, making sure that the document at provided 
   * rank is included (if such a document exists). If the provided rank is 
   * larger than the number of result documents, then all documents will be
   * ranked before this method returns. 
   * This is the only method that writes to the {@link #documentsOrder} list.
   * This method is executed synchronously in the client thread.
   *  
   * @param rank
   * @throws IOException 
   */
  protected void rankDocuments(int rank) throws IOException {
    if(rank < documentsOrder.size()) return;
    synchronized(documentsOrder) {
      // rank some documents
      int rankRangeStart = documentsOrder.size();
      // right boundary is exclusive
      int rankRangeEnd = rank + 1;
      if((rankRangeEnd - rankRangeStart) < (docBlockSize)) {
        // extend the size of the chunk of documents to be ranked
        rankRangeEnd = rankRangeStart + docBlockSize; 
      }
      // the document with the minimum score already ranked.
      int smallestOldScoreDocId = rankRangeStart > 0 ? 
        documentIds.getInt(documentsOrder.getInt(rankRangeStart -1))
        : -1;
      // the score for the document above, which is a the upper limit for new scores
      double smallestOldScore = rankRangeStart > 0 ? 
          documentScores.getDouble(documentsOrder.getInt(rankRangeStart -1))
          : Double.POSITIVE_INFINITY;
      // now collect some more documents
      for(int i = 0; i < documentIds.size(); i++) {
        int documentId = documentIds.getInt(i);
        double documentScore = documentScores.getDouble(i);
        // the index for the document with the smallest score, 
        // from the new ones being ranked 
        int smallestDocIndex = rankRangeStart < documentsOrder.size() ?
            documentsOrder.getInt(rankRangeStart) : -1;
        // the smallest score that's been seen in this new round 
        double smallestNewScore = smallestDocIndex == -1 ? Double.NEGATIVE_INFINITY : 
            documentScores.getDouble(smallestDocIndex);
        // we care about this new document if:
        // - we haven't collected enough documents yet, or
        // - it has a better score than the smallest score so far, but a 
        // smaller score than the maximum permitted score (i.e. it has not 
        // already been ranked)., or
        // - it's a new document (i.e. with an ID strictly larger) with the same 
        // score as the largest permitted score
        if(documentsOrder.size() < rankRangeEnd
           || 
           (documentScore > smallestNewScore && documentScore < smallestOldScore) 
           ||
           (documentScore == smallestOldScore && documentId > smallestOldScoreDocId)
           ) {
          // find the rank for the new doc in the documentsOrder list, and insert
          documentsOrder.add(findRank(documentScore, rankRangeStart, 
              documentsOrder.size()), i);
          // if we have too many documents, drop the lowest scoring one
          if(documentsOrder.size() > rankRangeEnd) {
            documentsOrder.removeInt(documentsOrder.size() - 1);
          }          
        }
      }
      // start collecting the hits for the newly ranked documents (in a new thread)
      if(documentsOrder.size() > rankRangeStart){
        collectHits(new int[] {rankRangeStart, documentsOrder.size()});
      }
    }
  }
  
  /**
   * Given a document score, finds the correct insertion point into the 
   * {@link #documentsOrder} list, within a given range of ranks.
   * This method performs binary search followed by a linear scan so that the 
   * returned insertion point is the largest correct one (i.e. later documents 
   * with the same score get sorted after earlier ones, thus keeping the sorting
   * stable).
   *      
   * @param documentScore the score for the new document.
   * @param start the start of the search range within {@link #documentsOrder} 
   * @param end the end of the search range within {@link #documentsOrder} 
   * @return the largest correct insertion point
   */
  protected int findRank(double documentScore, int start, int end) {
    // standard binary search
    double midVal;
    end--;
    while (start <= end) {
     int mid = (start + end) >>> 1;
     midVal = documentScores.getDouble(documentsOrder.getInt(mid));
     // note that the documentOrder list is in decreasing score order!
     if (midVal > documentScore) start = mid + 1;
     else if (midVal < documentScore) end = mid - 1;
     else {
       // we found a doc with exactly the same score: scan to the right
       while(documentsOrder.size() < mid && 
             documentScores.getDouble(documentsOrder.getInt(mid)) == 
           documentScore){
         mid++;
       }
       return mid;
     }
    }
    return start;
  }
  
  /**
   * Makes sure all the documents in the specified range are queued for hit 
   * collection. 
   * @param interval the interval specified by 2 document ranks. The interval is
   * defined as the elements in {@link #documentsOrder} between ranks 
   * interval[0] and (interval[1]-1) inclusive. 
   * @return the future that has been queued for collecting the hits.
   */
  protected Future<?> collectHits(int[] interval) {
    // expand the interval to block size (or size of documentsOrder)
    if(interval[1] - interval[0] < docBlockSize) {
      final int expansion = docBlockSize - (interval[1] - interval[0]);
      // expand up to (expansion / 2) to the left
      interval[0] = Math.max(0, interval[0] - (expansion / 2));
      // expand to the right
      int upperBound = documentsOrder != null ? 
          documentsOrder.size() : documentIds.size();
      interval[1] = Math.min(upperBound, interval[0] + docBlockSize);
    }
    HitsCollector hitsCollector = null;
    synchronized(hitCollectors) {
      SortedMap<int[], Future<?>> headMap = hitCollectors.headMap(interval); 
      int[] previousInterval = headMap.isEmpty() ? new int[]{0, 0} : 
          headMap.lastKey();
      if(previousInterval[1] >= interval[1]) {
        // we're part of previous interval
        return hitCollectors.get(previousInterval);
      } else {
        // calculate an appropriate interval to collect hits for
        SortedMap<int[], Future<?>> tailMap = hitCollectors.tailMap(
          new int[]{interval[1], interval[1]});
        int[] followingInterval = tailMap.isEmpty() ? 
            new int[]{interval[1], interval[1]} : tailMap.firstKey();
        int start = Math.max(previousInterval[1] - 1, interval[0]);
        int end = Math.min(followingInterval[0], interval[1]);
        hitsCollector = new HitsCollector(start, end);
        FutureTask<?> future = new FutureTask<Object>(hitsCollector, null);
        hitCollectors.put(new int[]{start, end}, future);
        try {
          backgroundTasks.put(future);
        } catch(InterruptedException e) {
          logger.error("Error while queuing background work", e);
          throw new RuntimeException("Error while queuing background work", e);
        }
        return future;
      }
    }
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentText(int, int, int)
   */
  @Override
  public String[][] getDocumentText(int rank, int termPosition, int length) 
          throws IndexException, IndexOutOfBoundsException, IOException {
    return queryEngine.getText(getDocumentID(rank), termPosition, length);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentURI(int)
   */
  @Override
  public String getDocumentURI(int rank) throws IndexException, 
      IndexOutOfBoundsException, IOException {
    return queryEngine.getDocumentURI(getDocumentID(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentTitle(int)
   */
  @Override
  public String getDocumentTitle(int rank) throws IndexException, 
      IndexOutOfBoundsException, IOException {
    return queryEngine.getDocumentTitle(getDocumentID(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataField(int, java.lang.String)
   */
  @Override
  public Serializable getDocumentMetadataField(int rank, String fieldName)
      throws IndexException, IndexOutOfBoundsException, IOException {
    return queryEngine.getDocumentMetadataField(getDocumentID(rank), fieldName);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataFields(int, java.util.Set)
   */
  @Override
  public Map<String, Serializable> getDocumentMetadataFields(int rank,
      Set<String> fieldNames) throws IndexException, IndexOutOfBoundsException, 
      IOException {
    Map<String, Serializable> res = new HashMap<String, Serializable>();
    int docId = getDocumentID(rank);
    for(String fieldName : fieldNames) {
      Serializable value = getDocumentMetadataField(docId, fieldName);
      if(value != null) res.put(fieldName, value);
    }
    return res;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#renderDocument(int, java.lang.Appendable)
   */
  @Override
  public void renderDocument(int rank, Appendable out) throws IOException, 
      IndexException {
        queryEngine.renderDocument(getDocumentID(rank), 
                getDocumentHits(rank), out);
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#close()
   */
  @Override
  public void close() throws IOException {
    if(queryEngine != null) queryEngine.releaseQueryRunner(this);
    if(queryExecutor != null) queryExecutor.close();
    scorer = null;
    try {
      // stop the background tasks runnable, 
      // which will return the thread to the pool
      backgroundTasks.put(NO_MORE_TASKS);
    } catch(InterruptedException e) {
      // ignore
    }
  }
}