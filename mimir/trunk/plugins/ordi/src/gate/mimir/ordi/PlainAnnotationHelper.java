/*
 * Copyright (c) 1998-2009, The University of Sheffield.
 * 
 * Valentin Tablan, 26 Feb 2009
 * 
 * $Id$
 */
package gate.mimir.ordi;

import static gate.mimir.ordi.ORDIUtils.ORDI_CLIENT_COUNT_KEY;
import static gate.mimir.ordi.ORDIUtils.ORDI_INDEX_DIRNAME;
import static gate.mimir.ordi.ORDIUtils.ORDI_SOURCE_KEY;
import gate.Annotation;
import gate.Document;
import gate.FeatureMap;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.IndexConfig;
import gate.mimir.index.Indexer;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;
import gate.util.GateRuntimeException;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.BindingSet;
import org.openrdf.query.algebra.evaluation.QueryBindingSet;
import org.openrdf.query.impl.DatasetImpl;

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
 * A helper for plain annotations. This only stores in the semantic repository:
 * <ul>
 * <li>the annotation type,</li>
 * <li>the feature values, for a given set of features, and</li>
 * <li>the annotation length</li>
 * </ul>
 * For each unique set of such values, it will create a <b>single</b> mention
 * URI which is associated with all of the annotations that match those values.
 * For example, if we only only need to store the <em>type</em> feature, then
 * all annotations of type <tt>Measurement</tt>, with <tt>type=scalarValue</tt>
 * and a length of <tt>2</tt> will be associated with the same mention URI. This
 * class stores no document-related state, so it is safe to use from multiple
 * threads.
 * 
 * @deprecated Please use {@link ORDISemanticAnnotationHelper} instead!
 */
@Deprecated
public class PlainAnnotationHelper extends AbstractSemanticAnnotationHelper {
  /**
   * 
   */
  private static final long serialVersionUID = 6460345906912558378L;

  protected static long uniqueId = 0;

  public static final int COMMIT_INTERVAL = 5000;

  /**
   * The namespace used for Mimir annotation URIs.
   */
  public static final String ANNOTATION_NAMESPACE = ORDIUtils.MIMIR_NAMESPACE
          + "annotation:";

  /**
   * How many calls to getMentionURI until we next try and commit.
   */
  protected int callsUntilNextCommit = COMMIT_INTERVAL;

  /**
   * URI representing the mimir:hasMention predicate.
   */
  protected URI hasMentionPredicate;

  /**
   * URI representing the mimir:hasFeatures predicate.
   */
  protected URI hasFeaturesPredicate;

  /**
   * URI representing the mimir:hasLength predicate.
   */
  protected URI hasLengthPredicate;

  /**
   * The URI for the named graph used by Mimir.
   */
  protected URI mimirGraphURI;

  /**
   * Creates a new {@link PlainAnnotationHelper}.
   * 
   * @param featureNames
   *          the names of features that should be stored.
   * @param indexer
   *          the {@link Indexer} this helper belongs to.
   */
  public PlainAnnotationHelper(String... featureNames) {
    super(null, featureNames, null, null, null, null);
    this.nominalFeatureNames = featureNames;
  }

  public void init(Indexer indexer) {
    try {
      TSource ordiSource = getOrdiSource(indexer.getIndexConfig());
      ordiConnection = ordiSource.getConnection();
      ordiConnection.setTransactionIsolationLevel(
              IsolationLevel.TRANSACTION_READ_COMMITTED);
      ordiFactory = ordiSource.getTriplesetFactory();
      initURIs();
    } catch(ORDIException e) {
      throw new RuntimeException(e);
    }
  }

  public void init(QueryEngine qEngine) {
    TSource ordiSource = getOrdiSource(qEngine.getIndexConfig());
    ordiConnection = ordiSource.getConnection();
    ordiFactory = ordiSource.getTriplesetFactory();
    initURIs();
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
      ordiConfig.put(TRREEAdapter.PARAM_CACHE_SIZE, "8192");
      // entity index size -> default is 10E+6, i.e. 10 million
      ordiConfig.put(TRREEAdapter.PARAM_ENTITY_INDEX_SIZE, "20000000");
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

  private void initURIs() {
    hasMentionPredicate =
            ordiFactory.createURI(ORDIUtils.MIMIR_NAMESPACE,
                    ORDIUtils.HAS_MENTION);
    hasFeaturesPredicate =
            ordiFactory.createURI(ORDIUtils.MIMIR_NAMESPACE,
                    ORDIUtils.HAS_FEATURES);
    hasLengthPredicate =
            ordiFactory.createURI(ORDIUtils.MIMIR_NAMESPACE,
                    ORDIUtils.HAS_LENGTH);
    mimirGraphURI = ordiFactory.createURI(ORDIUtils.MIMIR_NAMESPACE, "graph");
  }


  /**
   * The connection to ORDI.
   */
  protected transient TConnection ordiConnection;

  /**
   * The ORDI Triples factory.
   */
  protected transient TFactory ordiFactory;

  /**
   * @return the features
   */
  public String[] getFeatures() {
    return nominalFeatureNames;
  }

  /*
   * (non-Javadoc)
   * 
   * @see gate.mimir.SemanticAnnotationHelper#documentEnd()
   */
  public void documentEnd() {
  }

  /*
   * (non-Javadoc)
   * 
   * @see gate.mimir.SemanticAnnotationHelper#documentStart(gate.Document)
   */
  public void documentStart(Document document) {
  }

  public void close(Indexer indexer){
    logger.info("Committing ORDI data");
    try {
      ordiConnection.commit();
    } catch(ORDIException e) {
      logger.error("Error while committing ORDI data", e);
    }
    logger.info("Closing ORDI connection");
    try {
      ordiConnection.close();
    } catch(ORDIException e) {
      logger.error("Error while closing ORDI connection", e);
    }
    ordiConnection = null;
    ordiFactory = null;
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

  public void close(QueryEngine qEngine){
    logger.info("Committing ORDI data");
    try {
      ordiConnection.commit();
    } catch(ORDIException e) {
      logger.error("Error while committing ORDI data", e);
    }
    logger.info("Closing ORDI connection");
    try {
      ordiConnection.close();
    } catch(ORDIException e) {
      logger.error("Error while closing ORDI connection", e);
    }
    ordiConnection = null;
    ordiFactory = null;
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
  
  /**
   * Identifies the URI that is associated with the provided annotation. If one
   * cannot be found, it creates the appropriate ontology instance and returns
   * its URI.
   * 
   * @see gate.mimir.SemanticAnnotationHelper#getMentionUri(gate.Annotation,
   *      com.ontotext.ordi.tripleset.TConnection)
   */
  public synchronized String[] getMentionUris(Annotation annotation,
          int length, Indexer indexer) {
    // lazy init
    if(hasFeaturesPredicate == null) {
      init(indexer);
    }
    try {
      String featureString = featuresToString(annotation.getFeatures());
      // find the entity for this annotation, creating if necessary
      URI annotationClassURI = ordiFactory.createURI(
              ANNOTATION_NAMESPACE, annotation.getType());
      QueryBindingSet bindings = new QueryBindingSet();
      CloseableIterator<? extends BindingSet> result =
              ordiConnection.evaluate(
                      ORDIUtils.getAnnotationEntityQuery(annotationClassURI,
                              featureString), bindings, new DatasetImpl(),
                      true, null);
      URI entityURI = null;
      if(result.hasNext()) {
        BindingSet aResult = result.next();
        entityURI = (URI)aResult.getValue("entity");
      } else {
        // no entity found - create one
        Literal featuresLiteral = ordiFactory.createLiteral(featureString);
        entityURI = ordiFactory.createURI(
                ANNOTATION_NAMESPACE + annotation.getType() + 
                ":" + (uniqueId++));
        ordiConnection.addStatement(entityURI, RDF.TYPE,
                annotationClassURI, mimirGraphURI);
        ordiConnection.addStatement(entityURI,
                hasFeaturesPredicate, featuresLiteral, mimirGraphURI);
      }
      result.close();
      // find the mention URI
      URI mentionURI = ordiFactory.createURI(entityURI.stringValue() + ":" + length);
      Literal lengthLiteral = ordiFactory.createLiteral(length);
      // see whether the mention is already there
      CloseableIterator<?> lengthResult = ordiConnection.search(mentionURI,
                      hasLengthPredicate, lengthLiteral, mimirGraphURI, null);
      if(!lengthResult.hasNext()) {
        // add the mention
        ordiConnection.addStatement(entityURI,
                hasMentionPredicate, mentionURI, mimirGraphURI);
        ordiConnection.addStatement(mentionURI,
                hasLengthPredicate, lengthLiteral, mimirGraphURI);
        // TODO mention rdf:type mimir:Mention
      }
      lengthResult.close();
      if(callsUntilNextCommit-- < 0) {
        callsUntilNextCommit = COMMIT_INTERVAL;
        logger.info("Committing ORDI connection");
        ordiConnection.commit();
      }
      return new String[]{mentionURI.stringValue()};
    } catch(ORDIException e) {
      throw new GateRuntimeException("Exception accessing ORDI", e);
    }
  }

  /**
   * Returns a single string representation of the feature values for an
   * annotation. Calls {@link #featuresToString(FeatureMap, String[])} to do the
   * real work.
   */
  protected String featuresToString(FeatureMap features) {
    return featuresToString(features, nominalFeatureNames);
  }

  /**
   * Returns a single string representation of the feature values for an
   * annotation. The string produced comprises the feature values, separated by
   * ampersands. If any feature value actually contains an ampersand character,
   * this will be escaped by prefixing it with a '\' (backslash) character. The
   * features are read in the order given by the {@link #nominalFeatureNames} array.
   * All feature values are assumed to be String.
   * 
   * @param features
   *          the annotation for which the features should be encoded.
   * @param featureNames
   *          TODO
   * @return a {@link String} value.
   */
  public static String featuresToString(FeatureMap features,
          String[] featureNames) {
    StringBuffer result = new StringBuffer();
    for(int i = 0; i < featureNames.length; i++) {
      Object aFeatureValue = features.get(featureNames[i]);
      if(aFeatureValue != null) {
        String aFeatureValueString = aFeatureValue.toString();
        // first escape any backslashes
        aFeatureValueString = aFeatureValueString.replaceAll("\\\\", "\\\\");
        // next escape any ampersands
        aFeatureValueString = aFeatureValueString.replaceAll("&", "\\&");
        result.append(aFeatureValueString);
      }
      if(i < featureNames.length - 1) result.append("&");
    }
    return result.toString();
  }

  /**
   * Given a set of constraints on a feature value, returns a regular expression
   * pattern that will match any feature string that satisfies the constraints.
   * 
   * @param constraints
   *          a map of feature name to allowed value.
   * @param featureNames
   *          the names of the features in the feature string, in order.
   */
  public static String featureConstraintsToRegex(
          Map<String, String> constraints, String[] featureNames) {
    StringBuilder result = new StringBuilder();
    for(int i = 0; i < featureNames.length; i++) {
      if(constraints.containsKey(featureNames[i])) {
        // escape any backslashes and ampersands, and quote the result
        result.append(Pattern.quote(constraints.get(featureNames[i])
                .replaceAll("(\\\\|&)", "\\\1")));
      } else {
        // yes, this is a horrible regex. We want to allow:
        // [
        // double-backslash | backslash ampersand |
        // anything other than a backslash or an ampersand
        // ] any number of times
        result.append("(?:\\\\\\\\|\\\\&|[^\\\\&])*");
      }
      // add a separator if this is not the last feature
      if(i < featureNames.length - 1) {
        result.append('&');
      }
    }
    return result.toString();
  }

  /**
   * Converts a single string representation of a feature map into individual
   * feature values. This method performs the reverse operation of
   * {@link #featuresToString(FeatureMap, String[])}.
   * 
   * @param featureString
   *          the string representing the feature map.
   * @return an array of {@link String}s, representing the individual feature
   *         values.
   * @throws ParseException
   *           if the provided string cannot be parsed.
   */
  public static String[] featuresFromString(String featureString)
          throws ParseException {
    // unescape and split the content
    List<String> featureValues = new ArrayList<String>();
    StringBuilder aFeatureValue = new StringBuilder(featureString.length());
    for(int i = 0; i < featureString.length(); i++) {
      boolean escape = false;
      char c = featureString.charAt(i);
      switch(c){
        case '\\':
          // backslash -> either escape start, or backslash character
          if(escape) {
            aFeatureValue.append(c);
            escape = false;
          } else {
            escape = true;
          }
          break;
        case '&':
          if(escape) {
            aFeatureValue.append(c);
            escape = false;
          } else {
            // new feature
            // save the current value
            featureValues.add(aFeatureValue.toString());
            // reset the featureValue for next chars.
            aFeatureValue.delete(0, aFeatureValue.length());
          }
          break;
        default:
          if(escape) {
            // this should not happen, as the only escaped chars are \ and &
            throw new ParseException("Invalid features string!", i);
          } else {
            aFeatureValue.append(c);
          }
      }
    }
    return featureValues.toArray(new String[featureValues.size()]);
  }

  /**
   * Get all the mentions of the given annotation type that match the given
   * constraints.
   * 
   * @param annotationType
   *          the annotation type.
   * @param predicates
   *          constraints on the annotation's feature values.
   * @param engine
   *          the {@link QueryEngine} in which this query will be running.
   * @return
   */
  public List<Mention> getMentions(String annotationType,
          List<Constraint> predicates, QueryEngine engine) {
    // lazy init
    if(hasFeaturesPredicate == null) {
      init(engine);
    }
    // retrofitting the implementation to fit the new interface
    Map<String, String> constraints =
            new HashMap<String, String>(predicates.size());
    for(Constraint aConstraint : predicates) {
      if(constraints.containsKey(aConstraint.getFeatureName())) { throw new IllegalArgumentException(
              "This annotation helper does not support multiple "
                      + "constraints on the same feature!"); }
      if(aConstraint.getPredicate() != ConstraintType.EQ) { throw new IllegalArgumentException(
              "This annotation helper only supports constraints of type "
                      + "equals!"); }
      if(aConstraint.getValue() instanceof String) {
        constraints.put(aConstraint.getFeatureName(),
                (String)aConstraint.getValue());
      } else {
        throw new IllegalArgumentException(
                "This annotation helper only supports String values for "
                        + "constraints");
      }
    }
    try {
      List<Mention> mentions = new ArrayList<Mention>();
      // build up a sparql query to find the entities for this annotation type
      URI annotationClassURI = ordiFactory.createURI(ANNOTATION_NAMESPACE,
                      annotationType);
      QueryBindingSet bindings = new QueryBindingSet();
      CloseableIterator<? extends BindingSet> result =
              ordiConnection.evaluate(ORDIUtils.getEntitiesQuery(
                      annotationClassURI, !constraints.isEmpty()), bindings,
                      new DatasetImpl(), true, null);
      try {
        Pattern pattern = null;
        if(constraints != null && !constraints.isEmpty()) {
          pattern =
                  Pattern.compile(featureConstraintsToRegex(constraints,
                          nominalFeatureNames));
        }
        while(result.hasNext()) {
          BindingSet binding = result.next();
          if(pattern != null) {
            // if we have constraints, check them now
            String features =
                    binding.getBinding("features").getValue().stringValue();
            if(!pattern.matcher(features).matches()) {
              continue;
            }
          }
          URI entityURI = (URI)binding.getBinding("entity").getValue();
          CloseableIterator<? extends TStatement> mentionsResult =
                  ordiConnection.search(entityURI,
                          hasMentionPredicate, null, mimirGraphURI, null);
          try {
            while(mentionsResult.hasNext()) {
              TStatement hasMentionStatement = mentionsResult.next();
              CloseableIterator<? extends TStatement> lengthResult =
                      ordiConnection.search(
                              (URI)hasMentionStatement.getObject(),
                              hasLengthPredicate, null, mimirGraphURI, null);
              if(lengthResult.hasNext()) {
                TStatement lengthStatement = lengthResult.next();
                lengthResult.close();
                mentions.add(new Mention(hasMentionStatement.getObject()
                        .stringValue(), ((Literal)lengthStatement.getObject())
                        .intValue()));
              }
            }
          } finally {
            mentionsResult.close();
          }
        }
      } finally {
        result.close();
      }
      return mentions;
    } catch(ORDIException e) {
      throw new GateRuntimeException("Exception accessing ORDI", e);
    }
  }

  private static final Logger logger = Logger
          .getLogger(PlainAnnotationHelper.class);
}
