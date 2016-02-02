<html>
<head>
  <%-- Integrate with Sitemesh layouts           --%>
  <meta name="layout" content="mimir" />

  <title>M&iacute;mir Index &quot;${index?.name}&quot;</title>

  <!-- Pass some variable to the GWT code -->
  <r:script>
    var indexId = '${index?.indexId?.encodeAsJavaScript()}';
    var uriIsLink = '${index?.uriIsExternalLink}';
  </r:script>
  
  <%--                                           --%>
  <%-- This script loads your compiled module.   --%>
  <%-- If you add any GWT meta tags, they must   --%>
  <%-- be added before this line.                --%>
  <%--                                           --%>
  <script type="text/javascript" src="${r.resource(plugin:'mimir-web', dir: 'gwt/gate.mimir.web.UI', file: 'gate.mimir.web.UI.nocache.js')}"></script>
  
  <%-- Add any custom CSS from the current index --%>
  <g:if test="${index?.css}">
  <content tag="customCss">${index?.css}</content>
  </g:if>
</head>

<!--                                           -->
<!-- The body can have arbitrary html, or      -->
<!-- you can leave the body empty if you want  -->
<!-- to create a completely dynamic ui         -->
<!--                                           -->
<body>
  <!-- OPTIONAL: include this if you want history support -->
  <iframe id="__gwt_historyFrame" style="width:0;height:0;border:0"></iframe>

  <!-- Add the rest of the page here, or leave it -->
  <!-- blank for a completely dynamic interface.  -->
  
  <h1>Searching Index &quot;${index?.name}&quot;</h1>
  <div class="searchBox" id="searchBox"></div>
  <div class="bluebar" id="feedbackBar"></div>
  <div class="searchResults" id="searchResults"></div>
  <div id="pageLinks" class="pageLinks bluebar"></div>
</body>
</html>
