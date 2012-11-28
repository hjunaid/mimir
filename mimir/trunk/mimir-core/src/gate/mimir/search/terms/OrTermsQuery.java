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
import it.unimi.dsi.fastutil.objects.ObjectHeapSemiIndirectPriorityQueue;

import java.io.IOException;

/**
 * Boolean OR operator for term queries.
 */
public class OrTermsQuery extends AbstractCompoundTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 3293699315503739659L;


  /**
   * Constructs a new OR terms query.
   * @param stringsEnabled should terms strings be returned.
   * @param countsEnabled should term counts be returned. Counts are 
   * accumulated across all sub-queries: the count for a term is the sum of all
   * counts for the same term in all sub-queries.  
   * @param limit the maximum number of terms to be returned. 
   * @param subQueries the term queries that form the disjunction.
   */
  public OrTermsQuery(TermsQuery... subQueries) {
    super(subQueries);
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.terms.CompoundTermsQuery#combine(gate.mimir.search.terms.TermsResultSet[])
   */
  @Override
  public TermsResultSet combine(TermsResultSet... resSets) {
    return orResultsSets(resSets);
  }

  /**
   * Given a set of {@link TermsResultSet} values, this method combines them
   * into a single {@link TermsResultSet} representing the disjunction of all
   * the provided results sets. 
   * @param resSets 
   * @return
   */
  public static TermsResultSet orResultsSets(TermsResultSet... resSets) {
    String[] currentTerm = new String[resSets.length];
    ObjectHeapSemiIndirectPriorityQueue<String> queue = 
        new ObjectHeapSemiIndirectPriorityQueue<String>(currentTerm);
    int[] termIndex = new int[resSets.length];
    boolean lengthsAvailable = true;
    boolean countsAvailable = true;
    for(int i = 0; i < resSets.length; i++) {
      // this implementation requires that all sub-queries return terms in a 
      // consistent order, so we sort them lexicographically by termString
      sortTermsResultSetByTermString(resSets[i]);
      if(resSets[i].termStrings.length > 0){
        termIndex[i] = 0;
        currentTerm[i] = resSets[i].termStrings[termIndex[i]];
        queue.enqueue(i);
      }
      // we need *all* sub-queries to provide lengths, because we don't know
      // which one will provide any of the results.
      if(resSets[i].termLengths == null) lengthsAvailable = false;
      if(resSets[i].termCounts == null) countsAvailable = false;
    }
    
    // prepare local data
    ObjectArrayList<String> termStrings = new ObjectArrayList<String>();
    IntArrayList termLengths = lengthsAvailable ? new IntArrayList() : null;
    IntArrayList termCounts = countsAvailable ? new IntArrayList() : null;
    int front[] = new int[resSets.length];
    // enumerate all terms
    top:while(!queue.isEmpty()) {
      int first = queue.first();
      String termString = resSets[first].termStrings[termIndex[first]];
      termStrings.add(termString);
      if(countsAvailable) {
        int frontSize = queue.front(front);
        int count = 0;
        for(int i = 0;  i < frontSize; i++) {
          int subRunnerId = front[i];
          count += resSets[subRunnerId].termCounts[termIndex[subRunnerId]];
        }
        termCounts.add(count);
      }
      // consume all equal terms
      while(resSets[first].termStrings[termIndex[first]].equals(termString)) {
        // advance this subRunner
        termIndex[first]++;
        if(termIndex[first] == resSets[first].termStrings.length) {
          // 'first' is out
          queue.dequeue();
          if(queue.isEmpty()) break top;
        } else {
          currentTerm[first] = resSets[first].termStrings[termIndex[first]];
          queue.changed();
        }
        first = queue.first();
      }
    }
    // construct the result
    return new TermsResultSet(
        termStrings.toArray(new String[termStrings.size()]),
        lengthsAvailable ? termLengths.toIntArray() : null,
        countsAvailable ? termCounts.toIntArray() : null);
  }
}
