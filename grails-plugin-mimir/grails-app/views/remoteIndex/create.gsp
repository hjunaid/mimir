<%@ page import="gate.mimir.web.*"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
<meta name="layout" content="mimir" />
<title>Connect to Remote Index</title>
</head>
<body>
<div class="nav"><span class="menuButton"> <g:link class="home"
	controller="mimirStaticPages" action="index">Home</g:link> </span> <%--			<span class="menuButton">
				<g:link class="list" action="list">Remote Index List</g:link>
			</span>
--%></div>
<div class="body">
<h1>Connect to Remote Index</h1>
<g:if test="${flash.message}">
	<div class="message">
	${flash.message}
	</div>
</g:if> <g:hasErrors bean="${remoteIndexInstance}">
	<div class="errors"><g:renderErrors bean="${remoteIndexInstance}"
		as="list" /></div>
</g:hasErrors> <g:form action="save" method="post">
	<div class="dialog">
	<table>
		<tbody>

			<tr class="prop">
				<td valign="top" class="name"><label for="name">Name:</label></td>
				<td valign="top"
					class="value ${hasErrors(bean:remoteIndexInstance,field:'name','errors')}">
				<input type="text" id="name" name="name"
					value="${fieldValue(bean:remoteIndexInstance,field:'name')}" /></td>
			</tr>

			<tr class="prop">
				<td valign="top" class="name"><label for="serverUrl">Remote
				URL:</label></td>
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
        <td valign="top" class="name"><label for="uriIsExternalLink">Document
      URIs are external links:</label></td>
        <td valign="top" class="value"><g:checkBox
          name="uriIsExternalLink"
          value="${remoteIndexInstance?.uriIsExternalLink}" /></td>
      </tr>

		</tbody>
	</table>
	</div>
	<div class="buttons"><span class="button"> <input
		class="save" type="submit" value="Create" /> </span></div>
</g:form></div>
</body>
</html>
