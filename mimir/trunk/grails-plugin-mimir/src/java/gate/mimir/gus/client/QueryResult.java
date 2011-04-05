package gate.mimir.gus.client;

import com.google.gwt.user.client.rpc.IsSerializable;

public class QueryResult implements IsSerializable {
  private int documentID;
  private String documentURI;
  private String documentTitle;
  private int termPosition;
  private int length;
  private String leftContext;
  private String spanText;
  private String rightContext;
  
  public int getDocumentID() {
    return documentID;
  }
  
  public void setDocumentID(int documentID) {
    this.documentID = documentID;
  }
  
  public String getDocumentURI() {
    return documentURI;
  }

  public void setDocumentURI(String documentURI) {
    this.documentURI = documentURI;
  }

  public String getDocumentTitle() {
    return documentTitle;
  }

  public void setDocumentTitle(String documentTitle) {
    this.documentTitle = documentTitle;
  }

  public int getTermPosition() {
    return termPosition;
  }
  
  public void setTermPosition(int termPosition) {
    this.termPosition = termPosition;
  }
  
  public int getLength() {
    return length;
  }
  
  public void setLength(int length) {
    this.length = length;
  }
  
  public String getLeftContext() {
    return leftContext;
  }
  
  public void setLeftContext(String leftContext) {
    this.leftContext = leftContext;
  }
  
  public String getSpanText() {
    return spanText;
  }
  
  public void setSpanText(String spanText) {
    this.spanText = spanText;
  }
  
  public String getRightContext() {
    return rightContext;
  }
  
  public void setRightContext(String rightContext) {
    this.rightContext = rightContext;
  }
}
