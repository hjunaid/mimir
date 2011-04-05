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
