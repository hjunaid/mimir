/*
 *  RelocatableZipDocumentCollectionHolder.java
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
 *  Ian Roberts, 03 Mar 2009
 *
 *  $Id$
 */
package gate.mimir.index.mg4j;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import it.unimi.dsi.mg4j.document.DocumentFactory;
import it.unimi.dsi.mg4j.document.ZipDocumentCollection;

/**
 * Holder class allowing us to store a zipped document collection with
 * its path stored relative to the index directory rather than as an
 * absolute path in the serialized file.
 */
public class RelocatableZipDocumentCollectionHolder implements Serializable {
  private static final long serialVersionUID = 710348004677083961L;

  /**
   * The file name of the zip file (relative to the index directory)
   */
  protected String zipFilename;

  /**
   * The underlying document factory.
   */
  protected DocumentFactory factory;

  /**
   * The number of documents stored in the collection.
   */
  protected int numberOfDocuments;

  /**
   * Is the collection exact (i.e. includes the non-tokens between
   * tokens) or inexact (i.e. just the tokens)?
   */
  protected boolean exact;

  /**
   * Create a holder for zip document collection parameters. This is
   * intended to be called at the start of indexing, so we don't yet
   * know how many documents are in the collection. You must call
   * {@link #setNumberOfDocuments(int)} before serializing this object.
   */
  public RelocatableZipDocumentCollectionHolder(String zipFilename,
          DocumentFactory factory, boolean exact) {
    this.zipFilename = zipFilename;
    this.factory = factory;
    this.exact = exact;
  }

  /**
   * Set the number of documents contained in this collection.
   */
  void setNumberOfDocuments(int numberOfDocuments) {
    this.numberOfDocuments = numberOfDocuments;
  }

  /**
   * Get a {@link ZipDocumentCollection} corresponding to the parameters
   * in this object, resolving the zip file location relative to the
   * specified directory.
   * 
   * @param indexDirectory the directory against which the zip file
   *          location will be resolved (typically the directory in
   *          which it is stored).
   * @throws IOException if an exception occurs creating the collection
   *           object.
   */
  public ZipDocumentCollection getCollection(File indexDirectory)
          throws IOException {
    return new ZipDocumentCollection(new File(indexDirectory, zipFilename)
            .getAbsolutePath(), factory, numberOfDocuments, exact);
  }
}
