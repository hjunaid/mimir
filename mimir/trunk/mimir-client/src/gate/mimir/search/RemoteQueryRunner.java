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
 *  Valentin Tablan, 05 Jan 2010
 *  
 *  $Id$
 */
package gate.mimir.search;

import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;
import gate.mimir.tool.WebUtils;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * A {@link QueryRunner} implementation that proxies a QueryRunner running on
 * a remote Mímir server.  
 */
public class RemoteQueryRunner implements QueryRunner {
  
  protected static final String ACTION_RENDER_DOCUMENT = "renderDocument";

  protected static final String SERVICE_SEARCH = "search";
  
  protected static final String ACTION_DOC_TEXT_BIN = "docTextBin";

  protected static final String ACTION_DOC_URI_BIN = "docURIBin";
  
  protected static final String ACTION_DOC_TITLE_BIN = "docTitleBin";
  
  protected static final String ACTION_DOC_MEDATADA_FIELDS_BIN = "docMetadataFieldsBin";
  
  protected static final String ACTION_CLOSE = "close";
  
  
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
   * The document IDs for the documents found to contain hits. This list is
   * sorted in ascending documentID order.
   */
  protected IntList documentIds;
  
  /**
   * If scoring is enabled ({@link #scorer} is not <code>null</code>), this list
   * contains the scores for the documents found to contain hits. This list is 
   * aligned to {@link #documentIds}.   
   */
  protected DoubleArrayList documentScores;
  
  /**
   * The sets of hits for each returned document. This data structure is lazily 
   * built, so some elements may be null. 
   */
  protected ObjectList<List<Binding>> documentHits;
  
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
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCount()
   */
  @Override
  public int getDocumentsCount() {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getCurrentDocumentsCount()
   */
  @Override
  public int getCurrentDocumentsCount() {
    return documentIds.size();
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentID(int)
   */
  @Override
  public int getDocumentID(int rank) throws IndexOutOfBoundsException,
          IOException {
    return documentIds.get(rank);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentScore(int)
   */
  @Override
  public double getDocumentScore(int rank) throws IndexOutOfBoundsException,
          IOException {
    return documentScores.get(rank);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentHits(int)
   */
  @Override
  public List<Binding> getDocumentHits(int rank)
          throws IndexOutOfBoundsException, IOException {
    return documentHits.get(rank);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentText(int, int, int)
   */
  @Override
  public String[][] getDocumentText(int rank, int termPosition, int length)
          throws IndexException, IndexOutOfBoundsException, IOException {
    try {
      return (String[][])webUtils.getObject(
              getActionBaseUrl(ACTION_DOC_TEXT_BIN),
              "queryId", queryId,
              "documentRank", Integer.toString(rank),
              "termPosition", Integer.toString(termPosition),
              "length", Integer.toString(length));
    } catch(IOException e) {
      throw new IndexException(e);
    } catch(ClassNotFoundException e) {
      throw new IndexException("Was expecting a bi-dimensional array of" +
          " Strings, but got an unknown object type!", e);
    }    
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentURI(int)
   */
  @Override
  public String getDocumentURI(int rank) throws IndexException,
          IndexOutOfBoundsException, IOException {
    try {
      return (String)webUtils.getObject(
              getActionBaseUrl(ACTION_DOC_URI_BIN),
              "queryId", queryId,
              "documentRank", Integer.toString(rank));
    } catch(IOException e) {
      throw new IndexException(e);
    } catch(ClassNotFoundException e) {
      throw new IndexException("Was expecting a String value, but got an " +
          "unknown object type!", e);
    }
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentTitle(int)
   */
  @Override
  public String getDocumentTitle(int rank) throws IndexException,
          IndexOutOfBoundsException, IOException {
    try {
      return (String)webUtils.getObject(
              getActionBaseUrl(ACTION_DOC_TITLE_BIN),
              "queryId", queryId,
              "documentRank", Integer.toString(rank));
    } catch(IOException e) {
      throw new IndexException(e);
    } catch(ClassNotFoundException e) {
      throw new IndexException("Was expecting a String value, but got an " +
          "unknown object type!", e);
    }
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataField(int, java.lang.String)
   */
  @Override
  public Serializable getDocumentMetadataField(int rank, String fieldName)
          throws IndexException, IndexOutOfBoundsException, IOException {
    Set<String> names = new HashSet<String>();
    names.add(fieldName);
    return getDocumentMetadataFields(rank, names).get(fieldName);    
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataFields(int, java.util.Set)
   */
  @Override
  public Map<String, Serializable> getDocumentMetadataFields(int rank,
          Set<String> fieldNames) throws IndexException,
          IndexOutOfBoundsException, IOException {
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
              "documentRank", Integer.toString(rank),
              "fieldNames", namesStr.toString());
    } catch(IOException e) {
      throw new IndexException(e);
    } catch(ClassNotFoundException e) {
      throw new IndexException("Was expecting a Map<String, Serializable> " +
          "value, but got an unknown object type!", e);
    }    
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
  }
}
