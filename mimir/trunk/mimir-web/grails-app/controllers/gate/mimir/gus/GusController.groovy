/*
 *  GusController.groovy
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
    redirect(action:"gus", params:params)
  }

  /**
   * Search action - nothing to do, all the logic is in the GSP/GWT.
   */
  def gus = {
    Index index = Index.findByIndexId(params.indexId)
    [indexId:params.indexId, uriIsLink:index?.uriIsExternalLink]
  }

  /**
   * Render the content of the given document.  Most of the magic happens in
   * the documentContent tag of the GusTagLib.
   */
  def gusDocument = {
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
  def gusRpc = {
    forward(controller:'gwt', action:'index', params:[module:'gate.mimir.gus.Application'])
  }
}
