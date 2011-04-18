/*
 *  Binding.java
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
 *  Valentin Tablan, 4 Mar 2009
 *
 *  $Id$
 */
package gate.mimir.search.query;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A binding used for representing search results. When a query has found a hit,
 * each {@link QueryNode} in the query is bound to some interval on a document.
 * This binding is represented through objects of this class.
 */
public class Binding implements Comparable<Binding>, Serializable {

  private static final long serialVersionUID = -4892765737583819336L;

  /**
   * The document ID for this binding.
   */
  protected int documentID;
  
  /**
   * The term position for this binding.
   */
  protected int termPosition;
  
  /**
   * The length of this binding.
   */
  protected int length;
  
  /**
   * The {@link QueryNode} for this binding.
   */
  protected QueryNode queryNode;
  
  /**
   * The bindings for the sub-query nodes.
   */
  protected Binding[] containedBindings;
  
  /**
   * @param queryNode
   * @param documentID
   * @param termPosition
   * @param length
   * @param containedBindings
   */
  public Binding(QueryNode queryNode, int documentID, int termPosition,
          int length, Binding[] containedBindings) {
    this.queryNode = queryNode;
    this.documentID = documentID;
    this.termPosition = termPosition;
    this.length = length;
    this.containedBindings = containedBindings;
  }

  
  /* (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(Binding o) {
    int res = getDocumentId() - o.getDocumentId();
    if(res == 0){
      res = getTermPosition() - o.getTermPosition();
    }
    if(res == 0){
      res = getLength() - o.getLength();
    }
    return res;
  }

  /**
   * Gets the documentID for this binding.
   * @return
   */
  public int getDocumentId(){
    return documentID;
  }
  
  /**
   * Gets the term position where this binding starts, in the document wth the 
   * ID returned by {@link #getDocumentId()}.
   * @return
   */
  public int getTermPosition(){
    return termPosition;
  }
  
  /**
   * Gets the length (the number of terms) for this binding.
   * @return
   */
  public int getLength(){
    return length;
  }
  
  /**
   * The {@link QueryNode} representing the query segment that this binding is
   * assigned to.
   * @return
   */
  public QueryNode getQueryNode(){
    return queryNode;
  }
  
  
  /**
   * Gets the bindings corresponding to all the sub-nodes of the query node for 
   * this binding.
   * To save memory, in the case of compound {@link QueryNode}s (i.e. nodes that
   * contain other nodes), only the top node will contain this array of 
   * bindings, which will include the bindings for the entire hierarchy of nodes 
   * (but not including the binding for the top node).
   * @return an array of {@link Binding} values.
   */
  public Binding[] getContainedBindings(){
    return containedBindings;
  }

  /**
   * @param containedBindings the containedBindings to set
   */
  public void setContainedBindings(Binding[] containedBindings) {
    this.containedBindings = containedBindings;
  }
}
