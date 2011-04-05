/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  Valentin Tablan, 5 Mar 2009
 *
 *  $Id$
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.util.*;

/**
 * Query node for OR queries. It wraps an array of sub-queries and performs a
 * disjunction between them all.
 */
public class OrQuery implements QueryNode {

  private static final long serialVersionUID = 5351173042947382168L;

  /**
   * Executes a disjunction of other queries.
   * 
   * The hit candidates list is sorted by document ID, then by termPosition,
   * and then by hit length.
   */
  public static class OrQueryExecutor extends AbstractQueryExecutor{

    /**
     * @param engine
     * @param query
     * @throws IOException 
     */
    public OrQueryExecutor(OrQuery query, QueryEngine engine) throws IOException {
      super(engine);
      this.query = query;
      //prepare all the executors
      this.executors = new ExecutorsList(engine, query.getNodes());
      this.hitsOnCurrentDocument = new ObjectArrayList<Binding>();
      for(int i = 0; i < executors.size(); i++){
        executors.nextDocument(i, -1);
      }
    }

    
    /**
     * The query being executed.
     */
    protected OrQuery query;
    
    
    
    /**
     * A list of hits (from the executors) on the current document. This value
     * is populated when {@link #nextDocument(int)} is called, and it emptied as
     * hits are consumed by calling {@link #nextHit()}.
     */
    protected ObjectArrayList<Binding> hitsOnCurrentDocument;
    
    /**
     * The {@link QueryExecutor}s for the contained nodes.
     */
    protected ExecutorsList executors;
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      super.close();
      executors.close();
      executors = null;
      hitsOnCurrentDocument.clear();
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public int nextDocument(int greaterThan) throws IOException {
      if(closed) return latestDocument = -1;
      if(latestDocument == -1) return latestDocument;
      //find the minimum value for latest document
      if (greaterThan >= latestDocument){
        //we need to advance all executors that are lower than greaterThan
        for(int i = 0 ; i < executors.size(); i++){
          if(executors.latestDocument(i) <= greaterThan){
            executors.nextDocument(i, greaterThan);
          }
        }
      }
      
      //now find the minimum value from latestDocuments, 
      //and prepare the hitsOnCurrentDocument list
      hitsOnCurrentDocument.clear();
      List<Integer> minExecutors = new LinkedList<Integer>();
      int minDoc = Integer.MAX_VALUE;
      for(int i = 0; i < executors.size(); i++){
        if(executors.latestDocument(i) >= 0){
          if(executors.latestDocument(i) < minDoc){
            //we found a smaller minimum
            minExecutors.clear();
            minDoc = executors.latestDocument(i);
            minExecutors.add(i);
          }else if(executors.latestDocument(i) == minDoc){
            //another executor on the current document just add to the list
            minExecutors.add(i);
          }
        }
      }
      if(minExecutors.isEmpty()){
        //all executors are out of documents
        return latestDocument = -1;
      }else{
        //for each executor on the current document
        for(int i : minExecutors){
          //extract all results on the new current document
          Binding aHit = executors.nextHit(i);
          while(aHit != null){
            hitsOnCurrentDocument.add(aHit);
            aHit = executors.nextHit(i);
          }
          //move the executor to its next document
          executors.nextDocument(i, -1);
        }
        //now sort the list of candidates
        it.unimi.dsi.fastutil.Arrays.quickSort(0, hitsOnCurrentDocument.size(),
                new IntComparator() {
                  @Override
                  public int compare(Integer one, Integer other) {
                    return compare(one.intValue(), other.intValue());
                  }
                  
                  @Override
                  public int compare(int one, int other) {
                    return hitsOnCurrentDocument.get(one).compareTo(
                            hitsOnCurrentDocument.get(other));
                  }
                }, 
                new Swapper() {
                  @Override
                  public void swap(int one, int other) {
                    Binding temp = hitsOnCurrentDocument.get(one);
                    hitsOnCurrentDocument.set(one,
                            hitsOnCurrentDocument.get(other));
                    hitsOnCurrentDocument.set(other, temp);
                  }
                });
        return latestDocument = minDoc;
      }
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed) return null;
      if(hitsOnCurrentDocument.isEmpty()){
        //no more hits
        return null;
      }else{
        //return the first hit from the list
        Binding aHit = hitsOnCurrentDocument.get(0);
        hitsOnCurrentDocument.remove(0);
        //prepare the hit to be returned
        Binding[] containedBindings = null;
        if(engine.isSubBindingsEnabled()){
          Binding[] subBindings = aHit.getContainedBindings();
          containedBindings = subBindings == null ?
                  new Binding[1] : new Binding[subBindings.length + 1];
          if(subBindings != null){
            System.arraycopy(subBindings, 0, containedBindings, 1, 
                    subBindings.length);
            aHit.setContainedBindings(null);
            containedBindings[0] = aHit;
          }
        }        
        return new Binding(query, aHit.getDocumentId(), aHit.getTermPosition(),
                aHit.getLength(), containedBindings); 
      }
    }

  }
  
  protected QueryNode[] nodes;
  /**
   * Creates anew OR Query from an array of sub-queries.
   * @param nodes the nodes contained by this query.
   */
  public OrQuery(QueryNode... nodes){
    this.nodes = nodes;
  }
  
  
  /**
   * @return the nodes
   */
  public QueryNode[] getNodes() {
    return nodes;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(gate.mimir.search.QueryEngine)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine) throws IOException {
    return new OrQueryExecutor(this, engine);
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder("OR (");
    if(nodes != null){
      for(int  i = 0; i < nodes.length -1; i++){
        str.append(nodes[i].toString());
        str.append(", ");
      }
      str.append(nodes[nodes.length -1].toString());
    }
    str.append(")");
    return str.toString();
  }

}
