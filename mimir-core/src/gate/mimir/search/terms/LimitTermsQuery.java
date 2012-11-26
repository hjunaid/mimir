/*
 *  LimitTermsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 18 Jul 2012
 *
 *  $Id$
 */
package gate.mimir.search.terms;

import gate.mimir.search.QueryEngine;

import java.io.IOException;

/**
 * A wrapper for another terms query that limit the number of returned terms
 * to a certain value.
 * <br>
 * This is not the same as setting the {@link AbstractTermsQuery#limit} 
 * parameter for an {@link AbstractTermsQuery} implementation because, in this 
 * case,  the limit is applied <strong>after</strong> the execution of the 
 * wrapped query has completed. This allows for example to wrap a query into a
 * {@link SortedTermsQuery} (to change the results order) and 
 * <strong>then</strong> limit the number of results. 
 */
public class LimitTermsQuery extends AbstractTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -2853628566995944376L;
  
  protected TermsQuery query;
  
  
  public LimitTermsQuery(TermsQuery query, int limit) {
    super(query.isStringsEnabled(), query.isCountsEnabled(), limit);
    this.query = query;
  }


  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    TermsResultSet trs = query.execute(engine);
    if(trs.termIds.length > limit) {
      long[] termIds = new long[limit];
      System.arraycopy(trs.termIds, 0, termIds, 0, limit);
      int[] termCounts = null;
      if(trs.termCounts != null) {
        termCounts = new int[limit];
        System.arraycopy(trs.termCounts, 0, termCounts, 0, limit);
      }
      String[] termStrings = null;
      if(trs.termStrings != null) {
        termStrings = new String[limit];
        System.arraycopy(trs.termStrings, 0, termStrings, 0, limit);
      }
      int[] termLengths = null;
      if(trs.termLengths != null) {
        termLengths = new int[limit];
        System.arraycopy(trs.termLengths, 0, termLengths, 0, limit);
      }
      return new TermsResultSet(termIds, termStrings, termLengths, termCounts);
    } else {
      return trs;  
    }
  }
}
