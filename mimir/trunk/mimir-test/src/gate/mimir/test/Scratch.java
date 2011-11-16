package gate.mimir.test;

import it.unimi.dsi.fastutil.IndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntArrayPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import it.unimi.dsi.mg4j.search.score.BM25FScorer;
import it.unimi.dsi.mg4j.search.score.BM25Scorer;
import it.unimi.dsi.mg4j.search.score.CountScorer;
import it.unimi.dsi.mg4j.search.score.DelegatingScorer;
import it.unimi.dsi.mg4j.search.score.Scorer;
import it.unimi.dsi.mg4j.search.score.TfIdfScorer;

import java.io.File;
import java.text.NumberFormat;
import java.util.Arrays;

import gate.Gate;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.QueryParser;
import gate.mimir.search.score.BindingScorer;
import gate.mimir.search.score.MimirScorer;

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
  
  /**
   * Version that exercises the scorers 
   * @param args
   */
  public static void mainScore(String[] args) throws Exception {
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
    String query = "{Measurement}";
    QueryEngine qEngine = new QueryEngine(new File(args[0]));
    QueryNode qNode = QueryParser.parse(query);
    QueryExecutor qExecutor = qNode.getQueryExecutor(qEngine);
    // TfIdfScorer scorer = new TfIdfScorer();
    // BM25Scorer scorer = new BM25Scorer();
    // CountScorer innerScorer = new CountScorer();
    // MimirScorer scorer = new DelegatingScoringQueryExecutor(innerScorer);
    MimirScorer scorer = new BindingScorer(2, 0.9);
    scorer.wrap(qExecutor);
    // TestUtils.dumpResultsToFile(qExecutor, new File("results.txt"));
    int latestDoc = scorer.nextDocument(-1);
    while(latestDoc >= 0) {
      System.out.println("Doc " + latestDoc + ", score: " + scorer.score());
      latestDoc = scorer.nextDocument(-1);
    }
    qEngine.close();
  }  
  
  public static void mainPrioQueue(String[] args) throws Exception {
    // simulate a stream of documents, in increasing documentId order and random scores
    int docCount = 10000;
    int[] inputDocIds = new int[docCount];
    final double[] inputDocScores = new double[docCount];
    for(int i = 0; i < inputDocIds.length; i++) {
      int prevDocID = i == 0 ? 0 : inputDocIds[i -1];
      inputDocIds[i] = (int)(prevDocID + (Math.random() * 10));
      inputDocScores[i] = Math.random() * 100;
      if(inputDocIds[i] % 5 == 0) {
        // multiples of 5 are good
        inputDocScores[i] += 100;
      }
    }
    
    // perform the filtering
    // how many documents are we keeping
    int maxSize = 10;
    // document IDs, sorted by score, highest first
    IntPriorityQueue topDocs = new IntHeapPriorityQueue(maxSize, new IntComparator() {
      @Override
      public int compare(Integer o1, Integer o2) {
        return compare(o1.intValue(), o2.intValue());
      }
      
      @Override
      public int compare(int k1, int k2) {
        double d1 = inputDocScores[k1];
        double d2 = inputDocScores[k2];
        if(d1 > d2) return 1;
        else if(d1 < d2) return -1;
        return 0;
      }
    });
    
    for(int i = 0; i <  inputDocIds.length; i++) {
      if(topDocs.size() < maxSize) {
        topDocs.enqueue(i);
      } else {
        if(inputDocScores[i] > inputDocScores[topDocs.first()]) {
          topDocs.dequeueInt();
          topDocs.enqueue(i);
        }
      }
    }
    // extract the results
    while(!topDocs.isEmpty()) {
      int smallestDoc = topDocs.dequeue();
      System.out.println("Doc id: " + inputDocIds[smallestDoc] + ", score: " + inputDocScores[smallestDoc]);
    }
  }
  
  
  public static void main(String[] args) throws Exception {
    // simulate a stream of documents, in increasing documentId order and random scores
    int docCount = 1000000;
    int[] inputDocIds = new int[docCount];
    double[] inputDocScores = new double[docCount];
    double maxScore = 0;
    for(int i = 0; i < inputDocIds.length; i++) {
      int prevDocID = i == 0 ? 0 : inputDocIds[i -1];
      inputDocIds[i] = (int)(prevDocID + 1 + (Math.random() * 10));
      inputDocScores[i] = Math.random() * 100;
      if(inputDocIds[i] % 5 == 0) {
        // multiples of 5 are good
        inputDocScores[i] += 100;
      }
      if(inputDocScores[i] > maxScore) maxScore = inputDocScores[i];
    }
    
    // how many documents are we keeping
    int maxSize = 10000;
    
    int docIds[] = new int[maxSize];
    final double docScores[] = new double[maxSize];
    int docIdWriteIndex = 0;
    int docsByScore[] = new int[maxSize];
    Arrays.fill(docIds, -1);
    Arrays.fill(docScores, 0.0d);
    Arrays.fill(docsByScore, -1);
    
    for(int i = 0; i < inputDocIds.length; i++) {
      int newDocId = inputDocIds[i];
      double newDocScore = inputDocScores[i];
      int smallestDoc = docsByScore[0];
      double smallestScore = smallestDoc == -1 ? 0.0 : docScores[smallestDoc];
      if(docIdWriteIndex < docIds.length || newDocScore > smallestScore) {
        // a new document to store
        if(docIdWriteIndex == docIds.length) {
          // we need to remove the smallest doc
          for(int j = smallestDoc; j < docIds.length -1; j++) {
            docIds[j] = docIds[j + 1];
            docScores[j] = docScores[j + 1];
          }
          docIds[docIds.length -1] = -1;
          docScores[docScores.length -1] = -1;
          for(int j = 0; j < docScores.length -1; j++){
            // the smallest scoring document disappears;
            int newIndex = docsByScore[j + 1];
            if(newIndex > smallestDoc) newIndex--;
            docsByScore[j] = newIndex;
          }
          docsByScore[docsByScore.length - 1] = -1;
          docIdWriteIndex--;
        }
        docIds[docIdWriteIndex] = newDocId;
        docScores[docIdWriteIndex] = newDocScore;
        
        // find the rank for the new doc
        int rank = 0;
        while(docsByScore[rank] >= 0 && 
              newDocScore > docScores[docsByScore[rank]]){
          rank++;
        }
        for(int j = docIdWriteIndex; j > rank; j--) {
          docsByScore[j] = docsByScore[j - 1];
        }
        docsByScore[rank] = docIdWriteIndex;
        docIdWriteIndex++;
      }
    }
    
    // extract the results
//    System.out.print("Retained document IDs:");
//    for(int docId : docIds) System.out.print(" " + docId);
    System.out.println("\nID\tScore");
    for(int i = docsByScore.length - 1; i >= docsByScore.length - 10 ; i--) {
      System.out.println(docIds[docsByScore[i]] + "\t" + docScores[docsByScore[i]]);
    }
    System.out.println("Max score: " + maxScore);
  }
  
}
