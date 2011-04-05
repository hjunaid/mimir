<%@ page import="gate.mimir.web.SearchController" %>
<html>
	<head>
		<title>
			M&iacute;mir XML Service (<mimir:createRootUrl />)
		</title>
		<meta name="layout" content="mimir" />
	</head>
	<body>
		<div class="body">
			<h1>M&iacute;mir XML Service (<mimir:createRootUrl />)</h1>
			<g:if test="${flash.message}">
				<div class="message">${flash.message}</div>
			</g:if>

			<p>This is the M&iacute;mir search Web Service on 
			<mimir:createRootUrl />, searching index <b>&quot;${index.name}&quot;</b>.</p>
	<p>You can also <g:link controller="gus" action="search" 
	  params="[indexId:index.indexId]" 
	  title="Search this index">search this index using the web interface</g:link>.</p>
	<p>A call to this service consists of a normal HTTP connection to a URL like:
	<mimir:createIndexUrl indexId="${index.indexId}" />/search/<b>action</b>, 
	where the action value is the name of one of the supported actions, described below. 
	</p>	
  <p>
	Parameters may be supplied as query parameters with a GET request or in
	normal application/x-www-form-urlencoded form in a POST request.
	Alternatively, they may be supplied as XML (if the request content type
	is
	text/xml or application/xml) of the form:</p>
  <pre>
&lt;request xmlns=&quot;${SearchController.MIMIR_NAMESPACE}&quot;&gt;
  &lt;firstParam&gt;value&lt;/firstParam&gt;
  &lt;secondParam&gt;value&lt;/secondParam&gt;
&lt;/request&gt;</pre>

  <p>The first request to the service will return a session cookie, which
      must be passed back with all subsequent requests.</p>

  <div class="action-box">
    <span class="action-name">help</span>
    <span class="action-desc">Prints this help message.</span>
    <div class="list"><b>Parameters:</b> none</div>
    <b>Returns:</b> this help page.
  </div>
          
  <div class="action-box">
    <span class="action-name">postQuery</span>
    <span class="action-desc">Action for starting a new query.</span>
    <div class="list"><b>Parameters:</b>
	    <table>
	      <tr>
	      <td>queryString</td>
	      <td>the text of the query.</td>
	      </tr>
	    </table>
    </div>
    <b>Returns:</b> the ID of the new query, if successful.
  </div>

  <div class="action-box">
    <span class="action-name">hitCount</span>
    <span class="action-desc">Action for obtaining the number of available hits for a given query.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> the number of hits retrieved so far for the given query.
  </div>

  <div class="action-box">
    <span class="action-name">docCount</span>
    <span class="action-desc">Action for obtaining the number of distinct documents that have hits for a given query.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> the number of distinct documents that have been found so far
     to include hits for the given query. 
  </div>

  <div class="action-box">
    <span class="action-name">docStats</span>
    <span class="action-desc">Action for obtaining the number of available hits for a given query, for each of the documents.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
        <tr>
        <td>startIndex</td>
        <td>the index of the first desired document. This value should greater 
        or equal to zero, and smaller than the value returned by the last call
        to <b>docCount</b>!</td>
        </tr>
        <tr>
        <td>count</td>
        <td>the number of desired documents. This value should be greater than 
        zero, and smaller than <b>docCount - startIndex</b>!</td>
        </tr>        
      </table>
    </div>
    <b>Returns:</b> for each requested document, its ID and the number of hits. 
  </div>
  
  <div class="action-box">
    <span class="action-name">hits</span>
    <span class="action-desc">Action for obtaining a set of hits.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
        <tr>
        <td>startIndex</td>
        <td>the index of the first desired hit.</td>
        </tr>
        <tr>
        <td>count</td>
        <td>the number of desired hits.</td>
        </tr>
      </table>
    </div>
    <p><b>Returns:</b> a list of hits, which may be:
      <ul>
        <li><b>null</b>, if no more hits exist,</li>
        <li><b>empty</b>, if no more hits have been found so far. In this case, 
        you should call <b>getMoreHits</b> to trigger a new search stage, in 
        order to obtain more hits. A future call to this action may return 
        more hits (if more were obtained in the mean time).</li>
        <li><b>shorter than the value of maxHits</b>, if not enough hits have 
        been retrieved so far.</li>
      </ul>
      </p>
  </div>
  

  <div class="action-box">
    <span class="action-name">getMoreHits</span>
    <span class="action-desc">Asks the query executor to restart the search 
    process and try to get more hits.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
      </table>
    </div>
    <p><b>Returns:</b> the exit state (success or error).
      </p>
  </div>
  
  <div class="action-box">
    <span class="action-name">isActive</span>
    <span class="action-desc">Finds out whether a specified query is currently 
    active. If the query is not currently active (which means that the latest
    search stage has completed, due to either heaving found enough hits, or the
    specified timeout having lapsed), it can be re-started by calling 
    <b>getMoreHits</b>.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> a boolean value representing the state of the query.
  </div>  
  
    <div class="action-box">
    <span class="action-name">isComplete</span>
    <span class="action-desc">Finds out whether a specified query has completed
    its execution (it has found all the possible hits in the index). A query 
    that completed cannot be restarted (as it has already found everything) but
    can still be queried to obtain the document statistics, the hits, etc. 
    Remember to <b>close</b> the query when it is not needed any more!</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> a boolean value representing the state of the query.
  </div>
  
  <div class="action-box">
    <span class="action-name">renderDocument</span>
    <span class="action-desc">Renders the document text and hits, in the context
    of a given query. The html of the document is rendered directly to the 
    response stream of this connection.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
        <tr>
        <td>documentId</td>
        <td>the ID of the requested document (as obtained from the <tt>documentMetadata</tt> call).</td>
        </tr>        
      </table>
    </div>
    <b>Returns:</b> the HTML source of the rendered document.
  </div>

  <div class="action-box">
    <span class="action-name">documentMetadata</span>
    <span class="action-desc">Action for obtaining the document metadata.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of an active query, to be used as a context for this call.</td>
        </tr>
        <tr>
        <td>documentId</td>
        <td>the ID of the desired document</td>
        </tr>
      </table>
      <p><b>Returns:</b> 
      <ul>
        <li>the document URI</li>
        <li>the document title</li>
      </ul></p>
    </div>
  </div>

  <div class="action-box">
    <span class="action-name">documentText</span>
    <span class="action-desc">Action for obtaining [a segment of] the text of a document.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of an active query, to be used as a context for this call.</td>
        </tr>
        <tr>
        <td>documentId</td>
        <td>the ID of the desired document</td>
        </tr>
        <tr>
        <td>position</td>
        <td>(optional) the index of the first token to be returned,
      defaults to 0 if omitted, i.e. start from the beginning of the
      document.</td>
        </tr>
        <tr>
        <td>length</td>
        <td>(optional) the number of tokens (and spaces) to be returned.
      If omitted, all tokens from position to the end of the document will
      be returned.</td>
        </tr>          
      </table>
      <p><b>Returns:</b> 
      <ul>
        <li>the document URI</li>
        <li>the document title</li>
      </ul></p>
    </div>
  </div>

  <div class="action-box">
    <span class="action-name">close</span>
    <span class="action-desc">Action for releasing a query.</span>
    <div class="list"><b>Parameters:</b>
      <table>
        <tr>
        <td>queryId</td>
        <td>the ID of the requested query.</td>
        </tr>
      </table>
    </div>
    <b>Returns:</b> the exit state (success or error).
  </div>

</div>
</body>
</html>
			