package gate.mimir.web

import gate.mimir.web.Index;

import javax.servlet.http.HttpServletResponse

class BuildIndexController {
  /**
   * Check that the index identified by the indexId parameter exists and is in
   * indexing mode.  If so, store the relevant Index object as a request
   * attribute.
   */
  def beforeInterceptor = {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      if(theIndex.state == Index.INDEXING) {
        request.theIndex = theIndex
        return true
      }
      else if(theIndex.state == Index.SEARCHING) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Index with ID ${params.indexId} is open for searching, not indexing.")
      }
      else if(theIndex.state == Index.CLOSING) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Index with ID ${params.indexId} is in the process of closing")
      }
    }
    else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "No index with ID ${params.indexId}")
    }
    return false
  }

  /**
   * Requests a URL to which documents should be posted.  Clients must treat
   * the URL returned from this method as transient and should re-query each
   * time they want to submit a document for indexing.  This is because a
   * federated index may return a different URL each time.
   */
  def indexUrl = {
    render(text:request.theIndex.indexUrl(), contentType:"text/plain", 
           encoding:"UTF-8")
  }

  /**
   * Copy of the indexUrl action under a different name, for use in the remote
   * protocol.
   */
  def indexUrlBin = {
    render(text:request.theIndex.indexUrl(), contentType:"text/plain", 
           encoding:"UTF-8")
  }

  /**
   * Takes a binary serialization of one or more GATE documents on the input
   * stream, deserializes it and passes it to the indexer.
   */
  def addDocuments = {
    request.inputStream.withStream { stream ->
      request.theIndex.indexDocuments(stream)
    }
    render("OK")
  }
}
