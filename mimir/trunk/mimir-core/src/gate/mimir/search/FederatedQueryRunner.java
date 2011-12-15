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

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.log4j.Logger;

/**
 * A {@link QueryRunner} that presents a set of sub-indexes (represented by 
 * their own QueryRunners) as a single index.
 */
public class FederatedQueryRunner implements QueryRunner {
  
  /**
   * Background action responsible with updating the document statistics from 
   * the sub-runners.
   */
  private class DocumentDataUpdater implements Runnable {

    @Override
    public void run() {
      boolean allDone = false;
      while(!allDone) {
        allDone = true;
        int newCurrentDocCount = 0;
        for(QueryRunner aRunner : subrunners) {
          int subDocCount = aRunner.getDocumentsCount();
          if(subDocCount > 0) {
            newCurrentDocCount += subDocCount;
          } else {
            allDone = false;
            newCurrentDocCount += aRunner.getDocumentsCurrentCount();
          }
        }
        currentDocumentsCount = newCurrentDocCount;
        if(allDone) {
          documentsCount = newCurrentDocCount;
        } else {
          try {
            // wait a while and try again
            Thread.sleep(100);
          } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
  }
  
  /**
   * The total number of result documents (or -1 if not yet known).
   */
  private volatile int documentsCount;
  
  /**
   * The current number of documents. After all documents have been retrieved, 
   * this value is identical to {@link #documentsCount}. 
   */
  private volatile int currentDocumentsCount;
  
  private volatile boolean closed;
  
  /**
   * Shared Logger
   */
  private static Logger logger = Logger.getLogger(FederatedQueryRunner.class);
  
  /**
   * The query runners for the sub-indexes.
   */
  protected QueryRunner[] subrunners;
  
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
  
  /**
   * The score for each result document. 
   */
  protected DoubleList rank2score;
  
  /**
   * The documentID (within the original sub-runner) for each result document.
   */
  protected IntList rank2subDocumentId;
  
  public FederatedQueryRunner(QueryRunner[] subrunners, Executor threadSource) {
    this.subrunners = subrunners;
    DocumentDataUpdater docDataUpdater = new DocumentDataUpdater();
    if(threadSource != null) {
      threadSource.execute(docDataUpdater);
    } else {
      new Thread(docDataUpdater, 
        DocumentDataUpdater.class.getCanonicalName()).start();
    }    
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCount()
   */
  @Override
  public int getDocumentsCount() {
    return documentsCount;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getCurrentDocumentsCount()
   */
  @Override
  public int getDocumentsCurrentCount() {
    return (documentsCount < 0) ? currentDocumentsCount : documentsCount;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentID(int)
   */
  @Override
  public int getDocumentID(int rank) throws IndexOutOfBoundsException,
    IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentScore(int)
   */
  @Override
  public double getDocumentScore(int rank) throws IndexOutOfBoundsException,
    IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentHits(int)
   */
  @Override
  public List<Binding> getDocumentHits(int rank)
    throws IndexOutOfBoundsException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentText(int, int, int)
   */
  @Override
  public String[][] getDocumentText(int rank, int termPosition, int length)
    throws IndexException, IndexOutOfBoundsException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentURI(int)
   */
  @Override
  public String getDocumentURI(int rank) throws IndexException,
    IndexOutOfBoundsException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentTitle(int)
   */
  @Override
  public String getDocumentTitle(int rank) throws IndexException,
    IndexOutOfBoundsException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataField(int, java.lang.String)
   */
  @Override
  public Serializable getDocumentMetadataField(int rank, String fieldName)
    throws IndexException, IndexOutOfBoundsException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataFields(int, java.util.Set)
   */
  @Override
  public Map<String, Serializable> getDocumentMetadataFields(int rank,
                                                             Set<String> fieldNames)
    throws IndexException, IndexOutOfBoundsException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#renderDocument(int, java.lang.Appendable)
   */
  @Override
  public void renderDocument(int rank, Appendable out) throws IOException,
    IndexException {
    // TODO Auto-generated method stub
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#close()
   */
  @Override
  public void close() throws IOException {
    // TODO Auto-generated method stub
  }
}
