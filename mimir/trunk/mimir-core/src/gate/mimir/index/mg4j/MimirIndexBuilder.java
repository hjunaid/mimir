/*
 *  MimirIndexBuilder.java
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
 *  Valentin Tablan, Ian Roberts, 03 Mar 2009
 *
 *  $Id$
 */
package gate.mimir.index.mg4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;


import gate.Annotation;
import gate.mimir.IndexConfig;
import gate.mimir.index.*;
import gate.util.GateRuntimeException;
import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.mg4j.index.*;
import it.unimi.dsi.mg4j.index.CompressionFlags.Coding;
import it.unimi.dsi.mg4j.index.CompressionFlags.Component;
import it.unimi.dsi.mg4j.index.cluster.ContiguousDocumentalStrategy;
import it.unimi.dsi.mg4j.index.cluster.DocumentalCluster;
import it.unimi.dsi.mg4j.index.cluster.DocumentalConcatenatedCluster;
import it.unimi.dsi.mg4j.index.cluster.IndexCluster;
import it.unimi.dsi.mg4j.io.ByteArrayPostingList;
import it.unimi.dsi.mg4j.tool.Combine;
import it.unimi.dsi.mg4j.tool.Concatenate;
import it.unimi.dsi.mg4j.tool.Scan;
import it.unimi.dsi.mg4j.tool.Scan.Completeness;
import it.unimi.dsi.util.ImmutableExternalPrefixMap;
import it.unimi.dsi.util.Properties;
import it.unimi.dsi.util.StringMaps;

/**
 * An index builder suitable for use by mimir.  It follows the logic
 * of the MG4J IndexBuilder and Scan classes but in a simplified way:
 * <ul>
 * <li>We only support "standard" indexing, no virtual fields</li>
 * <li>We only index a single field - Mimir uses a pipeline of separate
 * builders, one for each index</li>
 * <li>We don't do memory compaction, so as to be a nicer citizen when
 * sharing the JVM with other classes</li>
 * <li>We allow multiple consecutive postings at the same location.
 * The logic to determine the correct location for a posting is delegated
 * to subclasses rather than hard-coded as a simple ++.</li>
 * <li>Instead of iterating over the collection with a DocumentIterator,
 * we receive documents and send results using BlockingQueues.</li>
 * </ul>
 * 
 * Subclasses must implement {@link #indexBasename},
 * {@link #getAnnotsToProcess}, {@link #calculateStartPositionForAnnotation}
 * and {@link #calculateTermStringForAnnotation}.  They may also override
 * {@link #documentStarting} and {@link #documentEnding} if they require
 * notification of the start and end of document processing, and
 * {@link #initIndex()} and {@link #flush()} to perform actions at the
 * beginning and end of the whole indexing process (in these cases you
 * <em>must</em> call the super method).
 * @author ian
 */
public abstract class MimirIndexBuilder implements Runnable {
  /**
   * This is a slightly modified version of the {@link ByteArrayPostingList} 
   * class in the sense that it silently ignores subsequent calls to 
   * addPosition(int), if the position provided is the same as the previous one. 
   */
  protected static class PostingsList extends ByteArrayPostingList{
    public PostingsList(byte[] a, boolean differential) {
      super(a, differential, Completeness.POSITIONS);
    }

    /**
     * The last seen position.
     */
    private int lastPosition = -1;
    
    /**
     * The last seen document pointer.
     */
    private int lastDocumentPointer = -1;
    
    @Override
    public void setDocumentPointer(int pointer) {
      if(pointer != lastDocumentPointer) {
        // reset the lastPosition when moving to a new document
        lastDocumentPointer = pointer;
        lastPosition = -1;
      }
      super.setDocumentPointer(pointer);
    }

    @Override
    public void addPosition(int pos) {
      //ignore if the position hasn't changed.
      if(pos == lastPosition) return;
      //otherwise call the super
      super.addPosition(pos);
      //and update lastPosition
      lastPosition = pos;
    }
    
    
    /**
     * Checks whether the given position is valid (i.e. greater than the last 
     * seen positions. If the position is invalid, this means that a call to
     * {@link #addPosition(int)} with the same value would actually be a 
     * no-operation.  
     * @param pos
     * @return
     */
    public boolean checkPosition(int pos){
      return pos != lastPosition;
    }
  }
  
  private static Logger logger = Logger.getLogger(MimirIndexBuilder.class);
  
  /**
   * Progress logger.
   */
  protected ProgressLogger progressLogger;
  

  /**
   * A blocking queue where documents are queued for indexing.
   */
  private BlockingQueue<GATEDocument> inputQueue;
  
  /**
   * An output queue, where indexed documents are returned.
   */
  private BlockingQueue<GATEDocument> outputQueue;
  
  /**
   * Flag showing whether the indexer is closed. 
   */
  private boolean closed = false;
  
  /**
   * A value between 0 and 1 representing the progress of the current index 
   * closing operation. 
   */
  private volatile double closingProgress = 0.0;
  
  /**
   * The index configuration.
   */
  protected IndexConfig indexConfig;
  
  /**
   * The current document pointer (gets incremented for each document).
   */
  protected int documentPointer;
  
  /**
   * The position of the current (or most-recently used) token in the current
   * document.
   */
  protected int tokenPosition;
  
  
  /**
   * The highest term position (i.e. document size) in the current batch. 
   */
  protected int maxTermPositionInBatch;

  /**
   * The highest term position (i.e. document size) in all the batches. 
   */
  protected int maxTermPositionGlobal;
  
  /**
   * The ID of the current batch.
   */
  protected int currentBatch;
  
  /**
   * The number of postings in the current batch.
   */
  protected int occurrencesInTheCurrentBatch;
  
  /**
   * The number of postings in the whole index.
   */
  protected long occurrencesInTotal;
  
  protected long postingsInTotal;
  /**
   * The total number of documents indexed.
   */
  protected int totalDocuments;
  
  /** The initial size of the term map. */
  private static final int INITIAL_TERM_MAP_SIZE = 1000;
  
  /** The default buffer size. */
  public static final int DEFAULT_BUFFER_SIZE = 64 * 1024;
  
  /** The extension of the property file for the cluster associated to a scan. */
  private static final String CLUSTER_STRATEGY_EXTENSION = ".cluster.strategy";

  /** The extension of the strategy for the cluster associated to a scan. */
  public static final String CLUSTER_PROPERTIES_EXTENSION = ".cluster.properties";

  /**
   * How many batches to combine at one time. If the number of batches to be
   * combined is greater than this value, then hierarchical batch combination
   * will be employed.
   */
  public static final int MAXIMUM_BATCHES_TO_COMBINE = 50;
  
  /**
   * If available memory drops below this percentage we will force all
   * indexers to dump a batch.
   */
  public static final long MIN_AVAILABLE_MEMORY = 20;
  
  /**
   * An in-memory inverted index that gets dumped to files for each batch. 
   */
  protected Object2ReferenceOpenHashMap<MutableString, PostingsList> termMap;
  
  /** The flag map for batches. */
  protected Map<Component, Coding> flags;
  
  /**
   * The output bit stream for size information. It saves the list of 
   * &gamma;-coded document sizes.
   */
  protected OutputBitStream sizesStream;
  
  /**
   * A mutable string used to create instances of MutableString on the cheap.
   */
  protected MutableString currentTerm;
  
  /**
   * The term processor used to process the feature values being indexed.
   */
  protected TermProcessor termProcessor;

  
  /**
   * If true, we should dump a batch at the next available opportunity.
   * Volatile as it may be set from any of the sub-indexer threads.
   */
  protected volatile boolean dumpBatchASAP;
  
  /**
   * The cutpoints of the batches (for building later a
   * {@link it.unimi.dsi.mg4j.index.cluster.ContiguousDocumentalStrategy}).
   */
  protected IntArrayList cutPoints;
  
  
  protected int globalMaxCount;

  /**
   * The top level {@link Indexer} controlling this index builder
   */
  protected Indexer indexer;
  
  /**
   * The base name used for files created by this indexer. 
   */
  protected String indexBaseName;
  
  
  public MimirIndexBuilder(BlockingQueue<GATEDocument> inputQueue,
          BlockingQueue<GATEDocument> outputQueue,
          Indexer indexer, 
          String baseName) {
    this.inputQueue = inputQueue;
    this.outputQueue = outputQueue;
    this.indexer = indexer;
    this.indexConfig = indexer.getIndexConfig();
    this.indexBaseName = baseName;
    
    // create the progress logger.  We use this.getClass to use the
    // logger belonging to a subclass rather than our own.
    this.progressLogger = new ProgressLogger(
            Logger.getLogger(this.getClass()), "documents");
    closed = false;
    closingProgress = 0;
  }
  
  // Abstract methods, must be implemented by subclasses
  
  /**
   * Get the basename of the index created by this builder (e.g.
   * the feature name for an index based on Token features).
   */
  protected String indexBasename(){
    return indexBaseName;
  }
  
  /**
   * Get the annotations that are to be processed for a document,
   * in increasing order of offset.
   */
  protected abstract Annotation[] getAnnotsToProcess(
          GATEDocument gateDocument) throws IndexException;
  
  /**
   * Hook for subclasses, called before processing the annotations
   * for this document.  The default implementation is a no-op.
   */
  protected void documentStarting(GATEDocument gateDocument) throws IndexException {
  }

  /**
   * Hook for subclasses, called after annotations for this document
   * have been processed.  The default implementation is a no-op.
   */
  protected void documentEnding(GATEDocument gateDocument) throws IndexException {
  }

  /**
   * Calculate the starting position for the given annotation, storing
   * it in {@link #tokenPosition}.  The starting position is the
   * index of the token within the document where the annotation starts,
   * and <em>must</em> be &gt;= the previous value of tokenPosition.
   * @param ann
   * @param gateDocument
   */
  protected abstract void calculateStartPositionForAnnotation(Annotation ann,
          GATEDocument gateDocument) throws IndexException;
  
  /**
   * Determine the string (or strings, if there are alternatives) that should 
   * be stored in the index for the given annotation.
   * 
   * If a single string value should be returned, it is more efficient to store
   * the value in {@link #currentTerm}, in which case <code>null</code> should 
   * be returned instead.
   * 
   * If the current term should not be indexed (e.g. it's a stop word), then 
   * the implementation should return an empty String array.
   * 
   * @param ann
   * @param gateDocument
   */
  protected abstract String[] calculateTermStringForAnnotation(Annotation ann,
          GATEDocument gateDocument) throws IndexException;
  
  
  // template methods, which call the abstract ones above

  protected void initIndex() throws FileNotFoundException{
    totalDocuments = 0;
    //initialise the last token position (we start from 0)
    tokenPosition = 0;
    maxTermPositionGlobal = 0;
    globalMaxCount = 0;
    //create the shared instance of mutable word
    currentTerm = new MutableString();
    //create the terms map
    termMap = new Object2ReferenceOpenHashMap<MutableString, 
        PostingsList>(INITIAL_TERM_MAP_SIZE, Hash.FAST_LOAD_FACTOR );
    //use default flags for coding
    flags = new EnumMap<Component, Coding>(
            CompressionFlags.DEFAULT_STANDARD_INDEX);
    //first batch
    currentBatch = 0;
    occurrencesInTotal = 0;
    postingsInTotal = 0;
    //initialise the cutpoints array
    cutPoints = new IntArrayList();
    cutPoints.add(0);

    File indexDir = new File(indexConfig.getIndexDirectory(), 
            Indexer.MG4J_INDEX_DIRNAME);
    indexDir.mkdirs();    
    //start the first batch
    initBatch();
  }

  /**
   * Initialises the current batch.
   * @throws FileNotFoundException 
   */
  protected void initBatch() throws FileNotFoundException{
    dumpBatchASAP = false;
    //start with 0 for first document
    documentPointer = 0;
    maxTermPositionInBatch = 0;
    //make sure we have an empty term map
    if(termMap.size() > 0) termMap.clear();
    occurrencesInTheCurrentBatch = 0;
    //open the sizes stream
    File sizesFile = getBatchFile(DiskBasedIndex.SIZES_EXTENSION);
    sizesStream = new OutputBitStream(sizesFile);
  }
  
  protected File getBatchFile(String extension){
    return getBatchFile(extension, currentBatch);
  }
  
  protected File getBatchFile(String extension, int batch){
    File indexDir = new File(indexConfig.getIndexDirectory(), 
            Indexer.MG4J_INDEX_DIRNAME);
    return new File(indexDir, 
            Indexer.MG4J_INDEX_BASENAME + "-" + indexBasename() + 
            "@" + (batch) + extension);
  }

  protected File getGlobalFile(String extension){
    File indexDir = new File(indexConfig.getIndexDirectory(), 
            Indexer.MG4J_INDEX_DIRNAME);
    return new File(indexDir, 
            Indexer.MG4J_INDEX_BASENAME + "-" + indexBasename() + 
            extension);
  }
  
  protected void processDocument(GATEDocument gateDocument) throws IndexException{
    //zero document related counters
    tokenPosition = 0;

    //get the annotations to be processed
    Annotation[] annotsToProcess = getAnnotsToProcess(gateDocument);
    
    logger.debug("Starting document "
            + gateDocument.getDocument().getName() + ". "
            + annotsToProcess.length + " annotations to process");
    
    documentStarting(gateDocument);

    //process the annotations one by one.
    for(Annotation ann : annotsToProcess){
      processAnnotation(ann, gateDocument);
    }
    
    documentEnding(gateDocument);

    //write the size of the current document to the sizes stream
    try {
      sizesStream.writeGamma(tokenPosition + 1);
    } catch(IOException e) {
      throw new IndexException(e);
    }
        
    if(tokenPosition > maxTermPositionInBatch) {
      maxTermPositionInBatch = tokenPosition;
    }
    //increment doc pointer for next doc
    documentPointer++;
    progressLogger.update();
  }
  
  /**
   * Process a single annotation.
   * @param ann the annotation to process
   * @param gateDocument the GATEDocument containing the annotation.
   */
  protected void processAnnotation(Annotation ann,
          GATEDocument gateDocument) throws IndexException {
    // calculate the position and string for this annotation
    calculateStartPositionForAnnotation(ann, gateDocument);
    String[] terms = calculateTermStringForAnnotation(ann, gateDocument);
    if(terms == null){
      //the value was already stored in #currentTerm by the implementation.
      indexCurrentTerm();
    }else if(terms.length == 0){
      //we received an empty array -> we should NOT index the current term
      
    }else{
      //we have received multiple values from the implementation
      for(String aTerm : terms){
        currentTerm.replace(aTerm == null ? "" : aTerm);
        indexCurrentTerm();
      }
    }
  }

  /**
   * Adds the value in {@link #currentTerm} to the index.
   */
  protected void indexCurrentTerm(){
    //check if we have seen this mention before
    PostingsList termPostings = termMap.get(currentTerm);
    if(termPostings == null){
      //new term -> create a new postings list.
      termMap.put( currentTerm.copy(), 
              termPostings = new PostingsList( new byte[ 32 ], true));
    }
    //add the current posting to the current postings list
    termPostings.setDocumentPointer(documentPointer);
    //this is needed so that we don't increment the number of occurrences
    //for duplicate values.
    if(termPostings.checkPosition(tokenPosition)){
      termPostings.addPosition(tokenPosition);
      occurrencesInTheCurrentBatch++;
    }
    else {
      logger.debug("Duplicate position");
    }
    if(termPostings.outOfMemoryError) {
      // we are running out of memory, dump batches ASAP to free it up.
      indexer.getMg4jIndexer().dumpASAP();
    }
  }
  
  /**
   * Dumps a batch to disk.  This method is not responsible for starting
   * the next batch, as the batch being dumped may be the last one.
   * @throws IOException
   * @throws ConfigurationException
   */
  protected void dumpBatch() throws IOException, ConfigurationException{
    try {
      int numTerms = termMap.size();
      logger.info( "Generating index for batch " + currentBatch + 
              "; documents: " + documentPointer + "; terms:" + numTerms + 
              "; occurrences: " + occurrencesInTheCurrentBatch );
      
      // This is not strictly necessary, but nonetheless it frees enough memory 
      // for the subsequent allocation.
      for( ByteArrayPostingList bapl: termMap.values() ) bapl.close();
      
      sizesStream.close();
      
      
      // We write down all term in appearance order in termArray.
      final MutableString[] termArray = termMap.keySet().toArray(new MutableString[ numTerms ]);
      // We sort the terms appearing in the batch and write them on disk.
      Arrays.quickSort(0, termArray.length, 
              new IntComparator() {
                @Override
                public int compare(Integer one, Integer other) {
                  return compare(one.intValue(), other.intValue());
                }
                
                @Override
                public int compare(int one, int other) {
                  return termArray[one].compareTo(termArray[other]);
                }
              },
              new Swapper() {
                @Override
                public void swap(int one, int other) {
                  MutableString temp = termArray[one];
                  termArray[one] = termArray[other];
                  termArray[other] = temp;
                }
              });
      final PrintWriter pw = new PrintWriter( 
              new OutputStreamWriter( new FastBufferedOutputStream(
              new FileOutputStream(getBatchFile(DiskBasedIndex.TERMS_EXTENSION)), 
              DEFAULT_BUFFER_SIZE), "UTF-8" ) );
      for (MutableString t : termArray ) {
        t.println( pw );
      }
      pw.close();      
      
      final OutputBitStream frequenciesStream = 
        new OutputBitStream( getBatchFile(DiskBasedIndex.FREQUENCIES_EXTENSION));
      
      final OutputBitStream globCountsStream = new OutputBitStream(
              getBatchFile(DiskBasedIndex.GLOBCOUNTS_EXTENSION));
  
      final OutputBitStream indexStream = new OutputBitStream(
              getBatchFile(DiskBasedIndex.INDEX_EXTENSION));
      
      final OutputBitStream offsetsStream = new OutputBitStream(
              getBatchFile(DiskBasedIndex.OFFSETS_EXTENSION));
  
      final OutputBitStream posLengthsStream = new OutputBitStream(
              getBatchFile(DiskBasedIndex.POSITIONS_NUMBER_OF_BITS_EXTENSION));

      
      ByteArrayPostingList postingsList;
      int maxCount = 0;
      int frequency;
      long bitLength;
      long postings = 0;
      long prevOffset = 0;
  
      offsetsStream.writeGamma(0);
  
      for ( int i = 0; i < numTerms; i++ ) {
        postingsList = termMap.get( termArray[ i ] );
        frequency = postingsList.frequency;
  
        postingsList.flush();
        if ( maxCount < postingsList.maxCount ) maxCount = postingsList.maxCount;
        bitLength = postingsList.writtenBits();
        postingsList.align();
  
        postings += frequency;
  
        indexStream.writeGamma( frequency - 1 );
  
        // We need special treatment for terms appearing in all documents
        if ( frequency == documentPointer){
          postingsList.stripPointers( indexStream, bitLength );
        } else indexStream.write(postingsList.buffer, bitLength );
  
        frequenciesStream.writeGamma( frequency );
        globCountsStream.writeLongGamma( postingsList.globCount );
        offsetsStream.writeLongGamma( indexStream.writtenBits() - prevOffset );
        posLengthsStream.writeLongGamma( postingsList.posNumBits );
        prevOffset = indexStream.writtenBits();
      }
  
      if(globalMaxCount < maxCount) globalMaxCount = maxCount;
      occurrencesInTotal += occurrencesInTheCurrentBatch; 
        
      postingsInTotal += postings;
  
      //dump the properties file for the current batch
      final Properties properties = new Properties();
      properties.setProperty( Index.PropertyKeys.DOCUMENTS, documentPointer );
      properties.setProperty( Index.PropertyKeys.TERMS, numTerms );
      properties.setProperty( Index.PropertyKeys.POSTINGS, postings );
      properties.setProperty( Index.PropertyKeys.MAXCOUNT, maxCount );
      properties.setProperty( Index.PropertyKeys.INDEXCLASS, FileIndex.class.getName() );
      properties.addProperty( Index.PropertyKeys.CODING, "FREQUENCIES:GAMMA" );
      properties.addProperty( Index.PropertyKeys.CODING, "POINTERS:DELTA" );
      properties.addProperty( Index.PropertyKeys.CODING, "COUNTS:GAMMA" );
      properties.addProperty( Index.PropertyKeys.CODING, "POSITIONS:DELTA" );
      properties.setProperty( Index.PropertyKeys.TERMPROCESSOR, 
              termProcessor == null ? 
              NullTermProcessor.class.getName() :
              termProcessor.getClass().getName());
      properties.setProperty( Index.PropertyKeys.OCCURRENCES, occurrencesInTheCurrentBatch );
      properties.setProperty( Index.PropertyKeys.MAXDOCSIZE, maxTermPositionInBatch + 1);
      properties.setProperty( Index.PropertyKeys.SIZE, indexStream.writtenBits());
      properties.setProperty( Index.PropertyKeys.FIELD, indexBasename());
      properties.save(getBatchFile(DiskBasedIndex.PROPERTIES_EXTENSION));
      indexStream.close();
      offsetsStream.close();
      globCountsStream.close();
      posLengthsStream.close();
      frequenciesStream.close();
      termMap.clear();
      termMap.trim( INITIAL_TERM_MAP_SIZE );
      termMap.growthFactor( Hash.DEFAULT_GROWTH_FACTOR ); // In case we changed it because of an out-of-memory error.

      
      occurrencesInTotal += occurrencesInTheCurrentBatch;
      totalDocuments += documentPointer;
      cutPoints.add( cutPoints.getInt( cutPoints.size() - 1 ) + documentPointer);
  
      maxTermPositionGlobal = Math.max(maxTermPositionGlobal, 
              maxTermPositionInBatch);
      
      logger.info("Index for batch " + currentBatch + " written");
    }catch ( IOException e ) {
      logger.fatal( "I/O Error on batch " + currentBatch );
      throw e;
    }
    // other locals were defined in the try and have already gone
    // out of scope.
    logger.info(Util.percAvailableMemory()
            + "% of memory currently available.  Doing GC.");
    System.gc();
    logger.info("GC done, " + Util.percAvailableMemory()
            + "% of memory now available.");
  }
  
  protected void makeEmpty(File file) throws IOException {
    if ( file.exists() && !file.delete() ){
      throw new IOException( "Cannot delete file " + file );
    }
    file.createNewFile();
  }
  
  
  
  /**
   * Gets the input queue for this indexer. 
   * @return the inputQueue
   */
  public BlockingQueue<GATEDocument> getInputQueue() {
    return inputQueue;
  }

  /**
   * Gets the output queue for this indexer.
   * @return the outputQueue
   */
  public BlockingQueue<GATEDocument> getOutputQueue() {
    return outputQueue;
  }

  /**
   * Is the indexer closed (a call to {@link #close()} was made, and the closing
   * operation has completed)? 
   * @return a boolean value.
   */
  public boolean isClosed() {
    return closed;
  }

  
  /**
   * Returns a value between 0 and 1, representing the amount of work already 
   * performed for the index closing operation. Closing a large index can be 
   * very lengthy operation; this method can be called regularly to obtain an 
   * indication of progress. 
   * @return
   */
  public double getClosingProgress(){
    return closingProgress;
  }
  
  /**
   * Closes this indexer, saves the last batch, and releases all resources.
   */
  public void flush() throws ConfigurationException, IOException {
    if(occurrencesInTheCurrentBatch > 0) {
      dumpBatch();
      currentBatch++;
    }else if(occurrencesInTheCurrentBatch == 0){
      // At some point we started a new batch, but we never 
      // got any data to write in it. We need to close (and delete) the only 
      // open file for the new batch.
      sizesStream.close();
      File sizesFile = getBatchFile(DiskBasedIndex.SIZES_EXTENSION);
      if(sizesFile.exists()){
        sizesFile.delete();
      }

      if(currentBatch == 0){
        //an index with nothing in it!
        // Special case: no term has been indexed. We generate an empty batch.
        logger.info( "Generating empty index " + getBatchFile(""));
        makeEmpty(getBatchFile(DiskBasedIndex.TERMS_EXTENSION));
        makeEmpty(getBatchFile(DiskBasedIndex.FREQUENCIES_EXTENSION));
        makeEmpty(getBatchFile(DiskBasedIndex.GLOBCOUNTS_EXTENSION));
        makeEmpty(getBatchFile(DiskBasedIndex.SIZES_EXTENSION));
        
        final IndexWriter indexWriter = new BitStreamIndexWriter(
                getBatchFile("").getAbsolutePath(), totalDocuments, true, flags );
        indexWriter.close();
        final Properties properties = indexWriter.properties();
        properties.setProperty( Index.PropertyKeys.TERMPROCESSOR, 
                termProcessor == null ? 
                NullTermProcessor.class.getName() :
                termProcessor.getClass().getName());
        properties.setProperty( Index.PropertyKeys.OCCURRENCES, 0 );
        properties.setProperty( Index.PropertyKeys.MAXCOUNT, 0 );
        properties.setProperty( Index.PropertyKeys.MAXDOCSIZE, maxTermPositionGlobal );
        properties.setProperty( Index.PropertyKeys.SIZE, 0 );
        properties.setProperty( Index.PropertyKeys.FIELD, indexBasename());
        properties.save(getBatchFile(DiskBasedIndex.PROPERTIES_EXTENSION));
        //current batch value needs to point to "the next batch to be written"
        currentBatch = 1;
      }else{
        // the currentBatch value points to the batch that was never written, 
        // so it's ${last written batch} + 1
      }
    }
    // at this point, the currentBatch value is guaranteed to be 
    // ${last written batch} + 1 
    
    termMap = null;
  
    final Properties properties = new Properties();
    properties.setProperty( Index.PropertyKeys.FIELD, indexBasename() );
    properties.setProperty( Index.PropertyKeys.BATCHES, currentBatch );
    properties.setProperty( Index.PropertyKeys.DOCUMENTS, totalDocuments );
    properties.setProperty( Index.PropertyKeys.MAXDOCSIZE, maxTermPositionGlobal);
    properties.setProperty( Index.PropertyKeys.MAXCOUNT, globalMaxCount );
    properties.setProperty( Index.PropertyKeys.OCCURRENCES, occurrencesInTotal);
    properties.setProperty( Index.PropertyKeys.POSTINGS, postingsInTotal);
    properties.setProperty( Index.PropertyKeys.TERMPROCESSOR,
           termProcessor == null ? 
           NullTermProcessor.class.getName() :
           termProcessor.getClass().getName());
  
    // This set of batches can be seen as a documental cluster index.
    final Properties clusterProperties = new Properties();
    clusterProperties.addAll( properties );
    clusterProperties.setProperty( Index.PropertyKeys.TERMS, -1 );
    clusterProperties.setProperty( DocumentalCluster.PropertyKeys.BLOOM, false );
    clusterProperties.setProperty( IndexCluster.PropertyKeys.FLAT, false );
  
    clusterProperties.setProperty( Index.PropertyKeys.INDEXCLASS, DocumentalConcatenatedCluster.class.getName() );
    BinIO.storeObject( new ContiguousDocumentalStrategy( cutPoints.toIntArray()),
            getGlobalFile(CLUSTER_STRATEGY_EXTENSION));
  
    clusterProperties.setProperty( IndexCluster.PropertyKeys.STRATEGY,
            getGlobalFile(CLUSTER_STRATEGY_EXTENSION));
    for ( int i = 0; i < currentBatch; i++ ){
      clusterProperties.addProperty(IndexCluster.PropertyKeys.LOCALINDEX, 
             getBatchFile("", i).getAbsolutePath());
    }
    clusterProperties.save( getGlobalFile(CLUSTER_PROPERTIES_EXTENSION));
  
    properties.save( getGlobalFile(DiskBasedIndex.PROPERTIES_EXTENSION));
  }
  
  public void dumpASAP() {
    dumpBatchASAP = true;
  }

  /* (non-Javadoc)
   * @see java.lang.Runnable#run()
   */
  public void run() {
    progressLogger.displayFreeMemory = true;
    progressLogger.start("Indexing documents...");
    GATEDocument aDocument;
    try {
      initIndex();

      while((aDocument = inputQueue.take()) != GATEDocument.END_OF_QUEUE){
        try {
          processDocument(aDocument);
        } catch(Throwable e) {
          logger.error("Problem while indexing document!", e);
        }
        //dump batch if needed
        int percAvailableMemory = Util.percAvailableMemory();
        if(percAvailableMemory < MIN_AVAILABLE_MEMORY) {
          dumpBatchASAP = true;
          indexer.getMg4jIndexer().dumpASAP();
        }
        if (
               // we have been asked to dump 
             ( dumpBatchASAP || 
               //.. OR we reached the maximum document limit for a batch       
               documentPointer == MG4JIndexer.DOCUMENTS_PER_BATCH
             ) &&
             // AND there is data to dump
             occurrencesInTheCurrentBatch > 0
           ){
          dumpBatch();
          //now get ready for the next batch
          currentBatch++;
          initBatch();
        }
        outputQueue.put(aDocument);
      }
      
      // try block only so we can put the END_OF_QUEUE
      // on the output queue in finally
      try {
        //now we need to dump the last current batch
        flush();
        progressLogger.done();        
      }
      finally {
        //notify end of operation
        outputQueue.put(GATEDocument.END_OF_QUEUE);
      }
    } catch(InterruptedException e) {
        Thread.currentThread().interrupt();
    } catch(Exception e) {
      throw new GateRuntimeException("Exception during indexing!", e);
    }
  }

  
  /**
   * Closes this indexer, performing all required operations to finalise the 
   * index (e.g. combining the batches produced during the indexing process).
   * @throws IndexException if there are any problems while closing the index.
   */
  public void close() throws IndexException {
    closingProgress = 0;
    logger.info("Combining batches for index " + indexBasename());
    //finished all docs -> combine the batches
    try {
      final String[] inputBasename = new Properties(
              getGlobalFile(Scan.CLUSTER_PROPERTIES_EXTENSION)).
              getStringArray( IndexCluster.PropertyKeys.LOCALINDEX );
      combineBatches(inputBasename, getGlobalFile("").getAbsolutePath());

      //save the termMap
      BinIO.storeObject( StringMaps.synchronize(
          ImmutableExternalPrefixMap.class.getConstructor(Iterable.class).
          newInstance(new FileLinesCollection(
          getGlobalFile(DiskBasedIndex.TERMS_EXTENSION).getAbsolutePath(), 
          "UTF-8" ))), 
          getGlobalFile(DiskBasedIndex.TERMMAP_EXTENSION));
    } catch(Exception e) {
      throw new IndexException("Exception while closing the index", e);
    }
    logger.info("Indexing completed for index " + indexBasename());
    closingProgress = 1;
    closed = true;
  }
  
  /**
   * Combines a set of batches. If the provided number of input batches is 
   * greater than {@link #MAXIMUM_BATCHES_TO_COMBINE}, then this method will 
   * start hierarchical batch combination: it will combine 
   * {@link #MAXIMUM_BATCHES_TO_COMBINE} at one time, then combining the ouputs
   * of those processes, until a single result index is created. 
   * @param inputBasenames
   * @param ouputBaseName
   * @return
   * @throws NoSuchMethodException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws InstantiationException 
   * @throws ClassNotFoundException 
   * @throws URISyntaxException 
   * @throws IOException 
   * @throws SecurityException 
   * @throws ConfigurationException 
   */
  protected void combineBatches(String[] inputBasenames,  
          String outputBaseName) throws ConfigurationException, 
          SecurityException, IOException, URISyntaxException, 
          ClassNotFoundException, InstantiationException, 
          IllegalAccessException, InvocationTargetException, 
          NoSuchMethodException {
    //calculate how many stages there will be
    int inputCount = inputBasenames.length;
    int totalStages = 0;
    while(inputCount > MAXIMUM_BATCHES_TO_COMBINE){
      totalStages++;
      int remainder = inputBasenames.length % MAXIMUM_BATCHES_TO_COMBINE;
      inputCount = inputCount / MAXIMUM_BATCHES_TO_COMBINE;
      if(inputCount + remainder <= MAXIMUM_BATCHES_TO_COMBINE){
        inputCount+= remainder;
      }else{
        inputCount++;
      }
    }
    totalStages++;
    
    int stage = 0;
    //start the actual hierarchical combine
    while(inputBasenames.length > MAXIMUM_BATCHES_TO_COMBINE){
      int remainder = inputBasenames.length % MAXIMUM_BATCHES_TO_COMBINE;
      int stageSteps = inputBasenames.length / MAXIMUM_BATCHES_TO_COMBINE;
      //each stage step results in one output; there may be enough space left 
      //for the remainder
      if(stageSteps + remainder <= MAXIMUM_BATCHES_TO_COMBINE){
        //the remaining input batches, will just be copied over to the output
      }else{
        //we need an extra stage step
        stageSteps++;
        remainder = 0;
      }
      
      String[] stageOutputFiles = new String[stageSteps + remainder];
      //create and run the sub-batches 
      for(int i = 0; i < stageOutputFiles.length; i++){
        if(i < stageOutputFiles.length - remainder){
          //normal combine step
          int start = i * MAXIMUM_BATCHES_TO_COMBINE;
          int end = Math.min((i + 1) * MAXIMUM_BATCHES_TO_COMBINE, 
                  inputBasenames.length);
          String[] subInputBasenames = new String[end - start];
          System.arraycopy(inputBasenames, start, subInputBasenames, 0, 
                  subInputBasenames.length);
          stageOutputFiles[i] = getBatchFile("-stage" + stage, i).getAbsolutePath();
          combineBatchesNonRec(subInputBasenames, stageOutputFiles[i]);
        }else{
          //we are in the remainder are -> just copy the input to the output
          stageOutputFiles[i] = inputBasenames[
              i - stageOutputFiles.length + inputBasenames.length];
        }
        //update progress value
        closingProgress = 
          //completed stages
          (stage / totalStages) +
          //current fraction
          ((double)(i + 1) / stageOutputFiles.length)
          //of one stage
          / totalStages;
      }
      //prepare for next step
      inputBasenames = stageOutputFiles;
      stage++;
    }
    //at this point, we need to do the last combine
    combineBatchesNonRec(inputBasenames, outputBaseName);
  }  
  
  /**
   * Combines a set of maximum {@link #MAXIMUM_BATCHES_TO_COMBINE} batches. 
   * If the provided number of input batches is greater than that, then this 
   * method will fire a runtime exception. To avoid this, client code should 
   * never call this method directly, but use 
   * {@link #combineBatches(String[], String)} instead! 
   * @param inputBasenames
   * @param ouputBaseName
   * @return
   * @throws NoSuchMethodException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws InstantiationException 
   * @throws ClassNotFoundException 
   * @throws URISyntaxException 
   * @throws IOException 
   * @throws SecurityException 
   * @throws ConfigurationException 
   */
  protected void combineBatchesNonRec(String[] inputBasenames,  
          String outputBaseName) throws ConfigurationException, 
          SecurityException, IOException, URISyntaxException, 
          ClassNotFoundException, InstantiationException, 
          IllegalAccessException, InvocationTargetException, 
          NoSuchMethodException{
    if(inputBasenames.length <= MAXIMUM_BATCHES_TO_COMBINE){
      //simple combine
      new Concatenate(outputBaseName,
              inputBasenames, false, 
              Combine.DEFAULT_BUFFER_SIZE, 
              CompressionFlags.DEFAULT_STANDARD_INDEX,
              false, true, 
//              BitStreamIndex.DEFAULT_QUANTUM,
              // replaced with optimised automatic calculation
              -5, 
              BitStreamIndex.DEFAULT_HEIGHT, 
              SkipBitStreamIndexWriter.DEFAULT_TEMP_BUFFER_SIZE, 
              ProgressLogger.DEFAULT_LOG_INTERVAL).run();
      //clean up the used batch files
      for(String batchBasename : inputBasenames){
        cleanBatchFiles(batchBasename);
      }
    }else{
      throw new RuntimeException(
          "Internal mafunction: too many batches to combine (" +
          inputBasenames.length + " > " + MAXIMUM_BATCHES_TO_COMBINE + ")!");
    }
  }
  
  /**
   * Removes all files associated with a batch.
   * @param basename
   */
  protected void cleanBatchFiles(String basename){
    new File( basename + DiskBasedIndex.FREQUENCIES_EXTENSION ).delete();
    new File( basename + DiskBasedIndex.GLOBCOUNTS_EXTENSION ).delete();
    new File( basename + DiskBasedIndex.POSITIONS_NUMBER_OF_BITS_EXTENSION).delete();
    new File( basename + DiskBasedIndex.INDEX_EXTENSION ).delete();
    new File( basename + DiskBasedIndex.OFFSETS_EXTENSION ).delete();
    new File( basename + DiskBasedIndex.SIZES_EXTENSION ).delete();
    new File( basename + DiskBasedIndex.STATS_EXTENSION ).delete();
    new File( basename + DiskBasedIndex.PROPERTIES_EXTENSION ).delete();
    new File( basename + DiskBasedIndex.POSITIONS_EXTENSION ).delete();
    new File( basename + DiskBasedIndex.TERMS_EXTENSION ).delete();
    new File( basename + DiskBasedIndex.UNSORTED_TERMS_EXTENSION ).delete();
  }

}
