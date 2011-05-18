/*
 * SPARQLSemanticAnnotationHelper.java
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
 * Valentin Tablan, 19 Apr 2011
 * 
 * $Id$
 */
package gate.mimir.sparql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.log4j.Logger;

import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.Indexer;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;
import gate.mimir.util.DelegatingSemanticAnnotationHelper;
import gate.util.GateRuntimeException;

/**
 * A Semantic annotation helper that, at query time, connects to a SPARQL
 * endpoint to obtain a list of candidate URIs that are then passed to the
 * underlying delegate annotation helper.
 */
public class SPARQLSemanticAnnotationHelper extends
                                           DelegatingSemanticAnnotationHelper {
  private static final Logger logger = Logger
      .getLogger(SPARQLSemanticAnnotationHelper.class);

  
  /**
   * A query fragment that, if set, gets prepended to all SPARQL queries sent 
   * to the end point. This could be used, for example, for setting up a list of 
   * prefixes.
   */
  private String queryPrefix;
  

  /**
   * A query fragment that, if set, gets appended to all SPARQL queries sent 
   * to the end point. This could be used, for example, for setting up a 
   * LIMIT constraint.
   */
  private String querySuffix;
  
  
  /**
   * The name used for the synthetic feature used at query time to supply the
   * SPARQL query.
   */
  public static final String SPARQL_QUERY_FEATURE_NAME = "sparql";

  
  public static final String NOMINAL_FEATURES_KEY = "nominalFeatures";
  
  public static final String INTEGER_FEATURES_KEY = "integerFeatures";
  
  public static final String FLOAT_FEATURES_KEY = "floatFeatures";
  
  public static final String TEXT_FEATURES_KEY = "textFeatures";
  
  public static final String URI_FEATURES_KEY = "uriFeatures";
  
  public static final String ANN_TYPE_KEY = "annotationType";
  
  public static final String DELEGATE_KEY = "delegate";
  
  public static final String SPARQL_ENDPOINT_KEY = "sparqlEndpoint";
  
  /**
   * The service endpoint where SPARQL queries are forwarded to.
   */
  private String sparqlEndpoint;

  
  /**
   * See {@link #setQueryPrefix(String)}
   * @return
   */
  public String getQueryPrefix() {
    return queryPrefix;
  }

  /**
   * Sets the query prefix: a query fragment that, if set, gets prepended to 
   * all SPARQL queries sent to the end point. This could be used, for example,
   * for setting up a list of PREFIXes.
   */
  public void setQueryPrefix(String queryPrefix) {
    this.queryPrefix = queryPrefix;
  }

  /**
   * See {@link #setQuerySuffix(String)}.
   * @return
   */
  public String getQuerySuffix() {
    return querySuffix;
  }

  /**
   * Sets the query suffix: a query fragment that, if set, gets appended to 
   * all SPARQL queries sent to the end point. This could be used, for example,
   * for setting up a LIMIT constraint.
   */  
  public void setQuerySuffix(String querySuffix) {
    this.querySuffix = querySuffix;
  }

  public SPARQLSemanticAnnotationHelper(String annotationType,
      String sparqlEndpoint, String[] nominalFeatureNames,
      String[] integerFeatureNames, String[] floatFeatureNames,
      String[] textFeatureNames, String[] uriFeatureNames,
      SemanticAnnotationHelper delegate) {
    super(annotationType, 
        concatenateArrays(nominalFeatureNames, 
            new String[]{SPARQL_QUERY_FEATURE_NAME}), integerFeatureNames,
        floatFeatureNames, textFeatureNames, uriFeatureNames, delegate);
    this.sparqlEndpoint = sparqlEndpoint;
  }

  public SPARQLSemanticAnnotationHelper(Map<String, Object> params) {
    this((String)params.get(ANN_TYPE_KEY), 
        (String)params.get(SPARQL_ENDPOINT_KEY),
        featureNames(params.get(NOMINAL_FEATURES_KEY)),
        featureNames(params.get(INTEGER_FEATURES_KEY)),
        featureNames(params.get(FLOAT_FEATURES_KEY)),
        featureNames(params.get(TEXT_FEATURES_KEY)),
        featureNames(params.get(URI_FEATURES_KEY)),
        (SemanticAnnotationHelper)params.get(DELEGATE_KEY));
  }
  
  public SPARQLSemanticAnnotationHelper(String sparqlEndpoint, 
      AbstractSemanticAnnotationHelper delegate) {
    this(delegate.getAnnotationType(), 
        sparqlEndpoint, 
        delegate.getNominalFeatureNames(),
        delegate.getIntegerFeatureNames(),
        delegate.getFloatFeatureNames(),
        delegate.getTextFeatureNames(),
        delegate.getUriFeatureNames(),
        delegate);
  }
  
  protected static String[] featureNames(Object param) {
    if(param == null) return null;
    if(param instanceof String[]) return (String[])param;
    if(param instanceof Collection) {
      return ((Collection<String>)param).toArray(new String[((Collection)param).size()]);
    }
    throw new IllegalArgumentException("Supplied parameter is neither a " +
    		"String array nor a String collection!");
  }
  
  @Override
  public void init(QueryEngine queryEngine) {
    super.init(queryEngine);
  }

  @Override
  public List<Mention> getMentions(String annotationType,
      List<Constraint> constraints, QueryEngine engine) {
    // Accumulate the mentions in a set, so that we remove duplicates.
    Set<Mention> mentions = new HashSet<Mention>();
    List<Constraint> passThroughConstraints = new ArrayList<Constraint>();
    String query = null;
    String originalQuery = null;
    for(Constraint aConstraint : constraints) {
      if(SPARQL_QUERY_FEATURE_NAME.equals(aConstraint.getFeatureName())) {
        originalQuery = (String)aConstraint.getValue(); 
        query = (queryPrefix != null ? queryPrefix : "") + 
            originalQuery + (querySuffix != null ? querySuffix : "");
      } else {
        passThroughConstraints.add(aConstraint);
      }
    }
    if(query == null) {
      // no SPARQL constraints in this query
      return delegate.getMentions(annotationType, constraints, engine);
    } else {
      // run the query on the SPARQL endpoint
      try {
        SPARQLResultSet srs = runQuery(query);
        // check for errors
        for(int i = 0; i < srs.getColumnNames().length; i++) {
          if(srs.getColumnNames()[i].equals("error-message")) {
            // we have an error message
            String errorMessage = (srs.getRows().length > 0 &&  
                srs.getRows()[0].length > i) ? srs.getRows()[0][i] : null;
            throw new IllegalArgumentException("Query \"" + originalQuery + 
                "\" resulted in an error" + 
                (errorMessage != null ? (":\n" + errorMessage) : "."));
          }
        }
        // convert each result row into a query for the delegate
        for(String[] aRow : srs.getRows()) {
          List<Constraint> delegateConstraints =
              new ArrayList<Constraint>(passThroughConstraints);
          for(int i = 0; i < srs.getColumnNames().length; i++) {
            delegateConstraints.add(new Constraint(ConstraintType.EQ, srs
                .getColumnNames()[i], aRow[i]));
          }
          mentions.addAll(delegate.getMentions(annotationType, 
              delegateConstraints, engine));
        }
      } catch(IOException e) {
        logger.error(
            "I/O error while communicating with " + "SPARQL endpoint.", e);
        throw new GateRuntimeException("I/O error while communicating with "
            + "SPARQL endpoint.", e);
      } catch(XMLStreamException e) {
        logger.error("Error parsing results from SPARQL endpoint.", e);
        throw new GateRuntimeException("Error parsing results from SPARQL "
            + "endpoint.", e);
      }
      return new ArrayList<Mention>(mentions);
    }
  }

  /**
   * Runs a query against the SPARQL endpoint and returns the results.
   * 
   * @param query
   * @return
   * @throws XMLStreamException
   */
  protected SPARQLResultSet runQuery(String query) throws IOException,
      XMLStreamException {
    try {
      String urlStr =
          sparqlEndpoint + "?query=" + URLEncoder.encode(query, "UTF-8");
      URL url = new URL(urlStr);
      URLConnection urlConn = url.openConnection();
      urlConn.setRequestProperty("Accept", "application/sparql-results+xml");
      return new SPARQLResultSet(urlConn.getInputStream());
    } catch(UnsupportedEncodingException e) {
      // like that's gonna happen...
      throw new RuntimeException("UTF-8 encoding not supported by this JVM");
    } catch(MalformedURLException e) {
      // this may actually happen
      throw new RuntimeException("Invalid URL - have you set the correct "
          + "SPARQL endpoint?", e);
    }
  }
}
