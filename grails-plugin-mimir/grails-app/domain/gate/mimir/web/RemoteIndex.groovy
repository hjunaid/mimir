package gate.mimir.web;

import gate.mimir.search.QueryRunner;
import gate.mimir.search.RemoteQueryRunner;
import gate.mimir.tool.WebUtils;
import gate.mimir.util.WebUtilsManager;

/**
 * A remote index, accessed via a web service, and published locally.
 */
class RemoteIndex extends Index {
  
  static constraints = {
    remoteUrl(blank:false, nullable:false)
  }
  
  /**
   * The URL of the server hosting the remote index.
   */
  String remoteUrl
  
  // behaviour
  
  /**
   * Shared thread pool (autowired)
   */
  transient searchThreadPool
  
  /**
   * The remote index service (autowired) 
   */
  transient remoteIndexService;
  
  /**
   * The web utils manager, used to get the appropriate WebUtils for
   * remote calls.
   */
  transient webUtilsManager;
  
  
  /**
   * Start running the given query.
   */
  QueryRunner startQuery(String query) {
    //post query to the actual index
    
    //create a local RemoteQueryRunner and store the service URL, index ID, 
    //and query ID in it.
    return new RemoteQueryRunner(remoteUrl, query, searchThreadPool, webUtilsManager.currentWebUtils(remoteUrl))
  }

  /**
   * Obtains the annotations config from the remote controller.
   */
  String[][] annotationsConfig() {
    //call the appropriate method on the remote search controller
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) + 
        "search/annotationsConfigBin";
    try{
      return webUtilsManager.currentWebUtils(remoteUrl).getObject(urlStr)
    }catch(Exception e){
      return new String[0][0]
    }
  }
  
  public double closingProgress() {
    return remoteIndexService.findProxy(this).closingProgress
  }
  
  String indexUrl() {
    StringBuilder responseString = new StringBuilder()
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) + 
        "buildIndex/indexUrlBin";
    try{
      webUtilsManager.currentWebUtils(remoteUrl).getText(responseString, urlStr)
    }catch(IOException e){
      log.error("Problem communicating with the remote server!", e)
      RemoteIndex.withTransaction{
        //by convention, any communication error switches the index state
        state = Index.FAILED
      }
    }   
    return responseString.toString()
  }
  
  /**
   * Asks the remote index to start closing.
   */
  void close() {
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) + 
    "manage/closeBin";
    try{
      webUtilsManager.currentWebUtils(remoteUrl).getVoid(urlStr)
    }catch(IOException e){
      log.error("Problem communicating with the remote server!", e)
      RemoteIndex.withTransaction{
        //by convention, any communication error switches the index state
        state = Index.FAILED
      }
    }   
  }
  
}