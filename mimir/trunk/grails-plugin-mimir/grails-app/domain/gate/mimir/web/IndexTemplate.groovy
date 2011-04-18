/*
 *  IndexTemplate.groovy
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
