/*
 *  GusService.groovy
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
package gate.mimir.gus
import gate.mimir.web.Index;

import java.util.ArrayList;
import java.util.List;

import gate.mimir.search.*
import gate.mimir.search.query.*
import gate.mimir.SemanticAnnotationHelper;

import java.util.concurrent.ConcurrentHashMap
import gate.mimir.gus.client.SearchException
import gate.mimir.gus.client.QueryResult
import gate.mimir.gus.client.TotalResults
import gate.mimir.gus.client.TotalResults

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.DisposableBean
import java.text.NumberFormat

import grails.converters.JSON


/**
 * A service that exposes the Mimir search functionality for the GWT-based
 * GUS client. 
 */
class GusService implements InitializingBean, DisposableBean {

  static transactional = false
  
  /**
   * The SearchService that does the actual work (autowired by Grails)
   */
  def searchService
  
  // scope the service to the web session, so one instance per user
  static scope = "session"
  
  // expose for GWT RPC
  static expose = ["gwt:gate.mimir.gus.client"]

  
  /**
   * Set of in-progress queries for this session. The values are qeury IDs
   * obtained from the search service
   */
  def Set<String> runningQueries = new HashSet<String>()
 
  public void afterPropertiesSet() {
  }

  /**
   * Close the executors and free any running queries
   */
  public void destroy() {
    runningQueries.each(this.&releaseQuery)
  }

  
  /**
   * Initialize a query. To get the results the {@link #getHits(int, int, int)}
   * method must be called after this one.
   */
  String search(String indexId, String query) throws SearchException {
    Index.withTransaction {
      try {
        def index = Index.findByIndexId(indexId)
        if(!index) {
          throw new SearchException("Invalid index ID ${indexId}")
        }
        else if(index.state != Index.SEARCHING) {
          throw new SearchException("Index ${indexId} is not open for searching")
        }
        else {
          String queryId = searchService.postQuery(index, query)
          runningQueries.add(queryId)
          return [id:queryId] as JSON
        }
      } catch(SearchException e) {
        log.warn("Exception starting search", e)
        throw e
      } catch(Exception e) {
        log.warn("Exception starting search", e)
        throw new SearchException("Could not start search. Error was:\n${e.message}");
      }
    }
  }

  /**
   * Release the resources associated with a given query.  Does nothing
   * if the given ID does not correspond to a running query.
   */
  void releaseQuery(String id) {
    //check if it's one of our queries
    if(runningQueries.remove(id)){
      searchService.closeQueryRunner(id)
    }
  }

  /**
   * Get the currently available results.
   * To be called after {@link #search(String, int)}.
   * @return a List of {@link QueryResult} objects, one for each hit, or
   * <code>null</code> if there are no more results available.  An empty
   * list means that there are no results <i>currently</i> available, but
   * the query is still running so there may be some more later on.
   */
  List getHits(String id, int contextSize, int numberOfHits, int startingIndex) {
    try {
      if(runningQueries.contains(id)){
        QueryRunner qRunner = searchService.getQueryRunner(id) 
        List<Binding> hits = qRunner.getHits( 
                startingIndex, numberOfHits)
        if(hits) {
          return hits.collect(hitToQueryResult.curry(qRunner, contextSize))
        } else {
          if(qRunner.isComplete()) {
            // no hits and query is complete - definitely won't be anything to return
            return null
          } else {
            // no hits but runner not yet complete - try again later
            return []
          }
        }
      }else{
        throw new SearchException("Unknown query id ${id}, try starting a new search")
      }
    } catch(IOException e) {
      throw new SearchException("I/O error collecting hits")
    }
  }

  /**
  * Obtains the types of annotation known to the index, and their features. 
   * This method supports auto-completion in the GWT UI.
  */
  String[][] getAnnotationsConfig(String indexId){
    Index.withTransaction {
      Index index = Index.findByIndexId(indexId)
      if(index) {
        return index.annotationsConfig()
      }
      else {
        return ([] as String[][])
      }
    }
  }
  
  TotalResults getTotalResults(String queryID) {
    if(runningQueries.contains(queryID)){
      QueryRunner qr = searchService.getQueryRunner(queryID)
      TotalResults tr = new TotalResults()
      tr.totalResults = qr.hitsCount
      tr.searchFinished = qr.isComplete()
      tr.searchRunning = qr.isActive()
      return tr
    } else {
      throw new SearchException("Unknown query id ${queryID}, try starting a new search")
    }
  }
  
  void runQuery(String queryID) {
    if(runningQueries.contains(queryID)){
      QueryRunner qr = searchService.getQueryRunner(queryID)
      qr.getMoreHits()
    } else {
      throw new SearchException("Unknown query id ${queryID}, try starting a new search")
    }
  }

  void renderDocument(String queryID, int documentID, Appendable out) {
    if(runningQueries.contains(queryID)) {
      searchService.getQueryRunner(queryID).renderDocument(documentID, out)
    } else {
      throw new SearchException("Unknown query id ${queryID}, try starting a new search")
    }
  }

  
  /**
   * Closure that turns a Binding into an RPC-serializable {@link QueryResult}.
   */
  private hitToQueryResult = { qRunner, contextSize, hit ->
    QueryResult qr = new QueryResult()
    qr.documentID =  hit.documentId
    qr.documentURI = qRunner.getDocumentURI(hit.documentId)
    qr.documentTitle = qRunner.getDocumentTitle(hit.documentId)
    qr.termPosition = hit.termPosition
    qr.length = hit.length
    //start getting the various text elements
    int tokenCount = contextSize
    int startOffset = qr.termPosition - contextSize
    if(startOffset < 0){
      tokenCount += startOffset // startOffset is negative, so this will
                               //subtract from numTokens
      startOffset = 0;
    }
    String[][] leftContext = qRunner.getDocumentText(qr.documentID, 
            startOffset, tokenCount)
            
    String[][] spanText = qRunner.getDocumentText(qr.documentID, 
            qr.termPosition, qr.length)
            
    String[][] rightContext = qRunner.getDocumentText(qr.documentID, 
        qr.termPosition + qr.length, contextSize) 
    
    // left context is just the concatenation of the tokens and non-tokens
    // in the leftContext array
    StringBuilder leftContextStr = new StringBuilder()
    for(int i = 0; i < leftContext[0].length; i++) {
      leftContextStr.append(leftContext[0][i])
      leftContextStr.append(leftContext[1][i])
    }
    
    qr.leftContext = leftContextStr.toString()
    
    // span text is the concatenation of the tokens and non-tokens in the
    // spanText array, except that the last non-token is considered part
    // of the right context, not of the hit.
    StringBuilder spanTextStr = new StringBuilder()
    def lastHitToken = spanText[0].length - 1
    for(int i = 0; i <= lastHitToken; i++) {
      spanTextStr.append(spanText[0][i])
      if(i < lastHitToken) {
        spanTextStr.append(spanText[1][i])
      }
    }
    
    qr.spanText = spanTextStr.toString()
    
    // right context is the last non-token of the span text plus the
    // concatenation of the tokens and non-tokens in the rightContext
    // array
    StringBuilder rightContextStr = new StringBuilder()
    if(lastHitToken >= 0) {
      rightContextStr.append(spanText[1][lastHitToken])
    }
    for(int i = 0; i < rightContext[0].length; i++) {
      // when we get a null token value, it means we've run out of document 
      // content.
      if(rightContext[0][i] != null) {
        rightContextStr.append(rightContext[0][i])
        rightContextStr.append(rightContext[1][i])
      }
    }
    
    qr.rightContext = rightContextStr.toString()
    
    return qr
  }
}
