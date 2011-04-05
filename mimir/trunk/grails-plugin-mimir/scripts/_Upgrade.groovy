//
// This script is executed by Grails during application upgrade ('grails upgrade'
// command). This script is a Gant script so you can use all special variables
// provided by Gant (such as 'baseDir' which points on project base dir). You can
// use 'ant' to access a global instance of AntBuilder
//
// For example you can create directory under project tree:
//
//    ant.mkdir(dir:"${basedir}/grails-app/jobs")
//

// copy in the default URL mappings if the target project does not already
// contain them
File targetMappingsFile = new File(new File(new File(basedir, "grails-app"), "conf"), "MimirUrlMappings.groovy")
def toFile = "${basedir}/grails-app/conf/MimirUrlMappings.groovy"
if(targetMappingsFile.exists()) {
  println "MimirUrlMappings.groovy already exists - new mappings"
  println "copied to MimirUrlMappings.groovy.new - please compare them."
  toFile += ".new"
}
ant.copy(tofile:toFile,
  file:"${pluginBasedir}/src/templates/conf/MimirUrlMappings.groovy")