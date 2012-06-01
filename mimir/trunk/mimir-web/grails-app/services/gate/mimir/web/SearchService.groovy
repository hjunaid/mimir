/*
 *  SearchService.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
package gate.mimir.web

import gate.mimir.web.Index;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.google.common.cache.CacheBuilder
import com.google.common.cache.Cache
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import gate.mimir.search.QueryRunner
import gate.mimir.search.query.parser.ParseException;

class SearchService {
  
  /**
   * We use transactions to access the index objects, so we need this set to 
   * true.
   */
  static transactional = true
  
  /**
   * The Search service is a singleton.
   */
  static scope = "singleton"
  
  /**
   * A map holding the currently active query runners. 
   */
  //Map<String, QueryRunner> queryRunners = [:].asSynchronized()
  Cache<String, QueryRunner> queryRunners
  
  CacheCleaner cacheCleaner
          
  public QueryRunner getQueryRunner(String id){
    return id ? queryRunners.getIfPresent(id) : null
  }

  public boolean closeQueryRunner(String id){
    QueryRunner runner = getQueryRunner(id)
    if(runner){
      log.debug("Releasing query ID ${id}")
      // the cache listener will close the runner
      queryRunners.invalidate(id)
      return true
    }
    return false
  }
  
  /**
   * Posts a query to a specified index. Creates a query runner for it, stores
   * the query runner in the internal runners map, starts the query executions
   *  and returns the ID for the query runner.
   */
  public String postQuery(Index theIndex, String queryString) 
      throws IOException, ParseException {
    QueryRunner aRunner = theIndex.startQuery(queryString)
    if(aRunner){
      String runnerId = UUID.randomUUID()
      queryRunners.put(runnerId, aRunner)
      return runnerId
    } else {
      throw new RuntimeException("Could not start query")
    } 
  }
  
  /**
   * Posts a query to a specified index. Creates a query runner for it, stores
   * the query runner in the internal runners map, starts the query executions
   *  and returns the ID for the query runner.
   */
  public String postQuery(String indexId, String queryString) 
      throws IOException, ParseException {
    Index theIndex = Index.findByIndexId(params.indexId)
    if(theIndex){
     return postQuery (theIndex, queryString) 
    }
    throw new IllegalArgumentException("Index with specified ID not found!")
  }
  
  @PostConstruct
  public void setUp() {
    // construct the runners cache
    queryRunners = CacheBuilder.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .removalListener(new CacheRemovalListener(log:log))
        .build()
    // background thread used to clean up the cache
    cacheCleaner = new CacheCleaner(theCache:queryRunners, log:log)
    new Thread(cacheCleaner).start()
  }
  
  @PreDestroy
  public void destroy() {
    // close all remaining query runners
    queryRunners.invalidateAll()
    cacheCleaner.interrupt()
  }
}

/**
 * Implementation of a cache removal listener, so that we can close the query
 * runners as they get evicted.
 */
class CacheRemovalListener implements RemovalListener<String, QueryRunner> {

  def log
  
  /* (non-Javadoc)
   * @see com.google.common.cache.RemovalListener#onRemoval(com.google.common.cache.RemovalNotification)
   */
  @Override
  public void onRemoval(RemovalNotification<String, QueryRunner> notification) {
    log.debug("Evicting query ${notification.key}.")
    notification.value.close()
  }
  
}

/**
 * Action to regularly clean up the cache, running from a background thread.
 */
class CacheCleaner implements Runnable {

  volatile Cache theCache
  
  def log
  
  Thread myThread
  
  public void interrupt() {
    theCache = null
    myThread?.interrupt()
  }
  
  public void run() {
    myThread = Thread.currentThread()
    while(theCache != null) {
      theCache.cleanUp();
      log.debug("Removed stale queries; count after clean-up: ${theCache.size()}")
      try {
        // every 30 seconds, clear out the old runners
        Thread.sleep(30 * 1000);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt()
      }
    }
  }
}