/*
 *  WithinQuery.java
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
 *  Ian Roberts, 13 Mar 2009
 *
 *  $Id$
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import java.io.IOException;


/**
 * Filtering query that matches hits from the target query that
 * are contained inside a hit of the filter query, i.e. any
 * X that occurs within a Y.
 */
public class WithinQuery extends AbstractOverlapQuery {

  private static final long serialVersionUID = 7820023079040779064L;

  public WithinQuery(QueryNode innerQuery, QueryNode outerQuery) {
    super(innerQuery, outerQuery);
  }

  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new AbstractOverlapQuery.OverlapQueryExecutor(
            this, engine, SubQuery.INNER);
  }

  public String toString(){
    return "WITHIN (\nOUTER:" + outerQuery.toString() + ",\nINNER:" + 
        innerQuery.toString() +"\n)";
  }

}
