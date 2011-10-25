/*
 *  WebUtilsManager.groovy
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

import org.springframework.web.context.request.RequestContextHolder as RCH

import gate.mimir.tool.WebUtils
import gate.mimir.web.RemoteIndex;

class WebUtilsManager {
  public WebUtils currentWebUtils(RemoteIndex remoteIndex) {
    if(RCH.requestAttributes) {
      WebUtils utils = RCH.requestAttributes.session.webUtilsInstance
      if(!utils) {
        utils = new WebUtils(remoteIndex.remoteUsername, 
          remoteIndex.remotePassword)
        RCH.requestAttributes.session.webUtilsInstance = utils
      }
      return utils
    } else {
      // no thread-bound request, use the static instance (if no authentication
      // required), or a fresh one every time
      if(remoteIndex.remoteUsername) {
        return new WebUtils(remoteIndex.remoteUsername, 
            remoteIndex.remotePassword)   
      } else {
        return WebUtils.staticWebUtils()
      }
    }
  }
}
