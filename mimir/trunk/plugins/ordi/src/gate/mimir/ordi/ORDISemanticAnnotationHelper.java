/*
 *  ORDISemanticAnnotationHelper.java
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
 *  Valentin Tablan, 20 Feb 2009
 *  
 *  $Id$
 */
package gate.mimir.ordi;

import static gate.mimir.ordi.ORDIUtils.HAS_LENGTH;
import static gate.mimir.ordi.ORDIUtils.HAS_MENTION;
import static gate.mimir.ordi.ORDIUtils.MIMIR_NAMESPACE;
import static gate.mimir.ordi.ORDIUtils.ORDI_CLIENT_COUNT_KEY;
import static gate.mimir.ordi.ORDIUtils.ORDI_INDEX_DIRNAME;
import static gate.mimir.ordi.ORDIUtils.ORDI_SOURCE_KEY;
import gate.Annotation;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.IndexConfig;
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;
import gate.util.GateRuntimeException;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.model.vocabulary.XMLSchema;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.evaluation.QueryBindingSet;
import org.openrdf.query.impl.DatasetImpl;
import org.openrdf.query.parser.sparql.SPARQLParser;

import com.ontotext.ordi.Factory;
import com.ontotext.ordi.IsolationLevel;
import com.ontotext.ordi.exception.ORDIException;
import com.ontotext.ordi.iterator.CloseableIterator;
import com.ontotext.ordi.tripleset.TConnection;
import com.ontotext.ordi.tripleset.TFactory;
import com.ontotext.ordi.tripleset.TSource;
import com.ontotext.ordi.tripleset.TStatement;
import com.ontotext.ordi.trree.TRREEAdapter;

/**
 * A helper for indexing annotations with support for:
 * <ul>
 * <li>nominal features: features that take values from a limited set of String
 * values.</li>
 * <li>numeric features: features that take values that can be represented as a
 * Double value). The only values accepted for these features are subclasses of
 * {@link Number}.</li>
 * <li>text features: features that have arbitrary string values.
 * </ul>
 * Nominal features are the least expensive to index and search, followed by the
 * numeric ones, with the text features being the most expensive.
 */
public class ORDISemanticAnnotationHelper extends
                                            AbstractSemanticAnnotationHelper {
  /**
   * Serialisation UID
   */
  private static final long serialVersionUID = -4644860283803183305L;

  /**
   * Flag for the static initialisation. Set to <code>true</code> the first time
   * the static initialisation procedure is completed.
   */
  private static boolean staticInitDone = false;

  /**
   * The URI for the ANNOTATION_TEMPLATE class
   */
  public static URI MIMIR_GRAPH_URI;

  /**
   * The NULL value used for RDF properties
   */
  public static URI MIMIR_NULL_URI;

  /**
   * The semantic constraint feature name for querying with sparql.
   */
  public static final String SEMANTIC_CONSTRAINT_FEATURE = "semanticConstraint";
  
  /**
   * The NULL value used for String datatype properties.
   */
  public static final String MIMIR_NULL_STRING = MIMIR_NAMESPACE + "NULL";

  /**
   * The NULL value used for numeric datatype properties.
   */
  public static final double MIMIR_NULL_DOUBLE = Double.MIN_VALUE;

  /**
   * The URI for the ANNOTATION_TEMPALTE class
   */
  public static URI ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI;

  /**
   * The URI for the ANNOTATION_TEMPALTE class
   */
  public static URI ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI;

  /**
   * The URI for the MENTION class
   */
  public static URI MENTION_CLASS_URI;

  /**
   * URI for the hasSpecialisation object property
   */
  public static URI HAS_SPECIALISATION_PROP_URI;

  /**
   * URI for the hasMention object property
   */
  public static URI HAS_MENTION_PROP_URI;

  /**
   * URI for the hasLength datatype property
   */
  public static URI HAS_LENGTH_PROP_URI;

  /**
   * Flag for the initialisation. Set to <code>true</code> after the
   * initialisation has completed.
   */
  private boolean initDone = false;

  
  /**
   * Constructs a new DefaultSemanticAnnotationHelper.
   * 
   * @param annotationType
   *          the type of the annotations handled by this helper.
   * @param nominalFeatureNames
   *          the names of the features to be indexed that have nominal values
   *          (i.e. values from a small set of values).
   * @param floatFeatureNames
   *          the names of the features to be indexed that have numeric values
   *          (i.e. values that can be converted to a double).
   * @param textFeatureNames
   *          the names of the features to be indexed that have arbitrary text
   *          values.
   * @param uriFeatureNames
   *          the names of the features to be indexed that have URIs as values.
   */
  public ORDISemanticAnnotationHelper(String annotationType,
          String[] nominalFeatureNames, String[] floatFeatureNames, 
          String[] textFeatureNames,
          String[] uriFeatureNames) {
    this(annotationType, nominalFeatureNames, null,
            floatFeatureNames, textFeatureNames, uriFeatureNames);
  }
  
  /**
   * Standard SAH constructor taking all feature types.  This helper does not
   * support integer features directly, they will be combined with the float
   * features.
   */
  public ORDISemanticAnnotationHelper(String annotationType,
          String[] nominalFeatureNames, String[] integerFeatureNames,
          String[] floatFeatureNames, String[] textFeatureNames,
          String[] uriFeatureNames) {
    super(annotationType, nominalFeatureNames, null, 
            concatenateArrays(integerFeatureNames, floatFeatureNames),
            textFeatureNames, uriFeatureNames);
    if(integerFeatureNames != null && integerFeatureNames.length > 0) {
      logger.warn("This helper types does not support integer features, " +
              "they will be indexed as floating point numbers!");
    }
    this.nominalFeaturePredicates = new URI[this.nominalFeatureNames.length];
    this.floatFeaturePredicates = new URI[this.floatFeatureNames.length];
    this.textFeaturePredicates = new URI[this.textFeatureNames.length];    
    this.uriFeaturePredicates = new URI[this.uriFeatureNames.length];
  }

  protected String buildSPARQLConstraint(String variableName, URI property,
          List<Constraint> constraints, boolean numeric) {
    StringBuilder query = new StringBuilder();
    if(numeric) {
      query.append("?" + variableName + " <" + property.stringValue() + "> ");
      // we mark the value as a variable
      String valueVar = "var" + sparqlVarUniqueId++;
      query.append("?" + valueVar + " .\n");
      // and append all constraints into an AND FILTER
      // start with non -NULL
      query.append("FILTER (?" + valueVar + " != " + MIMIR_NULL_DOUBLE);
      for(Constraint constraint : constraints) {
        query.append(" && ?" + valueVar);
        String valueLiteral =
                constraint.getValue() instanceof Number ? ((Number)constraint
                        .getValue()).toString() : "\""
                        + constraint.getValue().toString() + "\"";
        switch(constraint.getPredicate()){
          case EQ:
            query.append(" = " + valueLiteral);
            break;
          case GT:
            query.append(" > " + valueLiteral);
            break;
          case LT:
            query.append(" < " + valueLiteral);
            break;
          case GE:
            query.append(" >= " + valueLiteral);
            break;
          case LE:
            query.append(" <= " + valueLiteral);
            break;
          default:
            throw new IllegalArgumentException(
                    "Don't understand predicate type: "
                            + constraint.getPredicate() + "!");
        }
      }// for constraints
      // close the FILTER (...)
      query.append(") \n");
    } else {
      // non numeric constraints
      String valueVar = null;
      for(Constraint constraint : constraints) {
        if(constraint.getPredicate() == ConstraintType.EQ) {
          query.append("?" + variableName + " <" + property.stringValue()
                  + "> ");
          // it's an EQ predicate -> we can simply include it as a condition
          query.append("\"" + constraint.getValue().toString() + "\" .\n");
        } else {
          // we need to use FILTER
          // we mark the value as a variable
          if(valueVar == null) {
            valueVar = "var" + sparqlVarUniqueId++;
            query.append("?" + variableName + " <" + property.stringValue()
                    + "> ?" + valueVar + " .\n");
          }
          if(constraint.getPredicate() == ConstraintType.REGEX) {
            query.append("FILTER regex(?" + valueVar + ", ");
            if(constraint.getValue() instanceof String) {
              query.append("\"" + (String)constraint.getValue() + "\"");
            } else if(constraint.getValue() instanceof String[]) {
              query.append("\"" + ((String[])constraint.getValue())[0]
                      + "\", \"" + ((String[])constraint.getValue())[1] + "\"");
            }
            query.append(") \n");
          } else {
            String valueLiteral =
                    constraint.getValue() instanceof Number
                            ? ((Number)constraint.getValue()).toString()
                            : "\"" + constraint.getValue().toString() + "\"";
            query.append("FILTER (?" + valueVar);
            switch(constraint.getPredicate()){
              case EQ:
                query.append(" = " + valueLiteral);
                break;
              case GT:
                query.append(" > " + valueLiteral);
                break;
              case LT:
                query.append(" < " + valueLiteral);
                break;
              case GE:
                query.append(" >= " + valueLiteral);
                break;
              case LE:
                query.append(" <= " + valueLiteral);
                break;
              default:
                throw new IllegalArgumentException(
                        "Don't understand predicate type: "
                                + constraint.getPredicate() + "!");
            }
            // close the FILTER (...)
            query.append(") \n");
          }// not EQ, nor REGEX
        }
      }// for constraints
    }// non numeric
    // XXXXXXXXXXXXXXXXXXXXXXXXXXXXX OLD VERSION XXXXXXXXXXXXXXXXXXXXXXXXXXXX
    // for(Constraint constraint : constraints){
    // query.append("?" + variableName + " <" + property.stringValue() + "> ");
    //
    // if(constraint.getPredicate() == ConstraintType.EQ && !numeric){
    // //it's an EQ predicate -> we can simply include it as a condition
    // query.append("\"" + constraint.getValue().toString() + "\" .\n");
    // }else if(constraint.getPredicate() == ConstraintType.REGEX) {
    // query.append("FILTER regex(?annotationTemplateInstance,  ");
    // if(constraint.getValue() instanceof String){
    // query.append("\"" + (String)constraint.getValue() + "\"");
    // }else if(constraint.getValue() instanceof String[]){
    // query.append("\"" + ((String[])constraint.getValue())[0] +
    // "\", \"" + ((String[])constraint.getValue())[1] +
    // "\"");
    // }
    // query.append(") .\n");
    // }else{
    // //we mark the value as a variable
    // String valueVar = "var" + sparqlVarUniqueId++;
    // query.append("?" + valueVar + " .\n");
    // String valueLiteral = constraint.getValue() instanceof Number ?
    // ((Number)constraint.getValue()).toString() :
    // "\"" + constraint.getValue().toString() + "\"";
    // switch(constraint.getPredicate()){
    // case EQ:
    // query.append("FILTER (?" + valueVar + " = " + valueLiteral);
    // break;
    // case GT:
    // query.append("FILTER (?" + valueVar + " > " + valueLiteral);
    // break;
    // case LT:
    // query.append("FILTER (?" + valueVar + " < " + valueLiteral);
    // break;
    // case GE:
    // query.append("FILTER (?" + valueVar + " >= " + valueLiteral);
    // break;
    // case LE:
    // query.append("FILTER (?" + valueVar + " <= " + valueLiteral);
    // break;
    // default:
    // throw new IllegalArgumentException(
    // "Don't understand predicate type: " +
    // constraint.getPredicate() + "!");
    // }
    // query.append(") .\n");
    // }
    // }
    return query.toString();
  }

  public List<Mention> getMentions(String annotationType,
          List<Constraint> constraints, QueryEngine engine) {
    if(!initDone) init(engine);
    StringBuilder atQuery =
            new StringBuilder("?annotationTemplateInstance " + "<"
                    + RDF.TYPE.stringValue() + "> " + "<"
                    + annotationTemplateURILevel1 + "> .\n");
    for(int i = 0; i < nominalFeatureNames.length; i++) {
      String aFeatureName = nominalFeatureNames[i];
      List<Constraint> constraintsForThisFeature = new LinkedList<Constraint>();
      for(Constraint aConstraint : constraints) {
        if(aConstraint.getFeatureName().equals(aFeatureName)) {
          constraintsForThisFeature.add(aConstraint);
        }
      }
      if(constraintsForThisFeature.size() > 0) {
        // we have at least one constraint
        atQuery.append(buildSPARQLConstraint("annotationTemplateInstance",
                nominalFeaturePredicates[i], constraintsForThisFeature, false));
      }
    }
    boolean hasNonNominalConstraints = false;
    StringBuilder specQuery =
            new StringBuilder("?annotationTemplateInstance " + "<"
                    + HAS_SPECIALISATION_PROP_URI.stringValue() + "> "
                    + "?specATInstance .\n");
    for(int i = 0; i < floatFeatureNames.length; i++) {
      String aFeatureName = floatFeatureNames[i];
      List<Constraint> constraintsForThisFeature = new LinkedList<Constraint>();
      for(Constraint aConstraint : constraints) {
        if(aConstraint.getFeatureName().equals(aFeatureName)) {
          constraintsForThisFeature.add(aConstraint);
        }
      }
      if(constraintsForThisFeature.size() > 0) {
        // we have at elast one constraint
        hasNonNominalConstraints = true;
        specQuery.append(buildSPARQLConstraint("specATInstance",
                floatFeaturePredicates[i], constraintsForThisFeature, true));
      }
    }
    for(int i = 0; i < textFeatureNames.length; i++) {
      String aFeatureName = textFeatureNames[i];
      List<Constraint> constraintsForThisFeature = new LinkedList<Constraint>();
      for(Constraint aConstraint : constraints) {
        if(aConstraint.getFeatureName().equals(aFeatureName)) {
          constraintsForThisFeature.add(aConstraint);
        }
      }
      if(constraintsForThisFeature.size() > 0) {
        // we have at least one constraint
        hasNonNominalConstraints = true;
        specQuery.append(buildSPARQLConstraint("specATInstance",
                textFeaturePredicates[i], constraintsForThisFeature, false));
      }
    }
    for(int i = 0; i < uriFeatureNames.length; i++) {
      String aFeatureName = uriFeatureNames[i];
      for(Constraint aConstraint : constraints) {
        if(aConstraint.getFeatureName().equals(aFeatureName)) {
          // we have a constraint
          hasNonNominalConstraints = true;
          // URIs only support EQ predicate
          if(aConstraint.getPredicate() == ConstraintType.EQ) {
            specQuery.append("?specATInstance " + "<"
                    + uriFeaturePredicates[i].stringValue() + "> " + "<"
                    + aConstraint.getValue().toString() + "> .\n");
          } else {
            throw new IllegalArgumentException("Attempt to use a constraint of"
                    + "type " + aConstraint.getPredicate() + "." + "Feature \""
                    + aFeatureName + "\" is of type URI, so only \"equals\" "
                    + "constraints are supported.");
          }
        }
      }
    }
    for(Constraint sConstraint : constraints) {
      if(sConstraint.getFeatureName().equals(SEMANTIC_CONSTRAINT_FEATURE)) {
        specQuery.append("?specATInstance mimir:" + annotationType
                + "_hasinst ?inst . \n");
        hasNonNominalConstraints = true;
        String constraintValue = sConstraint.getValue().toString();
        if(!constraintValue.trim().endsWith(".")) constraintValue += " . ";
        if(!constraintValue.endsWith(" \n")) constraintValue += "\n";
        specQuery.append(constraintValue);
      }
    }
    // now build the top query
    StringBuilder query =
            new StringBuilder("PREFIX mimir: <" + MIMIR_NAMESPACE + ">\n");
    query.append("PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n");
    query.append("PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>\n");
    query.append("SELECT ?mentionInstance ?length \n");
    query.append("WHERE\n");
    query.append("{\n");
    if(hasNonNominalConstraints) {
      query.append(atQuery);
      // query.append(
      // "?annotationTemplateInstance <" +
      // HAS_SPECIALISATION_PROP_URI.stringValue() +
      // "> ?specATInstance .\n");
      query.append(specQuery);
      query.append("?specATInstance <" + HAS_MENTION_PROP_URI.stringValue()
              + "> ?mentionInstance .\n");
      query.append("?mentionInstance <" + HAS_LENGTH_PROP_URI.stringValue()
              + "> ?length .\n");
    } else {
      // no non-nominal constraints: just use the top level template
      query.append(atQuery);
      query.append("?annotationTemplateInstance <"
              + HAS_MENTION_PROP_URI.stringValue() + "> ?mentionInstance .\n");
      query.append("?mentionInstance <" + HAS_LENGTH_PROP_URI.stringValue()
              + "> ?length .\n");
    }
    query.append("}");
    logger.debug("About to execute query:\n" + query.toString());
    try {
      SPARQLParser parser = new SPARQLParser();
      TupleExpr tupleQuery =
              parser.parseQuery(query.toString(), MIMIR_NAMESPACE)
                      .getTupleExpr();
      QueryBindingSet bindings = new QueryBindingSet();
      CloseableIterator<? extends BindingSet> result =
              ordiConnection.evaluate(tupleQuery, bindings,
                      new DatasetImpl(), true, null);
      List<Mention> mentions = new ArrayList<Mention>();
      while(result.hasNext()) {
        BindingSet binding = result.next();
        String mentionURI =
                binding.getBinding("mentionInstance").getValue().stringValue();
        mentions.add(new Mention(mentionURI, ((Literal)binding.getBinding(
                "length").getValue()).intValue()));
      }
      return mentions;
    } catch(MalformedQueryException e) {
      throw new GateRuntimeException(
              "Error while generating SPARQL query. The generated query "
                      + "was: " + query.toString(), e);
    } catch(ORDIException e) {
      throw new GateRuntimeException("Problem accessing ORDI.", e);
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * gate.mimir.SemanticAnnotationHelper#getMentionUri(gate.Annotation
   * , int, gate.mimir.index.Indexer)
   */
  public String[] getMentionUris(Annotation annotation, int length,
          Indexer indexer) {
    // lazy init
    if(!initDone) {
      init(indexer);
    }
    try {
      URI[] mentionURIs = uriCache.getMentionURIs(annotation, length);
      String[] res = new String[mentionURIs.length];
      for(int i = 0; i < mentionURIs.length; i++) {
        res[i] = mentionURIs[i].stringValue();
      }
      return res;
    } catch(ORDIException e) {
      throw new GateRuntimeException("Problem while accessing ORDI.", e);
    } catch(IndexException e) {
      // we could not find a mention URI ->just skip this annotation
      logger.error("Could not create mention URI for annotation", e);
      return new String[]{};
    }
  }

  /**
   * Initialises this semantic annotation helper.
   * 
   * @throws ORDIException
   */
  public void init(Indexer indexer){
    if(initDone) return;
    logger.warn("The ORDI Semantic Annotation Helper is now deprecated and " +
    		"should only be used for opening old indexes.\nPlease use the new " +
    		"Sesame Semantic Annotation Helper instead for building new indexes!");
    try {
      TSource ordiSource = getOrdiSource(indexer.getIndexConfig());
      ordiConnection = ordiSource.getConnection();
      ordiConnection.setTransactionIsolationLevel(
              IsolationLevel.TRANSACTION_READ_COMMITTED);
      ordiFactory = ordiSource.getTriplesetFactory();
      parser = new SPARQLParser();
      // ensure static initialization is done.
      if(!staticInitDone) staticInit(ordiConnection, ordiFactory);
      this.uriCache = new URICache(this);
      docsSoFar = 0;
      initCommon();
    } catch(ORDIException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Initialises this semantic annotation helper.
   * 
   * @throws ORDIException
   */
  public void init(QueryEngine qEngine){
    if(initDone) return;
    try{
      TSource ordiSource = getOrdiSource(qEngine.getIndexConfig());
      ordiConnection = ordiSource.getConnection();
      ordiConnection.setTransactionIsolationLevel(
              IsolationLevel.TRANSACTION_READ_COMMITTED);
      ordiFactory = ordiSource.getTriplesetFactory();
      parser = new SPARQLParser();
      // ensure static initialization is done.
      if(!staticInitDone) staticInit(ordiConnection, ordiFactory);
      initCommon();
    } catch(ORDIException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Gets the current ORDI Triple source from the current index config context 
   * (whether inside an index or a query engine). If none currently exists, one 
   * is created, saved in the context, and then returned.
   * 
   * @param context
   * @return
   */
  protected TSource getOrdiSource(IndexConfig config) {
    TSource ordiSource = (TSource)config.getContext().get(ORDI_SOURCE_KEY);
    if(ordiSource == null) {
      // not initialised yet - > we create it and save it ourselves.
      File topDir = config.getIndexDirectory();
      Map<Object, Object> ordiConfig = new HashMap<Object, Object>();
      // set the default triple source
      ordiConfig.put(TSource.class.getName(),
              "com.ontotext.ordi.trree.TRREEAdapter");
      // set the storage directory
      File ordiIndexDir = new File(topDir, ORDI_INDEX_DIRNAME);
      ordiIndexDir.mkdirs();
      ordiConfig.put(TRREEAdapter.PARAM_STORAGE_DIRECTORY,
              ordiIndexDir.getAbsolutePath());
      // set storage mode to file
      ordiConfig.put(TRREEAdapter.PARAM_REPOSITORY_TYPE, "file");
      // set persistency ON
      ordiConfig.put("keep-persisted-data", "true");
      // enable RDFS optimisation
      ordiConfig.put(TRREEAdapter.PARAM_PARTIALRDFS, "true");
      // do not use embedded full text search
      ordiConfig.put(TRREEAdapter.PARAM_FTS_INDEX_POLICY, "never");
      // cache size -> default is 4096
      ordiConfig.put(TRREEAdapter.PARAM_CACHE_SIZE, "2048");
      // entity index size -> default is 10E+6, i.e. 10 million
      ordiConfig.put(TRREEAdapter.PARAM_ENTITY_INDEX_SIZE, "1000000");
      // //use OWL semantics
      // ordiConfig.put("ruleset", "owl-horst");
      // //use RDFS semantics
      // ordiConfig.put("ruleset", "rdfs");
      // use no semantics
      ordiConfig.put("ruleset", "empty");
      ordiSource = Factory.createTSource(ordiConfig);
      config.getContext().put(ORDI_SOURCE_KEY, ordiSource);
      config.getContext().put(ORDI_CLIENT_COUNT_KEY, 1);
    } else {
      int clientCount = (Integer)config.getContext().get(ORDI_CLIENT_COUNT_KEY);
      config.getContext().put(ORDI_CLIENT_COUNT_KEY, 
              Integer.valueOf(clientCount + 1));
    }
    return ordiSource;
  }
  
  public void close(Indexer indexer){
    if(initDone) {
      logger.info("Closing ORDI connection");
      try {
        ordiConnection.close();
      } catch(ORDIException e) {
        logger.error("Error while closing the ORDI connection", e);
      }
      ordiConnection = null;
      ordiFactory = null;
      parser = null;
      initDone = false;
      // if we're the last helper, shutdown the ORDI source
      int clientCount = (Integer)indexer.getIndexConfig().getContext().get(
              ORDI_CLIENT_COUNT_KEY);
      clientCount--;
      indexer.getIndexConfig().getContext().put(ORDI_CLIENT_COUNT_KEY, 
              Integer.valueOf(clientCount));
      if(clientCount == 0){
        // shutting down the ORDI Source
        TSource ordiSource = (TSource) indexer.getIndexConfig().getContext().get(
                ORDI_SOURCE_KEY);
        if(ordiSource != null) {
          logger.info("Shutting down ORDI TSource");
          ordiSource.shutdown();
          indexer.getIndexConfig().getContext().remove(ORDI_SOURCE_KEY);
        }      
      }      
    }
  }

  public void close(QueryEngine qEngine){
    if(initDone) {
      logger.info("Closing ORDI connection");
      try {
        ordiConnection.close();
      } catch(ORDIException e) {
        logger.error("Error while closing the ORDI connection", e);
      }
      ordiConnection = null;
      ordiFactory = null;
      parser = null;
      initDone = false;
      // if we're the last helper, shutdown the ORDI source
      int clientCount = (Integer)qEngine.getIndexConfig().getContext().get(
              ORDI_CLIENT_COUNT_KEY);
      clientCount--;
      qEngine.getIndexConfig().getContext().put(ORDI_CLIENT_COUNT_KEY, 
              Integer.valueOf(clientCount));
      if(clientCount == 0){
        // shutting down the ORDI Source
        TSource ordiSource = (TSource) qEngine.getIndexConfig().getContext().get(
                ORDI_SOURCE_KEY);
        if(ordiSource != null) {
          logger.info("Shutting down ORDI TSource");
          ordiSource.shutdown();
          qEngine.getIndexConfig().getContext().remove(ORDI_SOURCE_KEY);
        }      
      } 
    }
  }  
  
  /**
   * Runs the initialisation routines common to indexing and searching.
   * 
   * @throws ORDIException
   */
  protected void initCommon() throws ORDIException {
    // create all ontology resources required by *this*
    // DefaultSemanticAnnotationHelper.
    // create the ANNOTATION_TEMPLATE sub-classes
    annotationTemplateURILevel1 =
            ordiFactory.createURI(MIMIR_NAMESPACE, annotationType + "L1");
    createClass(annotationTemplateURILevel1, ordiConnection, ordiFactory);
    ordiConnection.addStatement(annotationTemplateURILevel1, RDFS.SUBCLASSOF,
            ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI, MIMIR_GRAPH_URI);
    annotationTemplateURILevel2 =
            ordiFactory.createURI(MIMIR_NAMESPACE, annotationType + "L2");
    createClass(annotationTemplateURILevel2, ordiConnection, ordiFactory);
    ordiConnection.addStatement(annotationTemplateURILevel2, RDFS.SUBCLASSOF,
            ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI, MIMIR_GRAPH_URI);
    // define the required properties for the nominal features
    for(int i = 0; i < nominalFeatureNames.length; i++) {
      nominalFeaturePredicates[i] =
              ordiFactory.createURI(MIMIR_NAMESPACE, annotationType + "_has"
                      + nominalFeatureNames[i]);
      createDatatypeProperty(nominalFeaturePredicates[i],
              annotationTemplateURILevel1, XMLSchema.STRING, ordiConnection,
              ordiFactory);
    }
    // define the required properties for the numeric features
    for(int i = 0; i < floatFeatureNames.length; i++) {
      floatFeaturePredicates[i] =
              ordiFactory.createURI(MIMIR_NAMESPACE, annotationType + "_has"
                      + floatFeatureNames[i]);
      createDatatypeProperty(floatFeaturePredicates[i],
              annotationTemplateURILevel2, XMLSchema.DOUBLE, ordiConnection,
              ordiFactory);
    }
    // define the required properties for the text features
    for(int i = 0; i < textFeatureNames.length; i++) {
      textFeaturePredicates[i] =
              ordiFactory.createURI(MIMIR_NAMESPACE, annotationType + "+has"
                      + textFeatureNames[i]);
      createDatatypeProperty(textFeaturePredicates[i],
              annotationTemplateURILevel2, XMLSchema.STRING, ordiConnection,
              ordiFactory);
    }
    // define the required properties for the URI features
    for(int i = 0; i < uriFeatureNames.length; i++) {
      uriFeaturePredicates[i] =
              ordiFactory.createURI(MIMIR_NAMESPACE, annotationType + "_has"
                      + uriFeatureNames[i]);
      createRDFProperty(uriFeaturePredicates[i], ordiConnection, ordiFactory);
    }
    // prepare the value that will be returned as the uriFeatureNames we report
    // to other clients.
    if(Arrays.asList(uriFeatureNames).contains(SEMANTIC_CONSTRAINT_FEATURE)) {
      uriFeatureNamesPlusSemanticConstraint = uriFeatureNames;
    }
    else {
      uriFeatureNamesPlusSemanticConstraint = new String[uriFeatureNames.length + 1];
      System.arraycopy(uriFeatureNames, 0, uriFeatureNamesPlusSemanticConstraint, 0, uriFeatureNames.length);
      uriFeatureNamesPlusSemanticConstraint[uriFeatureNames.length] = SEMANTIC_CONSTRAINT_FEATURE;
    }
    initDone = true;
  }

  protected static void staticInit(TConnection ordiConnection,
          TFactory ordiFactory) throws ORDIException {
    if(staticInitDone) return;
    percentFormat = NumberFormat.getPercentInstance();
    percentFormat.setMinimumFractionDigits(2);
    // create the ontology resources required by all instances of
    // DefaultSemanticAnnotationHelper
    // create the MIMIR graph
    MIMIR_GRAPH_URI = ordiFactory.createURI(MIMIR_NAMESPACE, "graph");
    MIMIR_NULL_URI = ordiFactory.createURI(MIMIR_NAMESPACE, "NULL");
    // create ANNOTATION_TEMPLATE classes
    ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI =
            ordiFactory.createURI(MIMIR_NAMESPACE, "AnnotationTemplateL1");
    createClass(ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI, ordiConnection,
            ordiFactory);
    ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI =
            ordiFactory.createURI(MIMIR_NAMESPACE, "AnnotationTemplateL2");
    createClass(ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI, ordiConnection,
            ordiFactory);
    // define hasSpecialisation property
    HAS_SPECIALISATION_PROP_URI =
            ordiFactory.createURI(MIMIR_NAMESPACE, "hasSpecialisation");
    ordiConnection.addStatement(HAS_SPECIALISATION_PROP_URI, RDF.TYPE,
            OWL.OBJECTPROPERTY, MIMIR_GRAPH_URI);
    ordiConnection.addStatement(HAS_SPECIALISATION_PROP_URI, RDFS.DOMAIN,
            ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI, MIMIR_GRAPH_URI);
    ordiConnection.addStatement(HAS_SPECIALISATION_PROP_URI, RDFS.RANGE,
            ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI, MIMIR_GRAPH_URI);
    // create MENTION class
    MENTION_CLASS_URI = ordiFactory.createURI(MIMIR_NAMESPACE, "Mention");
    createClass(MENTION_CLASS_URI, ordiConnection, ordiFactory);
    // add the ANNOTATION_TEMPLATE hasMention MENTION triple.
    HAS_MENTION_PROP_URI = ordiFactory.createURI(MIMIR_NAMESPACE, HAS_MENTION);
    ordiConnection.addStatement(HAS_MENTION_PROP_URI, RDF.TYPE,
            OWL.OBJECTPROPERTY, MIMIR_GRAPH_URI);
    ordiConnection.addStatement(HAS_MENTION_PROP_URI, RDFS.DOMAIN,
            ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI, MIMIR_GRAPH_URI);
    ordiConnection.addStatement(HAS_MENTION_PROP_URI, RDFS.RANGE,
            MENTION_CLASS_URI, MIMIR_GRAPH_URI);
    // add MENTION hasLength property
    HAS_LENGTH_PROP_URI = ordiFactory.createURI(MIMIR_NAMESPACE, HAS_LENGTH);
    ordiConnection.addStatement(HAS_LENGTH_PROP_URI, RDF.TYPE,
            OWL.DATATYPEPROPERTY, MIMIR_GRAPH_URI);
    ordiConnection.addStatement(HAS_LENGTH_PROP_URI, RDFS.DOMAIN,
            MENTION_CLASS_URI, MIMIR_GRAPH_URI);
    ordiConnection.addStatement(HAS_LENGTH_PROP_URI, RDFS.RANGE, XMLSchema.INT,
            MIMIR_GRAPH_URI);
    staticInitDone = true;
  }

  /**
   * Creates a new ontology class with a given URI.
   * 
   * @param classURI
   *          the URI for the new class
   * @param ordiConnection
   *          a connection to ORDI.
   * @param ordiFactory
   *          an ORDI triples factory.
   * @return <code>true</code> if the new class was created successfully, or
   *         <code>false</code> if the class was already present.
   * @throws ORDIException
   *           if there are problems while creating the class.
   */
  protected static boolean createClass(URI classURI,
          TConnection ordiConnection, TFactory ordiFactory)
          throws ORDIException {
    // check if the class exists already
    CloseableIterator<? extends TStatement> result =
            ordiConnection.search(classURI, RDF.TYPE, OWL.CLASS,
                    MIMIR_GRAPH_URI, null);
    if(result.hasNext()) {
      // class already exists
      return false;
    }
    // else, create new class
    TStatement statement =
            ordiConnection.addStatement(classURI, RDF.TYPE, OWL.CLASS,
                    MIMIR_GRAPH_URI);
    return statement != null;
  }

  /**
   * Creates a new datatype property.
   * 
   * @param propertyURI
   *          the URI for the new property
   * @param propertyRange
   *          the URI for the property value type.
   * @param ordiConnection
   *          a connection to ORDI.
   * @param ordiFactory
   *          an ORDI triples factory.
   * @return <code>true</code> if the new property was created successfully, or
   *         <code>false</code> if the property was already present.
   * @throws ORDIException
   *           if there are problems while creating the class.
   */
  protected static boolean createDatatypeProperty(URI propertyURI,
          URI propertyDomain, URI propertyRange, TConnection ordiConnection,
          TFactory ordiFactory) throws ORDIException {
    // check if the property exists already
    CloseableIterator<? extends TStatement> result =
            ordiConnection.search(propertyURI, RDF.TYPE, OWL.DATATYPEPROPERTY,
                    MIMIR_GRAPH_URI, null);
    if(result.hasNext()) {
      // property already exists
      return false;
    }
    // else, create new property
    TStatement statement =
            ordiConnection.addStatement(propertyURI, RDF.TYPE,
                    OWL.DATATYPEPROPERTY, MIMIR_GRAPH_URI);
    ordiConnection.addStatement(propertyURI, RDFS.RANGE, propertyRange,
            MIMIR_GRAPH_URI);
    ordiConnection.addStatement(propertyURI, RDFS.DOMAIN, propertyDomain,
            MIMIR_GRAPH_URI);
    return statement != null;
  }

  /**
   * Creates a new plain RDF property.
   * 
   * @param propertyURI
   *          the URI for the new property
   * @param ordiConnection
   *          a connection to ORDI.
   * @param ordiFactory
   *          an ORDI triples factory.
   * @return <code>true</code> if the new property was created successfully, or
   *         <code>false</code> if the property was already present.
   * @throws ORDIException
   *           if there are problems while creating the class.
   */
  protected static boolean createRDFProperty(URI propertyURI,
          TConnection ordiConnection, TFactory ordiFactory)
          throws ORDIException {
    // check if the property exists already
    CloseableIterator<? extends TStatement> result =
            ordiConnection.search(propertyURI, RDF.TYPE, RDF.PROPERTY,
                    MIMIR_GRAPH_URI, null);
    if(result.hasNext()) {
      // property already exists
      return false;
    }
    // else, create new property
    TStatement statement =
            ordiConnection.addStatement(propertyURI, RDF.TYPE, RDF.PROPERTY,
                    MIMIR_GRAPH_URI);
    return statement != null;
  }

  /**
   * The URI for the level 1 annotation template ontology class corresponding to
   * the current annotation type.
   */
  protected URI annotationTemplateURILevel1;

  /**
   * The URI for the level 2 annotation template ontology class corresponding to
   * the current annotation type.
   */
  protected URI annotationTemplateURILevel2;

  /**
   * The connection to ORDI.
   */
  protected transient TConnection ordiConnection;

  /**
   * Parser used for SPARQL queries.
   */
  protected transient SPARQLParser parser;

  /**
   * The ORDI triple factory.
   */
  protected transient TFactory ordiFactory;

  /**
   * Used to generate unique URIs for annotation template instances.
   */
  protected long atInstanceUniqueId = 0;

  /**
   * Used to generate unique URIs for annotation template specialisation
   * instances.
   */
  protected long atInstanceSpecUniqueId = 0;

  /**
   * Used to generate unique URIs for mention instances.
   */
  protected long mentionUniqueId = 0;

  /**
   * Used to generate unique variable names in SPARQL queries.
   */
  protected int sparqlVarUniqueId = 0;

  /**
   * The URIs for the nominal feature predicates.
   */
  protected URI[] nominalFeaturePredicates;

  /**
   * the URIs for the numeric feature predicates.
   */
  protected URI[] floatFeaturePredicates;

  /**
   * The URIs for the text feature predicates.
   */
  protected URI[] textFeaturePredicates;

  /**
   * The URIs for the URI feature predicates.
   */
  protected URI[] uriFeaturePredicates;

  /**
   * A cache for annotation template URIs.
   */
  protected transient URICache uriCache;
  
  protected transient String[] uriFeatureNamesPlusSemanticConstraint;

  private static NumberFormat percentFormat;

  /**
   * The number of documents indexed so far
   */
  protected int docsSoFar;

  private static final Logger logger =
          Logger.getLogger(ORDISemanticAnnotationHelper.class);

  /*
   * (non-Javadoc)
   * 
   * @see
   * gate.mimir.index.ordi.AbstractSemanticAnnotationHelper#documentEnd
   * ()
   */
  @Override
  public void documentEnd() {
    if(uriCache != null) {
      double l1ratio = uriCache.getL1CacheHitRatio();
      double l2ratio = uriCache.getL2CacheHitRatio();
      double l3ratio = uriCache.getL3CacheHitRatio();
      double ordiL1ratio = uriCache.getOrdiL1CacheHitRatio();
      double ordiL2ratio = uriCache.getOrdiL2CacheHitRatio();
      double ordiL3ratio = uriCache.getOrdiL3CacheHitRatio();
      logger.debug("Cache size("
              + annotationType
              + "):"
              + uriCache.size()
              + ". Hit ratios L1, L2, L3: "
              + (Double.isNaN(l1ratio) ? "N/A" : percentFormat.format(l1ratio))
              + ", "
              + (Double.isNaN(l2ratio) ? "N/A" : percentFormat.format(l2ratio))
              + ", "
              + (Double.isNaN(l3ratio) ? "N/A" : percentFormat.format(l3ratio))
              + "\nOrdi hit ratios L1, L2, L3: "
              + (Double.isNaN(ordiL1ratio) ? "N/A" : percentFormat
                      .format(ordiL1ratio))
              + ", "
              + (Double.isNaN(ordiL2ratio) ? "N/A" : percentFormat
                      .format(ordiL2ratio))
              + ", "
              + (Double.isNaN(ordiL3ratio) ? "N/A" : percentFormat
                      .format(ordiL3ratio)));
      docsSoFar++;
      if(docsSoFar % 200 == 0) {
        // every 200 docs, adjust the cache sizes
        uriCache.adjustCacheSizes();
      }
    } else {
      logger.debug("Cache size(" + annotationType + "): null");
    }
  }

  @Override
  public String[] getUriFeatureNames() {
    // TODO Auto-generated method stub
    return uriFeatureNamesPlusSemanticConstraint;
  }
  
  
}
