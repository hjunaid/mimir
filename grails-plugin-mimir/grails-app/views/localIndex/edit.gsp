
<%@ page import="gate.mimir.web.LocalIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Edit LocalIndex</title>
</head>
<body>
<div class="nav"><span class="menuButton"> <g:link class="home"
	controller="mimirStaticPages" action="index">Home</g:link> </span> <%--
			<span class="menuButton">
				<g:link class="list" action="list">LocalIndex List</g:link>
			</span>
			<span class="menuButton">
				<g:link class="create" action="create">New LocalIndex</g:link>
			</span>
			--%></div>
<div class="body">
<h1>Edit LocalIndex</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if> <g:hasErrors bean="${localIndexInstance}">
	<div class="errors"><g:renderErrors bean="${localIndexInstance}"
		as="list" /></div>
</g:hasErrors> <g:form method="post">
	<input type="hidden" name="id" value="${localIndexInstance?.id}" />
	<input type="hidden" name="version"
		value="${localIndexInstance?.version}" />
	<div class="dialog">
	<table>
		<tbody>

			<tr class="prop">
				<td valign="top" class="name"><label for="indexId">Index
				Id:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'indexId','errors')}">
				<input type="text" id="indexId" name="indexId"
					value="${fieldValue(bean:localIndexInstance,field:'indexId')}" />
				</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="state">State:</label>
				</td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'state','errors')}">
				${fieldValue(bean:localIndexInstance,field:'state')}
				</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="indexDirectory">Index
				Directory:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:localIndexInstance,field:'indexDirectory','errors')}">
				<input type="text" id="indexDirectory" name="indexDirectory"
					value="${fieldValue(bean:localIndexInstance,field:'indexDirectory')}" />
				</td>
			</tr>
			
      <tr class="prop">
        <td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
        <td valign="top" class="value"><g:checkBox
          name="uriIsExternalLink"
          value="${localIndexInstance.uriIsExternalLink}" /></td>
      </tr>

		</tbody>
	</table>
	</div>
	<div class="buttons"><span class="button"> <g:actionSubmit
		class="save" value="Save" action="Update"
		title="Click to save your changes." /> </span> <span class="button"> <g:actionSubmit
		class="delete" onclick="return confirm('Are you sure?');"
		value="Delete" /> </span></div>
</g:form></div>
</body>
</html>
