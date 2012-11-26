/*
 *  SortedTermsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 18 Jul 2012
 *
 *  $Id$
 */
package gate.mimir.search.terms;

import gate.mimir.search.QueryEngine;

import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.IntComparator;

import java.io.IOException;

/**
 * A wrapper for another terms query that simply sorts the returned terms based
 * on some criteria.
 */
public class SortedTermsQuery extends AbstractTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -4763084996036534582L;
  

  public static enum SortOrder {
    /** Sort by ID, ascending. */
    ID,
    
    /** Sort by ID (descending) */
    ID_DESC,
    
    /** Sort by counts */
    COUNT,
    
    /** Sort by counts (descending). */
    COUNT_DESC,
    /** Sort by term string */
    STRING,
    /** Sort by term string (descending) */
    STRING_DESC
  }
  
  protected TermsQuery query;
  
  protected SortOrder[] criteria;
  
  /**
   * The default sort criteria: returned terms are sorted by:
   * <ul>
   *   <li>terms count (descending), then by</li>
   *   <li>term string (ascending), then by</li>
   *   <li>term ID (ascending)</li>
   * </ul> 
   */
  public static final SortOrder[] DEFAULT_SORT_CRITERIA = new SortOrder[]
      { SortOrder.COUNT_DESC, SortOrder.STRING, SortOrder.ID };
  
  /**
   * Creates a new sorted terms query, wrapping the provided query, and using 
   * the given sort criteria.
   * @param query
   * @param criteria
   */
  public SortedTermsQuery(TermsQuery query, SortOrder... criteria) {
    super(query.isStringsEnabled(), query.isCountsEnabled(), NO_LIMIT);
    this.query = query;
    this.criteria = criteria;
  }

  /**
   * Creates a new sorted terms query, wrapping the provided query, and using 
   * the {@link #DEFAULT_SORT_CRITERIA}. 
   * @param query
   */
  public SortedTermsQuery(TermsQuery query) {
   this(query, DEFAULT_SORT_CRITERIA);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    final TermsResultSet trs = query.execute(engine);
    Arrays.quickSort(0, trs.termIds.length, new IntComparator() {
      @Override
      public int compare(Integer o1, Integer o2) {
        return compare(o1.intValue(), o2.intValue());
      }
      
      @Override
      public int compare(int k1, int k2) {
        int retval = 0;
        for(SortOrder crit: criteria) {
          switch(crit) {
            case ID:
              retval = Long.signum(trs.termIds[k1] - trs.termIds[k2]);
              break;
            case ID_DESC:
              retval = -Long.signum(trs.termIds[k1] - trs.termIds[k2]);
              break;
            case COUNT:
              if(trs.termCounts != null){
                retval = trs.termCounts[k1] - trs.termCounts[k2];
              }
              break;
            case COUNT_DESC:
              if(trs.termCounts != null){
                retval = trs.termCounts[k2] - trs.termCounts[k1];
              }
              break;
            case STRING:
              if(trs.termStrings != null &&
                 trs.termStrings[k1] != null &&
                 trs.termStrings[k2] != null) {
                retval = trs.termStrings[k1].compareTo(trs.termStrings[k2]);
              }
              break;
            case STRING_DESC:
              if(trs.termStrings != null &&
                 trs.termStrings[k1] != null &&
                 trs.termStrings[k2] != null) {
                retval = trs.termStrings[k2].compareTo(trs.termStrings[k1]);
              }
           break;
          }
          if(retval != 0) return retval;
        }
        return retval;
      }
    }, new Swapper() {
      @Override
      public void swap(int a, int b) {
        long termId = trs.termIds[a];
        trs.termIds[a] = trs.termIds[b];
        trs.termIds[b] = termId;
        if(trs.termStrings != null) {
          String termString = trs.termStrings[a];
          trs.termStrings[a] = trs.termStrings[b];
          trs.termStrings[b] = termString;
        }
        if(trs.termLengths != null) {
          int termLen = trs.termLengths[a];
          trs.termLengths[a] = trs.termLengths[b];
          trs.termLengths[b] = termLen;
        }
        if(trs.termCounts != null) {
          int termCount = trs.termCounts[a];
          trs.termCounts[a] = trs.termCounts[b];
          trs.termCounts[b] = termCount;
        }
      }
    });
    return trs;
  }
}
