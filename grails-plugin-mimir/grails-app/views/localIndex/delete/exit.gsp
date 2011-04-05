<% 
    flash.message = "Local index ${localIndexInstance.name} deleted"
    response.sendRedirect(g.createLink(uri:'/').toString())
%>
