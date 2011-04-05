//
// This script is executed by Grails after plugin was installed to project.
// This script is a Gant script so you can use all special variables provided
// by Gant (such as 'baseDir' which points on project base dir). You can
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

File targetMimirConfig = new File(new File(new File(basedir, "grails-app"), "conf"), "MimirConfig.groovy")
if(!targetMimirConfig.exists()) {
  println "Installing default Mimir configuration"
  ant.copy(tofile:targetMimirConfig, file:"${pluginBasedir}/src/templates/conf/MimirConfig.groovy")
  println "Installing default Mimir gate-home"
  ant.copy(todir:"${basedir}/web-app/WEB-INF") {
    fileset(dir:"${pluginBasedir}/src/templates", includes:'gate-home/**')
  }
} else {
  println "MimirConfig.groovy already exists, not overwriting"
}