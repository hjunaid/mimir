/*
 *  DocumentMetadataHelper.java
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licensed under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 6 Oct 2009
 *
 *  $Id$ 
 */
package gate.mimir;

import gate.mimir.index.mg4j.GATEDocument;
import gate.mimir.index.mg4j.zipcollection.DocumentData;

/**
 * Interface for classes that implement a method of generating document 
 * metadata.
 */
public interface DocumentMetadataHelper {
  
  /**
   * Called when the indexing a new document begins.
   * @param document the document being indexed.
   */
  public void documentStart(GATEDocument document);
  
  /**
   * Called when the indexing of a document has completed. This method should
   * add metadata fields to the provided documentData object. 
   * @param document the document being indexed
   * @param documentData the documentData value that will be stored as part of
   * the index, and which holds the metadata fields.
   */
  public void documentEnd(GATEDocument document, DocumentData documentData);
}
