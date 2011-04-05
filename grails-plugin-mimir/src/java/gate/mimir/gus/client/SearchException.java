package gate.mimir.gus.client;

import com.google.gwt.user.client.rpc.IsSerializable;

public class SearchException extends Exception implements IsSerializable {
  private static final long serialVersionUID = 1L;

  public SearchException() {
    super();
  }

  public SearchException(String message) {
    super(message);
  }
}
