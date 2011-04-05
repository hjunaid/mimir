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
