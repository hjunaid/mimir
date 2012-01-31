<%@ page import="gate.mimir.web.Index"%>
<%@ page import="gate.mimir.web.LocalIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<link rel="stylesheet" href="${resource(dir:'css',file:'progressbar.css')}" />
<g:javascript library="prototype" />
<title>Mimir index &quot;${indexInstance.name}&quot;</title>
<mimir:load/>

<g:if test="${indexInstance.state == Index.CLOSING}">
  <g:javascript src="json2.min.js" />
  <g:javascript>
    function updateProgress() {
      <g:remoteFunction
        url="[controller:'indexAdmin', action:'closingProgress', params:[indexId:indexInstance.indexId]]"
        method="GET" onSuccess="doProgressUpdate(e)"
        onFailure="progressUpdateFailed(e)" />;
    }

    function doProgressUpdate(response) {
      var result = JSON.parse(response.responseText);
      if(result.complete) {
        window.location.reload();
      } else {
        document.getElementById('closingProgress-bar').style.width = result.progress;
        document.getElementById('closingProgress-value').innerHTML = result.progress;
        setTimeout(updateProgress, 5000);
      }
    }

    function progressUpdateFailed(response) {
      var progValue = document.getElementById('closingProgress-value');
      progValue.innerHTML = progValue.innerHTML + ' (unable to update dynamically, please reload to check progress)';
    }
  </g:javascript>
</g:if>

</head>
<body>
  <div class="nav">
    <span class="menuButton"> <g:link class="home"
        controller="mimirStaticPages" action="admin">Admin Home</g:link>
   </span>
  </div>
  <div class="body">
    <g:if test="${flash.message}">
      <div class="message">
        ${flash.message}
      </div>
    </g:if>
    <h1>
      Mimir index &quot;${indexInstance.name}&quot;
    </h1>
    <div class="dialog">
      <table>
        <tbody>
          <tr class="prop">
            <td valign="top" class="name">Index Name:</td>
            <td valign="top" class="value">
              ${indexInstance.name}
            </td>
          </tr>
          <tr class="prop">
            <td valign="top" class="name">Index URL:</td>
            <td valign="top" class="value"><mimir:createIndexUrl
                indexId="${indexInstance.indexId}" /></td>
          </tr>
          <tr class="prop">
            <td valign="top" class="name">State:</td>
            <td valign="top" class="value">
              ${indexInstance.state}
            </td>
          </tr>
          <tr style="vertical-align:top">
            <td>Annotations indexed:</td>
            <td><mimir:revealAnchor id="annotsConf">Detail...</mimir:revealAnchor>
            <mimir:revealBlock id="annotsConf"><mimir:indexAnnotationsConfig index="${indexInstance}"/></mimir:revealBlock>
            </td>
          </tr>
          <g:if test="${indexInstance instanceof LocalIndex}" >
            <tr class="prop">
              <td valign="top" class="name">Scorer:</td>
              <td valign="top" class="value">
               ${indexInstance.scorer?:'No Scoring'}</td>
            </tr>          
          </g:if>          
          <g:if test="${indexInstance.state == Index.SEARCHING}">
            <tr class="prop">
              <td colspan="2">
                <g:link controller="search" action="index"
                  params="[indexId:indexInstance.indexId]"
                  title="Search this index">Search this index using the web interface.</g:link><br />
                <g:link controller="search"
                  action="help" params="[indexId:indexInstance.indexId]"
                  title="Search this index">Search this index using the XML service interface.</g:link><br />
                <g:link action="deletedDocuments"
                  params="[indexId:indexInstance.indexId]"
                  title="Add or remove 'deleted' markers for documents">Manage deleted documents.</g:link></td>
            </tr>
          </g:if>
          <g:elseif test="${indexInstance.state == Index.CLOSING}">
            <tr class="prop">
              <td>Index Closing Progress:</td>
              <td><mimir:progressbar id="closingProgress" value="${indexInstance.closingProgress()}" /><g:javascript>setTimeout(updateProgress, 5000);</g:javascript></td>
            </tr>
          </g:elseif>
        </tbody>
      </table>
    </div>
    <div class="buttons">
      <table>
        <tr>
          <g:form
            controller="${grails.util.GrailsNameUtils.getPropertyNameRepresentation(indexInstance.getClass())}">
            <input type="hidden" name="id" value="${indexInstance?.id}" />
            <td><span class="button"> <g:actionSubmit class="show"
                  action="Show" value="Details"
                  title="Click to see more information about this index." /> </span>
            </td>
            <td><span class="button"> <g:actionSubmit class="edit"
                  value="Edit" title="Click to modify this index." /> </span>
            </td>
            <td><span class="button"> <g:actionSubmit class="delete"
                  title="Click to delete this index."
                  onclick="return confirm('Are you sure?');" value="Delete" />
            </span>
            </td>
          </g:form>
          <g:if test="${indexInstance.state == Index.INDEXING}">
            <g:form action="close" params="[indexId:indexInstance?.indexId]">
              <input type="hidden" name="indexId"
                value="${indexInstance?.indexId}" />
              <td><span class="button"> <g:submitButton
                    class="close"
                    title="Click to stop the indexing process and prepare this index for searching."
                    onclick="return confirm('Are you sure?');" name="Close" /> </span>
              </td>
            </g:form>
          </g:if>
        </tr>
      </table>
    </div>
  </div>
</body>
</html>
