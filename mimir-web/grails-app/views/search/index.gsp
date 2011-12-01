<html>
<head>
  <!-- Integrate with Sitemesh layouts           -->
  <meta name="layout" content="mimir" />

  <title>M&iacute;mir Index ${index?.name}</title>

  <!-- Pass some variable to the GWT code -->
  <g:javascript>
    var indexId = '${index?.indexId?.encodeAsJavaScript()}';
    var uriIsLink = '${index?.uriIsExternalLink}';
  </g:javascript>
  
  <!--                                           -->
  <!-- This script loads your compiled module.   -->
  <!-- If you add any GWT meta tags, they must   -->
  <!-- be added before this line.                -->
  <!--                                           -->
  <script type="text/javascript" src="${resource(dir: 'gwt/gate.mimir.web.UI', file: 'gate.mimir.web.UI.nocache.js')}"></script>
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
  
  <h1>Searching Index ${index?.name}</h1>
  <div class="searchbox" id="searchBox"> </div>
  <div class="bluebar" >Results</div>
  <div id="searchResults">
  </div>
</body>
</html>
