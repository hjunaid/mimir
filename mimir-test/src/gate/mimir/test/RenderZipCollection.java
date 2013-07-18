package gate.mimir.test;

import gate.Gate;
import gate.mimir.DocumentRenderer;
import gate.mimir.IndexConfig;
import gate.mimir.index.Indexer;
import gate.mimir.index.mg4j.zipcollection.DocumentData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.input.CloseShieldInputStream;

public class RenderZipCollection {
  
  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
    Gate.setGateHome(new File("gate-home"));
    Gate.setUserConfigFile(new File("gate-home/user-gate.xml"));
    Gate.init();
    // load the tokeniser plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("gate-home/plugins/ANNIE-tokeniser").toURI().toURL());
    // load the DB plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/db-h2").toURI().toURL());
//    Gate.getCreoleRegister().registerDirectories(
//      new File("../plugins/sesame").toURI().toURL());    
    // load the measurements plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/measurements").toURI().toURL());
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/sparql").toURI().toURL());

    File indexDir = new File(args[0]);
    File outputDir = new File(args[1]);
    // load the IndexConfig to obtain the right renderer
    IndexConfig indexConfig =
            IndexConfig.readConfigFromFile(new File(indexDir,
                    Indexer.INDEX_CONFIG_FILENAME), indexDir);
    DocumentRenderer renderer = indexConfig.getDocumentRenderer();

    // enumerate the zip collection files
    File mg4jDir = new File(indexDir, Indexer.MG4J_INDEX_DIRNAME);
    File[] zipCollectionFiles = mg4jDir.listFiles(new FilenameFilter() {
      
      @Override
      public boolean accept(File dir, String name) {
        return name.startsWith(Indexer.MIMIR_COLLECTION_BASENAME)
                && name.endsWith(Indexer.MIMIR_COLLECTION_EXTENSION);
      }
    });

    
    for(File zf : zipCollectionFiles) {
      // for each input file, create a corresponding output file
      File outFile = new File(outputDir, "rendered-" + zf.getName());
      try(ZipInputStream collIn = new ZipInputStream(new FileInputStream(zf));
          ZipOutputStream rendOut = new ZipOutputStream(new FileOutputStream(outFile))) {
        ZipEntry inEntry;
        while((inEntry = collIn.getNextEntry()) != null) {
          // for each document, load the DocumentData from the original zip
          DocumentData dd = null;
          try(ObjectInputStream ois = new ObjectInputStream(new CloseShieldInputStream(collIn))) {
            dd = (DocumentData)ois.readObject();
          }
          if(dd != null) {
            // and write the rendered form to the new zip (in UTF-8)
            ZipEntry outEntry = new ZipEntry(inEntry.getName());
            rendOut.putNextEntry(outEntry);
            try(BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FilterOutputStream(rendOut) {
              @Override
              public void close() throws IOException {
                flush();
                ((ZipOutputStream)out).closeEntry();
              }
              
            }, "UTF-8"))) {
              renderer.render(dd, null, w);
            }
          } else {
            System.out.println("Error converting document " + inEntry.getName());
          }
        }
      }
    }
  }

}
