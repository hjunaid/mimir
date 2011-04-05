package gate.mimir.tool;

import gate.util.GateRuntimeException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.bind.DatatypeConverter;

/**
 * Extension of the standard WebUtils class that passes a fixed
 * HTTP basic authentication header with all its HTTP requests.
 */
public class BasicAuthWebUtils extends WebUtils {

  private String authHeader;
  
  public BasicAuthWebUtils(String username, String password) {
    super();
    buildAuthHeader(username, password);
  }
  
  public BasicAuthWebUtils(CookieHandler cookieJar, String username, String password) {
    super(cookieJar);
    buildAuthHeader(username, password);
  }
  
  protected void buildAuthHeader(String username, String password) {
    try {
      String userPass = username + ":" + password;
      authHeader = "Basic " + DatatypeConverter.printBase64Binary(userPass.getBytes("UTF-8"));
    } catch(UnsupportedEncodingException e) {
      throw new GateRuntimeException("UTF-8 encoding not supported by this JVM!", e);
    }
  }

  /**
   * Open a connection to the given URL, supplying the configured
   * Authorization header.
   */
  @Override
  protected URLConnection openURLConnection(URL u) throws IOException {
    URLConnection conn = super.openURLConnection(u);
    conn.setRequestProperty("Authorization", authHeader);
    return conn;
  }
}
