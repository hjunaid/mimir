/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licensed under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
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
