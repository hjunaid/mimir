/*
 *  MimirDirectIndexBuilder.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 11 Jul 2012
 *
 *  $Id$
 */
package gate.mimir.index.mg4j;

import gate.Annotation;
import gate.mimir.IndexConfig;
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.mimir.util.MG4JTools;
import gate.util.GateRuntimeException;
import it.unimi.dsi.Util;
import it.unimi.dsi.big.mg4j.index.Index;
import it.unimi.dsi.big.mg4j.index.IndexIterator;
import it.unimi.dsi.big.mg4j.index.IndexReader;
import it.unimi.dsi.big.mg4j.tool.Scan.Completeness;
import it.unimi.dsi.logging.ProgressLogger;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;

import org.apache.log4j.Logger;

/**
 * Class use to transpose an inverted index by building an MG4J index where 
 * terms and documents are used as reverse images of each each other. 
 */
public class MimirDirectIndexBuilder extends MimirIndexBuilder {

  private static Logger logger = Logger.getLogger(MimirDirectIndexBuilder.class);
  
  /**
   * The progress of the index building operation. 
   */
  private volatile double buildProgress = 0;
  
  protected String inputSubindexBasename;
  
  public static final String BASENAME_SUFFIX = "-dir";
  
  /**
   * @param indexDirectory the top level directory for the Mímir index being
   * modified.
   * @param subIndexName the name for the subindex being modified (e.g. 
   * &quot;token-0&quot;, or &quot;mentions-0&quot;).
   * @throws IndexException 
   * @throws IOException 
   */
  public MimirDirectIndexBuilder(File indexDirectory, String subIndexName) 
      throws IOException, IndexException {
    super();
    this.indexConfig = IndexConfig.readConfigFromFile(
      new File(indexDirectory, Indexer.INDEX_CONFIG_FILENAME), indexDirectory);
    this.inputSubindexBasename = subIndexName;
    this.indexBaseName = subIndexName + BASENAME_SUFFIX;
    // create the progress logger.  We use this.getClass to use the
    // logger belonging to a subclass rather than our own.
    progressLogger = new ProgressLogger(
            Logger.getLogger(this.getClass()), "input terms");
    savePositions = false;
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.mg4j.MimirIndexBuilder#getAnnotsToProcess(gate.mimir.index.mg4j.GATEDocument)
   */
  @Override
  protected Annotation[] getAnnotsToProcess(GATEDocument gateDocument)
    throws IndexException {
    throw new UnsupportedOperationException("Not implemented.");
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.mg4j.MimirIndexBuilder#calculateStartPositionForAnnotation(gate.Annotation, gate.mimir.index.mg4j.GATEDocument)
   */
  @Override
  protected void calculateStartPositionForAnnotation(Annotation ann,
                                                     GATEDocument gateDocument)
    throws IndexException {
    throw new UnsupportedOperationException("Not implemented.");
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.mg4j.MimirIndexBuilder#calculateTermStringForAnnotation(gate.Annotation, gate.mimir.index.mg4j.GATEDocument)
   */
  @Override
  protected String[] calculateTermStringForAnnotation(Annotation ann,
      GATEDocument gateDocument) throws IndexException {
    throw new UnsupportedOperationException("Not implemented.");
  }



  /* (non-Javadoc)
   * @see gate.mimir.index.mg4j.MimirIndexBuilder#run()
   */
  @Override
  public void run() {
    buildProgress = 0;
    double lastProgress = buildProgress;
    // input documentIDs become output termIDs
    // input termIDs become output documentIDs
    // NB: the variables in this method are named based on output semantics! 
    progressLogger.start("Inverting index...");
    try {
      // open the input index for reading
      Index inputIndex = MG4JTools.openMg4jIndex(
        new File(new File(indexConfig.getIndexDirectory(), 
          Indexer.MG4J_INDEX_DIRNAME), 
          Indexer.MG4J_INDEX_BASENAME + "-" + inputSubindexBasename).toURI());
      IndexReader inputIndexReader = inputIndex.getReader();
      // open the output index for writing
      initIndex();
      // we are iterating over the input terms (output 'documents')
      NumberFormat percentNF = NumberFormat.getPercentInstance();
      IndexIterator inputTermIterator = inputIndexReader.nextIterator();
      long termsProcessed = 0;
      while(inputTermIterator != null) {
        try { // Start: process output document
          // the current input term ID is an output document ID.
          // BUG? This value seems to be wrong on large indexes. Maybe we should
          // just count them instead?
          long outputDocId = inputTermIterator.termNumber();
          if(outputDocId != termsProcessed) {
            logger.warn("Mismatch between term ID from index " + outputDocId + 
                ", counted ID: " + termsProcessed + ".");
          }
          //zero document related counters
          tokenPosition = -1;
          // for each input term, we iterate over its documents
          // the current input document ID is an output term ID
          long outputTermId = inputTermIterator.nextDocument();
          while(outputTermId != IndexIterator.END_OF_LIST && outputTermId != -1) {
            tokenPosition ++;
            currentTerm.replace(longToTerm(outputTermId));
            // how many times the current term occurs in the current document 
            int count = inputTermIterator.count();
            //check if we have seen this mention before
            PostingsList termPostings = termMap.get(currentTerm);
            if(termPostings == null){
              //new term -> create a new postings list.
              termMap.put(currentTerm.copy(), termPostings = new PostingsList(
                  new byte[ 32 ], true, Completeness.COUNTS));
            }
            termPostings.setDocumentPointer(outputDocId);
            termPostings.setCount(count);
            occurrencesInTheCurrentBatch += count;
            if(termPostings.outOfMemoryError) {
              // we are running out of memory, dump batches ASAP to free it up.
              indexer.getMg4jIndexer().dumpASAP();
            }
            // and move to the next output term (input document)
            outputTermId = inputTermIterator.nextDocument();
          }          
        } finally {
          //write the size of the current document to the sizes stream
          try {
            sizesStream.writeGamma(tokenPosition + 1);
          } catch(IOException e) {
            throw new IndexException(e);
          } finally {
            if(tokenPosition > maxTermPositionInBatch) {
              maxTermPositionInBatch = tokenPosition;
            }
            //increment doc pointer for next doc
            documentPointer++;
            progressLogger.update();
          }
        } // End: process output document
        
        //dump batch if needed
        int percAvailableMemory = Util.percAvailableMemory();
        if(percAvailableMemory < MIN_AVAILABLE_MEMORY) {
          dumpBatchASAP = true;
          indexer.getMg4jIndexer().dumpASAP();
        }
        if ( // we have been asked to dump 
             ( dumpBatchASAP || 
               //.. OR we reached the maximum occurrences for a batch       
               // We're not storing positions, so the amount of data in the 
               // index is smaller. We increase the number of occurrences / batch
               // by a factor of 3.
               occurrencesInTheCurrentBatch > (MG4JIndexer.OCCURRENCES_PER_BATCH * 3)
               ) &&
             // AND there is data to dump
             occurrencesInTheCurrentBatch > 0 ){
          dumpBatch();
          //now get ready for the next batch
          currentBatch++;
          initBatch();
        }
        
        // and move to the next input term (output 'document')
        inputTermIterator = inputIndexReader.nextIterator();
        termsProcessed++;
        buildProgress = (double)termsProcessed / inputIndex.numberOfTerms;
        if(buildProgress - lastProgress >= 1) {
          logger.debug("Direct index  " +  percentNF.format(buildProgress) + 
              " built.");
          lastProgress = buildProgress;
        }
      }
      inputIndexReader.close();
      // dump the last current batch
      flush();
      buildProgress = 1;
      // close the index (combine the batches)
      close();
      progressLogger.done();
    } catch(Exception e) {
      throw new GateRuntimeException("Exception during indexing!", e);
    }
  }
  
  /**
   * Returns a value between 0 and 1, representing the amount of work already 
   * performed for the index building operation. Building a large index can be 
   * very lengthy operation; this method can be called regularly to obtain an 
   * indication of progress. 
   * @return a double value
   */
  public double getProgress() {
    return (closingProgress + buildProgress) / 2;
  }
  
  /**
   * Converts a long value into a String containing a zero-padded Hex 
   * representation of the input value. The lexicographic ordering of the 
   * generated strings is the same as the natural order of the corresponding
   * long values.
   *  
   * @param value the value to convert.
   * @return the string representation.
   */
  public static final String longToTerm(long value) {
    String valueStr = Long.toHexString(value);
    return "0000000000000000".substring(valueStr.length()) + valueStr;
  }
  
}
