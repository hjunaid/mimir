package gate.mimir.web;

import gate.mimir.web.Index;

import javax.servlet.http.HttpServletResponse;

/**
 * Controller for operations common to all types of index.
 */
class IndexManagementController {
  static defaultAction = "admin"
  
  def info = {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
      redirect(uri:'/')
      return
    }

    [indexInstance:indexInstance]
  }
  
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
  
  def stateBin = {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(indexInstance){
      render(text:indexInstance.state, contentType:'text/plain', encoding:'UTF-8')
    }  else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
      "Index ID ${params.indexId} not known!")
    }
  }
  
  // Binary protocol for remote indexes.
  
  def infoBin = {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(indexInstance) {
      render("This is index ${params.indexId}")
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
      "Index ID ${params.indexId} not known!")
    }
  }
  
  def closeBin = {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(indexInstance){
      indexInstance.close()
      render(text:"OK", contentType:'text/plain', encoding:'UTF-8')
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
      "Index ID ${params.indexId} not known!")
    }
  }
  
  def closingProgressBin = {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(indexInstance){
      try{
        //we have all required parameters
        double value = indexInstance.closingProgress()
        new ObjectOutputStream (response.outputStream).withStream {stream -> 
          stream.writeDouble(value)
        }
      }catch(Exception e){
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
            "Error while obtaining the closing progress: \"" + e.getMessage() + "\"!")
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Index ID ${params.indexId} not known!")
    }
  }
}
