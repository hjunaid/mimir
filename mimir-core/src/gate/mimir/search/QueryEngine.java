/*
 *  QueryEngine.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 04 Mar 2009
 *  
 *  $Id$
 */
package gate.mimir.search;

import gate.LanguageAnalyser;
import gate.mimir.DocumentMetadataHelper;
import gate.mimir.DocumentRenderer;
import gate.mimir.IndexConfig;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.mimir.index.mg4j.MentionsIndexBuilder;
import gate.mimir.index.mg4j.MimirDirectIndexBuilder;
import gate.mimir.index.mg4j.TokenIndexBuilder;
import gate.mimir.index.mg4j.zipcollection.DocumentCollection;
import gate.mimir.index.mg4j.zipcollection.DocumentData;
import gate.mimir.search.query.AnnotationQuery;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.ParseException;
import gate.mimir.search.query.parser.QueryParser;
import gate.mimir.search.score.MimirScorer;
import gate.mimir.util.MG4JTools;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.di.big.mg4j.index.Index;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;

/**
 * This class represents the entry point to the Mimir search API.
 */
public class QueryEngine {
  
  private class WriteDeletedDocsTask extends TimerTask {
    public void run() {
      synchronized(writeDeletedDocsTimer) {
        File delFile = new File(indexDir, DELETED_DOCUMENT_IDS_FILE_NAME);
        if(delFile.exists()) {
          delFile.delete();
        }
        try{
          logger.debug("Writing deleted documents set");
          ObjectOutputStream oos = new ObjectOutputStream(
                  new GZIPOutputStream(
                  new BufferedOutputStream(
                  new FileOutputStream(delFile))));
          oos.writeObject(deletedDocumentIds);
          oos.flush();
          oos.close();
          logger.debug("Writing deleted documents set completed.");
        }catch (IOException e) {
          logger.error("Exception while writing deleted documents set", e);
        }        
      }
    }
  }
  
  
  /**
   * Represents the type of index that should be searched. Mimir uses two types
   * of indexes: token indexes (which index the text input) and annotation
   * indexes (which index semantic annotations).
   */
  public static enum IndexType{
    /**
     * Value representing token indexes, used for the document text.
     */
    TOKENS,
    
    /**
     * Value representing annotation indexes, used for the document semantic
     * annotations.
     */
    ANNOTATIONS
  }

  /**
   * The various indexes in the Mimir composite index. The array contains first
   * the token indexes (in the same order as listed in the index configuration),
   * followed by the mentions indexes (in the same order as listed in the index
   * configuration).
   */
  protected IndexReaderPool[] indexReaderPools;
  
  /**
   * Array containing the direct indexes (if enabled) for the Mímir composite
   * index.
   */
  protected IndexReaderPool[] directIndexReaderPools;

  /**
   * The zipped document collection from MG4J (built during the indexing of the
   * first token feature). This can be used to obtain the document text and to
   * display the content of the hits.
   */
  protected DocumentCollection documentCollection;

  /**
   * A cache of {@link DocumentData} values used for returning the various
   * document details (title, URI, text).
   */
  protected Long2ObjectLinkedOpenHashMap<DocumentData> documentCache;

  /**
   * The maximum number of documents to be stored in the document cache.
   */
  protected static final int DOCUMENT_CACHE_SIZE = 100;
  
  /**
   * The set of IDs for the documents marked as deleted. 
   */
  private transient SortedSet<Long> deletedDocumentIds;
  
  /**
   * A timer used to execute the writing of deleted documents data to disk.
   * This timer is used to create a delay, allowing a batch of writes to be 
   * coalesced into a single one.
   */
  private transient Timer writeDeletedDocsTimer;
  
  /**
   * The timer task used to top write to disk the deleted documents data.
   * This value is non-null only when there is a pending write. 
   */
  private volatile transient WriteDeletedDocsTask writeDeletedDocsTask;

  /**
   * The document sizes used during search time (if running in document mode) to
   * simulate document-spanning annotations.
   */
  private transient IntBigList documentSizes;
  
  /**
   * The name for the file (stored in the root index directory) containing 
   * the serialised version of the {@link #deletedDocumentIds}. 
   */
  public static final String DELETED_DOCUMENT_IDS_FILE_NAME = "deleted.ser";
  
  
  /**
   * The maximum size of an index that can be loaded in memory (by default 64
   * MB).
   */
  public static final long MAX_IN_MEMORY_INDEX = 64 * 1024 * 1024;
  
  /**
   * The default value for the document block size.
   * @see #setDocumentBlockSize(int)
   */
  public static final int DEFAULT_DOCUMENT_BLOCK_SIZE = 1000;

  
  /**
   * The top level directory of the Mimir index.
   */
  protected File indexDir;

  /**
   * The index configuration this index was built from.
   */
  protected IndexConfig indexConfig;

  /**
   * Should sub-bindings be generated when searching?
   */
  protected boolean subBindingsEnabled;

  /**
   * A callable that produces new {@link MimirScorer} instances on request. 
   */
  protected Callable<MimirScorer> scorerSource;
  
  protected static final Logger logger = Logger.getLogger(QueryEngine.class);

  /**
   * The tokeniser (technically any GATE LA) used to split the text segments
   * found in queries into individual tokens. The same tokeniser used to create
   * the indexed documents should be used here. If this value is not set, then a
   * default ANNIE tokeniser will be used.
   */
  protected LanguageAnalyser queryTokeniser;

  /**
   * The executor used to run tasks for query execution. If the value is not
   * set, then new threads are created as needed.
   */
  protected Executor executor;

  /**
   * How many documents get ranked in one ranking stage.
   */
  private int documentBlockSize = DEFAULT_DOCUMENT_BLOCK_SIZE;
  
  /**
   * A list of currently active QueryRunners. This is used to close all active 
   * runners when the query engine itself is closed (thus releasing all open 
   * files).
   */
  private List<QueryRunner> activeQueryRunners;
  
  /**
   * @return the indexDir
   */
  public File getIndexDir() {
    return indexDir;
  }

  /**
   * Are sub-bindings used in this query engine. Sub-bindings are used to
   * associate sub-queries with segments of the returned hits. This can be
   * useful for showing high-level details about the returned hits. By default,
   * sub-bindings are not used.
   * 
   * @return the subBindingsEnabled
   */
  public boolean isSubBindingsEnabled() {
    return subBindingsEnabled;
  }

  /**
   * @param subBindingsEnabled
   *          the subBindingsEnabled to set
   */
  public void setSubBindingsEnabled(boolean subBindingsEnabled) {
    this.subBindingsEnabled = subBindingsEnabled;
  }

  /**
   * Gets the configuration parameter specifying the number of documents that 
   * get processed as a block. This is used to optimise the search 
   * process by limiting the number of results that get calculated by default.
   * @return
   */
  public int getDocumentBlockSize() {
    return documentBlockSize;
  }
  
  /**
   * Sets the configuration parameter specifying the number of documents that 
   * get processed in one go (e.g. the number of documents that get ranked when
   * enumerating results). This is used to optimise the search 
   * process by limiting the number of results that get calculated by default.
   * Defaults to {@link #DEFAULT_DOCUMENT_BLOCK_SIZE}.
   * @param documentBlockSize
   */
  public void setDocumentBlockSize(int documentBlockSize) {
    this.documentBlockSize = documentBlockSize;
  }

  /**
   * Gets the current source of scorers.
   * @see #setScorerSource(Callable)
   * @return
   */
  public Callable<MimirScorer> getScorerSource() {
    return scorerSource;
  }

  /**
   * Provides a {@link Callable} that the Query Engine can use for obtaining
   * new instances of {@link MimirScorer} to be used for ranking new queries.
   * @param scorerSource
   */
  public void setScorerSource(Callable<MimirScorer> scorerSource) {
    this.scorerSource = scorerSource;
  }

  /**
   * Gets the executor used by this query engine.
   * 
   * @return an executor that can be used for running tasks pertinent to this
   *         QueryEngine.
   */
  public Executor getExecutor() {
    return executor;
  }

  /**
   * Sets the {@link Executor} used for executing tasks required for running
   * queries. This allows the use of some type thread pooling, is needed. If
   * this value is not set, then new threads are created as required.
   * 
   * @param executor
   */
  public void setExecutor(Executor executor) {
    this.executor = executor;
  }

  /**
   * Sets the tokeniser (technically any GATE analyser) used to split the text
   * segments found in queries into individual tokens. The same tokeniser used
   * to create the indexed documents should be used here. If this value is not
   * set, then a default ANNIE tokeniser will be used.
   * 
   * @param queryTokeniser
   *          the new tokeniser to be used for parsing queries.
   */
  public void setQueryTokeniser(LanguageAnalyser queryTokeniser) {
    this.queryTokeniser = queryTokeniser;
  }

  /**
   * Finds the location for a given sub-index in the arrays returned by 
   * {@link #getIndexes()} and {@link #getDirectIndexes()}.
   * @param indexType the IndexType of the requested sub-index (tokens or 
   * annotations).
   * @param indexName the &quot;name&quot; of the requested sub-index (the 
   * indexed feature name for {@link IndexType#TOKENS} indexes, or the 
   * annotation type in the case of {@link IndexType#ANNOTATIONS} indexes). 
   * @return the position in the indexes array for the requested index, or -1 if
   * the requested index does not exist.
   */
  public int getSubIndexPosition(IndexType indexType, String indexName) {
    if(indexType == IndexType.TOKENS) {
      for(int i = 0; i < indexConfig.getTokenIndexers().length; i++) {
        if(indexConfig.getTokenIndexers()[i].getFeatureName().equals(indexName)) {
          return i; 
        }
      }
      return -1;
    } else if(indexType == IndexType.ANNOTATIONS) {
      for(int i = 0; i < indexConfig.getSemanticIndexers().length; i++) {
        for(String aType : 
            indexConfig.getSemanticIndexers()[i].getAnnotationTypes()) {
          if(aType.equals(indexName)) { 
            return indexConfig.getTokenIndexers().length + i; 
          }
        }
      }      
      return -1;
    } else {
      throw new IllegalArgumentException(
        "Don't understand sub-indexes of type " + indexType);
    }
  }
  
  /**
   * Returns the set of indexes in the Mimir composite index. The array contains
   * first the token indexes (in the same order as listed in the index
   * configuration), followed by the mentions indexes (in the same order as
   * listed in the index configuration).
   * 
   * @return an array of {@link Index} objects.
   */
  public IndexReaderPool[] getIndexes() {
    return indexReaderPools;
  }

  /**
   * Returns the set of direct indexes (if enabled) in the Mimir composite 
   * index. The array contains first the token indexes (in the same order as 
   * listed in the index configuration), followed by the mentions indexes 
   * (in the same order as listed in the index configuration). In other words,
   * this array is parallel to the one returned by {@link #getIndexes()}.
   * 
   * @return an array of {@link Index} objects.
   */
  public IndexReaderPool[] getDirectIndexes() {
    return directIndexReaderPools;
  }  
  
  /**
   * Gets the list of document sizes from one underlying MG4J index (all 
   * sub-indexes should have the same sizes).
   * @return
   */
  public IntBigList getDocumentSizes() {
    return documentSizes;
  }
  
  /**
   * Returns the index that stores the data for a particular feature of token
   * annotations.
   * 
   * @param featureName
   * @return
   */
  public IndexReaderPool getTokenIndex(String featureName) {
    for(int i = 0; i < indexConfig.getTokenIndexers().length; i++) {
      if(indexConfig.getTokenIndexers()[i].getFeatureName().equals(featureName)) {
        return indexReaderPools[i]; 
      }
    }
    return null;
  }

  /**
   * Returns the <strong>direct</strong> index that stores the data for a 
   * particular feature of token annotations. 
   * 
   * NB: direct indexes are used to search for term IDs given
   * a document ID. For standard searches (getting documents given search terms)
   * use the default (inverted) index returned by: 
   * {@link #getTokenIndex(String)}.
   * 
   * @param featureName
   * @return
   */
  public IndexReaderPool getTokenDirectIndex(String featureName) {
    for(int i = 0; i < indexConfig.getTokenIndexers().length; i++) {
      if(indexConfig.getTokenIndexers()[i].getFeatureName().equals(featureName)) {
        return directIndexReaderPools[i]; 
      }
    }
    return null;
  }  
  
  /**
   * Returns the index that stores the data for a particular semantic annotation
   * type.
   * 
   * @param annotationType
   * @return
   */
  public IndexReaderPool getAnnotationIndex(String annotationType) {
    for(int i = 0; i < indexConfig.getSemanticIndexers().length; i++) {
      for(String aType : 
          indexConfig.getSemanticIndexers()[i].getAnnotationTypes()) {
        if(aType.equals(annotationType)) { 
          return indexReaderPools[indexConfig.getTokenIndexers().length + i]; 
        }
      }
    }
    return null;
  }

  /**
   * Returns the <strong>direct</strong> index that stores the data for a 
   * particular semantic annotation type.
   * 
   * NB: direct indexes are used to search for term IDs given
   * a document ID. For standard searches (getting documents given search terms)
   * use the default (inverted) index returned by: 
   * {@link #getAnnotationIndex(String)}.
   * 
   * @param annotationType
   * @return
   */
  public IndexReaderPool getAnnotationDirectIndex(String annotationType) {
    for(int i = 0; i < indexConfig.getSemanticIndexers().length; i++) {
      for(String aType : 
          indexConfig.getSemanticIndexers()[i].getAnnotationTypes()) {
        if(aType.equals(annotationType)) { 
          return directIndexReaderPools[indexConfig.getTokenIndexers().length + i]; 
        }
      }
    }
    return null;
  }  
  
  public SemanticAnnotationHelper getAnnotationHelper(String annotationType) {
    for(int i = 0; i < indexConfig.getSemanticIndexers().length; i++) {
      String[] annTypes = indexConfig.getSemanticIndexers()[i]
          .getAnnotationTypes(); 
      for(int j = 0; j < annTypes.length; j++) {
        if(annTypes[j].equals(annotationType)) {
          return indexConfig.getSemanticIndexers()[i].getHelpers()[j];
        }
      }
    }
    return null;
  }
  
  /**
   * @return the index configuration for this index
   */
  public IndexConfig getIndexConfig() {
    return indexConfig;
  }

  /**
   * Constructs a new {@link QueryEngine} for a specified Mimir index. The mimir
   * semantic repository will be initialized using the default location in the
   * filesystem, provided by the IndexConfig
   * 
   * @param indexDir
   *          the directory containing an index.
   * @throws IndexException
   *           if there are problems while opening the indexes.
   */
  public QueryEngine(File indexDir) throws gate.mimir.index.IndexException {
    this.indexDir = indexDir;
    // read the index config
    try {
      indexConfig =
        IndexConfig.readConfigFromFile(new File(indexDir,
                Indexer.INDEX_CONFIG_FILENAME), indexDir);
      initMG4J();
      // initialise the semantic indexers
      if(indexConfig.getSemanticIndexers() != null && 
              indexConfig.getSemanticIndexers().length > 0) {
        for(SemanticIndexerConfig sic : indexConfig.getSemanticIndexers()){
          for(SemanticAnnotationHelper sah : sic.getHelpers()){
            sah.init(this);
            if(sah.getMode() == SemanticAnnotationHelper.Mode.DOCUMENT &&
                documentSizes == null) {
              // we need to load the document sizes from a token index
              documentSizes = getIndexes()[0].getIndex().sizes;
            }            
          }
        }
      }
      readDeletedDocs();
      
      activeQueryRunners = Collections.synchronizedList(
              new ArrayList<QueryRunner>());
    } catch(FileNotFoundException e) {
      throw new IndexException("File not found!", e);
    } catch(IOException e) {
      throw new IndexException("Input/output exception!", e);
    }
    subBindingsEnabled = false;
    writeDeletedDocsTimer = new Timer("Delete documents writer");
  }

  /**
   * Get the {@link SemanticAnnotationHelper} corresponding to a query's
   * annotation type.
   * @throws IllegalArgumentException if the annotation helper for this
   *         type cannot be found.
   */
  public SemanticAnnotationHelper getAnnotationHelper(AnnotationQuery query) {
    for(SemanticIndexerConfig semConfig : indexConfig.getSemanticIndexers()){
      for(int i = 0; i < semConfig.getAnnotationTypes().length; i++){
        if(query.getAnnotationType().equals(
                semConfig.getAnnotationTypes()[i])){
          return semConfig.getHelpers()[i];
        }
      }
    }
    throw new IllegalArgumentException("Semantic annotation type \""
            + query.getAnnotationType() + "\" not known to this query engine.");
  }
  
  
  /**
   * Obtains a query executor for a given {@link QueryNode}.
   * 
   * @param query
   *          the query to be executed.
   * @return a {@link QueryExecutor} for the provided query, running over the
   *         indexes in this query engine.
   * @throws IOException
   *           if the index files cannot be accessed.
   */
  public QueryRunner getQueryRunner(QueryNode query) throws IOException {
    logger.info("Executing query: " + query.toString());
    QueryExecutor qExecutor = query.getQueryExecutor(this);
    QueryRunner qRunner;
    MimirScorer scorer = null;
    try {
      scorer = scorerSource == null ? null : scorerSource.call();
    } catch(Exception e) {
      logger.error("Could not obtain a scorer. Running query unranked.", e);
    }
    qRunner = new RankingQueryRunnerImpl(qExecutor, scorer);
    activeQueryRunners.add(qRunner);
    return qRunner;
  }
  
  /**
   * Notifies the QueryEngine that the given QueryRunner has been closed. 
   * @param qRunner
   */
  public void releaseQueryRunner(QueryRunner qRunner) {
    activeQueryRunners.remove(qRunner);
  }

  /**
   * Obtains a query executor for a given query, expressed as a String.
   * 
   * @param query
   *          the query to be executed.
   * @return a {@link QueryExecutor} for the provided query, running over the
   *         indexes in this query engine.
   * @throws IOException
   *           if the index files cannot be accessed.
   * @throws ParseException
   *           if the string provided for the query cannot be parsed.
   */
  public QueryRunner getQueryRunner(String query) throws IOException,
  ParseException {
    logger.info("Executing query: " + query.toString());
    QueryNode qNode =
      (queryTokeniser == null) ? QueryParser.parse(query) : QueryParser
              .parse(query, queryTokeniser);
      return getQueryRunner(qNode);
  }

  /**
   * Obtains the document text for a given search hit.
   * 
   * @param hit
   *          the search hit for which the text is sought.
   * @param leftContext
   *          the number of tokens to the left of the hit to be included in the
   *          result.
   * @param rightContext
   *          the number of tokens to the right of the hit to be included in the
   *          result.
   * @return an array of arrays of {@link String}s, representing the tokens and
   *         spaces at the location of the search hit. The first element of the
   *         array is an array of tokens, the second element contains the
   *         spaces.The first element of each array corresponds to the first
   *         token of the left context.
   * @throws IOException
   */
  public String[][] getHitText(Binding hit, int leftContext, int rightContext)
  throws IndexException {
    return getText(hit.getDocumentId(), hit.getTermPosition() - leftContext,
            leftContext + hit.getLength() + rightContext);
  }

  /**
   * Gets the text covered by a given binding.
   * 
   * @param hit
   *          the binding.
   * @return an array of two string arrays, the first representing the tokens
   *         covered by the binding and the second the spaces after each token.
   * @throws IOException
   */
  public String[][] getHitText(Binding hit) throws IndexException {
    return getText(hit.getDocumentId(), hit.getTermPosition(), hit.getLength());
  }

  /**
   * Get the text to the left of the given binding.
   * 
   * @param hit
   *          the binding.
   * @param numTokens
   *          the maximum number of tokens of context to return. The actual
   *          number of tokens returned may be smaller than this if the hit
   *          starts within <code>numTokens</code> tokens of the start of the
   *          document.
   * @return an array of two string arrays, the first representing the tokens
   *         before the binding and the second the spaces after each token.
   * @throws IOException
   */
  public String[][] getLeftContext(Binding hit, int numTokens)
  throws IndexException {
    int startOffset = hit.getTermPosition() - numTokens;
    // if numTokens is greater than the start offset of the hit
    // then we need to return all the document text up to the
    // token before the hit position (possibly no tokens...)
    if(startOffset < 0) {
      numTokens += startOffset; // startOffset is negative, so this will
      // subtract from numTokens
      startOffset = 0;
    }
    return getText(hit.getDocumentId(), startOffset, numTokens);
  }

  /**
   * Get the text to the right of the given binding.
   * 
   * @param hit
   *          the binding.
   * @param numTokens
   *          the maximum number of tokens of context to return. The actual
   *          number of tokens returned may be smaller than this if the hit ends
   *          within <code>numTokens</code> tokens of the end of the document.
   * @return an array of two string arrays, the first representing the tokens
   *         after the binding and the second the spaces after each token.
   * @throws IOException
   */
  public String[][] getRightContext(Binding hit, int numTokens)
  throws IndexException {
    DocumentData docData = getDocumentData(hit.getDocumentId());
    int startOffset = hit.getTermPosition() + hit.getLength();
    if(startOffset >= docData.getTokens().length) {
      // hit is at the end of the document
      return new String[][]{new String[0], new String[0]};
    }
    if(startOffset + numTokens > docData.getTokens().length) {
      // fewer than numTokens tokens of right context available, adjust
      numTokens = docData.getTokens().length - startOffset;
    }
    return getText(hit.getDocumentId(), startOffset, numTokens);
  }

  /**
   * Obtains the text for a specified region of a document. The return value is
   * a pair of parallel arrays, one of tokens and the other of the spaces
   * between them. If <code>length >= 0</code>, the two parallel arrays will
   * always be exactly <code>length</code> items long, but any token positions
   * that do not exist in the document (i.e. before the start or beyond the end
   * of the text) will be <code>null</code>. If <code>length &lt; 0</code> the
   * arrays will be of sufficient length to hold all the tokens from
   * <code>termPosition</code> to the end of the document, with no trailing
   * <code>null</code>s (there may be leading <code>null</code>s if
   * <code>termPosition &lt; 0</code>).
   * 
   * @param documentID
   *          the document ID
   * @param termPosition
   *          the position of the first term required
   * @param length
   *          the number of terms to return. May be negativem, in which case all
   *          terms from termPosition to the end of the document will be
   *          returned.
   * @return an array of two string arrays. The first represents the tokens and
   *         the second represents the spaces between them
   * @throws IndexException
   */
  public String[][] getText(long documentID, int termPosition, int length)
  throws IndexException {
    return getDocumentData(documentID).getText(termPosition, length);
  }

  /**
   * Renders a document and a list of hits.
   * 
   * @param docID
   *          the document to be rendered.
   * @param hits
   *          the list of hits to be rendered.
   * @param output
   *          the {@link Appendable} used to write the output.
   * @throws IOException
   *           if the output cannot be written to.
   * @throws IndexException
   *           if no document renderer is available.
   */
  public void renderDocument(long docID, List<Binding> hits, Appendable output)
  throws IOException, IndexException {
    DocumentRenderer docRenderer = indexConfig.getDocumentRenderer();
    if(docRenderer == null) { throw new IndexException(
    "No document renderer is configured for this index!"); }
    docRenderer.render(getDocumentData(docID), hits, output);
  }

  public String getDocumentTitle(long docID) throws IndexException {
    return getDocumentData(docID).getDocumentTitle();
  }

  public String getDocumentURI(long docID) throws IndexException {
    return getDocumentData(docID).getDocumentURI();
  }

  /**
   * Obtains an arbitrary document metadata field from the stored document data.
   * {@link DocumentMetadataHelper}s used at indexing time can add arbitrary 
   * {@link Serializable} values as metadata fields for the documents being
   * indexed. This method is used at search time to retrieve those values. 
   *  
   * @param docID the ID of document for which the metadata is sought.
   * @param fieldName the name of the metadata filed to be obtained
   * @return the de-serialised value stored at indexing time for the given 
   * field name and document.
   * @throws IndexException
   */
  public Serializable getDocumentMetadataField(long docID, String fieldName) 
      throws IndexException {
    return getDocumentData(docID).getMetadataField(fieldName);
  }
  
  /**
   * Gets the {@link DocumentData} for a given document ID, from the on disk 
   * document collection. In memory caching is performed to reduce the cost of 
   * this call. 
   * @param documentID
   *          the ID of the document to be obtained.
   * @return the {@link DocumentData} associated with the given document ID.
   * @throws IndexException
   */
  public synchronized DocumentData getDocumentData(long documentID)
  throws IndexException {
    if(isDeleted(documentID)) {
      throw new IndexException("Invalid document ID " + documentID);
    }
    DocumentData documentData = documentCache.getAndMoveToFirst(documentID);
    if(documentData == null) {
      // cache miss
      documentData = documentCollection.getDocumentData(documentID);
      documentCache.putAndMoveToFirst(documentID, documentData);
      if(documentCache.size() > DOCUMENT_CACHE_SIZE) {
        documentCache.removeLast();
      }
    }
    return documentData;
  }

  /**
   * Closes this {@link QueryEngine} and releases all resources.
   */
  public void close() {
    // close all active query runners
    List<QueryRunner> runnersCopy = new ArrayList<QueryRunner>(activeQueryRunners);
    for(QueryRunner aRunner : runnersCopy) {
      try {
        logger.debug("Closing query runner: " + aRunner.toString());
        aRunner.close();
      } catch(IOException e) {
        // log and ignore
        logger.error("Exception while closing query runner.", e);
      }
    }
    // close the document collection
    documentCollection.close();
    // close all the semantic indexers
    logger.info("Closing Semantic Annotation Helpers.");
    if(indexConfig.getSemanticIndexers() != null) {
      for(SemanticIndexerConfig aSIC : indexConfig.getSemanticIndexers()) {
        if(aSIC.getHelpers() != null) {
          for(int i = 0; i < aSIC.getHelpers().length; i++) {
            SemanticAnnotationHelper aHelper = aSIC.getHelpers()[i];
            aHelper.close(this);
          }
        }
      }
    }
    // write the deleted documents set
    synchronized(writeDeletedDocsTimer) {
      if(writeDeletedDocsTask != null) {
        writeDeletedDocsTask.cancel();
      }
      writeDeletedDocsTimer.cancel();
      // explicitly call it one last time
      new WriteDeletedDocsTask().run();
    }
    documentCache.clear();
    for(IndexReaderPool aPool : indexReaderPools) {
      try {
        aPool.close();
      } catch(IOException e) {
        // log and ignore
        logger.error("Exception while closing index reader pool.", e);
      }
    }
    indexReaderPools = null;

    for(IndexReaderPool aPool : directIndexReaderPools) {
      try {
        if(aPool != null) aPool.close();
      } catch(IOException e) {
        // log and ignore
        logger.error("Exception while closing direct index reader pool.", e);
      }
    }
    directIndexReaderPools = null;
    
  }

  /**
   * Opens all the MG4J indexes.
   * 
   * @throws IOException
   * @throws IndexException
   */
  protected void initMG4J() throws IOException, IndexException {
    indexReaderPools = new IndexReaderPool[
        indexConfig.getTokenIndexers().length + 
        indexConfig.getSemanticIndexers().length];
    
    directIndexReaderPools = new IndexReaderPool[
        indexConfig.getTokenIndexers().length +
        indexConfig.getSemanticIndexers().length];
    
    try {
      File mg4JIndexDir = new File(indexDir, Indexer.MG4J_INDEX_DIRNAME);
      // Load the token indexes
      for(int i = 0; i < indexConfig.getTokenIndexers().length; i++) {
        File indexBasename =
          new File(mg4JIndexDir, Indexer.MG4J_INDEX_BASENAME + "-"
                  + TokenIndexBuilder.TOKEN_INDEX_BASENAME + "-" + i);
        indexReaderPools[i] = openOneSubIndex(indexBasename.toURI());
        directIndexReaderPools[i] = null;
        if(indexConfig.getTokenIndexers()[i].isDirectIndexEnabled()) {
          indexBasename = new File(mg4JIndexDir, 
              Indexer.MG4J_INDEX_BASENAME + "-" + 
              TokenIndexBuilder.TOKEN_INDEX_BASENAME + "-" + i + 
              MimirDirectIndexBuilder.BASENAME_SUFFIX);
            directIndexReaderPools[i] = openOneSubIndex(indexBasename.toURI());
        }
      }
      // load the mentions indexes
      for(int i = 0; i < indexConfig.getSemanticIndexers().length; i++) {
        File indexBasename =
          new File(mg4JIndexDir, Indexer.MG4J_INDEX_BASENAME + "-"
                  + MentionsIndexBuilder.MENTIONS_INDEX_BASENAME + "-"
                  + i);
        indexReaderPools[indexConfig.getTokenIndexers().length + i] =
          openOneSubIndex(indexBasename.toURI());
        directIndexReaderPools[indexConfig.getTokenIndexers().length + i] = null;
        if(indexConfig.getSemanticIndexers()[i].isDirectIndexEnabled()) {
          indexBasename = new File(mg4JIndexDir, 
              Indexer.MG4J_INDEX_BASENAME + "-" + 
              MentionsIndexBuilder.MENTIONS_INDEX_BASENAME + "-" + i +
              MimirDirectIndexBuilder.BASENAME_SUFFIX);
          directIndexReaderPools[indexConfig.getTokenIndexers().length + i] = 
              openOneSubIndex(indexBasename.toURI());
        }
      }
      // open the zipped document collection
      documentCollection = new DocumentCollection(indexDir);
      // prepare the document cache
      documentCache = new Long2ObjectLinkedOpenHashMap<DocumentData>();
    } catch(IOException e) {
      // IOException gets thrown upward
      throw e;
    } catch(Exception e) {
      // all other exceptions get wrapped
      throw new IndexException("Exception while opening indexes.", e);
    }
  }

  /**
   * Opens on MG4J sub-index.
   * 
   * @param indexUri
   *          the URI of the index to be opened. This a file path containing the
   *          basename for all the files in the index (i.e. the full file path
   *          without the extension).
   * @return a {@link Index} implementation.
   * @throws ConfigurationException
   * @throws SecurityException
   * @throws IOException
   * @throws URISyntaxException
   * @throws ClassNotFoundException
   * @throws InstantiationException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws NoSuchMethodException
   */
  protected IndexReaderPool openOneSubIndex(URI indexUri) throws ConfigurationException,
  SecurityException, IOException, URISyntaxException,
  ClassNotFoundException, InstantiationException,
  IllegalAccessException, InvocationTargetException,
  NoSuchMethodException {
    // see if the index needs upgrading
    MG4JTools.upgradeIndex(indexUri);
    Index theIndex = MG4JTools.openMg4jIndex(indexUri);
    return new IndexReaderPool(theIndex, indexUri);
  }
  
  /**
   * Given a index URI (a file URI denoting the index base name for all the 
   * index files), this method checks if the index if an older version, and 
   * upgrades it to the current version, making sure it can be opened. 
   * @param indexUri
   * @throws IOException
   * @throws ClassNotFoundException
   * @throws ConfigurationException 
   * @deprecated Use {@link MG4JTools#upgradeIndex(URI)} instead
   */
  public static void upgradeIndex(URI indexUri) throws IOException, 
      ClassNotFoundException, ConfigurationException {
        MG4JTools.upgradeIndex(indexUri);
      }
  
  /**
   * Marks a given document (identified by its ID) as deleted. Deleted documents
   * are never returned as search results.
   * @param documentId
   */
  public void deleteDocument(long documentId) {
    if(deletedDocumentIds.add(documentId)) {
      writeDeletedDocsLater();
    }
  }

  /**
   * Marks the given batch of documents (identified by ID) as deleted. Deleted
   * documents are never returned as search results.
   * @param documentIds
   */
  public void deleteDocuments(Collection<? extends Number> documentIds) {
    List<Long> idsToDelete = new ArrayList<Long>(documentIds.size());
    for(Number n : documentIds) {
      idsToDelete.add(Long.valueOf(n.longValue()));
    }
    if(deletedDocumentIds.addAll(idsToDelete)) {
      writeDeletedDocsLater();
    }
  }
  
  /**
   * Checks whether a given document (specified by its ID) is marked as deleted. 
   * @param documentId
   * @return
   */
  public boolean isDeleted(long documentId) {
    return deletedDocumentIds.contains(documentId);
  }
  
  /**
   * Mark the given document (identified by ID) as <i>not</i> deleted.  Calling
   * this method for a document ID that is not currently marked as deleted has
   * no effect.
   */
  public void undeleteDocument(long documentId) {
    if(deletedDocumentIds.remove(documentId)) {
      writeDeletedDocsLater();
    }
  }
  
  /**
   * Mark the given documents (identified by ID) as <i>not</i> deleted.  Calling
   * this method for a document ID that is not currently marked as deleted has
   * no effect.
   */
  public void undeleteDocuments(Collection<? extends Number> documentIds) {
    List<Long> idsToUndelete = new ArrayList<Long>(documentIds.size());
    for(Number n : documentIds) {
      idsToUndelete.add(Long.valueOf(n.longValue()));
    }
    if(deletedDocumentIds.removeAll(idsToUndelete)) {
      writeDeletedDocsLater();
    }
  }
  
  /**
   * Writes the set of deleted document to disk in a background thread, after a
   * short delay. If a previous request has not started yet, this new request 
   * will replace it. 
   */
  protected void writeDeletedDocsLater() {
    synchronized(writeDeletedDocsTimer) {
      if(writeDeletedDocsTask != null) {
        writeDeletedDocsTask.cancel();
      }
      writeDeletedDocsTask = new WriteDeletedDocsTask();
      writeDeletedDocsTimer.schedule(writeDeletedDocsTask, 1000);
    }
  }
  
  /**
   * Reads the list of deleted documents from disk. 
   */
  @SuppressWarnings("unchecked")
  protected synchronized void readDeletedDocs() throws IOException{
    deletedDocumentIds = Collections.synchronizedSortedSet(
            new TreeSet<Long>());
    File delFile = new File(indexDir, DELETED_DOCUMENT_IDS_FILE_NAME);
    if(delFile.exists()) {
      try {
        ObjectInputStream ois = new ObjectInputStream(
                new GZIPInputStream(
                new BufferedInputStream(
                new FileInputStream(delFile))));
        // an old index will have saved a Set<Integer>, a new one will be
        // Set<Long>
        Set<? extends Number> savedSet = (Set<? extends Number>)ois.readObject();
        for(Number n : savedSet) {
          deletedDocumentIds.add(Long.valueOf(n.longValue()));
        }
      } catch(ClassNotFoundException e) {
        // this should never happen
        throw new RuntimeException(e);
      }
    }
  }
}
