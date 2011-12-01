package gate.mimir.web.client;

import com.google.gwt.user.client.rpc.IsSerializable;

public class GwtRpcException extends Exception implements IsSerializable {
  private static final long serialVersionUID = 1L;

  public GwtRpcException() {
    super();
  }

  public GwtRpcException(String message) {
    super(message);
  }
}
