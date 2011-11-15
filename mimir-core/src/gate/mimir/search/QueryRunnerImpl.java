/*
 *  QueryRunnerImpl.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin tablan, 16 Dec 2009
 *  
 *  $Id$
 */
package gate.mimir.search;


import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.score.MimirScorer;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;


/**
 * An object that manages the execution of a Mimir query.
 * 
 * @author valyt
 *
 */
public class QueryRunnerImpl implements QueryRunner {
  
  private class SearchStageRunner implements Runnable{
    
    public void run(){
      //store the running thread
      synchronized(QueryRunnerImpl.this) {
        if(runningThread != null){
          //some task is already running
          return;
        }
        runningThread = Thread.currentThread();  
      }
      //do the actual work
      try {
        long startTime = System.currentTimeMillis();
        if(currentDocStats == null){
          //this is the first search stage
          // if we're ranking, we need to do multiple passes
          if(scorer != null) {
            
          }
          currentDocStats = new int[]{nextNonDeleted(), 0};
        }
        int hitsThisStage = 0;
        while(hitsThisStage < maxHitsPerStage &&
              (timeout < 0 || 
               (System.currentTimeMillis() - startTime) < timeout) &&
               !closed){
          if(currentDocStats[0] < 0){
            //we have run out of documents
            complete = true;
            return;
          }
          Binding aHit = queryExecutor.nextHit();
          if(aHit != null){
            hitsThisStage++;
            currentDocStats[1]++;
            resultsQueue.put(aHit);
          }else{
            //no more hits from this document
            //save the hits for the document just ended
            synchronized(resultsList) {
              resultsQueue.drainTo(resultsList);
            }
            //save the stats for the document just ended
            synchronized(documentStats) {
              documentStats.add(currentDocStats); 
            }
            //and move to the next doc
            currentDocStats = new int[]{nextNonDeleted(), 0};
          }
        }
      } catch(IOException e) {
        //something went bad!
        logger.error("IOException during search!", e);
      } catch(InterruptedException e) {
        // interrupted, so we're done!
        complete = true;
      }finally{
        //this search stage has completed -> clear the running thread
        synchronized(QueryRunnerImpl.this) {
          runningThread = null;  
        }
      }      
    }

    /**
     * Find the next available document from the query runner that is not
     * marked as deleted.
     */
    private int nextNonDeleted() throws IOException {
      int nextDocId = queryExecutor.nextDocument(
              queryExecutor.getLatestDocument());
      while(nextDocId != -1 && 
            queryExecutor.getQueryEngine().isDeleted(nextDocId)) {
        nextDocId = queryExecutor.nextDocument(
                queryExecutor.getLatestDocument());
      }
      return nextDocId;
    }
    
    
  }
  
  protected Thread runningThread;
  
  /**
   * Runnable object used to perform the actual search stages.
   */
  protected SearchStageRunner stageRunner;
  
  protected BlockingQueue<Binding> resultsQueue;
  
  protected List<Binding> resultsList;
  
  
  /**
   * A list holding document statistics. Each element refers to a document, and 
   * is an array of 2 ints: the document ID, and the number of hits 
   * respectively.
   */
  protected List<int[]> documentStats;
  
  protected int maxHitsPerStage = DEFAULT_MAX_HITS;
  
  protected int timeout = DEFAULT_TIMEOUT;
  
  protected QueryExecutor queryExecutor;

  /**
   * The scorer to be used for ranking the results. Set to <code>null</code> if
   * ranking is not required.  
   */
  protected MimirScorer scorer;
    
  protected Logger logger =  Logger.getLogger(QueryRunnerImpl.class);
  
  /**
   * Stats (docID, hitsCount) for the current document.
   */
  protected int[] currentDocStats = null;

  /**
   * Has the query execution completed? 
   */
  protected boolean complete = false;
  
  /**
   * Has this query runner been closed already?
   */
  protected boolean closed = false;
  
  /**
   * Creates a query runner in ranking mode.
   * @param qNode the {@link QueryNode} for the query being executed.
   * @param scorer the {@link MimirScorer} to use for ranking.
   * @param qEngine the {@link QueryEngine} used for executing the queries.
   * @throws IOException
   */
  public QueryRunnerImpl(QueryExecutor executor, MimirScorer scorer) {
    this(executor);
    this.scorer = scorer;
  }
  
  public QueryRunnerImpl(QueryExecutor executor) {
    this.queryExecutor = executor;
    this.documentStats = new ArrayList<int[]>();
    this.resultsList = new ArrayList<Binding>();
    this.resultsQueue = new LinkedBlockingQueue<Binding>();
    this.stageRunner = new SearchStageRunner();
  }

  
  
  public int getDocumentHitsCount(int index) throws IndexOutOfBoundsException {
    synchronized(documentStats) {
      return documentStats.get(index)[1];
    }
  }

  public int getDocumentID(int index) throws IndexOutOfBoundsException {
    synchronized(documentStats) {
      return documentStats.get(index)[0];
    }
  }

  public int getDocumentsCount() {
    synchronized(documentStats) {
     return documentStats.size(); 
    }
  }

  public List<Binding> getHits(int startIndex, int hitCount)
          throws IndexOutOfBoundsException {
    synchronized(resultsList) {
      resultsQueue.drainTo(resultsList);
      if(startIndex >= resultsList.size()) {
        return Collections.emptyList();
      }
      else {
        return new ArrayList<Binding>(resultsList.subList(startIndex, 
                Math.min(startIndex + hitCount, resultsList.size())));
      }
    }
  }

  public int getHitsCount() {
    synchronized(resultsList) {
      resultsQueue.drainTo(resultsList);
    }
    synchronized(resultsList) {
      return resultsList.size();  
    }
  }

  public synchronized void getMoreHits() throws IOException {
    if(runningThread != null){
      //we're already running -> ignore
      return;
    }
    //get a thread from the executor, if one exists
    if(queryExecutor.getQueryEngine().getExecutor() != null){
      queryExecutor.getQueryEngine().getExecutor().execute(stageRunner);  
    }else{
      new Thread(stageRunner, getClass().getName()).start();
    }
  }

  public synchronized boolean isActive() {
    return runningThread != null;
  }

  public boolean isComplete() {
    return complete;
  }

  public void setStageMaxHits(int maxHits) {
    this.maxHitsPerStage = maxHits;
  }

  public void setStageTimeout(int timeout) {
    this.timeout = timeout;
  }

  public synchronized void close() throws IOException {
    //stop the query execution in an orderly fashion
    closed = true;
    // notify the engine we're closing
    queryExecutor.getQueryEngine().releaseQueryRunner(this);
    queryExecutor.close();
    
    synchronized(documentStats) {
     documentStats.clear(); 
    }
    resultsQueue.clear();
    synchronized(resultsList) {
      resultsList.clear();  
    }
    
  }



  public String[][] getDocumentText(int documentID, int termPosition, 
          int length) throws IndexException {
    return queryExecutor.getQueryEngine().getText(documentID, termPosition, length);
  }



  public String getDocumentURI(int documentID) throws IndexException {
    return queryExecutor.getQueryEngine().getDocumentURI(documentID);
  }

  public String getDocumentTitle(int documentID) throws IndexException {
    return queryExecutor.getQueryEngine().getDocumentTitle(documentID);
  }

  @Override
  public Serializable getDocumentMetadataField(int docID, String fieldName)
      throws IndexException {
    return queryExecutor.getQueryEngine().getDocumentMetadataField(docID, 
        fieldName);
  }

  @Override
  public Map<String, Serializable> getDocumentMetadataFields(int docID,
      Set<String> fieldNames) throws IndexException {
    Map<String, Serializable> res = new HashMap<String, Serializable>();
    for(String fieldName : fieldNames) {
      Serializable value = getDocumentMetadataField(docID, fieldName);
      if(value != null) res.put(fieldName, value);
    }
    return res;
  }



  /**
   * Returns the list of hits for a given document (specified by its ID). 
   * @param documentId the ID for the sought document
   * @return a {@link List} of {@link Binding} values.
   */
  public List<Binding> getHitsForDocument(int documentId){
    List<Binding> hits = new ArrayList<Binding>();
    int startIndex = 0;
    synchronized(documentStats) {
      finddoc:for(int i = 0; i < documentStats.size(); i++){
        int[] docStats = documentStats.get(i);
        if(documentId == docStats[0]){
          //we found the right document
          hits.addAll(resultsList.subList(startIndex, startIndex + docStats[1]));
          break finddoc;
        }else{
          //wrong document - we need to skip it
          startIndex += docStats[1];
        }
      }
    }
    return hits;
  }

  public void renderDocument(int documentId, Appendable out) 
      throws IOException, IndexException {
    queryExecutor.getQueryEngine().renderDocument(documentId, 
            getHitsForDocument(documentId), out);
  }
  
  
  
  
}
