/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  Valentin Tablan, 16 Dec 2009
 *
 *  $Id$
 */
package gate.mimir.search;



import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;

import java.io.IOException;
import java.util.List;



/**
 * A QueryRunner object can be used to execute a query in a separate thread. The
 * query execution will be performed in stages, each being limited to a given 
 * number of maximum hits or a set timeout. During the query execution 
 * statistics are made available about the number of hits currently obtained for
 * each document. The hits already collected can be accessed in a random 
 * fashion.
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
   * query execution has completed, this call will cause it to restart, 
   * accumulating more hits. 
   *  
   * @see #setMaxHits(int)
   * @see #setTimeout(int)
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