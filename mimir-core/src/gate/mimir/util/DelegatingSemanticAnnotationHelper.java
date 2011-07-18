/*
 *  DelegatingSemanticAnnotationHelper.java
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
 *  Ian Roberts, 28 Mar 2011
 *  
 *  $Id$
 */
package gate.mimir.util;

import java.util.List;
import java.util.Map;

import gate.Annotation;
import gate.Document;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.Constraint;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.Indexer;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;

/**
 * <p>{@link SemanticAnnotationHelper} that simply delegates all method calls to
 * another helper object. Use this as a base class for helpers that provide
 * search enhancements (converting higher-level search expressions into
 * appropriate low-level constraints) on top of any generic helper.</p>
 * 
 * <p>The default implementation of the "business" methods init/close,
 * documentStart/End and getMentions/getMentionUris is to simply call
 * the delegate's equivalent method.  However the "info" methods
 * getNominal/Integer/Float/Text/UriFeatureNames simply return the values
 * passed to the constructor.  This is deliberate, as it allows this
 * helper to claim support for additional features not provided by the
 * delegate, and/or to hide features that the delegate supports if, at
 * search time the helper will be faking these features in terms of
 * other features.</p>
 * 
 * <p><b>Note</b> this class does <b>not</b> override the convenience method
 * {@link AbstractSemanticAnnotationHelper#getMentions(String, Map, QueryEngine)},
 * so this method is implemented as a call to
 * <code>this.getMentions(String, List&lt;Constraint&gt;, QueryEngine)</code>, not
 * to <code>delegate.getMentions(String, Map, QueryEngine)</code>.
 */
public abstract class DelegatingSemanticAnnotationHelper extends
                                                        AbstractSemanticAnnotationHelper {
  private static final long serialVersionUID = 458089145672457600L;

  /**
   * Map key for the Groovy-friendly constructor in subclasses.
   */
  public static final String DELEGATE_KEY = "delegate";

  protected SemanticAnnotationHelper delegate;

  /**
   * Create a {@link DelegatingSemanticAnnotationHelper} for the given
   * annotation type and features, delegating to the given delegate helper.
   */
  protected DelegatingSemanticAnnotationHelper(String annotationType,
          String[] nominalFeatureNames, String[] integerFeatureNames,
          String[] floatFeatureNames, String[] textFeatureNames,
          String[] uriFeatureNames, SemanticAnnotationHelper delegate) {
    super(annotationType, nominalFeatureNames, integerFeatureNames,
            floatFeatureNames, textFeatureNames, uriFeatureNames);
    this.delegate = delegate;
  }


  @Override
  public void init(Indexer indexer) {
    delegate.init(indexer);
  }

  @Override
  public void init(QueryEngine queryEngine) {
    delegate.init(queryEngine);
  }

  @Override
  public void documentStart(Document document) {
    delegate.documentStart(document);
  }

  @Override
  public String[] getMentionUris(Annotation annotation, int length,
          Indexer indexer) {
    return delegate.getMentionUris(annotation, length, indexer);
  }

  @Override
  public List<Mention> getMentions(String annotationType,
          List<Constraint> constraints, QueryEngine engine) {
    return delegate.getMentions(annotationType, constraints, engine);
  }

  @Override
  public void documentEnd() {
    delegate.documentEnd();
  }

  @Override
  public void close(Indexer indexer) {
    delegate.close(indexer);
  }

  @Override
  public void close(QueryEngine qEngine) {
    delegate.close(qEngine);
  }
  
  public static String getAnnTypeFromMapOrDelegate(Map<String, ?> params) {
    String fromMap = getString(params, ANN_TYPE_KEY);
    if(fromMap == null && params.get(DELEGATE_KEY) instanceof AbstractSemanticAnnotationHelper) {
      return ((AbstractSemanticAnnotationHelper)params.get(DELEGATE_KEY)).getAnnotationType();
    } else {
      return fromMap;
    }
  }
  
  public static String[] getNominalFeaturesFromMapOrDelegate(Map<String, ?> params) {
    String[] fromMap = getArray(params, NOMINAL_FEATURES_KEY);
    if(fromMap == null && params.get(DELEGATE_KEY) instanceof AbstractSemanticAnnotationHelper) {
      return ((AbstractSemanticAnnotationHelper)params.get(DELEGATE_KEY)).getNominalFeatureNames();
    } else {
      return fromMap;
    }
  }

  public static String[] getIntegerFeaturesFromMapOrDelegate(Map<String, ?> params) {
    String[] fromMap = getArray(params, INTEGER_FEATURES_KEY);
    if(fromMap == null && params.get(DELEGATE_KEY) instanceof AbstractSemanticAnnotationHelper) {
      return ((AbstractSemanticAnnotationHelper)params.get(DELEGATE_KEY)).getIntegerFeatureNames();
    } else {
      return fromMap;
    }
  }

  public static String[] getFloatFeaturesFromMapOrDelegate(Map<String, ?> params) {
    String[] fromMap = getArray(params, FLOAT_FEATURES_KEY);
    if(fromMap == null && params.get(DELEGATE_KEY) instanceof AbstractSemanticAnnotationHelper) {
      return ((AbstractSemanticAnnotationHelper)params.get(DELEGATE_KEY)).getFloatFeatureNames();
    } else {
      return fromMap;
    }
  }

  public static String[] getTextFeaturesFromMapOrDelegate(Map<String, ?> params) {
    String[] fromMap = getArray(params, TEXT_FEATURES_KEY);
    if(fromMap == null && params.get(DELEGATE_KEY) instanceof AbstractSemanticAnnotationHelper) {
      return ((AbstractSemanticAnnotationHelper)params.get(DELEGATE_KEY)).getTextFeatureNames();
    } else {
      return fromMap;
    }
  }

  public static String[] getUriFeaturesFromMapOrDelegate(Map<String, ?> params) {
    String[] fromMap = getArray(params, URI_FEATURES_KEY);
    if(fromMap == null && params.get(DELEGATE_KEY) instanceof AbstractSemanticAnnotationHelper) {
      return ((AbstractSemanticAnnotationHelper)params.get(DELEGATE_KEY)).getUriFeatureNames();
    } else {
      return fromMap;
    }
  }

}
