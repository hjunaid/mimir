/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licensed under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
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
