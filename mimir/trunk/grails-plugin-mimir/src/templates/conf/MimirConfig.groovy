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
