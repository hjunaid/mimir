/*
 *  AndTermsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 17 Jul 2012
 *
 *  $Id$
 */
package gate.mimir.search.terms;

import gate.mimir.search.QueryEngine;

import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;

/**
 * Performs Boolean AND between multiple {@link TermsQuery} instances.
 */
public class AndTermsQuery extends AbstractTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -6757669202064075218L;
  
  /**
   * The sub-queries being AND'ed.
   */
  protected TermsQuery[] subQueries;
  
  /**
   * Constructs a new AND term query.
   * 
   * @param stringsEnabled should terms strings be returned.
   * @param countsEnabled should term counts be returned. Counts are 
   * accumulated across all sub-queries: the count for a term is the sum of all
   * counts for the same term in all sub-queries.  
   * @param limit the maximum number of terms to be returned. 
   * @param subQueries the term queries that form the disjunction.
   */
  public AndTermsQuery(boolean stringsEnabled, boolean countsEnabled,
                       int limit, TermsQuery... subQueries) {
    super(stringsEnabled, countsEnabled, limit);
    this.subQueries = subQueries;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    final TermsResultSet[] resSets = new TermsResultSet[subQueries.length];
    for(int i = 0; i < subQueries.length; i++) {
      resSets[i] = subQueries[i].execute(engine);
      if(resSets[i].termIds.length == 0) return TermsResultSet.EMPTY;
    }
    // optimisation: sort sub-runners by increasing sizes
    Arrays.quickSort(0, resSets.length, new IntComparator() {
      @Override
      public int compare(Integer o1, Integer o2) { 
        return compare(o1.intValue(), o2.intValue());
      }
      @Override
      public int compare(int k1, int k2) {
        return resSets[k1].termIds.length - resSets[k2].termIds.length; 
      }
    }, new Swapper() {
      @Override
      public void swap(int a, int b) {
        TermsResultSet trs = resSets[a];
        resSets[a] = resSets[b];
        resSets[b] = trs;
      }
    });
    
    // prepare local data
    LongArrayList termIds = new LongArrayList();
    ObjectArrayList<String> termStrings = stringsEnabled ? 
        new ObjectArrayList<String>() : null;
    IntArrayList termCounts = countsEnabled ? new IntArrayList() : null;
    // merge the inputs
    int[] indexes = new int[subQueries.length]; // initialised with 0s
    int currRunner = 0;
    long termId = resSets[currRunner].termIds[indexes[currRunner]];
    top:while(currRunner < subQueries.length) {
      currRunner++;
      while(currRunner < subQueries.length &&
            resSets[currRunner].termIds[indexes[currRunner]] == termId) {
        currRunner++;
      }
      if(currRunner == subQueries.length) {
        // all heads agree
        termIds.add(termId);
        if(stringsEnabled) {
          String termString = null;
          for(int i = 0; 
              i < subQueries.length && termString == null; 
              i++) {
            if(resSets[i].termStrings != null){
              termString = resSets[i].termStrings[indexes[i]]; 
            }
          }
          termStrings.add(termString);
        }
        if(countsEnabled) {
          int count = 0;
          for(int i = 0; i < subQueries.length; i++) {
            if(resSets[i].termCounts != null) {
              count += resSets[i].termCounts[indexes[i]];
            }
          }
          termCounts.add(count);
        }
        // and start fresh
        currRunner = 0;
        indexes[currRunner]++;
        if(indexes[currRunner] == resSets[currRunner].termIds.length) {
          // we're out
          break top;
        } else {
          termId  = resSets[currRunner].termIds[indexes[currRunner]];
          continue top;
        }
      } else {
        // current runner is wrong
        while(resSets[currRunner].termIds[indexes[currRunner]] < termId) {
          indexes[currRunner]++;
          if(indexes[currRunner] == resSets[currRunner].termIds.length) {
            // this runner has run out
            break top;
          } else {
            if(resSets[currRunner].termIds[indexes[currRunner]] > termId) {
              // new term ID
              termId = resSets[currRunner].termIds[indexes[currRunner]];
              currRunner = -1;
              continue top;
            }
          }
        }
      }
    } // top while
    // construct the result
    return new TermsResultSet(termIds.toLongArray(),
      stringsEnabled ? termStrings.toArray(new String[termStrings.size()]) : null,
      null,
      countsEnabled ? termCounts.toIntArray() : null);
  }
  
}
