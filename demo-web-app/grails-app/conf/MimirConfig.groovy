/*
 *  MimirConfig.groovy
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

 // Mimir configuration.  Values in this file get merged into the main
// GrailsApplication.config under gate.mimir, with values specified directly in
// Config.groovy overriding those specified here (and values in external
// configuration files overriding that, as usual).  So you can override (say)
// the queryTokeniserGapp setting from this file by specifying
//
// gate.mimir.queryTokeniserGapp = "...."
//
// in Config.groovy or a grails.config.locations external file.

// default GATE initialisation params - we do not specify pluginsHome or
// siteConfigFile as these take sensible defaults from gateHome
gateInit {
  gateHome = "WEB-INF/gate-home"
  userConfigFile = "WEB-INF/gate-home/user.xml"
}

// Mimir plugins to load.  You generally need at least one of the standard
// db-h2 and/or ordi plugins, as well as the measurements plugin if you are
// using Measurement annotations.  The plugins will be loaded from their
// specified locations for run-app and will be packaged into the WAR for
// deployment.
plugins {
  h2 = "../plugins/db-h2"
}

// the xgapp file that defines the query tokeniser.  Alternatively
// you can redefine the queryTokeniser Spring bean in resources.groovy
queryTokeniserGapp = "WEB-INF/gate-home/default-query-tokeniser.xgapp"

// the base directory in which newly created local indexes will
// be put.
indexBaseDirectory = "mimir-indexes"
 