package gate.mimir.gus.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.json.client.JSONException;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.HistoryListener;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.IncompatibleRemoteServiceException;
import com.google.gwt.user.client.rpc.InvocationException;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.KeyboardListener;
import com.google.gwt.user.client.ui.KeyboardListenerAdapter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle.MultiWordSuggestion;
import com.google.gwt.user.client.ui.SuggestOracle.Suggestion;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.SuggestOracle;
import com.google.gwt.user.client.ui.SuggestionEvent;
import com.google.gwt.user.client.ui.SuggestionHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;


/**
 * <p>Search GUI for GATE.</p>
 * 
 * <p>Features:</p>
 * <ul>
 * <li>Query autocompletion.</li>
 * <li>KWIC/concordance view of the results.</li>
 * <li>Document and Annotations stack view on user demand for each result.</li>
 * <li>Ranking per document as soon as all the results are retrieved.</li>
 * </ul>
 */
public class Application implements EntryPoint, HistoryListener {

  private final int resultsPerPage = 10;
  private final int contextWindow = 5;
  private final int UNKNOWN_NUMBER_OF_RESULTS = Integer.MAX_VALUE;
  private int numberOfResults = UNKNOWN_NUMBER_OF_RESULTS;
  private int currentPage = 1;
  private String query = "";
  /** Identifier of the current query. */
  private String queryID;
  /** Contains the results of the current page. */
  private ArrayList<QueryResult> resultsPageList =
    new ArrayList<QueryResult>(10);
  int resultsStartIndex = 0;
  boolean suggestionSelected = false;
  Timer timer;

  private GusServiceAsync gusService;

  /**
   * TextArea used for formulating the query.
   */
  private TextArea searchBox;
  /**
   * The SuggestBox wrapping the search box
   */
  private SuggestBox suggestBox;
  private StatisticsLabel statisticsLabel;
  private FlexTable resultsTable;
  private HorizontalPanel pageListPanel;
  private HorizontalPanel skipLinksPanel;

  /**
   * Initialisation of the GUI elements and the <code>History</code>.
   */
  public void onModuleLoad() {

    // initialize the connection with the server
    gusService = (GusServiceAsync) GWT.create(GusService.class);
    ServiceDefTarget endpoint = (ServiceDefTarget) gusService;
    String rpcUrl = GWT.getHostPageBaseURL() + "rpc";
    endpoint.setServiceEntryPoint(rpcUrl);
    
    //create the search box
    searchBox = new TextArea();
    searchBox.setCharacterWidth(60);
    searchBox.setVisibleLines(5);
    //wrap the search box into a suggest box
    suggestBox = new SuggestBox(new MimirOracle(), searchBox);
    suggestBox.setTitle("Press Escape to hide suggestions list; press Ctrl+Space to show it again.");
    RootPanel.get("searchbox").add(suggestBox);

    suggestBox.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        int keyCode = event.getNativeKeyCode();
        if(keyCode == KeyCodes.KEY_ENTER && event.isControlKeyDown()){
          // CTRL-ENTER -> fire the query
          processQuery(searchBox.getText());
        } else if(keyCode == KeyCodes.KEY_ESCAPE) {
          ((SuggestBox.DefaultSuggestionDisplay)
                  suggestBox.getSuggestionDisplay()).hideSuggestions();
        } else if(keyCode == ' ' && event.isControlKeyDown()) {
          // CTRL-Space: show suggestions
          suggestBox.showSuggestionList();
        }
        if(((SuggestBox.DefaultSuggestionDisplay)
                suggestBox.getSuggestionDisplay()).isSuggestionListShowing()) {
          // gobble up navigation keys
          if(keyCode == KeyCodes.KEY_UP ||
             keyCode == KeyCodes.KEY_DOWN ||
             keyCode == KeyCodes.KEY_ENTER) {
            event.stopPropagation();
            event.preventDefault();
          }
        }
      }
    });
    
    suggestBox.addKeyDownHandler(new KeyDownHandler() {
      @Override
      public void onKeyDown(KeyDownEvent event) {
        int keyCode = event.getNativeKeyCode();
        if(((SuggestBox.DefaultSuggestionDisplay)
                suggestBox.getSuggestionDisplay()).isSuggestionListShowing()) {
          // gobble up navigation keys
          if(keyCode == KeyCodes.KEY_UP ||
             keyCode == KeyCodes.KEY_DOWN ||
             keyCode == KeyCodes.KEY_ENTER) {
            event.stopPropagation();
            event.preventDefault();
          }
        }
      }
    });
    
    suggestBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        int keyCode = event.getNativeEvent().getKeyCode();
        if(((SuggestBox.DefaultSuggestionDisplay)
                suggestBox.getSuggestionDisplay()).isSuggestionListShowing()) {
          // gobble up navigation keys
          if(keyCode == KeyCodes.KEY_UP ||
             keyCode == KeyCodes.KEY_DOWN ||
             keyCode == KeyCodes.KEY_ENTER) {
            event.stopPropagation();
            event.preventDefault();
          }
        }
      }
    });
//    suggestBox.addKeyPressHandler(new KeyPressHandler() {
//      @Override
//      public void onKeyPress(KeyPressEvent event) {
//        if(event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ESCAPE) {
//          ((SuggestBox.DefaultSuggestionDisplay)
//                  suggestBox.getSuggestionDisplay()).hideSuggestions();
//        } else if(event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
//          if(event.isControlKeyDown()) {
//            processQuery(searchBox.getText());
//          } else {
//            // gobble up the event
//            event.stopPropagation();
//          }
//        } else if(event.getCharCode() == ' ' && event.isControlKeyDown()) {
//          // CTRL-Space: show suggestions
//          suggestBox.showSuggestionList();
//        }
//      }
//    });
    
    Button searchButton = new Button("Search", new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        processQuery(searchBox.getText());
      }
    });
    RootPanel.get("searchbutton").add(searchButton);


    // add a line with statistics for the results in the North panel
    statisticsLabel = new StatisticsLabel("Results", gusService);
    RootPanel.get("resultsbar").clear();
    RootPanel.get("resultsbar").add(statisticsLabel);
    
    statisticsLabel.addResultCountListener(new StatisticsLabel.ResultCountListener() {
      public void resultCountChanged(int oldCount, int newCount) {
        numberOfResults = newCount;
        updateListOfPages();
      }
    });

    // dock panel split in North, West, Center, East and South panels
    DockPanel dockPanel = new DockPanel();
    dockPanel.setWidth("100%");
    //dockPanel.setSpacing(30);

    // add a table of results in the Center panel
    resultsTable = new FlexTable();
    resultsTable.setCellPadding(5);
    resultsTable.setCellSpacing(0);
    resultsTable.setWidth("100%");
    displayMessage("Please enter a query in the text field above and press Enter.");
    dockPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
    dockPanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_TOP);
    dockPanel.add(resultsTable, DockPanel.CENTER);

    // add the list of pages in the South panel
    pageListPanel = new HorizontalPanel();
    pageListPanel.setSpacing(10);
    skipLinksPanel = new HorizontalPanel();
    skipLinksPanel.setSpacing(10);
    
    VerticalPanel paginationPanel = new VerticalPanel();
    paginationPanel.setSpacing(5);
    paginationPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
    paginationPanel.add(pageListPanel);
    paginationPanel.add(skipLinksPanel);
    dockPanel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
    dockPanel.setVerticalAlignment(HasVerticalAlignment.ALIGN_BOTTOM);
    dockPanel.add(paginationPanel, DockPanel.SOUTH);

    // make visible the dockPanel and add it to the body of the page
    RootPanel.get("resultstable").clear();
    RootPanel.get("resultstable").add(dockPanel);
    dockPanel.setVisible(true);

    // Add a history listener
    History.newItem("", false);
    History.addHistoryListener(this);
    History.fireCurrentHistoryState();
  }

  /**
   * Clear the results table, removing zebra stripes from empty rows.
   */
  private void clearTable() {
    resultsTable.clear();
    for(int i = 0; i < resultsTable.getRowCount(); ++i) {
      resultsTable.getRowFormatter().removeStyleName(i, (i % 2 == 0) ? "even" : "odd");
    }
  }
  
  /**
   * When the user click on a result page link or document link.
   */
  public void onHistoryChanged(String historyToken) {
    if (historyToken.startsWith("page=")) {
      // show a new page of results
      currentPage = Integer.parseInt(historyToken.substring("page=".length()));
      resultsStartIndex = (currentPage-1) * resultsPerPage;
      resultsPageList.clear();
      clearTable();
      if (queryID == null) {
        displayFirstQueryResults();
      } else {
        displayNextQueryResults(System.currentTimeMillis());
      }
      searchBox.setFocus(true);
    
    } else {
      // put the focus on the search box
      searchBox.setFocus(true);
    }
  }

  /**
   * When the user execute a query.
   * @param query the string to use as a query
   */
  private void processQuery(String query) {
    if (timer != null) {
      timer.cancel();
    }
    numberOfResults = UNKNOWN_NUMBER_OF_RESULTS;
    if(queryID != null) {
      // release server-side resources for the previous query
      gusService.releaseQuery(queryID, new AsyncCallback<Void>() {

        public void onFailure(Throwable caught) {
          // do nothing - if we failed to free the query there's not a lot
          // we can do about it...
        }

        public void onSuccess(Void result) {
          // nothing to do.
        }
        
      });
    }
    this.query = query;
    queryID = null;
    History.newItem("page=1", false);
    History.fireCurrentHistoryState();
  }

  /**
   * Display the results for the <code>currentPage</code>
   * in the <code>resultsTable</code>.
   */
  private void displayFirstQueryResults() {
    statisticsLabel.displayMessage("Retrieving "+ resultsPerPage +
      " results starting from " + (resultsStartIndex + 1)
      + "...");

    final long startTime = System.currentTimeMillis();
    gusService.search(getIndexId(), query, new AsyncCallback<String>() {
      String error = "A very very unexpected exception.";

      public void onFailure(Throwable caught) {
        try {
          throw caught;
        } catch (IncompatibleRemoteServiceException e) {
          error = "This client is not compatible with the "+
            "server. Please cleanup and refresh the browser.";
        } catch (InvocationException e) {
          error = "The call didn't complete cleanly.";
        } catch(SearchException e) {
          error = e.getMessage();
        } catch (Throwable e) {
          error = "A very unexpected exception. Is the server ready? " +
          "Please wait few seconds before trying again.";
        } finally {
          displayMessage(error);
        }
      }

      public void onSuccess(String result) {
        JSONValue jsonValue;
        try {
          jsonValue = JSONParser.parse(result);
        } catch (JSONException e) {
          displayMessage("Failed to parse JSON response: " + result);
          return;
        }
        JSONObject jsonObject;
        JSONString jsonString;
        if ((jsonObject = jsonValue.isObject()) != null
        && (jsonString = jsonObject.get("id").isString()) != null) {
          queryID = jsonString.stringValue();
        } else {
          displayMessage("No id returned for this query.");
          return;
        }
        timer = new Timer() {
          public void run() {
            displayNextQueryResults(startTime);
          }
        };
        // wait for the search to retrieve some results
        timer.schedule(500);
      }
    });
  }

  /**
   * To be called after {@link #displayFirstQueryResults()} to get the results
   * incrementally.
   * @param startTime time at the moment of the query execution
   */
  private void displayNextQueryResults(final long startTime) {

//    final String message =
//      "Number=" + resultsPerPage +
//      " Start=" + resultsStartIndex +
//      " List=" + resultsPageList.size() +
//      " Time=" + ((System.currentTimeMillis()-startTime)/1000.0) + " s.";
//    Window.setStatus(message);

    gusService.getHits(queryID, contextWindow,
      resultsPerPage - resultsPageList.size(),
      resultsStartIndex + resultsPageList.size(),
                          new AsyncCallback<List<QueryResult>>() {
      String error = "A very very unexpected exception.";

      public void onFailure(Throwable caught) {
        try {
          throw caught;
        } catch (IncompatibleRemoteServiceException e) {
          error = "This client is not compatible with the "+
            "server. Please cleanup and refresh the browser.";
        } catch (InvocationException e) {
          error = "The call didn't complete cleanly.";
        } catch(SearchException e) {
          error = e.getMessage();
        } catch (Throwable e) {
          error = "A very unexpected exception. Is the server ready? " +
          "Please wait few seconds before trying again.";
        } finally {
          displayMessage(error);
        }
      }

      public void onSuccess(List<QueryResult> results) {
        // no (more) results
        if (results == null && numberOfResults != UNKNOWN_NUMBER_OF_RESULTS) {
          if (resultsPageList.size() == 0) {
            if (numberOfResults == 0) {
              displayMessage(
                "Sorry, but we found no results for your query.</p>" +
                "<br><p>(Check the syntax if there is an error. " +
                "If not, use a more general query or index more documents.)");
            } else {
              displayMessage(
                "Sorry, but we found no more results for your query.</p>" +
                "<br><p>The total number of results is " +
                  numberOfResults +".");
            }
          }
//          statisticsLabel.setText(statisticsLabel.getText()
//            .replaceFirst("unknown", String.valueOf(numberOfResults)));
          updateListOfPages();

        // more results
        } else {
          if(results != null) {
            for (QueryResult r : results) {
              // add the results to resultsPageList
              resultsPageList.add(r);
            }
          }
          updateQueryResults(String.valueOf(" in " +
            (System.currentTimeMillis()-startTime)/1000.0) + " s.");
          updateListOfPages();

          if (resultsPageList.size() < resultsPerPage) {
            // some results have not been retrived
            timer = new Timer() {
              public void run() {
                displayNextQueryResults(startTime);
              }
            };
            // wait before to execute itself again
            timer.schedule(500);
          }
        }
      }
    });
  }

  /**
   * Update the results within the page.
   * @param time elapsed time since the query has been executed
   */
  private void updateQueryResults(String time) {

    //update the title of the page
    Window.setTitle("GUS - GATE Unified Search - " + query + " - page "
      + currentPage);

    // update the statistics line
    statisticsLabel.displayResults(queryID, resultsStartIndex + 1, Math.min((currentPage * resultsPerPage), numberOfResults));

    // update the results table
    for (int row = 0; row < resultsPerPage; row++) {
      if (resultsTable.getRowCount() > row && 
          resultsTable.getCellCount(row) > 1 && 
          resultsTable.getWidget(row, 0) != null) {
        // non empty row, skip it
        continue;
      }
      if (row >= resultsPageList.size()) {
        // no more results to display
        break;
      }
      int documentID = resultsPageList.get(row).getDocumentID();
      String documentURI = resultsPageList.get(row).getDocumentURI();
      String documentTitle = resultsPageList.get(row).getDocumentTitle();
      if(documentTitle == null || documentTitle.trim().length() == 0) {
        // we got no title to display: use the URI file
        String [] pathElems = documentURI.split("/");
        documentTitle = pathElems[pathElems.length -1];
      }
      // zebra stripes
      resultsTable.getRowFormatter().addStyleName(row, (row % 2 == 0) ? "even" : "odd");
      resultsTable.getCellFormatter().setHorizontalAlignment(row, 0, 
              HasHorizontalAlignment.ALIGN_LEFT);
      // each cell is a Grid
      Grid hitGrid = new Grid(2, 1);
      hitGrid.getCellFormatter().setHorizontalAlignment(0, 0, HasHorizontalAlignment.ALIGN_LEFT);
      hitGrid.getCellFormatter().setHorizontalAlignment(1, 0, HasHorizontalAlignment.ALIGN_LEFT);
      // top row of grid contains the link(s) 
      String documentURIText = "<span title=\"" + documentURI + "\">" +
      documentTitle + "</span>";
      if(getUriIsLink().equals("true")) {
        // generate two links: original doc and cached
        FlowPanel linksPanel = new FlowPanel();
        linksPanel.setStylePrimaryName("hit");
        linksPanel.add(new Anchor(documentURIText, true,
                documentURI, "documentWindow"));
        linksPanel.add(new InlineLabel(" ("));
        linksPanel.add(new Anchor("cached", false,
                "document/" + documentID + "?queryId=" + queryID, "documentWindow"));
        linksPanel.add(new InlineLabel(")"));
        hitGrid.setWidget(0, 0, linksPanel);
      } else {
        // generate one link: cached, with document name as text
        hitGrid.setWidget(0, 0, new Anchor(documentURIText, true,
                "document/" + documentID + "?queryId=" + queryID, "documentWindow"));
      }
      // second row is the hit text + contexts 
      HTML snippet = new HTML(
              resultsPageList.get(row).getLeftContext() + 
              "<span class=\"mimir-hit\">" + 
              resultsPageList.get(row).getSpanText() + "</span>" + 
              resultsPageList.get(row).getRightContext());
      snippet.setStylePrimaryName("snippet");
      hitGrid.setWidget(1, 0, snippet);
      resultsTable.setWidget(row, 0, hitGrid);
    }
  }
  
  private static final NumberFormat numberFormat = NumberFormat.getDecimalFormat();

  /**
   * Update the list of pages at the bottom of the page. 
   */
  private void updateListOfPages() {
    if(numberOfResults != UNKNOWN_NUMBER_OF_RESULTS) {
      pageListPanel.clear();
      pageListPanel.add(new Label("Page"));
      
      // previous button
      if(currentPage > 1) {
        pageListPanel.add(new Hyperlink("< Prev", "page=" + (currentPage-1)));
      }
  
      int lastPage = numberOfResults / resultsPerPage;
      if(numberOfResults % resultsPerPage != 0) lastPage += 1;
  
      for (int num = ((currentPage<6)?1:(currentPage-5));
           num <= ((currentPage<6)?10:(currentPage+4))
            && num <= lastPage; num++) {
        pageListPanel.add(pageLink(num));
      }
      
      if(currentPage < lastPage) {
        pageListPanel.add(new Hyperlink("Next >", "page=" + (currentPage+1)));
      }
      
      skipLinksPanel.clear();
      
      if(currentPage > 10 || lastPage > currentPage + 10) {
        // need a skip list
        skipLinksPanel.add(new Label("Skip pages"));
        
        int longestBackSkip = 1;
        for(int tmp = currentPage; tmp > 0; tmp /= 10, longestBackSkip *= 10);
        
        for(int i = longestBackSkip; i > 1; i /= 10) {
          if(currentPage > i) {
            skipLinksPanel.add(skipLink(-i));
          }
        }
        
        skipLinksPanel.add(new Label(" "));
        
        for(int i = 10; currentPage + i <= lastPage; i *= 10) {
          skipLinksPanel.add(skipLink(i));
        }
      }
    }
  }
  
  /**
   * Create a link to a given page number.
   */
  private Widget pageLink(int num) {
    if (num == currentPage) {
      return new Label(numberFormat.format(num));
    } else {
      return new Hyperlink(numberFormat.format(num),
        "page="+String.valueOf(num));
    }
  }
  
  /**
   * Create a link to skip a given number of pages.  Negative skips
   * skip backwards, positive skips skip forwards.
   */
  private Widget skipLink(int skip) {
    int targetPage = currentPage + skip;
    if(skip < 0) {
      return new Hyperlink("<" + numberFormat.format(-skip), "page=" + targetPage);
    }
    else {
      return new Hyperlink(numberFormat.format(skip) + ">", "page=" + targetPage);
    }
  }


  private class MimirOracle extends SuggestOracle{

    
    public MimirOracle() {
      super();
      gusService.getAnnotationsConfig(getIndexId(), new AsyncCallback<String[][]>() {

        /* (non-Javadoc)
         * @see com.google.gwt.user.client.rpc.AsyncCallback#onFailure(java.lang.Throwable)
         */
        public void onFailure(Throwable caught) {
          //we could not get the data from the server
          annotationsConfig = new String[][]{
            new String[]{}      
          };
        }

        /* (non-Javadoc)
         * @see com.google.gwt.user.client.rpc.AsyncCallback#onSuccess(java.lang.Object)
         */
        public void onSuccess(String[][] result) {
          annotationsConfig = result;
        }
        
      });
    }

    /* (non-Javadoc)
     * @see com.google.gwt.user.client.ui.SuggestOracle#requestSuggestions(com.google.gwt.user.client.ui.SuggestOracle.Request, com.google.gwt.user.client.ui.SuggestOracle.Callback)
     */
    @Override
    public void requestSuggestions(Request request, Callback callback) {
      ArrayList<MultiWordSuggestion> suggestions =
        new ArrayList<MultiWordSuggestion>();
      String query = request.getQuery();
      int caretIndex = searchBox.getCursorPos();
      int startIndex = query.lastIndexOf('{', caretIndex - 1);
      int endIndex = query.indexOf('{', caretIndex);
      if (endIndex == -1) { endIndex = caretIndex; }
      int lastClose = query.lastIndexOf("}", caretIndex);
      if (startIndex != -1 && lastClose < startIndex) {
        // an open bracket '{' is present, and not followed by } yet
        //check if we have the annotation type already
        String annType = null;
        boolean nonSpaceSeen = false;
        int charIdx = startIndex + 1;
        for(; charIdx < endIndex; charIdx++){
          // this method is deprecated, but the replacement (isWhitespace())
          // is not implemented in GWT
          if(Character.isSpace(query.charAt(charIdx))){
            if(nonSpaceSeen){
              //we found some space, after some actual content was seen
              annType = query.substring(startIndex + 1, charIdx);
              break;
            }
          }else{
            nonSpaceSeen = true;
          }
        }
        if(annType == null){
          //we have not found an ann type -> suggest some
          //the string before the last open {, before the caret
          String before = query.substring(0, startIndex);
          //the string after the next {
          String after = query.substring(endIndex);
          //the string from the current open {, to the caret, or the next {
          String middle = (startIndex >= 0 && startIndex < endIndex) ?
            query.substring(startIndex+1, endIndex): "";
          for(int annTypeId = 0; annTypeId < annotationsConfig.length; annTypeId++){
            if(annotationsConfig[annTypeId][0].startsWith(middle)){
              //we have identified the annotation type
              String suggestion = "{" + annotationsConfig[annTypeId][0];
              suggestions.add(new MultiWordSuggestion(
                      before + suggestion + after, suggestion));
//              Window.alert("Suggestion is: \"" + before + suggestion + after + "\"!");
            }
          }
        }else{
          //we know the ann type -> consume everything until the last word
          int lastSpace = charIdx;
          int wordCount = 0;
          boolean inSpace = true;
          boolean inQuote = false;
          for(; charIdx < endIndex; charIdx++){
            if(inQuote){
              //while in quote, consume everything until the closing quote
              if(query.charAt(charIdx) == '"' && charIdx > 0 &&
                      query.charAt(charIdx -1) != '\\'){
                inQuote = false;
//                wordCount++;
              }
            }else{
              if(Character.isSpace(query.charAt(charIdx))){
                lastSpace = charIdx;
                if(!inSpace){
                  //we're starting a new space (so we just finished a word)
                  wordCount++;
                  inSpace = true;
                }
              }else if(query.charAt(charIdx) == ')'){
                //closing of REGEX
                wordCount = 0;
                inSpace = false;
              } else if(query.charAt(charIdx) == '"' && charIdx > 0 &&
                      query.charAt(charIdx -1 ) != '\\'){
                inQuote = true;
                inSpace = false;
              } else{
                //some other non-space char
                if(inSpace){
                  //we're starting a new word
                  inSpace = false;
                }
              }
            }
          }
          if(inQuote){
            //suggest nothing
          }else{
            String before = query.substring(0, lastSpace + 1);
            String after = query.substring(endIndex);
            String middle = lastSpace < endIndex ? 
                    query.substring(lastSpace + 1, endIndex) : "";
            //we are still typing the feature name or operator
            //words appear in this sequence: <feature> <operator> <value>
            if(wordCount % 3 == 0){
              //feature
              //find the ann type
              for(int annTypeId = 0; annTypeId < annotationsConfig.length; annTypeId++){
                if(annotationsConfig[annTypeId][0].equalsIgnoreCase(annType)){
                  //suggest some feature names
                  for(int featId = 1; featId < annotationsConfig[annTypeId].length; 
                      featId++){
                    if(annotationsConfig[annTypeId][featId].startsWith(middle)){
                      String suggestion = annotationsConfig[annTypeId][featId];
                      suggestions.add(new MultiWordSuggestion(
                              before + suggestion + after, suggestion));
                    }
                  }
                  //also offer to close the annotation
                  String suggestion = "}";
                  suggestions.add(new MultiWordSuggestion(
                          before + suggestion + after, suggestion));
                  //only one ann type can match
                  break;
                }
              }              
            } else if(wordCount % 3 == 1){
              //operator
              String[] strArray = new String[]{"= \"\"", "<", "<=", ">", ">=", ".REGEX()"};
              for(String suggestion : strArray){
                suggestions.add(new MultiWordSuggestion(
                        before + suggestion + after, suggestion));
              }
            }else{
              //value -> no suggestions
            }
          }
        }
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX        
        
//        //the string before the last open {, before the caret
//        String before = query.substring(0, startIndex);
//        //the string after the next {
//        String after = query.substring(endIndex);
//        //the string from the current open {, to the caret, or the next {
//        String middle = (startIndex >= 0 && startIndex < endIndex) ?
//          query.substring(startIndex+1, endIndex): "";
//        //find the annotation type
//        String[] inputs = middle.split("\\s");
//        if(inputs.length == 1){
//          //we're searching only for annotation type
//          for(int annTypeId = 0; annTypeId < indexConfig.length; annTypeId++){
//            if(indexConfig[annTypeId][0].startsWith(inputs[0])){
//              //we have identified the annotation type
//              String suggestion = "{" + indexConfig[annTypeId][0];
//              suggestions.add(new MultiWordSuggestion(
//                      before + suggestion + after, suggestion));
//            }
//          }
//        } else if(inputs.length > 1){
//          //we already have the ann type, we need to suggest feature names
//          for(int annTypeId = 0; annTypeId < indexConfig.length; annTypeId++){
//            if(indexConfig[annTypeId][0].equalsIgnoreCase(inputs[0])){
//              //now we need to suggest a feature name
//              //the before string must contain everything up to the current feature
//              
//              String lastPrefix = inputs[inputs.length -1];
//              if(lastPrefix.indexOf('=') <0){
//                //we are still typing the feature name
//                for(int featId = 1; featId < indexConfig[annTypeId].length; 
//                    featId++){
//                  if(indexConfig[annTypeId][featId].startsWith(lastPrefix)){
//                    String suggestion = indexConfig[annTypeId][featId] + " = ";
//                    suggestions.add(new MultiWordSuggestion(
//                            before + suggestion + after, suggestion));
//                  }
//                }
//              }
//              //we only match one annotation type, so we break now
//              break;
//            }
//          }
//        }//if(inputs.length > 1)
      }
      
      Response response = new Response(suggestions);
      callback.onSuggestionsReady(request, response);
    }
    
    private String[][] annotationsConfig = new String[][]{new String[]{}};

  }
  


  private void displayMessage(String message) {
    clearTable();
    resultsTable.getCellFormatter().setHorizontalAlignment(0, 0,
      HasHorizontalAlignment.ALIGN_CENTER);
    resultsTable.setWidget(0, 0, new HTML("<p>"+message+"</p>"));
  }
  
  private native String getIndexId() /*-{
    return $wnd.indexId;
  }-*/;
  
  private native String getUriIsLink() /*-{
    return $wnd.uriIsLink;
  }-*/;  
}
