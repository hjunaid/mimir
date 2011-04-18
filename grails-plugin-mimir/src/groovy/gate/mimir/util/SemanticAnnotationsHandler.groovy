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

  private IndexHandler defaultHandler = new IndexHandler(this)

  def index(Closure callable) {
    def handler = new IndexHandler(this)
    callable.delegate = handler
    callable.resolveStrategy = Closure.DELEGATE_FIRST
    callable.call()

    indexerConfigs << new SemanticIndexerConfig(
                            handler.annotationTypes as String[],
                            handler.helpers as SemanticAnnotationHelper[])
  }

  /**
   * Checks if there were any semantic annotation type calls outside any
   * <code>index</code> block, and if so, groups them together into an extra
   * SemanticIndexerConfig.
   */
  void finish() {
    if(defaultHandler.annotationTypes) {
      indexerConfigs << new SemanticIndexerConfig(
                         defaultHandler.annotationTypes as String[],
                         defaultHandler.helpers as SemanticAnnotationHelper[])
    }
  }

  /**
   * Delegate everything else to the default handler.
   */
  def methodMissing(String name, args) {
    defaultHandler."$name"(args)
  }

}
