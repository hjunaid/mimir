/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licensed under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 15 Apr 2009
 *
 *  $Id$
 */
package gate.mimir.index.mg4j.zipcollection;


import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;

import java.io.*;

import org.apache.log4j.Logger;


import java.util.zip.*;

/**
 * A writer for Mimir zip document collections.
 * A Mimir document collection is a set of serialised {@link DocumentData} 
 * objects stored in one or more zip files.
 * To create a Mimir document collection, create a writer (pointing it to the 
 * top level index directory), add new documents using the 
 * {@link #writeDocument(DocumentData)} method, and close the collection at the
 * end, by calling the {@link #close()} method.
 * 
 * This writer will create one or more zip files as required, never writing more
 * than {@link #ZIP_FILE_MAX_ENTRIES} entries, or more than 
 * {@link #ZIP_FILE_MAX_SIZE} bytes to a single file.
 */
public class DocumentCollectionWriter {

  
  /**
   * The maximum number of bytes to write to a single zip file.
   */
  public static final long ZIP_FILE_MAX_SIZE = 2 * 1000 * 1000 * 1000; 
    
  /**
   * The maximum number of entries to write to a single zip file.
   * Java 1.5 only support 2^16 entries, so the default limit is set below that.
   * If running on Java 1.6, this limit can safely be increased, however, the
   * total size of the file (as specified by {@link #ZIP_FILE_MAX_SIZE}) should 
   * not be greater than 4GB, in either case.
   */
  public static final int ZIP_FILE_MAX_ENTRIES = 65530;
  
  private static Logger logger = Logger.getLogger(DocumentCollectionWriter.class);
  
  /**
   * The zip file managed by this collection.
   */
  protected ZipOutputStream zipOuputStream;
  
  /**
   * The zip file to which we are currently writing.
   */
  protected File zipFile;
  
  /**
   * The top-level index directory.
   */
  protected File indexDir;
  /**
   * The number of entries written so far to the current zip file.
   */
  protected int currentEntries;
  
  /**
   * The amount of bytes written so far to the current zip file.
   */
  protected long currentLength;
  
  /**
   * A {@link ByteArrayOutputStream} used to temporarily store serialised 
   * document data objects.
   */
  protected ByteArrayOutputStream byteArrayOS;
  
  /**
   * The ID for the next document to be written. This value is initialised to 0
   * and then is automatically incremented whenever anew document is written.
   */
  protected int documentId;
  

  /**
   * The unique ID of the current zip file.
   */
  protected int zipFileId;
  
  /**
   * Creates a new DocumentCollectionWriter for the specified index.
   * @param indexDir the top level index directory.
   */
  public DocumentCollectionWriter(File indexDir){
    this.indexDir = indexDir;
    byteArrayOS = new ByteArrayOutputStream();
    documentId = 0;
    zipFileId = 0;
  }

  
  /**
   * Writes a new document to the underlying zip file. The documents added 
   * through this method will get automatically generated names starting from 
   * &quot;0&quot;, and continuing with &quot;1&quot;, &quot;2&quot;, etc.   
   * @param document
   * @throws IndexException if there are any problems while accessing the zip 
   * collection file(s).
   */
  public void writeDocument(DocumentData document) throws IndexException{
    if(zipFile == null) openZipFile();
    try{
      //write the new document to the byte array
      ObjectOutputStream objectOutStream = new ObjectOutputStream(byteArrayOS);
      objectOutStream.writeObject(document);
      objectOutStream.close();

      //see if we're about to go over the limits
      if(currentEntries >= ZIP_FILE_MAX_ENTRIES || 
         currentLength + byteArrayOS.size()  >= ZIP_FILE_MAX_SIZE){
        //move to the next zip file
        closeZipFile();
        zipFileId ++;
        openZipFile();
      }

      //create a new entry in the current zip file
      ZipEntry entry = new ZipEntry(Integer.toString(documentId++));
      zipOuputStream.putNextEntry(entry);
      //write the data
      byteArrayOS.writeTo(zipOuputStream);
      zipOuputStream.closeEntry();
      currentLength += entry.getCompressedSize();
      //clean up the byte array for next time
      byteArrayOS.reset();
      currentEntries++;
    }catch(IOException e){
      throw new IndexException("Problem while accessing the collection file", e);
    }
  }
  
  /**
   * Opens the current zip file and sets the {@link #zipFile} and 
   * {@link #zipOuputStream} values accordingly. 
   * @throws IndexException if the collection zip file already exists, or cannot
   * be opened for writing.
   */
  protected void openZipFile() throws IndexException{
    zipFile = new File(new File(indexDir, Indexer.MG4J_INDEX_DIRNAME), 
            Indexer.MIMIR_COLLECTION_BASENAME + 
            "-" + zipFileId +
            Indexer.MIMIR_COLLECTION_EXTENSION);
    if(zipFile.exists()) throw new IndexException("Collection zip file (" + 
            zipFile.getAbsolutePath() + ") already exists!");
    
    try {
      zipOuputStream = new ZipOutputStream(new BufferedOutputStream(
              new  FileOutputStream(zipFile)));
    } catch(FileNotFoundException e) {
      throw new IndexException("Cannot write to collection zip file (" + 
              zipFile.getAbsolutePath() + ")", e);
    }
    currentEntries = 0;
    currentLength = 0;
  }
  
  /**
   * Closes the current zip file.
   * @throws IOException 
   */
  protected void closeZipFile() throws IOException{
    zipOuputStream.close();
  }
  
  /**
   * Closes this writer (and the underlying zip file).
   * @throws IOException 
   */
  public void close() throws IOException{
    closeZipFile();
  }
  
}
