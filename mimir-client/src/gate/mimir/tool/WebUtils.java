package gate.mimir.tool;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.CharBuffer;
import java.util.List;
import java.util.Map;

/**
 * A collection of methods that provide various utility functions for web
 * applications.
 */
public class WebUtils {
  
  private CookieHandler cookieJar;
  
  public WebUtils(CookieHandler cookieJar) {
    this.cookieJar = cookieJar;
  }
  
  public WebUtils() {
    this(new CookieManager());
  }
  
  private static WebUtils staticWebUtils = null;
  
  public static WebUtils staticWebUtils() {
    if(staticWebUtils == null) {
      staticWebUtils = new WebUtils();
    }
    return staticWebUtils;
  }
  
  /**
   * Constructs a URL from a base URL segment and a set of query parameters.
   * @param urlBase the string that will be the prefix of the returned. 
   * This should include everything apart from the query part of the URL.
   * @param params an array of String values, which should contain alternating 
   * parameter names and parameter values. It is obvious that the size of this 
   * array must be an even number.
   * @return a URl built according to the provided parameters. If for example 
   * the following parameter values are provided: <b>urlBase:</b>
   * <tt>http://host:8080/appName/service</tt>; <b>params:</b> <tt>foo1, bar1, 
   * foo2, bar2, foo3, bar3</tt>, then the following URL would be returned:
   * <tt>http://host:8080/appName/service?foo1=bar1&amp;foo2=bar2&amp;foo3=bar3</tt>
   */
  public static String buildUrl(String urlBase, String... params){
    StringBuilder str = new StringBuilder(urlBase);
    if(params != null && params.length > 0){
      str.append('?');
      for(int i = 0 ; i < (params.length/2) - 1; i++){
        str.append(params[i * 2]);
        str.append('=');
        str.append(params[i * 2 + 1]);
        str.append('&');
      }
      //and now, the last parameter
      str.append(params[params.length - 2]);
      str.append('=');
      str.append(params[params.length - 1]);
    }
    return str.toString();
  }
  
  /**
   * Opens a URLConnection to the specified URL, setting any appropriate
   * cookie headers.  Can be overridden by subclasses that need to set
   * any additional request headers.
   */
  protected URLConnection openURLConnection(URL u) throws IOException {
    URLConnection conn = u.openConnection();
    try {
      // add the cookie headers
      Map<String, List<String>> cookieHeaders = cookieJar.get(
              u.toURI(), conn.getRequestProperties());
      for(Map.Entry<String, List<String>> e : cookieHeaders.entrySet()) {
        for(String val : e.getValue()) {
          conn.addRequestProperty(e.getKey(), val);
        }
      }
    } catch(URISyntaxException e) {
      throw (IOException)new IOException(
              "Error converting URL " + u + " to a URI").initCause(e);
    }
    return conn;
  }
  
  public static void main(String[] args){
    System.out.println(buildUrl("http://host:8080/appName/service", 
            "foo1", "bar1",
            "foo2", "bar2",
            "foo3", "bar3", 
            "foo4", "bar4"));
  }
  
  /**
   * @deprecated use the instance method {@link #getVoid(String, String...)}.
   */
  public static void actionReturnsVoid(String baseUrl, String... params)
          throws IOException {
    staticWebUtils().getVoid(baseUrl, params);
  }
  
  /**
   * Calls a web service action (i.e. it connects to a URL). If the connection 
   * fails, for whatever reason, or the response code is different from 
   * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
   * This method will drain (and discard) all content available from either the 
   * input and error streams of the resulting connection (which should permit
   * connection keepalives).
   *   
   * @param baseUrl the constant part of the URL to be accessed.
   * @param params an array of String values, that contain an alternation of
   * parameter name, and parameter values.
   * @throws IOException if the connection fails.
   */
  public void getVoid(String baseUrl, String... params) throws IOException {
    
    URL actionUrl = new URL(buildUrl(baseUrl, params));
    
    URLConnection conn = openURLConnection(actionUrl);
    if(conn instanceof HttpURLConnection) {
      HttpURLConnection httpConn = (HttpURLConnection)conn;
      try {
        httpConn.connect();
        int code = httpConn.getResponseCode();
        if(code != HttpURLConnection.HTTP_OK) {
          // try to get more details
          String message = httpConn.getResponseMessage();
          throw new IOException(code
                  + (message != null ? " (" + message + ")" : "")
                  + " Remote connection failed.");
        }
      } finally {
        // make sure the connection is drained, to allow connection keepalive
        drainConnection(httpConn);
      }
    } else {
      throw new IOException("Connection received is not HTTP!"
              + " Connection class: " + conn.getClass().getCanonicalName());
    }
  }
  
  /**
   * @deprecated use the instance method {@link #getText(Appendable, String, String...)}.
   */
  public static void actionReturnsText(Appendable out, String baseUrl, 
          String... params)
          throws IOException {
    staticWebUtils().getText(out, baseUrl, params);
  }
  
  /**
   * Calls a web service action (i.e. it connects to a URL). If the connection 
   * fails, for whatever reason, or the response code is different from 
   * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
   * This method will write all content available from the 
   * input stream of the resulting connection to the provided Appendable.
   * 
   * @param out an {@link Appendable} to which the output is written.
   * @param baseUrl the constant part of the URL to be accessed.
   * @param params an array of String values, that contain an alternation of
   * parameter name, and parameter values.
   * @throws IOException if the connection fails.
   */
  public void getText(Appendable out, String baseUrl, String...params)
          throws IOException {
    
    URL actionUrl = new URL(buildUrl(baseUrl, params));
    
    URLConnection conn = openURLConnection(actionUrl);
    if(conn instanceof HttpURLConnection) {
      HttpURLConnection httpConn = (HttpURLConnection)conn;
      try {
        httpConn.connect();
        int code = httpConn.getResponseCode();
        if(code == HttpURLConnection.HTTP_OK) {
          // copy content to appendable
          Reader r = new InputStreamReader(httpConn.getInputStream(), "UTF-8");
          char[] bufArray = new char[4096];
          CharBuffer buf = CharBuffer.wrap(bufArray);
          int charsRead = -1;
          while((charsRead = r.read(bufArray)) >= 0) {
            buf.position(0);
            buf.limit(charsRead);
            out.append(buf);
          }
        }else{
          // some problem -> try to get more details
          String message = httpConn.getResponseMessage();
          throw new IOException(code
                  + (message != null ? " (" + message + ")" : "")
                  + " Remote connection failed.");
        }
      }finally {
        // make sure the connection is drained, to allow connection keepalive
        drainConnection(httpConn);
      }
    } else {
      throw new IOException("Connection received is not HTTP!"
              + " Connection class: " + conn.getClass().getCanonicalName());
    }
  }

  /**
   * @deprecated use the instance method {@link #getInt(String, String...)}.
   */
  public static int actionReturnsInt(String baseUrl, String... params)
          throws IOException {
    return staticWebUtils().getInt(baseUrl, params);
  }
  
  /**
   * Calls a web service action (i.e. it connects to a URL), and reads a 
   * serialised int value from the resulting connection. If the connection 
   * fails, for whatever reason, or the response code is different from 
   * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
   * This method will drain (and discard) all additional content available from 
   * either the input and error streams of the resulting connection (which 
   * should permit connection keepalives).
   *   
   * @param baseUrl the constant part of the URL to be accessed.
   * @param params an array of String values, that contain an alternation of
   * parameter name, and parameter values.
   * @throws IOException if the connection fails.
   */
  public int getInt(String baseUrl, String... params)
          throws IOException {
    
    URL actionUrl = new URL(buildUrl(baseUrl, params));
    
    URLConnection conn = openURLConnection(actionUrl);
    if(conn instanceof HttpURLConnection) {
      HttpURLConnection httpConn = (HttpURLConnection)conn;
      int res;
      try {
        httpConn.connect();
        int code = httpConn.getResponseCode();
        if(code == HttpURLConnection.HTTP_OK) {
          res = new ObjectInputStream(httpConn.getInputStream()).readInt();
        }else{
          // try to get more details
          String message = httpConn.getResponseMessage();
          throw new IOException(code
                  + (message != null ? " (" + message + ")" : "")
                  + " Remote connection failed.");
        }
      } finally {
        // make sure the connection is drained, to allow connection keepalive
        drainConnection(httpConn);
      }
      return res;
    } else {
      throw new IOException("Connection received is not HTTP!"
              + " Connection class: " + conn.getClass().getCanonicalName());
    }
  }
  
  /**
   * @deprecated use the instance method {@link #getDouble(String, String...)}.
   */
  public static double actionReturnsDouble(String baseUrl, String... params)
          throws IOException {
    return staticWebUtils().getDouble(baseUrl, params);
  }

  /**
   * Calls a web service action (i.e. it connects to a URL), and reads a 
   * serialised double value from the resulting connection. If the connection 
   * fails, for whatever reason, or the response code is different from 
   * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
   * This method will drain (and discard) all additional content available from 
   * either the input and error streams of the resulting connection (which 
   * should permit connection keepalives).
   *   
   * @param baseUrl the constant part of the URL to be accessed.
   * @param params an array of String values, that contain an alternation of
   * parameter name, and parameter values.
   * @throws IOException if the connection fails.
   */
  public double getDouble(String baseUrl, String...params)
          throws IOException {
    
    URL actionUrl = new URL(buildUrl(baseUrl, params));
    
    URLConnection conn = openURLConnection(actionUrl);
    if(conn instanceof HttpURLConnection) {
      HttpURLConnection httpConn = (HttpURLConnection)conn;
      double res;
      try {
        httpConn.connect();
        int code = httpConn.getResponseCode();
        if(code == HttpURLConnection.HTTP_OK) {
          res = new ObjectInputStream(httpConn.getInputStream()).readDouble();
        }else{
          // try to get more details
          String message = httpConn.getResponseMessage();
          throw new IOException(code
                  + (message != null ? " (" + message + ")" : "")
                  + " Remote connection failed.");
        }
      } finally {
        // make sure the connection is drained, to allow connection keepalive
        drainConnection(httpConn);
      }
      return res;
    } else {
      throw new IOException("Connection received is not HTTP!"
              + " Connection class: " + conn.getClass().getCanonicalName());
    }
  }

  /**
   * @deprecated use the instance method {@link #getBoolean(String, String...)}
   */
  public static boolean actionReturnsBoolean(String baseUrl, String... params)
          throws IOException {
    return staticWebUtils().getBoolean(baseUrl, params);
  }
  
  /**
   * Calls a web service action (i.e. it connects to a URL), and reads a 
   * serialised boolean value from the resulting connection. If the connection 
   * fails, for whatever reason, or the response code is different from 
   * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
   * This method will drain (and discard) all additional content available from 
   * either the input and error streams of the resulting connection (which 
   * should permit connection keepalives).
   *   
   * @param baseUrl the constant part of the URL to be accessed.
   * @param params an array of String values, that contain an alternation of
   * parameter name, and parameter values.
   * @throws IOException if the connection fails.
   */
  public boolean getBoolean(String baseUrl, String... params)
          throws IOException {
        
    URL actionUrl = new URL(buildUrl(baseUrl, params));
    
    URLConnection conn = openURLConnection(actionUrl);
    if(conn instanceof HttpURLConnection) {
      HttpURLConnection httpConn = (HttpURLConnection)conn;
      boolean res;
      try {
        httpConn.connect();
        int code = httpConn.getResponseCode();
        if(code == HttpURLConnection.HTTP_OK) {
          res = new ObjectInputStream(httpConn.getInputStream()).readBoolean();
        }else{
          // try to get more details
          String message = httpConn.getResponseMessage();
          throw new IOException(code
                  + (message != null ? " (" + message + ")" : "")
                  + " Remote connection failed.");
        }
      } finally {
        // make sure the connection is drained, to allow connection keepalive
        drainConnection(httpConn);
      }
      return res;
    } else {
      throw new IOException("Connection received is not HTTP!"
              + " Connection class: " + conn.getClass().getCanonicalName());
    }
  }
  
  /**
   * @deprecated use the instance method {@link #getObject(String, String...)}.
   */
  public static Object actionReturnsObject(String baseUrl, String... params)
          throws IOException, ClassNotFoundException {
    return staticWebUtils().getObject(baseUrl, params);
  }
  
  /**
   * Calls a web service action (i.e. it connects to a URL), and reads a 
   * serialised Object value from the resulting connection. If the connection 
   * fails, for whatever reason, or the response code is different from 
   * {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
   * This method will drain (and discard) all additional content available from 
   * either the input and error streams of the resulting connection (which 
   * should permit connection keepalives).
   *   
   * @param baseUrl the constant part of the URL to be accessed.
   * @param params an array of String values, that contain an alternation of
   * parameter name, and parameter values.
   * @throws IOException if the connection fails.
   * @throws ClassNotFoundException if the value read from the remote connection
   * is of a type unknown to the local JVM.
   */
  public Object getObject(String baseUrl, String... params)
          throws IOException, ClassNotFoundException {
  
    URL actionUrl = new URL(buildUrl(baseUrl, params));
    URLConnection conn = openURLConnection(actionUrl);
    if(conn instanceof HttpURLConnection) {
      HttpURLConnection httpConn = (HttpURLConnection)conn;
      Object res;
      try {
        httpConn.connect();
        int code = httpConn.getResponseCode();
        if(code == HttpURLConnection.HTTP_OK) {
          res = new ObjectInputStream(httpConn.getInputStream()).readObject();
        }else{
          // try to get more details
          String message = httpConn.getResponseMessage();
          throw new IOException(code
                  + (message != null ? " (" + message + ")" : "")
                  + " Remote connection failed.");
        }
      } finally {
        // make sure the connection is drained, to allow connection keepalive
        drainConnection(httpConn);
      }
      return res;
    } else {
      throw new IOException("Connection received is not HTTP!"
              + " Connection class: " + conn.getClass().getCanonicalName());
    }
  }

  /**
   * @deprecated use the instance method {@link #postObject(String, Serializable, String...)}.
   */
  public static void actionTakesObject(String baseUrl, Serializable object,
          String... params) throws IOException {
    staticWebUtils().postObject(baseUrl, object, params);
  }
  
  /**
   * Calls a web service action (i.e. it connects to a URL) using the POST HTTP
   * method, sending the given object in Java serialized format as the request
   * body.  The request is sent using chunked transfer encoding, and the
   * request's Content-Type is set to application/octet-stream.  If the
   * connection fails, for whatever reason, or the response code is different
   * from {@link HttpURLConnection#HTTP_OK}, then an IOException is raised.
   * This method will drain (and discard) all content available from either the
   * input and error streams of the resulting connection (which should permit
   * connection keepalives).
   *   
   * @param baseUrl the constant part of the URL to be accessed.
   * @param object the object to serialize and send in the POST body
   * @param params an array of String values, that contain an alternation of
   * parameter name, and parameter values.
   * @throws IOException if the connection fails.
   */
  public void postObject(String baseUrl, Serializable object,
          String... params) throws IOException {
    URL actionUrl = new URL(buildUrl(baseUrl, params));
    URLConnection conn = openURLConnection(actionUrl);
    if(conn instanceof HttpURLConnection) {
      HttpURLConnection httpConn = (HttpURLConnection)conn;
      try {
        // enable output and set HTTP method
        httpConn.setDoOutput(true);
        httpConn.setRequestMethod("POST");
        // turn on chunking (we don't want to buffer the output if we don't
        // have to). 0 means use default chunk size.
        httpConn.setChunkedStreamingMode(0);
        // don't time out
        httpConn.setConnectTimeout(0);
        httpConn.setReadTimeout(0);
        
        // MIME type (defaults to form encoded, so must change it)
        httpConn.setRequestProperty("Content-Type", "application/octet-stream");

        // connect and send the object
        httpConn.connect();
        OutputStream httpOutputStream = httpConn.getOutputStream();
        ObjectOutputStream objectStream = new ObjectOutputStream(httpOutputStream);
        objectStream.writeObject(object);
        objectStream.close();

        int code = httpConn.getResponseCode();
        if(code != HttpURLConnection.HTTP_OK) {
          // try to get more details
          String message = httpConn.getResponseMessage();
          throw new IOException(code
                  + (message != null ? " (" + message + ")" : "")
                  + " Remote connection failed.");
        }
      } finally {
        // make sure the connection is drained, to allow connection keepalive
        drainConnection(httpConn);
      }
    } else {
      throw new IOException("Connection received is not HTTP!"
              + " Connection class: " + conn.getClass().getCanonicalName());
    }
  }
  
  /**
   * Read and discard the response body from an HttpURLConnection. This first
   * attempts to get the normal input stream (
   * {@link HttpURLConnection#getInputStream}) and read that, but if that fails
   * with an exception we attempt to read the error stream (
   * {@link HttpURLConnection#getErrorStream}) instead.  Also processes any
   * cookie-related headers from the response, storing them in the cookie jar.
   */
  private void drainConnection(HttpURLConnection httpConnection)
          throws IOException {
    try {
      // store cookies for future connections
      cookieJar.put(httpConnection.getURL().toURI(),
              httpConnection.getHeaderFields());
    } catch(URISyntaxException e1) {
      // ignore, we tried.
    }
    // small buffer - we're only expecting to drain a few dozen bytes from each
    // request, if that.
    byte[] buf = new byte[1024];
    try {
      InputStream in = httpConnection.getInputStream();
      while(in.read(buf) >= 0) {
        // do nothing
      }
      in.close();
    } catch(IOException e) {
      InputStream errStream = httpConnection.getErrorStream();
      if(errStream != null){
        while(errStream.read(buf) >= 0) {
          // do nothing
        }
        errStream.close();
      }
    }
  }
}
