/*
 *  AbstractIndexTermsQuery.java
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

import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;
import it.unimi.dsi.big.mg4j.search.DocumentIterator;
import it.unimi.dsi.big.mg4j.search.visitor.CounterCollectionVisitor;
import it.unimi.dsi.big.mg4j.search.visitor.CounterSetupVisitor;
import it.unimi.dsi.big.mg4j.search.visitor.TermCollectionVisitor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;

/**
 * Base class for terms queries that use an MG4J index for their search.
 */
public abstract class AbstractIndexTermsQuery extends AbstractTermsQuery {
  
  /**
   * The name of the subindex in which the terms are sought. Each Mímir 
   * index includes multiple sub-indexes (some storing tokens, other storing 
   * annotations), identified by a name. For token indexes, the index name is
   * the name of the token feature being indexed; for annotation indexes, the
   * index name is the annotation type. 
   */
  protected final String indexName; 

  /**
   * The type of index being searched (tokens or annotations).
   */
  protected final IndexType indexType;
  
  /**
   * The direct index used for executing the query. This value is non-null only 
   * if a direct index was configured as part of the Mímir index being searched.
   */
  protected IndexReaderPool directIndexPool;
  
  /**
   * The indirect index used for executing the query.
   */
  protected IndexReaderPool indirectIndexPool;
  
  /**
   * The query engine used to execute this query.
   */
  protected QueryEngine engine;
  
  public AbstractIndexTermsQuery(String indexName, IndexType indexType, 
      boolean stringsEnabled, boolean countsEnabled, int limit) {
    super(stringsEnabled, countsEnabled, limit);
    this.indexName = indexName;
    this.indexType = indexType;
  }

  
  protected void prepare(QueryEngine engine) {
    this.engine = engine;
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
  }
  
  protected TermsResultSet buildResultSet(DocumentIterator documentIterator) 
      throws IOException {
    // prepare local data
    LongArrayList termIds = new LongArrayList();
    ObjectArrayList<String> termStrings = stringsEnabled ? 
        new ObjectArrayList<String>() : null;
    IntArrayList termCounts = countsEnabled ? new IntArrayList() : null;
    TermCollectionVisitor termCollectionVisitor = null;
    CounterSetupVisitor counterSetupVisitor = null;
    CounterCollectionVisitor counterCollectionVisitor = null;
    if(countsEnabled) {
      termCollectionVisitor = new TermCollectionVisitor();
      counterSetupVisitor = new CounterSetupVisitor( termCollectionVisitor );
      counterCollectionVisitor = new CounterCollectionVisitor( counterSetupVisitor );  
      termCollectionVisitor.prepare();
      documentIterator.accept( termCollectionVisitor );
      counterSetupVisitor.prepare();
      documentIterator.accept( counterSetupVisitor ); 
    }
    
    long termId = documentIterator.nextDocument();
    while(termId != DocumentIterator.END_OF_LIST && termId != -1 &&
        termIds.size() < limit) {
      termIds.add(termId);
      if(countsEnabled){
        counterSetupVisitor.clear();
        documentIterator.acceptOnTruePaths( counterCollectionVisitor );
        int count = 0;
        for (int aCount : counterSetupVisitor.count ) count +=  aCount;
        termCounts.add(count);
      }
      if(stringsEnabled) termStrings.add(indirectIndexPool.getTerm(termId));
      termId = documentIterator.nextDocument();
    }
    // construct the result
    return new TermsResultSet(termIds.toLongArray(),
      stringsEnabled ? termStrings.toArray(new String[termStrings.size()]) : null,
      null,
      countsEnabled ? termCounts.toIntArray() : null);  
  }
}
