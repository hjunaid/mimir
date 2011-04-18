/*
 *  SemanticAnnotationHelper.java
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
 *  Valentin Tablan, 19 Feb 2009
 *
 *  $Id$
 */
package gate.mimir;

import gate.Annotation;
import gate.Document;
import gate.mimir.index.Indexer;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Interface for classes that convert annotations into semantic metadata.
 */
public interface SemanticAnnotationHelper extends Serializable{
  
  /**
   * Called by the containing {@link Indexer} when this helper is first created, 
   * when doing indexing.
   * @param indexer
   */
  public void init(Indexer indexer);
  
  /**
   * Called by the containing {@link QueryEngine} when this helper is first 
   * created, in preparation for searching.
   * @param queryEngine
   */
  public void init(QueryEngine queryEngine);
  
  /**
   * This method converts an annotation into the corresponding semantic metadata
   * and returns the mention URIs corresponding to the original annotation.
   * @param annotation the input annotation.
   * @param length the length of the annotation (given as number of tokens).
   * @return the URIs for the mention (created in the triple store) that are 
   * associated with the input annotation.
   */
  public String[] getMentionUris(Annotation annotation, int length, Indexer indexer); 
  
  /**
   * Prepares this helper for running on a new document.
   * @param document the new document.
   */
  public void documentStart(Document document);
  
  /**
   * Notifies this helper that the current document has finished.
   */
  public void documentEnd();
 
  /**
   * Convenience method: variant of 
   * {@link #getMentions(String, List, QueryEngine)}, where all constraints are
   * of type {@link ConstraintType#EQ}. 
   * 
   * @param annotationType the annotation type.
   * @param constraints constraints on the annotation's feature values.
   * @param engine the {@link QueryEngine} in which this query will be
   *         running.
   * @return the list of URIs for mentions that match the provided constraints.
   */
  public List<Mention> getMentions(String annotationType,
          Map<String, String> constraints, QueryEngine engine);
  
  /**
   * This method supports searching for annotation mentions. It is used to 
   * obtain all the mentions of the given annotation type that match the
   * given constraints.
   * 
   * @param annotationType the type of annotation required.
   * @param constraints a list of constraints that the sough annotation should
   * satisfy.
   * @param engine the {@link QueryEngine} in which this query will be running.
   * 
   * @return the list of URIs for mentions that match the provided constraints.
   */
  public List<Mention> getMentions(String annotationType,
          List<Constraint> constraints, QueryEngine engine);
  
  /**
   * Closes this annotation helper. Implementers should perform maintenance 
   * operations (such as closing connections to ORDI, etc) on this call.
   */
  public void close(Indexer indexer);
  
  
  /**
   * Closes this annotation helper. Implementers should perform maintenance 
   * operations (such as closing connections to ORDI, etc) on this call.
   */
  public void close(QueryEngine qEngine);
  
}
