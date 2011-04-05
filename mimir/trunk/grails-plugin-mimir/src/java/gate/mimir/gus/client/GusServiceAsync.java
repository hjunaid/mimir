package gate.mimir.gus.client;

import java.util.List;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface GusServiceAsync {
  void search(String indexId, String query, AsyncCallback<String> callback);
  void releaseQuery(String queryID, AsyncCallback<Void> callback);
  void getHits(String queryID, int contextSize, int numHits, int startIndex,
               AsyncCallback<List<QueryResult>> callback);
  
  void getTotalResults(String queryID, AsyncCallback<TotalResults> callback);
  void runQuery(String queryID, AsyncCallback<Void> callback);
  
  void getAnnotationsConfig(String indexId, AsyncCallback<String[][]> callback);
}
