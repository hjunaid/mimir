/**
*  GwtRpcService.java
*
*  Copyright (c) 1995-2010, The University of Sheffield. See the file
*  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
*
*  This file is part of GATE (see http://gate.ac.uk/), and is free
*  software, licenced under the GNU Library General Public License,
*  Version 2, June 1991 (in the distribution as file licence.html,
*  and also available at http://gate.ac.uk/gate/licence.html).
*
*  Valentin Tablan, 01 Dec 2011
*/
package gate.mimir.web.server

import gate.mimir.gus.client.SearchException;
import grails.converters.JSON;

import java.util.Set;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;


import gate.mimir.search.QueryRunner;
import gate.mimir.search.query.Binding;
import gate.mimir.web.Index;
import gate.mimir.web.client.DocumentData;
import gate.mimir.web.client.GwtRpcException
import gate.mimir.web.client.ResultsData;
import gate.mimir.web.client.DocumentData;

class GwtRpcService implements InitializingBean, DisposableBean, gate.mimir.web.client.GwtRpcService {

  static transactional = false

  /**
   * The SearchService that does the actual work (autowired by Grails)
   */
  def searchService

  // scope the service to the web session, so one instance per user
  static scope = "session"

  // expose for GWT RPC
  static expose = ["gwt:gate.mimir.web.client"]

  /**
   * Set of in-progress queries for this session. The values are query IDs
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
   * Post a query
   */
  String search(String indexId, String query) {
    Index.withTransaction {
      try {
        def index = Index.findByIndexId(indexId)
        if(!index) {
          throw new GwtRpcException("Invalid index ID ${indexId}")
        }
        else if(index.state != Index.SEARCHING) {
          throw new GwtRpcException("Index ${indexId} is not open for searching")
        }
        else {
          String queryId = searchService.postQuery(index, query)
          runningQueries.add(queryId)
          return queryId
        }
      } catch(GwtRpcException e) {
        log.warn("Exception starting search", e)
        throw e
      } catch(Exception e) {
        log.warn("Exception starting search", e)
        throw new GwtRpcException("Could not start search. Error was:\n${e.message}");
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

  @Override
  public ResultsData getResultsData(String queryId, 
        int firstDocumentRank, int documentsCount) throws GwtRpcException {
    QueryRunner qRunner = searchService.getQueryRunner(queryId);
    if(qRunner) {
      ResultsData rData = new ResultsData(
        resultsTotal:qRunner.getDocumentsCount(),
        resultsPartial: qRunner.getCurrentDocumentsCount())
      if(firstDocumentRank >= 0) {
        // also obtain some documents data
        List<DocumentData> documents = []
        for(int docRank = firstDocumentRank; 
            docRank < firstDocumentRank + documentsCount; 
            docRank++) {
          DocumentData docData = new DocumentData(
            documentRank:docRank,
            documentTitle:qRunner.getDocumentTitle(docRank),
            documentUri:qRunner.getDocumentURI(docRank))
          // create the snippets
          List<String[]> snippets = new ArrayList<String[]>();
          List<Binding> hits = qRunner.getDocumentHits(docRank).collect{it};
          StringBuilder str = new StringBuilder()
          3.times {
            if(hits) {
              String[] snippet = new String[3];
              Binding aHit = hits.remove(0)
              int termPos = Math.max(0, aHit.termPosition - 3)
              if(termPos < aHit.termPosition) {
                String[][] left =  qRunner.getDocumentText(docRank, termPos,
                  aHit.termPosition - termPos)
                left[0].each { str << (it + ' ') }
                snippet[0] = str.toString()
              } else {
                snippet[0] = "";
              }
              str = new StringBuilder()
              qRunner.getDocumentText(docRank, aHit.termPosition, 
                aHit.length)[0].each{ str << (it + ' ')}
              snippet[1] = str.toString()
              str = new StringBuilder()
              qRunner.getDocumentText(docRank, 
                aHit.termPosition + aHit.length, 3)[0].each{str << (it?:'' + ' ')}
              snippet[2] = str.toString()
              snippets << snippet  
            }
          }
          if(hits) {
            // more than 3 hits: show ellipsis
            snippets.add(["   ", "...", "   "] as String[])
          }
          docData.snippets = snippets
          documents.add(docData)
        }
        rData.setDocuments(documents)
      }
      return rData
    } else {
      throw new GwtRpcException("Could not find your query. " + 
          "Your search session may have expired, in which case you will need " +
          "to start your search again.")
    }
  }

  
  
}
