/*
 *  AbstractOverlapQuery.java
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
 *  Valentin Tablan, 21 Apr 2009
 *
 *  $Id$
 */
package gate.mimir.search.query;


import gate.mimir.search.QueryEngine;

import java.io.IOException;
import java.util.*;


/**
 * Abstract class providing the shared functionality used by both 
 * {@link WithinQuery} and {@link ContainsQuery}.
 */
public abstract class AbstractOverlapQuery implements QueryNode{

  private static final long serialVersionUID = -6787211242951582971L;

  /**
   * @param innerQuery
   * @param outerQuery
   */
  public AbstractOverlapQuery(QueryNode innerQuery, QueryNode outerQuery) {
    this.innerQuery = innerQuery;
    this.outerQuery = outerQuery;
  }
  
  public static class OverlapQueryExecutor extends AbstractQueryExecutor{
    
    /**
     * Which of the sub-queries is the target?
     */
    protected SubQuery targetQuery;
    
    /**
     * @param engine
     * @param query
     * @throws IOException 
     */
    public OverlapQueryExecutor(AbstractOverlapQuery query, QueryEngine engine, SubQuery target) throws IOException {
      super(engine);
      this.targetQuery = target;
      this.query = query;
      
      innerExecutor = query.innerQuery.getQueryExecutor(engine);
      outerExecutor = query.outerQuery.getQueryExecutor(engine);
      
      hitsOnCurrentDocument = new ArrayList<Binding>();
    }

    protected QueryExecutor innerExecutor;
    
    protected QueryExecutor outerExecutor;

    /**
     * The query being executed.
     */
    private AbstractOverlapQuery query;
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      super.close();
      innerExecutor.close();
      outerExecutor.close();
    }


    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public int nextDocument(int greaterThan) throws IOException {
      if(closed) return latestDocument = -1;
      if(latestDocument == -1) return latestDocument;
      hitsOnCurrentDocument.clear();
      while(hitsOnCurrentDocument.isEmpty() && latestDocument != -1){
        //find a document on which both sub-executors agree
        int outerNext = outerExecutor.nextDocument(greaterThan);
        int innerNext = innerExecutor.nextDocument(greaterThan);
        while(outerNext != innerNext){
          if(outerNext == -1 || innerNext == -1){
            //one executor has run out -> we're done!
            return  latestDocument = -1;
          }
          //advance the smallest one 
          while(outerNext < innerNext){
            outerNext = outerExecutor.nextDocument(innerNext - 1);
            if(outerNext == -1) return -1;
          }
          while(innerNext < outerNext){
            innerNext = innerExecutor.nextDocument(outerNext -1);
            if(innerNext == -1) return -1;
          }
        }
        //at this point, the next docs are the same
        latestDocument = outerNext;
        //now check that there are actual hits on the current doc
        if(latestDocument != -1){
          getHitsOnCurrentDocument();
        }
      }
      return latestDocument;
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed) return null;
      return hitsOnCurrentDocument.isEmpty() ?  null : hitsOnCurrentDocument.remove(0);
    }
  
    protected List<Binding> hitsOnCurrentDocument;
    
    protected void getHitsOnCurrentDocument() throws IOException{
      hitsOnCurrentDocument.clear();
      //get the hits from each of the sub-executors
      List<Binding> outerHits = new LinkedList<Binding>();
      Binding aHit = outerExecutor.nextHit();
      while(aHit != null){
        outerHits.add(aHit);
        aHit = outerExecutor.nextHit();
      }
      List<Binding> innerHits = new LinkedList<Binding>();
      aHit = innerExecutor.nextHit();
      while(aHit != null){
        innerHits.add(aHit);
        aHit = innerExecutor.nextHit();
      }
      //now find the overlaps
      outer:for(Binding outerHit : outerHits){
        Iterator<Binding> innerIter = innerHits.iterator();
        while(innerIter.hasNext()){
          Binding innerHit = innerIter.next();
          if(innerHit.getTermPosition() < outerHit.getTermPosition()){
            //inner not useful any more
            innerIter.remove();
          }else if((innerHit.getTermPosition() + innerHit.getLength()) <= 
                   (outerHit.getTermPosition() + outerHit.getLength())){
            //good hit:
            // inner.start not smaller than outer.start && 
            // inner.end smaller than outer.end
            switch(targetQuery){
              case INNER:
                hitsOnCurrentDocument.add(innerHit);
                //hit returned, cannot be used any more
                innerIter.remove();
                break;
              case OUTER:
                hitsOnCurrentDocument.add(outerHit);
                //hit returned, move to next one
                continue outer;
            }
          }else if(innerHit.getTermPosition() > 
                   (outerHit.getTermPosition() + outerHit.getLength())){
            //all remaining inners are later than current outer
            //move to next outer
            continue outer;
          }else{
            //current inner starts inside current outer, but ends outside
            //it may still be useful for other outers
            //we just ignore it, and move to the next one
          }
        }
      }
    }
    
  }

  /**
   * A simple enum used to identify the two sub-queries.
   */
  protected enum SubQuery{
    INNER, OUTER
  }

  /**
   * The query providing the inner intervals.
   */
  protected QueryNode innerQuery;
  
  /**
   * The query providing the outer intervals.
   */  
  protected QueryNode outerQuery;

}
