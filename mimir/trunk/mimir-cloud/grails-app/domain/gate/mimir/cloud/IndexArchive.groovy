/*
 *  IndexArchive.groovy
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
package gate.mimir.cloud

import gate.mimir.util.IndexArchiveState;

/**
 * Domain class holding the data required for downloading a local index.
 *
 */
class IndexArchive {

  /**
   * The index this domain object refers to
   */
  static belongsTo = [theIndex : gate.mimir.web.LocalIndex]
  
  String localDownloadDir
  
  IndexArchiveState state
  
  /**
   * The reason for the current state (used to store any error messages that may 
   * useful in debugging a failed state)
   */
  String cause
  
  /**
   * A 0 - 100 value indicating the progress of the current operation
   */
  int progress
  
  static constraints = {
    theIndex (nullable:false)
    localDownloadDir(nullable:true, blank:true)
    state(nullable:false)
    cause(nullable:true, blank:true)
  }
}
