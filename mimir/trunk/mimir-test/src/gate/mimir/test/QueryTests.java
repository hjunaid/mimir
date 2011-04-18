/*
 *  QueryTests.java
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
package gate.mimir.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import gate.Document;
import gate.Gate;
import gate.mimir.IndexConfig;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.AndQuery;
import gate.mimir.search.query.AnnotationQuery;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.GapQuery;
import gate.mimir.search.query.OrQuery;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.query.RepeatsQuery;
import gate.mimir.search.query.SequenceQuery;
import gate.mimir.search.query.TermQuery;
import gate.mimir.search.query.WithinQuery;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A class with tests for the various Mimir query operators.
 */
public class QueryTests {
  
  /**
   * The QueryEngine used for the tests.
   */
  private static QueryEngine engine;
  

  
  private static String resultsPath;
  private static final String NEW_LINE = System.getProperty("line.separator");

  /**
   * Prepares the QueryEngine used by all tests.
   */
  @BeforeClass
  public static void oneTimeSetUp() throws Exception {
    Gate.setGateHome(new File("gate-home"));
    Gate.init();
    // load the tokeniser plugin
    Gate.getCreoleRegister().registerDirectories(new File("gate-home/plugins/ANNIE-tokeniser").toURI().toURL());
    // load the DB plugin
    Gate.getCreoleRegister().registerDirectories(new File("../plugins/db-h2").toURI().toURL());
    // load the measurements plugin
    Gate.getCreoleRegister().registerDirectories(new File("../plugins/measurements").toURI().toURL());
    
    File indexDir = File.createTempFile("mimir-index", null);
    indexDir.delete();
    // change this to use a different helper (e.g for the the ORDI one you 
    // would use: "gate.mimir.ordi.ORDISemanticAnnotationHelper")
    IndexConfig indexConfig = TestUtils.getTestIndexConfig(indexDir, 
            Class.forName("gate.mimir.db.DBSemanticAnnotationHelper", true, 
                Gate.getClassLoader()).asSubclass(SemanticAnnotationHelper.class));
    // now start indexing the documents
    Indexer indexer = new Indexer(indexConfig);
    String pathToZipFile = "data/gatexml-output.zip";
    File zipFile = new File(pathToZipFile);
    String fileURI = zipFile.toURI().toString();
    ZipFile zip = new ZipFile(pathToZipFile);
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while(entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if(entry.isDirectory()) {
        continue;
      }
      URL url = new URL("jar:" + fileURI + "!/" + entry.getName());
      Document doc = gate.Factory.newDocument(url, "UTF-8");
      indexer.indexDocument(doc);
    }
    indexer.close();
    engine = new QueryEngine(indexDir);
    resultsPath = "reports/query-results";
  }

  /**
   * Closes the shared QueryEngine.
   */
  @AfterClass
  public static void oneTimeTearDown() {
    if(engine != null) {
      File indexDir = engine.getIndexDir();
      engine.close();
      engine = null;
      // recursively delete index dir
      if(!TestUtils.deleteDir(indexDir)) {
        System.err.println("Could not delete index directory " + indexDir);
      }
    }
  }

  /**
   * Executes two sequence queries, one with gaps and one without and checks
   * that the results of one are included in the other one.
   * 
   * @throws IOException
   */
  @Test
  public void testSequenceQueryGaps() throws IOException {
    TermQuery tq1 = new TermQuery("string", "up");
    TermQuery tq2 = new TermQuery("string", "to");
    TermQuery tq3 = new TermQuery("string", "the");
    SequenceQuery sQuery = new SequenceQuery(null, tq1, tq2, tq3);
    SequenceQuery sQueryGaps =
            new SequenceQuery(new SequenceQuery.Gap[]{SequenceQuery
                    .getGap(1, 1)}, tq1, tq3);
    List<Binding>[] diff = TestUtils.calculateDiff(sQuery, sQueryGaps, engine);
    // second query is more permissive than first
    assertNotNull("The two queries returned the same result set!", diff);
    assertTrue("The non gaps query has results not included in the gaps one!",
            diff[0].isEmpty());
    assertTrue("The gaps query returned no additional hits!",
            diff[1].size() > 0);
  }

  /**
   * Executes two equivalent queries using {@link RepeatsQuery} and
   * {@link OrQuery} and compares the results.
   * 
   * @throws IndexException
   * @throws IOException
   */
  @Test
  public void testRepeatsAndOrQueries() throws IndexException, IOException {
    Map<String, String> empty = Collections.emptyMap();
    AnnotationQuery annQuery = new AnnotationQuery("Measurement", empty);
    RepeatsQuery rQuery = new RepeatsQuery(annQuery, 1, 3);
    OrQuery orQuery =
            new OrQuery(annQuery, new SequenceQuery(null, annQuery, annQuery),
                    new SequenceQuery(null, annQuery, annQuery, annQuery));
    List<Binding>[] diff = TestUtils.calculateDiff(rQuery, orQuery, engine);
    if(diff != null) {
      System.err.println(TestUtils.printDiffResults(diff, engine));
    }
    assertNull("Repeats query result different from equivalent OR query. "
            + "See system.err for details!", diff);
  }

  /**
   * Executes two equivalent queries using {@link SequenceQuery} and
   * {@link RepeatsQuery} and compares the results.
   * 
   * @throws IndexException
   * @throws IOException
   */
  @Test
  public void testSequenceAndRepeatsQueries() throws IndexException,
          IOException {
    Map<String, String> empty = Collections.emptyMap();
    AnnotationQuery annQuery = new AnnotationQuery("Measurement", empty);
    SequenceQuery sQuery =
            new SequenceQuery(null, annQuery, annQuery, annQuery);
    RepeatsQuery rQuery = new RepeatsQuery(annQuery, 3, 3);
    List<Binding>[] diff = TestUtils.calculateDiff(sQuery, rQuery, engine);
    if(diff != null) {
      System.err.println(TestUtils.printDiffResults(diff, engine));
    }
    assertNull("Repeats query result different from equivalent OR query. "
            + "See system.err for details!", diff);
  }

  /**
   * Executes three equivalent queries using different gap implementations
   * coming from {@link SequenceQuery}, {@link TermQuery} and {@link GapQuery}
   * and compares the results.
   * 
   * @throws IndexException
   * @throws IOException
   * @throws IOException
   */
  @Test
  public void testGapImplementations() throws IndexException, IOException {
    TermQuery tq1 = new TermQuery("string", "up");
    TermQuery tq3 = new TermQuery("root", "the");
    SequenceQuery sQuery1 =
            new SequenceQuery(new SequenceQuery.Gap[]{SequenceQuery
                    .getGap(1, 1)}, tq1, tq3);
    TermQuery tq1Gap = new TermQuery(TermQuery.IndexType.TOKENS, "string", "up", 2);
    SequenceQuery sQuery2 = new SequenceQuery(null, tq1Gap, tq3);
    GapQuery gQ1 = new GapQuery(tq1, 1);
    SequenceQuery sQuery3 = new SequenceQuery(null, gQ1, tq3);
    assertTrue("Not all results are the same!", TestUtils.allEqual(engine,
            sQuery1, sQuery2, sQuery3));
  }

  /**
   * Tests the functionality of the result set diff algorithm in
   * {@link TestUtils}.
   * 
   * @throws IOException
   * @throws IndexException
   */
  @Test
  public void testDiffer() throws IOException, IndexException {
    String[] terms = new String[]{"up", "to", "the"};
    TermQuery[] tqs = new TermQuery[terms.length];
    for(int i = 0; i < terms.length; i++) {
      tqs[i] = new TermQuery("string", terms[i]);
    }
    SequenceQuery seqQuery = new SequenceQuery(null, tqs);
    List<Binding>[] res = TestUtils.calculateDiff(seqQuery, seqQuery, engine);
    assertNull("Different results from the same query!", res);
  }
  
  @Test
  public void annotationQuery() {
    Map<String, String> constraints = new HashMap<String, String>();
    constraints.put("spec", "1 to 32 degF");
    AnnotationQuery annQuery = new AnnotationQuery("Measurement", constraints);
    performQuery("annotation", annQuery);
  }
  
  @Test
  public void testStringSequenceQuery() {
    String[] terms = new String[] {"up", "to", "the"};
    // String[] terms = new String[]{"ability", "of", /*"the", "agent",*/ "to",
    // "form", "an", "acid", "or", "base", "upon", "heating", "whereby",
    // "dehydrating", "cellulose", "at", "a", "low", "temperature",
    // "within", "a", "short", "period", "to", "yield", "water", "and",
    // "carbon"};
    TermQuery[] termQueries = new TermQuery[terms.length];
    for(int i = 0; i < terms.length; i++) {
      termQueries[i] = new TermQuery("string", terms[i]);
    }
    SequenceQuery.Gap[] gaps = new SequenceQuery.Gap[28];
    gaps[1] = SequenceQuery.getGap(2, 3);
    SequenceQuery sequenceQuery = new SequenceQuery(null/* gaps */, termQueries);
    
    performQuery("termSequence", sequenceQuery);
  }
  
  @Test
  public void testCategorySequenceQuery() {
    String[] terms = new String[]{"NN", "NN", "NN"};
    TermQuery[] termQueries = new TermQuery[terms.length];
    for(int i = 0; i < terms.length; i++) {
      termQueries[i] = new TermQuery("category", terms[i]);
    }
    SequenceQuery.Gap[] gaps = new SequenceQuery.Gap[28];
    gaps[1] = SequenceQuery.getGap(2, 3);
    SequenceQuery sequenceQuery = new SequenceQuery(null/* gaps */, termQueries);
    performQuery("categorySequence", sequenceQuery);
  }
  
  @Test
  public void testAnnotationSequenceQuery() {
    Map<String, String> empty = Collections.emptyMap();
    AnnotationQuery annQuery = new AnnotationQuery("Measurement", empty);
    SequenceQuery sequenceQuery = new SequenceQuery(null/* gaps */, annQuery, annQuery, annQuery);
    performQuery("annotationSequence", sequenceQuery);
  }
  
  @Test
  public void testRepeatsQuery() {
    Map<String, String> empty = Collections.emptyMap();
    AnnotationQuery annQuery = new AnnotationQuery("Measurement", empty);
    RepeatsQuery repeatsQuery = new RepeatsQuery(annQuery, 3, 3);
    performQuery("repeats", repeatsQuery);
  }
  
  @Test
  public void testWithinQuery() {
    AnnotationQuery intervalQuery = new AnnotationQuery("Measurement", Collections.singletonMap("type", "interval"));
    TermQuery toQuery = new TermQuery("string", "to");
    WithinQuery withinQuery = new WithinQuery(toQuery, intervalQuery);
    performQuery("within", withinQuery);
  }
  
  @Test
  public void testInAndQuery() {
    QueryNode inAndQuery = new WithinQuery(new AndQuery(new TermQuery(null, "London"),
              new TermQuery(null, "press")), new AnnotationQuery(
              "Reference", new HashMap<String, String>()));
    performQuery("inAnd", inAndQuery);
  }
  
  @Test
  public void testMeasurementSpecQuery() {
    AnnotationQuery specQuery = new AnnotationQuery("Measurement", Collections.singletonMap("spec", "5 cm"));
    performQuery("measurementSpec", specQuery);
  }
  
  @Test
  public void testQueryEngineRenderDocument() {
    List<Binding> hits = new ArrayList<Binding>();
    hits.add(new Binding(null, 0, 100, 5, null));
    hits.add(new Binding(null, 0, 110, 4, null));
    try {
      engine.renderDocument(0, hits, new FileWriter(resultsPath + "/renderDocumentResult.txt"));
    } catch(Exception e) {
      fail(e.getMessage());
    }
  }
  
  private void performQuery(String name, QueryNode query) {

    QueryExecutor executor = null;
    Binding hit = null;
    FileWriter queryResult = null;
    BufferedWriter writer = null;
    int hitCount = 0;

    try {

      File resultsDirectory = new File(resultsPath);
      if (!resultsDirectory.exists())
        resultsDirectory.mkdirs();
      
      executor = query.getQueryExecutor(engine);
      
      while (executor.nextDocument(-1) != -1) {
        if(hitCount == 0) {
          queryResult = new FileWriter(resultsPath + "/" + name + "QueryResult.xml");
          writer = new BufferedWriter(queryResult);
          writer.write("<query query=\"" + query.toString() + "\">");
          writer.newLine();
          writer.write("\t<hits>");
          writer.newLine();
        }
        hit = executor.nextHit();
        hitCount++;
        writer.write("\t\t<hit number=\"" + hitCount + "\">");
        writer.write(getHitString(hit, engine));
        writer.write("</hit>\n");
      }
      if(hitCount > 0) {
        writer.write("\t</hits>");
        writer.newLine();
        writer.write("</query>");
        writer.newLine();
        writer.flush();
      }

    } catch(Exception e) {
      fail(e.getMessage());
    } finally {
      try {
        if (queryResult != null)
          queryResult.close();
        if (writer != null)
          writer.close();
        if(executor != null)
          executor.close();
      } catch(Exception e) {
        fail(e.getMessage());
      }
    }
  }

  private String getHitString(Binding hit, QueryEngine searcher) throws IndexException
  {
    StringBuilder sb = new StringBuilder();
    String[][] text = searcher.getLeftContext(hit, 2);
    appendHitText(hit, text, sb);
    text = searcher.getHitText(hit);
    appendHitText(hit, text, sb);
    text = searcher.getRightContext(hit, 2);
    appendHitText(hit, text, sb);
    return sb.toString().replace(NEW_LINE, " ");
  }

  private void appendHitText(Binding hit, String[][] text, StringBuilder sb)
  {
    int length = Math.min(text[0].length, text[1].length);
    for (int i = 0; i < length; ++i)
    {
      final String token = text[0][i];
      final String space = text[1][i];
      sb.append(token != null ? token : "").append(space != null ? space : " ");
    }
  }
  
}
