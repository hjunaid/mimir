package gate.mimir.util

import org.springframework.web.context.request.RequestContextHolder as RCH

import gate.mimir.tool.WebUtils

class WebUtilsManager {
  public WebUtils currentWebUtils(String remoteUrl) {
    if(RCH.requestAttributes) {
      WebUtils utils = RCH.requestAttributes.session.webUtilsInstance
      if(!utils) {
        utils = new WebUtils()
        RCH.requestAttributes.session.webUtilsInstance = utils
      }
      return utils
    } else {
      // no thread-bound request, use the static instance
      return WebUtils.staticWebUtils()
    }
  }
}
