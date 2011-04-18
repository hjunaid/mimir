/*
 *  TermQuery.java
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
 *  Valentin Tablan, 03 Mar 2009
 *  
 *  $Id$
 */

package gate.mimir.search.query;

import gate.mimir.IndexConfig;
import gate.mimir.search.QueryEngine;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.mg4j.index.Index;
import it.unimi.dsi.mg4j.index.IndexIterator;

import java.io.IOException;
import java.util.*;


/**
 * A {@link QueryNode} for term queries. A term query consists of an index name 
 * and a query term. 
 */
public class TermQuery implements QueryNode {

  private static final long serialVersionUID = 7302348587893649887L;

  /**
   * Represents the type of index that should be searched. Mimir uses two types
   * of indexes: token indexes (which index the text input) and annotation
   * indexes (which index semantic annotations).
   */
  public static enum IndexType{
    /**
     * Value representing token indexes, used for the document text.
     */
    TOKENS,
    
    /**
     * Value representing annotation indexes, used for the document semantic
     * annotations.
     */
    ANNOTATIONS
  }
  
  /**
   * The query term
   */
  private String term;
  
  /**
   * The name of the index to search.
   */
  private String indexName;
  
  /**
   * The type of the index to be searched.
   */
  private IndexType indexType;
  
  /**
   * The length of the matches. Defaults to <code>1</code>.
   */
  private int length;
  
  
  /**
   * A {@link QueryExecutor} for {@link TermQuery} nodes.
   */
  public static class TermQueryExecutor extends AbstractQueryExecutor{

    
    /**
     * The {@link TermQuery} node being executed.
     */
    private TermQuery query;
    
    /**
     * The index running on. 
     */
    private IndexIterator indexIterator;
    
    /**
     * The positions iterator for the latest document.
     */
    private IntIterator positionsIterator;
    
    
    /**
     * @param node
     * @param index
     * @throws IOException if the index files cannot be accessed.
     */
    public TermQueryExecutor(TermQuery node, QueryEngine engine) throws IOException {
      super(engine);
      this.query = node;
      Index index;
      switch(this.query.indexType){
        case TOKENS:
          if(query.indexName == null){
            //token query, with no index name provided -> use the default
            index = engine.getIndexes()[0];
          }else{
            index = engine.getTokenIndex(query.indexName);
          }
          break;
        case ANNOTATIONS:
          index = engine.getAnnotationIndex(query.indexName);
          break;
        default:
          throw new IllegalArgumentException("Indexes of type " + 
                  this.query.indexType + " are not supported!"); 
      }

      if(index == null) throw new IllegalArgumentException(
              "No index provided for field " + node.getIndexName() + "!");
      //use the term processor for the query term
      MutableString mutableString = new MutableString(query.getTerm());
      index.termProcessor.processTerm(mutableString);
      this.indexIterator = index.documents(mutableString.toString());
      positionsIterator = null;
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument()
     */
    public int nextDocument(int from) throws IOException {
      if(closed) return latestDocument = -1;
      if(latestDocument == -1){
        //we have exhausted the search already
        return latestDocument;
      }
      
      if (from >= latestDocument){
        //we do need to skip
        latestDocument = indexIterator.skipTo(from + 1);
        if(latestDocument == Integer.MAX_VALUE){
          //no more documents available
          latestDocument = -1;
        }
      }else{
        //from is lower than latest document, 
        //so we just return the next document
        latestDocument = indexIterator.nextDocument();
      }
      if(latestDocument != -1){
        positionsIterator = indexIterator.positions();
      }
      return latestDocument;
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit(java.util.Map)
     */
    public Binding nextHit() throws IOException{
      if(closed) return null;
      if(latestDocument >= 0 && positionsIterator.hasNext()){
        int position = positionsIterator.nextInt();
        return new Binding(query, latestDocument, position, query.length, null);
      }else{
        //no more positions, or no more documents
        return null;
      }
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      super.close();
      indexIterator.dispose();
      indexIterator = null;
    }
    
  }
  
  /**
   * @return the term
   */
  public CharSequence getTerm() {
    return term;
  }
  /**
   * @return the indexName
   */
  public String getIndexName() {
    return indexName;
  }
  
  
  /**
   * Creates a new term query, for searching over the document text. 
   * 
   * @param indexName the name of the index to be searched. This should be one
   * of the annotation feature names used for indexing tokens (see 
   * {@link IndexConfig.TokenIndexerConfig}).
   * 
   * @param term the term to be searched for.
   * 
   * @see IndexConfig.TokenIndexerConfig
   */
  public TermQuery(String indexName, String term) {
    this(IndexType.TOKENS, indexName, term, 1);
  }
  

  
  /**
   * Creates a new term query, for searching over semantic annotations.
   *   
   * @param annotationType the type of annotation sought. This should one of the 
   * annotation types used when indexing semantic annotations (see 
   * {@link IndexConfig.SemanticIndexerConfig}).
   * 
   * @param mentionURI the URI of the mention sought.
   * 
   * @param length the length of the mention sought.
   */
  public TermQuery(String annotationType, String mentionURI, int length) {
    this.indexType = IndexType.ANNOTATIONS;
    this.indexName = annotationType;
    this.term = mentionURI;
    this.length = length;
  }

  
  /**
   * Creates a new term query. This constructor is part of a low-level API. see 
   * the other constructors of this class, which may be more suitable!
   *   
   * @param indexType The type of index to be searched.
   * 
   * @param indexName the name of the index to be searched. If the indexType is
   * {@link IndexType#TOKENS}, then the name is interpreted as the feature name 
   * for the document tokens, if the indexType is {@link IndexType#ANNOTATIONS}, 
   * then the name is interpreted as annotation type.
   * 
   * @param term the term to be searched for.
   * 
   * @param length the length of the hits (useful in the case of annotation 
   * indexes, where the length of each mention is stored external to the actual 
   * index).
   */
  public TermQuery(IndexType indexType, String indexName, String term, int length) {
    this.indexType = indexType;
    this.indexName = indexName;
    this.term = term;
    this.length = length;
  }
  
  /**
   * Gets a new query executor for this {@link TermQuery}.
   * @param indexes the set of indexes running on.
   * @return an appropriate {@link QueryExecutor} (in this case, an instance of
   * {@link TermQueryExecutor}).
   * @throws IOException if the index files cannot be accessed.
   * @throws IllegalArgumentException if the provided set of indexes does not
   * include an index for this query's {@link #indexName}.
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(java.util.Map)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new TermQueryExecutor(this, engine);
  }
  
  public String toString() {
    return "TERM(" + 
        (indexName == null ? "" : indexName) + 
        ":" + term + ")";
  }

}
