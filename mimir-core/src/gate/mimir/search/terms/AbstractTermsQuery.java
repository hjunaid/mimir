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

import java.io.IOException;

import gate.mimir.search.QueryEngine;


/**
 * Base class for term queries.
 */
public abstract class AbstractTermsQuery implements TermsQuery{
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -8448110711378800097L;
  
  public AbstractTermsQuery() {
  }
  
  
  
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    // TODO Auto-generated method stub
    return null;
  }
}
