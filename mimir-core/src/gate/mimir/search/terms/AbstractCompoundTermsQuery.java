/*
 *  AbstractCompoundTermsQuery.java
 *
 *  Copyright (c) 2007-2012, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 27 Nov 2012
 *
 *  $Id$
 */
package gate.mimir.search.terms;

import java.io.IOException;

import gate.creole.annic.apache.lucene.search.TermQuery;
import gate.mimir.search.QueryEngine;

/**
 * Abstract base class for {@link TermQuery} implementations that wrap a group
 * of {@link TermQuery} sub-queries.
 */
public abstract class AbstractCompoundTermsQuery extends AbstractTermsQuery 
    implements CompoundTermsQuery{

  /**
   * The wrapped wrappedQuery
   */
  protected TermsQuery[] subQueries;
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -6582029649819116753L;

  public AbstractCompoundTermsQuery(TermsQuery... subQueries) {
    this.subQueries = subQueries;
  }
  
  /**
   * @return the subQueries
   */
  @Override
  public TermsQuery[] getSubQueries() {
    return subQueries;
  }

  /**
   * Executes each sub-query and then calls {@link #combine(TermsResultSet...)}
   * passing the array of {@link TermsResultSet} values thus produced.
   * This implementation is marked final, forcing the sub-classes to place
   * their logic in {@link #combine(TermsResultSet...)}.  
   */
  @Override
  public final TermsResultSet execute(QueryEngine engine) throws IOException {
    TermsResultSet[] resSets = new TermsResultSet[subQueries.length];
    for(int i = 0; i < subQueries.length; i++) {
      resSets[i] = subQueries[i].execute(engine);
    }
    return combine(resSets);
  }
}
