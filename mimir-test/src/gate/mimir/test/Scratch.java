package gate.mimir.test;

import it.unimi.dsi.fastutil.IndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntArrayPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import it.unimi.di.big.mg4j.index.Index;
import it.unimi.di.big.mg4j.index.IndexIterator;
import it.unimi.di.big.mg4j.index.IndexReader;
import it.unimi.di.big.mg4j.search.score.BM25FScorer;
import it.unimi.di.big.mg4j.search.score.BM25Scorer;
import it.unimi.di.big.mg4j.search.score.CountScorer;
import it.unimi.di.big.mg4j.search.score.DelegatingScorer;
import it.unimi.di.big.mg4j.search.score.Scorer;
import it.unimi.di.big.mg4j.search.score.TfIdfScorer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.configuration.ConfigurationException;

import gate.Document;
import gate.Factory;
import gate.Gate;
import gate.Utils;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.IndexConfig;
import gate.mimir.index.OriginalMarkupMetadataHelper;
import gate.mimir.index.mg4j.GATEDocument;
import gate.mimir.index.mg4j.MimirDirectIndexBuilder;
import gate.mimir.index.mg4j.zipcollection.DocumentData;
import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;
import gate.mimir.search.QueryRunner;
import gate.mimir.search.RankingQueryRunnerImpl;
import gate.mimir.search.RemoteQueryRunner;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.parser.QueryParser;
import gate.mimir.search.score.BindingScorer;
import gate.mimir.search.score.DelegatingScoringQueryExecutor;
import gate.mimir.search.score.MimirScorer;
import gate.mimir.search.terms.AndTermsQuery;
import gate.mimir.search.terms.DocumentTermsQuery;
import gate.mimir.search.terms.DocumentsAndTermsQuery;
import gate.mimir.search.terms.DocumentsOrTermsQuery;
import gate.mimir.search.terms.LimitTermsQuery;
import gate.mimir.search.terms.OrTermsQuery;
import gate.mimir.search.terms.SortedTermsQuery;
import gate.mimir.search.terms.TermsQuery;
import gate.mimir.search.terms.TermsResultSet;
import gate.mimir.tool.WebUtils;
import gate.mimir.util.MG4JTools;
import gate.util.GateException;

public class Scratch {

  public static void main (String[] args) throws Exception {
    mainOMMH(args);
//     mainSimple(args);
//     mainDirectIndexes(args);
//    mainBuildDirectIndex(args);
//    mainQueryIndex(args);
//    mainRemote(args);
  }
  
  /**
   * Interactive tool for querying a MG4J index (e.g. a Mímir sub-index, or a 
   * Mímir sub-index batch).
   * 
   * @param args
   * @throws NoSuchMethodException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws InstantiationException 
   * @throws ClassNotFoundException 
   * @throws URISyntaxException 
   * @throws IOException 
   * @throws SecurityException 
   * @throws ConfigurationException 
   */
  public static void mainQueryIndex(String[] args) throws ConfigurationException, SecurityException, IOException, URISyntaxException, ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    IndexReaderPool termSource = null;
    
    // open the term supplying index
    URI termsIndexUri = new File("/data/mimir-indexes/index-fastvac-1M.mimir/mg4j/mimir-token-2").toURI();
    Index termsIndex = MG4JTools.openMg4jIndex(termsIndexUri);
    termSource = new IndexReaderPool(termsIndex, termsIndexUri);

    
    if(args == null || args.length < 2) {
      System.out.println("Usage:\njava Scratch indexDir indexName...\n" +
      		"where indexDir is a mimir index directory, indexName is the basename of an index file (the file name without any extension).");
      return;
    }
    // open the MG4J index
    URI[] indexURIs = new URI[args.length - 1];
    File mg4jDir = new File(new File(args[0]), "mg4j");
    IndexReaderPool[] readerPools = new IndexReaderPool[args.length - 1];
    for(int i = 0; i < indexURIs.length; i++) {
      indexURIs[i] = new File(mg4jDir, args[i + 1]).toURI();
      Index theIndex = MG4JTools.openMg4jIndex(indexURIs[i]);
      readerPools[i] = new IndexReaderPool(theIndex, indexURIs[i]);      
    }
    
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("Query:");
    String line = in.readLine();
    while(line != null && line.length() > 0) {
      for(int i = 0; i < readerPools.length; i++) {
        System.out.print("From " + indexURIs[i] + ":\n\t");
        
        IndexReader indexReader = readerPools[i].borrowReader();
        try {
          IndexIterator indexIter = indexReader.documents(Long.parseLong(line));
          long docId = indexIter.nextDocument();
          boolean first = true;
          while(docId > 0 && docId != IndexIterator.END_OF_LIST) {
            if(first) first = false;
            else System.out.print(", ");
            System.out.print(Long.toString(docId));
            if(termSource != null) {
              System.out.print("('" + termSource.getTerm(docId) + "')");
            }
            docId = indexIter.nextDocument();
          }
          System.out.println("\n");
        } finally {
          readerPools[i].returnReader(indexReader);
        }
      }
      System.out.print("Query:");
      line = in.readLine();
    }
  }
  
  public static void mainSimple(String[] args) throws Exception {
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
    if(args.length  < 2) {
      System.err.println(
        "Parameters: <index directory> <sub-index basename>...");
      return;
    }
    try {
      for(int i = 1; i <args.length; i++) {
        System.out.println("Inverting index " + args[i]);
        MimirDirectIndexBuilder mdib = new MimirDirectIndexBuilder(
          new File(args[0]), args[i]);
        mdib.run();
        System.out.println("\n===============================\n");
      }
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Main version for testing direct indexes
   * @param args
   * @throws Exception sometimes 
   */
  public static void mainDirectIndexes(String[] args) throws Exception {
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
    
    TermsQuery query = null;
    
//    query = new DocumentTermsQuery("root", IndexType.TOKENS, 
//      true, true, DocumentTermsQuery.NO_LIMIT, 0);
//    printTermQuery(query, qEngine);
//    System.out.println("\n=======================================");
//    query = new DocumentTermsQuery("root", IndexType.TOKENS, 
//      true, true, DocumentTermsQuery.NO_LIMIT, 1);
//    printTermQuery(query, qEngine);
//    System.out.println("\n=======================================");
    
//    query = new DocumentsOrTermsQuery("root", IndexType.TOKENS, 
//      true, true, TermsQuery.NO_LIMIT, 0, 1);
//    printTermQuery(query, qEngine);
//    System.out.println("\n=======================================");
    
//    TermsQuery q1 = new DocumentTermsQuery("root", IndexType.TOKENS, 
//        true, true, TermsQuery.NO_LIMIT, 0);
//    TermsQuery q2 = new DocumentTermsQuery("root", IndexType.TOKENS, 
//      true, true, TermsQuery.NO_LIMIT, 1);
//    query = new OrTermsQuery(true, true, TermsQuery.NO_LIMIT, q1, q2);
//    
//    query = new LimitTermsQuery(new SortedTermsQuery(query), 100);
    
    query = new LimitTermsQuery(
      new SortedTermsQuery(
      new DocumentsOrTermsQuery("root", IndexType.TOKENS, true, false, 0, 1, 2))
      , 100);
    printTermQuery(query, qEngine);
    
    System.out.println("\n=======================================");
    
    
    qEngine.close();
  }
  
  /**
   * Scratch code for using the remote query runner
   * @param args 2 string: index URL and query
   * @throws Exception
   */
  public static void mainRemote(String[] args) throws Exception {
    if(args.length != 2) {
      System.out.println("Usage: Scratch indexUrl queryString");
      return;
    }
    RemoteQueryRunner rqr = new RemoteQueryRunner(args[0], args[1], null, new WebUtils());
    long docCount = rqr.getDocumentsCount();
    while(docCount < 0) {
      System.out.println("Working (found " + rqr.getDocumentsCurrentCount() + 
        " documents so far)");
      Thread.sleep(1000);
      docCount = rqr.getDocumentsCount();
    }
    System.out.println("Search complete; found: " + docCount + " documents.");
  }
  
  private static NumberFormat nf = NumberFormat.getNumberInstance();

  private static void printTermQuery(TermsQuery query, QueryEngine qEngine) throws IOException {

    long start = System.currentTimeMillis();
    TermsResultSet res = query.execute(qEngine);
    for(int  i = 0; i < res.termStrings.length; i++) {
      System.out.print(res.termStrings[i] + "\"\t");
      if(res.termLengths != null) {
        System.out.print("len:" + res.termLengths[i] + "\t");
      }
      if(res.termCounts != null) {
        System.out.print("cnt:" + res.termCounts[i]);
      }
      System.out.println();
    }
    
    System.out.println("Found " + nf.format(res.termStrings.length)
        + " hits in " + 
        nf.format(System.currentTimeMillis() - start) + " ms.");
  }
  
  
  public static void mainOMMH(String[] args) throws Exception {
    Gate.setGateHome(new File("gate-home"));
    Gate.setUserConfigFile(new File("gate-home/user-gate.xml"));
    Gate.init();
    // load the DB plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/db-h2").toURI().toURL());
    // load the measurements plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/measurements").toURI().toURL());
    // load the SPARQL plugin
    Gate.getCreoleRegister().registerDirectories(
      new File("../plugins/sparql").toURI().toURL());

    IndexConfig indexConfig = TestUtils.getTestIndexConfig(new File("/tmp"), 
        Class.forName("gate.mimir.db.DBSemanticAnnotationHelper", true, 
            Gate.getClassLoader()).asSubclass(
                AbstractSemanticAnnotationHelper.class));
    
    OriginalMarkupMetadataHelper ommh = new OriginalMarkupMetadataHelper( new HashSet<String>(Arrays.asList(
        new String[] {
            "b", "i", "li", "ol", "p", "sup", "sub", "u", "ul", "br", "div"})));
    
    Document doc = Factory.newDocument(new File(args[0]).toURI().toURL());
    GATEDocument gDoc = new GATEDocument(doc, indexConfig);
    String[] documentTokens = new String[gDoc.getTokenAnnots().length];
    for(int i = 0; i < documentTokens.length; i++) {
      documentTokens[i] = Utils.cleanStringFor(doc, gDoc.getTokenAnnots()[i]);
    }
    DocumentData docData = new DocumentData("document URI", "documentTitle", 
        documentTokens, gDoc.getNonTokens()); 
    
    ommh.documentStart(gDoc);
    ommh.documentEnd(gDoc, docData);
    
    System.out.println("\nOMMH Tags:\n" + 
        ((Map)docData.getMetadataField(
            OriginalMarkupMetadataHelper.class.getName())).get(
                OriginalMarkupMetadataHelper.TAGS_KEY) + 
         "\n");
    
    ommh.render(docData, new LinkedList<Binding>(), System.out);
  }
}
