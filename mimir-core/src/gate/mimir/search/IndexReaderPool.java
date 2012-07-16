/*
 *  IndexReaderPool.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 15 Sep 2011
 *  
 *  $Id$
 */
package gate.mimir.search;

import it.unimi.dsi.big.io.FileLinesCollection;
import it.unimi.dsi.big.mg4j.index.DiskBasedIndex;
import it.unimi.dsi.big.mg4j.index.Index;
import it.unimi.dsi.big.mg4j.index.IndexReader;
import it.unimi.dsi.big.util.ImmutableExternalPrefixMap;
import it.unimi.dsi.lang.MutableString;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool for IndexReader values assocuated with a given index.
 */
public class IndexReaderPool {
  
  private class IndexDictionary extends ImmutableExternalPrefixMap {

    private static final long serialVersionUID = 3350171413268036811L;

    public IndexDictionary(Iterable<? extends CharSequence> terms)
      throws IOException {
      super(terms);
    }

    /**
     * A mutable string used internally. 
     */
    private MutableString ms = new MutableString();

    /**
     * Gets the term string for a given term ID. This method is synchronised to
     * protect the shared mutable string instance.
     * @param termId the ID for the term being sought.
     * @return the string for the given term.
     */
    public synchronized String getTerm(long termId) {
      return super.getTerm(termId, ms).toString();
    }
    
  }
  
  private static final int DEFAULT_CAPACITY = 100000;
  
  /**
   * How many readers are norally kept. The actual size can be much larger than
   * this, as this pool will never refuse to create new readers. However, upon 
   * being returned, all excess readers get destroyed instead of being 
   * re-queued.
   */
  private int capacity;
  
  /**
   * The index used to create IndexReaders.
   */
  private Index index;
  
  
  /**
   * A URI representing the index basename (the template index file name, 
   * without any extension). 
   */
  private URI indexURI;
  
  /**
   * The current size of the pool.
   */
  private AtomicInteger size;
  
  /**
   * An external map that holds the index terms. Allows the retrieval of a 
   * term's text given a term ID.
   */
  private IndexDictionary dictionary;
  
  /**
   * The actual pool.
   */
  private ConcurrentLinkedQueue<IndexReader> pool;

  public IndexReaderPool(Index index, int capacity, URI indexUri) {
    this.capacity = capacity;
    this.index = index;
    this.indexURI = indexUri;
    pool = new ConcurrentLinkedQueue<IndexReader>();
    size = new AtomicInteger(0);
  }
  
  public IndexReaderPool(Index index, URI indexUri) {
    this(index, DEFAULT_CAPACITY, indexUri);
  }
  
  /**
   * Gets an {@link IndexReader} for the index associated with this pool.
   * @return
   * @throws IOException
   */
  public IndexReader borrowReader() throws IOException {
    IndexReader reader = pool.poll();
    if(reader == null) {
      // create a new one
      reader = index.getReader();
    } else {
      size.decrementAndGet();
    }
    return reader;
  }
  

  /**
   * Returns an {@link IndexReader} previously borrowed (see 
   * {@link #borrowReader()}) to the pool.
   * @param indexReader
   * @throws IOException
   */
  public void returnReader(IndexReader indexReader) throws IOException {
    if(size.get() > capacity) {
      //destroy
      indexReader.close();
    } else {
      pool.add(indexReader);
      size.incrementAndGet();
    }
  }

  public Index getIndex() {
    return index;
  }
  
  /**
   * Gets the string term for a given term ID.
   * @param termId
   * @return
   * @throws IOException
   */
  public String getTerm(long termId) throws IOException {
    if(dictionary == null) {
      FileLinesCollection dictionaryLines = new FileLinesCollection(
        new File(URI.create(indexURI.toString() + 
          DiskBasedIndex.TERMS_EXTENSION)).getAbsolutePath(), "UTF-8");
      dictionary = new IndexDictionary(dictionaryLines);
    }
    return dictionary.getTerm(termId);
  }
  
  public void close() throws IOException {
    IndexReader aReader = pool.poll();
    size.decrementAndGet();
    while(aReader != null) {
      aReader.close();
      aReader = pool.poll();
      size.decrementAndGet();
    }
  }
  
}
