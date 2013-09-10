<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
    <head>
        <title><g:layoutTitle default="Mimir" /></title>
        <link rel="stylesheet" href="${resource(dir:'css',file:'mimir.css')}" />
        <%-- The query string is ignored but causes the browsers to refresh the 
             cached version. The query value should be changed whenever the 
             favicon changes. --%>
        <link rel="shortcut icon" href="${resource(dir:'images',file:'favicon.ico')}?v=2" type="image/x-icon" />
				<link rel="apple-touch-icon" href="${resource(dir: 'images', file: 'apple-touch-icon.png')}?v=2">
				<link rel="apple-touch-icon" sizes="114x114" href="${resource(dir: 'images', file: 'apple-touch-icon-retina.png')}?v=2">        
        <g:layoutHead />
        <g:javascript src="application.js" />
        <r:layoutResources/>
        <%-- Add any custom CSS content provided by the page. --%>
        <style type="text/css">
        <g:pageProperty name="page.customCss" default="" />
        </style>
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
				  <div align="center">
				    <p>M&iacute;mir <mimir:version />,  &copy; <a href="http://gate.ac.uk">GATE</a> 2013.</p>
				  </div>
        </div>
        <r:layoutResources/>
    </body>
</html>
