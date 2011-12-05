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

import java.util.ArrayList;
import java.util.List;

import gate.mimir.gus.client.GusService;
import gate.mimir.gus.client.GusServiceAsync;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.TextArea;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class UI implements EntryPoint {
 
  /**
   * A Timer implementation that fetches the latest results information from the 
   * server and updates the results display accordingly.
   */
  protected class StatsUpdater extends Timer {

    @Override
    public void run() {
      // calculate which documents we need now
      int firstDoc = firstDocumentOnPage + documentsData.size();
      int docCount = maxDocumentsOnPage - documentsData.size(); 
      if(docCount <= 0) {
        firstDoc = -1;
      }
      gwtRpcService.getResultsData(queryId, firstDoc, docCount, 
        new AsyncCallback<ResultsData>() {
        @Override
        public void onSuccess(ResultsData result) {
          updatePage(result);
          if(result.getResultsTotal() < 0) {
            // more to come
            schedule(500);
          }
        }
        
        @Override
        public void onFailure(Throwable caught) {
          // ignore and try again later
          schedule(500);
          // throw the exception so it's seen
          throw new RuntimeException(caught);
        }
      });
    }
  }
  
  private GwtRpcServiceAsync gwtRpcService;
  
  protected TextArea searchBox;
  
  protected Button searchButton;
  
  protected String queryId;
  
  protected Label resultStatsLabel;
  
  protected HTMLPanel searchResultsPanel;
  
  /**
   * The rank of the first document on page.
   */
  protected int firstDocumentOnPage;
  
  protected int maxDocumentsOnPage = 20;
  
  protected int maxSnippetLength = 100;
  
  private List<DocumentData> documentsData;
  
  /**
   * This is the entry point method.
   */
  public void onModuleLoad() {
    // connect to the server RPC endpoint
    gwtRpcService = (GwtRpcServiceAsync) GWT.create(GwtRpcService.class);
    ServiceDefTarget endpoint = (ServiceDefTarget) gwtRpcService;
    String rpcUrl = GWT.getHostPageBaseURL() + "gwtRpc";
    endpoint.setServiceEntryPoint(rpcUrl);
    
    queryId = null;
    
    initGui();
    initListeners();
  }
  
  protected void initGui() {
    HTMLPanel searchDiv = HTMLPanel.wrap(Document.get().getElementById("searchBox"));
    
    searchBox = new TextArea();
    searchBox.setCharacterWidth(60);
    searchBox.setVisibleLines(10);
    searchDiv.add(searchBox);
    
    searchButton = new Button();
    searchButton.setText("Search");
    searchDiv.add(searchButton);
    
    HTMLPanel resultsBar = HTMLPanel.wrap(Document.get().getElementById("resultsBar"));
    resultStatsLabel = new Label("Ready");
    resultsBar.add(resultStatsLabel);

    searchResultsPanel = HTMLPanel.wrap(Document.get().getElementById("searchResults"));
    
  }
  
  protected void initListeners() {
    searchButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        startSearch();
      }
    });
  }
  
  protected void startSearch() {
    String query = searchBox.getText();
    gwtRpcService.search(getIndexId(), query, new AsyncCallback<String>() {
      @Override
      public void onFailure(Throwable caught) {
        // TODO Auto-generated method stub
        
      }
      @Override
      public void onSuccess(String result) {
        queryId = result;
        firstDocumentOnPage = 0;
        documentsData = new ArrayList<DocumentData>(maxDocumentsOnPage);
        searchResultsPanel.clear();
        new StatsUpdater().schedule(50);
      }
    });
  }
  
  protected void updatePage(ResultsData resultsData) {
    int resTotal = resultsData.getResultsTotal();
    int resPartial = resultsData.getResultsPartial();
    StringBuilder textBuilder = new StringBuilder("Documents ");
    textBuilder.append(firstDocumentOnPage);
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
    resultStatsLabel.setText(textBuilder.toString());

    if(resultsData.getDocuments() != null){
      // now update the documents display
      int docPosition = 0;      
      for(DocumentData docData : resultsData.getDocuments()) {
        // skip already populated positions
        while(docPosition < documentsData.size() && 
            documentsData.get(docPosition).documentRank < docData.documentRank){
          docPosition ++;
        }
        if(docPosition == documentsData.size()) {
          documentsData.add(docData);
          HTMLPanel documentDisplay = buildDocumentDisplay(docData);
          if(docPosition % 2 == 0) documentDisplay.addStyleName("even");
          searchResultsPanel.add(documentDisplay);
        } else {
          if(documentsData.get(docPosition).documentRank == docData.documentRank) {
            // we got the same document: skip it
          } else {
            // malfunction?
            // TODO
          }
        }
      }      
    }
  }
  
  private HTMLPanel buildDocumentDisplay(DocumentData docData) {
    HTMLPanel documentDisplay = new HTMLPanel("");
    documentDisplay.setStyleName("hit");
    HTMLPanel docTitle = new HTMLPanel(docData.documentTitle);
    docTitle.setStyleName("document-title");
    documentDisplay.add(docTitle);
    if(docData.snippets != null) {
      // each row is left context, snippet, right context
      for(String[] snippet : docData.snippets) {
        HTMLPanel snippetPanel = new HTMLPanel("");
        snippetPanel.setStyleName("snippet");
        snippetPanel.add(new InlineLabel(snippet[0]));
        String snipText = snippet[1];
        int snipLen = snipText.length();
        if(snipLen > maxSnippetLength) {
          int toRemove = snipLen - maxSnippetLength;
          snipText = snipText.substring(0, (snipLen - toRemove) / 2) + 
              " ... " + 
              snipText.substring((snipLen + toRemove) / 2);
        }
        InlineLabel snippetLabel = new InlineLabel(snipText);
        snippetLabel.setStyleName("snippet-text");
        snippetPanel.add(snippetLabel);
        snippetPanel.add(new InlineLabel(snippet[2]));
        documentDisplay.add(snippetPanel);
      }
    }
    return documentDisplay;
  }
  
  private native String getIndexId() /*-{
    return $wnd.indexId;
  }-*/;

  private native String getUriIsLink() /*-{
    return $wnd.uriIsLink;
  }-*/;
}
