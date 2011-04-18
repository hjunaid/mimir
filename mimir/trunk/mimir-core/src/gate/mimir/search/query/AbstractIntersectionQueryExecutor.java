/*
 *  AbstractIntersectionQueryExecutor.java
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
 *  Valentin Tablan, 8 Jul 2009
 *
 *  $Id$
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import java.io.IOException;


/**
 * An abstract query executor that implements the nextDocument() functionality
 * shared between all query executors that combine a set of sub-executors and
 * only need to return results on common documents. 
 */
public abstract class AbstractIntersectionQueryExecutor extends
                                                       AbstractQueryExecutor {
  
  
  /**
   * Constructor from {@link QueryEngine}.
   * @throws IOException if the index files cannot be accessed.
   */
  public AbstractIntersectionQueryExecutor(QueryEngine engine, 
          QueryNode... nodes) throws IOException {
    super(engine);
    this.nodes = nodes;
    // prepare all the executors
    this.executors = new QueryExecutor[nodes.length];
    this.nextDocIDs = new int[executors.length];
    for(int i = 0; i < nodes.length; i++) {
      executors[i] = nodes[i].getQueryExecutor(engine);
      nextDocIDs[i] = executors[i].nextDocument(-1);
    }

  }


  public int nextDocument(int greaterThan) throws IOException {
    if(closed) return latestDocument = -1;
    if(latestDocument == -1) return latestDocument;
    // we want all documentIDs to converge to max, which should be at least
    // greaterThan + 1
    // the max value will only move up!
    // Note that the greterThan value can be anything (e.g.-100), so we need to  
    // force the advance by comparing to latestDocument.
    int max = Math.max(latestDocument, greaterThan)  + 1;
    // move all documentIDs to at or over current max,
    // until they all have the same ID
    boolean doneAdvancing = false;
    while(!doneAdvancing) {
      doneAdvancing = true;
      for(int i = 0; i < executors.length; i++) {
        if(nextDocIDs[i] == -1) {
          // one executor has run out of documents -> we're done here!
          return latestDocument = -1;
        }else if(nextDocIDs[i] < max) {
          // this needs to move forward to at least max
          nextDocIDs[i] = executors[i].nextDocument(max - 1);
          doneAdvancing = false;
        }else if(nextDocIDs[i] > max) {
          max = nextDocIDs[i];
          //we need to move all others to the same value
          doneAdvancing = false;
        }
      }
    }
    // If we reached this point, all executors are pointing to the same
    // document (max).
    return latestDocument = max;
  }
  
  
  /**
   * The {@link QueryExecutor}s for the contained nodes.
   */
  protected QueryExecutor[] executors;
  
  /**
   * The sub-queries
   */
  protected QueryNode[] nodes;
  
  /**
   * An array of current nextDocumentID values, for all of the sub nodes.
   */
  protected int[] nextDocIDs;
  
}
