/*
 *  DocumentsAndTermsQuery.java
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
import it.unimi.dsi.big.mg4j.search.AndDocumentIterator;

import java.io.IOException;

/**
 * Find the terms that occur in <strong>all</strong> the documents in a given
 * set.
 */
public class DocumentsAndTermsQuery extends AbstractIndexTermsQuery {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -5815729554557481213L;

  public DocumentsAndTermsQuery(String indexName, IndexType indexType,
                                boolean countsEnabled,
                                long... documentIds) {
    super(indexName, indexType, countsEnabled, documentIds);
  }

  public DocumentsAndTermsQuery(String indexName, IndexType indexType, 
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
      return buildResultSet(AndDocumentIterator.getInstance(iterators));
    } finally {
      for(IndexReader reader : indexReaders) {
        directIndexPool.returnReader(reader);
      }
    }
  }
}
