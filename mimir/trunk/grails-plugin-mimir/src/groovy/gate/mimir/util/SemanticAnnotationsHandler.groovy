/*
 *  SemanticAnnotationsHandler.groovy
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
 *  $Id$
 */
package gate.mimir.util

import gate.Gate;
import gate.mimir.SemanticAnnotationHelper
import gate.mimir.IndexConfig.SemanticIndexerConfig

import org.apache.log4j.Logger

/**
 * Class used as closure delegate to handle the
 * mimir.rpcIndexer.semanticAnnotations closure in mimir configuration.  Method
 * calls are interpreted as the names of semantic annotation types to index.
 * Each method call should have either a single parameter, being the
 * SemanticAnnotationHelper that should be used to handle that annotation type,
 * or a map of zero or more named parameters nominalFeatures, numericFeatures,
 * textFeatures and uriFeatures, which will be used to create a default helper.
 */
class SemanticAnnotationsHandler {

  private static final Logger log = Logger.getLogger(SemanticAnnotationsHandler)

  List indexerConfigs = []

  Map currentIndex = [:]

  def index(Closure callable) {
    Map savedCurrentIndex = currentIndex
    currentIndex = [annotationTypes:[], helpers:[]]
    callable.delegate = this
    callable.call()

    indexerConfigs << new SemanticIndexerConfig(
        currentIndex.annotationTypes as String[],
        currentIndex.helpers as SemanticAnnotationHelper[])
    
    currentIndex = savedCurrentIndex
  }

  /**
   * Main DSL method.  Expects a named parameter "helper" specifying
   * the SemanticAnnotationHelper to register.  If the helper does
   * not provide a getAnnotationType method (i.e. if it is not a
   * subclass of AbstractSemanticAnnotationHelper) then an additional
   * argument named "type" is also required to specify the annotation
   * type against which the helper should be registered.
   * <pre>
   * annotation helper:new DefaultHelper(annType:'Person', nominalFeatures:['gender'])
   * </pre>
   */
  void annotation(Map args) {
    if(!currentIndex) {
      throw new IllegalStateException("annotation method called outside any \"index\" closure")
    }
    def helper = args.helper
    if(!helper) {
      throw new IllegalArgumentException("annotation method requires a \"helper\" parameter")
    }
    if(!(helper instanceof SemanticAnnotationHelper)) {
      throw new IllegalArgumentException("annotation method \"helper\" parameter must be a " +
        "SemanticAnnotationHelper, but ${helper.getClass().getName()} is not.")
    }
    def type = args.type
    if(!type) {
      if(helper.hasProperty("annotationType")) {
        type = helper.annotationType
      }
    }
    if(!type) {
      throw new IllegalArgumentException("annotation method could not determine annotation " +
        "type - type could not be determined from the helper, and no explicit \"type\" parameter " +
        "was provided.")
    }

    currentIndex.annotationTypes << type
    currentIndex.helpers << helper
  }

  /**
   * Old-style DSL method to create and register a semantic annotation helper by calling a method named for the annotation type.  This pattern is now deprecated.
   * Expects the following
   * map entries:
   * <dl>
   *   <dt>type</dt><dd>The Class object representing the type
   *     of the helper.  This class must implement
   *     {@link SemanticAnnotationHelper} and must provide a
   *     constructor taking six arguments - the annotation type
   *     and five String[] arguments giving the nominal, integer,
   *     float, text and URI feature names.</dd>
   *   <dt>nominalFeatures</dt>
   *   <dt>integerFeatures</dt>
   *   <dt>floatFeatures</dt>
   *   <dt>textFeatures</dt>
   *   <dt>uriFeatures</dt><dd>The features of various kinds that
   *     should be indexed by the helper.  These entries should be
   *     String arrays or List<String> (anything that is convertable
   *     to a String array by Groovy's <code>as String[]</code>
   *     operator)</dd>
   * </dl>
   */
  void methodMissing(String annotationType, args) {
    if(args.size() != 1 || !(args[0] instanceof Map)) {
      throw new MissingMethodException(annotationType, this.getClass(), args)
    }
    Map defParams = args[0]
    Class theClass = null
    if(defParams?.type) {
      // a Class object
      theClass = defParams.type
    } else {
      throw new IllegalArgumentException("Index template does not include the " +
      "type for the semantic annotation helper for annotation type ${annotationType}.")
    }

    annotation(helper:theClass.newInstance(annotationType,
        defParams?.nominalFeatures as String[],
        defParams?.integerFeatures as String[],
        defParams?.floatFeatures as String[],
        defParams?.textFeatures as String[],
        defParams?.uriFeatures as String[]))
  }
}
