/*
 *  AnnotationQuery.java
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


import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.IndexConfig;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.index.Mention;
import gate.mimir.index.mg4j.MentionsIndexBuilder;
import gate.mimir.search.QueryEngine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;


/**
 * A query for the annotations index. 
 */
public class AnnotationQuery implements QueryNode {

  private static final long serialVersionUID = 5996543707885867821L;


  public static class AnnotationQueryExecutor extends AbstractQueryExecutor{

    
    /**
     * @param engine
     * @param query
     */
    public AnnotationQueryExecutor(AnnotationQuery query, QueryEngine engine) throws IOException {
      super(engine);
      this.query = query;
      buildQuery();
    }
    
    /**
     * The query being executed.
     */
    private AnnotationQuery query;
    
    /**
     * Logger for this class.
     */
    private static Logger logger = Logger.getLogger(AnnotationQueryExecutor.class);

    /**
     * The underlying OrQuery executor that actually does the work.
     */
    private QueryExecutor underlyingExecutor;
    
    /**
     * Build the underlying OrQuery executor that this annotation query uses.
     */
    protected void buildQuery() throws IOException {
      // find the semantic annotation helper for the right annotation type
      SemanticAnnotationHelper helper = getAnnotationHelper(engine.getIndexConfig());
      // ask the helper for the mentions that correspond to this query
      long start = System.currentTimeMillis();      
            List<Mention> mentions = helper.getMentions(query.getAnnotationType(),
                    query.getConstraints(), engine);
      logger.debug(mentions.size() + " mentions obtained in " + 
        (System.currentTimeMillis() - start) + " ms");
//if(true) System.exit(0);
      // now create a big OrQuery of all the possible mentions with
      // appropriate gaps
      QueryNode[] disjuncts = new QueryNode[mentions.size()];
      int index = 0;
      for(Mention m : mentions) {
        // create a term query for the mention URI
        disjuncts[index] = new TermQuery(query.annotationType, 
                m.getUri(), m.getLength());
        index++;
      }
      
      QueryNode underlyingQuery = new OrQuery(disjuncts);
      underlyingExecutor = underlyingQuery.getQueryExecutor(engine);
    }

    /**
     * Get the {@link SemanticAnnotationHelper} corresponding to this query's
     * annotation type.
     * @param indexConfig the index configuration
     * @throws IllegalArgumentException if the annotation helper for this
     *         type is not a {@link PlainAnnotationHelper}.
     */
    protected SemanticAnnotationHelper getAnnotationHelper(
            IndexConfig indexConfig) {
      for(SemanticIndexerConfig semConfig : indexConfig.getSemanticIndexers()){
        for(int i = 0; i < semConfig.getAnnotationTypes().length; i++){
          if(query.getAnnotationType().equals(
                  semConfig.getAnnotationTypes()[i])){
            return semConfig.getHelpers()[i];
          }
        }
      }
      throw new IllegalArgumentException("Semantic annotation type \""
              + query.getAnnotationType() + "\" not known to this query engine.");
    }
    
    
    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#close()
     */
    public void close() throws IOException {
      super.close();
      underlyingExecutor.close();
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#getLatestDocument()
     */
    public int getLatestDocument() {
      return underlyingExecutor.getLatestDocument();
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextDocument(int)
     */
    public int nextDocument(int greaterThan) throws IOException {
      if(closed) return -1;
      return underlyingExecutor.nextDocument(greaterThan);
    }

    /* (non-Javadoc)
     * @see gate.mimir.search.query.QueryExecutor#nextHit()
     */
    public Binding nextHit() throws IOException {
      if(closed) return null;
      Binding underlyingHit = underlyingExecutor.nextHit();
      if(underlyingHit == null) return null;
      
      return new Binding(query, underlyingHit.getDocumentId(),
              underlyingHit.getTermPosition(),
              underlyingHit.getLength(),
              underlyingHit.getContainedBindings());
    }
    
  }
  
  /**
   * Constructs a new {@link AnnotationQuery}.
   * 
   * Convenience variant of {@link #AnnotationQuery(String, List)} 
   * for cases where all predicates are of type 
   * {@link SemanticAnnotationHelper.ConstraintType#EQ}.
   * 
   * @param annotationType the desired annotation type, for the annotations to 
   * be matched.
   * @param featureConstraints the constraints over the features of the 
   * annotations to be found. This is represented as a {@link Map} from feature
   * name (a {@link String}) to feature value (also a {@link String}).
   * 
   * @see AnnotationQuery#AnnotationQuery(String, List)  
   */
  public AnnotationQuery(String annotationType,
          Map<String, String> featureConstraints) {
    if(featureConstraints == null){
      featureConstraints = new HashMap<String, String>();
    }
    this.annotationType = annotationType;
    this.constraints = new ArrayList<Constraint>(featureConstraints.size());
    for(Map.Entry<String, String> entry : featureConstraints.entrySet()){
      this.constraints.add(new Constraint(ConstraintType.EQ,
              entry.getKey(), entry.getValue()));
    }
  }

  /**
   * Constructs a new Annotation Query.
   *  
   * @param annotationType the type of annotation being sought.
   * @param constraints a list of constraints placed on the feature values. An 
   * empty constraints list will make no requests regarding the feature values,
   * hence it will match all annotations of the right type. 
   */
  public AnnotationQuery(String annotationType, List<Constraint> constraints) {
    this.annotationType = annotationType;
    this.constraints = constraints == null ? new ArrayList<Constraint>() :constraints;
  }
  
  /* (non-Javadoc)
   * @see gate.mimir.search.query.QueryNode#getQueryExecutor(java.util.Map)
   */
  public QueryExecutor getQueryExecutor(QueryEngine engine)
          throws IOException {
    return new AnnotationQueryExecutor(this, engine);
  }
  
  
  /**
   * Gets the annotation type for this query. 
   * @return the annotationType
   */
  public String getAnnotationType() {
    return annotationType;
  }

  /**
   * Gets the feature constraints, represented as a {@link Map} from 
   * feature name (a {@link String}) to feature value (also a {@link String}). 
   * @return the featureConstraints
   */
  public List<Constraint> getConstraints() {
    return constraints;
  }


  /**
   * The annotation type for this query.
   */
  private String annotationType;
  
  /**
   * The constrains over the annotation features.
   */
  private List<Constraint> constraints;
  
  
  public String toString() {
    return "Annotation ( type = " + 
    annotationType + ", features=" + 
    (constraints != null ? constraints.toString() : "[]") +
    ")";
  }

}
