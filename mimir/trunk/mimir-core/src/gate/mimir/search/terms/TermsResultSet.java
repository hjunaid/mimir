/*
 *  TermsResultSet.java
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

import java.io.Serializable;

/**
 * Class representing the results of a {@link TermsQuery}. 
 * A terms result set is a set of terms, represented by their 
 * {@link #termStrings}. Optionally {@link #termCounts}, and 
 * {@link #termLengths} may also be available.
 */
public class TermsResultSet implements Serializable {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -7722325563637139625L;

  
  /**
   * The lengths (number of tokens) for the terms.
   */
  public final int[] termLengths;
  
  /**
   * The strings for the terms. Array parallel with 
   * {@link #termIds} and {@link #termCounts}.
   */
  public final String[] termStrings;
  
  
  /**
   * The counts (numbers of occurrences) for the terms. Array parallel with 
   * {@link #termStrings} and {@link #termIds}.
   */
  public final int[] termCounts;

  public TermsResultSet(String[] termStrings,int[] termLengths, 
                        int[] termCounts) {
    super();
    this.termStrings = termStrings;
    this.termLengths = termLengths;
    this.termCounts = termCounts;
  }
  
  /**
   * Constant representing the empty result set.
   */
  public static final TermsResultSet EMPTY = new TermsResultSet(
      new String[]{}, new int[] {}, new int[]{});
  
}
