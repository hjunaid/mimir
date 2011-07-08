/*
 *  LocalIndexService.groovy
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
 *  Ian Roberts, 21 Dec 2009
 *  
 *  $Id$
 */
package gate.mimir.web;

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
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.util.*

/**
 * Service for working with local indexes.
 */
class LocalIndexService {
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
          indexers[indexId]?.close()
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
      getQueryEngine(index).close()
    }
  }
  
  public double closingProgress(LocalIndex index) {
    try{
      return findIndexer(index).getClosingProgress()
    } catch (IllegalStateException e) {
      return 1.0d
    }
  }
  
  public synchronized QueryRunner getQueryRunner(LocalIndex index, String query) {
    return getQueryEngine(index).getQueryRunner(query)
  }
  
  public synchronized void deleteDocuments(LocalIndex index, Collection<Integer> documentIds) {
    getQueryEngine(index).deleteDocuments(documentIds)
  }

  public synchronized void undeleteDocuments(LocalIndex index, Collection<Integer> documentIds) {
    getQueryEngine(index).undeleteDocuments(documentIds)
  }
    
  
  private synchronized QueryEngine getQueryEngine (LocalIndex index){
    QueryEngine engine = queryEngines[index.id]
    if(!engine) {
      if(index.state != Index.SEARCHING) {
        throw new IllegalStateException(
        "Index ${index.indexId} is not open for searching")
      }
      engine = new QueryEngine(new File(index.indexDirectory))
      engine.queryTokeniser = queryTokeniser
      engine.executor = searchThreadPool
      queryEngines[index.id] = engine
    }
    return engine    
  }
  
  public String[][] annotationsConfig(LocalIndex index) {
    
    IndexConfig indexConfig = getQueryEngine(index)?.indexConfig
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
            for(String feat : helper.getNominalFeatureNames()){
              row.add(feat);
            }
            for(String feat : helper.getIntegerFeatureNames()){
              row.add(feat);
            }
            for(String feat : helper.getFloatFeatureNames()){
              row.add(feat);
            }
            for(String feat : helper.getTextFeatureNames()){
              row.add(feat);
            }
            for(String feat : helper.getUriFeatureNames()){
              row.add(feat);
            }
          }
          rows.add(row.toArray(new String[row.size()]));
        }
      }
      return rows.toArray(new String[rows.size()][]);
    }
    else {
      throw new IllegalStateException(
        "Index ${index.indexId} is not open for searching")
    }
  }
  
  
  public void deleteIndex(LocalIndex index, deleteFiles) throws IOException {
    String indexDirectory = index.indexDirectory
    // stop the index
    try{
      getQueryEngine(index).close()
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
