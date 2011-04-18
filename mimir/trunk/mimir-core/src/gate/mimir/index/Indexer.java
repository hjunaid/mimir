/*
 *  Indexer.java
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
 * Valentin Tablan, 18 Feb 2009
 *
 *  $Id$
 */
package gate.mimir.index;

import gate.Document;
import gate.Gate;
import gate.mimir.IndexConfig;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.index.mg4j.GATEDocument;
import gate.mimir.index.mg4j.MG4JIndexer;
import gate.util.GateRuntimeException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;


/**
 * An Indexer for GATE documents. This object can be used to construct a
 * file-based index, capable of indexing GATE documents, with support for
 * indexing multiple annotation features. Semantic indexing is also supported.
 */
public class Indexer {
  /**
   * Gets the documents after they were indexed, and deletes them from GATE
   */
  private static class DocumentsDestroyer implements Runnable {
    public DocumentsDestroyer(BlockingQueue<GATEDocument> finshedDocs) {
      this.finishedDocs = finshedDocs;
    }

    private BlockingQueue<GATEDocument> finishedDocs;

    public void run() {
      List<GATEDocument> indexedDocuments = new ArrayList<GATEDocument>();
      bigwhile: while(true) {
        // clear the list for re-use
        indexedDocuments.clear();
        int docCount = finishedDocs.drainTo(indexedDocuments);
        if(docCount > 0) {
          for(GATEDocument aGateDocument : indexedDocuments) {
            if(aGateDocument != GATEDocument.END_OF_QUEUE) {
              logger.debug("Deleting document "
                      + aGateDocument.getDocument().getName());
              gate.Factory.deleteResource(aGateDocument.getDocument());
              logger.debug("Document deleted.  "
                      + Gate.getCreoleRegister().getLrInstances(
                              aGateDocument.getDocument().getClass().getName())
                              .size() + " documents still live.");
            } else {
              // no more docs
              break bigwhile;
            }
          }
        } else {
          // we got no documents last time, let's sleep for a while
          try {
            Thread.sleep(100);
          } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
  }

  /**
   * Creates a new GATE Index, using the provided configuration.
   * 
   * @param config
   *          the indexer configuration.
   */
  public Indexer(IndexConfig config) throws IndexException {
    this.config = config;
    indexDir = config.getIndexDirectory();
    if(indexDir.exists()) { throw new IndexException(
            "Was asked to create a new index, but index directory (" + indexDir
            + ") already exists! Please delete it first."); }
    // initialise the MG4J indexer
    initMG4J();
    // initialise the semantic indexers
    if(config.getSemanticIndexers() != null && 
       config.getSemanticIndexers().length > 0) {
      for(SemanticIndexerConfig sic : config.getSemanticIndexers()){
        for(SemanticAnnotationHelper sah : sic.getHelpers()){
          sah.init(this);
        }
      }
    }
    // save config to file.
    try {
      IndexConfig.writeConfigToFile(config, new File(indexDir,
              INDEX_CONFIG_FILENAME));
    } catch(IOException e) {
      throw new GateRuntimeException("Could not save the index configuration!",
              e);
    }
    closed = false;
    annHelpersClosingProgress = 0;
  }

  protected void initMG4J() {
    // make sure the index directory exists
    mg4jIndexDir =
      new File(config.getIndexDirectory(), Indexer.MG4J_INDEX_DIRNAME);
    mg4jIndexDir.mkdirs();
    mg4jOutputQueue = new LinkedBlockingQueue<GATEDocument>();
    mg4jIndexer = new MG4JIndexer(this, mg4jOutputQueue);
    mg4jIndexer.init();
    documentDestroyerThread =
      new Thread(new DocumentsDestroyer(mg4jOutputQueue), getClass()
              .getName()
              + " document destroyer");
    documentDestroyerThread.start();
  }

  /**
   * Opens a pre-existing GATE index.
   * 
   * @param indexDirectory
   */
  public Indexer(File indexDirectory) {
    throw new UnsupportedOperationException(
    "Updateable indexes not implemented!");
  }

  /**
   * Queues a new document for indexing. Once indexed, the document will be
   * destroyed by calling gate.Factory.deleteResource().
   * 
   * @param document
   *          the GATE document to be indexed.
   * @return the URI for the newly indexed document.
   */
  public CharSequence indexDocument(Document document) {
    // add to mg4j
    return mg4jIndexer.indexDocument(document);
  }

  /**
   * Waits for the document queue to finish indexing and closes all open
   * resources. This method will return when the indexing has completed.
   */
  public void close() {
    logger.info("Shutting down MG4J");
    mg4jIndexer.close();
    try {
      documentDestroyerThread.join();
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // close all the semantic indexers, if any were opened
    annHelpersClosingProgress = 0;
    if(config.getSemanticIndexers() != null
            && config.getSemanticIndexers().length > 0) {
      for(SemanticIndexerConfig aSIC : config.getSemanticIndexers()) {
        if(aSIC.getHelpers() != null) {
          for(int i = 0; i < aSIC.getHelpers().length; i++) {
            SemanticAnnotationHelper aHelper = aSIC.getHelpers()[i];
            aHelper.close(this);
          }
        }
        annHelpersClosingProgress += 1.0 / config.getSemanticIndexers().length;
      }
    }
    // finally, save the config to file again (in case some helpers need to
    // store
    // data)
    try {
      IndexConfig.writeConfigToFile(config, new File(indexDir,
              INDEX_CONFIG_FILENAME));
    } catch(IOException e) {
      throw new GateRuntimeException("Could not save the index configuration!",
              e);
    }
    logger.info("Shutdown complete");
    annHelpersClosingProgress = 1;
    closed = true;
  }

  /**
   * Returns a value between 0 and 1, representing the amount of work already
   * performed for the index closing operation. Closing a large index can be
   * very lengthy operation; this method can be called regularly to obtain an
   * indication of progress.
   * 
   * @return
   */
  public double getClosingProgress() {
    if(closed) return 1;
    double mg4jProgress =
      mg4jIndexer.isClosed() ? 1 : mg4jIndexer.getClosingProgress();
    // we assume that closing the ORDIes takes about 10% of the time
    return mg4jProgress * 0.9 + annHelpersClosingProgress * 0.1;
  }

  
  /**
   * @return the config
   */
  public IndexConfig getIndexConfig() {
    return config;
  }
  
  /**
   * @return the mg4jIndexer
   */
  public MG4JIndexer getMg4jIndexer() {
    return mg4jIndexer;
  }

  /**
   * Gets the top level directory for the MG4J index.
   * 
   * @return the mg4jIndexDir
   */
  public File getMg4jIndexDir() {
    return mg4jIndexDir;
  }

  /**
   * Gets the top level index dir.
   * 
   * @return the indexDir
   */
  public File getIndexDir() {
    return indexDir;
  }

  /**
   * Is the indexer closed (a call to {@link #close()} was made, and the closing
   * operation has completed)?
   * 
   * @return a boolean value.
   */
  public boolean isClosed() {
    return closed;
  }

  /**
   * Combines two indexes by importing all the index data from another index
   * into the current index.
   * 
   * @param otherIndexer
   *          the other index from which the data should be imported.
   */
  public void combineIndex(Indexer otherIndexer) {
    throw new UnsupportedOperationException("Index merging not implemented!");
  }

  /**
   * The thread used to destroy documents at the end of the indexing process.
   * This is used to wait for the indexing to finish.
   */
  private Thread documentDestroyerThread;

  /**
   * The configuration for this indexer.
   */
  private IndexConfig config;
  
  /**
   * The MG4J indexer used to index documents.
   */
  private MG4JIndexer mg4jIndexer;

  /**
   * The top level directory for the MG4J index.
   */
  private File mg4jIndexDir;

  /**
   * The top level directory of this index.
   */
  private File indexDir;

  /**
   * Flag showing whether the indexer is closed.
   */
  private boolean closed = false;

  /**
   * A value between 0 and 1, that indicates the progress of closing all the
   * semantic annotation helper instances.
   */
  private volatile double annHelpersClosingProgress = 0;

  private BlockingQueue<GATEDocument> mg4jOutputQueue;

  /**
   * The name of the index subdirectory storing MG4J indexes.
   */
  public static final String MG4J_INDEX_DIRNAME = "mg4j";

  /**
   * The basename used for all MG4J indexes.
   */
  public static final String MG4J_INDEX_BASENAME = "mimir";

  /**
   * The filename for the zip collection.
   */
  public static final String MIMIR_COLLECTION_BASENAME =
    MG4J_INDEX_BASENAME + "-collection";

  /**
   * The file extension used for the mimir-specific relocatable zip collection
   * definition.
   */
  public static final String MIMIR_COLLECTION_EXTENSION = ".zip";

  /**
   * The name of the file in the index directory where the index config is
   * saved.
   */
  public static final String INDEX_CONFIG_FILENAME = "config.xml";

  private static Logger logger = Logger.getLogger(Indexer.class);
}
