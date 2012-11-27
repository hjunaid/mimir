/*
 *  AbstractWrapperTermsQuery.java
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

import gate.creole.annic.apache.lucene.search.TermQuery;

/**
 * Abstract base class for {@link TermQuery} implementations that wrap another
 * {@link TermQuery} instance.
 */
public abstract class AbstractWrapperTermsQuery extends AbstractTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -6582029649819116753L;

  public AbstractWrapperTermsQuery(TermsQuery wrappedQuery) {
    this(wrappedQuery, NO_LIMIT);
  }

  public AbstractWrapperTermsQuery(TermsQuery wrappedQuery, int limit) {
    super(limit);
    this.wrappedQuery = wrappedQuery;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#isCountsEnabled()
   */
  @Override
  public boolean isCountsEnabled() {
    return wrappedQuery != null && wrappedQuery.isCountsEnabled();
  }
  
  /**
   * The wrapped wrappedQuery
   */
  protected TermsQuery wrappedQuery;

}
