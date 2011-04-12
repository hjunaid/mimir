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
