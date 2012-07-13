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

import it.unimi.dsi.big.mg4j.index.BitStreamIndex;
import it.unimi.dsi.big.mg4j.index.Index;
import it.unimi.dsi.big.util.StringMap;
import it.unimi.dsi.lang.MutableString;

import java.util.List;

import org.apache.log4j.Logger;

import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.AnnotationQuery;
import gate.mimir.search.query.OrQuery;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.TermQuery;

/**
 * Given an {@link AnnotationQuery}, this finds the set of terms that satisfy 
 * it.
 */
public class AnnotationTermQuery extends AbstractTermsQuery {
  
  public AnnotationTermQuery(boolean idsEnabled, boolean stringsEnabled,
                             boolean countsEnabled,
                             AnnotationQuery annotationQuery) {
    super(idsEnabled, stringsEnabled, countsEnabled);
    this.annotationQuery = annotationQuery;
  }

  public AnnotationTermQuery(AnnotationQuery annotationQuery, QueryEngine engine) {
    super();
    this.annotationQuery = annotationQuery;
    this.engine = engine;
  }

  protected AnnotationQuery annotationQuery;
  
  protected QueryEngine engine;
  
  private static final Logger logger = Logger.getLogger(AnnotationTermQuery.class);
  
  /* (non-Javadoc)
   * @see gate.mimir.search.terms.TermQuery#execute()
   */
  @Override
  public TermsResultSet execute() {
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
    StringMap<? extends CharSequence> termMap = null;
    Index mg4jIndex = engine.getAnnotationIndex(
      annotationQuery.getAnnotationType()).getIndex();
    if(mg4jIndex instanceof BitStreamIndex) {
      termMap = ((BitStreamIndex)mg4jIndex).termMap;
    } else {
      // this indicates major changes in the underlying MG4J implementation
      throw new IllegalStateException(
        "Underlying MG4J index is not bitstream based!");
    }
  
    if(mentions.size() > 0) {
      long[] termIds = new long[mentions.size()];
      String[] terms = new String[mentions.size()];
      int[] lengths = new int[mentions.size()];
      int index = 0;
      for(Mention m : mentions) {
        terms[index] = m.getUri();
        lengths[index] = m.getLength();
        // find the term ID
        //use the term processor for the query term
        MutableString mutableString = new MutableString(m.getUri());
        mg4jIndex.termProcessor.processTerm(mutableString);
        // TODO use toString() if not working
        termIds[index] = termMap.getLong( mutableString);
        index++;
      }
      return new TermsResultSet(termIds, terms, lengths, null);
    } else {
      return TermsResultSet.EMPTY;
    }
  }
}
