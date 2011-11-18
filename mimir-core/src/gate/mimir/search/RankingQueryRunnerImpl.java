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
 *  Valentin Tablan, 16 Nov 201119/Jan/00
 *
 *  $Id$
 */
package gate.mimir.search;

import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.score.MimirScorer;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;

/**
 * A QueryRunner implementation that can perform ranking.
 * This query runner has two modes of functioning: ranking and non-ranking, 
 * depending on whether a {@link MimirScorer} is provided  during construction
 * or not.  
 */
public class RankingQueryRunnerImpl {
  
  private static final Runnable NO_MORE_JOBS = new Runnable(){ 
    public void run() {}
  };
  
  protected class BackgroundRunner implements Runnable {
    @Override
    public void run() {
      try {
        while(true) {
          Runnable job = backgroundTasks.take();
          if(job == NO_MORE_JOBS) break;
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
    int start;
    int end;
    
    public HitsCollector(int rangeStart, int rangeEnd) {
      this.start = rangeStart;
      this.end = rangeEnd;
    }
    
    @Override
    public void run() {
      // TODO Auto-generated method stub
      if(documentScores != null) {
        // we're ranking -> first calculate the range of documents in ID order
        
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
        final boolean scoring = scorer != null;
        if(scoring) scorer.wrap(queryExecutor);
        int docId = scoring ? scorer.nextDocument(-1) : queryExecutor.nextDocument(-1);
        if(!scoring) { // then also collect some hits
          synchronized(hitCollectors) {
           hitCollectors.put(new int[]{0, docBlockSize}, 
             new FutureTask<Object>(this, null));
          }
        }
        while(docId >= 0) {
          documentIds.add(docId);
          if(scoring){
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
          docId = scoring ? scorer.nextDocument(-1) : queryExecutor.nextDocument(-1);
        }
        allDocIdsCollected = true;
        if(scoring) {
          // now rank the first batch of documents
          // this will also start a second background job to collect the hits
          rankDocuments(queryExecutor.getQueryEngine().getRankingDocCount() -1);
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
  
  protected Logger logger =  Logger.getLogger(RankingQueryRunnerImpl.class);
  
  /**
   * The {@link QueryExecutor} for the query being run.
   */
  protected QueryExecutor queryExecutor;
  
  /**
   * The {@link MimirScorer} to be used for ranking documents.
   */
  protected MimirScorer scorer;

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
  protected DoubleList documentScores;
  
  /**
   * The sets of hits for each returned document. This data structure is lazily 
   * built, so some elements may be null. 
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
    docBlockSize = queryExecutor.getQueryEngine().getRankingDocCount();
    documentIds = new IntArrayList();
    documentHits = new ObjectArrayList<List<Binding>>();
    if(scorer != null) {
      documentScores = new DoubleArrayList();
      documentsOrder = new IntArrayList(
        queryExecutor.getQueryEngine().getRankingDocCount());
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
    if(queryExecutor.getQueryEngine().getExecutor() != null){
      queryExecutor.getQueryEngine().getExecutor().execute(backgroundRunner);
    }else{
      new Thread(backgroundRunner, getClass().getName()).start();
    }

    // queue a job for collecting all document ids
    try {
      backgroundTasks.put(new DocIdsCollector());
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Could not queue a background task.", e);
    }
  }
  
  /**
   * Gets the number of result documents. If the search has not yet completed, 
   * then -1 is returned.
   * @return
   */
  public int getDocumentsCount() {
    if(allDocIdsCollected) return documentIds.size();
    else return -1;
  }

  /**
   * Gets the number of documents found to contain hits so far. After the search
   * completes, the result returned by this call is identical to that of 
   * {@link #getDocumentsCount()}. 
   * @return
   */
  public int getCurrentDocumentsCount() {
    return documentIds.size();
  }
  
  /**
   * Gets the ID of a result document.
   * @param rank the index of the desired document in the list of documents. 
   * This should be a value between 0 and {@link #getDocumentsCount()} -1.
   *  
   * If the requested document position has not yet been ranked (i.e. we know 
   * there is a document at that position, but we don't yet know which one) then 
   * the necessary ranking is performed before this method returns. 
   *
   * @return an int value, representing the ID of the requested document.
   * @throws IndexOutOfBoundsException is the index provided is less than zero, 
   * or greater than {@link #getDocumentsCount()} -1.
   * @throws IOException 
   */
  public int getDocumentID(int rank) throws IndexOutOfBoundsException, IOException {
    return documentIds.getInt(getDocumentIndex(rank));
  }
  
  /**
   * Retrieves the hits withing a given result document.
   * @param rank the index of the desired document in the list of documents.
   * This should be a value between 0 and {@link #getDocumentsCount()} -1.
   * 
   * This method call waits until the requested data is available before 
   * returning (document hits are being collected by a background thread).
   * 
   * @return
   * @throws IOException 
   * @throws IndexOutOfBoundsException 
   */
  public List<Binding> getDocumentHits(int rank) throws IndexOutOfBoundsException, IOException {
    int documentIndex = getDocumentIndex(rank);
    List<Binding> hits = documentHits.get(documentIndex);
    if(hits == null) {
      // hits not collected yet
      try {
        // find the Future working on it, or start a new one, 
        // then wait for it to complete
        collectHits(new int[]{documentIndex, documentIndex}).get();
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
   * @param rank
   * @return
   * @throws IOException, IndexOutOfBoundsException 
   */
  protected int getDocumentIndex(int rank) throws IOException, IndexOutOfBoundsException {
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
   * index is included.
   * This is the only method that writes to the {@link #documentsOrder} list.
   * This method is executed synchronously in the client thread.
   *  
   * @param index
   * @throws IOException 
   */
  protected void rankDocuments(int index) throws IOException {
    synchronized(documentsOrder) {
      // rank some documents
      int rankRangeStart = documentsOrder.size();
      int rankRangeEnd = index;
      if(rankRangeEnd - rankRangeStart < 
          queryExecutor.getQueryEngine().getRankingDocCount()) {
        // extend the size of the chunk of documents to be ranked
        rankRangeEnd = rankRangeStart + 
            queryExecutor.getQueryEngine().getRankingDocCount(); 
      }
      int documentsOrderWriteIndex = rankRangeStart;
      
      // the document with the minimum score already ranked.
      int smallestOldScoreDocId = rankRangeStart > 0 ? 
        documentIds.getInt(documentsOrder.getInt(rankRangeStart -1))
        : -1;
      // the score for the document above, which is a the upper limit for new scores
      double smallestOldScore = rankRangeStart > 0 ? 
          documentScores.getDouble(documentsOrder.getInt(rankRangeStart -1))
          : -1;
      // now collect some more documents
      for(int i = 0; i < documentIds.size(); i++) {
        int documentId = documentIds.getInt(i);
        double documentScore = documentScores.getDouble(i);
        // the index for the document with the smallest score, 
        // from the new ones being ranked 
        int smallestDocIndex = rankRangeStart < documentsOrder.size() ?
            documentsOrder.getInt(rankRangeStart) : -1;
        // the smallest score that's been seen in this new round 
        double smallestNewScore = smallestDocIndex == -1 ? 0.0 : 
            documentScores.getDouble(smallestDocIndex);
        // we care about this new document if:
        // - we haven't collected enough documents yet, or
        // - it has a better score than the smallest score so far, but a 
        // smaller score than the maximum permitted score (i.e. it has not 
        // already been ranked)., or
        // - it's a new document with the same score as the largest permitted score
        if(documentsOrderWriteIndex < rankRangeEnd 
           || 
           (documentScore > smallestNewScore && 
               (smallestOldScore < 0 || documentScore < smallestOldScore)) 
           ||
           documentScore == smallestOldScore && documentId != smallestOldScoreDocId) {
          if(documentsOrderWriteIndex == rankRangeEnd) {
            // we need to remove the  newly ranked document 
            // with the smallest score
            documentsOrderWriteIndex--;
            documentsOrder.removeInt(documentsOrderWriteIndex);
          }
          // find the rank for the new doc
          int rank = rankRangeStart;
          while(rank < documentsOrder.size() && 
                documentScore < documentScores.getDouble(documentsOrder.getInt(rank))){
            rank++;
          }
          documentsOrder.add(rank, i);
          documentsOrderWriteIndex++;
        }
      }
      // start collecting the hits for the newly ranked documents (in a new thread)
      if(documentsOrderWriteIndex > rankRangeStart){
        collectHits(new int[] {rankRangeStart, documentsOrderWriteIndex});
      }
    }
  }
  
  /**
   * Makes sure all the documents in the specified range are queued for hit 
   * collection. 
   * @param interval the interval specified by 2 document ranks
   * @return the future that has been queued for collecting the hits.
   */
  protected Future collectHits(int[] interval) {
    // expand the interval to block size
    if(interval[1] - interval[0] < docBlockSize) {
      interval[0] -= docBlockSize / 2;
      interval[1] += docBlockSize / 2;
    }
    HitsCollector hitsCollector = null;
    Future<?> future;
    synchronized(hitCollectors) {
      SortedMap<int[], Future<?>> headMap = hitCollectors.headMap(interval); 
      int[] previousInterval = headMap.isEmpty() ? new int[]{0,0} : 
          headMap.lastKey();
      if(previousInterval[1] >= interval[1]) {
        // we're part of previous interval
        future = hitCollectors.get(previousInterval);
      } else {
        // calculate an appropriate interval to collect hits for
        SortedMap<int[], Future<?>> tailMap = hitCollectors.tailMap(interval);
        int[] followingInterval = tailMap.isEmpty() ? 
          new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE} : tailMap.firstKey();
        int start = Math.max(previousInterval[1], interval[0]);
        int end = Math.min(followingInterval[0], interval[1]);
        hitsCollector = new HitsCollector(start, end);
        future = new FutureTask(hitsCollector, null);
        hitCollectors.put(new int[]{start, end}, future);
      }
    }
    if(hitsCollector != null) {
      try {
        backgroundTasks.put(hitsCollector);
      } catch(InterruptedException e) {
        logger.error("Error while queuing background work", e);
        throw new RuntimeException("Error while queuing background work", e);
      }
    }
    return future;
  }
  
  public void close() throws IOException {
    queryExecutor.close();
    scorer = null;
    try {
      backgroundTasks.put(NO_MORE_JOBS);
    } catch(InterruptedException e) {
      // ignore
    }
  } 
}