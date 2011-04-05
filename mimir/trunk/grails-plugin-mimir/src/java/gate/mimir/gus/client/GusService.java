package gate.mimir.gus.client;

import java.util.List;

import com.google.gwt.user.client.rpc.RemoteService;

public interface GusService extends RemoteService {
  String search(String indexId, String query) throws SearchException;
  void releaseQuery(String queryID);
  List<QueryResult> getHits(String queryID, int contextSize,
          int numHits, int startIndex) throws SearchException;
  
  TotalResults getTotalResults(String queryID) throws SearchException;
  void runQuery(String queryID) throws SearchException;
  
  java.lang.String[][] getAnnotationsConfig(String indexId);
}
