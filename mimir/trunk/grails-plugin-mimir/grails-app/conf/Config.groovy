/*
*  Config.groovy
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
// external configuration location
grails.config.locations = ["file:mimir.groovy"]
if(System.properties["mimir.config"]) {
  grails.config.locations << "file:" + System.properties["mimir.config"]
}
grails.config.locations << "classpath:mimir.groovy"


grails.mime.file.extensions = true // enables the parsing of file extensions from URLs into the request format
grails.mime.use.accept.header = false
grails.mime.types = [ html: ['text/html','application/xhtml+xml'],
                      xml: ['text/xml', 'application/xml'],
                      text: 'text/plain',
                      js: 'text/javascript',
                      rss: 'application/rss+xml',
                      atom: 'application/atom+xml',
                      css: 'text/css',
                      csv: 'text/csv',
                      all: '*/*',
                      json: ['application/json','text/json'],
                      form: 'application/x-www-form-urlencoded',
                      multipartForm: 'multipart/form-data'
                    ]
// The default codec used to encode data with ${}
grails.views.default.codec="none" // none, html, base64
grails.views.gsp.encoding="UTF-8"
grails.converters.encoding="UTF-8"

// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true

// set per-environment serverURL stem for creating absolute links
// log4j configuration
log4j = {
    root {
        info 'stdout'
        additivity = true
    }

    error  'org.codehaus.groovy.grails.web.servlet',  //  controllers
         'org.codehaus.groovy.grails.web.pages', //  GSP
         'org.codehaus.groovy.grails.web.sitemesh', //  layouts
         'org.codehaus.groovy.grails."web.mapping.filter', // URL mapping
         'org.codehaus.groovy.grails."web.mapping', // URL mapping
         'org.codehaus.groovy.grails.commons', // core / classloading
         'org.codehaus.groovy.grails.plugins', // plugins
         'org.codehaus.groovy.grails.orm.hibernate', // hibernate integration
         'org.springframework',
         'org.hibernate'
    warn 'org.mortbay.log'
    info 'it.unimi' // MG4J and friends

    environments {
      development {
        debug 'gate.mimir', // mimir
              'gate.mimir.gus', // mimir
              'grails.app.service.gate.mimir.gus'  //GUS search service
      }
      production {
        info 'gate.mimir', // mimir
             'grails.app.service.gate.mimir.gus'  //GUS search service
      }
    }
}          

//mimir config
gate {
  mimir {
    defaultIndexConfig = '''\
import gate.creole.ANNIEConstants
import gate.mimir.index.OriginalMarkupMetadataHelper

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
    Sentence()
  }
  index {
    Abstract(nominalFeatures:["lang"])
    Assignee()
    ClassificationIPCR(nominalFeatures:["status"])
    InventionTitle(nominalFeatures:["lang", "status"])
    Inventor(nominalFeatures:["format", "status"])
    PatentDocument(integerFeatures:["date"], textFeatures:["ucid"])
    PriorityClaim(textFeatures:["ucid"])
  }
}
documentRenderer = new OriginalMarkupMetadataHelper()
documentMetadataHelpers = [documentRenderer]
'''

    // base directory that relative index paths will be resolved against
    indexBaseDirectory = '/path/to/the/base/index/dir'
  }
}
