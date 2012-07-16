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

import java.io.IOException;

import it.unimi.dsi.big.mg4j.index.BitStreamIndex;
import it.unimi.dsi.big.mg4j.index.Index;
import it.unimi.dsi.big.mg4j.index.IndexIterator;
import it.unimi.dsi.big.mg4j.index.IndexReader;
import it.unimi.dsi.big.mg4j.search.DocumentIterator;
import it.unimi.dsi.big.util.StringMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import gate.mimir.index.mg4j.MimirDirectIndexBuilder;
import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;

import static gate.mimir.search.QueryEngine.IndexType;

/**
 * A {@link TermsQuery} that returns the terms occurring in a document.
 */
public class DocumentTermsQuery extends AbstractTermsQuery {
  
  /**
   * The ID of the document for which the terms are being sought.
   */
  protected final long documentId;
  
  /**
   * The name of the subindex in which the terms are sought. Each Mímir 
   * index includes multiple sub-indexes (some storing tokens, other storing 
   * annotations), identified by a name. For token indexes, the index name is
   * the name of the token feature being indexed; for annotation indexes, the
   * index name is the annotation type. 
   */
  protected final String indexName; 

  protected final IndexType indexType;
  
  
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
  public DocumentTermsQuery(long documentId, String indexName, 
      IndexType indexType, boolean stringsEnabled, boolean countsEnabled, 
      int limit) {
    super(stringsEnabled, countsEnabled, limit);
    this.documentId = documentId;
    this.indexName = indexName;
    this.indexType = indexType;
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
  public DocumentTermsQuery(long documentId, String indexName, 
      IndexType indexType) {
    this(documentId, indexName, indexType, false, true, NO_LIMIT);
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
  public DocumentTermsQuery(long documentId, String indexName, 
      IndexType indexType, int limit) {
    this(documentId, indexName, indexType, false, true, limit);
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermQuery#execute()
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) throws IOException {
    IndexReaderPool directIndexPool = null;
    IndexReaderPool indirectIndexPool = null;
    IndexReader indexReader = null;
    try{
      switch(indexType){
        case ANNOTATIONS:
          directIndexPool = engine.getAnnotationDirectIndex(indexName);
          indirectIndexPool = engine.getAnnotationIndex(indexName);
          break;
        case TOKENS:
          directIndexPool = engine.getTokenDirectIndex(indexName);
          indirectIndexPool = engine.getTokenIndex(indexName);
          break;
        default:
          throw new IllegalArgumentException("Invalid index type: " + 
              indexType.toString());
      }
      
      // prepare local data
      LongArrayList termIds = new LongArrayList();
      ObjectArrayList<String> termStrings = stringsEnabled ? 
          new ObjectArrayList<String>() : null;
      IntArrayList termCounts = countsEnabled ? new IntArrayList() : null;
      // start the actual search
      indexReader = directIndexPool.borrowReader();
      IndexIterator results = indexReader.documents(
        MimirDirectIndexBuilder.longToTerm(documentId));
      long termId = results.nextDocument();
      while(termId != DocumentIterator.END_OF_LIST && termId != -1) {
        termIds.add(termId);
        if(countsEnabled) termCounts.add(results.count());
        if(stringsEnabled) termStrings.add(indirectIndexPool.getTerm(termId));
        termId = results.nextDocument();
      }
      // construct the result
      return new TermsResultSet(termIds.toLongArray(),
        stringsEnabled ? termStrings.toArray(new String[termStrings.size()]) : null,
        null,
        countsEnabled ? termCounts.toIntArray() : null);      
    } finally {
      if(indexReader != null) {
        directIndexPool.returnReader(indexReader);  
      }
    }
  }
}
