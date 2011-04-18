/*
 *  FederatedQueryRunner.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007 (also included with this distribution as file 
 *  LICENCE-AGPL3.html).
 *
 *  A commercial licence is also available for organisations whose business
 *  models preclude the adoption of open source and is subject to a licence
 *  fee charged by the University of Sheffield. Please contact the GATE team
 *  (see http://gate.ac.uk/g8/contact) if you require a commercial licence.
 *
 *  Ian Roberts, 15 Mar 2010
 * 
 *  $Id$
 */
package gate.mimir.search;

import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * QueryRunner implementation for querying federated indexes.
 */
public class FederatedQueryRunner implements QueryRunner {

  /**
   * The QueryRunners running the sub-queries of this federated
   * query.  Typically this would be one runner for each sub-index,
   * all running the same query string.
   */
  protected QueryRunner[] subRunners;

  /**
   * A pointer to the most recent DocumentData object in the
   * {@link #documentStats} list for each sub-query.
   */
  protected DocumentData[] lastDocumentForSubquery;
  
  /**
   * List of statistics from the documents that are currently
   * available from this federated query.
   */
  protected List<DocumentData> documentStats;
  
  /**
   * Is this query runner currently active?
   */
  protected boolean active = true;
  
  /**
   * Is this query runner complete?
   */
  protected boolean complete = false;
  
  /**
   * Class representing the information we need to keep about each
   * document.
   */
  protected static class DocumentData {
    /**
     * Which sub-query (as an index into subRunners) does this document
     * come from?
     */
    protected int query;
    
    /**
     * The index of the document within the sub-query results list.
     */
    protected int documentIndexInSubQuery;
    
    /**
     * The document ID in the sub-query's index.  To map this to the
     * document ID in the federated index as a whole, multiply by
     * subRunners.length and add {@link #query}.
     */
    protected int documentIdInSubIndex;
    
    /**
     * The index of the first hit in this document within its sub-query
     * runner.  This is necessary in order to map a getHits call to the
     * correct hit range in the sub-queries.
     */
    protected int firstHitOffsetInSubQuery;
    
    /**
     * The cumulative hit count of this federated query up to and
     * including this document.  This allows us to quickly find the
     * right document for a getHits call using binary search.
     */
    protected int cumulativeHitsInFederatedQuery;
  }
  
  /**
   * Comparator that can compare DocumentData objects against each
   * other or against Integer objects.  For DocumentData we take the
   * cumulativeHitsInFederatedQuery, for Integer we take the intValue.
   */
  protected static final Comparator<Object> STATS_COMPARATOR = new Comparator<Object>() {
    public int compare(Object a, Object b) {
      return valueToCompare(a) - valueToCompare(b);
    }
    
    private int valueToCompare(Object o) {
      if(o instanceof Integer) {
        return ((Integer)o).intValue();
      }
      else {
        return ((DocumentData)o).cumulativeHitsInFederatedQuery;
      }
    }
  };
  
  /**
   * Create a federated query runner that delegates to the specified
   * sub-query runners.
   */
  public FederatedQueryRunner(QueryRunner[] subRunners) {
    super();
    this.subRunners = subRunners;
    // Java doesn't allow you to create an array of generic elements
    documentStats = new ArrayList<DocumentData>();
    lastDocumentForSubquery = new DocumentData[subRunners.length];
    Arrays.fill(lastDocumentForSubquery, null);
  }
  
  /**
   * Checks the subqueries and drains as many results as possible into
   * our federated statistics list.
   */
  protected void collectStatistics() {
    if(complete) return;
    synchronized(documentStats) {
      // a sub-query is "blocked" if it is not complete but we have
      // already consumed all its currently-available documents.
      boolean[] subQueryBlocked = new boolean[subRunners.length];
  
      // The index into the sub query's statistics list of the next
      // available or potentially-available
      // document in each sub query
      int[] nextDocumentIndexInSubQuery = new int[subRunners.length];

      // the next available or potentially-available document ID
      // for each sub query - if the sub-query is blocked this is
      // the next ID that it could possibly return, but there may
      // not actually be any hits for that document.
      int[] nextDocumentIdForSubQuery = new int[subRunners.length];
  
      boolean finished = false;
      while(!finished) {
        // we need to look at the next document ID available for each
        // sub-query, convert the IDs from the sub-index view to the
        // federated index view and determine which is smallest
        int smallestDocId = Integer.MAX_VALUE;
        int nextQuery = -1;
        for(int i = 0; i < subRunners.length; ++i) {
          // assume not blocked by default
          subQueryBlocked[i] = false;
          
          // initialize nextDocumentIndexInSubQuery[i] to the index in
          // the sub-query's list at which the next document will be
          // found.  This will be 0 for queries that have not yet
          // returned anything, and 1 greater than the last index
          // returned otherwise.
          nextDocumentIndexInSubQuery[i] = 0;
          if(lastDocumentForSubquery[i] != null) {
            nextDocumentIndexInSubQuery[i] = lastDocumentForSubquery[i].documentIndexInSubQuery + 1;
          }
          
          boolean subQueryComplete = subRunners[i].isComplete();
          int maxDocIndexForSubQuery = subRunners[i].getDocumentsCount() - 1;
          // if the current sub-query is not complete we ignore the last
          // document returned from its runner, as there may still be
          // more hits to be found in this document.
          if(!subQueryComplete) {
            maxDocIndexForSubQuery--;
          }

          // if the sub-query has documents available that we have not yet
          // consumed then find the (sub-index) document ID for the next
          // available document from the sub-query runner.
          if(nextDocumentIndexInSubQuery[i] <= maxDocIndexForSubQuery) {
            nextDocumentIdForSubQuery[i] = subRunners[i].getDocumentID(
                  nextDocumentIndexInSubQuery[i]);
          }
          else {
            // if the sub-query does not have any documents available, but it 
            // is already complete, then we may ignore it when searching for
            // the smallest ID
            if(subQueryComplete) {
              nextDocumentIdForSubQuery[i] = Integer.MAX_VALUE;
            }
            // if the sub-query does not have any documents available and it
            // is *not* complete, then it is blocked.  Assume the next ID
            // that might be returned is one higher than the last one we saw
            else {
              nextDocumentIdForSubQuery[i] = 0;
              if(lastDocumentForSubquery[i] != null) {
                nextDocumentIdForSubQuery[i] = lastDocumentForSubquery[i].documentIdInSubIndex + 1;
              }
              subQueryBlocked[i] = true;
            }
          }
          // find the minimum - when two sub-queries give the same document
          // ID the one on the left (smaller value of i) wins.
          if(nextDocumentIdForSubQuery[i] < smallestDocId) {
            smallestDocId = nextDocumentIdForSubQuery[i];
            nextQuery = i;
          }
        }
        
        // at this point we know the smallest document ID available and
        // which query it came from.  Now there are three cases:
        //
        // 1: all queries complete and completely consumed - we are
        // done (and the federated query is complete and inactive)
        if(smallestDocId == Integer.MAX_VALUE) {
          finished = true;
          active = false;
          complete = true;
        }
        // 2: the smallest value came from a blocked query - the
        // federated query is now blocked, and is active if and
        // only if the blocked sub-query is active
        else if(subQueryBlocked[nextQuery]) {
          finished = true;
          active = subRunners[nextQuery].isActive();
        }
        // 3: the smallest value came from a non-complete, non-blocked
        // sub-query - we can consume its next document
        else {
          DocumentData newDocumentData = new DocumentData();
          newDocumentData.query = nextQuery;
          newDocumentData.documentIndexInSubQuery = 0;
          if(lastDocumentForSubquery[nextQuery] != null) {
            newDocumentData.documentIndexInSubQuery =
              lastDocumentForSubquery[nextQuery].documentIndexInSubQuery + 1;
          }
          // convert document ID back into sub-index ID
          newDocumentData.documentIdInSubIndex =
              nextDocumentIdForSubQuery[nextQuery];
          int hitsForDocument = subRunners[nextQuery].getDocumentHitsCount(
                  newDocumentData.documentIndexInSubQuery);
          newDocumentData.firstHitOffsetInSubQuery = 0;
          if(lastDocumentForSubquery[nextQuery] != null) {
            newDocumentData.firstHitOffsetInSubQuery =
              lastDocumentForSubquery[nextQuery].firstHitOffsetInSubQuery 
                + subRunners[nextQuery].getDocumentHitsCount(
                  lastDocumentForSubquery[nextQuery].documentIndexInSubQuery);
          }
          int previousCumulativeHits = 0;
          if(documentStats.size() > 0) {
            previousCumulativeHits = documentStats.get(
                    documentStats.size() - 1).cumulativeHitsInFederatedQuery;
          }
          newDocumentData.cumulativeHitsInFederatedQuery = previousCumulativeHits + hitsForDocument;
          
          documentStats.add(newDocumentData);
          // this DocumentData is now the last-seen for this sub-query
          lastDocumentForSubquery[nextQuery] = newDocumentData;
        }
      }
    }
  }

  public int getDocumentHitsCount(int index) throws IndexOutOfBoundsException {
    synchronized(documentStats) {
      collectStatistics();
      if(index == 0) {
        return documentStats.get(0).cumulativeHitsInFederatedQuery;
      }
      else {
        return documentStats.get(index).cumulativeHitsInFederatedQuery
               - documentStats.get(index - 1).cumulativeHitsInFederatedQuery;
      }
    }
  }

  public int getDocumentID(int index) throws IndexOutOfBoundsException {
    DocumentData data;
    synchronized(documentStats) {
      collectStatistics();
      data = documentStats.get(index);
    }
    return data.documentIdInSubIndex * subRunners.length + data.query;
  }

  public String[][] getDocumentText(int documentID, int termPosition, int length)
          throws IndexException {
    int subQuery = documentID % subRunners.length;
    int idWithinSubIndex = documentID / subRunners.length;
    return subRunners[subQuery].getDocumentText(idWithinSubIndex, termPosition, length);
  }

  public String getDocumentURI(int documentID) throws IndexException {
    int subQuery = documentID % subRunners.length;
    int idWithinSubIndex = documentID / subRunners.length;
    return subRunners[subQuery].getDocumentURI(idWithinSubIndex);
  }
  
  public String getDocumentTitle(int documentID) throws IndexException {
    int subQuery = documentID % subRunners.length;
    int idWithinSubIndex = documentID / subRunners.length;
    return subRunners[subQuery].getDocumentTitle(idWithinSubIndex);    
  }

  public List<Binding> getHits(int startIndex, int hitCount)
          throws IndexOutOfBoundsException {
    if(startIndex < 0) {
      throw new IndexOutOfBoundsException("negative startIndex: " + startIndex);
    }
    synchronized(documentStats) {
      collectStatistics();
      int documentIdx = Collections.binarySearch(documentStats, Integer.valueOf(startIndex), STATS_COMPARATOR);
      if(documentIdx >= 0) {
        // found an exact match, which means that the startIndex-th hit
        // is actually the first hit for the next document.
        documentIdx++;
      }
      else {
        // not an exact match, so the startIndex-th hit is in the
        // middle of the hit list for this document.
        documentIdx = -documentIdx - 1;
      }
      
      if(documentIdx >= documentStats.size()) {
        return Collections.emptyList();
      }
      
      List<Binding> hitsToReturn = new ArrayList<Binding>(hitCount);
      
      int cumulativeHitsToPrevDoc = 0;
      if(documentIdx > 0) {
        cumulativeHitsToPrevDoc = documentStats.get(documentIdx - 1).cumulativeHitsInFederatedQuery;
      }
      boolean exhausted = false;
      while(!exhausted && hitCount > 0) {
        DocumentData docData = documentStats.get(documentIdx);
        int firstHitFromDoc = startIndex - cumulativeHitsToPrevDoc;
        if(firstHitFromDoc < 0) {
          firstHitFromDoc = 0;
        }
        // how many hits does this query have in this document
        int hitsInDoc = docData.cumulativeHitsInFederatedQuery - cumulativeHitsToPrevDoc;
        // how many hits should we fetch from the sub-query runner?
        // this is the minimum of hitCount and (hitsInDoc - firstHitFromDoc)
        int hitsToFetch = hitsInDoc - firstHitFromDoc;
        if(hitsToFetch > hitCount) {
          hitsToFetch = hitCount;
        }
        
        List<Binding> subQueryHits = subRunners[docData.query].getHits(
                docData.firstHitOffsetInSubQuery + firstHitFromDoc, hitsToFetch);
        for(Binding subQueryBinding : subQueryHits) {
          // construct a Binding from the point of view of the federated query:
          Binding fedQueryBinding = new Binding(
                  // QueryNode from sub-query binding
                  subQueryBinding.getQueryNode(),
                  // document ID converted to federated index
                  docData.documentIdInSubIndex * subRunners.length + docData.query,
                  // term position and length from bub-query binding
                  subQueryBinding.getTermPosition(), subQueryBinding.getLength(),
                  // TODO do we want to handle sub-bindings?
                  null);
          hitsToReturn.add(fedQueryBinding);
          hitCount--;
        }
        
        // update previous cumulative hits counter
        cumulativeHitsToPrevDoc = docData.cumulativeHitsInFederatedQuery;
        documentIdx++;
        // have we run out of documents?
        if(documentIdx == documentStats.size()) {
          exhausted = true;
        }
      }
      return hitsToReturn;
    }
  }

  public void renderDocument(int documentId, Appendable out)
          throws IOException, IndexException {
    int subQuery = documentId % subRunners.length;
    int idWithinSubIndex = documentId / subRunners.length;
    subRunners[subQuery].renderDocument(idWithinSubIndex, out);
  }
  
  /**
   * Returns the total number of documents seen so far.
   */
  public int getDocumentsCount() {
    synchronized(documentStats) {
      collectStatistics();
      return documentStats.size();
    }
  }
  
  /**
   * Return the total number of hits seen so far.
   */
  public int getHitsCount() {
    synchronized(documentStats) {
      collectStatistics();
      if(documentStats.size() == 0) {
        return 0;
      }
      else {
        return documentStats.get(documentStats.size() - 1)
                .cumulativeHitsInFederatedQuery;
      }
    }
  }

  /**
   * Tell all the sub-query runners to getMoreHits.
   */
  public void getMoreHits() throws IOException {
    for(QueryRunner r : subRunners) {
      r.getMoreHits();
    }
  }

  /**
   * A federated query is active iff the sub-query from which the next
   * hits will come is active.
   */
  public boolean isActive() {
    return active;
  }

  /**
   * A federated query is complete iff all its sub-queries are complete.
   */
  public boolean isComplete() {
    return complete;
  }

  /**
   * Share out the max hits per stage between the sub-runners.
   */
  public void setStageMaxHits(int maxHits) throws IOException{
    int hitsPerRunner = maxHits / subRunners.length;
    // if subRunners.length does not divide evenly into maxHits then
    // we need to set n subRunners to hitsPerRunner + 1 and the rest
    // to hitsPerRunner, in order to share out the remainder so the
    // total stageMaxHits is correct
    int i = 0;
    for(i = 0; i < maxHits % subRunners.length; i++) {
      subRunners[i].setStageMaxHits(hitsPerRunner + 1);
    }
    for(; i < subRunners.length; i++) {
      subRunners[i].setStageMaxHits(hitsPerRunner);
    }
  }

  /**
   * Pass on the stage timeout to all the sub-runners.
   */
  public void setStageTimeout(int timeout) throws IOException {
    for(QueryRunner r : subRunners) {
      r.setStageTimeout(timeout);
    }
  }

  /**
   * Close all the sub-query runners.
   */
  public void close() throws IOException {
    for(QueryRunner r : subRunners) {
      r.close();
    }
  }
}
