/*
 *  MG4JIndexer.java
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
 *  Valentin Tablan, 19 Feb 2009
 *
 *  $Id$
 */
package gate.mimir.index.mg4j;

import gate.mimir.IndexConfig;
import gate.mimir.index.*;
import gate.util.GateRuntimeException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;


/**
 * This class provides some helper methods for managing the indexing activity 
 * with MG4J. 
 */
public class MG4JIndexer {
  

  /**
   * The indexers used for each individual sub-index.
   */
  private List<MimirIndexBuilder> subIndexers;

  /**
   * Flag showing whether the indexer is closed. 
   */
  private boolean closed = false;
  
  /**
   * A {@link Runnable} used to push documents from the main input queue to 
   * the input queues of the sub-indexers.
   */
  protected class DocumentPusher implements Runnable{
    public void run(){
      boolean finished = false;
      while(!finished){
        try {
          GATEDocument aDoc = inputQueue.take();
          //push the new document to each of the subIndexers.
          for(MimirIndexBuilder aSubIndexer: subIndexers){
            try {
              aSubIndexer.getInputQueue().put(aDoc);
            } catch(InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
          finished = aDoc == GATEDocument.END_OF_QUEUE;
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
  
  /**
   * A {@link Runnable} that collects the documents from the sub-indexers and
   * adds them to the output queue.
   */
  protected class DocumentConsumer implements Runnable{
    public void run(){
      boolean finished = false;
      while(!finished){
        GATEDocument currentDocument = null;
        try {
          //get one document from each of the sub-indexers
          //check identity and add to output queue.
          for(MimirIndexBuilder aSubIndexer : subIndexers){
            GATEDocument aDoc = aSubIndexer.getOutputQueue().take();
            if(currentDocument == null){
              currentDocument = aDoc;
            }else if(aDoc != currentDocument){
              //malfunction!
              throw new RuntimeException(
                      "Out of order document received from sub-indexer!");
            }
          }
          //we obtained the same document from all the sub-indexers
          if(currentDocument == GATEDocument.END_OF_QUEUE){
            finished = true;
            //run the index closing activity for all sub-indexers (including
            //combining the produced batches) from this single thread,
            //in order to avoid opening too many file at the same time.
            for(MimirIndexBuilder aSubIndexer : subIndexers){
              try {
                aSubIndexer.close();
              } catch(IndexException e) {
                throw new GateRuntimeException(e);
              }
            }
            closed = true;
          }
          outputQueue.put(currentDocument);
        } catch(InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
  
  /**
   * Initialises the MG4J indexing engine.
   * @param config the index configuration
   * @param outputQueue a blocking queue where the documents are saved once they
   * have been indexed. This can be used for further processing of the documents.
   * This class will NOT delete the GATE documents, so it is the responsibility 
   * of the client code to release the memory by calling 
   * gate.Factory.deleteResource().
   */
  public MG4JIndexer(Indexer indexer, 
          BlockingQueue<GATEDocument> outputQueue){
    this.indexer = indexer;
    this.indexConfig = indexer.getIndexConfig();
    this.outputQueue = outputQueue;
    this.subIndexers = new ArrayList<MimirIndexBuilder>();
    closed = false;
  }
  
  /**
   * Initialises the MG4JConnector, based on the index config provided to the 
   * constructor, and all other options set after construction.
   */
  public void init(){
    gateDocFactory = new GATEDocumentFactory(indexConfig);
    inputQueue =  new LinkedBlockingQueue<GATEDocument>(documentQueueSize);
    //start the sub-indexers for the token features
    for(int i = 0; i < indexConfig.getTokenIndexers().length; i++){
      BlockingQueue<GATEDocument> anInputQueue = 
        new LinkedBlockingQueue<GATEDocument>(bufferQueueSize);
      BlockingQueue<GATEDocument> anOutputQueue = 
        new LinkedBlockingQueue<GATEDocument>(bufferQueueSize);
      //create a sub-indexer
      TokenIndexBuilder aSubIndexer = new TokenIndexBuilder(
              anInputQueue, anOutputQueue, indexer, gateDocFactory, 
              i == 0, TokenIndexBuilder.TOKEN_INDEX_BASENAME + "-" +i, 
              indexConfig.getTokenIndexers()[i]);
      subIndexers.add(aSubIndexer);
      Thread subIndexThread = new Thread(aSubIndexer, getClass().getName() + 
              " " + aSubIndexer.indexBasename() + "-indexer");
      subIndexThread.start();
    }
    //now start the sub-indexers for semantic annotations
    for(int i = 0; i < indexConfig.getSemanticIndexers().length; i++){
      BlockingQueue<GATEDocument> anInputQueue = 
        new LinkedBlockingQueue<GATEDocument>(bufferQueueSize);
      BlockingQueue<GATEDocument> anOutputQueue = 
        new LinkedBlockingQueue<GATEDocument>(bufferQueueSize);
      //create a sub-indexer
      MentionsIndexBuilder aSubIndexer = new MentionsIndexBuilder(
              anInputQueue, anOutputQueue, indexer, 
              MentionsIndexBuilder.MENTIONS_INDEX_BASENAME + "-" + i, 
              indexConfig.getSemanticIndexers()[i]);
      subIndexers.add(aSubIndexer);
      //...and start a new thread
      Thread subIndexThread = new Thread(aSubIndexer, getClass().getName() + 
              " " + aSubIndexer.indexBasename() + "-indexer");
      subIndexThread.start();
    }
    
    //create and start a document consumer
    Thread consumerThread = new Thread(new DocumentConsumer(), 
            getClass().getName() + "-document consumer");
    consumerThread.start();
    //finally, create and start a document pusher
    Thread pusherThread = new Thread(new DocumentPusher(), 
            getClass().getName() + "-document pusher");
    pusherThread.start();
  }
  
  /**
   * Tell all the sub-indexers to dump a batch as soon as they can.
   */
  public void dumpASAP() {
    for(MimirIndexBuilder builder : subIndexers) {
      builder.dumpASAP();
    }
  }
  
  /**
   * Queues a new document for indexing.
   * @param document the GATE document to be indexed.
   */
  public CharSequence indexDocument(gate.Document document){
    try {
    	GATEDocument doc = new GATEDocument(document, indexConfig);
    	inputQueue.put(doc);
    	return doc.uri();
      
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return "";
  }
  
  
  /**
   * Notifies this indexer that there are no more documents to index. 
   */
  public void close(){
    try {
      inputQueue.put(GATEDocument.END_OF_QUEUE);
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  
  /**
   * Returns a value between 0 and 1, representing the amount of work already 
   * performed for the index closing operation. Closing a large index can be 
   * very lengthy operation; this method can be called regularly to obtain an 
   * indication of progress. 
   * @return a double value
   */
  public double getClosingProgress(){
    if(closed) return 1;
    else{
      //calculate the closing progress
      double closingProgress = 0;
      for(MimirIndexBuilder aSubIndexer : subIndexers){
        closingProgress +=  aSubIndexer.getClosingProgress()/subIndexers.size(); 
      }
      return closingProgress;
    }
  }
  
  /**
   * Gets the maximum size of the buffer queues used during the indexing 
   * process.
   * @return the documentQueueSize
   */
  public int getDocumentQueueSize() {
    return documentQueueSize;
  }

  /**
   * Sets the maximum size of the input queue used to pass documents to the
   * indexing process. This essentially limits the number of GATE documents
   * stored in RAM at the same time. 
   * @param documentQueueSize the documentQueueSize to set
   */
  public void setDocumentQueueSize(int documentQueueSize) {
    this.documentQueueSize = documentQueueSize;
  }

  /**
   * Sets the maximum size of the buffer queues used to pass documents from
   * one sub-indexer to the next. This essentially limits the number of GATE
   * documents stored in RAM at the same time. 
   * @param bufferQueueSize the size of the buffer queues
   */
  public void setBufferQueueSize(int bufferQueueSize) {
    this.bufferQueueSize = bufferQueueSize;
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
   * The default length for the {@link #inputQueue}. 
   */
  private static final int DEFAULT_DOCUMENT_QUEUE_SIZE = 30;
  
  /**
   * The default length for the buffer queues between sub-indexers.
   */
  private static final int DEFAULT_BUFFER_QUEUE_SIZE = 30;

  /**
   * How many documents should be indexed in memory before dumping a batch to 
   * disk.
   */
  public static final int DOCUMENTS_PER_BATCH = 4000;

  /**
   * The size of the {@link #inputQueue}. 
   */
  private int documentQueueSize = DEFAULT_DOCUMENT_QUEUE_SIZE;
  
  /**
   * The size of the buffer queues between the sub-indexers.
   */
  private int bufferQueueSize = DEFAULT_BUFFER_QUEUE_SIZE;
  
  /**
   * The current index configuration.
   */
  private IndexConfig indexConfig;
  
  /**
   * The top level indexer to which this {@link MG4JIndexer} belongs.
   */
  private Indexer indexer;
  
  /**
   * The shared document factory, configured according to the current indexing
   * spec.
   */
  private GATEDocumentFactory gateDocFactory;
  
  /**
   * A queue for storing GATE document between the moment of the indexing 
   * request and the actual indexing. This is used to expose a document iterator
   * behaviour to MG4J, based on the indexing requests from the client code.  
   */
  private BlockingQueue<GATEDocument> inputQueue;
  
  /**
   * A queue that stores the documents for which the indexed has finished.  
   */
  private BlockingQueue<GATEDocument> outputQueue;
  
  private static Logger logger = Logger.getLogger(MG4JIndexer.class);

}
