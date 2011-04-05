
<%@ page import="gate.mimir.web.FederatedIndex"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Edit Federated Index</title>
</head>
<body>
<div class="nav"><span class="menuButton"><g:link class="home"
	controller="mimirStaticPages" action="index">Home</g:link></span> <%--            <span class="menuButton"><g:link class="list" action="list">Federated Index List</g:link></span>
            <span class="menuButton"><g:link class="create" action="create">New Federated Index</g:link></span>
--%></div>
<div class="body">
<h1>Edit Federated Index</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if> <g:hasErrors bean="${federatedIndexInstance}">
	<div class="errors"><g:renderErrors
		bean="${federatedIndexInstance}" as="list" /></div>
</g:hasErrors> <g:form method="post">
	<input type="hidden" name="id" value="${federatedIndexInstance?.id}" />
	<input type="hidden" name="version"
		value="${federatedIndexInstance?.version}" />
	<div class="dialog">
	<table>
		<tbody>

			<tr class="prop">
				<td valign="top" class="name"><label for="name">Name:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:federatedIndexInstance,field:'name','errors')}">
				<input type="text" id="name" name="name"
					value="${fieldValue(bean:federatedIndexInstance,field:'name')}" />
				</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="indexId">Index
				Id:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:federatedIndexInstance,field:'indexId','errors')}">
				<input type="text" id="indexId" name="indexId"
					value="${fieldValue(bean:federatedIndexInstance,field:'indexId')}" />
				</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="state">State:</label>
				</td>
				<td valign="top"
					class="value ${hasErrors(bean:federatedIndexInstance,field:'state','errors')}">
				${fieldValue(bean:federatedIndexInstance,field:'state')}
				</td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="indexes">Indexes:</label>
				</td>
				<td valign="top"
					class="value ${hasErrors(bean:federatedIndexInstance,field:'indexes','errors')}">
				<g:select name="indexes"
					from="${gate.mimir.web.Index.list().findAll { it.id != federatedIndexInstance.id } }"
					size="5" multiple="yes" optionKey="id" optionValue="name"
					value="${federatedIndexInstance?.indexes.collect{it.id}}" /></td>
			</tr>

      <tr class="prop">
        <td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
        <td valign="top" class="value"><g:checkBox
          name="uriIsExternalLink"
          value="${federatedIndexInstance.uriIsExternalLink}" /></td>
      </tr>

		</tbody>
	</table>
	</div>
	<div class="buttons"><span class="button"><g:actionSubmit
		class="save" value="Update" /></span> <span class="button"><g:actionSubmit
		class="delete" onclick="return confirm('Are you sure?');"
		value="Delete" /></span></div>
</g:form></div>
</body>
</html>
