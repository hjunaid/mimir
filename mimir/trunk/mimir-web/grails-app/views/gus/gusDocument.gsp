<html>
    <head>
        <title><g:message code="gus.document.title" args="${[documentTitle]}" /></title>
        <meta name="layout" content="mimir" />
        <%-- 
        <g:if test="${baseHref}"><base href="${baseHref}"></g:if> 
        --%>        
    </head>
    <body>
      <g:if test="${documentTitle != null}">
       <h1><g:message code="gus.document.heading" args="${[documentTitle]}" /></h1>
        <gus:documentContent indexId="${indexId}" documentId="${documentId}"
            queryId="${queryId}" />
      </g:if>
      <g:else>
        <p>Cannot find query with given ID; perhaps your session expired. Please try your search again!</p>
      </g:else>
    </body>
</html>
