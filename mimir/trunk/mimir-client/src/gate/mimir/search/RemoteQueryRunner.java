/*
 *  RemoteQueryRunner.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007 (also included with this distribution as file 
 *  LICENCE-AGPL3.html).
 *
 *  A commercial licence is also available for organisations whose business
 *  models preclude the adoption of open source and is subject to a licence
 *  fee charged by the University of Sheffield. Please contact the GATE team
 *  (see http://gate.ac.uk/g8/contact) if you require a commercial licence.
 *
 *  Valentin Tablan, 05 Jan 2010
 *  
 *  $Id$
 */
package gate.mimir.search;

import gate.mimir.index.IndexException;
import gate.mimir.search.QueryRunner;
import gate.mimir.search.query.Binding;
import gate.mimir.tool.WebUtils;

import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.log4j.Logger;


public class RemoteQueryRunner implements QueryRunner {
  /**
   * The WebUtils instance we use to communicate with the remote
   * index.
   */
  private WebUtils webUtils;
  
  /**
   * The URL to the server hosting the remote index we're searching
   */
  private String remoteUrl;

  /**
   * The query ID for the actual query runner, local to the remote index.
   */
  private String queryId;

  /**
   * The implementation for the background thread action.
   */
  private RemoteUpdater backgroundThread;

  /**
   * Holds a local cache of the document statistics from the actual runner. Each
   * element refers to a document, and is an array of 2 ints: the document ID,
   * and the number of hits respectively. This copy is filled in by a background
   * thread. This value acts as the lock for multi-threaded access to all values
   * that are updated by the {@link RemoteUpdater background thread}.
   */
  private List<int[]> documentStats;

  /**
   * Flag for the state of the query runner. This is a local copy loosely
   * synchronised with the flag of the actual query runner (on the remote
   * server). This value is updated by the {@link RemoteUpdater background
   * thread}.
   */
  private volatile boolean active;

  /**
   * Flag for the state of the query runner. This is a local copy loosely
   * synchronised with the flag of the actual query runner (on the remote
   * server). This value is updated by the {@link RemoteUpdater background
   * thread}.
   */
  private volatile boolean complete;

  /**
   * If the {@link RemoteUpdater background thread} encounters an exception, it
   * will save it here. As the background thread cannot report it itself, it is
   * the job of any of the interactive methods to report it.
   */
  private Exception exceptionInBackgroundThread;

  private Logger logger = Logger.getLogger(RemoteQueryRunner.class);

  private String getActionBaseUrl(String action) throws IOException{
    //this method is always called from interactive methods, that are capable of
    //reporting errors to the user. So we use this place to check if the 
    //background thread had any problems, and report them if so.
    if(exceptionInBackgroundThread != null){
      Exception e = exceptionInBackgroundThread;
      exceptionInBackgroundThread = null;
      throw (IOException)new IOException(
          "Problem communicating with the remote index").initCause(e);
    }
    
    //an example URL looks like this:
    //http://localhost:8080/mimir/remote/bf25398f-f087-4224-bfa6-c2ef00399c04/search/hitCountBin?queryId=c4da799e-9ca2-46ae-8ded-30bdc37ad607
    StringBuilder str = new StringBuilder(remoteUrl);
    str.append(SERVICE_SEARCH);
    str.append('/');
    str.append(action);
    return str.toString();
  }
  
  /**
   * The action implementation for the background thread responsible for reading
   * the document statistics from the actual remote query runner, and updating
   * the local cached values.
   */
  private class RemoteUpdater implements Runnable {
    public void run() {
      try {
        while(!complete) {
          if(active) {
            // update the active flag
            active = webUtils.getBoolean(
                    getActionBaseUrl(ACTION_IS_ACTIVE_BIN),
                    "queryId", queryId);
            // read the doc count (if we just became inactive (or complete),
            // these will be the last docs we read in this stage (or ever).
            int docCount = webUtils.getInt(
                    getActionBaseUrl(ACTION_DOC_COUNT_BIN),
                    "queryId", queryId);
            if(docCount > documentStats.size()) {
              // get the new data and append to the local cache
              int[][] newStats = (int[][])webUtils.getObject(
                      getActionBaseUrl(ACTION_DOC_STATS_BIN),
                      "queryId", queryId,
                      "startIndex", Integer.toString(documentStats.size()),
                      "count", Integer.toString(docCount - documentStats.size()));
              if(newStats != null && newStats.length > 0) {
                synchronized(documentStats) {
                  for(int[] stat : newStats) {
                    documentStats.add(stat);
                  }
                }
              }
            }
            // update the complete flag
            complete = webUtils.getBoolean(
                    getActionBaseUrl(ACTION_IS_COMPLETE_BIN),
                    "queryId", queryId);
            // take a nap
            Thread.sleep(300);
          } else {
            // non-active -> sleep longer
            Thread.sleep(1000);
          }
        }
      } catch(InterruptedException e) {
        // pass it on
        Thread.currentThread().interrupt();
      } catch(Exception e) {
        // something went bad
        logger.error("Cannot communicate with remote service!", e);
        exceptionInBackgroundThread = e;
      } finally {
        // nullify the reference to us from the enclosing object, to mark that
        // we have finished our work.
        backgroundThread = null;
      }
    }
  }

  protected static final String ACTION_CLOSE = "close";

  protected static final String ACTION_DOC_COUNT_BIN = "docCountBin";

  protected static final String ACTION_DOC_STATS_BIN = "docStatsBin";

  protected static final String ACTION_DOC_TEXT_BIN = "docTextBin";

  protected static final String ACTION_DOC_URI_BIN = "docURIBin";
  
  protected static final String ACTION_DOC_TITLE_BIN = "docTitleBin";
  
  protected static final String ACTION_DOC_MEDATADA_FIELDS_BIN = "docMetadataFieldsBin";

  protected static final String ACTION_GET_MORE_HITS = "getMoreHits";

  protected static final String ACTION_HIT_COUNT_BIN = "hitCountBin";

  protected static final String ACTION_HITS_BIN = "hitsBin";

  protected static final String ACTION_IS_ACTIVE_BIN = "isActiveBin";

  protected static final String ACTION_IS_COMPLETE_BIN = "isCompleteBin";

  protected static final String ACTION_POST_QUERY_BIN = "postQueryBin";

  protected static final String ACTION_SET_STAGE_MAX_HITS_BIN = "setStageMaxHitsBin";
  
  protected static final String ACTION_SET_STAGE_TIMEOUT_BIN = "setStageTimeoutBin";
  

  protected static final String ACTION_RENDER_DOCUMENT = "renderDocument";

  protected static final String SERVICE_SEARCH = "search";

  /**
   * Constructs a new RemoteQueryRunner.
   * 
   * @param remoteUrl
   *          the URL of the remote server holding the actual index being
   *          queried. This should include the host name, the port (if needed),
   *          and the web app root name, and the index UUID (e.g.
   *          http://mimirhost:8080/mimir/1234).
   * @param queryId
   * @param threadPool
   */
  public RemoteQueryRunner(String remoteUrl, String queryString,
          Executor threadPool) throws IOException {
    this(remoteUrl, queryString, threadPool, WebUtils.staticWebUtils());
  }
  
  public RemoteQueryRunner(String remoteUrl, String queryString,
          Executor threadPool, WebUtils webUtils) throws IOException {
    this.webUtils = webUtils;
    this.remoteUrl = remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/");

    // submit the remote query
    try {
      this.queryId = (String) webUtils.getObject(
              getActionBaseUrl(ACTION_POST_QUERY_BIN), 
              "queryString", URLEncoder.encode(queryString, "UTF-8"));
    } catch(ClassNotFoundException e) {
      //we were expecting a String but got some object of unknown class
      throw (IOException)new IOException(
          "Was expecting a String query ID value, but got " +
      		"an unknown object type!").initCause(e);
    }
    
    documentStats = new ArrayList<int[]>();
    // create the background thread, and start it.
    this.backgroundThread = new RemoteUpdater();
    if(threadPool != null) {
      threadPool.execute(backgroundThread);
    } else {
      new Thread(backgroundThread, this.getClass().getCanonicalName()
              + " background thread").start();
    }
  }





  public void close() throws IOException {
    webUtils.getVoid(getActionBaseUrl(ACTION_CLOSE),
            "queryId", queryId);
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

  @SuppressWarnings("unchecked")
  public List<Binding> getHits(int startIndex, int hitCount)
          throws IndexOutOfBoundsException {
    if(startIndex < 0) {
      throw new IndexOutOfBoundsException("Negative startIndex: " + startIndex);
    }
    try {
      return (List<Binding>)webUtils.getObject(
             getActionBaseUrl(ACTION_HITS_BIN),
             "queryId", queryId, 
             "startIndex", Integer.toString(startIndex),
             "count", Integer.toString(hitCount));
    } catch(IOException e) {
      throw new RuntimeException(e);
    } catch(ClassNotFoundException e) {
      throw new RuntimeException("Was expecting a list of bindings, but got " +
              "an unknown object type!", e);
    }
  }

  public int getHitsCount() {
    try {
      return webUtils.getInt(getActionBaseUrl(ACTION_HIT_COUNT_BIN),
             "queryId", queryId);
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void getMoreHits() throws IOException {
    webUtils.getVoid(getActionBaseUrl(ACTION_GET_MORE_HITS),
            "queryId", queryId);
    // restart the background thread, if stopped.
    active = true;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isComplete() {
    return complete;
  }

  public void setStageMaxHits(int maxHits) throws IOException{
    webUtils.getVoid(getActionBaseUrl(ACTION_SET_STAGE_MAX_HITS_BIN),
            "queryId", queryId, 
            "maxHits", Integer.toString(maxHits));
  }

  public void setStageTimeout(int timeout) throws IOException {
    webUtils.getVoid(getActionBaseUrl(ACTION_SET_STAGE_TIMEOUT_BIN),
            "queryId", queryId, 
            "timeout", Integer.toString(timeout));    
  }

  public String[][] getDocumentText(int documentId, int termPosition, int length)
          throws IndexException {
    try {
      return (String[][])webUtils.getObject(
              getActionBaseUrl(ACTION_DOC_TEXT_BIN),
              "queryId", queryId,
              "documentId", Integer.toString(documentId),
              "termPosition", Integer.toString(termPosition),
              "length", Integer.toString(length));
    } catch(IOException e) {
      throw new IndexException(e);
    } catch(ClassNotFoundException e) {
      throw new IndexException("Was expecting a bi-dimensional array of" +
      		" Strings, but got an unknown object type!", e);
    }
  }

  public String getDocumentURI(int documentId) throws IndexException {
    try {
      return (String)webUtils.getObject(
              getActionBaseUrl(ACTION_DOC_URI_BIN),
              "queryId", queryId,
              "documentId", Integer.toString(documentId));
    } catch(IOException e) {
      throw new IndexException(e);
    } catch(ClassNotFoundException e) {
      throw new IndexException("Was expecting a String value, but got an " +
      		"unknown object type!", e);
    }
  }
  
  @Override
  public Serializable getDocumentMetadataField(int documentId, String fieldName)
      throws IndexException {
    Set<String> names = new HashSet<String>();
    names.add(fieldName);
    return getDocumentMetadataFields(documentId, names).get(fieldName);
  }

  @Override
  public Map<String, Serializable> getDocumentMetadataFields(int documentId,
      Set<String> fieldNames) throws IndexException {
    try {
      // build a comma-separated value
      StringBuilder namesStr = new StringBuilder();
      boolean first = true;
      for(String aName : fieldNames) {
        if(first) {
          first = false;
        } else {
          namesStr.append(", ");
        }
        namesStr.append(aName.replace(",", "\\,"));
      }
      return (Map<String, Serializable>)webUtils.getObject(
              getActionBaseUrl(ACTION_DOC_MEDATADA_FIELDS_BIN),
              "queryId", queryId,
              "documentId", Integer.toString(documentId),
              "fieldNames", namesStr.toString());
    } catch(IOException e) {
      throw new IndexException(e);
    } catch(ClassNotFoundException e) {
      throw new IndexException("Was expecting a Map<String, Serializable> " +
      		"value, but got an unknown object type!", e);
    }    
  }

  public String getDocumentTitle(int documentID) throws IndexException {
    try {
      return (String)webUtils.getObject(
              getActionBaseUrl(ACTION_DOC_TITLE_BIN),
              "queryId", queryId,
              "documentId", Integer.toString(documentID));
    } catch(IOException e) {
      throw new IndexException(e);
    } catch(ClassNotFoundException e) {
      throw new IndexException("Was expecting a String value, but got an " +
          "unknown object type!", e);
    }  }





  public void renderDocument(int documentId, Appendable out)
          throws IOException, IndexException {
    webUtils.getText(out, getActionBaseUrl(ACTION_RENDER_DOCUMENT),
            "queryId", queryId,
            "documentId", Integer.toString(documentId));
  }

}
