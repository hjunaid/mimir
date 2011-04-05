package gate.mimir.web;

import gate.mimir.web.Index;
import gate.mimir.web.RemoteIndex;

import java.util.Map;

import org.apache.log4j.Logger

class RemoteIndexService {
  
  def webUtilsManager
  
  private Map<String, RemoteIndexProxy> proxies = [:];
  
  public String getIndexState(RemoteIndex index){
    return findProxy(index).state
  }
  
  public void indexDeleted(id){
    proxies.remove(id)?.close()
  }
  
  public synchronized RemoteIndexProxy findProxy(RemoteIndex index) {
    RemoteIndexProxy p = proxies[index.id]
    if(!p) {
      p = new RemoteIndexProxy(index)
      proxies[index.id] = p
    }
    return p
  }
  
  /**
   * Create proxies for all known remote indexes.
   */
  public void init() {
    RemoteIndex.list().each {
      try{
        findProxy(it)
      }catch(Exception e){
        log.error("Problem while initialising remote index ${it.name}!", e)
      }
    }
  }
}

/**
 * An object to keep tabs on a remote index.
 */
class RemoteIndexProxy implements Runnable {
  
  private static final Logger log = Logger.getLogger("grails.app.service.${RemoteIndexProxy.class.getName()}")
  private static final int DELAY = 10000
  
  public RemoteIndexProxy(RemoteIndex index) {
    this.id = index.id
    Thread t = new Thread(this)
    t.setDaemon(true)
    t.start()
    RemoteIndex.withTransaction{
      fetchRemoteState(index)  
    }
  }
  
  /**
   * The hibernate ID of the index for which this proxy was created.
   */
  def id
  double closingProgress = 0.0
  
  boolean stop = false
  
  public void run() {
    Thread.sleep(DELAY)
    while(!stop) {
      //get the index object
      RemoteIndex.withTransaction{
        RemoteIndex index = RemoteIndex.get(id)
        fetchRemoteState(index)
        if(index.state == Index.CLOSING) fetchClosingProgress(index)
      }
      Thread.sleep(DELAY)
    }
  }
  
  private fetchRemoteState(RemoteIndex index) {
    StringBuilder sb = new StringBuilder()
    try {
      webUtilsManager.currentWebUtils(index.remoteUrl).getText(sb, "${index.remoteUrl}/manage/stateBin")
      index.state = sb.toString()
    }
    catch(IOException e) {
      index.state = Index.FAILED
      log.error("Problem communicating with remote index", e)
    }
  }
  
  private fetchClosingProgress(RemoteIndex index) {
    try {
      closingProgress = webUtilsManager.currentWebUtils(index.remoteUrl).getDouble(
          "${index.remoteUrl}/manage/closingProgressBin")
    }
    catch(IOException e) {
      index.state = Index.FAILED
      log.error("Problem communicating with remote index", e)
    }
  }
  
  public void close() {
    stop = true
  }
}