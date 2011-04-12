import groovy.xml.MarkupBuilder;

import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH

// Package up any Mimir plugins configured at relative paths to be included in
// the war file.
eventCreateWarStart = { warName, stagingDir ->
  event('StatusUpdate', ["Bundling Mimir plugins for WAR"])
  // load the mimir configuration (which includes the plugins)
  def MimirConfigLoader = classLoader.loadClass('gate.mimir.web.MimirConfigLoader')
  MimirConfigLoader.loadMimirConfig()
  
  URI baseURI = new File(basedir).toURI()

  File pluginsList = new File("${stagingDir}/WEB-INF/mimir-plugins.xml")
  pluginsList.withPrintWriter("UTF-8") { pw ->
    new MarkupBuilder(pw).plugins {
      if(CH.config.gate.mimir.plugins) {
        for(loc in CH.config.gate.mimir.plugins.entrySet()) {
          // only package plugins that are not absolute URLs
          if(!(loc.value =~ /^[a-z]{2,}:/)) {
            File pluginDir = new File(baseURI.resolve(loc.value))
            ant.mkdir(dir:"${stagingDir}/WEB-INF/mimir-plugins/${loc.key}")
            ant.copy(todir:"${stagingDir}/WEB-INF/mimir-plugins/${loc.key}") {
              ant.fileset(dir:pluginDir.absolutePath)
            }
            // write an appropriate element to the XML file
            plugin(loc.key)
          }
        }
      }
    }
  }
}

/**
 * Force a GWT compile when packaging the plugin.
 */
eventPackagePluginStart = {pluginName ->
  if(pluginName == 'mimir-web') {
    includeTargets << new File("${gwtPluginDir}/scripts/_GwtInternal.groovy")
    compileGwtModules()
  }
}
