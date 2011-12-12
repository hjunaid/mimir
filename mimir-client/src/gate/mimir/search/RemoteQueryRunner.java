/*
 *  RemoteQueryRunner.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 08 Dec 2011
 *  
 *  $Id$
 */
package gate.mimir.search;

import gate.mimir.index.IndexException;
import gate.mimir.index.mg4j.zipcollection.DocumentData;
import gate.mimir.search.query.Binding;
import gate.mimir.tool.WebUtils;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;

import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

/**
 * A {@link QueryRunner} implementation that proxies a QueryRunner running on
 * a remote Mímir server.  
 */
public class RemoteQueryRunner implements QueryRunner {
  
  protected static final String SERVICE_SEARCH = "search";
  
  protected static final String ACTION_POST_QUERY_BIN = "postQueryBin";
  
  protected static final String ACTION_CURRENT_DOC_COUNT_BIN = "documentsCurrentCountBin";
  
  protected static final String ACTION_DOC_COUNT_BIN = "documentsCountBin";
  
  protected static final String ACTION_DOC_ID_BIN = "documentIdBin";
  
  protected static final String ACTION_DOC_HITS_BIN = "documentHitsBin";
  
  protected static final String ACTION_DOC_SCORES_BIN = "documentsScoresBin";
  
  protected static final String ACTION_DOC_DATA_BIN = "documentDataBin";
  
  protected static final String ACTION_RENDER_DOCUMENT = "renderDocument";
  
  protected static final String ACTION_CLOSE = "close";
  
  /**
   * The maximum number of documents to be stored in the local document cache.
   */
  protected static final int DOCUMENT_CACHE_SIZE = 1000;
  
  /**
   * Action run in a background thread, used to update the document data 
   * (document ID, document score) from the remote endpoint.
   * This runs once,  started during the creation of the query runner.
   */
  protected class DocumentDataUpdater implements Runnable {
    @Override
    public void run() {
      int failuresAllowed = 10;
      // wait for the first pass to complete
      while(documentsCount < 0) {
        if(closed) return;
        // update document counts
        try {
          int newDocumentsCount = webUtils.getInt(
              getActionBaseUrl(ACTION_DOC_COUNT_BIN), "queryId", 
              URLEncoder.encode(queryId, "UTF-8"));
          if(newDocumentsCount < 0) {
            // still not finished-> update current count
            currentDocumentsCount = webUtils.getInt(
              getActionBaseUrl(ACTION_CURRENT_DOC_COUNT_BIN), "queryId", 
              URLEncoder.encode(queryId, "UTF-8"));
            // ... and wait a while before asking again
            Thread.sleep(500);
          } else {
            // remote side has finished enumerating all documents
            // download all the scores in one go.
            double[] docScores = (double[]) webUtils.getObject(
              getActionBaseUrl(ACTION_DOC_SCORES_BIN), "queryId", 
              URLEncoder.encode(queryId, "UTF-8"));
            if(docScores != null && docScores.length > 0) {
              documentScores = new DoubleArrayList(docScores);
              if(documentScores.size() != newDocumentsCount) {
                // malfunction
                exceptionInBackgroundThread = new RuntimeException(
                  "Incorrect number of document scores from remote side: " +
                  "was expecting " + newDocumentsCount + " but got " +
                  documentScores.size() +"!");
                return;
              }              
            } else {
              // we're not scoring
              documentScores = null;
            }
            // ...and we're done!
            documentsCount = newDocumentsCount;
          }
        } catch(IOException e) {
          if(failuresAllowed > 0) {
            failuresAllowed --;
            logger.error("Exception while obtaining remote document data (will retry)", e);
            try {
              Thread.sleep(100);
            } catch(InterruptedException e1) {
              Thread.currentThread().interrupt();
            }
          } else {
            logger.error("Exception while obtaining remote document data.", e);
            exceptionInBackgroundThread = e;
            return;
          }
        } catch(ClassNotFoundException e) {
          if(failuresAllowed > 0) {
            failuresAllowed --;
            logger.error("Exception while obtaining remote document data (will retry)", e);
            try {
              Thread.sleep(100);
            } catch(InterruptedException e1) {
              Thread.currentThread().interrupt();
            }
          } else {
            logger.error("Exception while obtaining remote document data.", e);
            exceptionInBackgroundThread = e;
            return;
          }
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
          logger.warn("Interrupted while waiting", e);
        }
      }
    }
  }
  
  
  /**
   * A cache of MG4J {@link Document}s used for returning the hit text.
   */
  private Int2ObjectLinkedOpenHashMap<DocumentData> documentCache;
  
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
   * The total number of result documents (or -1 if not yet known).
   */
  private volatile int documentsCount;
  
  private volatile boolean closed;
  
  /**
   * The current number of documents. After all documents have been retrieved, 
   * this value is identical to {@link #documentsCount}. 
   */
  private int currentDocumentsCount;
  
  /**
   * Shared Logger
   */
  private static Logger logger = Logger.getLogger(RemoteQueryRunner.class);
  
  
  /**
   * If the background thread encounters an exception, it
   * will save it here. As the background thread cannot report it itself, it is
   * the job of any of the interactive methods to report it.
   */
  private Exception exceptionInBackgroundThread;
  
  /**
   * If scoring is enabled ({@link #scorer} is not <code>null</code>), this list
   * contains the scores for the documents found to contain hits. This list is 
   * aligned to {@link #documentIds}.   
   */
  protected DoubleArrayList documentScores;
  
  
  public RemoteQueryRunner(String remoteUrl, String queryString, 
      Executor threadSource,  WebUtils webUtils) throws IOException {
    this.remoteUrl = remoteUrl.endsWith("/") ? remoteUrl : (remoteUrl + "/");
    this.webUtils = webUtils;
    this.closed = false;
    // submit the remote query
    try {
      this.queryId = (String) webUtils.getObject(
              getActionBaseUrl(ACTION_POST_QUERY_BIN), 
              "queryString", 
              URLEncoder.encode(queryString, "UTF-8"));
    } catch(ClassNotFoundException e) {
      //we were expecting a String but got some object of unknown class
      throw (IOException) new IOException(
          "Was expecting a String query ID value, but got " +
          "an unknown object type!").initCause(e);
    }
    
    // init the cache
    documentCache = new Int2ObjectLinkedOpenHashMap<DocumentData>();    
    
    // start the background action
    documentsCount = -1;
    currentDocumentsCount = 0;
    DocumentDataUpdater docDataUpdater = new DocumentDataUpdater(); 
    if(threadSource != null) {
      threadSource.execute(docDataUpdater);
    } else {
      new Thread(docDataUpdater, 
        DocumentDataUpdater.class.getCanonicalName()).start();
    }
  }

  protected String getActionBaseUrl(String action) throws IOException{
    //this method is always called from interactive methods, that are capable of
    //reporting errors to the user. So we use this place to check if the 
    //background thread had any problems, and report them if so.
    if(exceptionInBackgroundThread != null){
      Exception e = exceptionInBackgroundThread;
      exceptionInBackgroundThread = null;
      throw (IOException)new IOException(
          "Problem communicating with the remote index", e);
    }
    
    //an example URL looks like this:
    //http://localhost:8080/mimir/bf25398f-f087-4224-bfa6-c2ef00399c04/search/hitCountBin?queryId=c4da799e-9ca2-46ae-8ded-30bdc37ad607
    StringBuilder str = new StringBuilder(remoteUrl);
    str.append(SERVICE_SEARCH);
    str.append('/');
    str.append(action);
    return str.toString();
  }

  /**
   * Gets (from the cache, or from the remote endpoint) the {@link DocumentData}
   * for the document at the specified rank.
   * @param rank
   * @return
   * @throws IndexException
   * @throws IndexOutOfBoundsException
   * @throws IOException
   */
  protected DocumentData getDocumentData(int rank) throws IndexException, 
      IndexOutOfBoundsException, IOException {
    DocumentData docData = documentCache.getAndMoveToFirst(rank);
    if(docData == null) {
      // cache miss -> remote retrieve
      try {
        docData = (DocumentData)webUtils.getObject(
            getActionBaseUrl(ACTION_DOC_DATA_BIN),
            "queryId",  URLEncoder.encode(queryId, "UTF-8"),
            "documentRank", Integer.toString(rank));
        documentCache.putAndMoveToFirst(rank, docData);
        if(documentCache.size() > DOCUMENT_CACHE_SIZE) {
          // reduce size
          documentCache.removeLast();
        }
      } catch(IOException e) {
        throw new IndexException(e);
      } catch(ClassNotFoundException e) {
        throw new IndexException("Was expecting a DocumentData value, " +
        		"but got an unknown object type!", e);
      }
    }
    return docData;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCount()
   */
  @Override
  public int getDocumentsCount() {
    return documentsCount;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getCurrentDocumentsCount()
   */
  @Override
  public int getDocumentsCurrentCount() {
    return (documentsCount < 0) ? currentDocumentsCount : documentsCount;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentID(int)
   */
  @Override
  public int getDocumentID(int rank) throws IndexOutOfBoundsException,
          IOException {
    return webUtils.getInt(getActionBaseUrl(ACTION_DOC_ID_BIN), 
            "queryId", URLEncoder.encode(queryId, "UTF-8"), 
            "documentRank", Integer.toString(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentScore(int)
   */
  @Override
  public double getDocumentScore(int rank) throws IndexOutOfBoundsException,
          IOException {
    if(documentsCount < 0) {
      // premature call
      throw new IndexOutOfBoundsException(
        "Score requested before collection of documents has completed.");
    }
    return documentScores != null ? documentScores.get(rank) : DEFAULT_SCORE;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentHits(int)
   */
  @SuppressWarnings("unchecked")
  @Override
  public List<Binding> getDocumentHits(int rank)
          throws IndexOutOfBoundsException, IOException {
    try {
      return (List<Binding>)webUtils.getObject(
        getActionBaseUrl(ACTION_DOC_HITS_BIN),
        "queryId", queryId,
        "documentRank", Integer.toString(rank));
    } catch(ClassNotFoundException e) {
      throw new RuntimeException("Got wrong value type from remote endpoint", 
        e);
    }
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentText(int, int, int)
   */
  @Override
  public String[][] getDocumentText(int rank, int termPosition, int length)
          throws IndexException, IndexOutOfBoundsException, IOException {
    return getDocumentData(rank).getText(termPosition, length);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentURI(int)
   */
  @Override
  public String getDocumentURI(int rank) throws IndexException,
          IndexOutOfBoundsException, IOException {
    return getDocumentData(rank).getDocumentURI();
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentTitle(int)
   */
  @Override
  public String getDocumentTitle(int rank) throws IndexException,
          IndexOutOfBoundsException, IOException {
    return getDocumentData(rank).getDocumentTitle();
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataField(int, java.lang.String)
   */
  @Override
  public Serializable getDocumentMetadataField(int rank, String fieldName)
          throws IndexException, IndexOutOfBoundsException, IOException {
    return getDocumentData(rank).getMetadataField(fieldName);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataFields(int, java.util.Set)
   */
  @Override
  public Map<String, Serializable> getDocumentMetadataFields(int rank,
      Set<String> fieldNames) throws IndexException, IndexOutOfBoundsException, 
      IOException {
    Map<String, Serializable> res = new HashMap<String, Serializable>();
    int docId = getDocumentID(rank);
    for(String fieldName : fieldNames) {
      Serializable value = getDocumentMetadataField(docId, fieldName);
      if(value != null) res.put(fieldName, value);
    }
    return res;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#renderDocument(int, java.lang.Appendable)
   */
  @Override
  public void renderDocument(int rank, Appendable out) throws IOException,
          IndexException {
    webUtils.getText(out, getActionBaseUrl(ACTION_RENDER_DOCUMENT),
            "queryId", queryId,
            "documentRank", Integer.toString(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#close()
   */
  @Override
  public void close() throws IOException {
    webUtils.getVoid(getActionBaseUrl(ACTION_CLOSE),
            "queryId", queryId);
    closed = true;
    documentCache.clear();
  }
}
