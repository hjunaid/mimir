package gate.mimir.web

// default GATE initialisation params - we do not specify pluginsHome or
// siteConfigFile as these take sensible defaults from gateHome
gateInit {
  gateHome = "WEB-INF/gate-home"
  userConfigFile = "WEB-INF/gate-home/user.xml"
}

// the xgapp file that defines the query tokeniser.  Alternatively
// you can redefine the queryTokeniser Spring bean in resources.groovy
queryTokeniserGapp = "WEB-INF/gate-home/default-query-tokeniser.xgapp"

// the base directory in which newly created local indexes will
// be put.
indexBaseDirectory = "mimir-indexes"