package gate.mimir.web

import gate.mimir.gus.client.SearchException;
import grails.converters.JSON;

import java.util.Set;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import gate.mimir.web.client.GwtRpcException

class GwtRpcService implements InitializingBean, DisposableBean {

  static transactional = false

  /**
   * The SearchService that does the actual work (autowired by Grails)
   */
  def searchService

  // scope the service to the web session, so one instance per user
  static scope = "session"

  // expose for GWT RPC
  static expose = ["gwt:gate.mimir.web.client"]

  /**
   * Set of in-progress queries for this session. The values are qeury IDs
   * obtained from the search service
   */
  def Set<String> runningQueries = new HashSet<String>()

  public void afterPropertiesSet() {
  }

  /**
   * Close the executors and free any running queries
   */
  public void destroy() {
    runningQueries.each(this.&releaseQuery)
  }

  /**
   * Post a query
   */
  String search(String indexId, String query) {
    Index.withTransaction {
      try {
        def index = Index.findByIndexId(indexId)
        if(!index) {
          throw new GwtRpcException("Invalid index ID ${indexId}")
        }
        else if(index.state != Index.SEARCHING) {
          throw new GwtRpcException("Index ${indexId} is not open for searching")
        }
        else {
          String queryId = searchService.postQuery(index, query)
          runningQueries.add(queryId)
          return [id:queryId] as JSON
        }
      } catch(GwtRpcException e) {
        log.warn("Exception starting search", e)
        throw e
      } catch(Exception e) {
        log.warn("Exception starting search", e)
        throw new GwtRpcException("Could not start search. Error was:\n${e.message}");
      }
    }
  }

  /**
   * Release the resources associated with a given query.  Does nothing
   * if the given ID does not correspond to a running query.
   */
  void releaseQuery(String id) {
    //check if it's one of our queries
    if(runningQueries.remove(id)){
      searchService.closeQueryRunner(id)
    }
  }

  /**
  * Obtains the types of annotation known to the index, and their features.
   * This method supports auto-completion in the GWT UI.
  */
  String[][] getAnnotationsConfig(String indexId){
    Index.withTransaction {
      Index index = Index.findByIndexId(indexId)
      if(index) {
        return index.annotationsConfig()
      }
      else {
        return ([] as String[][])
      }
    }
  }

}
