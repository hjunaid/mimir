package gate.mimir.web

import grails.util.Environment
import groovy.util.ConfigSlurper
import groovy.util.ConfigObject

import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH

class MimirConfigLoader {
  static boolean mimirConfigLoaded = false
  
  public static synchronized void loadMimirConfig() {
    if(mimirConfigLoaded) return
    ConfigObject fullMimirConfig = new ConfigObject()
    ConfigSlurper slurper = new ConfigSlurper(Environment.current.name)
    try {
      // parse the default config
      fullMimirConfig = slurper.parse(DefaultMimirConfig)
    } catch(Exception e) {
      println "Could not load DefaultMimirConfig"
    }
    try {
      // parse the app-provided MimirConfig (if it exists) and merge this into
      // the default config (app-provided settings win over defaults)
      GroovyClassLoader classLoader = new GroovyClassLoader(MimirConfigLoader.class.getClassLoader())
      ConfigObject mimirConf = slurper.parse(classLoader.loadClass("MimirConfig"))
      fullMimirConfig = fullMimirConfig.merge(mimirConf)
    } catch(Exception e) {
      // ignore, MimirConfig may legitimately be missing.
    }

    // finally merge in any settings coming from the existing app config
    fullMimirConfig = fullMimirConfig.merge(CH.config.gate.mimir)

    // and store the result back in the main config
    CH.config.gate.mimir = fullMimirConfig

    mimirConfigLoaded = true
  }
}
