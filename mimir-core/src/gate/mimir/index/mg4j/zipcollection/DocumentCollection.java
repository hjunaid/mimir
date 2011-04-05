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
import java.util.*;
import java.util.zip.*;

import org.apache.log4j.Logger;



/**
 * A Mimir document collection. Consists of one or more zip files containing 
 * serialised {@link DocumentData} values.
 */
public class DocumentCollection {

  /**
   * A simple {@link FilenameFilter} that only accepts the zip files that are
   * part of a collection.
   * 
   * In order to be accepted, the file name needs to be in the form:
   * &quot;{@value Indexer#MIMIR_COLLECTION_BASENAME}-number{@value Indexer#MIMIR_COLLECTION_EXTENSION}&quot;
   */
  private class CollectionFilenameFilter implements FilenameFilter{
    public boolean accept(File dir, String name) {
      return getZipFileId(name) != -1;
    }
  }
  
  /**
   * Given the name of a zip file, this method returns its ID (the numeric part 
   * of the name), or -1 if the name is not that of a valid collection file.
   * @param fileName the file name to be parsed.
   * @return the ID of the file, or -1.
   */
  protected int getZipFileId(String fileName){
    if(fileName.startsWith(Indexer.MIMIR_COLLECTION_BASENAME + "-") &&
            fileName.endsWith(Indexer.MIMIR_COLLECTION_EXTENSION)){
      String numberPart = fileName.substring(
             Indexer.MIMIR_COLLECTION_BASENAME.length() + 1,
             fileName.length() - Indexer.MIMIR_COLLECTION_EXTENSION.length());
      
      try {
        return Integer.parseInt(numberPart);
      } catch(NumberFormatException e) {
        //non-parseable
        return -1;
      }
    }
    return -1; 
  }
  
  /**
   * The zip files containing the document collection.
   */
  protected ZipFile[] zipFiles = null;
  
  private static Logger logger = Logger.getLogger(DocumentCollection.class);
  
  /**
   * The top level directory for the index.
   */
  protected File indexDir;
  
  /**
   * The maximum entry number in each zip file. This array is aligned with 
   * {@link #zipFiles}. The zip file at position <code>i</code> in 
   * {@link #zipFiles} will contain the entries with numbers between 
   * <code>maxEntries[i-1] + 1</code> and <code>maxEntries[i]</code>, inclusive.
   * By convention, <code>maxEntries[-1]=-1</code>.
   */
  protected int[] maxEntries = null;
  
  /**
   * Flag that gets set to true when the collection is closed (and blocks all 
   * subsequent operations).
   */
  private volatile boolean closed = false; 
  
  /**
   * Opens a zip file and creates a DocumentCollection object for accessing the 
   * document data.
   * @param indexDirectory
   * @throws IndexException if the document collection files cannot be accessed. 
   */
  public DocumentCollection(File indexDirectory) throws IndexException {
    this.indexDir = indexDirectory;
  }
  
  /**
   * Opens all the collection files, parses their catalogues, and populates the
   * {@link #zipFiles} and {@link #maxEntries} arrays. 
   * @param indexDirectory
   * @throws IndexException
   */
  protected void openCollectionFiles() throws IndexException{
    File mg4JIndexDir = new File(indexDir, Indexer.MG4J_INDEX_DIRNAME);
    if(!mg4JIndexDir.isDirectory()) throw new IndexException(
            "Cannot locate an MG4J index directory at " + mg4JIndexDir + "!");
    File[] collectionFiles = mg4JIndexDir.listFiles(
            new CollectionFilenameFilter());
    if(collectionFiles.length == 0){
      logger.warn("No collection files found! The index at " + indexDir + 
              " is probably emtpy or corrupted!");
    }
    //sort the files by ID
    Arrays.sort(collectionFiles, new Comparator<File>(){
      public int compare(File o1, File o2) {
        return getZipFileId(o1.getName()) - getZipFileId(o2.getName());
      }
    });
    zipFiles = new ZipFile[collectionFiles.length];
    maxEntries = new int[collectionFiles.length];
    for(int  i = 0; i  < collectionFiles.length; i++){
      try {
        //for each file, open a ZipFile, parse the entries, set the maxEntry value.
        zipFiles[i] = new ZipFile(collectionFiles[i]);
        Enumeration<? extends ZipEntry> entries = zipFiles[i].entries();
        maxEntries[i] = -1;
        while(entries.hasMoreElements()){
          ZipEntry anEntry = entries.nextElement();
          String entryName = anEntry.getName();
          try {
            int entryId = Integer.parseInt(entryName);
            //sanity check
            if(i > 0 && entryId <= maxEntries[i-1]){
              throw new IndexException(
                      "Invalid entries distribution: collection file " + 
                      collectionFiles[i].getAbsolutePath() + 
                      " contains an entry named \"" + entryName + 
                      "\", but an entry with a larger-or-equal ID was " +
                      "already seen in a previous collection file!");
            }
            //update the current maximum
            if(entryId > maxEntries[i]) maxEntries[i] = entryId;
          } catch(NumberFormatException e) {
            //not parseable -> we'll ignore this entry.
            logger.warn("Unparseable zip entry name: " + entryName);
          }
        }
      } catch(ZipException e) {
        throw new IndexException("Problem while reading collection file " + 
                collectionFiles[i].getAbsolutePath(), e);
      } catch(IOException e) {
        throw new IndexException("Problem while accessing collection file " + 
                collectionFiles[i].getAbsolutePath(), e);
      }
    }
    
    logger.info("Opened zip collection: maxEntries = " + Arrays.toString(maxEntries));
  }
  
  /**
   * Gets the document data for a given document ID.
   * @param documentID the ID of the document to be retrieved.
   * @return a {@link DocumentData} object for the requested document ID.
   * @throws IOException if there are problems accessing the underlying zip file; 
   * @throws NoSuchElementException if the requested document ID is not found.
   */
  public DocumentData getDocumentData(int documentID) throws IndexException{
    if(closed) throw new IllegalStateException(
            "This document collection has already been closed!");
    if(zipFiles == null){
      //open the zip files, parse their catalogues and update the values in 
      //maxEntries
      openCollectionFiles();
    }
    //locate the right zip file
    int zipFileId = 0;
    while(zipFileId < maxEntries.length && documentID > maxEntries[zipFileId]){
      zipFileId++;
    }
    if(zipFileId >= maxEntries.length){
      //entry not found (entry number too large)
      throw new NoSuchElementException("No entry found for document ID " + 
              documentID + ". Document ID too large for this collection!");
    }
    
    ZipEntry entry = zipFiles[zipFileId].getEntry(Integer.toString(documentID));
    if(entry == null) 
      throw new NoSuchElementException("No entry found for document ID " + documentID);
    try {
      ObjectInputStream ois = new ObjectInputStream(zipFiles[zipFileId].getInputStream(entry));
      
      DocumentData docData = (DocumentData) ois.readObject();
      ois.close();
      return docData;
    } catch(ClassNotFoundException e) {
      //invalid data read from the zip file
      throw new IndexException("Invalid data read from zip file!", e);
    } catch(IOException e) {
      throw new IndexException("Exception reading zip file!", e);
    }
  }
  
  /**
   * Close this document collection and release all allocated resources (such 
   * as open file handles). 
   */
  public void close() {
    closed = true;
    if(zipFiles != null){
      for(int i = 0; i < zipFiles.length; i++){
        try {
          zipFiles[i].close();
          zipFiles[i] = null;
        } catch(IOException e) {
          // ignore
        }
      }
      zipFiles = null;      
    }
  }
}
