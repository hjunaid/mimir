/*
 *  StatisticsLabel.java
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
package gate.mimir.gus.client;

import gate.mimir.gus.client.GusServiceAsync;
import gate.mimir.gus.client.TotalResults;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

public class StatisticsLabel extends Composite {
  
  public static interface ResultCountListener {
    void resultCountChanged(int oldCount, int newCount);
  }

  private FlowPanel mainPanel;
  private Label messageLabel;
  private Label lowerBoundLabel;
  private Label upperBoundLabel;
  private InlineHTML totalLabel;
  private int currentTotal;
  private int currentUpperBound;
  private Anchor keepSearchingAnchor;
  
  private String currentQuery;
  
  private GusServiceAsync gusService;
  
  private Timer totalResultsTimer;
  
  private List<ResultCountListener> listeners;
  
  private static final NumberFormat numberFormat = NumberFormat.getDecimalFormat();
  
  public StatisticsLabel(String initialMessage, final GusServiceAsync gusService) {
    this.gusService = gusService;
    mainPanel = new FlowPanel();
    
    messageLabel = new Label(initialMessage);
    mainPanel.add(messageLabel);
    
    // create the other widgets, but don't add them yet
    lowerBoundLabel = new InlineLabel();
    upperBoundLabel = new InlineLabel();
    totalLabel = new InlineHTML("<i>unknown</i>");
    keepSearchingAnchor = new Anchor(">> keep searching");
    keepSearchingAnchor.addClickListener(new ClickListener() {
      public void onClick(Widget sender) {
        mainPanel.remove(keepSearchingAnchor);
        gusService.runQuery(currentQuery, new AsyncCallback<Void>() {
          public void onFailure(Throwable caught) {
            
          }

          public void onSuccess(Void result) {
            totalResultsTimer.scheduleRepeating(1000);
          }          
        });
      }
    });
    
    totalResultsTimer = new Timer() {
      @Override
      public void run() {
        updateTotalResults();
      }
    };

    
    currentQuery = null;
    currentTotal = -1;
    
    listeners = new ArrayList<ResultCountListener>();
    
    initWidget(mainPanel);
  }
  
  public void displayResults(String queryID, int lowerBound, int upperBound) {
    if(currentQuery == null || !currentQuery.equals(queryID)) {
      searchStarting(queryID);
    }
    currentUpperBound = upperBound;
    lowerBoundLabel.setText(numberFormat.format(lowerBound));
    upperBoundLabel.setText(numberFormat.format(upperBound));
  }
  
  public void displayMessage(String message) {
    totalResultsTimer.cancel();
    currentQuery = null;
    messageLabel.setText(message);
    mainPanel.clear();
    mainPanel.add(messageLabel);
  }
  
  private void searchStarting(String queryID) {
    if(currentQuery == null) {
      // need to add the widgets, replacing the single message
      mainPanel.clear();
      mainPanel.add(new InlineLabel("Results "));
      mainPanel.add(lowerBoundLabel);
      mainPanel.add(new InlineLabel(" - "));
      mainPanel.add(upperBoundLabel);
      mainPanel.add(new InlineLabel(" of "));
      mainPanel.add(totalLabel);
    }
    
    currentQuery = queryID;
    
    // clear the total
    currentTotal = -1;
    totalLabel.setHTML("<i>unknown</i>");
    
    totalResultsTimer.scheduleRepeating(1000);
  }
  
  private void updateTotalResults() {
    gusService.getTotalResults(currentQuery, new AsyncCallback<TotalResults>() {

      public void onFailure(Throwable caught) {
        // TODO Auto-generated method stub
        
      }

      public void onSuccess(TotalResults result) {
        int oldTotal = currentTotal;
        currentTotal = result.getTotalResults();
        if(result.isSearchFinished()) {
          totalResultsTimer.cancel();
         // totalLabel.setText(String.valueOf(result.getTotalResults()));
          totalLabel.setText(numberFormat.format(currentTotal));
          // adjust the current upper bound down if appropriate (so we
          // don't get Results 11 - 20 of 15)
          if(currentUpperBound > currentTotal) {
            currentUpperBound = currentTotal;
            upperBoundLabel.setText(numberFormat.format(currentUpperBound));
          }
        }
        else {
//          totalLabel.setText("at least " + result.getTotalResults() + " ");
          totalLabel.setText("at least " + numberFormat.format(currentTotal) + " ");
          if(!result.isSearchRunning()) {
            mainPanel.add(keepSearchingAnchor);
            totalResultsTimer.cancel();
          }
        }
        if(currentTotal != oldTotal) {
          fireResultCountChanged(oldTotal);
        }
      }
    });
  }
  
  public void addResultCountListener(ResultCountListener l) {
    listeners.add(l);
  }
  
  public void removeResultCountListener(ResultCountListener l) {
    listeners.remove(l);
  }

  private void fireResultCountChanged(int oldTotal) {
    for(ResultCountListener l : listeners) {
      l.resultCountChanged(oldTotal, currentTotal);
    }
  }

}
