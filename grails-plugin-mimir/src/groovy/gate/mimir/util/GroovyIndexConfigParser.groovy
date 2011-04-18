/*
 *  GroovyIndexConfigParser.groovy
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
import gate.mimir.DocumentMetadataHelper
import gate.mimir.IndexConfig
import gate.mimir.IndexConfig.SemanticIndexerConfig
import gate.mimir.IndexConfig.TokenIndexerConfig

/**
 * Helper class for parsing Groovy index configuration scripts into IndexConfig
 * objects.
 */
public class GroovyIndexConfigParser {
  public static IndexConfig createIndexConfig(String groovyScript, File indexDir) {
    // evaluate the configuration script
    def scriptBinding = new Binding([:])
    def shell = new GroovyShell(Gate.classLoader,
        scriptBinding)
    shell.evaluate(groovyScript)

    // process the tokenFeatures section
    def tokenFeaturesHandler = new TokenFeaturesHandler()
    def tokenFeaturesClosure = scriptBinding.tokenFeatures
    tokenFeaturesClosure.delegate = tokenFeaturesHandler
    tokenFeaturesClosure.resolveStrategy = Closure.DELEGATE_FIRST
    tokenFeaturesClosure.call()

    // process the semanticAnnotations section
    def semanticAnnotationsHandler = new SemanticAnnotationsHandler()
    def semanticAnnotationsClosure = scriptBinding.semanticAnnotations
    semanticAnnotationsClosure.delegate = semanticAnnotationsHandler
    semanticAnnotationsClosure.resolveStrategy = Closure.DELEGATE_FIRST
    semanticAnnotationsClosure.call()
    semanticAnnotationsHandler.finish()

    // build the index config
    def indexConfig = new IndexConfig(
        indexDir,
        scriptBinding.tokenASName,
        scriptBinding.tokenAnnotationType,
        scriptBinding.semanticASName,
        tokenFeaturesHandler.indexerConfigs as TokenIndexerConfig[],
        semanticAnnotationsHandler.indexerConfigs as SemanticIndexerConfig[],
        scriptBinding.documentMetadataHelpers as DocumentMetadataHelper[],
        scriptBinding.documentRenderer)

    return indexConfig
  }

}
