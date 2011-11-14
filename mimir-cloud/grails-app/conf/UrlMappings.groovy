/*
 *  UrlMappings.groovy
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
class UrlMappings {

  static mappings = {
    // all other mappings are handled by the MimirUrlMappings file.
    // Here we only ned to take care of the actions we added to the ones 
    // provided by the Mimir plugin
    "/admin/passwords"(controller:"mimirStaticPages", action:"passwords")
    "/admin/savePasswords"(controller:"mimirStaticPages", action:"savePasswords")
    
    
    // spring security
    "/login/$action?"(controller: "login")
    "/logout/$action?"(controller: "logout")

    "/_gcn_active" (view:'/status')
  }
}
