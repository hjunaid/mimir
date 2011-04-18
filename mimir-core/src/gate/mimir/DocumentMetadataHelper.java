/*
 *  DocumentMetadataHelper.java
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
