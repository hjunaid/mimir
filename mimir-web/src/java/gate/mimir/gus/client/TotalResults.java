/*
 *  TotalResults.java
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

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * A structure to hold information about the results found by a query.
 */
public class TotalResults implements IsSerializable {
  private int totalResults;
  private boolean searchFinished;
  private boolean searchRunning;
  
  public int getTotalResults() {
    return totalResults;
  }
  
  public void setTotalResults(int totalResults) {
    this.totalResults = totalResults;
  }
  
  public boolean isSearchFinished() {
    return searchFinished;
  }
  
  public void setSearchFinished(boolean searchFinished) {
    this.searchFinished = searchFinished;
  }
  
  public boolean isSearchRunning() {
    return searchRunning;
  }
  
  public void setSearchRunning(boolean searchRunning) {
    this.searchRunning = searchRunning;
  }
}
