package gate.mimir.util

import gate.Gate;
import gate.mimir.SemanticAnnotationHelper


/**
 * Closure delegate for a single <code>index</code> block within the
 * semanticAnnotations closure in the mimir configuration.
 */
class IndexHandler {
  List annotationTypes = []
  List helpers = []
  
  SemanticAnnotationsHandler saHandler
  
  public IndexHandler(SemanticAnnotationsHandler saHandler) {
    this.saHandler = saHandler
  }

  def invokeMethod(String name, args) {
    annotationTypes << name
    def defParams = null
    if(args) {
      // if an explicit helper has been passed in, use that
      if(args[0] instanceof SemanticAnnotationHelper) {
        helpers << args[0]
        return
      }
      else if(args[0] instanceof Map) {
        defParams = args[0]
      }
      else {
        throw new IllegalArgumentException("Expected either a " +
            "SemanticAnnotationHelper or a Map of parameters " +
            "defining a default helper")
      }
    }
    // if we get here, create a default helper from the params
    Class theClass = null
    if(defParams?.type) {
      // a Class object
      theClass = defParams.type
    } else {
      throw new IllegalArgumentException("Index template does not include the " + 
          "type for the semantic annotation helper for annotation type ${name}.")
    }
  
    helpers << theClass.newInstance(name,
                     defParams?.nominalFeatures as String[],
                     defParams?.integerFeatures as String[],
                     defParams?.floatFeatures as String[],
                     defParams?.textFeatures as String[],
                     defParams?.uriFeatures as String[])
  }
}

