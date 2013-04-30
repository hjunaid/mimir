import it.unimi.di.big.mg4j.search.score.BM25Scorer
import it.unimi.di.big.mg4j.search.score.CountScorer
import it.unimi.di.big.mg4j.search.score.TfIdfScorer
import gate.mimir.search.score.BindingScorer
import gate.mimir.search.score.DelegatingScoringQueryExecutor

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
      dbh2 = "../plugins/db-h2"
      measurements = "../plugins/measurements"
      sesame = "../plugins/sesame"
      sparql = "../plugins/sparql"
}

// the xgapp file that defines the query tokeniser.  Alternatively
// you can redefine the queryTokeniser Spring bean in resources.groovy
queryTokeniserGapp = "WEB-INF/gate-home/default-query-tokeniser.xgapp"

// The base directory in which newly created local indexes will be put.
// This value can be set interactively from the administration pages, so you 
// don't have to set it here. If you do, the value here will be used to 
// initialise to configuration databse record. Once a value exists in the DB, 
// the one set here _is ignored_ (so changes in the admin UI take precedence)!

// indexBaseDirectory = "mimir-indexes"

// this directory is used for storing temporary files (such as index archives
// prepared for download)
tempDir="mimir-archives"

// The set of scorers available. Each scorer has a name and is defined by a 
// closure that returns a fully configured instance of 
// gate.mimir.search.score.MimirScorer.

scorers.'Count Scoring' = {
  new DelegatingScoringQueryExecutor(new CountScorer())
}

scorers.'TF.IDF' = {
  new DelegatingScoringQueryExecutor(new TfIdfScorer())
}

scorers.'BM25' = {
  new DelegatingScoringQueryExecutor(new BM25Scorer())
}

scorers.'Hit Length Scoring' = {
  new BindingScorer()
}