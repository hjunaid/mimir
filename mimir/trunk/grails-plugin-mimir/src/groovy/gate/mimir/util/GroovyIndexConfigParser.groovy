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
