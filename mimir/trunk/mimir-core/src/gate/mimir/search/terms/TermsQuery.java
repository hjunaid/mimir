/*
 *  TermQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 13 Jul 2012
 *
 *  $Id$
 */
package gate.mimir.search.terms;

import java.io.IOException;
import java.io.Serializable;

import gate.mimir.search.QueryEngine;

/**
 * A query that returns terms. The terms returned must be sorted in ascending 
 * order of their term ID.
 */
public interface TermsQuery extends Serializable {
  
  public static final int NO_LIMIT = Integer.MAX_VALUE;
  
  /**
   * Runs the term query (in the calling thread) and returns the matched terms.
   * The terms returned must be sorted in ascending order of their term ID. 
   * @return a {@link TermsResultSet} containing the matched terms.
   * @param engine the {@link QueryEngine} used to execute the search.
   * @throws IOException 
   */
  public TermsResultSet execute(QueryEngine engine) throws IOException;
  
  /**
   * Does this query return term strings?
   * @return
   */
  public boolean isStringsEnabled();
  
  /**
   * Does this query return term counts?
   * @return
   */
  public boolean isCountsEnabled();
  
}
