/*
 *  SearchController.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
package gate.mimir.web


import java.io.OutputStreamWriter;

import gate.mimir.web.Index;
import gate.mimir.web.SearchService;
import groovy.xml.StreamingMarkupBuilder;
import gate.mimir.index.mg4j.zipcollection.DocumentData;
import gate.mimir.search.query.Binding;

import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;

import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryRunner;


/**
 * A controller for searching mimir indexes known to this instance.
 * It supports HTML search, XML-over-HTTP, and AJAX-backing RPC actions.
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

 
// ==================== HTML-GWT Interface ===================================
  
  /**
   * Action that supports the GWT-based web UI
   */
  def index = {
    // GWT takes care of the rest
    return [index:request.theIndex]
  }
  
  /**
  * Render the content of the given document.  Most of the magic happens in
  * the documentContent tag of the GusTagLib.
  */
 def document = {
   QueryRunner runner = searchService.getQueryRunner(params.queryId)
   if(runner){
     Index index = Index.findByIndexId(params.indexId)
     return [
         indexId:params.indexId,
         documentRank: params.documentRank,
         queryId:params.queryId,
         documentTitle:runner.getDocumentTitle(params.documentRank as int),
         baseHref:index?.isUriIsExternalLink() ? runner.getDocumentURI(params.documentRank as int) : null
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
 def gwtRpc = {
   forward(controller:'gwt', action:'index', 
     params:[module:'gate.mimir.web.client.UI'])
 }
  
  
  
// ==================== XML-over-HTTP Interface ===============================
  
  def info = {
    return [indexInstance:request.theIndex]
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
      render(buildMessage(SUCCESS, null){
        queryId(runnerId)
      }, contentType:"text/xml")
    }catch(Exception e){
      log.error("Exception posting query", e)
      render(buildMessage(ERROR, e.message, null), contentType:"text/xml")
    }
  }
  
  /**
   * Gets the number of result documents.
   * @return <code>-1</code> if the search has not yet completed, the total 
   * number of result document otherwise. 
   */
  def documentsCount = {
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
  
  /**
   * Gets the number of result documents found so far. After the search 
   * completes, the result returned by this call is identical to that of 
   * {@link #documentsCount}.
   * @return the number of result documents known so far.   */
  def documentsCurrentCount = {
    def p = params["request"] ?: params
    //a closure representing the return message
    def message;
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      try{
        //we have all required parameters
        int docCount = runner.getDocumentsCurrentCount()
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
  
  /**
   * Gets the ID of a result document.
   * @param rank the index of the desired document in the list of documents. 
   * This should be a value between 0 and {@link #documentsCount} -1.
   *  
   * If the requested document position has not yet been ranked (i.e. we know 
   * there is a document at that position, but we don't yet know which one) then 
   * the necessary ranking is performed before this method returns. 
   *
   * @return an int value, representing the ID of the requested document.   */
  def documentId = {
    def p = params["request"] ?: params
    def message
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the other params
      String rankStr = p["rank"]
      if(rankStr){
        try {
          int rank = Integer.parseInt(rankStr)
          int docId = runner.getDocumentID(rank)
          message = buildMessage(SUCCESS, null){
            value(docId)
          }
        } catch(NumberFormatException e) {
          message = buildMessage(ERROR, 
            "Non-integer value provided for parameter rank", null);
        }
      }else{
        message = buildMessage(ERROR, "No value provided for parameter rank", 
            null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    render(contentType:"text/xml", message)
  }
  
  /**
   * Get the score for a given result document. The value for the score depends 
   * on the scorer used by the {@link QueryEngine} (see 
   * {@link QueryEngine#setScorerSource(java.util.concurrent.Callable)}). 
   * @param rank the index of the desired document in the list of documents. 
   * This should be a value between 0 and {@link #documentsCount} -1.   
   */
  def documentScore = {
    def p = params["request"] ?: params
    def message
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the other params
      String rankStr = p["rank"]
      if(rankStr){
        try {
          int rank = Integer.parseInt(rankStr)
          double score = runner.getDocumentScore(rank)
          message = buildMessage(SUCCESS, null){
            value(score)
          }
        } catch(NumberFormatException e) {
          message = buildMessage(ERROR,
            "Non-integer value provided for parameter rank", null);
        }
      } else {
        message = buildMessage(ERROR,  "No value provided for parameter rank", 
          null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    render(contentType:"text/xml", message)
  }
  
  /**
   * Retrieves the hits within a given result document.
   * @param rank the index of the desired document in the list of documents.
   * This should be a value between 0 and {@link #documentsCount} -1.
   * 
   * This method call waits until the requested data is available before 
   * returning (document hits are being collected by a background thread).   
   */
  def documentHits = {
    def p = params["request"] ?: params
    //a closure representing the return message
    def message;
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the other params
      String rankStr = p["rank"]
      if(rankStr){
        try {
          int rank = Integer.parseInt(rankStr)
          //we have all required parameters
          List<Binding> hits = runner.getDocumentHits(rank)
          message = buildMessage(SUCCESS, null){
            if(hits) {
              delegate.hits {
                for(Binding hit : hits){
                  delegate.hit (documentId: hit.getDocumentId(),
                      termPosition: hit.getTermPosition(),
                      length: hit.getLength())
                }
              }
            }
          }
        } catch(NumberFormatException e) {
          message = buildMessage(ERROR,
            "Non-integer value provided for parameter rank", null);
        }
      } else {
        message = buildMessage(ERROR,  "No value provided for parameter rank",
          null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    //return the results
    render(contentType:"text/xml",
        builder: new StreamingMarkupBuilder(),
        message)
  }
  
  
  /**
   * Gets a segment of the document text for a given document.
   * @param rank the rank of the requested document. This should be a value 
   * between 0 and {@link #getDocumentsCount()} -1.
   * @param termPosition the first term requested.
   * @param length the number of terms requested.
   * @return two parallel String arrays, one containing term text, the other 
   * containing the spaces in between. The first term is results[0][0], the 
   * space following it is results[1][0], etc.
   *
   * The effect of the default values for termPosition and length is that if
   * both are omitted the text for the whole of the given document is returned.
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
      String rankStr = p["rank"]
      String positionStr = p["termPosition"] ?: "0"
      String lengthStr = p["length"] ?: "-1"
      if(rankStr){
        try {
          paramName = "rank"
          int rank = Integer.parseInt(rankStr)
          paramName = "termPosition"
          int position = Integer.parseInt(positionStr)
          paramName = "length"
          int length = Integer.parseInt(lengthStr)
          message = buildMessage(SUCCESS, null){
            String[][] docText = runner.getDocumentText(rank, position, length)
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
        message = buildMessage(ERROR, "No value provided for parameter rank", 
            null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    render(contentType:"text/xml", message)
  }
  
  /**
   * Action for obtaining the document metadata.
   * Parameters:
   *   - rank: the rank of the desired document
   *   - fieldNames (optional): a comma-separated list of other field names to 
   *   be returned. 
   * Returns:
   *   - the document URI
   *   - the document title
   *   - the values for the other field names, if requested and present.
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
      String rankStr = p["rank"]
      if(rankStr){
        int rank = rankStr as int
        // see if any fields were requested
        Map<String, Serializable> metadata = null;
        def fieldNamesStr = p["fieldNames"]
        if(fieldNamesStr) {
          Set<String> fieldNames = new HashSet<String>()
          // split on each comma (not preceded by a backslash)
          fieldNamesStr.split(/\s*(?<!\\),\s*/).collect{
            // un-escape commas
            it.replace('\\,', ',')
          }.each{fieldNames.add(it)}
          metadata = runner.getDocumentMetadataFields(rank, fieldNames)
        }
        try{
          //we have all required parameters
          String documentURI = runner.getDocumentURI(rank)
          String documentTitle = runner.getDocumentTitle(rank)
          message = buildMessage(SUCCESS, null){
            delegate.documentTitle(documentTitle)
            delegate.documentURI(documentURI)
            metadata?.each{String key, Serializable value -> 
              delegate.metadataField(name:key, value:value.toString())
            }
          }
        }catch(Exception e){
          message = buildMessage(ERROR, 
            'Error while obtaining the document metadata: "' + 
            e.getMessage() + "\"!", null)
        }
      }else{
        message= buildMessage(ERROR, 
        "No value provided for parameter rank!", null)
      }
    } else{
      message = buildMessage(ERROR, "Query ID ${queryId} not known!", null)
    }
    //return the results
    render(contentType:"text/xml", builder: new StreamingMarkupBuilder(),
    message)
  }

    
  def close = {
    def p = params["request"] ?: params
    
    //get the query ID
    String queryId = p["queryId"]
    if(searchService.closeQueryRunner(queryId)){
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
  
// ============== Binary protocol (used by remote clients) ===================
  
  /**
   * Binary version of post query 
   */
  def postQueryBin = {
    def p = params["request"] ?: params
    //get the query string
    String queryString = p["queryString"]
    try {
      String runnerId = searchService.postQuery(request.theIndex, queryString)
      new ObjectOutputStream (response.outputStream).withStream {stream -> 
        stream.writeObject(runnerId)
      }
    } catch(Exception e) {
      log.error("Exception posting query", e)
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
      "Problem posting query: \"" + e.getMessage() + "\"")
    }
  }

  /**
   * Gets the number of result documents found so far. After the search 
   * completes, the result returned by this call is identical to that of 
   * {@link #documentsCountBin}. The result is returned as a binary 
   * representation of an int value.
   */
  def documentsCurrentCountBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner) {
      try {
        // we have all required parameters
        int docCount = runner.getDocumentsCurrentCount()
        new ObjectOutputStream (response.outputStream).withStream {stream ->
          stream.writeInt(docCount)
        }
      } catch(Exception e) {
        log.warn("Error while sending document current count", e)
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Error while obtaining the documents count: \"" + e.getMessage() + "\"!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
      }
  }
 
  /**
   * Gets the number of result documents found.  Returns <code>-1</code> if the
   * search has not yet completed, the total number of result document 
   * otherwise. The result is returned as a binary representation of an int 
   * value.
   */
  def documentsCountBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner) {
      try {
        // we have all required parameters
        int docCount = runner.getDocumentsCount()
        new ObjectOutputStream (response.outputStream).withStream {stream ->
          stream.writeInt(docCount)
        }
      } catch(Exception e) {
        log.warn("Error while sending document count", e)
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Error while obtaining the documents count: \"" + e.getMessage() + "\"!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
    }
  }
  
  // protected static final String ACTION_DOC_IDS_BIN = "documentIdsBin";
  /**
   * Gets the IDs of a range of documents, in ranking order.
   */
  def documentIdsBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      if(runner.getDocumentsCount() < 0) {
        // premature call
        response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} has not completed collecting hits; please try later")
      }
      //get the parameters: int documentRank
      def firstRankParam = p["firstRank"]
      if (firstRankParam) {
        def sizeParam = p["size"]
        if(sizeParam) {
          //we have all required parameters
          try {
            int from = firstRankParam.toInteger()
            int resultSize = sizeParam.toInteger()
            int to = from + resultSize
            if(to > runner.getDocumentsCount()) {
              to = runner.getDocumentsCount()
            }
            int[] docIds = new int[(to - from)]
            for(int rank = from; rank < to; rank++) {
              docIds[rank] = runner.getDocumentID(rank)
            }
            new ObjectOutputStream (response.outputStream).withStream {stream ->
              stream.writeObject(docIds)
            }
          } catch(Exception e){
            log.warn("Error while sending document ID", e)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error while obtaining the document ID: \"" +
                e.getMessage() + "\"!")
          }
        } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "No value provided for parameter size!")
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "No value provided for parameter firstRank!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
    }
  }

  // protected static final String ACTION_DOC_SCORES_BIN = "documentsScoresBin";  
  /**
   * Retrieves the scores for a range of documents
   */
  def documentsScoresBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters: int documentRank
      def firstRankParam = p["firstRank"]
      if (firstRankParam) {
        def sizeParam = p["size"]
        if(sizeParam) {
          //we have all required parameters
          try {
            int from = firstRankParam.toInteger()
            int resultSize = sizeParam.toInteger()
            int to = from + resultSize
            if(to > runner.getDocumentsCount()) {
              to = runner.getDocumentsCount()
            }
            double[] docScores = new double[(to - from)]
            for(int rank = from; rank < to; rank++) {
              docScores[rank] = runner.getDocumentScore(rank)
            }
            new ObjectOutputStream (response.outputStream).withStream {stream ->
              stream.writeObject(docScores)
            }
          } catch(Exception e){
            log.warn("Error while sending document ID", e)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error while obtaining the document ID: \"" +
                e.getMessage() + "\"!")
          }
        } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "No value provided for parameter size!")
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "No value provided for parameter firstRank!")
      }
    } else {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
    }
  }
  
  /**
   * Retrieves the hits within a given result document.
   */
  def documentHitsBin = {
    def p = params["request"] ?: params
    //get the query ID
    String queryId = p["queryId"]
    QueryRunner runner = searchService.getQueryRunner(queryId);
    if(runner){
      //get the parameters
      def documentRankParam = p["documentRank"]
      if(documentRankParam){
        try{
          int documentRank = documentRankParam.toInteger()
          //we have all required parameters
          List<Binding> hits = runner.getDocumentHits(documentRank)
          new ObjectOutputStream (response.outputStream).withStream {stream ->
            stream.writeObject(hits)
          }
        }catch(Exception e){
          log.warn("Error while sending document hits", e)
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Error while obtaining the hits: \"" + e.getMessage() + "\"!")
        }
        
      }else{
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "No value provided for parameter documentRank!")
      }
    } else{
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          "Query ID ${queryId} not known!")
    }
  }
  
  //  protected static final String ACTION_DOC_DATA_BIN = "documentDataBin";
  /**
   * Gets the document data (title, URI, text) for a given document. The 
   * requested document should be specified by providing paramter values for
   * either documentId or both queryId and documentRank. The result is a 
   * serialised {@link DocumentData} value.
   */
  def documentDataBin = {
    def p = params["request"] ?: params
    Index index = request.theIndex
    // get the document ID
    int documentId = -1;
    String documentIdParam = p["documentId"]
    if(documentIdParam) {
      documentId = documentIdParam.toInteger()
    } else {
      // we didn't get the explicit ID; try queryId and rank instead
      String queryId = p["queryId"]
      if(queryId) {
        QueryRunner runner = searchService.getQueryRunner(queryId);
        if(runner){
          String documentRankParam = p["documentRank"]
          if (documentRankParam) {
            int documentRank = documentRankParam.toInteger()
            documentId = runner.getDocumentID(documentRank)
          } else {
            log.warn("Error while sending document data: " +
              "Neither documentId nor documentRank parameters were provided!")
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
              "Neither documentId nor documentRank parameters were provided!")
          }
        } else {
          log.warn("Error while sending document data: " +
              "Query ID ${queryId} not known!")
          response.sendError(HttpServletResponse.SC_NOT_FOUND,
            "Query ID ${queryId} not known!")
        }
      } else {
        log.warn("Error while sending document data: " +
            "Neither documentId nor queryId parameters were provided!")
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "Neither documentId nor queryId parameters were provided!")
      }
    }
    // by this point we need to have the documentID
    if(documentId >= 0) {
      try {
        new ObjectOutputStream (response.outputStream).withStream {stream ->
          stream.writeObject(index.getDocumentData(documentId))
        }
      } catch(Exception e) {
        log.warn("Error while sending document data", e)
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Error while obtaining the document ID: \"" +
            e.getMessage() + "\"!")
      }
    } else {
      log.warn("Error while sending document data: " +
          "Could not find a valid documentId with the provided parameters!")
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Could not find a valid documentId with the provided parameters!")
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
      def documentRankParam = p["rank"]
      if(documentRankParam){
        try{
          //we have all the required parameters
          int documentRank = documentRankParam.toInteger()
          response.characterEncoding = "UTF-8"
          response.writer.withWriter{ writer ->
            runner.renderDocument(documentRank, writer)
          }
        }catch(Exception e){
          response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
              "Error while rendering document: \"" + 
              e.getMessage() + "\"!")
        }
      }else{
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, 
        "No value provided for parameter rank!")
      }
    }else{
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
}