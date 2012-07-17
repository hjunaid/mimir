/*
 *  DocumentTermQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 16 Jul 2012
 *
 *  $Id$
 */
package gate.mimir.search.terms;

import gate.mimir.index.mg4j.MimirDirectIndexBuilder;
import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;
import it.unimi.dsi.big.mg4j.index.IndexIterator;
import it.unimi.dsi.big.mg4j.index.IndexReader;

import java.io.IOException;

/**
 * A {@link TermsQuery} that returns the terms occurring in a document.
 */
public class DocumentTermsQuery extends AbstractIndexTermsQuery {
  
  /**
   * The ID of the document for which the terms are being sought.
   */
  protected final long documentId;
  
  
  /**
   * Creates a new document term query.
   * 
   * @param documentId the ID of the document for which the terms are sought
   * 
   * @param indexName the name of the sub-index to be searched. Each Mímir 
   * index includes multiple sub-indexes (some storing tokens, other storing 
   * annotations), identified by a name. For token indexes, the index name is
   * the name of the token feature being indexed; for annotation indexes, the
   * index name is the annotation type.
   * 
   * @param idsEnabled should term IDs be returned.
   * 
   * @param stringsEnabled should term Strings be returned.
   * 
   * @param countsEnabled should term counts be returned.
   * 
   * @param limit the maximum number of results to be returned.
   */
  public DocumentTermsQuery(String indexName, 
      IndexType indexType, boolean stringsEnabled, boolean countsEnabled, 
      int limit, long documentId) {
    super(indexName, indexType, stringsEnabled, countsEnabled, limit);
    this.documentId = documentId;
  }

  /**
   * Creates a new document term query.
   * The returned result set will contain term IDs and term counts, but not
   * term strings. The number of results returned is not limited. If a 
   * different result set configuration is required, use the
   * {@link #DocumentTermQuery(long, String, boolean, boolean, boolean)} 
   * constructor variant.
   * 
   * @param documentId the ID of the document for which the terms are sought
   * 
   * @param indexName the name of the sub-index to be searched. Each Mímir 
   * index includes multiple sub-indexes (some storing tokens, other storing 
   * annotations), identified by a name. For token indexes, the index name is
   * the name of the token feature being indexed; for annotation indexes, the
   * index name is the annotation type.
   * 
   * @param indexType the type of the index being searched.
   */
  public DocumentTermsQuery(String indexName, IndexType indexType, 
                            long documentId) {
    this(indexName, indexType, false, true, NO_LIMIT, documentId);
  }

  /**
   * Creates a new document term query.
   * The returned result set will contain term IDs and term counts, but not
   * term strings. If a different result set configuration is required, use the
   * {@link #DocumentTermQuery(long, String, boolean, boolean, boolean)} 
   * constructor variant.
   * 
   * @param documentId the ID of the document for which the terms are sought
   * 
   * @param indexName the name of the sub-index to be searched. Each Mímir 
   * index includes multiple sub-indexes (some storing tokens, other storing 
   * annotations), identified by a name. For token indexes, the index name is
   * the name of the token feature being indexed; for annotation indexes, the
   * index name is the annotation type.
   * 
   * @param indexType the type of the index being searched.
   * 
   * @param limit the maximum number of results to be returned.
   */
  public DocumentTermsQuery(String indexName, IndexType indexType, int limit,
                            long documentId) {
    this(indexName, indexType, false, true, limit, documentId);
  }

  
  
  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermsQuery#execute(gate.mimir.search.QueryEngine)
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    prepare(engine);
    IndexReader indexReader = null; 
    try{
      indexReader = directIndexPool.borrowReader();
      return buildResultSet(
        indexReader.documents(MimirDirectIndexBuilder.longToTerm(documentId)));
    } finally {
      if(indexReader != null) directIndexPool.returnReader(indexReader);
    }
  }
}
