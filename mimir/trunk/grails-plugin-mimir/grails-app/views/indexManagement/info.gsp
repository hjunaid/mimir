<%@ page import="gate.mimir.web.Index" %>
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="layout" content="mimir" />
    <link rel="stylesheet" href="${resource(dir:'css',file:'progressbar.css')}" />
    <g:javascript library="prototype" />
    <title>Mimir index ${indexInstance.indexId}</title>
  </head>
  <body>
    <div class="body">
      <g:if test="${flash.message}">
        <div class="message">${flash.message}</div>
      </g:if>
      <h1>Mimir index &quot;${indexInstance.name}&quot;</h1>
      <g:if test="${indexInstance.state == Index.SEARCHING}">
        <p><g:link controller="gus" action="search"
              params="[indexId:indexInstance.indexId]"
              title="Search this index">Search this index.</g:link>
        </p>
      </g:if>
    </div>
  </body>
</html>
