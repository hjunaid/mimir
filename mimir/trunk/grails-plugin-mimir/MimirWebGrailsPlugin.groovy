/*
 *  MimirWebGrailsPlugin.groovy
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
import gate.Gate;

import gate.mimir.util.WebUtilsManager;
import gate.mimir.web.IndexTemplate
import gate.mimir.web.MimirConfigLoader
import gate.util.spring.ExtraGatePlugin

import java.util.concurrent.Executors

import org.apache.log4j.Logger
import org.codehaus.groovy.grails.commons.ConfigurationHolder as CH

class MimirWebGrailsPlugin {
    static Logger log = Logger.getLogger('gate.mimir.mimir-web')
  
    // the plugin version
    def version = "3.2.1-snapshot"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.5 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def author = "GATE Team"
    def authorEmail = "gate-users@lists.sourceforge.net"
    def title = "Mimir"
    def description = '''\
Multi-paradigm Information Management Index and Repository

see http://gate.ac.uk/family/mimir for full details
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/mimir-web"

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before 
    }

    def doWithSpring = {
      MimirConfigLoader.loadMimirConfig()
      
      // web utils manager
      webUtilsManager(WebUtilsManager)
      
      // thread pool for the search service
      searchThreadPool(Executors) { bean ->
        bean.factoryMethod = 'newCachedThreadPool'
        bean.destroyMethod = 'shutdown'
      }
      
      xmlns gate:'http://gate.ac.uk/ns/spring'
      // take <gate:init> attributes from configuration
      gate.init(CH.config.gate.mimir.gateInit)
      
      // Mimir plugins
      if(application.warDeployed) {
        // we are in a WAR deployment, load all plugins listed in WEB-INF/mimir-plugins.xml
        def pluginsXml = null
        application.parentContext.getResource("WEB-INF/mimir-plugins.xml")?.inputStream.withStream {
          pluginsXml = new XmlSlurper().parse(it)
        }
        if(pluginsXml?.plugin) {
          for(plugin in pluginsXml.plugin) {
            "mimirPlugin-${plugin}"(ExtraGatePlugin) {
              location = "WEB-INF/mimir-plugins/${plugin}"
            }
          }
        }
        // and all other plugins whose locations are absolute URLs
        if(CH.config.gate.mimir.plugins) {
          for(loc in CH.config.gate.mimir.plugins.entrySet()) {
            if(loc.value =~ /^[a-z]{2,}:/) {
              "mimirPlugin-${loc.key}"(ExtraGatePlugin) {
                location = loc.value
              }
            }
          }
        }
      } else {
        // run-app - load the configured plugins directly
        if(CH.config.gate.mimir.plugins) {
          URI baseURI = new File(System.getProperty('user.dir')).toURI()
          for(loc in CH.config.gate.mimir.plugins.entrySet()) {
            "mimirPlugin-${loc.key}"(ExtraGatePlugin) {
              location = baseURI.resolve(loc.value).toString()
            }
          }
        }
      }
      
      // the default query tokeniser (can be overridden in the
      // host app's resources.xml/groovy
      gate.'saved-application'(id:"queryTokeniser",
        location:CH.config.gate.mimir.queryTokeniserGapp.toString())
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
    }

    def doWithApplicationContext = { applicationContext ->
      if(IndexTemplate.count() == 0) {
        log.info("No index templates found in database, adding default one")

        def defaultHelper = 'gate.mimir.db.DBSemanticAnnotationHelper'
        try {
          Class.forName(defaultHelper, true, Gate.classLoader)
        } catch(Exception e) {
          // db not loaded, try ordi
          defaultHelper = 'gate.mimir.ordi.ORDISemanticAnnotationHelper'
          try {
            Class.forName(defaultHelper, true, Gate.classLoader)
          } catch(Exception e2) {
            defaultHelper = 'com.example.MySemanticAnnotationHelper'
          }
        }
        
        def defaultIndexConfig = """\
import gate.creole.ANNIEConstants
import gate.mimir.index.OriginalMarkupMetadataHelper
import ${defaultHelper} as DefaultHelper

tokenASName = "mimir"
tokenAnnotationType = ANNIEConstants.TOKEN_ANNOTATION_TYPE
tokenFeatures = {
  string()
  category()
  root()
}

semanticASName = "mimir"
semanticAnnotations = {
  index {
    annotation helper:new DefaultHelper(annType:'Sentence')
  }
  index {
    annotation helper:new DefaultHelper(annType:'Person', nominalFeatures:["gender"])
    annotation helper:new DefaultHelper(annType:'Location', nominalFeatures:["locType"])
    annotation helper:new DefaultHelper(annType:'Organization', nominalFeatures:["orgType"])
    annotation helper:new DefaultHelper(annType:'Date', integerFeatures:["normalized"])
  }
}
documentRenderer = new OriginalMarkupMetadataHelper()
documentMetadataHelpers = [documentRenderer]
"""
        IndexTemplate.withTransaction {
          def defaultTemplate = new IndexTemplate(
              name:'default',
              comment:'The default index configuration',
              configuration:defaultIndexConfig)
    
          if(!defaultTemplate.save(flush:true)) {
            log.warn("Couldn't save default index template")
          }
        }
      }

      // initialise the index services, in the right order
      applicationContext.localIndexService.init()
      applicationContext.remoteIndexService.init()
      applicationContext.federatedIndexService.init()
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }
    
}
