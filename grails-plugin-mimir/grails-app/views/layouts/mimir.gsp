<html>
    <head>
        <title><g:layoutTitle default="Mimir" /></title>
        <link rel="stylesheet" href="${resource(dir:'css',file:'mimir.css')}" />
        <link rel="shortcut icon" href="${resource(dir:'images',file:'favicon.ico')}" type="image/x-icon" />
        <g:layoutHead />
        <g:javascript library="application" />				
    </head>
    <body>
        <div id="spinner" class="spinner" style="display:none;">
            <img src="${resource(dir:'images',file:'spinner.gif')}" alt="Spinner" />
        </div>	
        <div id="paneHeader" class="paneHeader">
          <table>
            <tbody>
            <tr>
              <td  valign="top" colSpan="3">
                <div align="left"><img alt="Mimir" align="top" 
                src="${resource(dir:'images', file:'logo.png')}" /></div></td>
              <td valign="top" width="20%">
                <div align="right"><img alt="Powered by M&iacute;mir"
                src="${resource(dir:'images', file:'logo-poweredby.png')}"
                border="0"/></div></td></tr></tbody></table>
        </div>
        <div id="content">
          <g:layoutBody />
        </div>
    </body>
</html>
