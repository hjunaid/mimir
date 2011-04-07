<%@ page import="gate.mimir.web.Index" %>
<ol>
  <g:each in="${indexes}" status="i" var="indexInstance">
    <g:set var="indexName" value="${indexInstance.name}" />
    <li>
      <g:link controller="indexAdmin" action="admin"
        params="[indexId:indexInstance.indexId]"
        title='Click to manage ${" " + indexName}'>${indexName}</g:link>
      <g:if test="${indexInstance.state == Index.SEARCHING}">
        (<g:link controller="gus" action="gus"
          params="[indexId:indexInstance.indexId]"
          title='Click to search ${" " + indexName}'>search</g:link>)
      </g:if>
    </li>
  </g:each>
</ol>
