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
    remoteUsername(blank:true, nullable:true)
    remotePassword(blank:true, nullable:true)
  }
  
  /**
   * The URL of the server hosting the remote index.
   */
  String remoteUrl
  
  
  /**
   * If the remote server uses authentication, the username to be used when 
   * connecting.
   */
  String remoteUsername
  
  /**
   * If the remote server uses authentication, the password to be used when 
   * connecting
   */
  String remotePassword
  
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
    return new RemoteQueryRunner(remoteUrl, query, searchThreadPool, webUtilsManager.currentWebUtils(this))
  }

  /**
   * Obtains the annotations config from the remote controller.
   */
  String[][] annotationsConfig() {
    //call the appropriate method on the remote search controller
    String urlStr = (remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/")) + 
        "search/annotationsConfigBin";
    try{
      return webUtilsManager.currentWebUtils(this).getObject(urlStr)
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
        "manage/indexUrl";
    try{
      webUtilsManager.currentWebUtils(this).getText(responseString, urlStr)
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
    "manage/close";
    try{
      webUtilsManager.currentWebUtils(this).getVoid(urlStr)
    }catch(IOException e){
      log.error("Problem communicating with the remote server!", e)
      RemoteIndex.withTransaction{
        //by convention, any communication error switches the index state
        state = Index.FAILED
      }
    }   
  }
  
}