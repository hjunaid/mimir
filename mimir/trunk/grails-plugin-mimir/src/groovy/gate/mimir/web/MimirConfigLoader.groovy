package gate.mimir.web

import grails.util.Environment
import groovy.util.ConfigSlurper
import groovy.util.ConfigObject

import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH

class MimirConfigLoader {
  static boolean mimirConfigLoaded = false
  
  public static synchronized void loadMimirConfig() {
    if(mimirConfigLoaded) return
    ConfigSlurper slurper = new ConfigSlurper(Environment.current.name)
    try {
      // load the default mimir configuration and merge it into the config
      // loaded from Config.groovy, with values from Config.groovy winning
      ConfigObject defaultMimirConf = slurper.parse(DefaultMimirConfig)
      CH.config.gate.mimir = defaultMimirConf.merge(CH.config.gate.mimir)
    } catch(Exception e) {
      println "Could not load DefaultMimirConfig"
    }
    try {
      // do the same with the app-provided MimirConfig.groovy (don't worry
      // if this fails).
      GroovyClassLoader classLoader = new GroovyClassLoader(MimirConfigLoader.class.getClassLoader())
      ConfigObject mimirConf = slurper.parse(classLoader.loadClass("MimirConfig"))
      CH.config.gate.mimir = mimirConf.merge(CH.config.gate.mimir)
    } catch(Exception e) {
      // ignore, MimirConfig may legitimately be missing.
    }
    mimirConfigLoaded = true
  }
}
