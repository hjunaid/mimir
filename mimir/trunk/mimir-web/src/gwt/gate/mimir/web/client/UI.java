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

import gate.mimir.gus.client.GusService;
import gate.mimir.gus.client.GusServiceAsync;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.TextArea;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class UI implements EntryPoint {
  
  
  private GwtRpcServiceAsync gwtRpcService;
  
  protected TextArea searchBox;
  
  protected Button searchButton;
  
  protected String queryId;
  
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
        Window.alert("Search started");    
      }
    });
  }
  
  private native String getIndexId() /*-{
    return $wnd.indexId;
  }-*/;

  private native String getUriIsLink() /*-{
    return $wnd.uriIsLink;
  }-*/;
}
