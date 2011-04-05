<%@ page import="gate.mimir.web.Index" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
  <meta http-equiv="content-type" content="text/html; charset=utf-8">

  <!-- Integrate with Sitemesh layouts           -->
  <meta name="layout" content="mimir" />

  <!--                                           -->
  <!-- Any title is fine                         -->
  <!--                                           -->
  <title>M&iacute;mir</title>

  <!-- Save the value for indexId in a JS var -->
  <g:javascript>
    var indexId = '${indexId.encodeAsJavaScript()}';
    var uriIsLink = '${uriIsLink ? true : false}';
  </g:javascript>
  
  <!--                                           -->
  <!-- This script loads your compiled module.   -->
  <!-- If you add any GWT meta tags, they must   -->
  <!-- be added before this line.                -->
  <!--                                           -->
  <script type="text/javascript" src="${resource(dir: 'gwt/gate.mimir.gus.Application', file: 'gate.mimir.gus.Application.nocache.js')}"></script>
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
  <h1>Searching index: ${Index.findByIndexId(indexId).name}</h1>
  <table align="center">
    <tr>
      <td id="searchbox"></td>
      <td id="searchbutton"></td>
    </tr>
  </table>
  <div class="bluebar" id="resultsbar"></div>
  <div id="resultstable"></div>

  <div align="center">
    <p>GATE Unified Search, powered by M&iacute;mir
    ${grailsApplication.metadata.'app.version'}.
    &copy; <a href="http://gate.ac.uk">GATE</a> and
    OWLIM &copy; <a href="http://www.ontotext.com">Ontotext</a>, 2010.</p>
  </div>
</body>
</html>
