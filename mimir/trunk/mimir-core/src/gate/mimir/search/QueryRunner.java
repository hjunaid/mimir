/*
 *  QueryRunner.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin tablan, 16 Dec 2009
 *  
 *  $Id$
 */
package gate.mimir.search;

import gate.mimir.DocumentMetadataHelper;
import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A QueryRunner object can be used to execute a query in a separate thread. The
 * query execution will be performed in stages, each being limited to a given 
 * number of maximum hits or a set timeout. During the query execution 
 * statistics are made available about the number of hits currently obtained for
 * each document. The hits already collected can be accessed in a random 
 * fashion.
 * 
 * N.B. Documents are referred to in two different manners: 
 * <ul>
 *   <li>by documentId: the ID associated with the document at indexing time, 
 *   which never changes</li>
 *   <li>by index: the index in the list of documents found to contain hits.</li>
 * </ul>
 * You can tell which addressing scheme is used based on the of the parameter.
 * 
 * Implementations of this interface must be thread-safe!
 */
public interface QueryRunner {
  
  /**
   * The default number of hits obtained in one search stage.
   */
  public static final int DEFAULT_MAX_HITS = 1000000;
  
  
  /**
   * The default maximum amount of time (in milliseconds) used for one search 
   * stage.
   */
  public static final int DEFAULT_TIMEOUT = 30000;
  
  /**
   * Starts a new search stage, obtaining more results. Executes the query 
   * asynchronously until a set number of hits are obtained, or a given 
   * period is elapsed.
   * 
   * If the query execution is currently in progress (i.e. a previous search 
   * stage has not yet finished), this call will have no effect. If a previous 
   * query execution stage has ended, this call will cause the search to 
   * restart, accumulating more hits. If the search had finished, this call 
   * will have no effect.
   *  
   * @see #setMaxHits(int)
   * @see #setTimeout(int)
   * @see #isActive()
   * @see #isComplete()
   *  
   * @throws IOException
   */
  public void getMoreHits() throws IOException;
  
  /**
   * Sets the maximum number of hits to be obtain in one search stage. If not 
   * set, the {@link #DEFAULT_MAX_HITS default value} is used.
   * If the value provided is negative, then no limit is imposed on the number 
   * of hits obtained.
   *  
   * @param the maximum number of hits to be obtained by the next call to 
   * {@link #getMoreHits()}.
   * @throws IOException if the communication with the query runner 
   * implementation fails.
   */
  public void setStageMaxHits(int maxHits) throws IOException;
  
  /**
   * Sets the maximum amount of time to be used for one search stage. If not 
   * set, the {@link #DEFAULT_TIMEOUT default value} is used. If the value 
   * provided is negative, then no limit is imposed on the amount of time spent.
   * 
   * @param timeout the maximum amount of time (in milliseconds) to be used by 
   * the next call to {@link #getMoreHits()}. 
   * @throws IOException if the communication with the query runner 
   * implementation fails.
   */
  public void setStageTimeout(int timeout) throws IOException;
  
  /**
   * Gets the number of hits obtained so far.
   * This number may increase at any time if the query is currently
   * {@link #isActive() active}.
   * @return an int value, representing the number of hits.
   */
  public int getHitsCount();
  
  /**
   * Gets the number of distinct documents found to contain hits so far.
   * This number may increase at any time if the query is currently
   * {@link #isActive() active}.
   * @return an int value, representing the number of distinct documents.
   */
  public int getDocumentsCount();
  
  /**
   * Gets the ID of a document found to contain hits.
   * @param index the index of the desired document in the list of documents. 
   * This should be a value between 0 and {@link #getDocumentsCount()} -1.
   *  
   * @return an int value, representing the ID of the requested document.
   * @throws IndexOutOfBoundsException is the index provided is less than zero, 
   * or greater than {@link #getDocumentsCount()} -1.
   */
  public int getDocumentID(int index) throws IndexOutOfBoundsException;
  
  /**
   * Gets the number of hits for one of the documents found to contain hits.
   * Note that for the <i>last</i> document this number is a lower bound - there
   * may still be more hits to be found in this document (unless
   * {@link #isComplete()} returns true).
   * 
   * @param index the index of the desired document in the list of documents. 
   * This should be a value between 0 and {@link #getDocumentsCount()} -1.
   *  
   * @return an int value, representing the number of hits on the requested 
   * document.
   * @throws IndexOutOfBoundsException is the index provided is less than zero, 
   * or greater than {@link #getDocumentsCount()} -1.
   */
  public int getDocumentHitsCount(int index) throws IndexOutOfBoundsException;
  
  /**
   * Gets a subset of the hits obtained so far.  If the set of hits requested
   * does not exist (e.g. if the startIndex is too large) then an empty
   * list will be returned.
   * 
   * @param startIndex the index of the first requested hit.
   * @param hitCount the maximum number of hits to be returned (fewer hits will 
   * be returned if there aren't enough available).
   * @return a list of maximum hitCount hits.
   * @throws IndexOutOfBoundsException if startIndex is negative.
   */
  public List<Binding> getHits(int startIndex, int hitCount)
      throws IndexOutOfBoundsException;
  
  /**
   * Gets all the hits for a given document.
   * @param documentId the ID of the document for which the hits are being 
   * requested.
   * @return a list of hits
   * @throws IndexOutOfBoundsException
   */
  public List<Binding> getHitsForDocument(int documentId)
      throws IndexOutOfBoundsException;
  
  /**
   * Render the content of the given document, with the hits for this query
   * highlighted.
   * @param documentId
   * @param out
   * @throws IOException if the output cannot be written to.
   * @throws IndexException if no document renderer is available.
   */
  public void renderDocument(int documentId, Appendable out) throws IOException, 
      IndexException;
  
  
  /**
   * Obtains the URI for a given document (specified by its ID).
   * @param documentID
   * @return
   * @throws IndexException if the document URI cannot be retrieved from the 
   * index.
   */
  public String getDocumentURI(int documentID) throws IndexException;
  
  /**
   * Obtains the title for a given document (specified by its ID).
   * @param documentID
   * @return
   * @throws IndexException if the document title cannot be retrieved from the 
   * index.
   */
  public String getDocumentTitle(int documentID) throws IndexException;
  
  /**
   * Obtains an arbitrary document metadata field from the stored document data.
   * {@link DocumentMetadataHelper}s used at indexing time can add arbitrary 
   * {@link Serializable} values as metadata fields for the documents being
   * indexed. This method is used at search time to retrieve those values. 
   *  
   * @param docID the ID of document for which the metadata is sought.
   * @param fieldName the name of the metadata fields to be obtained
   * @return the de-serialised value stored at indexing time for the given 
   * field name and document.
   * @throws IndexException
   */  
  public Serializable getDocumentMetadataField(int docID, String fieldName) 
      throws IndexException;
  
  /**
   * Obtains a set of arbitrary document metadata fields from the stored 
   * document data.
   * {@link DocumentMetadataHelper}s used at indexing time can add arbitrary 
   * {@link Serializable} values as metadata fields for the documents being
   * indexed. This method is used at search time to retrieve those values. 
   *  
   * @param docID the ID of document for which the metadata is sought.
   * @param fieldNames the names of the metadata fields to be obtained
   * @return the de-serialised values stored at indexing time for the given 
   * field names and document (as a Map from field name to filed value).
   * @throws IndexException
   */  
  public Map<String, Serializable> getDocumentMetadataFields(int docID, 
      Set<String> fieldNames) throws IndexException;  
  
  /**
   * Gets a segment of the document text for a given document. 
   * @param documentID
   * @param startToken
   * @param length
   * @return
   * @throws IndexException if the document text cannot be retrieved from the 
   * index.
   */
  public String[][] getDocumentText(int documentID, int termPosition, 
          int length) throws IndexException;
  
  /**
   * Checks whether a search stage is currently active.
   * @return <code>true</code> iff more hits are currently being sought (a 
   * search stage has started and not finished yet)
   */
  public boolean isActive();
  
  /**
   * Checks whether all the available hits have been obtained. When this returns
   * <code>true</code>, further calls to {@link #getMoreHits()} will have no 
   * effect. 
   * @return <code>true</code> after all the possible hits have been obtained.
   */
  public boolean isComplete();
  
  /**
   * Closes this {@link QueryExecutor} and releases all resources used.
   * @throws IOException if the index files cannot be accessed.
   */
  public void close() throws IOException;
}
