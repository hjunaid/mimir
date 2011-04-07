package gate.mimir.web;

import gate.mimir.web.Index;
import gate.mimir.web.RemoteIndex;


/**
 * A controller for remote indexes.
 */
class RemoteIndexController {
  
  
  /**
   * Pointer to the service (autowired)
   */
  def remoteIndexService
  
  /**
   * By default we list the indexes.
   */
  def index = { redirect(uri:"/") }
  
  // the delete, save and update actions only accept POST requests
  static allowedMethods = [delete:'POST', save:'POST', update:'POST']
  
  def list = {
    params.max = Math.min( params.max ? params.max.toInteger() : 10,  100)
    [ remoteIndexInstanceList: RemoteIndex.list( params ), remoteIndexInstanceTotal: RemoteIndex.count() ]
  }
  
  def show = {
    def remoteIndexInstance = RemoteIndex.get( params.id )

    if(!remoteIndexInstance) {
        flash.message = "Remote Index not found with id ${params.id}"
        redirect(uri:"/")
    }
    else { return [ remoteIndexInstance : remoteIndexInstance ] }
  }

  def delete = {
      def remoteIndexInstance = RemoteIndex.get( params.id )
      if(remoteIndexInstance) {
          try {
              def hibernateId = remoteIndexInstance.id
              String name = remoteIndexInstance.name
              remoteIndexInstance.delete(flush:true)
              remoteIndexService.indexDeleted( hibernateId )
              flash.message = "Remote Index \"${name}\" deleted"
              redirect(uri:"/")
          }
          catch(org.springframework.dao.DataIntegrityViolationException e) {
              flash.message = "Remote Index ${params.id} could not be deleted"
              redirect(action:show, id:params.id)
          }
      }
      else {
          flash.message = "Remote Index not found with id ${params.id}"
          redirect(uri:"/")
      }
  }

  def edit = {
      def remoteIndexInstance = RemoteIndex.get( params.id )
  
      if(!remoteIndexInstance) {
          flash.message = "Remote Index not found with id ${params.id}"
          redirect(uri:"/")
      }
      else {
          return [ remoteIndexInstance : remoteIndexInstance ]
      }
  }  
  
  def update = {
    def remoteIndexInstance = RemoteIndex.get( params.id )
    if(remoteIndexInstance) {
      if(params.version) {
        def version = params.version.toLong()
        if(remoteIndexInstance.version > version) {
          
          remoteIndexInstance.errors.rejectValue("version", "remoteIndex.optimistic.locking.failure", "Another user has updated this Remote Index while you were editing.")
          render(view:'edit',model:[remoteIndexInstance:remoteIndexInstance])
          return
        }
      }
      remoteIndexInstance.properties = params
      if(!remoteIndexInstance.hasErrors() && remoteIndexInstance.save()) {
        flash.message = "Remote Index ${params.id} updated"
        redirect(action:show,id:remoteIndexInstance.id)
      }
      else {
        render(view:'edit',model:[remoteIndexInstance:remoteIndexInstance])
      }
    }
    else {
      flash.message = "Remote Index not found with id ${params.id}"
      redirect(uri:"/")
    }
  }
  
  /**
   * Create a new index, open for indexing.
   */
  def create = {
    def remoteIndexInstance = new RemoteIndex()
    remoteIndexInstance.properties = params
    return ['remoteIndexInstance':remoteIndexInstance]
  }
  
  /**
   * Action to create a new index for indexing.
   */
  def save = {
    def remoteIndexInstance = new RemoteIndex(indexId:UUID.randomUUID().toString())
    remoteIndexInstance.name = params.name
    remoteIndexInstance.uriIsExternalLink = params.uriIsExternalLink ? true : false
    remoteIndexInstance.remoteUrl = params.remoteUrl
    //start with some default state 
    remoteIndexInstance.state = Index.WORKING
    if(!remoteIndexInstance.hasErrors() && remoteIndexInstance.save()) {
      //make sure the proxy object is created
      remoteIndexService.findProxy(remoteIndexInstance)
      flash.message = "Remote Index \"${remoteIndexInstance.name}\" created"
      redirect(controller:"indexAdmin", action:"admin", 
        params:[indexId:remoteIndexInstance.indexId])
    }
    else {
      render(view:'create',model:[remoteIndexInstance:remoteIndexInstance])
    }
  }
    
}