package gate.mimir.web

import gate.mimir.web.FederatedIndex;
import gate.mimir.web.Index;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger

import org.apache.log4j.Logger;

/**
 * Service for operations on federated indexes.
 */
class FederatedIndexService {

  /**
   * Map holding the index into the list of indexes that should be used next by
   * each federated index.  Federated indexes dispatch documents for indexing
   * to their sub-indexes in round-robin fashion, the first to sub-index 0, the
   * next to 1, and so on up to <code>indexes.size()</code>, then starting
   * again from 0.  But we can't store this next index number in the database
   * because at high request volumes we'll either get loads of optimistic
   * locking failures, or will need to synchronize access to the database.
   * Neither of these would be acceptable, so instead we store a map in this
   * service from the FederatedIndex id to an AtomicInteger which we can
   * getAndIncrement without locking.
   */
  private Map nextIndex = [:]
  
  private Map<String, FederatedIndexProxy> proxies = [:];
  
  public synchronized FederatedIndexProxy findProxy(FederatedIndex index) {
    FederatedIndexProxy p = proxies[index.id]
    if(!p) {
      p = new FederatedIndexProxy(index)
      proxies[index.id] = p
    }
    return p
  }
  
  public void indexDeleted(id){
    proxies.remove(id)?.close()
  }
  
  /**
   * Register a FederatedIndex in the nextIndex map.  This should be called
   * when the index is opened for indexing (i.e. at BootStrap for existing
   * indexes or at creation time for new ones).
   */
  public void registerIndex(FederatedIndex index) {
    nextIndex[index.id] = new AtomicInteger(0)
  }

  /**
   * Return the index into the given FederatedIndex's list of sub-indexes that
   * the next document should be sent to.  This is the value from the nextIndex
   * map taken modulo the number of sub-indexes that the federated index
   * contains.
   */
  public int getNextIndex(FederatedIndex index) {
    if(!nextIndex.containsKey(index.id)) {
      throw new IllegalArgumentException("Federated index ${index.indexId} not registered")
    }
    return (int)(nextIndex[index.id].getAndIncrement() % index.indexes.size())
  }
  
  /**
   * Register existing indexes with this class at startup.
   */
  public void init() {
    FederatedIndex.list().each {
      findProxy(it)
      if(it.state == Index.INDEXING) registerIndex(it)
    }
  }
}

class FederatedIndexProxy implements Runnable{
  
  public FederatedIndexProxy(FederatedIndex index){
    this.id = index.id
    Thread t = new Thread(this)
    t.setDaemon(true)
    t.start()
    FederatedIndex.withTransaction{
      updateData(index)  
    }
  }
  
  /**
   * Updates the internal data for the federated index that we're managing, 
   * based on the data from  the children.
   */
  private void updateData (FederatedIndex index) {
    index.state = index.indexes.collect { it.state }.inject(null) { prev, cur ->
      if(prev == null) {
        // first step
        return cur
      }
      else if(prev == Index.FAILED) {
        // anything failed => everything failed
        return Index.FAILED
      }
      else if(prev == cur) {
        // all the same so far
        return cur
      }
      else if([prev,cur].containsAll([Index.SEARCHING, Index.CLOSING])) {
        // we know prev != cur, if one is closing and the other searching then
        // some of our children have finished closing and some haven't
        return Index.CLOSING
      }
      else {
        // states are inconsistent but may become clean shortly (e.g. delays
        // in obtaining the remote states)
        return Index.WORKING
      }
    }
    
    if(index.state == Index.CLOSING) {
      closingProgress = index.indexes.collect {
        (it.state == Index.CLOSING) ? it.closingProgress() : 1.0 
      }.sum() / index.indexes.size()
    }
  }
  
  
  /**
   * The hibernate ID of the index for which this proxy was created.
   */
  def id
  double closingProgress = 0.0
  private static final Logger log = Logger.getLogger("grails.app.service.${FederatedIndexProxy.class.getName()}")
  private static final int DELAY = 10000
  boolean stop = false
    
  public void run(){
    Thread.sleep(DELAY)
    while(!stop) {
      //get the index object
      FederatedIndex.withTransaction{
        FederatedIndex index = FederatedIndex.get(id)
        updateData(index)
      }
      Thread.sleep(DELAY)
    }    
  }
  
  public void close() {
    stop = true
  }
}
