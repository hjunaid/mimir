<html>
    <head>
        <title><g:layoutTitle default="Mimir" /></title>
        <link rel="stylesheet" href="${resource(dir:'css',file:'main.css')}" />
        <link rel="shortcut icon" href="${resource(dir:'images',file:'favicon.ico')}" type="image/x-icon" />
        <g:layoutHead />
        <g:javascript library="application" />			
        <r:layoutResources />	
    </head>
    <body>
        <div id="spinner" class="spinner" style="display:none;">
            <img src="${resource(dir:'images',file:'spinner.gif')}" alt="Spinner" />
        </div>	
        <div class="logo"><img src="${resource(dir:'images',file:'logo.png')}" alt="Logo" /></div>	
        <g:layoutBody />		
        <r:layoutResources />
    </body>	
</html>
