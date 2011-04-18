/*
 *  ContainsQuery.java
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
 *  Valentin Tablan, 20 Mar 2009
 *  $Id$
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;


/**
 * Filtering query that matches hits from the target query that
 * contain a hit of the filter query, i.e. any
 * X that has a Y within it.
 */
public class ContainsQuery extends AbstractOverlapQuery {

  private static final long serialVersionUID = -3152202241528149456L;

  public ContainsQuery(QueryNode outerQuery, QueryNode innerQuery) {
    super(innerQuery,  outerQuery);
  }

  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new AbstractOverlapQuery.OverlapQueryExecutor(this, engine, 
            SubQuery.OUTER);
  }
  
  public String toString(){
    return "CONTAINS (\nOUTER:" + outerQuery.toString() + ",\nINNER:" + 
        innerQuery.toString() +"\n)";
  }
}
