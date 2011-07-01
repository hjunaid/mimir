/*
 *  SearchController.groovy
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


import java.io.OutputStreamWriter;

import gate.mimir.web.Index;
import gate.mimir.web.SearchService;
import groovy.xml.StreamingMarkupBuilder;
import gate.mimir.search.query.Binding;

import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import gate.mimir.search.QueryRunner;


/**
 * A controller for searching mimir indexes known to this instance.
 */
class SearchController {
  
  /**
   * Constant for Error state
   */
  public static final String ERROR = "ERROR"

  /**
   * Constant for Success state
   */
  public static final String SUCCESS = "SUCCESS"
   
  /**
   * XML namespace for mimir request and response messages.
   */
  public static final String MIMIR_NAMESPACE = 'http://gate.ac.uk/ns/mimir'
  
  /**
   * Reference to the search service, autowired.
   */
  def searchService
  
  /**
   * Check that the index identified by the indexId parameter exists and is in
   * search mode.  If so, store the relevant Index object as a request
   * attribute.
   */
  def beforeInterceptor = {
    def theIndex = Index.findByIndexId(params.indexId)
    if(theIndex) {
      if(theIndex.state == Index.SEARCHING) {
        request.theIndex = theIndex
        return true
      }
      else if(theIndex.state == Index.INDEXING) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
            "Index with ID ${params.indexId} is open for indexing, not searching. Please close the index, then try your search again.")
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
   * By default just run the help action.
   */
  static defaultAction = "info"

  
  def info = {
    def indexInstance = Index.findByIndexId(params.indexId)
    if(!indexInstance) {
      flash.message = "Index not found with index id ${params.indexId}"
      redirect(uri:'/')
      return
    }
    [indexInstance:indexInstance]
  }

  /**
   * Default action: prints a short message explaining how to use the 
   * controller.
   * Parameters: none.
   */
  def help = {
    return [index:request.theIndex] 
  }
  
  def postQuery = {
    def p = params["request"] ?: params
    
    //get the query string
    String queryString = p["queryString"]
    try{
      String runnerId = searchService.postQuery(request.theIndex, queryString)
      //save the query ID in the session, so we can close it on expiry
      getSessionQueryIDs().add(runnerId)
      render(buildMessage(SUCCESS, null){
        queryId(runnerId)
      }, contentType:"text/xml")
    }catch(Exception e){
      log.error("Exception posting query", e)
      render(buildMessage(ERROR, e.message, null), contentType:"text/xml")
    }
  }
  
  def isActive = {
  	def p = params["request"] ?: params
  	
  	//get the query ID
  	String queryId = p["queryId"]
	  QueryRunner runner = searchService.getQueryRunner(queryId);
	  if(runner){
      render(buildMessage(SUCCESS, null){
        value(runner.isActive())
      }, contentType:"text/xml")
	  } else{
	    render(buildMessage(ERROR, "Query ID ${queryId} not known!", null), 
		    contentType:"text/xml")
	  }
  }

  def isComplete = {
    def p = params["request"] ?: params
    
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      render(buildMessage(SUCCESS, null){
        value(runner.isComplete())
      }, contentType:"text/xml")
    } else{
      render(buildMessage(ERROR, "Query ID ${queryId} not known!", null), 
        contentType:"text/xml")
    }
  }

  def hitCount = {
    def p = params["request"] ?: params
    
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      render(buildMessage(SUCCESS, null){
        value(runner.getHitsCount())
      }, contentType:"text/xml")
    } else{
      render(buildMessage(ERROR, "Query ID ${queryId} not known!", null), 
        contentType:"text/xml")
    }
  }


  /**
   * Gets the document statistics for a set of documents. The returned value is
   * the XML representation of a two-rows int array where:
   * <ul>
   *   <li>result[0][i] is the ID for the i<sup>th</sup> document</li>
   *   <li>result[1][i] is the hits count for the i<sup>th</sup> document</li>
   * </ul>
   */
  def docStats = {
    def p = params["request"] ?: params
    //a closure representing the return message
    def message;
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters
      def startIndexParam = p["startIndex"]
      if(startIndexParam){
        def docCountParam = p["count"]
        if(docCountParam){
          try{
            //we have all required parameters
            int startIndex = startIndexParam.toInteger()
            int docCount = docCountParam.toInteger()
            int[][] docStats = new int[docCount][2];
            for(int i = 0; i < docCount; i++){
              docStats[i][0] = runner.getDocumentID(i + startIndex)
              docStats[i][1] = runner.getDocumentHitsCount(i + startIndex)
            }
            
            message = buildMessage(SUCCESS, null){
              for(int i = 0; i < docCount; i++){
                delegate.document(id:docStats[i][0], hitCount:docStats[i][1])
              }
            }
          }catch(Exception e){
            message = buildMessage(ERROR, "Error while obtaining the document statistics: \"" + 
            e.getMessage() + "\"!", null)
          }
        }else{
          message = buildMessage(ERROR, "No value provided for parameter count!", 
                  null)
        }
      }else{
        message= buildMessage(ERROR, 
                "No value provided for parameter startIndex!", null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    //return the results
    render(contentType:"text/xml", builder: new StreamingMarkupBuilder(),
        message)
  }


  /**
   * Action for obtaining the document metadata.
   * Parameters:
   *   - documentId: the ID of the desired document
   * Returns:
   *   - the document URI
   *   - the document title
   */
  def documentMetadata = {
    def p = params["request"] ?: params
    //a closure representing the return message
    def message;
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters
      String docIdStr = p["documentId"]
      if(docIdStr){
        int docId = docIdStr as int
        // see if any fields were requested
        Map<String, Serializable> metadata = null;
        def fieldNamesStr = p["fieldNames"]
        if(fieldNamesStr) {
          Set<String> fieldNames = new HashSet<String>()
          // split on each comma (not preceded by a backslash)
          fieldNamesStr.split("\\s*(?<=[^\\\\]),\\s*").collect{
            // un-escape commas
            it.replace('\\,', ',')
          }.each{fieldNames.add(it)}
          metadata = runner.getDocumentMetadataFields(docId, fieldNames)
        }
        try{
          //we have all required parameters
          String documentURI = runner.getDocumentURI(docId)
          String documentTitle = runner.getDocumentTitle(docId)
          message = buildMessage(SUCCESS, null){
            delegate.documentTitle(documentTitle)
            delegate.documentURI(documentURI)
            metadata?.each{String key, Serializable value -> 
              delegate.metadataField(name:key, value:value.toString())
            }
          }
        }catch(Exception e){
          message = buildMessage(ERROR, "Error while obtaining the document statistics: \"" + 
          e.getMessage() + "\"!", null)
        }
      }else{
        message= buildMessage(ERROR, 
        "No value provided for parameter documentId!", null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    //return the results
    render(contentType:"text/xml", builder: new StreamingMarkupBuilder(),
    message)
  }


  /**
   * Action for obtaining [a segment of] the text of a document.
   * Parameters:
   *   - documentId: the ID of the requested document
   *   - position: (optional) the index of the first token to be returned,
   *     defaults to 0 if omitted.
   *   - length: (optional) the number of tokens (and spaces) to be returned.
   *     If omitted, all tokens from position to the end of the document will
   *     be returned.
   *
   * The effect of the default values for position and length is that if both
   * are omitted the text for the whole of the given document is returned.
   */
  def documentText = {
    def p = params["request"] ?: params
    def paramName = null;
    def message
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the other params
      String docIdStr = p["documentId"]
      String positionStr = p["position"] ?: "0"
      String lengthStr = p["length"] ?: "-1"
      if(docIdStr){
        try {
          paramName = "docId"
          int docId = Integer.parseInt(docIdStr)
          paramName = "position"
          int position = Integer.parseInt(positionStr)
          paramName = "length"
          int length = Integer.parseInt(lengthStr)
          message = buildMessage(SUCCESS, null){
            String[][] docText = runner.getDocumentText(docId, position, length)
            for(int i = 0; i < docText[0].length; i++){
              String token = docText[0][i]
              String space = docText[1][i]
              delegate.text(position: position+i, token)
              if(space) delegate.space(space)
            }
          }
        } catch(NumberFormatException e) {
          message = buildMessage(ERROR, "Non-integer value provided for parameter ${paramName}", null);
        }
      }else{
        message = buildMessage(ERROR, "No value provided for parameter documentId", null)
      }      
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    render(contentType:"text/xml", message)
  }  
  
  /**
   * Gets the number of distinct documents in the current hit list.
   */
  def docCount = {
    def p = params["request"] ?: params
    //a closure representing the return message
    def message;
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      try{
        //we have all required parameters
        int docCount = runner.getDocumentsCount()
        message = buildMessage(SUCCESS, null){
          value(docCount)
        }
      }catch(Exception e){
        message = buildMessage(ERROR, 
                "Error while obtaining the documents count: \"" + 
                e.getMessage() + "\"!", null)      
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    //return the results
    render(contentType:"text/xml", builder: new StreamingMarkupBuilder(),
        message)
  }
  
  def getMoreHits = {
    def p = params["request"] ?: params
    
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      runner.getMoreHits()
      render(buildMessage(SUCCESS, null, null), contentType:"text/xml")
    } else{
      render(buildMessage(ERROR, "Query ID ${queryId} not known!", null), 
        contentType:"text/xml")
    }
  }


  
  def hits = {
    def p = params["request"] ?: params
    //a closure representing the return message
    def message;
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters
      def startIndex = p["startIndex"]
      if(startIndex){
        def hitCount = p["count"]
        if(hitCount){
          //we have all required parameters
          List<Binding> hits = runner.getHits(startIndex as int, hitCount as int)
          message = buildMessage(SUCCESS, null){
            if(hits) {
              delegate.hits {
                for(Binding hit : hits){
                  delegate.hit (documentId: hit.getDocumentId(),
                      position: hit.getTermPosition(), 
                      length: hit.getLength())
                }
              }
            }
          }
        }else{
          message = buildMessage(ERROR,  
                    "No value provided for parameter count!",
                    null)  
        }
      }else{
        message = buildMessage(ERROR,  
                "No value provided for parameter startIndex!", null)   
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    //return the results
    render(contentType:"text/xml", 
        builder: new StreamingMarkupBuilder(),
        message)
  }
  
  def close = {
    def p = params["request"] ?: params
    
    //get the query ID
    String queryId = p["queryId"]
    if(searchService.closeQueryRunner(queryId)){
      getSessionQueryIDs().remove(queryId)
      render(buildMessage(SUCCESS, null, null), contentType:"text/xml")
    }else{
      render(buildMessage(ERROR, "Query ID ${queryId} not known!", null), 
        contentType:"text/xml")
    }
  }
  
  
  /**
   * A method to build a closure representing a Mimir message.
   * @param theState the state value (either {@link #SUCCESS} or {@link #ERROR})
   * @param theError the text describing the error (if any)
   * @param dataClosure a closure representing the contents for the data
   * element.
   */
  private buildMessage(String theState, String theError, dataClosure) {
    return  {
      mkp.xmlDeclaration()
      mkp.declareNamespace('':MIMIR_NAMESPACE)
      
      delegate.message {
        delegate.state(theState)
        if(theError) {
          delegate.error(theError)
        }
        if(dataClosure){
          delegate.data {
            dataClosure.delegate = delegate
            //            dataClosure.resolveStrategy = Closure.DELEGATE_FIRST
            dataClosure.call()
          }
        }
      }
    }
  }
  
  /**
   * Gets the query IDs for the current session
   */
  private Set<String> getSessionQueryIDs(){
    Set<String> queryIDs = session["queryIDs"]
    if(queryIDs == null){
      queryIDs = new SessionSet(searchService);
      session["queryIDs"] = queryIDs
    }
    return queryIDs
  }
  
  /////////////////////////// Binary protocol implementation /////////////////
  
  /**
   * Binary version of post query 
   */
  def postQueryBin = {
    def p = params["request"] ?: params
    
    //get the query string
    String queryString = p["queryString"]
    try{
      String runnerId = searchService.postQuery(request.theIndex, queryString)
      //save the query ID in the session, so we can close it on expiry
      getSessionQueryIDs().add(runnerId)
      new ObjectOutputStream (response.outputStream).withStream {stream -> 
        stream.writeObject(runnerId)
      }
    }catch(Exception e){
      log.error("Exception posting query", e)
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
      "Problem posting query: \"" + e.getMessage() + "\"")
    }
  }
  
  
  /**
   * Binary version of hits call
   */
  def hitsBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters
      def startIndexParam = p["startIndex"]
      if(startIndexParam){
        def hitCountParam = p["count"]
        if(hitCountParam){
          try{
            int startIndex = startIndexParam.toInteger()
            int hitCount = hitCountParam.toInteger()
            //we have all required parameters
            List<Binding> hits = new ArrayList<Binding>(runner.getHits(startIndex, hitCount))
            new ObjectOutputStream (response.outputStream).withStream {stream -> 
              stream.writeObject(hits)
            }
          }catch(Exception e){
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
            "Error while obtaining the hits: \"" + e.getMessage() + "\"!")
          }
        }else{
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
              "No value provided for parameter maxHits!")
        }
      }else{
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
            "No value provided for parameter firstHit!")
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Gets the number of available hits as a binary representation of an int 
   * value.
   */
  def hitCountBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      try{
        //we have all required parameters
        int hitCount = runner.getHitsCount()
        new ObjectOutputStream (response.outputStream).withStream {stream -> 
          stream.writeInt(hitCount)
        }
      }catch(Exception e){
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
            "Error while obtaining the hits count: \"" + e.getMessage() + "\"!")
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Gets the number of distinct documents in the current hit list as a binary
   * representation of an int value.
   */
  def docCountBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      try{
        //we have all required parameters
        int docCount = runner.getDocumentsCount()
        new ObjectOutputStream (response.outputStream).withStream {stream -> 
          stream.writeInt(docCount)
        }
      }catch(Exception e){
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
            "Error while obtaining the documents count: \"" + e.getMessage() + "\"!")
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Gets the document statistics for a set of documents. The returned value is
   * the binary representation of a two-rows int array where:
   * <ul>
   *   <li>result[0][i] is the ID for the i<sup>th</sup> document</li>
   *   <li>result[1][i] is the hits count for the i<sup>th</sup> document</li>
   * </ul>
   */
  def docStatsBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters
      def startIndexParam = p["startIndex"]
      if(startIndexParam){
        def docCountParam = p["count"]
        if(docCountParam){
          try{
            //we have all required parameters
            int startIndex = startIndexParam.toInteger()
            int docCount = docCountParam.toInteger()
            int[][] docStats = new int[docCount][2];
            for(int i = 0; i < docCount; i++){
              docStats[i][0] = runner.getDocumentID(i + startIndex)
              docStats[i][1] = runner.getDocumentHitsCount(i + startIndex)
            }
            new ObjectOutputStream (response.outputStream).withStream {stream -> 
              stream.writeObject(docStats)
            }
          }catch(Exception e){
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Error while obtaining the document statistics: \"" + 
                e.getMessage() + "\"!")
          }
        }else{
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
          "No value provided for parameter maxHits!")
        }
      }else{
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
        "No value provided for parameter firstHit!")
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
      "Query ID ${queryId} not known!")
    }
  }
  
  
  /**
   * Sets the maximum number of hits desired in one search stage.
   */
  def setStageMaxHitsBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      String maxHits = p["maxHits"]
      if(maxHits){
        //we have all required parameters
        try{
          runner.setStageMaxHits(maxHits as int)
          render(text:"OK", contentType:"text/plain", encoding:"UTF-8")
        }catch(Exception e){
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
              "Error while configuring the query runner: \"" + 
              e.getMessage() + "\"!")
        }        
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  
  /**
   * Sets the maximum number of hits desired in one search stage.
   */
  def setStageTimeoutBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      String timeout = p["timeout"]
      if(timeout){
        //we have all required parameters
        try{
          runner.setStageTimeout(timeout as int)
          render(text:"OK", contentType:"text/plain", encoding:"UTF-8")
        }catch(Exception e){
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
              "Error while configuring the query runner: \"" + 
              e.getMessage() + "\"!")
        }        
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Gets the active flag, as a serialise boolean value.
   */
  def isActiveBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      try{
        //we have all required parameters
        boolean active = runner.isActive()
        new ObjectOutputStream (response.outputStream).withStream {stream -> 
          stream.writeBoolean(active)
        }
      }catch(Exception e){
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
            "Error while obtaining the query state: \"" + 
            e.getMessage() + "\"!")
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Gets the complete flag, as a serialise boolean value.
   */
  def isCompleteBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      try{
        //we have all required parameters
        boolean complete = runner.isComplete()
        new ObjectOutputStream (response.outputStream).withStream {stream -> 
          stream.writeBoolean(complete)
        }
      }catch(Exception e){
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
            "Error while obtaining the query state: \"" + 
            e.getMessage() + "\"!")
      }        
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  
  /**
   * Calls the render document method on the corresponding query runner, piping
   * the output directly to the response stream.
   *  
   */
  def renderDocument = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters
      def documentIdParam = p["documentId"]
      if(documentIdParam){
        try{
          //we have all the required parameters
          int documentId = documentIdParam.toInteger()
          response.characterEncoding = "UTF-8"
          response.writer.withWriter{ writer ->
            runner.renderDocument(documentId, writer)
          }
        }catch(Exception e){
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
              "Error while rendering document: \"" + 
              e.getMessage() + "\"!")
        }
      }else{
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
        "No value provided for parameter documentId!")
      }
    }else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  
  /**
   * Gets [a segment of] the document text. The returned value is a 
   * Java-serialised array of String arrays. Each element in the main array 
   * corresponds to a token, and consists of an array of two strings: the token 
   * string and the token non-string (i.e. all the whitespace that follows the 
   * token).  
   */
  def docTextBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters: int documentID, int termPosition, int length
      def documentIdParam = p["documentId"]
      if(documentIdParam){
        def termPositionParam = p["termPosition"]
        if(termPositionParam){
          def lengthParam = p["length"]
          if(lengthParam){
            try{
              //we have all required parameters
              int documentId = documentIdParam.toInteger()
              int termPosition = termPositionParam.toInteger()
              int length = lengthParam.toInteger()
              String[][] docText = runner.getDocumentText(documentId, termPosition, length)
              new ObjectOutputStream (response.outputStream).withStream {stream -> 
                stream.writeObject(docText)
              }
            }catch(Exception e){
              response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                  "Error while obtaining the document text: \"" + 
                  e.getMessage() + "\"!")
            }              
          } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
            "No value provided for parameter length!")
          }
        } else {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
          "No value provided for parameter termPosition!")
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
        "No value provided for parameter documentID!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  
  /**
   * Gets the URI of a document as a serialises String value.
   */
  def docURIBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters: int documentID
      def documentIdParam = p["documentId"]
      if(documentIdParam){
        try{
          //we have all required parameters
          int documentId = documentIdParam.toInteger()
          String docUri = runner.getDocumentURI(documentId)
          new ObjectOutputStream (response.outputStream).withStream {stream -> 
            stream.writeObject(docUri)
          }
        }catch(Exception e){
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
              "Error while obtaining the document URI: \"" + 
              e.getMessage() + "\"!")
        }          
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
            "No value provided for parameter documentId!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Gets the title of a document as a serialised String value.
   */
  def docTitleBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters: int documentID
      def documentIdParam = p["documentId"]
      if(documentIdParam){
        try{
          //we have all required parameters
          int documentId = documentIdParam.toInteger()
          String docTitle = runner.getDocumentTitle(documentId)
          new ObjectOutputStream (response.outputStream).withStream {stream -> 
            stream.writeObject(docTitle)
          }
        }catch(Exception e){
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
              "Error while obtaining the document title: \"" + 
              e.getMessage() + "\"!")
        }          
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
            "No value provided for parameter documentId!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, 
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Gets a set of arbitrary document metadata fields
   */
  def docMetadataFieldsBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters: int documentID
      def documentIdParam = p["documentId"]
      if(documentIdParam){
        def fieldNamesStr = p["fieldNames"]
        if(fieldNamesStr) {
          try{
            //we have all required parameters
            int documentId = documentIdParam.toInteger()
            Set<String> fieldNames = new HashSet<String>()
            // split on each comma (not preceded by a backslash)
            fieldNamesStr.split("\\s*(?<=[^\\\\]),\\s*").collect{
              // un-escape commas
              it.replace('\\,', ',')
            }.each{fieldNames.add(it)}
            Map<String, Serializable> medatada = 
                runner.getDocumentMetadataFields(documentId, fieldNames)
            new ObjectOutputStream (response.outputStream).withStream {stream ->
              stream.writeObject(medatada)
            }
          }catch(Exception e){
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error while obtaining the document title: \"" +
                e.getMessage() + "\"!")
          }
        } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "No value provided for parameter fieldNames!")
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "No value provided for parameter documentId!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Gets the annotations config for a given index, as a serialised String[][] 
   * value.
   */
  def annotationsConfigBin = {
    def p = params["request"] ?: params
    //get the query ID
    String indexId = p["indexId"]
    if(indexId){
      Index theIndex = Index.findByIndexId(params.indexId)
      if(theIndex){
        try{
          String [][] indexConfig = theIndex.annotationsConfig()
          new ObjectOutputStream (response.outputStream).withStream {stream -> 
            stream.writeObject(indexConfig)
          }
        }catch(Exception e){
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
              "Error while obtaining the annotations configuration for " +
              "index \"" + indexId +  "\": \"" + e.getMessage() + "\"!")
        }          
      }else{
        //could not find the index
        response.sendError(HttpServletResponse.SC_NOT_FOUND, 
        "Index ID ${indexId} not known!")
      }
    }else{
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
      "No value provided for parameter indexId!")
    }
  }
  
  ////////////////////////////////////////////////////////////////////////////
  
}



/**
 * An extension of HashSet, that listens to session binding events and releases
 * all queries upon unbinding. 
 */
private class SessionSet extends HashSet<String> implements Set<String>, HttpSessionBindingListener{
  SearchService searchService
  
  public SessionSet(SearchService searchService){
    this.searchService = searchService
  }
  
  public void valueBound(HttpSessionBindingEvent event){
    
  }
  
  public void valueUnbound(HttpSessionBindingEvent event){
    for(String id : this){
      searchService.closeQueryRunner(id)
    }
  }
}
