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

/**
 * Class representing the results of a {@link TermsQuery}. 
 */
public class TermsResultSet {
  
  /**
   * The term IDs, as retrieved from the index. Array parallel with 
   * {@link #termStrings} and {@link #termCounts}.
   */
  public final long[] termIds;
  
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

  public TermsResultSet(long[] termIds, String[] termStrings,int[] termLengths, 
                        int[] termCounts) {
    super();
    this.termIds = termIds;
    this.termStrings = termStrings;
    this.termLengths = termLengths;
    this.termCounts = termCounts;
  }
  
  /**
   * Constant representing the empty result set.
   */
  public static final TermsResultSet EMPTY = new TermsResultSet(
      new long[] {}, new String[]{}, new int[] {}, new int[]{}); 
  
}
