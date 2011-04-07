<%@ page import="gate.mimir.web.Index" %>
<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
		<meta name="layout" content="mimir" />
		<link rel="stylesheet" href="${resource(dir:'css',file:'progressbar.css')}" />
		<g:javascript library="prototype" />
		<title>Mimir index &quot;${indexInstance.name}&quot;</title>

		<g:javascript>
			function pageLoaded() {
			<g:if test="${indexInstance.state == Index.CLOSING}">
				// start regular updates of the progress bar
				new Ajax.PeriodicalUpdater('closeProgress',
				'${g.createLink(controller:"indexAdmin",
				action:"closingProgress",
				params:[indexId:indexInstance.indexId]).encodeAsJavaScript()}', {
				frequency: 5
				});
      </g:if>
			}

			// can't do body onLoad="..." so use a prototype event handler instead
			Event.observe(window, 'load', pageLoaded, false);
		</g:javascript>
	</head>
	<body>
		<div class="nav">
			<span class="menuButton">
				<g:link class="home" controller="mimirStaticPages" action="index">Home</g:link>
			</span>

			<%--
			<span class="menuButton">
				<g:link class="list" action="list">LocalIndex List</g:link>
			</span>
			--%>
		</div>
		<div class="body">
			<g:if test="${flash.message}">
				<div class="message">${flash.message}</div>
			</g:if>
			<h1>Mimir index &quot;${indexInstance.name}&quot;</h1>
			<div class="dialog">
				<table>
					<tbody>
						<tr class="prop">
							<td valign="top" class="name">Index Name:</td>
							<td valign="top" class="value">${indexInstance.name}</td>
						</tr>
						<tr class="prop">
							<td valign="top" class="name">Index URL:</td>
							<td valign="top" class="value">
								<mimir:createIndexUrl indexId="${indexInstance.indexId}" />
							</td>
						</tr>
						<tr class="prop">
							<td valign="top" class="name">State:</td>
							<td valign="top" class="value">${indexInstance.state}</td>
						</tr>

						<g:if test="${indexInstance.state == Index.SEARCHING}">
							<tr class="prop">
								<td colspan="2">
									<g:link controller="gus" action="gus"
										params="[indexId:indexInstance.indexId]" title="Search this index">Search this
										index using the web interface.</g:link><br />
										<g:link controller="search" action="help"
                    params="[indexId:indexInstance.indexId]" title="Search this index">Search this
                    index using the XML service interface.</g:link>
								</td>
							</tr>
						</g:if>
						<g:elseif test="${indexInstance.state == Index.CLOSING}">
							<tr class="prop">
								<td>Index Closing Progress:</td>
								<td id="closeProgress"></td>
							</tr>
						</g:elseif>
					</tbody>
				</table>
			</div>
			<div class="buttons">
			  <table>
			   <tr>
					<g:form
						controller="${grails.util.GrailsNameUtils.getPropertyNameRepresentation(indexInstance.getClass())}">
						<input type="hidden" name="id" value="${indexInstance?.id}" />
						<td><span class="button">
							<g:actionSubmit class="show" action="Show" value="Details"
								title="Click to see more information about this index." />
						</span></td>
						<td><span class="button">
							<g:actionSubmit class="edit" value="Edit" title="Click to modify this index."/>
						</span></td>
						<td><span class="button">
							<g:actionSubmit class="delete"
							  title="Click to delete this index."
								onclick="return confirm('Are you sure?');" value="Delete" />
						</span></td>
					</g:form>
	        <g:if test="${indexInstance.state == Index.INDEXING}">
	          <g:form action="close" params="[indexId:indexInstance?.indexId]">
	            <input type="hidden" name="indexId" value="${indexInstance?.indexId}" />
              <td><span class="button">
                <g:submitButton class="close"
                  title="Click to stop the indexing process and prepare this index for searching."
                  onclick="return confirm('Are you sure?');" name="Close"/>
              </span></td>	            
	          </g:form>
	        </g:if>
         </tr>
        </table>
			</div>
		</div>
	</body>
</html>
