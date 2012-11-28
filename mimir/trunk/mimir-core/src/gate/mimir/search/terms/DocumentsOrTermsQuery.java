/*
 *  DocumentsOrTermsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 17 Jul 2012
 *
 *  $Id$
 */
package gate.mimir.search.terms;

import gate.mimir.index.mg4j.MimirDirectIndexBuilder;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;
import it.unimi.dsi.big.mg4j.index.IndexIterator;
import it.unimi.dsi.big.mg4j.index.IndexReader;
import it.unimi.dsi.big.mg4j.search.OrDocumentIterator;

import java.io.IOException;

/**
 * Find the terms that occur in <strong>any</strong> of the documents in a given
 * set.
 */
public class DocumentsOrTermsQuery extends AbstractIndexTermsQuery {
  

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -3836472816480490578L;
  
  public DocumentsOrTermsQuery(String indexName, IndexType indexType,
                               boolean countsEnabled, 
                               long... documentIds) {
    super(indexName, indexType, countsEnabled, documentIds);
    this.documentIds = documentIds;
  }


  public DocumentsOrTermsQuery(String indexName, IndexType indexType,
                               long... documentIds) {
    this(indexName, indexType, false, documentIds);
  }
  
  
  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    prepare(engine);
    IndexReader[] indexReaders = new IndexReader[documentIds.length];
    try {
      IndexIterator[] iterators = new IndexIterator[documentIds.length];
      for(int i = 0; i < documentIds.length; i++) {
        indexReaders[i] = directIndexPool.borrowReader();
        iterators[i] = indexReaders[i].documents(
          MimirDirectIndexBuilder.longToTerm(documentIds[i]));
      }
      return buildResultSet(OrDocumentIterator.getInstance(iterators));
    } finally {
      for(IndexReader reader : indexReaders) {
        directIndexPool.returnReader(reader);
      }
    }
  }
}
