package gate.mimir.web.client;


import com.google.gwt.user.client.rpc.RemoteService;

public interface GwtRpcService extends RemoteService {
    String search(java.lang.String indexId, java.lang.String query) throws GwtRpcException;
    void releaseQuery(java.lang.String queryId);
}
