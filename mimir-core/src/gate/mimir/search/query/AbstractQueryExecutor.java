/*
 *  AbstractQueryExecutor.java
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
