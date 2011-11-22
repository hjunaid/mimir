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
 *  Valentin Tablan, 22 Nov 2011
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
 * A QueryRunner is used to manage the execution of the query (supplied as a
 * {@link QueryExecutor}). Implementations may use a background thread to 
 * pre-fetch data which they then make available through the public API. 
 * 
 * All references to documents are made by rank, i.e. the position of the 
 * document in the list of results.
 * 
 * Unless there is a good reason not to (e.g. results ranking), the documents 
 * will be returned in increasing documentID order.
 * 
 * QueryRunners that perform ranking will re-order the result list so that 
 * documents are returned in decreasing score order. 
 */
public interface QueryRunner {
  /**
   * Gets the number of result documents.
   * @return <code>-1</code> if the search has not yet completed, the total 
   * number of result document otherwise. 
   */
  public int getDocumentsCount();

  /**
   * Gets the number of result documents found so far. After the search 
   * completes, the result returned by this call is identical to that of 
   * {@link #getDocumentsCount()}. 
   * @return the number of result documents known so far.
   */
  public int getCurrentDocumentsCount();

  /**
   * Gets the ID of a result document.
   * @param rank the index of the desired document in the list of documents. 
   * This should be a value between 0 and {@link #getDocumentsCount()} -1.
   *  
   * If the requested document position has not yet been ranked (i.e. we know 
   * there is a document at that position, but we don't yet know which one) then 
   * the necessary ranking is performed before this method returns. 
   *
   * @return an int value, representing the ID of the requested document.
   * @throws IndexOutOfBoundsException is the index provided is less than zero, 
   * or greater than {@link #getDocumentsCount()} -1.
   * @throws IOException 
   */
  public int getDocumentID(int rank) throws IndexOutOfBoundsException,
          IOException;

  /**
   * Retrieves the hits within a given result document.
   * @param rank the index of the desired document in the list of documents.
   * This should be a value between 0 and {@link #getDocumentsCount()} -1.
   * 
   * This method call waits until the requested data is available before 
   * returning (document hits are being collected by a background thread).
   * 
   * @return
   * @throws IOException 
   * @throws IndexOutOfBoundsException 
   */
  public List<Binding> getDocumentHits(int rank)
          throws IndexOutOfBoundsException, IOException;

  /**
   * Gets a segment of the document text for a given document.
   * @param rank the rank of the requested document.
   * @param termPosition the first term requested.
   * @param length the number of terms requested.
   * @return two parallel String arrays, one containing term text, the other 
   * containing the spaces in between. The first term is results[0][0], the 
   * space following it is results[1][0], etc.
   * 
   * @throws IndexException
   * @throws IndexOutOfBoundsException
   * @throws IOException
   */
  public String[][] getDocumentText(int rank, int termPosition, int length)
          throws IndexException, IndexOutOfBoundsException, IOException;

  /**
   * Obtains the URI for a given document.
   * @param rank the rank for the requested document.
   * @return the URI provided at indexing time for the document.
   * @throws IndexException
   * @throws IndexOutOfBoundsException
   * @throws IOException
   */
  public String getDocumentURI(int rank) throws IndexException,
          IndexOutOfBoundsException, IOException;

  /**
   * Obtains the title for a given document.
   * @param rank the rank of the requested document.
   * @return the document title (provided at indexing time).
   * @throws IndexException
   * @throws IndexOutOfBoundsException
   * @throws IOException
   */
  public String getDocumentTitle(int rank) throws IndexException,
          IndexOutOfBoundsException, IOException;

  /**
   * Obtains an arbitrary document metadata field from the stored document data.
   * {@link DocumentMetadataHelper}s used at indexing time can add arbitrary 
   * {@link Serializable} values as metadata fields for the documents being
   * indexed. This method is used at search time to retrieve those values.
   * 
   * @param rank the rank for the requested document.
   * @param fieldName the field name for which the value is sought.
   * @return
   * @throws IndexException
   * @throws IndexOutOfBoundsException
   * @throws IOException
   */
  public Serializable getDocumentMetadataField(int rank, String fieldName)
          throws IndexException, IndexOutOfBoundsException, IOException;

  /**
   * Obtains a set of arbitrary document metadata fields from the stored 
   * document data.
   * {@link DocumentMetadataHelper}s used at indexing time can add arbitrary 
   * {@link Serializable} values as metadata fields for the documents being
   * indexed. This method is used at search time to retrieve those values.
   * 
   * @param rank the rank for the requested document.
   * @param fieldNames the names of the metadata fields for which the values are 
   * requested.
   * @return a {@link Map} linking field names with their values.
   * @throws IndexException
   * @throws IndexOutOfBoundsException
   * @throws IOException
   */
  public Map<String, Serializable> getDocumentMetadataFields(int rank,
          Set<String> fieldNames) throws IndexException,
          IndexOutOfBoundsException, IOException;

  /**
   * Render the content of the given document, with the hits for this query
   * highlighted.
   * 
   * @param rank the rank for the requested document.
   * @param out an {@link Appendable} to which the output is written.
   * @throws IOException
   * @throws IndexException
   */
  public void renderDocument(int rank, Appendable out) throws IOException,
          IndexException;

  /**
   * Closes this {@link QueryExecutor} and releases all resources used.
   * @throws IOException
   */
  public void close() throws IOException;
}