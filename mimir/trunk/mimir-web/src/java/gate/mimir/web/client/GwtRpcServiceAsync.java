package gate.mimir.web.client;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface GwtRpcServiceAsync {
    void search(java.lang.String indexId, java.lang.String query, AsyncCallback<String> callback);
    void releaseQuery(java.lang.String queryId, AsyncCallback callback);
}
