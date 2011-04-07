package gate.mimir.web

import gate.mimir.web.Index;

import javax.servlet.http.HttpServletResponse

class IndexManagementController {
  
  static defaultAction = 'index'
  
  def index = {
    redirect(controller: "search", params:[indexId:params.indexId])
  }
  
  /**
   * Requests a URL to which documents should be posted.  Clients must treat
   * the URL returned from this method as transient and should re-query each
   * time they want to submit a document for indexing.  This is because a
   * federated index may return a different URL each time.
   */
  def indexUrl = {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      if(theIndex.state == Index.INDEXING) {
        render(text:request.theIndex.indexUrl(), contentType:"text/plain",
            encoding:"UTF-8")
      } else {
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Index with ID ${params.indexId} is in state ${theIndex.state}")
      }
    }
  }

  def close = {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      if(theIndex.state == Index.INDEXING) {
        request.theIndex.close()
        render("OK")
      } else {
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Index with ID ${params.indexId} is in state ${theIndex.state}")
      }
    }
  }

  def closingProgressBin = {
    try {
      def theIndex = Index.findByIndexId(params.indexId)
      if(theIndex) {
        double value = theIndex.closingProgress()
        new ObjectOutputStream (response.outputStream).withStream {stream ->
          stream.writeDouble(value)
        }
      }
    } catch(Exception e){
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error while obtaining the closing progress: \"" + e.getMessage() + "\"!")
    }
  }

  /**
   * Takes a binary serialization of one or more GATE documents on the input
   * stream, deserializes it and passes it to the indexer.
   */
  def addDocuments = {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      if(theIndex.state == Index.INDEXING) {
        request.inputStream.withStream { stream ->
          theIndex.indexDocuments(stream)
        }
        render("OK")
      } else {
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Index with ID ${params.indexId} is in state ${theIndex.state}")
      }
    }
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

}
