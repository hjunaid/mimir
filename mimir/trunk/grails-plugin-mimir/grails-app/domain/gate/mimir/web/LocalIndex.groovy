/*
 *  LocalIndex.groovy
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
 *  $Id$
 */
package gate.mimir.web;


import gate.mimir.search.QueryRunner
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

  QueryRunner startQuery(String queryString) {
    return localIndexService.getQueryRunner(this, queryString)
  }
}
