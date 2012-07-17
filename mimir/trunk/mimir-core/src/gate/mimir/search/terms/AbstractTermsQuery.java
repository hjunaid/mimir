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

import gate.mimir.search.IndexReaderPool;
import it.unimi.dsi.big.mg4j.index.IndexIterator;
import it.unimi.dsi.big.mg4j.search.DocumentIterator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Base class for term queries.
 */
public abstract class AbstractTermsQuery implements TermsQuery {
  
  protected boolean stringsEnabled;
  
  protected boolean countsEnabled;

  public static final int NO_LIMIT = Integer.MAX_VALUE;
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
