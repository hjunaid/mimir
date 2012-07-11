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
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.util.GateRuntimeException;

import it.unimi.dsi.Util;
import it.unimi.dsi.big.mg4j.index.IndexIterator;
import it.unimi.dsi.big.mg4j.index.IndexReader;
import it.unimi.dsi.big.mg4j.io.ByteArrayPostingList;
import it.unimi.dsi.big.mg4j.tool.Scan.Completeness;
import it.unimi.dsi.bits.Fast;

import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;

/**
 * Class use to transpose an inverted index by building an MG4J index where 
 * terms and documents are used as reverse images of each each other. 
 */
public class MimirDirectIndexBuilder extends MimirIndexBuilder {
  static Field countField;
  static {
    try {
      countField = ByteArrayPostingList.class.getDeclaredField("count");
      countField.setAccessible(true);
    } catch(Exception e) {
      throw new AssertionError("Could not acces the " + 
         ByteArrayPostingList.class.getName() + 
         ".count field via reflection.");
    }
  }
  
  /**
   * A reader for the (inverted) index being transposed.
   */
  private IndexReader inputIndexReader;
  
  /**
   * @param inputQueue
   * @param outputQueue
   * @param indexer
   * @param baseName
   */
  public MimirDirectIndexBuilder(BlockingQueue<GATEDocument> inputQueue,
                                 BlockingQueue<GATEDocument> outputQueue,
                                 Indexer indexer, String baseName) {
    super(inputQueue, outputQueue, indexer, baseName);
    // TODO Auto-generated constructor stub
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.mg4j.MimirIndexBuilder#getAnnotsToProcess(gate.mimir.index.mg4j.GATEDocument)
   */
  @Override
  protected Annotation[] getAnnotsToProcess(GATEDocument gateDocument)
    throws IndexException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.mg4j.MimirIndexBuilder#calculateStartPositionForAnnotation(gate.Annotation, gate.mimir.index.mg4j.GATEDocument)
   */
  @Override
  protected void calculateStartPositionForAnnotation(Annotation ann,
                                                     GATEDocument gateDocument)
    throws IndexException {
    // TODO Auto-generated method stub
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.mg4j.MimirIndexBuilder#calculateTermStringForAnnotation(gate.Annotation, gate.mimir.index.mg4j.GATEDocument)
   */
  @Override
  protected String[] calculateTermStringForAnnotation(Annotation ann,
                                                      GATEDocument gateDocument)
    throws IndexException {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub
  }

  /* (non-Javadoc)
   * @see gate.mimir.index.mg4j.MimirIndexBuilder#run()
   */
  @Override
  public void run() {
    try {
      initIndex();
      IndexIterator termIterator = inputIndexReader.nextIterator();
      while(termIterator != null) {
        // the current term
        long termId = termIterator.termNumber();
        // the current document
        long docId = termIterator.nextDocument();
        while(docId != IndexIterator.END_OF_LIST) {
          // how many times the current term occurs in the current document 
          int count = termIterator.count();
          // index the data
          ByteArrayPostingList postingsList = null; // TODO get from local in-RAM cache
          if(postingsList == null) {
            postingsList = new ByteArrayPostingList(null, true, Completeness.COUNTS);
            //TODO: add to local cache
          }
          postingsList.setDocumentPointer(termId);
          // this is horrible, but can't be avoided due to the BAPL class
          // keeping lots of things very private.
          countField.setInt(postingsList, count);
          // TODO
        }
        
        termIterator = inputIndexReader.nextIterator();
      }

//      while(true){
//        try {
//          processDocument(aDocument);
//        } catch(Throwable e) {
//          logger.error("Problem while indexing document!", e);
//        }
//        //dump batch if needed
//        int percAvailableMemory = Util.percAvailableMemory();
//        if(percAvailableMemory < MIN_AVAILABLE_MEMORY) {
//          dumpBatchASAP = true;
//          indexer.getMg4jIndexer().dumpASAP();
//        }
//        if (
//               // we have been asked to dump 
//             ( dumpBatchASAP || 
//               //.. OR we reached the maximum document limit for a batch       
//               documentPointer == MG4JIndexer.DOCUMENTS_PER_BATCH
//             ) &&
//             // AND there is data to dump
//             occurrencesInTheCurrentBatch > 0
//           ){
//          dumpBatch();
//          //now get ready for the next batch
//          currentBatch++;
//          initBatch();
//        }
//        outputQueue.put(aDocument);
//      }
      
      // dump the last current batch
      flush();
      // close the index (combine the batches)
      close();
      progressLogger.done();              
    } catch(Exception e) {
      throw new GateRuntimeException("Exception during indexing!", e);
    }
  }
  
  
}
