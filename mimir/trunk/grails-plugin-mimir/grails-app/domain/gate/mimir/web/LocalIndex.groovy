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
        '/buildIndex/addDocuments'
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
