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
}
