<html>
    <head>
        <title><g:message code="gus.document.title" args="${[documentTitle]}" /></title>
        <meta name="layout" content="mimir" />
  <%-- Add any custom CSS from the current index --%>
  <g:if test="${index?.css}">
  <content tag="customCss">${index?.css}</content>
  </g:if>        
    </head>
    <body>
      <g:if test="${documentTitle != null}">
       <h1><g:message code="gus.document.heading" args="${[documentTitle]}" /></h1>
        <mimir:documentContent indexId="${index?.id}" documentRank="${documentRank}"
            queryId="${queryId}" />
      </g:if>
      <g:else>
        <p>Cannot find query with given ID; perhaps your session expired. Please try your search again!</p>
      </g:else>
    </body>
</html>
