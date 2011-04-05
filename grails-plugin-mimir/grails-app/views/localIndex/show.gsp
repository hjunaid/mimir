
<%@ page import="gate.mimir.web.Index"%>
<%@ page import="gate.mimir.web.LocalIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Show LocalIndex</title>
</head>
<body>
<div class="nav"><span class="menuButton"> <g:link class="home"
	controller="mimirStaticPages" action="index">Home</g:link> </span> <%--
			<span class="menuButton">
				<g:link class="list" action="list">Local Index List</g:link>
			</span>
			<span class="menuButton">
				<g:link class="create" action="create">Create New Local Index for
					Indexing</g:link>
			</span>
			<span class="menuButton">
				<g:link class="create" action="importIndex">Import Existing Index for
					Searching</g:link>
				--%></div>
<div class="body">
<h1>Show LocalIndex</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if>
<div class="dialog">
<table>
	<tbody>
		<tr class="prop">
			<td valign="top" class="name">Type:</td>
			<td valign="top" class="value">Local Index</td>
		</tr>
		<tr class="prop">
			<td valign="top" class="name">Name:</td>

			<td valign="top" class="value">
			${fieldValue(bean:localIndexInstance,
								field:'name')}
			</td>

		</tr>

		<tr class="prop">
			<td valign="top" class="name">Index UUID:</td>

			<td valign="top" class="value">
			${fieldValue(bean:localIndexInstance,
								field:'indexId')}
			</td>

		</tr>

		<tr class="prop">
			<td valign="top" class="name">Index URL:</td>

			<td valign="top" class="value"><mimir:createIndexUrl
				indexId="${localIndexInstance.indexId}" /></td>
		</tr>

		<tr class="prop">
			<td valign="top" class="name">State:</td>

			<td valign="top" class="value">
			${fieldValue(bean:localIndexInstance,
								field:'state')}
			</td>

		</tr>

		<tr class="prop">
			<td valign="top" class="name">Index Directory:</td>

			<td valign="top" class="value">
			${fieldValue(bean:localIndexInstance,
								field:'indexDirectory')}
			</td>

		</tr>

		<tr class="prop">
			<td valign="top" class="name"><label for="uriIsExternalLink">Document
			URIs are external links:</label></td>
			<td valign="top" class="value"><g:formatBoolean
				boolean="${localIndexInstance.uriIsExternalLink}" /></td>
		</tr>
	</tbody>
</table>
</div>
<div class="buttons"><g:form>
	<input type="hidden" name="id" value="${localIndexInstance?.id}" />
	<span class="button"> <g:actionSubmit class="edit" value="Edit"
		title="Click to modify this index." /> </span>
	<span class="button"> <g:actionSubmit class="delete"
		title="Click to delete this index."
		onclick="return confirm('Are you sure?');" value="Delete" /> </span>
</g:form></div>
</div>
</body>
</html>
