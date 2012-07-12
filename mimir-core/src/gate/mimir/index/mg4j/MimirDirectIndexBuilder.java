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

import static it.unimi.dsi.big.mg4j.tool.Scan.Completeness.COUNTS;
import static it.unimi.dsi.big.mg4j.tool.Scan.Completeness.POSITIONS;
import static it.unimi.dsi.io.OutputBitStream.GAMMA;
import static it.unimi.dsi.io.OutputBitStream.MAX_PRECOMPUTED;
import gate.Annotation;
import gate.mimir.IndexConfig;
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.mimir.index.mg4j.MimirIndexBuilder.PostingsList;
import gate.mimir.util.MG4JTools;
import gate.util.GateRuntimeException;

import it.unimi.dsi.Util;
import it.unimi.dsi.big.mg4j.index.Index;
import it.unimi.dsi.big.mg4j.index.IndexIterator;
import it.unimi.dsi.big.mg4j.index.IndexReader;
import it.unimi.dsi.big.mg4j.io.ByteArrayPostingList;
import it.unimi.dsi.big.mg4j.tool.Scan.Completeness;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.logging.ProgressLogger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.text.NumberFormat;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;

/**
 * Class use to transpose an inverted index by building an MG4J index where 
 * terms and documents are used as reverse images of each each other. 
 */
public class MimirDirectIndexBuilder extends MimirIndexBuilder {

  private static Logger logger = Logger.getLogger(MimirDirectIndexBuilder.class);
  
  protected String inputSubindexBasename;
  
  protected static final String BASENAME_SUFFIX = "-dir";
  
  /**
   * @param indexDirectory the top level directory for the Mímir index being
   * modified.
   * @param subIndexName the name for the subindex being modified (e.g. 
   * &quot;mimir-token-0&quot;).
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
    this.progressLogger = new ProgressLogger(
            Logger.getLogger(this.getClass()), "documents");
    closed = false;
    closingProgress = 0;
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
    // input documentIDs become output termIDs
    // input termIDs become output documentIDs
    // NB: the variables in this method are named based on output semantics! 
    
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
          long outputDocId = inputTermIterator.termNumber();
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
            occurrencesInTheCurrentBatch++;
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
               //.. OR we reached the maximum document limit for a batch       
               documentPointer == MG4JIndexer.DOCUMENTS_PER_BATCH ) &&
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
        if(termsProcessed % 1000 == 0) {
          logger.debug("Processed " + 
              percentNF.format((double)termsProcessed / inputIndex.numberOfTerms) + 
              " terms");  
        }
        
      }
      
      // dump the last current batch
      flush();
      // close the index (combine the batches)
      close();
      progressLogger.done();              
    } catch(Exception e) {
      throw new GateRuntimeException("Exception during indexing!", e);
    }
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
