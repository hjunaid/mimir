package gate.mimir.web;

/**
 * A template for an index configuration, used to create new local indexes.
 */
class IndexTemplate {
  /**
   * Name of this template.
   */
  String name
  
  /**
   * Longer description.
   */
  String comment
  
  /**
   * Groovy fragment that defines the index configuration.
   */
  String configuration
  
  static constraints = {
    name(nullable:false, blank:false)
    comment(nullable:true, blank:true)
    configuration(nullable:false, maxSize:10240)
  }

  /**
   * Use the name of this template as its string representation.
   */
  public String toString() {
    return name
  }
}
