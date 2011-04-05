

package gate.mimir.web

import gate.mimir.web.FederatedIndex;
import gate.mimir.web.Index;

import java.util.UUID;

class FederatedIndexController {
    
    def federatedIndexService

    def index = { redirect(uri:"/") }

    // the delete, save and update actions only accept POST requests
    static allowedMethods = [delete:'POST', save:'POST', update:'POST']

    def list = {
        params.max = Math.min( params.max ? params.max.toInteger() : 10,  100)
        [ federatedIndexInstanceList: FederatedIndex.list( params ), federatedIndexInstanceTotal: FederatedIndex.count() ]
    }

    def show = {
        def federatedIndexInstance = FederatedIndex.get( params.id )

        if(!federatedIndexInstance) {
            flash.message = "FederatedIndex not found with id ${params.id}"
            redirect(uri:"/")
        }
        else { return [ federatedIndexInstance : federatedIndexInstance ] }
    }

    def delete = {
        def federatedIndexInstance = FederatedIndex.get( params.id )
        if(federatedIndexInstance) {
            try {
                def hibernateId = federatedIndexInstance.id
                String name = federatedIndexInstance.name 
                federatedIndexInstance.delete(flush:true)
                federatedIndexService.indexDeleted(hibernateId)
                flash.message = "FederatedIndex \"${name}\" deleted"
                redirect(uri:"/")
            }
            catch(org.springframework.dao.DataIntegrityViolationException e) {
                flash.message = "FederatedIndex ${params.id} could not be deleted"
                redirect(action:show,id:params.id)
            }
        }
        else {
            flash.message = "FederatedIndex not found with id ${params.id}"
            redirect(uri:"/")
        }
    }

    def edit = {
        def federatedIndexInstance = FederatedIndex.get( params.id )

        if(!federatedIndexInstance) {
            flash.message = "FederatedIndex not found with id ${params.id}"
            redirect(uri:"/")
        }
        else {
            return [ federatedIndexInstance : federatedIndexInstance ]
        }
    }

    def update = {
        def federatedIndexInstance = FederatedIndex.get( params.id )
        if(federatedIndexInstance) {
            if(params.version) {
                def version = params.version.toLong()
                if(federatedIndexInstance.version > version) {
                    
                    federatedIndexInstance.errors.rejectValue("version", "federatedIndex.optimistic.locking.failure", "Another user has updated this FederatedIndex while you were editing.")
                    render(view:'edit',model:[federatedIndexInstance:federatedIndexInstance])
                    return
                }
            }
            federatedIndexInstance.properties = params
            // check that all the sub-indexes are in the right state
            if(federatedIndexInstance.indexes.any { it.state != federatedIndexInstance.state }) {
                flash.message = "All sub-indexes of a federated index must have the same state as the federated index itself"
                render(view:'edit', model:[federatedIndexInstance:federatedIndexInstance])
                return
            }
            if(!federatedIndexInstance.hasErrors() && federatedIndexInstance.save()) {
                flash.message = "FederatedIndex ${params.id} updated"
                redirect(action:show,id:federatedIndexInstance.id)
            }
            else {
                render(view:'edit',model:[federatedIndexInstance:federatedIndexInstance])
            }
        }
        else {
            flash.message = "FederatedIndex not found with id ${params.id}"
            redirect(uri:"/")
        }
    }

    def create = {
        def federatedIndexInstance = new FederatedIndex()
        federatedIndexInstance.properties = params
        return ['federatedIndexInstance':federatedIndexInstance]
    }

    def save = {
        def federatedIndexInstance = new FederatedIndex(params)
        federatedIndexInstance.indexId =  UUID.randomUUID().toString()
        // check that all the sub-indexes are in the right state
        federatedIndexInstance.state = federatedIndexInstance.indexes.get(0).state 
        if(federatedIndexInstance.indexes.any { it.state != federatedIndexInstance.state }) {
            flash.message = "All sub-indexes of a federated index must have the same state as the federated index itself"
            render(view:'create', model:[federatedIndexInstance:federatedIndexInstance])
            return
        }
        if(!federatedIndexInstance.hasErrors() && federatedIndexInstance.save()) {
          //make sure the proxy is created
          federatedIndexService.findProxy(federatedIndexInstance)
          flash.message = "FederatedIndex \"${federatedIndexInstance.name}\" created"
          if(federatedIndexInstance.state == Index.INDEXING) {
            federatedIndexService.registerIndex(federatedIndexInstance)
          }
          redirect(action:show,id:federatedIndexInstance.id)
        }
        else {
            render(view:'create',model:[federatedIndexInstance:federatedIndexInstance])
        }
    }
}
