/*
 *  IndexReaderPool.java
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
 *  Valentin Tablan, 15 Sep 2011
 *  
 *  $Id$
 */
package gate.mimir.search;

import it.unimi.dsi.mg4j.index.Index;
import it.unimi.dsi.mg4j.index.IndexReader;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool for IndexReader values assocuated with a given index.
 */
public class IndexReaderPool {
  
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
   * The current size of the pool.
   */
  private AtomicInteger size;
  
  /**
   * The actual pool.
   */
  private ConcurrentLinkedQueue<IndexReader> pool;

  public IndexReaderPool(Index index, int capacity) {
    this.capacity = capacity;
    this.index = index;
    pool = new ConcurrentLinkedQueue<IndexReader>();
    size = new AtomicInteger(0);
  }
  
  public IndexReaderPool(Index index) {
    this(index, DEFAULT_CAPACITY);
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
