/*
 *  QueryExecutor.java
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
 *  Valentin Tablan, 04 Mar 2009
 *  
 *  $Id$
 */
package gate.mimir.search.query;

import gate.mimir.search.QueryEngine;
import it.unimi.dsi.mg4j.index.Index;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;


/**
 * A query executor is capable of running a query (represented as a tree of 
 * {@link QueryNode}s over a set of indexes. 
 */
public interface QueryExecutor {
  

  /**
   * Gets the next document that contains a match, with a document ID greater 
   * than the ID provided in the <code>from</code> parameter.
   * This method essentially requests the query executor to skip over a number 
   * of documents that are not desired for external reasons (e.g. when running
   * a complex query, other query constraints may have already removed a set of
   * documents from the candidate list). 
   * 
   * The query executor will <b>never</b> roll back: all document IDs returned
   * are in ascending order. This means that if the value provided for the 
   * <code>greaterThan<code> parameter is lower than the latest document ID 
   * returned, it will have no effect. 
   *  
   * @param greaterThan a document ID representing the lowest bound (exclusive) for 
   * the requested document ID.  
   * @return the matching document ID, or <tt>-1</tt> if no more documents match
   * the query. The returned value will be greater than <code>from</code>, or 
   * <code>-1</code> if no more matching documents are found.
   * @throws IOException if the index files cannot be accessed.
   */
  public int nextDocument(int greaterThan) throws IOException;
  

  /**
   * Returns the value returned by the most recent call to 
   * {@link #nextDocument(int)}.
   * @return an int value.
   */
  public int getLatestDocument();
  /**
   * Gets the next matching position, in the document last returned by 
   * {@link #nextDocument()}.  Hits are always returned in increasing order
   * of start offset, but hits that start at the same place may be returned in
   * any order (i.e. not necessarily longest first or shortest first).
   * @return the {@link Binding} corresponding to the root query node for this 
   * executor. This returned binding will also be included in the bindings map.
   * If no further matches are possible on the current document, then 
   * <code>null</code> is returned.
   * @throws IOException if the index files cannot be accessed.  
   */
  public Binding nextHit() throws IOException;
  
  
  /**
   * Closes this {@link QueryExecutor} and releases all resources used.
   * @throws IOException if the index files cannot be accessed.
   */
  public void close() throws IOException;
  
  /**
   * Gets the {@link QueryEngine} that has created this query executor.
   * @return
   */
  public QueryEngine getQueryEngine();
}
