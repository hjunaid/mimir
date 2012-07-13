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
public abstract class AbstractTermsQuery implements TermQuery {
  
  protected boolean stringsEnabled;
  
  protected boolean idsEnabled;
  
  protected boolean countsEnabled;

  public AbstractTermsQuery(boolean idsEnabled, boolean stringsEnabled,
                            boolean countsEnabled) {
    this.idsEnabled = idsEnabled;
    this.stringsEnabled = stringsEnabled;
    this.countsEnabled = countsEnabled;
  }
  
  public AbstractTermsQuery() {
    this(true, false, false);
  }
  
}
