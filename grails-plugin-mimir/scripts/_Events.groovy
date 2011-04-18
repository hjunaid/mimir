/*
 *  _Events.groovy
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
