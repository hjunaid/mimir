/*
 *  OrTermsQuery.java
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
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongHeapSemiIndirectPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;

/**
 * Boolean OR operator for term queries.
 */
public class OrTermsQuery extends AbstractTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 3293699315503739659L;
  
  /**
   * The sub-queries being OR'ed
   */
  protected TermsQuery[] subQueries;

  /**
   * Constructs a new OR terms query.
   * @param stringsEnabled should terms strings be returned.
   * @param countsEnabled should term counts be returned. Counts are 
   * accumulated across all sub-queries: the count for a term is the sum of all
   * counts for the same term in all sub-queries.  
   * @param limit the maximum number of terms to be returned. 
   * @param subQueries the term queries that form the disjunction.
   */
  public OrTermsQuery(boolean stringsEnabled, boolean countsEnabled,
                       int limit, TermsQuery... subQueries) {
    super(stringsEnabled, countsEnabled, limit);
    this.subQueries = subQueries;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    TermsResultSet[] resSets = new TermsResultSet[subQueries.length];
    long[] currentTerm = new long[resSets.length];
    LongHeapSemiIndirectPriorityQueue queue = 
        new LongHeapSemiIndirectPriorityQueue(currentTerm);
    int[] termIndex = new int[resSets.length];
    for(int i = 0; i < subQueries.length; i++) {
      resSets[i] = subQueries[i].execute(engine);
      if(resSets[i].termIds.length > 0){
        termIndex[i] = 0;
        currentTerm[i] = resSets[i].termIds[termIndex[i]];
        queue.enqueue(i);
      }
    }
    
    // prepare local data
    LongArrayList termIds = new LongArrayList();
    ObjectArrayList<String> termStrings = stringsEnabled ? 
        new ObjectArrayList<String>() : null;
    IntArrayList termCounts = countsEnabled ? new IntArrayList() : null;
    int front[] = null;
    if(stringsEnabled || countsEnabled) front = new int[resSets.length];
    // enumerate all terms
    top:while(!queue.isEmpty()) {
      int first = queue.first();
      long termId = resSets[first].termIds[termIndex[first]];
      termIds.add(termId);
      if(countsEnabled || stringsEnabled) {
        int frontSize = queue.front(front);
        String termString = null;
        int count = 0;
        for(int i = 0;  i < frontSize; i++) {
          int subRunnerId = front[i];
          if(stringsEnabled &&
             termString == null &&
             resSets[subRunnerId].termStrings != null) {
            termString = resSets[subRunnerId].termStrings[termIndex[subRunnerId]];
          }
          if(resSets[subRunnerId].termCounts != null) {
            count += resSets[subRunnerId].termCounts[termIndex[subRunnerId]];
          }
        }
        if(stringsEnabled) termStrings.add(termString);
        if(countsEnabled) termCounts.add(count);
      }
      // consume all equal terms
      while(resSets[first].termIds[termIndex[first]] == termId) {
        // advance this subRunner
        termIndex[first]++;
        if(termIndex[first] == resSets[first].termIds.length) {
          // 'first' is out
          queue.dequeue();
          if(queue.isEmpty()) break top;
        } else {
          currentTerm[first] = resSets[first].termIds[termIndex[first]];
          queue.changed();
        }
        first = queue.first();
      }
    }
    // construct the result
    return new TermsResultSet(termIds.toLongArray(),
      stringsEnabled ? termStrings.toArray(new String[termStrings.size()]) : null,
      null,
      countsEnabled ? termCounts.toIntArray() : null);
  }
}
