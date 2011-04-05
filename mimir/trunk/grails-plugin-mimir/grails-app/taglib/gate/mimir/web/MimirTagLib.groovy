package gate.mimir.web;

import java.util.Locale;

import java.text.NumberFormat;

class MimirTagLib {
  
  static namespace = "mimir"

  /**
   * Pointer to the Grails plugin manager.
   */
  def pluginManager
  
  static NumberFormat percentNumberInstance = NumberFormat.getPercentInstance(Locale.US)

  static{
    percentNumberInstance.setMaximumFractionDigits(2)
    percentNumberInstance.setMinimumFractionDigits(2)
  }
  
  /**
   * Any page that uses the Mimir taglib must include this tag in its head 
   * section (in order to load the required supporting JS scripts). 
   */
  def load = { attrs, body ->
    out << 
'''
<script type="text/javascript">
<!--
    function toggle_visibility(id) {
       var e = document.getElementById(id);
       if(e.style.display == 'block')
          e.style.display = 'none';
       else
          e.style.display = 'block';
    }
//-->
</script>
'''
  }
  
  def createIndexUrl = { attrs, body ->
    out << request.scheme
    out << "://"
    out << request.serverName
    if((request.scheme == "https" && request.serverPort != 443) ||
       (request.scheme == "http" && request.severPort != 80)) {
      out << ":${request.serverPort}"
    }
    out << g.createLink(controller:"indexManagement", action:"info",
                        params:[indexId:attrs.indexId])
  }
  
  def createRemoteIndexUrl = { attrs, body ->
    out << request.scheme
    out << "://"
    out << request.serverName
    if((request.scheme == "https" && request.serverPort != 443) ||
       (request.scheme == "http" && request.severPort != 80)) {
      out << ":${request.serverPort}"
    }
    out << g.createLink(controller:"indexManagement", action:"infoBin",
                        params:[indexId:attrs.indexId])
  }

  /**
   * Creates an absolute URL pointing to the home page of the web app.
   */
  def createRootUrl = { attrs, body ->
    out << request.scheme
    out << "://"
    out << request.serverName
    if((request.scheme == "https" && request.serverPort != 443) ||
    (request.scheme == "http" && request.severPort != 80)) {
      out << ":${request.serverPort}"
    }
    out << request.contextPath
  }

  /**
   * Creates a span containing a progress bar.
   * The following attributes can be used:
   * <ul>
   *   <li><b>value</b>: the amount of progress that should be shown. This is a
   *   String representing a numeric value (parseable by Double.parseDouble) 
   *   representing a percentage.</li>
   *   <li><b>height</b>: the height of the progress bar. The value is a String
   *   in the style of the height CSS attribute, and defaults to 
   *   &quot;1em&quot;</li>
   *   <li><b>width</b>: the width of the progress bar. The value is a String
   *   in the style of the height CSS attribute, and defaults to 
   *   &quot;20em&quot;</li>
   *   <li><b>showtext</b>: should the value of the progress (as a percentage)
   *   be shown? Permitted values are <tt>true</tt> (default) and 
   *   <tt>false</tt>.</li> 
   *   <li><b>id</b>: a value for the id attribute of the span element created.
   *   </li> 
   * </ul>
   */
  def progressbar = { attrs, body ->
/* What we're trying to create looks like this:
<span>
  <div class="progressbar" style="width:20em; height:1em; display:inline-block; margin-right:5px;vertical-align:middle;">
    <span class="progress" style="width:30%">
    </span>
  </div><span style="vertical-align:middle;">30%</span>
</span>    
*/
    double value = attrs.value
    String heightStr = attrs.height
    String widthStr = attrs.width
    String idStr = attrs.id
    boolean showText = true
    if(attrs.showText){
      showText = Boolean.parseBoolean(attrs.showText)
    }
    
    out << "<span"
    if(idStr) out << " id=\"" + idStr + "\""
    out << "> <div class=\"progressbar\" style=\"display:inline-block; "
    out << "height:" + (heightStr ? heightStr : "1em") + "; "
    out << "width:" + (widthStr ? widthStr : "20em") + "; "
    out << (showText ? "margin-right:5px; vertical-align:middle;\">" : "\">")
    out << "<span class=\"progress\" style=\"width:"
    out << percentNumberInstance.format(value)
    out << "\"></span></div>"
    if(showText){
      out << "<span style=\"vertical-align:middle;\">"
      out << percentNumberInstance.format(value)
      out << "</span>"
    }
    out << "</span>"
  }
  
  /**
   * Creates the anchor that can be used to hide/unhide a reveal block.
   * Required attributes:
   * id: the id of the block to be hidden/revealed.
   */
  def revealAnchor = { attrs, body ->
    
    out << "<a href=\"#\" onClick=\"toggle_visibility('${attrs.id}')\">"
    out << body()
    out << "</a>"
  }

  /**
   * Creates a div that can be hidden/revealed by a revealAnchor.
   * Required attributes:
   * id: the same ID as used for the corresponding reveal anchor.
   */
  def revealBlock = { attrs, body ->
    out << "<div id=\"${attrs.id}\" style=\"display:none\">"  
    out << body()
    out << "</div>"
  }
  
  /**
   * Prints out the version of the M&iacute;mir plugin 
   */
  def version = {
    out << pluginManager.getGrailsPlugin("mimir-web").version
  }
}
