This is the top level directory for the GATE Mímir source tree, containing the 
following directories:

- mimir-core: the core Mímir Java library, used by all components of Mímir.
- mimir-client: Java library for connecting to a remote Mímir server.
- doc: the Mímir user guide (LaTeX source and built PDF)
- mimir-web: a Grails (http://grails.org) plugin providing Mímir
  functionality for Grails-base web applications.
- plugins: Mímir plugins providing various Semantic Annotation Helper (SAH) 
  implementations.
  - db-h2: generic SAH based on the H2 relational database
    (http://www.h2database.com/) 
  - measurements: specialised SAH providing advanced support for Measurement
    annotations
  - ordi: *deprecated* generic SAH implementation using OWLIM via ORDI.
  - sesame: generic SAH implementation using OWLIM via Sesame.
  - sparql: SAH implementation that uses semantic queries against a SPARQL
    end-point to filter the results of standard Mímir queries. 
- mimir-test: Unit tests for mimir-core and mimir plugins.
- mimir-demo (automatically generated using Apache Ant): a very simple Grails
  application demonstrating the use of the Mímir Grails plugin to create a
  fully-working web application. 


The build.xml file is the ANT build file. See the user guide (inside the doc
directory) for details of how to use it.



