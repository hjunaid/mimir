/*
 *  LocalIndexController.groovy
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
package gate.mimir.web

import gate.mimir.web.Index;
import gate.mimir.web.IndexTemplate;
import gate.mimir.web.LocalIndex;

import java.util.UUID;


class LocalIndexController {
  
  /**
   * Service for interacting with local indexes.
   */
  def localIndexService
  
  def index = { redirect(uri:"/") }
  
  // the delete, save and update actions only accept POST requests
  static allowedMethods = [delete:'POST', save:'POST', update:'POST']
  
  def list = {
    params.max = Math.min( params.max ? params.max.toInteger() : 10,  100)
    [ localIndexInstanceList: LocalIndex.list( params ), localIndexInstanceTotal: LocalIndex.count() ]
  }
  
  def show = {
    def localIndexInstance = LocalIndex.get( params.id )
    
    if(!localIndexInstance) {
      flash.message = "LocalIndex not found with id ${params.id}"
      redirect(controller:'indexManagement', action:'home')
    }
    else { return [ localIndexInstance : localIndexInstance ]
    }
  }
  
  
  def deleteFlow = {
    triage {
      action {
        flow.localIndexInstance = LocalIndex.get(params.id)          
      }
      on("success").to("confirm")
    }
    
    confirm {
      on("delete") {
        flow.deleteFiles = params.deleteFiles
      }.to("delete")
      on("cancel").to("cancel")
    }
    
    delete {
      action {
        localIndexService.deleteIndex(flow.localIndexInstance, flow.deleteFiles)
      }
      on("success").to("exit")
      on(Exception).to("error")
    }
    
    cancel {
    }
    
    exit {
    }
    
    error {
    }
  }
  
  //    def delete = {
  //        def localIndexInstance = LocalIndex.get( params.id )
  //        if(localIndexInstance) {
  //            try {
  //                localIndexInstance.delete(flush:true)
  //                flash.message = "LocalIndex ${params.id} deleted"
  //                redirect(action:list)
  //            }
  //            catch(org.springframework.dao.DataIntegrityViolationException e) {
  //                flash.message = "LocalIndex ${params.id} could not be deleted"
  //                redirect(action:show,id:params.id)
  //            }
  //        }
  //        else {
  //            flash.message = "LocalIndex not found with id ${params.id}"
  //            redirect(action:list)
  //        }
  //    }
  
  def edit = {
    def localIndexInstance = LocalIndex.get( params.id )
    
    if(!localIndexInstance) {
      flash.message = "LocalIndex not found with id ${params.id}"
      redirect(uri:"/")
    }
    else {
      return [ localIndexInstance : localIndexInstance ]
    }
  }
  
  
  def getState = {
    def localIndexInstance = LocalIndex.get( params.id )
    render(localIndexInstance.state, contentType:"text/plain")
  }
  
  def update = {
    def localIndexInstance = LocalIndex.get( params.id )
    if(localIndexInstance) {
      if(params.version) {
        def version = params.version.toLong()
        if(localIndexInstance.version > version) {
          
          localIndexInstance.errors.rejectValue("version", "localIndex.optimistic.locking.failure", "Another user has updated this LocalIndex while you were editing.")
          render(view:'edit',model:[localIndexInstance:localIndexInstance])
          return
        }
      }
      localIndexInstance.properties = params
      if(!localIndexInstance.hasErrors() && localIndexInstance.save()) {
        flash.message = "LocalIndex ${params.id} updated"
        redirect(action:show,id:localIndexInstance.id)
      }
      else {
        render(view:'edit',model:[localIndexInstance:localIndexInstance])
      }
    }
    else {
      flash.message = "LocalIndex not found with id ${params.id}"
      redirect(uri:"/")
    }
  }
  
  /**
   * Create a new index, open for indexing.
   */
  def create = {
    def localIndexInstance = new LocalIndex()
    localIndexInstance.properties = params
    return ['localIndexInstance':localIndexInstance]
  }
  
  /**
   * Action to create a new index for indexing.
   */
  def save = {
    def indexTemplateInstance = IndexTemplate.get(params.indexTemplateId)
    if(!indexTemplateInstance) {
      flash.message = "Index template not found with ID ${params.indexTemplateId}"
      redirect(uri:"/")
      return
    }
    
    def localIndexInstance = new LocalIndex(indexId:UUID.randomUUID().toString())
    localIndexInstance.name = params.name
    localIndexInstance.uriIsExternalLink = params.uriIsExternalLink ? true : false
    localIndexInstance.state = Index.INDEXING
    try {
      def tempFile = File.createTempFile('index-', '.mimir',
            new File(grailsApplication.config.gate.mimir.indexBaseDirectory))
      tempFile.delete()
      localIndexInstance.indexDirectory = tempFile.absolutePath
    }
    catch(IOException e) {
      flash.message = "Couldn't create directory for new index: ${e}"
      log.info("Couldn't create directory for new index", e)
      redirect(uri:"/")
      return
    }
    if(!localIndexInstance.hasErrors() && localIndexInstance.save()) {
      try{
        localIndexService.createIndex(localIndexInstance,
            indexTemplateInstance)
        flash.message = "LocalIndex \"${localIndexInstance.name}\" created"
        redirect(controller:"indexAdmin", action:"admin",
            params:[indexId:localIndexInstance.indexId])
        return
      }catch (Exception e) {
        flash.message = "Could not create local index. Problem was: \"${e.message}\"."
        localIndexInstance.delete()
        redirect(uri:"/")
        return
      }
    }
    else {
      render(view:'create',model:[localIndexInstance:localIndexInstance])
    }
  }
  
  /**
   * Register an existing index directory to be opened for searching.
   */
  def importIndex = {
    def localIndexInstance = new LocalIndex()
    localIndexInstance.properties = params
    return ['localIndexInstance':localIndexInstance]
  }
  
  def doImport = {
    def localIndexInstance = new LocalIndex()
    localIndexInstance.name = params.name
    localIndexInstance.uriIsExternalLink = params.uriIsExternalLink ? true : false
    localIndexInstance.indexDirectory = params.indexDirectory
    localIndexInstance.state = Index.SEARCHING
    // sanity check that the specified directory exists and has the right
    // stuff in it
    def indexDir = new File(params.indexDirectory)
    if(!indexDir.isDirectory()) {
      localIndexInstance.errors.rejectValue('indexDirectory', 'gate.mimir.web.LocalIndex.indexDirectory.notexist')
      render(view:'importIndex', model:[localIndexInstance:localIndexInstance])
    }
    else if(!new File(indexDir, 'config.xml').isFile() ||
    !new File(indexDir, 'mg4j').isDirectory()) {
      localIndexInstance.errors.rejectValue('indexDirectory', 'gate.mimir.web.LocalIndex.indexDirectory.notindex')
      render(view:'importIndex', model:[localIndexInstance:localIndexInstance])
    }
    else {
      localIndexInstance.indexId = UUID.randomUUID().toString()
      if(localIndexInstance.save()) {
        flash.message = "Local Index \"${localIndexInstance.name}\" imported"
        redirect(controller:"indexAdmin", action:"admin", 
                 params:[indexId:localIndexInstance.indexId])
      }
      else {
        render(view:'importIndex', model:[localIndexInstance:localIndexInstance])
      }
    }
  }
}
