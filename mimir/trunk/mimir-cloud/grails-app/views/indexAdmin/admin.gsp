<%@page import="gate.mimir.web.LocalIndex"%>
<%@ page import="gate.mimir.web.Index" %>
<%@ page import="org.codehaus.groovy.grails.commons.ConfigurationHolder" %>
<html>
	<head>
		<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
		<meta name="layout" content="mimir" />
		<link rel="stylesheet" href="${resource(dir:'css',file:'progressbar.css')}" />
		<g:javascript library="prototype" />
		<mimir:load/>
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
			<g:set var="row" value="${0}" />
				<table>
					<tbody>
						<tr class="${row++ % 2 == 0 ? 'even' :'odd'}">
							<td valign="top" class="name">Index Name:</td>
							<td valign="top" class="value">${indexInstance.name}</td>
						</tr>
						<tr class="${row++ % 2 == 0 ? 'even' :'odd'}">
							<td valign="top" class="name">Index URL:</td>
							<td valign="top" class="value">
							<g:if test="${ConfigurationHolder.config.gate.mimir.indexUrlBase}">
								<mimir:createIndexUrl indexId="${indexInstance.indexId}" 
										urlBase="${ConfigurationHolder.config.gate.mimir.indexUrlBase}" />
							</g:if>
							<g:else>
								<mimir:createIndexUrl indexId="${indexInstance.indexId}" />
							</g:else>
							<br />
							<p>This URL can be used for API access to the index, for example 
							when creating a federated index or when configuring the output of
							a GATECloud.net Annotation Job.</p>
						  <p>When running inside the GATECloud.net cloud, the URL of your
						  index <u>changes every time you restart your M&iacute;mir 
						  server</u>! If you use it to create a federated index, then you 
						  will need to re-create your Remote index instance every time you 
						  restart.</p>
						  <p>For security reasons, the firewall only allows this URL to be 
						  accessed by other M&iacute;mir servers that you own (so that you
						  can create federated indexes) and by your Annotation Jobs. If you
						  have other requirements please contact the GATECloud.net team.</p>							
							</td>
						</tr>
						
						<tr class="${row++ % 2 == 0 ? 'even' :'odd'}">
							<td valign="top" class="name">State:</td>
							<td valign="top" class="value">${indexInstance.state}</td>
						</tr>
	          <tr class="${row++ % 2 == 0 ? 'even' :'odd'}">
	            <td>Annotations indexed:</td>
	            <td><mimir:revealAnchor id="annotsConf">Detail...</mimir:revealAnchor>
	            <mimir:revealBlock id="annotsConf"><mimir:indexAnnotationsConfig index="${indexInstance}"/></mimir:revealBlock>
	            </td>
	          </tr>
	          <g:if test="${indexInstance instanceof LocalIndex}" >
	            <tr class="${row++ % 2 == 0 ? 'even' :'odd'}">
	              <td valign="top" class="name">Scorer:</td>
	              <td valign="top" class="value">
	               ${indexInstance.scorer?:'No Scoring'}</td>
	            </tr>          
	          </g:if>

						<g:if test="${indexInstance.state == Index.SEARCHING}">
							<tr class="${row++ % 2 == 0 ? 'even' :'odd'}">
								<td colspan="2">
									<g:link controller="search" action="index"
										params="[indexId:indexInstance.indexId]" title="Search this index">Search this
										index using the web interface.</g:link><br />
										<g:link controller="search" action="help"
                    params="[indexId:indexInstance.indexId]" title="Search this index">Search this
                    index using the XML service interface.</g:link>
								</td>
							</tr>
						</g:if>
						<g:elseif test="${indexInstance.state == Index.CLOSING}">
							<tr class="${row++ % 2 == 0 ? 'even' :'odd'}">
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
	        <g:elseif test="${indexInstance instanceof gate.mimir.web.LocalIndex && indexInstance.state == Index.SEARCHING}">
	          <g:form action="download" controller="indexDownload" id="${indexInstance?.id}"  method="GET">
              <td><span class="button"><input type="submit" class="download" value="Download" 
              title="Click to download this index." />
              </span></td>	            
	          </g:form>
	          <g:form action="downloadNew" controller="indexDownload" id="${indexInstance?.id}" method="GET">
              <td><span class="button">
                <input type="submit" class="download"
                  title="Click to force the creation of a new index archive and then download it." 
                  value="[Re-]Archive and Download"/>
              </span></td>
	          </g:form>
	        </g:elseif>
         </tr>
        </table>
			</div>
		</div>
	</body>
</html>
