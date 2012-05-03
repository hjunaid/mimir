/*
 *  FederatedIndex.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
package gate.mimir.web;

import org.hibernate.proxy.HibernateProxy;

import gate.mimir.index.mg4j.zipcollection.DocumentData;
import gate.mimir.search.QueryRunner
import gate.mimir.search.FederatedQueryRunner

/**
 * An index exposing a collection of other indexes.
 */
class FederatedIndex extends Index {
  static hasMany = [indexes:Index]

  List indexes

  /**
   * Turn off lazy loading of the indexes relation
   */
  static mapping = {
    indexes(fetch:"join")
  }

  // behaviour

  /**
   * The service that does the clever stuff.
   */
  transient federatedIndexService

  /**
   * Delegates to the sub-index that is next in round-robin order.
   */
  String indexUrl() {
    int nextIndex = federatedIndexService.getNextIndex(this)
    def theIndex = indexes[nextIndex]
    //TODO: check if this is really needed.
    // un-proxy if necessary
//    if(theIndex instanceof HibernateProxy) {
//      theIndex = theIndex.hibernateLazyInitializer.implementation
//    }
    theIndex.indexUrl()
  }

  void indexDocuments(InputStream stream) {
    log.error("FederatedIndex.indexDocuments called")
    throw new UnsupportedOperationException(
        "FederatedIndex does not support indexing of documents directly, " +
        "you should send the documents straight to a sub-index.")
  }

  void close() {
    indexes.each {
      it.close()
    }
  }

  /**
   * Hand off the query to the sub-indexes and return a federated runner
   * delegating to the returned sub-query runners.
   */
  QueryRunner startQuery(String query) {
    QueryRunner[] subRunners = new QueryRunner[indexes.size()]
    indexes.eachWithIndex { subIndex, i ->
      subRunners[i] = subIndex.startQuery(query)
    }
    return new FederatedQueryRunner(subRunners)
  }
  
  /**
   * Gets the {@link DocumentData} value for a given document ID.
   * @param documentID
   * @return
   */
  DocumentData getDocumentData(int documentID) {
    return federatedIndexService.getDocumentData(this, documentID)
  }
  
  
  /* (non-Javadoc)
   * @see gate.mimir.web.Index#renderDocument(int, java.io.Writer)
   */
  @Override
  public void renderDocument(int documentID, Appendable out) {
    federatedIndexService.renderDocument(this, documentID, out)
  }
  
  /**
   * Returns the annotations config for this index.
   */
  String[][] annotationsConfig() {
    //all subindexes have the same config
    return indexes[0].annotationsConfig()
  }
  
  public double closingProgress() {
    return federatedIndexService.findProxy(this).closingProgress
  }

  public void deleteDocuments(Collection<Integer> documentIds) {
    federatedIndexService.deleteDocuments(this, documentIds)
  }

  public void undeleteDocuments(Collection<Integer> documentIds) {
    federatedIndexService.undeleteDocuments(this, documentIds)
  }
  
}
