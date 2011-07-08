/*
 *  IndexAdminController.groovy
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

import gate.mimir.web.Index;

import javax.servlet.http.HttpServletResponse;

/**
 * Controller for operations common to all types of index.
 */
class IndexAdminController {
  static defaultAction = "admin"

  def admin = {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
      redirect(uri:'/')
      return
    }

    [indexInstance:indexInstance]
  }
  
  def closingProgress = {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(!indexInstance) {
      render("No such index ${params.indexId}")
      return
    }

    double progress = indexInstance.closingProgress()
    render(mimir.progressbar(value:progress))
  }
  
  def close = {
    def indexInstance = Index.findByIndexId(params.indexId)
    
    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
    }
    else {
      if(indexInstance.state == Index.CLOSING) {
        flash.message = "Index  \"${indexInstance.name}\" is already in the process of closing."
      }
      else if(indexInstance.state == Index.INDEXING) {
        indexInstance.close()
        flash.message = "Index  \"${indexInstance.name}\" closing.  This may take a long time."
      }
    }
    redirect(action:admin, params:params)    
  }
  
  def deletedDocuments = {
    def indexInstance = Index.findByIndexId(params.indexId)

    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
      redirect(action:admin, params:params)
    }
    else if(indexInstance.state != Index.SEARCHING) {
      flash.message = "Index \"${indexInstance.name}\" is not in the searching state"
      redirect(action:admin, params:params)
    }
    else {
      return [indexInstance:indexInstance, documents:new DeletedDocumentsCommand(documentIds:"")]
    }
  }

  /**
   * deleteDocuments and undeleteDocuments are almost identical, so we
   * use a common implementation.
   */
  def doDeleteOrUndelete = { DeletedDocumentsCommand cmd ->
    def indexInstance = Index.findByIndexId(params.indexId)

    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
      redirect(action:admin, params:params)
    }
    else if(indexInstance.state != Index.SEARCHING) {
      flash.message = "Index \"${indexInstance.name}\" is not in the searching state"
      redirect(action:admin, params:params)
    }
    else {
      if(cmd.hasErrors()) {
        // invalid operation
        render(view:'deletedDocuments', model:[indexInstance:indexInstance, documents:cmd])
      } else {
        def lastTriedNumber = null
        try {
          def idsToDelete = cmd.documentIds.split(/\p{javaWhitespace}+/).collect {
            lastTriedNumber = it
            return it.toInteger()
          }

          // if we get to here we have a list of Integers
          indexInstance."${cmd.operation}Documents"(idsToDelete)
          flash.message = "${idsToDelete.size()} document(s) marked as ${cmd.operation}d"
          redirect(action:admin, params:[indexId:params.indexId])
        }
        catch(NumberFormatException e) {
          cmd.errors.rejectValue("documentIds",
            "gate.mimir.web.DeletedDocumentsCommand.documentIds.notnumber",
            [lastTriedNumber] as String[],
            "\"${lastTriedNumber}\" is not a valid document ID")
          render(view:'deletedDocuments', model:[indexInstance:indexInstance, documents:cmd])
        }
      }
    }
  }
}


/**
 * Simple command object used by the deleteDocuments and undeleteDocuments
 * actions so we can attach errors to the field.
 */
class DeletedDocumentsCommand {
  String documentIds
  String operation

  static constraints = {
    operation(inList:['delete', 'undelete'])
  }
}
