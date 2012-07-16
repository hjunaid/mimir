/*
 *  AbstractTermsQuery.java
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
 * Base class for term queries.
 */
public abstract class AbstractTermsQuery implements TermsQuery {
  
  protected boolean stringsEnabled;
  
  protected boolean countsEnabled;

  public static final int NO_LIMIT = -1;
  /**
   * The maximum number of results to be returned.
   */
  protected final int limit;  
  
  public AbstractTermsQuery(boolean stringsEnabled, boolean countsEnabled, 
                            int limit) {
    this.stringsEnabled = stringsEnabled;
    this.countsEnabled = countsEnabled;
    this.limit = limit;
  }
  
  public AbstractTermsQuery(boolean stringsEnabled, boolean countsEnabled) {
    this(stringsEnabled, countsEnabled, NO_LIMIT);
  }  
  
  public AbstractTermsQuery() {
    this(false, false, NO_LIMIT);
  }
  
}
