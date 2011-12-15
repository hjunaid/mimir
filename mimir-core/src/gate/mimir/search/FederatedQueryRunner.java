/*
 *  FederatedQueryRunner.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 15 Dec 2011
 * 
 *  $Id$
 */
package gate.mimir.search;

import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;

import it.unimi.dsi.fastutil.ints.IntList;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link QueryRunner} that presents a set of sub-indexes (represented by 
 * their own QueryRunners) as a single index.
 */
public class FederatedQueryRunner implements QueryRunner {
  
  /**
   * The total number of result documents (or -1 if not yet known).
   */
  private int documentsCount = -1;
  
  /**
   * The query runners for the sub-indexes.
   */
  protected QueryRunner[] subRunners;
  
  /**
   * The next rank that needs to be merged from each sub runner.
   */
  protected int[] nextSubRunnerRank;
  
  /**
   * For each result document rank, this list supplies the index for the 
   * sub-runner that supplied the document.  
   */
  protected IntList rank2runnerIndex;
  
  
  /**
   * For each result document rank, this list supplies the rank of the document
   * in sub-runner that supplied it.  
   */  
  protected IntList rank2subRank;
  
  public FederatedQueryRunner(QueryRunner[] subrunners) {
    this.subRunners = subrunners;
    this.nextSubRunnerRank = null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCount()
   */
  @Override
  public int getDocumentsCount() {
    if(documentsCount < 0) {
      int newDocumentsCount = 0;
      for(QueryRunner subRunner : subRunners) {
        int subDocumentsCount = subRunner.getDocumentsCount();
        if(subDocumentsCount < 0) {
          return -1;
        } else {
          newDocumentsCount += subDocumentsCount;
        }
      }
      synchronized(this) {
        // initialize the nextSubRunnerRank array
        nextSubRunnerRank = new int[subRunners.length];
        for(int i = 0; i < nextSubRunnerRank.length; i++) {
          if(subRunners[i].getDocumentsCount() == 0) {
            nextSubRunnerRank[i] = -1;
          }
        }
        documentsCount = newDocumentsCount;
      }
    }
    return documentsCount;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getCurrentDocumentsCount()
   */
  @Override
  public int getDocumentsCurrentCount() {
    if(documentsCount >= 0) {
      return documentsCount;
    } else {
      int newDocumentsCount = 0;
      for(QueryRunner subRunner : subRunners) {
        newDocumentsCount += subRunner.getDocumentsCurrentCount();
      }
      return newDocumentsCount;
    }
  }
  
  /**
   * Ensure that the given rank is resolved to the appropriate sub-runner rank.
   * @throws IndexOutOfBoundsException if rank is beyond the last document.
   */
  private final synchronized void checkRank(int rank) throws IndexOutOfBoundsException, IOException {
    // quick check to see if we need to do anything else
    if(rank < rank2runnerIndex.size()) {
      return;
    }
    
    for(int nextRank = rank2runnerIndex.size(); nextRank <= rank; nextRank++) {
      int subRunnerWithMin = 0;
      for(int i = 1; i < subRunners.length; i++) {
        if((nextSubRunnerRank[i] >= 0) &&
            (subRunners[i].getDocumentScore(nextSubRunnerRank[i]) <
            subRunners[subRunnerWithMin].getDocumentScore(nextSubRunnerRank[subRunnerWithMin]))) {
          subRunnerWithMin = i;
        }
      }
      // consume the next doc from subRunnerWithMin
      rank2runnerIndex.add(subRunnerWithMin);
      rank2subRank.add(nextSubRunnerRank[subRunnerWithMin]++);
      if(nextSubRunnerRank[subRunnerWithMin] >= subRunners[subRunnerWithMin].getDocumentsCount()) {
        // this runner has run out of documents
        nextSubRunnerRank[subRunnerWithMin] = -1;
      }
    }
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentID(int)
   */
  @Override
  public int getDocumentID(int rank) throws IndexOutOfBoundsException,
    IOException {
    checkRank(rank);
    int subId = subRunners[rank2runnerIndex.getInt(rank)].getDocumentID(rank2subRank.getInt(rank));
    return subId * subRunners.length + rank2runnerIndex.getInt(rank);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentScore(int)
   */
  @Override
  public double getDocumentScore(int rank) throws IndexOutOfBoundsException,
    IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentScore(rank2subRank.getInt(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentHits(int)
   */
  @Override
  public List<Binding> getDocumentHits(int rank)
    throws IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentHits(rank2subRank.getInt(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentText(int, int, int)
   */
  @Override
  public String[][] getDocumentText(int rank, int termPosition, int length)
    throws IndexException, IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentText(rank2subRank.getInt(rank), termPosition, length);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentURI(int)
   */
  @Override
  public String getDocumentURI(int rank) throws IndexException,
    IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentURI(rank2subRank.getInt(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentTitle(int)
   */
  @Override
  public String getDocumentTitle(int rank) throws IndexException,
    IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentTitle(rank2subRank.getInt(rank));
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataField(int, java.lang.String)
   */
  @Override
  public Serializable getDocumentMetadataField(int rank, String fieldName)
    throws IndexException, IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentMetadataField(rank2subRank.getInt(rank), fieldName);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataFields(int, java.util.Set)
   */
  @Override
  public Map<String, Serializable> getDocumentMetadataFields(int rank,
                                                             Set<String> fieldNames)
    throws IndexException, IndexOutOfBoundsException, IOException {
    checkRank(rank);
    return subRunners[rank2runnerIndex.getInt(rank)].getDocumentMetadataFields(rank2subRank.getInt(rank), fieldNames);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#renderDocument(int, java.lang.Appendable)
   */
  @Override
  public void renderDocument(int rank, Appendable out) throws IOException,
    IndexException {
    checkRank(rank);
    subRunners[rank2runnerIndex.getInt(rank)].renderDocument(rank2subRank.getInt(rank), out);
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#close()
   */
  @Override
  public void close() throws IOException {
    for(QueryRunner r : subRunners) {
      r.close();
    }
  }
}
