package gate.mimir.web

import gate.mimir.web.Index;

import java.io.IOException;
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
  Map<String, QueryRunner> queryRunners = [:]
  
  public QueryRunner getQueryRunner(String id){
    return queryRunners[id]
  }

  public boolean closeQueryRunner(String id){
    QueryRunner runner = queryRunners[id]
    if(runner){
      log.debug("Releasing query ID ${id}")
      runner.close()
      queryRunners.remove(id)
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
      //as a courtesy to the user, we'll start the search for them
      aRunner.getMoreHits()
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
  
}