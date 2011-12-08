/**
 *  UI.java
 * 
 *  Copyright (c) 1995-2010, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 23 Nov 2011 
 */

package gate.mimir.web.client;

import java.util.List;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.InlineHyperlink;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class UI implements EntryPoint {
 
  /**
   * A Timer implementation that fetches the latest results information from the 
   * server and updates the results display accordingly.
   * It will re-schedule itself until all required data is made available by 
   * the server. 
   */
  protected class ResultsUpdater extends Timer {
    
    private int newFirstDocument;
    
    /**
     * Creates a new results updater.
     * @param newFirstDocument the first document to be displayed on the page. 
     * This can be used for navigating between pages.
     */
    public ResultsUpdater(int newFirstDocument) {
      super();
      this.newFirstDocument = newFirstDocument;
    }

    @Override
    public void run() {
      if(newFirstDocument != firstDocumentOnPage) {
        // new page: clear data and old display
        firstDocumentOnPage = newFirstDocument;
        updateResultsDisplay(null);
      }
      feedbackLabel.setText("Working...");
      gwtRpcService.getResultsData(queryId, firstDocumentOnPage, maxDocumentsOnPage, 
        new AsyncCallback<ResultsData>() {
        @Override
        public void onSuccess(ResultsData result) {
          updatePage(result);
          if(result.getResultsTotal() < 0) schedule(500);
        }
        
        @Override
        public void onFailure(Throwable caught) {
          if(caught instanceof MimirSearchException &&
             ((MimirSearchException)caught).getErrorCode() ==
             MimirSearchException.QUERY_ID_NOT_KNOWN) {
            // query ID not known -> re-post the query
            if(queryString != null && queryString.length() > 0){
              queryId = null;
              postQuery(queryString);
            }
          } else {
            // ignore and try again later
            schedule(500);
            // re-throw the exception so it's seen (useful when debugging)
            throw new RuntimeException(caught);
          }
        }
      });
    }
  }
  
  /**
   * Gets the Javascript variable value from the GSP view. 
   * @return
   */
  private native String getIndexId() /*-{
    return $wnd.indexId;
  }-*/;

  /**
   * Gets the Javascript variable value from the GSP view. 
   * @return
   */
  private native String getUriIsLink() /*-{
    return $wnd.uriIsLink;
  }-*/;

  /**
   * The remote service used to communicate with the server.
   */
  private GwtRpcServiceAsync gwtRpcService;
  
  /**
   * The TextArea where the query string is typed by the user.
   */
  protected TextArea searchBox;
  
  /**
   * The Search button.
   */
  protected Button searchButton;
  
  /**
   * The current query ID (used when communicating with the server).
   */
  protected String queryId;
  
  /**
   * The current query string (used to re-post the query if the session expired
   * (e.g. the link was bookmarked).
   */
  protected String queryString;
  
  /**
   * Cached value for the current index ID (obtained once from 
   * {@link #getIndexId()}, then cached).
   */
  protected String indexId;
  
  /**
   * Cached value for the Javascript var (obtained once from 
   * {@link #getUriIsLink()}, then cached).
   */  
  protected boolean uriIsLink;
  
  /**
   * The label displaying feedback to the user (e.g. how many documents were 
   * found, or the current error message).
   */
  protected Label feedbackLabel;
  
  /**
   * The panel covering the centre of the page, where the results documents are
   * listed.
   */
  protected HTMLPanel searchResultsPanel;
  
  /**
   * The panel at the bottom of the page, containing links to other result 
   * pages. 
   */
  protected HTMLPanel pageLinksPanel;
  
  /**
   * The rank of the first document on page.
   */
  protected int firstDocumentOnPage;
  
  /**
   * How many documents should be shown on each result page.
   */
  protected int maxDocumentsOnPage = 20;
  
  /**
   * How many page links should be included at the bottom. The current page
   * would normally appear in the middle.
   */
  protected int maxPages = 20;
  
  /**
   * How many characters are displayed for each snippet (for longer snippets,
   * the middle content is truncated and replaced by an ellipsis).  
   */
  protected int maxSnippetLength = 100;
  
  /**
   * This is the entry point method.
   */
  public void onModuleLoad() {
    // connect to the server RPC endpoint
    gwtRpcService = (GwtRpcServiceAsync) GWT.create(GwtRpcService.class);
    ServiceDefTarget endpoint = (ServiceDefTarget) gwtRpcService;
    String rpcUrl = GWT.getHostPageBaseURL() + "gwtRpc";
    endpoint.setServiceEntryPoint(rpcUrl);
    
    indexId = getIndexId();
    uriIsLink = Boolean.parseBoolean(getUriIsLink());
    
    initLocalData();
    initGui();
    initListeners();
  }
  
  protected void initLocalData() {
    queryId = null;
    firstDocumentOnPage = 0;
  }
  
  protected void initGui() {
    HTMLPanel searchDiv = HTMLPanel.wrap(Document.get().getElementById("searchBox"));
    
    searchBox = new TextArea();
    searchBox.setCharacterWidth(60);
    searchBox.setVisibleLines(10);
    searchDiv.add(searchBox);
    
    searchButton = new Button();
    searchButton.setText("Search");
    searchButton.addStyleName("searchButton");
    searchDiv.add(searchButton);
    
    HTMLPanel resultsBar = HTMLPanel.wrap(Document.get().getElementById("feedbackBar"));
    feedbackLabel = new InlineLabel();
    resultsBar.add(feedbackLabel);
    resultsBar.add(new InlineHTML("&nbsp;"));

    searchResultsPanel = HTMLPanel.wrap(Document.get().getElementById("searchResults"));
    updateResultsDisplay(null);
    
    pageLinksPanel = HTMLPanel.wrap(Document.get().getElementById("pageLinks"));
    pageLinksPanel.add(new InlineHTML("&nbsp;"));
  }
  
  protected void initListeners() {
    searchButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        startSearch();
      }
    });
    
    History.addValueChangeHandler(new ValueChangeHandler<String>() {
      @Override
      public void onValueChange(ValueChangeEvent<String> event) {
        String historyToken = event.getValue();
        if(historyToken != null && historyToken.length() > 0) {
          String newQueryId = null;
          String newQueryString = null;
          int newFirstDoc = 0;
          
          String[] elems = historyToken.split("\\&");
          for(String elem : elems) {
            String[] keyVal = elem.split("=");
            String key = keyVal[0].trim();
            String value = keyVal[1].trim();
            if(key.equalsIgnoreCase("queryId")) {
              newQueryId = URL.decodeQueryString(value);
            } else if(key.equalsIgnoreCase("queryString")) {
              newQueryString = URL.decodeQueryString(value);
            } else if(key.equalsIgnoreCase("firstDoc")) {
              try{
                newFirstDoc =Integer.parseInt(value);
              } catch (NumberFormatException nfe) {
                // ignore, and start results from zero
              }
            }
          }
          // now update the display accordingly
          if(newQueryId != null && newQueryId.length() > 0) {
            queryId = newQueryId;
            queryString = newQueryString;
            if(!searchBox.getText().trim().equalsIgnoreCase(
              newQueryString.trim())){
              searchBox.setText(newQueryString);
            }
            new ResultsUpdater(newFirstDoc).schedule(10);
          }
        }
      }
    });
    // now read the current history
    String historyToken = History.getToken(); 
    if(historyToken != null && historyToken.length() > 0) {
      History.fireCurrentHistoryState();
    }
  }
  
  protected void updateResultsDisplay (List<DocumentData> documentsData) {
    searchResultsPanel.clear();
    if(documentsData == null || documentsData.isEmpty()) {
      for(int  i = 0; i < 20; i++) searchResultsPanel.add(new HTML("&nbsp;"));
    } else {
      for(DocumentData docData : documentsData) {
        searchResultsPanel.add(buildDocumentDisplay(docData));
      }      
    }
  }
  
  protected void startSearch() {
    // clean up old state
    if(queryId != null) {
      // release old query
      gwtRpcService.releaseQuery(queryId, new AsyncCallback<Void>() {
        @Override
        public void onSuccess(Void result) {}
        @Override
        public void onFailure(Throwable caught) {}
      });
    }
    // reset internal data
    initLocalData();
    // post the new query
    postQuery(searchBox.getText());
  }
  
  protected void postQuery(final String newQueryString) {
    feedbackLabel.setText("Working...");
    updateResultsDisplay(null);
    gwtRpcService.search(getIndexId(), newQueryString, new AsyncCallback<String>() {
      @Override
      public void onFailure(Throwable caught) {
        feedbackLabel.setText(caught.getLocalizedMessage());
      }
      @Override
      public void onSuccess(String newQueryId) {
        History.newItem(createHistoryToken(newQueryId, newQueryString, 
          firstDocumentOnPage));
      }
    });    
  }
  
  protected String createHistoryToken(String queryId, String queryString, 
                                      int firstDocument) {
    return "queryId=" + URL.encodeQueryString(queryId) + 
        "&queryString=" + URL.encodeQueryString(queryString) + 
        "&firstDoc=" + firstDocument;
  }
  
  /**
   * Updates the results display (including the feedback label)
   * @param resultsData
   */
  protected void updatePage(ResultsData resultsData) {
    int resTotal = resultsData.getResultsTotal();
    int resPartial = resultsData.getResultsPartial();
    StringBuilder textBuilder = new StringBuilder("Documents ");
    textBuilder.append(firstDocumentOnPage + 1);
    textBuilder.append(" to ");
    if(firstDocumentOnPage + maxDocumentsOnPage < resPartial) {
      textBuilder.append(firstDocumentOnPage + maxDocumentsOnPage);
    } else {
      textBuilder.append(resPartial - firstDocumentOnPage);
    }
    textBuilder.append(" of ");
    
    if(resTotal > 0) {
      // all results obtained
      textBuilder.append(resultsData.getResultsTotal());
    } else {
      // more to come
      textBuilder.append("at least ");
      textBuilder.append(resPartial);
    }
    textBuilder.append(":");
    feedbackLabel.setText(textBuilder.toString());
    // now update the documents display
    if(resultsData.getDocuments() != null){
      updateResultsDisplay(resultsData.getDocuments());
    }
    
    // page links
    pageLinksPanel.clear();
    int currentPage = firstDocumentOnPage / maxDocumentsOnPage;
    int firstPage = Math.max(0, currentPage - (maxPages / 2));
    int maxPage = resultsData.getResultsPartial() / maxDocumentsOnPage;
    if(resultsData.getResultsPartial() % maxDocumentsOnPage > 0) maxPage++;
    maxPage = Math.min(maxPage, firstPage + maxPages);
    for(int pageNo = firstPage; pageNo < maxPage; pageNo++) {
      Widget pageLink;
      if(pageNo != currentPage) {
        pageLink = new InlineHyperlink("" + (pageNo + 1), 
          createHistoryToken(queryId, queryString, 
            pageNo * maxDocumentsOnPage));
      } else {
        pageLink = new InlineLabel("" + (pageNo + 1));
      }
      pageLink.addStyleName("pageLink");
      pageLinksPanel.add(pageLink);
    }
  }
  
  private HTMLPanel buildDocumentDisplay(DocumentData docData) {
    HTMLPanel documentDisplay = new HTMLPanel("");
    documentDisplay.setStyleName("hit");
    String documentUri = docData.documentUri;
    String documentTitle = docData.documentTitle;
    if(documentTitle == null || documentTitle.trim().length() == 0) {
      // we got no title to display: use the URI file
      String [] pathElems = documentUri.split("/");
      documentTitle = pathElems[pathElems.length -1];
    }
    String documentTitleText = "<span title=\"" + documentUri + 
        "\" class=\"document-title\">" +
        docData.documentTitle + "</span>";
    FlowPanel docLinkPanel = new FlowPanel();
//    docLinkPanel.setStyleName("document-title");
    if(uriIsLink) {
      // generate two links: original doc and cached
      docLinkPanel.add(new Anchor(documentTitleText, true, documentUri));
      docLinkPanel.add(new InlineLabel(" ("));
      docLinkPanel.add(new Anchor("cached", false, 
          "document?documentRank=" + docData.documentRank + 
          "&queryId=" + queryId));
      docLinkPanel.add(new InlineLabel(")"));
    } else {
      // generate one link: cached, with document name as text
      docLinkPanel.add(new Anchor(documentTitle, true,
          "document?documentRank=" + docData.documentRank + 
          "&queryId=" + queryId));
    }
    documentDisplay.add(docLinkPanel);
    if(docData.snippets != null) {
      StringBuilder snippetsText = new StringBuilder("<div class=\"snippets\">");
      // each row is left context, snippet, right context
      for(String[] snippet : docData.snippets) {
        snippetsText.append("<span class=\"snippet\">");
        snippetsText.append(snippet[0]);
        snippetsText.append("<span class=\"snippet-text\">");
        String snipText = snippet[1];
        int snipLen = snipText.length();
        if(snipLen > maxSnippetLength) {
          int toRemove = snipLen - maxSnippetLength;
          snipText = snipText.substring(0, (snipLen - toRemove) / 2) + 
              " ... " + 
              snipText.substring((snipLen + toRemove) / 2);
        }
        snippetsText.append(snipText);
        //close snippet-text span
        snippetsText.append("</span>");
        snippetsText.append(snippet[2]);
        //close snippet span
        snippetsText.append("</span>");
      }
      documentDisplay.add(new HTML(snippetsText.toString()));
    }
    return documentDisplay;
  }
}