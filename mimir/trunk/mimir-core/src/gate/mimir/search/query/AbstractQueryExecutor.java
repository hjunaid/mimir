/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licensed under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 4 Mar 2009
 *
 *  $Id$
 */
package gate.mimir.search.query;

import gate.mimir.search.QueryEngine;

import java.io.IOException;


/**
 * A parent class for all query executors, containing some common functionality.
 */
public abstract class AbstractQueryExecutor implements QueryExecutor{
  
  
  /**
   * The latest document ID returned by a call to nextDocument.  Initially this 
   * value is set to -2, will be -1 if there are no more hits.
   */
  protected int latestDocument;
    
  /**
   * Flag to mark whether the executor has been closed.
   */
  protected boolean closed = false;
  
  /**
   * The {@link QueryEngine} in which we run.
   */
  protected QueryEngine engine;
  
  
  protected AbstractQueryExecutor(QueryEngine engine){
    this.engine = engine;
    latestDocument = -2;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryExecutor#getLatestDocument()
   */
  public int getLatestDocument() {
    return latestDocument;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryExecutor#close()
   */
  public void close() throws IOException {
    closed = true;
  }

  public QueryEngine getQueryEngine() {
    return engine;
  }
}
