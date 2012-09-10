/*
 *  UrlMappings.groovy
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
class UrlMappings {

  static mappings = {
    // all other mappings are handled by the MimirUrlMappings file.
    // Here we only need to take care of the actions we added to the ones 
    // provided by the Mimir plugin
    "/admin/passwords"(controller:"mimirStaticPages", action:"passwords")
    "/admin/savePasswords"(controller:"mimirStaticPages", action:"savePasswords")
    
    
    // spring security
    "/login/$action?"(controller: "login")
    "/logout/$action?"(controller: "logout")

    "/_gcn_active" (view:'/status')
  }
}
