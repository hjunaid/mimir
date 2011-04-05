package gate.mimir.gus

import gate.mimir.search.QueryRunner;
import gate.mimir.web.Index;

class GusController {

  static defaultAction = "index"

  /**
   * Search service (autowired).
   */
  def searchService

  def index = {
    redirect(action:"search", params:params)
  }

  /**
   * Search action - nothing to do, all the logic is in the GSP/GWT.
   */
  def search = {
    Index index = Index.findByIndexId(params.indexId)
    [indexId:params.indexId, uriIsLink:index?.uriIsExternalLink]
  }

  /**
   * Render the content of the given document.  Most of the magic happens in
   * the documentContent tag of the GusTagLib.
   */
  def document = {
    QueryRunner runner = searchService.getQueryRunner(params.queryId)
    if(runner){
      Index index = Index.findByIndexId(params.indexId)
      return [
          indexId:params.indexId,
          documentId:params.id,
          queryId:params.queryId,
          documentTitle:runner.getDocumentTitle(params.id as int),          
          baseHref:index?.isUriIsExternalLink() ? runner.getDocumentURI(params.id as int) : null 
          ]
    } else {
      //query has expired
      return [
        queryId:params.queryId
      ]
    }
  }
  
  /**
   * Action that forwards to the real GWT RPC controller.
   */
  def rpc = {
    forward(controller:'gwt', action:'index', params:[module:'gate.mimir.gus.Application'])
  }
}
