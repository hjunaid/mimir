/*
 *  QueryNode.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 03 Mar 2009
 *  
 *  $Id$
 */

package gate.mimir.search.query;

import gate.mimir.search.QueryEngine;
import it.unimi.dsi.big.mg4j.index.Index;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;


/**
 * Top level interface for all types of query nodes. A query object specifies 
 * a set of restrictions that need to be matched against the index. In the 
 * simplest case, this comprises a term and an index name. More complex queries
 * are usually constructed by combining simpler ones.
 */
public interface QueryNode extends Serializable {
  
  /**
   * Obtains a {@link QueryExecutor} appropriate for this query node. Each call
   * to this method will return a new {@link QueryExecutor}.
   * @param indexes the indexes to be searched, represented as a map from index 
   * name to index. 
   * @return an appropriate {@link QueryExecutor}.
   * @throws IOException if the index files cannot be accessed.
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine) 
      throws IOException;
}
