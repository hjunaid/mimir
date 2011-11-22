package gate.mimir.test;

import java.io.File;
import java.net.MalformedURLException;

import gate.Gate;
import gate.mimir.index.IndexException;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.query.parser.QueryParser;
import gate.util.GateException;
import groovy.ui.Console;

public class ScratchConsole {
  /**
   * @param args
   * @throws GateException 
   * @throws MalformedURLException 
   * @throws IndexException 
   */
  public static void main(String[] args) throws MalformedURLException, 
      GateException, IndexException {
    // GATE, Mimir init
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
    QueryEngine qEngine = new QueryEngine(new File(args[0]));
    // Prepare console
    Console console = new Console();
    console.setVariable("qEngine", qEngine);
    console.setVariable("qParser", QueryParser.class);
    console.run();
//    console.getInputArea().setText(
//      "import gate.mimir.search.*\n" +
//      "import gate.mimir.search.score.*\n" +
//      "def qNode = qParser.parse('{Measurement}')\n" +
//      "def qExecutor = qNode.getQueryExecutor(qEngine)\n" +
//      "def qRunner = new RankingQueryRunnerImpl(qExecutor, new BindingScorer())");    
  }
}
