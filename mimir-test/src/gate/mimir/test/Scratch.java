package gate.mimir.test;

import it.unimi.dsi.fastutil.IndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntArrayPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import it.unimi.dsi.big.mg4j.search.score.BM25FScorer;
import it.unimi.dsi.big.mg4j.search.score.BM25Scorer;
import it.unimi.dsi.big.mg4j.search.score.CountScorer;
import it.unimi.dsi.big.mg4j.search.score.DelegatingScorer;
import it.unimi.dsi.big.mg4j.search.score.Scorer;
import it.unimi.dsi.big.mg4j.search.score.TfIdfScorer;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.concurrent.Callable;

import gate.Gate;
import gate.mimir.index.mg4j.MimirDirectIndexBuilder;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;
import gate.mimir.search.QueryRunner;
import gate.mimir.search.RankingQueryRunnerImpl;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.QueryParser;
import gate.mimir.search.score.BindingScorer;
import gate.mimir.search.score.DelegatingScoringQueryExecutor;
import gate.mimir.search.score.MimirScorer;
import gate.mimir.search.terms.DocumentTermsQuery;
import gate.mimir.search.terms.TermsQuery;
import gate.mimir.search.terms.TermsResultSet;
import gate.util.GateException;

public class Scratch {

  
  
  public static void mainSimple(String[] args) throws Exception {
    Gate.setGateHome(new File("gate-home"));
    Gate.setUserConfigFile(new File("gate-home/user-gate.xml"));
    Gate.init();
    // load the tokeniser plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("gate-home/plugins/ANNIE-tokeniser").toURI().toURL());
    // load the DB plugin
//    Gate.getCreoleRegister().registerDirectories(
//      new File("../plugins/db-h2").toURI().toURL());
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/sesame").toURI().toURL());    
    // load the measurements plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/measurements").toURI().toURL());
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/sparql").toURI().toURL());
    
    QueryEngine qEngine = new QueryEngine(new File(args[0]));
//    String query = "the";
    String query = "{Document date > 20070000}";
    QueryNode qNode = QueryParser.parse(query);
    long start = System.currentTimeMillis();
    NumberFormat nf = NumberFormat.getNumberInstance();
    long startLocal = System.currentTimeMillis();
    QueryExecutor qExecutor = qNode.getQueryExecutor(qEngine);
    long latestDoc = qExecutor.nextDocument(-1);
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
  
  /**
   * Version that exercises the scorers 
   * @param args
   */
  public static void mainScorers(String[] args) throws Exception {
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
    // load the SPARQL plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/sparql").toURI().toURL());
    
    QueryEngine qEngine = new QueryEngine(new File(args[0]));
    qEngine.setScorerSource(new Callable<MimirScorer>() {
      @Override
      public MimirScorer call() throws Exception {
        return new BindingScorer(2, 0.9);
        // return new DelegatingScoringQueryExecutor(new TfIdfScorer());
        // return new DelegatingScoringQueryExecutor(new CountScorer());
        // return new DelegatingScoringQueryExecutor(new BM25Scorer());
      }
    });
    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    String query = null;
    do {
      try{
        System.out.print("? ");
        query = input.readLine();
        long start = System.currentTimeMillis();
        if(query == null || query.trim().length() == 0) break;
        QueryRunner qRunner = qEngine.getQueryRunner(query);
        
        while(qRunner.getDocumentsCount() < 0) {
          Thread.sleep(100);
        }
        double minScore = Double.MAX_VALUE;
        double maxScore = Double.MIN_VALUE;
        long docCount = qRunner.getDocumentsCount();
        for(int i = 0;  i < docCount; i++) {
          double score = qRunner.getDocumentScore(i);
          if(score < minScore) minScore = score;
          if(score > maxScore) maxScore = score;
          // exercise the runner
          qRunner.getDocumentID(i);
          qRunner.getDocumentTitle(i);
          qRunner.getDocumentURI(i);
          qRunner.getDocumentHits(i);
        }
        
        System.out.println(String.format(
          "Matched %d documents, scores %02.4f - %02.4f, in %02.2f seconds", 
          docCount, minScore, maxScore, 
          (double)(System.currentTimeMillis() - start)/1000));
        qRunner.close();
      }catch(Exception e) {
        e.printStackTrace(System.err);
      }
    } while (query != null);
    qEngine.close();
  }  
  
  
  /**
   * Main version for building direct indexes
   * @param args
   * @throws Exception sometimes 
   */
  public static void mainBuildDirectIndex(String[] args) throws Exception {
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
    // load the SPARQL plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/sparql").toURI().toURL());
    
    // the Mímir index dir from params
    if(args.length  != 2) {
      System.err.println(
        "Parameters: <index directory> <sub-index basename>");
      return;
    }
    try {
      MimirDirectIndexBuilder mdib = new MimirDirectIndexBuilder(new File(args[0]), args[1]);
      mdib.run();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Main version for testing direct indexes
   * @param args
   * @throws Exception sometimes 
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
    
    QueryEngine qEngine = new QueryEngine(new File(args[0]));
    NumberFormat nf = NumberFormat.getNumberInstance();
    
    long start = System.currentTimeMillis();
    TermsQuery query = new DocumentTermsQuery(1, "root", IndexType.TOKENS, 
      true, true, DocumentTermsQuery.NO_LIMIT);
    TermsResultSet res = query.execute(qEngine);
    for(int  i = 0; i < res.termIds.length; i++) {
      System.out.print(res.termIds[i] + "\t");
      if(res.termStrings != null) {
        System.out.print("\"" + res.termStrings[i] + "\"\t");
      }      
      if(res.termLengths != null) {
        System.out.print("len:" + res.termLengths[i] + "\t");
      }
      if(res.termCounts != null) {
        System.out.print("cnt:" + res.termCounts[i]);
      }
      System.out.println();
    }
    
    System.out.println("Found " + nf.format(res.termIds.length)
        + " hits in " + 
        nf.format(System.currentTimeMillis() - start) + " ms.");
    qEngine.close();
  }
  
}
