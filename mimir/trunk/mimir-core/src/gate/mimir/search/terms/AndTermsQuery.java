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
import it.unimi.dsi.fastutil.ints.AbstractIntComparator;
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
   * A boolean flag that is set to true if all sub-queries support counts, which
   * allows this query to also provide them.
   */
  protected boolean countsAvailable;
  
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
  public AndTermsQuery(int limit, TermsQuery... subQueries) {
    super(limit);
    this.subQueries = subQueries;
    countsAvailable = true;
    for(TermsQuery aQuery : subQueries) {
      if(!aQuery.isCountsEnabled()) {
        countsAvailable = false;
        break;
      }
    }
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    final TermsResultSet[] resSets = new TermsResultSet[subQueries.length];
    boolean lengthsAvailable = false;
    for(int i = 0; i < subQueries.length; i++) {
      resSets[i] = subQueries[i].execute(engine);
      if(resSets[i].termStrings.length == 0) return TermsResultSet.EMPTY;
      // this implementation requires that all sub-queries return terms in a 
      // consistent order, so we sort them lexicographically by termString
      sortTermsResultSetByTermString(resSets[i]);
      // at least one sub-query must provide lengths
      if(resSets[i].termLengths != null) lengthsAvailable = true;
    }
    // optimisation: sort sub-runners by increasing sizes
    Arrays.quickSort(0, resSets.length, new IntComparator() {
      @Override
      public int compare(Integer o1, Integer o2) { 
        return compare(o1.intValue(), o2.intValue());
      }
      @Override
      public int compare(int k1, int k2) {
        return resSets[k1].termStrings.length - resSets[k2].termStrings.length; 
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
    ObjectArrayList<String> termStrings = new ObjectArrayList<String>();
    IntArrayList termCounts = countsAvailable ? new IntArrayList() : null;
    IntArrayList termLengths = lengthsAvailable ? new IntArrayList() : null;
    // merge the inputs
    int[] indexes = new int[subQueries.length]; // initialised with 0s
    int currRunner = 0;
    String termString = resSets[currRunner].termStrings[indexes[currRunner]];
    top:while(currRunner < subQueries.length) {
      currRunner++;
      while(currRunner < subQueries.length &&
            resSets[currRunner].termStrings[indexes[currRunner]].equals(termString)) {
        currRunner++;
      }
      if(currRunner == subQueries.length) {
        // all heads agree:
        // store the term string
        termStrings.add(termString);
        // calculate the term count
        if(countsAvailable) {
          int count = 0;
          for(int i = 0; i < subQueries.length; i++) {
            if(resSets[i].termCounts != null) {
              count += resSets[i].termCounts[indexes[i]];
            }
          }
          termCounts.add(count);
        }
        // calculate the term length
        if(lengthsAvailable) {
          int termLength = -1;
          for(int i = 0; 
              i < subQueries.length && termLength == -1; 
              i++) {
            if(resSets[i].termLengths != null){
              termLength = resSets[i].termLengths[indexes[i]]; 
            }
          }
          termLengths.add(termLength);
        }
        
        
        // and start fresh
        currRunner = 0;
        indexes[currRunner]++;
        if(indexes[currRunner] == resSets[currRunner].termStrings.length) {
          // we're out
          break top;
        } else {
          termString  = resSets[currRunner].termStrings[indexes[currRunner]];
          continue top;
        }
      } else {
        // current runner is wrong
        while(resSets[currRunner].termStrings[indexes[currRunner]].compareTo(termString) < 0) {
          indexes[currRunner]++;
          if(indexes[currRunner] == resSets[currRunner].termStrings.length) {
            // this runner has run out
            break top;
          } else {
            if(resSets[currRunner].termStrings[indexes[currRunner]].compareTo(termString)  > 0) {
              // new term ID
              termString = resSets[currRunner].termStrings[indexes[currRunner]];
              currRunner = -1;
              continue top;
            }
          }
        }
      }
    } // top while
    // construct the result
    return new TermsResultSet(
        termStrings.toArray(new String[termStrings.size()]),
        lengthsAvailable? termLengths.toIntArray() : null,
        countsAvailable ? termCounts.toIntArray() : null);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#isCountsEnabled()
   */
  @Override
  public boolean isCountsEnabled() {
    return countsAvailable;
  }
}
