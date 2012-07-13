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

/**
 * A query that returns terms.
 * Term queries are fast, so they run synchronously.
 */
public interface TermQuery {
  /**
   * Runs the term query (in the calling thread) and returns the matched terms.
   * @return a {@link TermsResultSet} containing the matched terms.
   */
  public TermsResultSet execute();
  
}
