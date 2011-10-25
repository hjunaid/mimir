/*
 *  GusTagLib.groovy
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
package gate.mimir.gus

import gate.mimir.gus.client.SearchException

import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationContext

/**
 * Tag library defining a tag to render a document to the output.
 */
class GusTagLib implements ApplicationContextAware {
  static namespace = "gus"

  /* This is slightly messy - I want to autowire the gusService but taglibs are
   * singletons and the gusService is session-scoped.  So instead I inject the
   * applicationContext and then fetch the gusService bean from the context at
   * call time to get the right instance for the current session.
   */
  ApplicationContext applicationContext

  def documentContent = { attrs, body ->
    def queryId = attrs.queryId
    def documentId = attrs.documentId as int
    try {
      applicationContext.gusService.renderDocument(queryId, documentId, out)
    }
    catch(SearchException ex) {
      log.error("SearchException: ", ex)
      out << g.message(code:"gus.bad.query.id", args:[queryId])
    }
    catch(Exception ex) {
      log.error("Exception rendering document ${documentId}", ex)
      out << g.message(code:"gus.renderDocument.exception", args:[ex.message])
    }
  }
}
