/*
 *  LocalIndexService.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 21 Dec 2009
 *  
 *  $Id$
 */
package gate.mimir.web;

import java.util.concurrent.Callable;

import gate.mimir.web.Index;
import gate.mimir.web.IndexTemplate;
import gate.mimir.web.LocalIndex;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryRunner;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.IndexConfig
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer
import gate.mimir.index.mg4j.zipcollection.DocumentData;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.search.query.parser.ParseException;
import gate.mimir.search.score.MimirScorer
import gate.mimir.util.*

/**
 * Service for working with local indexes.
 */
class LocalIndexService {
  
  def grailsApplication 
  
  /**
   * We don't use transactions, as we don't access the database from this 
   * service.
   */
  static transactional = true

  /**
   * This service is a singleton.
   */
  static scope = "singleton"

  /**
   * Tokeniser for queries (autowired).
   */
  def queryTokeniser
  
  /**
   * Shared thread pool (autowired)
   */
  def searchThreadPool
    
  private Map indexers = [:]


  private Map queryEngines = [:]

  /**
   * At startup, any indexes that are listed in the DB as being in any state
   * other than searching are now invalid, so need to be marked as failed.
   */
  public void init() {
    LocalIndex.withTransaction {
      LocalIndex.list().each {
        if(it.state != Index.SEARCHING) {
          it.state = Index.FAILED
        }
      }
    }
  }
                          
  public synchronized Indexer findIndexer(LocalIndex index) {
    def indexer = indexers[index.id]
    if(!indexer) {
      if(index.state != Index.INDEXING) {
        throw new IllegalStateException(
            "Index ${index.indexId} is not open for indexing")
      }
    }
    return indexer
  }

  /**
   * Create a new index on disk from the given template in the directory
   * specified by the given LocalIndex, and store the corresponding Indexer for
   * future use.
   */
  public synchronized Indexer createIndex(LocalIndex index, IndexTemplate templ) {
    def indexConfig = GroovyIndexConfigParser.createIndexConfig(
        templ.configuration, new File(index.indexDirectory))
    Indexer indexer = new Indexer(indexConfig)
    indexers[index.id] = indexer
    return indexer
  }

  public void close(LocalIndex index) {
    if(index.state == Index.INDEXING) {
      index.state = Index.CLOSING
      index.save()
      def indexId = index.id
      Thread.start {
        try {
          indexers.remove(indexId)?.close()
          LocalIndex.withTransaction { status ->
            def theIndex = LocalIndex.get(indexId)
            theIndex.state = Index.SEARCHING
            theIndex.save()
          }
        }
        catch(IndexException e) {
          log.error("Error while closing index ${indexId}", e)
          LocalIndex.withTransaction { status ->
            def theIndex = LocalIndex.get(indexId)
            theIndex.state = Index.FAILED
            theIndex.save()          
          }
        }
      }
    } else if(index.state == Index.SEARCHING) {
      QueryEngine engine = queryEngines.remove(index.id)
      if(engine) {
        engine.close()
      }
    }
  }
  
  public double closingProgress(LocalIndex index) {
    try{
      return findIndexer(index).getClosingProgress()
    } catch (IllegalStateException e) {
      return 1.0d
    }
  }
  
  public synchronized QueryRunner getQueryRunner(LocalIndex index, String query) 
      throws ParseException {
    return getQueryEngine(index).getQueryRunner(query)
  }
  
  public synchronized DocumentData getDocumentData(LocalIndex index, long documentId) {
    return getQueryEngine(index).getDocumentData(documentId)
  }
  
  public synchronized void renderDocument(LocalIndex index, long documentId, Appendable out) {
    getQueryEngine(index).renderDocument(documentId, [], out)
  }
  
  
  public synchronized void deleteDocuments(LocalIndex index, Collection<Long> documentIds) {
    getQueryEngine(index).deleteDocuments(documentIds)
  }

  public synchronized void undeleteDocuments(LocalIndex index, Collection<Long> documentIds) {
    getQueryEngine(index).undeleteDocuments(documentIds)
  }
    
  
  public synchronized QueryEngine getQueryEngine (LocalIndex index){
    QueryEngine engine = queryEngines[index.id]
    if(!engine) {
      if(index.state != Index.SEARCHING) {
        throw new IllegalStateException(
        "Index ${index.indexId} is not open for searching")
      }
      try {
        engine = new QueryEngine(new File(index.indexDirectory))
        engine.queryTokeniser = queryTokeniser
        engine.executor = searchThreadPool
        engine.setSubBindingsEnabled(index.subBindingsEnabled?:false) 
        queryEngines[index.id] = engine
      } catch (Exception e) {
        log.error("Cannot open local index at ${index?.indexDirectory}", e)
        index.state = Index.FAILED
        return null
      }
    }
    // the scorer may have changed, so we update it every time
    if(index.scorer) {
      engine.setScorerSource(grailsApplication.config.gate.mimir.scorers[index.scorer] as Callable<MimirScorer>)
    } else {
      engine.setScorerSource(null)
    }
    return engine    
  }
  
  public String[][] annotationsConfig(LocalIndex index) {
    IndexConfig indexConfig = null
    if(index.state == Index.INDEXING) {
      indexConfig = findIndexer(index)?.indexConfig
    } else if(index.state == Index.SEARCHING) {
      indexConfig = getQueryEngine(index)?.indexConfig
    }
    if(indexConfig) {
      SemanticIndexerConfig[] semIndexers = indexConfig.getSemanticIndexers();
      List<String[]> rows = new ArrayList<String[]>();
      for(SemanticIndexerConfig semConf : semIndexers){
        String[] types = semConf.getAnnotationTypes();
        SemanticAnnotationHelper[] helpers = semConf.getHelpers();
        for(int i = 0; i < types.length; i++){
          List<String> row = new ArrayList<String>();
          //first add the ann type
          row.add(types[i]);
          //next, add its features, if known
          if(helpers[i] instanceof AbstractSemanticAnnotationHelper){
            AbstractSemanticAnnotationHelper helper = 
            (AbstractSemanticAnnotationHelper)helpers[i];
            for(String feat : helper.getNominalFeatures()){
              row.add(feat);
            }
            for(String feat : helper.getIntegerFeatures()){
              row.add(feat);
            }
            for(String feat : helper.getFloatFeatures()){
              row.add(feat);
            }
            for(String feat : helper.getTextFeatures()){
              row.add(feat);
            }
            for(String feat : helper.getUriFeatures()){
              row.add(feat);
            }
          }
          rows.add(row.toArray(new String[row.size()]));
        }
      }
      return rows.toArray(new String[rows.size()][]);
    }
    else {
      return ([] as String[][])
    }
  }
  
  
  public void deleteIndex(LocalIndex index, deleteFiles) throws IOException {
    String indexDirectory = index.indexDirectory
    // stop the index
    try{
      QueryEngine engine = queryEngines.remove(index.id)
      if (engine) {
        engine.close()
      }
    } catch(Exception e) {
      log.warn("Exception while trying to close index, prior to deletion", e)
    }
    // delete the index fromDB
    try {
      log.warn("Deleting index from DB!")
      index.attach()
      index.delete(flush:true)
    }
    catch(Exception e) {
      throw new IOException("Index deletion failed (${e.message})")
    }
    if(deleteFiles) {
      log.warn("Deleting files!")
      if(!(new File(indexDirectory).deleteDir())) {
        throw new IOException("Index deleted, but could not delete index files at ${indexDirectory}")
      }
    }
  } 
  
  @PreDestroy
  public void destroy() {
    // close the local indexes in a civilised fashion
    // (for indexes that are not in SEARCHING mode, there is no civilised way!)
    LocalIndex.list().each{ LocalIndex index ->
      if(index.state == Index.SEARCHING) close(index)
    }
  }
}
