/*
 * URICache.java
 * 
 * Copyright (c) 2007-2011, The University of Sheffield.
 * 
 * This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html),
 * and is free software, licenced under the GNU Affero General Public License,
 * Version 3, November 2007 (also included with this distribution as file
 * LICENCE-AGPL3.html).
 * 
 * A commercial licence is also available for organisations whose business
 * models preclude the adoption of open source and is subject to a licence fee
 * charged by the University of Sheffield. Please contact the GATE team (see
 * http://gate.ac.uk/g8/contact) if you require a commercial licence.
 * 
 * $Id$
 */
package gate.mimir.sesame;

import static gate.mimir.sesame.SesameSemanticAnnotationHelper.HAS_SPECIALISATION_PROP_URI;
import static gate.mimir.sesame.SesameSemanticAnnotationHelper.HAS_LENGTH_PROP_URI;
import static gate.mimir.sesame.SesameSemanticAnnotationHelper.HAS_MENTION_PROP_URI;
import static gate.mimir.sesame.SesameSemanticAnnotationHelper.MIMIR_GRAPH_URI;
import static gate.mimir.sesame.SesameUtils.MIMIR_NAMESPACE;
import static gate.mimir.sesame.SesameSemanticAnnotationHelper.MIMIR_NULL_DOUBLE;
import static gate.mimir.sesame.SesameSemanticAnnotationHelper.MIMIR_NULL_STRING;
import static gate.mimir.sesame.SesameSemanticAnnotationHelper.MIMIR_NULL_URI;
import gate.Annotation;
import gate.FeatureMap;
import gate.mimir.index.IndexException;
import it.unimi.dsi.fastutil.objects.Object2ShortMap;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.evaluation.QueryBindingSet;
import org.openrdf.query.impl.DatasetImpl;
import org.openrdf.query.parser.sparql.SPARQLUtil;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;

public class URICache {
  /**
   * The annotation helper that uses this cache.
   */
  protected SesameSemanticAnnotationHelper owner;

  /**
   * The 2-level cache used for annotation template instances.
   */
  protected LinkedHashMap<ShortArray, URIAndMap> annTemplateCache;

  /**
   * The cache used for mention URIs.
   */
  protected LinkedHashMap<URIAndInt, URI> mentionsCache;

  /**
   * For each nominal field, a map is stored which associates each value to a
   * short value.
   */
  protected Object2ShortMap<String>[] nominalValuesToShort;

  private static Logger logger = Logger.getLogger(URICache.class);

  private long l1CacheHits;

  private long l1CacheMisses;

  private long l2CacheHits;

  private long l2CacheMisses;

  private long l3CacheHits;

  private long l3CacheMisses;

  private long ordiL1CacheHits;

  private long ordiL1CacheMisses;

  private long ordiL2CacheHits;

  private long ordiL2CacheMisses;

  private long ordiL3CacheHits;

  private long ordiL3CacheMisses;

  /**
   * The URI suffix used for int values in SPARQL.
   */
  private static final String SPARQL_URI_SUFFIX_INT =
          "^^<http://www.w3.org/2001/XMLSchema#int>";

  private static final short KEY_NOT_FOUND = -2;

  private static final short KEY_FOR_NULL = -1;

  private static final int DEFAULT_L1_SIZE = 512;

  private static final int DEFAULT_L2_SIZE = 64;

  private static final int DEFAULT_L3_SIZE = 8192;

  private static final float DEFAULT_LOAD_FACTOR = 0.75f;

  private int l1CacheSize;

  private int l2CacheSize;

  private int l3CacheSize;

  /**
   * Stores a URI and Map Used to store values in a cache Map.
   */
  protected static class URIAndMap {
    protected Map<NonNominalFeatureValues, URI> map;

    protected URI uri;
  }

  /**
   * A structure that holds an URI value and an int. Used as key for mentions
   * cache.
   */
  protected static class URIAndInt {
    protected String uri;

    protected int length;

    public URIAndInt(URI uri, int length) {
      this.uri = uri.getLocalName();
      this.length = length;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + length;
      result = prime * result + ((uri == null) ? 0 : uri.hashCode());
      return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
      if(this == obj) return true;
      if(obj == null) return false;
      URIAndInt other = (URIAndInt)obj;
      if(length != other.length) return false;
      if(uri == null) {
        if(other.uri != null) return false;
      } else if(!uri.equals(other.uri)) return false;
      return true;
    }
  }

  protected static class ShortArray {
    public ShortArray(short[] data, int hashCode) {
      this.data = data;
      this.hashCode = hashCode;
    }

    // public ShortArray(short[] data) {
    // this.data = data;
    // this.hashCode = computeHhashCode();
    // }
    short[] data;

    /**
     * @return the data
     */
    public short[] getData() {
      return data;
    }

    private final int hashCode;

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
      return hashCode;
    }

    public int computeHhashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + Arrays.hashCode(data);
      return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
      if(this == obj) return true;
      if(obj == null) return false;
      ShortArray other = (ShortArray)obj;
      if(!Arrays.equals(data, other.data)) return false;
      return true;
    }
  }

  /**
   * A class for objects that store the values of the non-nominal features for a
   * given annotation. These objects are also used as keys in a Map used for
   * caching.
   */
  protected class NonNominalFeatureValues {
    public NonNominalFeatureValues(Annotation annotation, ValueFactory factory) {
      hasNonNominalFeatures = false;
      numericValues = new double[owner.getFloatFeatureNames().length];
      FeatureMap features = annotation.getFeatures();
      for(int i = 0; i < owner.getFloatFeatureNames().length; i++) {
        Object value = features.get(owner.getFloatFeatureNames()[i]);
        double valueNum = MIMIR_NULL_DOUBLE;
        if(value != null) {
          if(value instanceof Number) {
            valueNum = ((Number)value).doubleValue();
            hasNonNominalFeatures = true;
          } else if(value instanceof String) {
            try {
              valueNum = Double.parseDouble((String)value);
            } catch(NumberFormatException e) {
              logger.warn("Value provided for feature \""
                      + owner.getFloatFeatureNames()[i]
                      + "\" is a String that cannot be parsed to a number. Value ("
                      + value.toString() + ") will be ignored!");
            }
          } else {
            logger.warn("Value provided for feature \""
                    + owner.getFloatFeatureNames()[i]
                    + "\" is not a subclass of java.lang.Number. Value ("
                    + value.toString() + ") will be ignored!");
          }
        }
        numericValues[i] = valueNum;
      }
      // extract all the text feature values
      textValues = new String[owner.getTextFeatureNames().length];
      for(int i = 0; i < owner.getTextFeatureNames().length; i++) {
        Object value = features.get(owner.getTextFeatureNames()[i]);
        if(value == null) {
          textValues[i] = MIMIR_NULL_STRING;
        } else {
          textValues[i] = value.toString();
          hasNonNominalFeatures = true;
        }
      }
      // extract all the URI feature values
      uriValues = new URI[owner.getUriFeatureNames().length];
      for(int i = 0; i < owner.getUriFeatureNames().length; i++) {
        Object oValue = features.get(owner.getUriFeatureNames()[i]);
        String value = (oValue == null ? null : oValue.toString());
        try {
          uriValues[i] =
                  value == null || value.length() == 0
                          ? MIMIR_NULL_URI
                          : factory.createURI(value);
        } catch(Exception e) {
          logger.warn("Could not parse URI value. Problem was: " + e.toString());
          uriValues[i] = null;
        }
        if(uriValues[i] != MIMIR_NULL_URI) hasNonNominalFeatures = true;
      }
      hashCode = computeHashCode();
    }

    public int computeHashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + Arrays.hashCode(numericValues);
      result = prime * result + Arrays.hashCode(textValues);
      result = prime * result + Arrays.hashCode(uriValues);
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if(this == obj) return true;
      if(obj == null) return false;
      NonNominalFeatureValues other = (NonNominalFeatureValues)obj;
      if(!Arrays.equals(numericValues, other.numericValues)) return false;
      if(!Arrays.equals(textValues, other.textValues)) return false;
      if(!Arrays.equals(uriValues, other.uriValues)) return false;
      return true;
    }

    protected double[] numericValues;

    protected String[] textValues;

    protected URI[] uriValues;

    protected boolean hasNonNominalFeatures;

    private final int hashCode;

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
      return hashCode;
    }
  }

  /**
   * Optimises the sizes used for level 1 and level 2 caches.
   */
  protected void adjustCacheSizes() {
    double l1hit = getL1CacheHitRatio();
    double l2hit = getL2CacheHitRatio();
    if(l1hit - l2hit > 0.2) {
      // L1 better than L2, by more than 20% -> increase L2
      if(l1CacheSize >= (annTemplateCache.size() * 2)) {
        // L1 at less than 50% capacity
        l1CacheSize = l1CacheSize / 2;
        l2CacheSize = l2CacheSize * 2;
        logger.info("Decreasing L1 cache size to " + l1CacheSize
                + "; Increasing L2 cache size to " + l2CacheSize + ".");
      }
    } else if(l2hit - l1hit > 0.2) {
      // L2 better than L1, by more than 20% -> increase L1
      if((l1CacheSize * 2) > annTemplateCache.size()) {
        l1CacheSize = l1CacheSize * 2;
        l2CacheSize = l2CacheSize / 2;
        logger.info("Increasing L1 cache size to " + l1CacheSize
                + "; Decreasing L2 cache size to " + l2CacheSize + ".");
      }
    } else {
      // nothing to adjust
    }
  }

  /**
   * Gets the ratio of level 1 cache hits from all accesses.
   * 
   * @return
   */
  public double getL1CacheHitRatio() {
    if(l1CacheHits == 0 && l1CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)l1CacheHits / (l1CacheHits + l1CacheMisses);
    }
  }

  /**
   * Gets the ratio of level 2 cache hits from all accesses.
   * 
   * @return
   */
  public double getL2CacheHitRatio() {
    if(l2CacheHits == 0 && l2CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)l2CacheHits / (l2CacheHits + l2CacheMisses);
    }
  }

  /**
   * Gets the ratio of level 3 (mentions) cache hits from all accesses.
   * 
   * @return
   */
  public double getL3CacheHitRatio() {
    if(l3CacheHits == 0 && l3CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)l3CacheHits / (l3CacheHits + l3CacheMisses);
    }
  }

  /**
   * Gets the ratio of level 1 cache hits from all accesses.
   * 
   * @return
   */
  public double getOrdiL1CacheHitRatio() {
    if(ordiL1CacheHits == 0 && ordiL1CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)ordiL1CacheHits / (ordiL1CacheHits + ordiL1CacheMisses);
    }
  }

  /**
   * Gets the ratio of level 2 cache hits from all accesses.
   * 
   * @return
   */
  public double getOrdiL2CacheHitRatio() {
    if(ordiL2CacheHits == 0 && ordiL2CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)ordiL2CacheHits / (ordiL2CacheHits + ordiL2CacheMisses);
    }
  }

  /**
   * Gets the ratio of level 3 (mentions) cache hits from all accesses.
   * 
   * @return
   */
  public double getOrdiL3CacheHitRatio() {
    if(ordiL3CacheHits == 0 && ordiL3CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)ordiL3CacheHits / (ordiL3CacheHits + ordiL3CacheMisses);
    }
  }

  /**
   * Finds the first level annotation template instance for a given set of
   * nominal values. If none exists in the knowledge base, one will be created
   * and returned.
   * 
   * @param nominalValues
   *          an array of String value representing a set of values for the
   *          nominal features.
   * @return the URI of the annotation template instance or null, if none was
   *         found.
   * @throws RepositoryException
   * @throws MalformedQueryException
   * @throws QueryEvaluationException
   * @throws ORDIException
   * @throws IndexException
   */
  protected URI getFirstLevelATInstance(String[] nominalValues)
          throws RepositoryException, MalformedQueryException,
          QueryEvaluationException {
    StringBuilder query =
            new StringBuilder("PREFIX mimir: <" + MIMIR_NAMESPACE + ">\n");
    query.append("SELECT ?annotationTemplateInstance \n");
    query.append("WHERE {\n");
    query.append("  ?annotationTemplateInstance <" + RDF.TYPE.stringValue()
            + "> <" + owner.annotationTemplateURILevel1 + "> .\n");
    for(int i = 0; i < owner.nominalFeaturePredicates.length; i++) {
      query.append("  ?annotationTemplateInstance <"
              + owner.nominalFeaturePredicates[i].stringValue() + "> \""
              + SPARQLUtil.encodeString(nominalValues[i]) + "\" .\n");
    }
    query.append("}");
    URI topLevelTemplateInstance = null;
    try {
      TupleQuery tq =
              owner.connection.prepareTupleQuery(QueryLanguage.SPARQL,
                      query.toString(), MIMIR_NAMESPACE);
      TupleQueryResult result = tq.evaluate();
      if(result.hasNext()) {
        BindingSet binding = result.next();
        topLevelTemplateInstance =
                (URI)binding.getBinding("annotationTemplateInstance")
                        .getValue();
      }
      if(topLevelTemplateInstance == null) {
        // ordi L1 miss
        ordiL1CacheMisses++;
        // no correct AT instance yet -> create one
        String suffix = (UUID.randomUUID()).toString().replaceAll("-", "_");
        topLevelTemplateInstance =
                owner.factory.createURI(MIMIR_NAMESPACE,
                        owner.getAnnotationType()
                                + (owner.atInstanceUniqueId++) + "_" + suffix); // TODO
        // place
        // suffix
        owner.connection.add(topLevelTemplateInstance, RDF.TYPE,
                owner.annotationTemplateURILevel1, MIMIR_GRAPH_URI);
        for(int i = 0; i < owner.nominalFeaturePredicates.length; i++) {
          owner.connection.add(topLevelTemplateInstance,
                  owner.nominalFeaturePredicates[i],
                  owner.factory.createLiteral(nominalValues[i]),
                  MIMIR_GRAPH_URI);
        }
        owner.connection.commit();
      } else {
        ordiL1CacheHits++;
      }
    } catch(MalformedQueryException e) {
      // if this failed something is very wrong...
      logger.error(
              "Malformed query exception, while trying to obtain "
                      + "the first level annotation template instance. The query sent to "
                      + "ORDI was :\n" + query.toString(), e);
    }
    return topLevelTemplateInstance;
  }

  /**
   * Finds a specialised instance of annotation template (i.e. an instance that
   * has non-nominal properties set). If none exists in the knowledge base, one
   * is created and then returned.
   * 
   * @param atInstance
   *          the non-specialised annotation template instance, for which a
   *          specialisation is sought.
   * @param numericValues
   *          the values for the numeric properties.
   * @param textValues
   *          the values for the text properties.
   * @param uriValues
   *          the values for the URI properties.
   * @return the URI of the sought instance, or <code>null</code>, if none
   *         exists.
   * @throws RepositoryException
   * @throws QueryEvaluationException
   */
  protected URI getSpecialisedATInstance(URI atInstance,
          double[] numericValues, String[] textValues, URI[] uriValues)
          throws RepositoryException, QueryEvaluationException {
    StringBuilder query =
            new StringBuilder("PREFIX mimir: <" + MIMIR_NAMESPACE + ">\n");
    query.append("SELECT ?specATInstance");
    query.append("\n");
    query.append("WHERE {\n");
    query.append("  <" + atInstance.stringValue() + "> <"
            + HAS_SPECIALISATION_PROP_URI.stringValue()
            + "> ?specATInstance .\n");
    query.append("  ?specATInstance <" + RDF.TYPE.stringValue() + "> <"
            + owner.annotationTemplateURILevel2 + "> .\n");
    for(int i = 0; i < numericValues.length; i++) {
      // query.append("  ?specATInstance <" +
      // owner.numericFeaturePredicates[i].stringValue() +
      // "> ?nVal" + i + " .\n");
      // query.append("  FILTER ( ?nVal" + i + " = " +
      // owner.ordiFactory.createLiteral(numericValues[i]).stringValue() +
      // ")\n");
      query.append("  ?specATInstance <"
              + owner.floatFeaturePredicates[i].stringValue() + "> \""
              + numericValues[i]
              + "\"^^<http://www.w3.org/2001/XMLSchema#double> .\n");
    }
    for(int i = 0; i < textValues.length; i++) {
      query.append("  ?specATInstance <"
              + owner.textFeaturePredicates[i].stringValue() + "> \""
              + SPARQLUtil.encodeString(textValues[i]) + "\" .\n");
    }
    for(int i = 0; i < uriValues.length; i++) {
      query.append("  ?specATInstance  <"
              + owner.uriFeaturePredicates[i].stringValue() + "> <"
              + uriValues[i].stringValue() + "> .\n");
    }
    query.append("}");
    // logger.debug("About to execute query:\n" + query.toString());
    URI specialisedInstance = null;
    try {
      TupleQuery tq =
              owner.connection.prepareTupleQuery(QueryLanguage.SPARQL,
                      query.toString(), MIMIR_NAMESPACE);
      TupleQueryResult result = tq.evaluate();
      if(result.hasNext()) {
        BindingSet binding = result.next();
        specialisedInstance =
                (URI)binding.getBinding("specATInstance").getValue();
      }
      if(specialisedInstance == null) {
        // ordi L2 miss
        ordiL2CacheMisses++;
        // no specialisation present -> create one
        String suffix = (UUID.randomUUID()).toString().replaceAll("-", "_");
        specialisedInstance =
                owner.factory.createURI(MIMIR_NAMESPACE,
                        atInstance.getLocalName() + "_"
                                + (owner.atInstanceSpecUniqueId++) + "_"
                                + suffix); // TODO
        // place
        // suffix
        owner.connection.add(specialisedInstance, RDF.TYPE,
                owner.annotationTemplateURILevel2, MIMIR_GRAPH_URI);
        owner.connection.add(atInstance, HAS_SPECIALISATION_PROP_URI,
                specialisedInstance, MIMIR_GRAPH_URI);
        for(int i = 0; i < numericValues.length; i++) {
          owner.connection.add(specialisedInstance,
                  owner.floatFeaturePredicates[i],
                  owner.factory.createLiteral(numericValues[i]),
                  MIMIR_GRAPH_URI);
        }
        for(int i = 0; i < textValues.length; i++) {
          owner.connection.add(specialisedInstance,
                  owner.textFeaturePredicates[i],
                  owner.factory.createLiteral(textValues[i]), MIMIR_GRAPH_URI);
        }
        for(int i = 0; i < uriValues.length; i++) {
          owner.connection.add(specialisedInstance,
                  owner.uriFeaturePredicates[i], uriValues[i], MIMIR_GRAPH_URI);
        }
        owner.connection.commit();
      } else {
        ordiL2CacheHits++;
      }
    } catch(MalformedQueryException e) {
      // if this failed something is very wrong...
      logger.error("Malformed query exception, while trying to obtain "
              + "the specialised annotation template instance. "
              + "The query sent to ORDI was :\n" + query.toString(), e);
    }
    return specialisedInstance;
  }

  /**
   * Obtains the URIs for the mentions associated with the top level annotation
   * template and the specialised annotation template (if one is needed) for a
   * given annotation.
   * 
   * @param annotation
   *          the annotation for which the URIs are sought.
   * @return an array of 2 URI values, the first being the top level template,
   *         the second being the specialised template.
   * @throws QueryEvaluationException
   * @throws MalformedQueryException
   * @throws RepositoryException
   * @throws ORDIException
   * @throws IndexException
   */
  public URI[] getMentionURIs(Annotation annotation, int length)
          throws RepositoryException, MalformedQueryException,
          QueryEvaluationException {
    URI topLevelTemplateInstance = null;
    URI specialisedInstance = null;
    // extract the nominal values
    String[] nominalValues = new String[owner.getNominalFeatureNames().length];
    for(int i = 0; i < owner.getNominalFeatureNames().length; i++) {
      Object value =
              annotation.getFeatures().get(owner.getNominalFeatureNames()[i]);
      if(value == null) {
        nominalValues[i] = MIMIR_NULL_STRING;
      } else {
        nominalValues[i] = value.toString();
      }
    }
    // convert the values to a short[]
    short[] key = new short[nominalValues.length];
    for(int i = 0; i < nominalValues.length; i++) {
      if(nominalValues[i] == MIMIR_NULL_STRING) {
        key[i] = KEY_FOR_NULL;
      } else {
        short valueShort = nominalValuesToShort[i].getShort(nominalValues[i]);
        if(valueShort == KEY_NOT_FOUND) {
          // map miss -> add the value
          valueShort = (short)nominalValuesToShort[i].size();
          nominalValuesToShort[i].put(nominalValues[i], valueShort);
        }
        key[i] = valueShort;
      }
    }
    // build the key value
    ShortArray nomValuesKey =
            new ShortArray(key, Arrays.hashCode(nominalValues));
    URIAndMap topLevel = annTemplateCache.get(nomValuesKey);
    if(topLevel == null) {
      l1CacheMisses++;
      // cache miss -> we need to really find it
      // find the first level annotation template instance
      topLevelTemplateInstance = getFirstLevelATInstance(nominalValues);
      // we now have the correct URI -> add it to the cache
      topLevel = new URIAndMap();
      topLevel.uri = topLevelTemplateInstance;
      topLevel.map =
              new LinkedHashMap<NonNominalFeatureValues, URI>(l2CacheSize,
                      DEFAULT_LOAD_FACTOR, true) {
                @Override
                protected boolean removeEldestEntry(
                        Entry<NonNominalFeatureValues, URI> eldest) {
                  return size() > l2CacheSize;
                }
              };
      annTemplateCache.put(nomValuesKey, topLevel);
    } else {
      l1CacheHits++;
    }
    topLevelTemplateInstance = topLevel.uri;
    // now see if we need the specialise
    NonNominalFeatureValues nonNomvalues =
            new NonNominalFeatureValues(annotation, owner.factory);
    if(nonNomvalues.hasNonNominalFeatures) {
      specialisedInstance = topLevel.map.get(nonNomvalues);
      if(specialisedInstance == null) {
        // second level cache miss -> perform the actual search
        l2CacheMisses++;
        specialisedInstance =
                getSpecialisedATInstance(topLevelTemplateInstance,
                        nonNomvalues.numericValues, nonNomvalues.textValues,
                        nonNomvalues.uriValues);
        // now update the cache
        topLevel.map.put(nonNomvalues, specialisedInstance);
      } else {
        l2CacheHits++;
      }
    }
    // find mentions of the right length
    URI firstLevelMention = getMention(topLevelTemplateInstance, length);
    if(firstLevelMention == null) {
      logger.error("Could not find or create mention for annotation template "
              + topLevelTemplateInstance);
    }
    if(specialisedInstance != null) {
      URI secondLevelMention = getMention(specialisedInstance, length);
      if(secondLevelMention == null) {
        logger.error("Could not find or create mention for specialised annotation template "
                + specialisedInstance);
      }
      return new URI[]{firstLevelMention, secondLevelMention};
    } else {
      return new URI[]{firstLevelMention};
    }
  }

  /**
   * Finds the URI of a mention for a given annotation template and with a
   * specified length. It first searches the 3rd level cache. If not found, it
   * searches the knowledge base. If still not found, it creates the new mention
   * URI and returns it.
   * 
   * @param atInstanceURI
   *          the URI for the annotation template instance
   * @param length
   *          the desired mention length
   * @param ordiConnection
   *          an ORDI connection
   * @param ordiFactory
   *          an ORDI triples factory
   * @return the mention URI, or <code>null</code> if none exists.
   * @throws RepositoryException
   * @throws QueryEvaluationException
   * @throws ORDIException
   * @throws IndexException
   */
  protected URI getMention(URI atInstanceURI, int length)
          throws RepositoryException, QueryEvaluationException {
    // first try the cache
    URIAndInt l3key = new URIAndInt(atInstanceURI, length);
    URI mentionURI = mentionsCache.get(l3key);
    if(mentionURI == null) {
      // level 3 cache miss
      l3CacheMisses++;
      // now try in ORDI
      StringBuilder query =
              new StringBuilder("PREFIX mimir: <" + MIMIR_NAMESPACE + ">\n");
      query.append("SELECT ?mentionInstance\n");
      query.append("WHERE {\n");
      query.append("  <" + atInstanceURI.stringValue() + "> <"
              + HAS_MENTION_PROP_URI.stringValue() + "> ?mentionInstance .\n");
      query.append("  ?mentionInstance <" + HAS_LENGTH_PROP_URI.stringValue()
              + "> \"" + length + "\"" + SPARQL_URI_SUFFIX_INT + " .\n");
      // query.append("  ?mentionInstance <" +
      // HAS_LENGTH_PROP_URI.stringValue()
      // + "> ?length .\n");
      // query.append("  FILTER ( ?length = " + length + " ) .\n");
      query.append("}");
      try {
        TupleQuery tq =
                owner.connection.prepareTupleQuery(QueryLanguage.SPARQL,
                        query.toString(), MIMIR_NAMESPACE);
        TupleQueryResult result = tq.evaluate();
        if(result.hasNext()) {
          BindingSet binding = result.next();
          mentionURI = (URI)binding.getBinding("mentionInstance").getValue();
        }
        if(mentionURI == null) {
          // ordi L3 miss
          ordiL3CacheMisses++;
          // no such mention exits -> create one
          String suffix = (UUID.randomUUID()).toString().replaceAll("-", "_");
          mentionURI =
                  owner.factory.createURI(MIMIR_NAMESPACE,
                          atInstanceURI.getLocalName() + ":"
                                  + (owner.mentionUniqueId++) + "_" + suffix); // TODO
          // place
          // suffix
          owner.connection.add(atInstanceURI, HAS_MENTION_PROP_URI, mentionURI,
                  MIMIR_GRAPH_URI);
          owner.connection.add(mentionURI, HAS_LENGTH_PROP_URI,
                  owner.factory.createLiteral(length), MIMIR_GRAPH_URI);
          owner.connection.commit();
        } else {
          ordiL3CacheHits++;
        }
        // at this point we have a mention -> add it to the cache
        mentionsCache.put(l3key, mentionURI);
      } catch(MalformedQueryException e) {
        // if this failed something is very wrong...
        logger.error("Malformed query exception, while trying to obtain "
                + "the mention URI. The query sent to " + "ORDI was :\n"
                + query.toString(), e);
      }
    } else {
      l3CacheHits++;
    }
    return mentionURI;
  }
}
