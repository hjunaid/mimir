import grails.plugin.springsecurity.SecurityConfigType;

// locations to search for config files that get merged into the main config
// config files can either be Java properties files or ConfigSlurper scripts

// grails.config.locations = [ "classpath:${appName}-config.properties",
//                             "classpath:${appName}-config.groovy",
//                             "file:${userHome}/.grails/${appName}-config.properties",
//                             "file:${userHome}/.grails/${appName}-config.groovy"]

// if(System.properties["${appName}.config.location"]) {
//    grails.config.locations << "file:" + System.properties["${appName}.config.location"]
// }

// load the external local config file (generated by the cloud-init scripts
grails.config.locations = []
if(new File("/etc/mimir/mimir-local.groovy").exists()){
  grails.config.locations << "file:/etc/mimir/mimir-local.groovy"
}
if(new File("mimir-config.groovy").exists()){
  grails.config.locations << "file:mimir-config.groovy"
}

grails{
  /**
   * Grails Security config
   */
  plugin{
    springsecurity {
      userLookup.userDomainClassName = 'gate.mimir.security.User'
      userLookup.authorityJoinClassName = 'gate.mimir.security.UserRole'
      authority.className = 'gate.mimir.security.Role'
      // backwards compatibility with existing databases
      password.algorithm='SHA-256'
      password.hash.iterations = 1
      requestMap.className = 'gate.mimir.security.Requestmap'
      requestMap.urlField = 'url'
      requestMap.configAttributeField = 'configAttribute'
      securityConfigType = SecurityConfigType.Requestmap
          
      // allow access to unspecified resources
      rejectIfNoRule = true
      
      /** Role hierarchy */
      roleHierarchy =
'''\
ROLE_ADMIN > ROLE_MANAGER
ROLE_ADMIN > ROLE_USER
ROLE_MANAGER > ROLE_USER
'''
      // allow Basic HTTP auth
      useBasicAuth = true
      basic.realmName = "GATECloud.net Mímir Server"
    
    //...but only for the search, manage, and getFile actions
      filterChain.chainMap = [
        '/*/search/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
        '/*/manage/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
        '/admin/indexDownload/getFile/**': 'JOINED_FILTERS,-exceptionTranslationFilter',
        '/**': 'JOINED_FILTERS,-basicAuthenticationFilter,-basicExceptionTranslationFilter'
      ]
    }
  }
}

gate.mimir.runningOnCloud = false

// If supplied, this value is used to override the scheme, server name, and port
// parts of the index URL. This can be used to make cloud-based installations 
// show their private URl here instead of the one obtained from the current 
// request 
// gate.mimir.indexUrlBase = 'http://aws-mimir-server.change-me.example.com:8080/'

// The temporary directory gets set to one of the ephemeral partitions by the
// cloud-init scripts. This is a default value used during development
gate.mimir.tempDir = '/data-local/mimir/temp'

// The initial value for the admin password. This would normally get overridden
// by the external config file. 
gate.mimir.defaultAdminPassword = 'not set'

grails.project.groupId = appName // change this to alter the default package name and Maven publishing destination
grails.mime.file.extensions = true // enables the parsing of file extensions from URLs into the request format
grails.mime.use.accept.header = false

grails.mime.types = [ html: [
    'text/html',
    'application/xhtml+xml'
  ],
  xml: [
    'text/xml',
    'application/xml'
  ],
  text: 'text/plain',
  js: 'text/javascript',
  rss: 'application/rss+xml',
  atom: 'application/atom+xml',
  css: 'text/css',
  csv: 'text/csv',
  all: '*/*',
  json: [
    'application/json',
    'text/json'
  ],
  form: 'application/x-www-form-urlencoded',
  multipartForm: 'multipart/form-data'
]

// URL Mapping Cache Max Size, defaults to 5000
//grails.urlmapping.cache.maxsize = 1000

// What URL patterns should be processed by the resources plugin
grails.resources.adhoc.patterns = ['/images/*', '/css/*', '/js/*', '/plugins/*']

// The default codec used to encode data with ${}
grails.views.default.codec = "none" // none, html, base64
grails.views.gsp.encoding = "UTF-8"
grails.converters.encoding = "UTF-8"
// enable Sitemesh preprocessing of GSP pages
grails.views.gsp.sitemesh.preprocess = true
// scaffolding templates configuration
grails.scaffolding.templates.domainSuffix = 'Instance'


// Set to false to use the new Grails 1.2 JSONBuilder in the render method
grails.json.legacy.builder = false
// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true
// packages to include in Spring bean scanning
grails.spring.bean.packages = []
// whether to disable processing of multi part requests
grails.web.disable.multipart=false

// request parameters to mask when logging exceptions
grails.exceptionresolver.params.exclude = ['password']

// configure auto-caching of queries by default (if false you can cache individual queries with 'cache: true')
grails.hibernate.cache.queries = false


// set per-environment serverURL stem for creating absolute links
environments {
  development { 
    // whether to install the java.util.logging bridge for sl4j. Disable for AppEngine!
    grails.logging.jul.usebridge = true
    // grails.serverURL = "http://localhost:8080/${appName}" 
  }
  
  production { 
    // grails.serverURL = "http://www.changeme.com" 
  }
  
  test { 
    // grails.serverURL = "http://localhost:8080/${appName}" 
  }
}

// log4j configuration
log4j.main = {
  // Example of changing the log pattern for the default console
  // appender:
  //
  //appenders {
  //    console name:'stdout', layout:pattern(conversionPattern: '%c{2} %m%n')
  //}

  info 'gate'
  
  debug 'grails.app.services.gate.mimir',
        'grails.app.controllers.gate.mimir',
        'grails.app.domain.gate.mimir',
        'grails.app.taglib.gate.mimir'
        
//        'grails.app.service.grails.plugins.springsecurity',
//        'grails.app.controller.grails.plugins.springsecurity',
//        'grails.app.domain.grails.plugins.springsecurity',
//        'org.springframework.security'
        
  error  'org.codehaus.groovy.grails.web.servlet',  //  controllers
      'org.codehaus.groovy.grails.web.pages', //  GSP
      'org.codehaus.groovy.grails.web.sitemesh', //  layouts
      'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
      'org.codehaus.groovy.grails.web.mapping', // URL mapping
      'org.codehaus.groovy.grails.commons', // core / classloading
      'org.codehaus.groovy.grails.plugins', // plugins
      'org.codehaus.groovy.grails.orm.hibernate', // hibernate integration
      'org.springframework',
      'org.hibernate',
      'net.sf.ehcache.hibernate'

  warn   'org.mortbay.log'
}
