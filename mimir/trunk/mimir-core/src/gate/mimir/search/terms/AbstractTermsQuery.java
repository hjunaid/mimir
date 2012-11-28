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

import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.AbstractIntComparator;


/**
 * Base class for term queries.
 */
public abstract class AbstractTermsQuery implements TermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -8448110711378800097L;
  
  public AbstractTermsQuery() {
  }
  
  
  /**
   * Sorts the arrays inside a {@link TermsResultSet} using the termString for
   * comparison.
   * @param trs
   */
  public static void sortTermsResultSetByTermString(final TermsResultSet trs) {
    Arrays.quickSort(0, trs.termStrings.length, new AbstractIntComparator() {
      @Override
      public int compare(int k1, int k2) {
        return trs.termStrings[k1].compareTo(trs.termStrings[k2]);
      }
    }, new Swapper() {
      @Override
      public void swap(int a, int b) {
        String termString = trs.termStrings[a];
        trs.termStrings[a] = trs.termStrings[b];
        trs.termStrings[b] = termString;
        if(trs.termCounts != null) {
          int termCount = trs.termCounts[a];
          trs.termCounts[a] = trs.termCounts[b];
          trs.termCounts[b] = termCount;
        }
        if(trs.termLengths != null) {
          int termLength = trs.termLengths[a];
          trs.termLengths[a] = trs.termLengths[b];
          trs.termLengths[b] = termLength;
        }
      }
    });
  }
}
