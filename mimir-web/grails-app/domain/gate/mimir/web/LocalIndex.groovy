/*
 *  LocalIndex.groovy
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


import java.io.Writer;

import gate.mimir.index.mg4j.zipcollection.DocumentData
import gate.mimir.search.QueryRunner
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.ParseException
import gate.mimir.search.terms.TermsQuery;
import gate.mimir.search.terms.TermsResultSet;
import gate.Document
import gate.Gate
import gate.creole.ResourceData
import gate.creole.ResourceInstantiationException

import org.springframework.web.context.request.RequestContextHolder
import grails.util.GrailsWebUtil



/**
 * Index stored locally on disk.
 */
class LocalIndex extends Index implements Serializable {
  /**
   * Path to the directory storing the index.
   */
  String indexDirectory


  /**
   * The scorer to be used during searching.
   */
  String scorer
  
  
  static constraints = {
    indexDirectory (nullable:false, blank:false)
    scorer (nullable:true, blank:true)
  }
  
  // behaviour

  /**
   * Grails service that actually interacts with a local index.
   */
  transient localIndexService

  String indexUrl() {
    // We want to use the createLink tag to generate the URL, but domain
    // objects can't call taglibs, so we fetch the currently-executing
    // controller - this will throw an IllegalStateException if called outside
    // the scope of a request.
    def controller = GrailsWebUtil.getControllerFromRequest(
        RequestContextHolder.currentRequestAttributes().currentRequest)

    // call the createIndexUrl tag through the current controller
    return controller.mimir.createIndexUrl([indexId:indexId]) + 
        '/manage/addDocuments'
  }

  void indexDocuments(InputStream stream) {
    def indexer = localIndexService.findIndexer(this)
    if(!indexer) {
      throw new IllegalStateException("Cannot find indexer for index ${this}")
    }

    new ObjectInputStream(stream).withStream { objectStream ->
      objectStream.eachObject { Document doc ->
        ResourceData rd = Gate.creoleRegister[doc.getClass().name]
        if(rd) {
          rd.addInstantiation(doc)
          indexer.indexDocument(doc)
        }
        else {
          throw new ResourceInstantiationException(
              "Could not find resource data for class ${doc.getClass().name}")
        }
      }
    }
  }

  void close() {
    localIndexService.close(this)
  }
  
  double closingProgress() {
    return localIndexService.closingProgress(this)
  }
  
  String[][] annotationsConfig() {
    return localIndexService.annotationsConfig(this)
  }

  QueryRunner startQuery(String queryString) throws ParseException {
    return localIndexService.getQueryRunner(this, queryString)
  }
  
  QueryRunner startQuery(QueryNode query) {
    return localIndexService.getQueryEngine(this).getQueryRunner(query)
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.web.Index#postTermsQuery(gate.mimir.search.terms.TermsQuery)
   */
  @Override
  public TermsResultSet postTermsQuery(TermsQuery query) {
    return query.execute(localIndexService.getQueryEngine(this))
  }

  /**
  * Gets the {@link DocumentData} value for a given document ID.
  * @param documentID
  * @return
  */
  DocumentData getDocumentData(long documentID) {
    return localIndexService.getDocumentData(this, documentID)
  }

  /* (non-Javadoc)
   * @see gate.mimir.web.Index#renderDocument(int, java.io.Writer)
   */
  @Override
  public void renderDocument(long documentID, Appendable out) {
    localIndexService.renderDocument(this, documentID, out)
  }

  void deleteDocuments(Collection<Long> documentIds) {
    localIndexService.deleteDocuments(this, documentIds)
  }

  void undeleteDocuments(Collection<Long> documentIds) {
    localIndexService.undeleteDocuments(this, documentIds)
  }
  
}
