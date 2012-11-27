/*
 *  AnnotationTermQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *  
 *  Valentin Tablan, 13 Jul 2012
 *
 *  $Id$
 */
package gate.mimir.search.terms;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.log4j.Logger;

import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.AnnotationQuery;

/**
 * Given an {@link AnnotationQuery}, this finds the set of terms that satisfy 
 * it.
 */
public class AnnotationTermsQuery extends AbstractTermsQuery {
  
  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 777418229209857720L;

  public AnnotationTermsQuery(AnnotationQuery annotationQuery, 
      boolean countsEnabled, int limit) {
    super(limit);
    this.annotationQuery = annotationQuery;
  }
  
  public AnnotationTermsQuery(AnnotationQuery annotationQuery) {
    super();
    this.annotationQuery = annotationQuery;
  }

  protected AnnotationQuery annotationQuery;
  
  private static final Logger logger = Logger.getLogger(AnnotationTermsQuery.class);
  
  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermQuery#execute()
   */
  @Override
  public TermsResultSet execute(QueryEngine engine) {
    // find the semantic annotation helper for the right annotation type
    SemanticAnnotationHelper helper = 
        engine.getAnnotationHelper(annotationQuery);
    // ask the helper for the mentions that correspond to this query
    long start = System.currentTimeMillis();      
    List<Mention> mentions = helper.getMentions(
        annotationQuery.getAnnotationType(),
        annotationQuery.getConstraints(), engine);
    logger.debug(mentions.size() + " mentions obtained in " + 
      (System.currentTimeMillis() - start) + " ms");
  
    if(mentions.size() > 0) {
      String[] terms = new String[mentions.size()];
      int[] lengths = new int[mentions.size()];
      int index = 0;
      for(Mention m : mentions) {
        terms[index] = m.getUri();
        lengths[index] = m.getLength();
        index++;
      }
      return new TermsResultSet(terms, lengths, null);
    } else {
      return TermsResultSet.EMPTY;
    }
  }

  /**
   * This type of terms query does not use the index, all the results are 
   * obtained from the semantic annotation helpers. Because of this, counts are
   * not available: this method always returns <code>false</code>.
   */
  @Override
  public boolean isCountsEnabled() {
    return false;
  }
  
  
}
