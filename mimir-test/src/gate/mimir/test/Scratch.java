package gate.mimir.test;

import it.unimi.dsi.mg4j.search.score.BM25FScorer;
import it.unimi.dsi.mg4j.search.score.BM25Scorer;
import it.unimi.dsi.mg4j.search.score.CountScorer;
import it.unimi.dsi.mg4j.search.score.DelegatingScorer;
import it.unimi.dsi.mg4j.search.score.Scorer;
import it.unimi.dsi.mg4j.search.score.TfIdfScorer;

import java.io.File;
import java.text.NumberFormat;

import gate.Gate;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.QueryParser;

public class Scratch {

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
    // load the measurements plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/measurements").toURI().toURL());
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/sparql").toURI().toURL());
    
    QueryEngine qEngine = new QueryEngine(new File(args[0]));
//    String query = "the";
    String query = "{Document}";
    QueryNode qNode = QueryParser.parse(query);
    long start = System.currentTimeMillis();
    NumberFormat nf = NumberFormat.getNumberInstance();
    long startLocal = System.currentTimeMillis();
    QueryExecutor qExecutor = qNode.getQueryExecutor(qEngine);
    int latestDoc = qExecutor.nextDocument(-1);
    int totalHitCount = 0;
    int docCount = 0;
    while(latestDoc >= 0) {
      docCount++;
      int hitCount = 0;
      while(qExecutor.nextHit() != null) hitCount++;
      totalHitCount += hitCount;
      System.out.println("Doc " + latestDoc + ", hits: " + hitCount);
      latestDoc = qExecutor.nextDocument(-1);
    }
    System.out.println("Found " + nf.format(totalHitCount) + " hits in " +
      nf.format(docCount) + " documents, in " +
      nf.format(System.currentTimeMillis() - startLocal) + " ms.");
    qExecutor.close();
    System.out.println("Total time " +
      nf.format(System.currentTimeMillis() - start) + " ms.");
    qEngine.close();
  }
}
