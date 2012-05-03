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

import javax.annotation.PreDestroy;

import com.google.common.cache.CacheBuilder
import com.google.common.cache.Cache
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import gate.mimir.search.QueryRunner

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
  Cache<String, QueryRunner> queryRunners = 
      CacheBuilder.newBuilder()
          .expireAfterAccess(30, TimeUnit.MINUTES)
          .removalListener(new CacheRemovalListener(log:log))
          .build()
  
  
  public QueryRunner getQueryRunner(String id){
    return id ? queryRunners.getIfPresent(id) : null
  }

  public boolean closeQueryRunner(String id){
    QueryRunner runner = getQueryRunner(id)
    if(runner){
      log.debug("Releasing query ID ${id}")
      runner.close()
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
  public String postQuery(Index theIndex, String queryString) throws IOException{
    QueryRunner aRunner = theIndex.startQuery(queryString)
    if(aRunner){
      String runnerId = UUID.randomUUID()
      queryRunners.put(runnerId, aRunner)
      return runnerId
    }
    throw new RuntimeException("Could not start query")
  }
  
  /**
   * Posts a query to a specified index. Creates a query runner for it, stores
   * the query runner in the internal runners map, starts the query executions
   *  and returns the ID for the query runner.
   */
  public String postQuery(String indexId, String queryString) throws IOException{
    Index theIndex = Index.findByIndexId(params.indexId)
    if(theIndex){
     return postQuery (theIndex, queryString) 
    }
    throw new IllegalArgumentException("Index with specified ID not found!")
  }
  
  @PreDestroy
  public void destroy() {
    // close all remaining query runners
    queryRunners.invalidateAll()
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
