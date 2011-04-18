/*
 *  ORDIUtils.java
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
 *  Valentin Tablan, 26 Feb 2009
 *  
 *  $Id$
 */
package gate.mimir.ordi;

import gate.mimir.IndexConfig;

import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.parser.sparql.SPARQLParser;
import org.openrdf.query.parser.sparql.SPARQLUtil;

/**
 * A set of constants for ORDI.
 */
public abstract class ORDIUtils {
  /**
   * The namespace used for Mimir-specific URIs.
   */
  public static final String MIMIR_NAMESPACE = "urn:mimir:";

  /**
   * Predicate string.
   */
  public static final String HAS_FEATURES = "hasFeatures";

  /**
   * Predicate string.
   */
  public static final String HAS_MENTION = "hasMention";

  /**
   * Predicate string.
   */
  public static final String HAS_LENGTH = "hasLength";

  /**
   * Key used to retrieve the ORDI triple source from the 
   * {@link IndexConfig#getContext()} context map.
   */
  public static final String ORDI_SOURCE_KEY = "ordiSource";

  /**
   * Key used to retrieve the an Integer value, representing the number of
   * ORDI clients (how many times the creation of the ORDI source was attempted).
   * This is used when closing the ORDI clients, so that the last one can also
   * shutdown the ORDI source.
   */
  public static final String ORDI_CLIENT_COUNT_KEY = "ordiSourceClientCount";
  
  /**
   * The name of the index subdirectory storing ORDI data.
   */
  public static final String ORDI_INDEX_DIRNAME = "ordi";
  
  /**
   * Class name.
   */
  public static final String ANNOTATION_TEMPLATE = "AnnotationTemplate";

  public static TupleExpr getAnnotationEntityQuery(URI annotationClass,
          String annotationFeatures) {
    StringBuilder query =
            new StringBuilder("PREFIX mimir: <" + MIMIR_NAMESPACE + ">\n");
    query.append("SELECT ?entity\n");
    query.append("WHERE {\n");
    query.append("  ?entity <" + RDF.TYPE.stringValue() + "> <"
            + annotationClass.stringValue() + "> ;\n");
    query.append("          mimir:" + HAS_FEATURES + " \""
            + SPARQLUtil.encodeString(annotationFeatures) + "\" .\n");
    query.append("}");
    SPARQLParser parser = new SPARQLParser();
    try {
      return parser.parseQuery(query.toString(), MIMIR_NAMESPACE)
              .getTupleExpr();
    } catch(MalformedQueryException e) {
      // if this failed something is very wrong...
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Returns a parsed SPARQL query that will return all the entities
   * corresponding to a given annotation type. If includeFeatures is true, the
   * result set will also include the feature strings for those entities, which
   * can be used for later filtering.
   * 
   * @param annotationClass
   *          URI corresponding to the annotation type.
   * @param includeFeatures
   *          should we include the feature strings as a second column in the
   *          result set?
   */
  public static TupleExpr getEntitiesQuery(URI annotationClass,
          boolean includeFeatures) {
    StringBuilder query =
            new StringBuilder("PREFIX mimir: <" + MIMIR_NAMESPACE + ">\n");
    query.append("SELECT ?entity");
    if(includeFeatures) {
      query.append(" ?features");
    }
    query.append("\n");
    query.append("WHERE {\n");
    query.append("  ?entity <" + RDF.TYPE.stringValue() + "> <"
            + annotationClass.stringValue() + ">");
    if(includeFeatures) {
      query.append(" ;\n");
      query.append("      mimir:" + HAS_FEATURES + " ?features");
    }
    query.append(" .\n");
    query.append("}");
    SPARQLParser parser = new SPARQLParser();
    try {
      return parser.parseQuery(query.toString(), MIMIR_NAMESPACE)
              .getTupleExpr();
    } catch(MalformedQueryException e) {
      // if this failed something is very wrong...
      throw new IllegalArgumentException(e);
    }
  }
}
