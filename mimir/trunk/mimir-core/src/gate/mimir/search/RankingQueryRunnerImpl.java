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

import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.score.MimirScorer;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * A QueryRunner implementation that does ranking.
 */
public class RankingQueryRunnerImpl implements Runnable {
  
  /**
   * When doing ranking, this class is used to delegate the iteration of 
   * document IDs to an IntIterator that is limited to a finite set of documents
   * that have just been ranked. When that iterator is emptied, the 
   * {@link RankingQueryRunnerImpl#documentsById} field is nullified before 
   * hasNext() returns false, to indicate that more documents may be available.
   */
  protected class RankedDocIdIterator implements IntIterator {
    
    public RankedDocIdIterator(IntIterator underlyingIterator) {
      this.underlyingIterator = underlyingIterator;
    }

    protected IntIterator underlyingIterator;

    public boolean hasNext() {
      return underlyingIterator.hasNext();
    }

    public Integer next() {
      return underlyingIterator.next();
    }

    public void remove() {
      underlyingIterator.remove();
    }

    public int nextInt() {
      return underlyingIterator.nextInt();
    }

    public int skip(int n) {
      return underlyingIterator.skip(n);
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
  protected ObjectList<Binding[]> documentHits;
  

  /**
   * The order the documents should be returned in (elements in this list are 
   * indexes in {@link #documentIds}).
   */
  protected IntList documentsOrder;
  
  /**
   * An iterator supplying documentIDs in ascending order. These are used when 
   * collecting the hits.
   */
  protected IntIterator documentsById;
  
  /**
   * The thread used for executing the query. This is a separate thread from one 
   * that created the query runner.
   */
  protected Thread runningThread;
  
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
    documentsById = null;
    // start the search
    getMoreHits();
  }
  
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getMoreHits()
   */
  protected synchronized void getMoreHits() throws IOException {
    if(runningThread != null){
      //we're already running -> ignore
      return;
    }
    // get a thread from the executor, if one exists
    if(queryExecutor.getQueryEngine().getExecutor() != null){
      queryExecutor.getQueryEngine().getExecutor().execute(this);  
    }else{
      new Thread(this, getClass().getName()).start();
    }
  }
  
  
  /**
   * Gets the number of documents found to contain hits. If the search has not
   * yet completed, then -1 is returned.
   * @return
   */
  public int getDocumentsCount() {
    if(queryExecutor == null) return documentIds.size();
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
   * Gets the ID of a document found to contain hits.
   * @param index the index of the desired document in the list of documents. 
   * This should be a value between 0 and {@link #getDocumentsCount()} -1.
   *  
   * @return an int value, representing the ID of the requested document.
   * @throws IndexOutOfBoundsException is the index provided is less than zero, 
   * or greater than {@link #getDocumentsCount()} -1.
   */
  public int getDocumentID(int index) throws IndexOutOfBoundsException {
    // TODO: check position index has been ranked yet
    if(documentsOrder != null) {
      return documentIds.getInt(documentsOrder.getInt(index));
    } else {
      return documentIds.getInt(index);  
    }
  }
  
  /**
   * Creates an {@link IntIterator} used for enumerating the documents in the
   * correct order for returning to the user.
   * If no more documents are available, this should return null
   * @return
   * @throws IOException 
   */
  protected IntIterator getDocumentIterator() throws IOException {
    if(scorer != null) {
      // we're doing ranking
      if(documentIds == null) {
        // first stage: collect all documents and their scores
        documentIds = new IntArrayList();
        documentScores = new DoubleArrayList();
        documentHits = new ObjectArrayList<Binding[]>();
        documentsOrder = new IntArrayList(
          queryExecutor.getQueryEngine().getRankingDocCount());
        
        scorer.wrap(queryExecutor);
        int docId = scorer.nextDocument(-1);
        while(docId >= 0) {
          documentIds.add(docId);
          documentScores.add(scorer.score());
          documentHits.add(null);
          docId = scorer.nextDocument(-1);
        }
      }
      // collect some more ranked documents
      
      int rankRangeStart = documentsOrder.size();
      int rankRangeEnd = documentsOrder.size() + 
          queryExecutor.getQueryEngine().getRankingDocCount();
      int docsByRankWriteIndex = rankRangeStart;
      
      // the document with the minimum score already ranked.
      int smallestOldScoreDocId = rankRangeStart > 0 ? 
        documentIds.getInt(documentsOrder.getInt(rankRangeStart -1))
        : -1;
      // the score for the document above, which is a the upper limit for new scores
      double smallestOldScore = rankRangeStart > 0 ? 
          documentScores.getDouble(documentsOrder.getInt(rankRangeStart -1))
          : -1;
      // the documentIds for newly ranked documents
      IntSortedSet newDocuments = new IntAVLTreeSet();
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
        if(docsByRankWriteIndex < rankRangeEnd 
           || 
           (documentScore > smallestNewScore && 
               (smallestOldScore < 0 || documentScore < smallestOldScore)) 
           ||
           documentScore == smallestOldScore && documentId != smallestOldScoreDocId) {
          if(docsByRankWriteIndex == rankRangeEnd) {
            // we need to remove the  newly ranked document 
            // with the smallest score
            docsByRankWriteIndex--;
            int oldDocIndex = documentsOrder.removeInt(docsByRankWriteIndex);
            newDocuments.remove(documentIds.getInt(oldDocIndex));
          }
          // find the rank for the new doc
          int rank = rankRangeStart;
          while(rank < documentsOrder.size() && 
                documentScore < documentScores.getDouble(documentsOrder.getInt(rank))){
            rank++;
          }
          documentsOrder.add(rank, i);
          newDocuments.add(documentId);
          docsByRankWriteIndex++;
        }
      }
      if(newDocuments.isEmpty()){
        return null;
      } else {
        return new RankedDocIdIterator(newDocuments.iterator());
      }
    } else {
      // we're not doing scoring, simply use the queryExecutor as an intIterator
      return queryExecutor;
    }
  }
  
  public void run() {
    //store the running thread
    synchronized(this) {
      if(runningThread != null){
        //some task is already running
        return;
      }
      runningThread = Thread.currentThread();  
    }
    try {
      if(documentsById == null) {
        documentsById = getDocumentIterator();
      }
      // collect the hits
      while(documentsById != null) {
        
      }
    } catch(IOException e) {
      //something went bad!
      logger.error("IOException during search!", e);
    }finally{
      //this search stage has completed -> clear the running thread
      synchronized(this) {
        runningThread = null;  
      }
    } 
  }
}
