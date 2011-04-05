/*
 *  ExecutorsList.java
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  Valentin Tablan, 27 Aug 2009
 *
 *  $Id$ 
 */
package gate.mimir.search.query;

import gate.mimir.search.QueryEngine;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import java.util.Arrays;
import java.util.Map.Entry;

import org.apache.log4j.Logger;



/**
 * Class for managing a large list of {@link QueryExecutor}s. This class is 
 * responsible for accessing a large set of executors while only keeping a 
 * limited number of them loaded. It automatically manages the closing and 
 * reopening of executors as needed.  
 * 
 */
public class ExecutorsList {
  
  
  /**
   * The memory cache storing live executors.
   */
  protected class ExecutorCache extends LinkedHashMap<Integer, QueryExecutor>{

    public ExecutorCache() {
      super(maxLiveExecutors > 0 ? maxLiveExecutors : nodes.length, 
            DEFAULT_LOAD_FACTOR, true);
    }

    /* (non-Javadoc)
     * @see java.util.LinkedHashMap#removeEldestEntry(java.util.Map.Entry)
     */
    @Override
    protected boolean removeEldestEntry(Entry<Integer, QueryExecutor> eldest) {
      if (maxLiveExecutors > 0  && size() > maxLiveExecutors){
        try {
          eldest.getValue().close();
        } catch(IOException e) {
          logger.warn("Problem while closing old query execcutor!", e);
        }
        executorsClosed++;
        return true;
      }else{
        return false;
      }
    }
  }
  
  /**
   * Constructor.
   * 
   * @param maxLiveExecutors how many executor should maximally be kept in 
   * memory.
   * @param engine the {@link QueryEngine} used to create executors.
   * @param nodes the {@link QueryNode} for which the executors are created.
   */
  public ExecutorsList(int maxLiveExecutors, QueryEngine engine, 
          QueryNode[] nodes) {
    this.maxLiveExecutors = maxLiveExecutors;
    this.engine = engine;
    this.nodes = nodes;
    this.closed = false;
    
    latestDocuments = new int[nodes.length];
    Arrays.fill(latestDocuments, EXECUTOR_NOT_STARTED);
    hitsOnLatestDocument = new Binding[nodes.length][];
    hitsReturned = new int[nodes.length];
    executors = new ExecutorCache();
    executorsOpened = 0;
    executorsClosed = 0;
  }

  /**
   * Returns the number of nodes/executors managed by this list.
   * @return
   */
  public int size(){
    return nodes == null ? 0 : nodes.length;
  }
  
  /**
   * Constructor that uses the default maximum number of live executors. 
   * @param engine the {@link QueryEngine} used to create executors.
   * @param nodes the {@link QueryNode}s for which the executors are created.
   */
  public ExecutorsList(QueryEngine engine, QueryNode[] nodes) {
    this(DEFAULT_MAX_LIVE_EXECUTORS, engine, nodes);
  }


  protected QueryExecutor getExecutor(int nodeId) throws IOException{
    //try the cache
    QueryExecutor executor = executors.get(nodeId);
    if(executor == null){
      executorsOpened++;
      //recreate the executor
      executor = nodes[nodeId].getQueryExecutor(engine);
//      //scroll the executor to the right document
//      if(latestDocuments[nodeId] != EXECUTOR_NOT_STARTED){
//        int oldLatest = latestDocuments[nodeId];
//        latestDocuments[nodeId] = executor.nextDocument( 
//              latestDocuments[nodeId] - 1);
//        if(oldLatest != latestDocuments[nodeId]){
//          throw new RuntimeException("Malfunction in " + 
//                  this.getClass().getName() + 
//                  ": executor scrolled to a different document after reload!");
//        }
//        //skip the hits already returned
//        for(int i = 0; i< hitsReturned[nodeId]; i++){
//          if(executor.nextHit() == null){
//            throw new RuntimeException("Malfunction in " + 
//                    this.getClass().getName() + 
//                    ": executor did not return the same hits after reload!");
//          }
//        }
//        
//      }else{
//        //the executor has not been started yet, so no need to do so 
//      }
      //add to the cache
      executors.put(nodeId, executor);
    }
    return executor;
  }
  
  public int nextDocument(int nodeId, int greaterThan) throws IOException{
    if(latestDocuments[nodeId] == -1){
      //executor already exhausted
      return -1;
    }
    QueryExecutor executor = getExecutor(nodeId);
    if(executor.getLatestDocument() < 0) {
      // newly recreated executor, so we need to skip ahead
      greaterThan = Math.max(greaterThan, latestDocuments[nodeId]);
    }
    latestDocuments[nodeId] = executor.nextDocument(greaterThan);
    hitsReturned[nodeId] = 0;
    hitsOnLatestDocument[nodeId] = null;
    return latestDocuments[nodeId];
  }
  
  public Binding nextHit(int nodeId) throws IOException{
    if(latestDocuments[nodeId] == -1){
      //executor already exhausted
      return null;
    }
    
    if(hitsReturned[nodeId] == 0) {
      // we're asking for the first hit on this document: build the cache
      QueryExecutor executor = getExecutor(nodeId);
      if(executor.getLatestDocument() < 0) {
        // newly (re)created executor, so we need to skip ahead
        int oldLatest = latestDocuments[nodeId];
        latestDocuments[nodeId] = executor.nextDocument(latestDocuments[nodeId] - 1);
        if(oldLatest != latestDocuments[nodeId]){
          throw new RuntimeException("Malfunction in " + 
                  this.getClass().getName() + 
                  ": executor scrolled to a different document after reload!");
        }      
      }
      List<Binding> hits = new LinkedList<Binding>();
      Binding aHit = executor.nextHit();
      while(aHit != null) {
        hits.add(aHit);
        aHit = executor.nextHit();
      }
      hitsOnLatestDocument[nodeId] = hits.toArray(new Binding[hits.size()]);
    }
    // now return directly from cache
    Binding aHhit = 
      (hitsReturned[nodeId] < hitsOnLatestDocument[nodeId].length) ?
      hitsOnLatestDocument[nodeId][hitsReturned[nodeId]] :
      null;
    if(aHhit != null){
      hitsReturned[nodeId]++;
    }
    return aHhit;
  }
  
  public int latestDocument(int nodeId){
    return latestDocuments[nodeId];
  }
  
  /**
   * Closes all executors still live, and releases all memory resources.
   * @throws IOException 
   */
  public void close() throws IOException{
    closed = true;
    for(QueryExecutor executor : executors.values()){
      executor.close();
      executorsClosed++;
    }
    engine = null;
    executors.clear();
    executors = null;
    hitsReturned = null;
    latestDocuments = null;
    nodes = null;
    logger.debug("Closing executors list. Operations (open/close): " + 
            executorsOpened +"/" + executorsClosed);
  }
  
  /**
   * The default maximum number of executor to be kept live.
   */
  public static final int DEFAULT_MAX_LIVE_EXECUTORS = 20000;
  
  /**
   * The load factor used when none specified in constructor.
   */
  protected static final float DEFAULT_LOAD_FACTOR = 0.75f;
  
  /**
   * Value returned when {@link #latestDocument(int)} is called for an executor
   * that was not started yet (i.e. nextDocument was not called yet).
   */
  public static final int EXECUTOR_NOT_STARTED = -2;
  
  /**
   * The maximum number of executors that should be kept in memory at any one 
   * time.
   */
  protected int maxLiveExecutors;
  
  /**
   * The {@link QueryEngine} used to create executors.
   */
  protected QueryEngine engine;
  
  
  /**
   * Has {@link #close()} been called?
   */
  protected boolean closed;
  
  private long executorsClosed;
  
  private long executorsOpened;
  
  protected static Logger logger = Logger.getLogger(ExecutorsList.class);
  
  /**
   * The {@link QueryNode} used to create executors.
   */
  protected QueryNode[] nodes;
  
  /**
   * Array that holds the latest document ID returned by each executor.
   */
  protected int[] latestDocuments;
  
  /**
   * The number of hits already returned from the latest document, for each 
   * executor. This is used to skip already-returned hits when the executor 
   * needs to be re-loaded.  
   */
  protected int[] hitsReturned;
  
  /**
   * A cache storing all the hits on the latest document for each executor. 
   * First array index selects the executor, second array index selects the hit. 
   */
  protected Binding[][] hitsOnLatestDocument;
  
  
  /**
   * The memory cache that holds the live executors.
   */
  protected Map<Integer, QueryExecutor> executors;
  
}
