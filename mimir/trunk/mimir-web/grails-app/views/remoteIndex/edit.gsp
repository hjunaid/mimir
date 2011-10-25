<%@ page import="gate.mimir.web.RemoteIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Edit Remote Index</title>
</head>
<body>
<div class="nav"><span class="menuButton"> <g:link class="home"
	controller="mimirStaticPages" action="index">Home</g:link> </span> <%--			
			<span class="menuButton">
				<g:link class="list" action="list">Remote Index List</g:link>
			</span>
			<span class="menuButton">
				<g:link class="create" action="create">Connect to New Remote Index</g:link>
			</span>
--%></div>
<div class="body">
<h1>Edit Remote Index</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if> <g:hasErrors bean="${remoteIndexInstance}">
	<div class="errors"><g:renderErrors bean="${remoteIndexInstance}"
		as="list" /></div>
</g:hasErrors> <g:form method="post">
	<input type="hidden" name="id" value="${remoteIndexInstance?.id}" />
	<input type="hidden" name="version"
		value="${remoteIndexInstance?.version}" />
	<div class="dialog">
	<table>
		<tbody>

			<tr class="prop">
				<td valign="top" class="name"><label for="indexId">Index ID:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'indexId','errors')}">
				<label for="indexId">
				${fieldValue(bean:remoteIndexInstance,field:'indexId')} </label></td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="name">Name:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'name','errors')}">
				<input type="text" id="name" name="name"
					value="${fieldValue(bean:remoteIndexInstance,field:'name')}" /></td>
			</tr>


			<tr class="prop">
				<td valign="top" class="name">State:</td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'state','errors')}">
				${fieldValue(bean:remoteIndexInstance,field:'state')}
				</td>
			</tr>

			<tr class="prop">
				<th valign="top" class="name" colspan="2">Remote Data:
				</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="state"
					style="margin-left: 20px">Remote URL:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'remoteUrl','errors')}">
				<input type="text" id="remoteUrl" name="remoteUrl"
					value="${fieldValue(bean:remoteIndexInstance,field:'remoteUrl')}" />
				</td>
			</tr>
			<tr>
			  <td colspan="2">This should be the "Index URL" from the target index's management page.</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="remoteUsername">Remote Username:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'remoteUsername','errors')}">
				<input type="text" id="remoteUsername" name="remoteUsername"
					value="${fieldValue(bean:remoteIndexInstance,field:'remoteUsername')}" />
				</td>
			</tr>
			<tr>
			  <td colspan="2">If the remote server requires authentication, enter here
			  the username that should be used when connecting.</td>
			</tr>
			
			<tr class="prop">
				<td valign="top" class="name"><label for="remotePassword">Remote Password:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'remotePassword','errors')}">
				<input type="password" id="remotePassword" name="remotePassword"
					value="${fieldValue(bean:remoteIndexInstance,field:'remotePassword')}" />
				</td>
			</tr>
			<tr>
			  <td colspan="2">If the remote server requires authentication, enter here
			  the password that should be used when connecting.</td>
			</tr>

      <tr class="prop">
        <td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
        <td valign="top" class="value"><g:checkBox
          name="uriIsExternalLink"
          value="${remoteIndexInstance.uriIsExternalLink}" /></td>
      </tr>

		</tbody>
	</table>
	</div>
	<div class="buttons"><span class="button"> <g:actionSubmit
		class="save" value="Update" /> </span> <span class="button"> <g:actionSubmit
		class="delete" onclick="return confirm('Are you sure?');"
		value="Delete" /> </span></div>
</g:form></div>
</body>
</html>
