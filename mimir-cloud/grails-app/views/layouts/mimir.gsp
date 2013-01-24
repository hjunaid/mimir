<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<title><g:layoutTitle default="Mimir" /></title>
<link rel="stylesheet"
	href="${resource(dir:'css',file:'mimir.css', plugin:'mimir-web')}" />
<link rel="shortcut icon"
	href="${resource(dir:'images',file:'favicon.ico', plugin:'mimir-web')}"
	type="image/x-icon" />
<link rel="stylesheet"
	href="${resource(dir:'css',file:'mimir-cloud.css')}" />	
<g:layoutHead />
<g:javascript library="application" />
<r:layoutResources />
</head>
<body>
	<div id="spinner" class="spinner" style="display: none;">
		<img src="${resource(dir:'images',file:'spinner.gif')}" alt="Spinner" />
	</div>
	<div id="paneHeader" class="paneHeader">
		<table>
			<tbody>
				<tr>
					<td valign="top" colSpan="3">
						<div align="left">
							<img alt="Mimir" align="top"
								src="${resource(dir:'images', file:'logo.png', plugin:'mimir-web')}" />
						</div></td>
					<td valign="top" width="20%">
						<div align="right">
							<img alt="Powered by M&iacute;mir"
								src="${resource(dir:'images', file:'logo-poweredby.png', plugin:'mimir-web')}"
								border="0" />
							<sec:ifLoggedIn>
								<br />You are logged in as <strong><sec:username /></strong>. (<g:link
									controller="logout">Log out</g:link>)</sec:ifLoggedIn>
						</div></td>
				</tr>
			</tbody>
		</table>
	</div>
	<div id="content">
		<g:layoutBody />
		<div align="center">
			<p>M&iacute;mir <mimir:version />, &copy; <a href="http://gate.ac.uk">GATE</a> 2013.
			</p>
		</div>
	</div>
	<r:layoutResources />	
</body>
</html>
