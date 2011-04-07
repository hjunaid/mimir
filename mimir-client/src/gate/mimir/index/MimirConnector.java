package gate.mimir.index;


import gate.Document;
import gate.mimir.tool.WebUtils;

import java.io.IOException;
import java.net.URL;

/**
 * Utility class that implements the client side of the Mimir RPC indexing
 * protocol. 
 */
public class MimirConnector {
  public static final String MIMIR_URI_FEATURE = "gate.mimir.uri";
  
  private WebUtils webUtils;
  
  private static MimirConnector staticConnector;
  
  public MimirConnector(WebUtils webUtils) {
    this.webUtils = webUtils;
  }
  
  public MimirConnector() {
    this(WebUtils.staticWebUtils());
  }
  
  public static MimirConnector defaultConnector() {
    if(staticConnector == null) {
      staticConnector = new MimirConnector();
    }
    return staticConnector;
  }

  /**
   * @deprecated use the instance method {@link #sendToMimir(Document, String, URL)}.
   */
  public static void indexDocument(Document doc, String documentURI,
          URL indexURL) throws IOException {
    defaultConnector().sendToMimir(doc, documentURI, indexURL);
  }
  
  /**
   * Pass the given GATE document to the Mimir index at the given URL for
   * indexing.  The document should match the expectations of the Mimir index
   * to which it is being sent, in particular it should include the token and
   * semantic annotation types that the index expects.  This method has no way
   * of checking that those expectations are met, and will not complain if they
   * are not, but in that case the resulting index will not contain any useful
   * information.
   *
   * @param doc the document to index.
   * @param documentURI the URI that should be used to represent the document
   *         in the index.  May be null, in which case the index will assign a
   *         URI itself.
   * @param indexURL the URL of the mimir index that is to receive the
   *         document.  This would typically be of the form
   *         <code>http://server:port/mimir/&lt;index UUID&gt;/</code>.
   * @throws IOException if any error occurs communicating with the Mimir
   *         service.
   */
  public void sendToMimir(Document doc, String documentURI,
          URL indexURL) throws IOException {
    boolean uriFeatureWasSet = false;
    Object oldUriFeatureValue = null;

    if(documentURI != null) {
      // set the URI as a document feature, saving the old value (if any)
      uriFeatureWasSet = doc.getFeatures().containsKey(MIMIR_URI_FEATURE);
      oldUriFeatureValue = doc.getFeatures().get(MIMIR_URI_FEATURE);
      doc.getFeatures().put(MIMIR_URI_FEATURE, documentURI);
    }
    // first phase - call the indexUrl action to find out where to post the
    // data
    StringBuilder indexURLString = new StringBuilder(indexURL.toExternalForm());
    if(indexURLString.length() == 0) {
      throw new IllegalArgumentException("No index URL specified");
    }
    if(indexURLString.charAt(indexURLString.length() - 1) != '/') {
      // add a slash if necessary
      indexURLString.append('/');
    }
    indexURLString.append("manage/indexUrl");
    StringBuilder postUrlBuilder = new StringBuilder();
    webUtils.getText(postUrlBuilder, indexURLString.toString());

    // second phase - post to the URL we were given
    webUtils.postObject(postUrlBuilder.toString(), doc);

    if(documentURI != null) {
      // reset the URI feature to the value it had (or didn't have) before
      if(uriFeatureWasSet) {
        doc.getFeatures().put(MIMIR_URI_FEATURE, oldUriFeatureValue);
      } else {
        doc.getFeatures().remove(MIMIR_URI_FEATURE);
      }
    }
  }
}

