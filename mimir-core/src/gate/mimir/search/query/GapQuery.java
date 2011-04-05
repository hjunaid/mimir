/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  Valentin Tablan, 5 Mar 2009
 *
 *  $Id$
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import java.io.IOException;
import java.util.*;


/**
 * A query node that wraps another query node and adds a gap with a specified
 * length (as number of terms) at the end of every result.
 */
public class GapQuery implements QueryNode {

  private static final long serialVersionUID = 223755948497895844L;

  public static class GapQueryExecutor extends AbstractQueryExecutor{

    /**
     * The query to be executed.
     */
    protected GapQuery gapQuery;
    
    /**
     * The executor of the query wrapped by this gap query.
     */
    protected QueryExecutor wrappedExecutor;
    
    
    /**
     * @param queryNode
     * @throws IOException 
     */
    public GapQueryExecutor(GapQuery queryNode, QueryEngine engine) throws IOException {
      super(engine);
      this.gapQuery = queryNode;
      wrappedExecutor = queryNode.getWrappedQuery().getQueryExecutor(engine);
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      super.close();
      wrappedExecutor.close();
    }

    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#getLatestDocument()
     */
    public int getLatestDocument() {
      return wrappedExecutor.getLatestDocument();
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public int nextDocument(int from) throws IOException {
      if(closed) return -1;
      return wrappedExecutor.nextDocument(from);
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed) return null;
      Binding wrappedBinding = wrappedExecutor.nextHit();
      if(wrappedBinding != null){
        Binding[] containedBindings = null;
        if(engine.isSubBindingsEnabled()){
          //create the new bindings array
          Binding[] subBindings = wrappedBinding.getContainedBindings();
          containedBindings = subBindings == null ?
                  new Binding[1] : new Binding[subBindings.length +1];
          if(subBindings != null){        
            System.arraycopy(subBindings, 0, containedBindings, 1, 
                    subBindings.length);
            wrappedBinding.setContainedBindings(null);
          }
          containedBindings[0] = wrappedBinding;
        }
        return new Binding(gapQuery,
                wrappedBinding.getDocumentId(), 
                wrappedBinding.getTermPosition(),
                wrappedBinding.getLength() + gapQuery.getGap(),
                containedBindings); 
      }else{
        return null;
      }
    }
    
  }
  
  /**
   * The length of the gap to be added at the end of all results.
   */
  protected int gap;
  
  /**
   * The query node wrapped by this {@link GapQuery}.
   */
  protected QueryNode wrappedQuery;
  
  
  /**
   * Constructs a new {@link GapQuery}
   * @param wrappedQuery the query node wrapped by this {@link GapQuery}
   * @param gap the length of the gap (i.e. the number of terms to be added to 
   * the length of all results). 
   */
  public GapQuery(QueryNode wrappedQuery, int gap) {
    this.wrappedQuery = wrappedQuery;
    this.gap = gap;
  }

  

  /**
   * Gets the gap length.
   * @return the gap
   */
  public int getGap() {
    return gap;
  }



  /**
   * Gets the wrapped query.
   * @return the wrappedQuery
   */
  public QueryNode getWrappedQuery() {
    return wrappedQuery;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(gate.mimir.search.QueryEngine)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new GapQueryExecutor(this, engine);
  }
  
}
