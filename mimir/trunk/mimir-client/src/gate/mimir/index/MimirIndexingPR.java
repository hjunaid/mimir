/*
 *  MimirIndexingPR.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 01 Dec 2011
 *  
 *  $Id$
 */
package gate.mimir.index;

import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.mimir.tool.WebUtils;
import gate.util.GateRuntimeException;

import java.net.URL;


/**
 * A simple PR for sending documents to a Mímir index.
 */
@CreoleResource(comment="A PR that sends documents to a Mímir server for indexing.",
 name="Mímir Indexing PR")
public class MimirIndexingPR extends AbstractLanguageAnalyser {

  private static final long serialVersionUID = 3291873032301133998L;

  private URL mimirIndexUrl;
  
  private String mimirUsername;
  
  private String mimirPassword;

  protected MimirConnector mimirConnector;
  
  
  
  public URL getMimirIndexUrl() {
    return mimirIndexUrl;
  }

  @CreoleParameter (comment="The Index URL, as obtained from the Mímir Server.")
  @RunTime
  public void setMimirIndexUrl(URL mimirIndexUrl) {
    this.mimirIndexUrl = mimirIndexUrl;
  }

  public String getMimirUsername() {
    return mimirUsername;
  }

  @CreoleParameter(comment="Username for authenticating to the Mímir server. Leave empty if no authentication is required.")
  @Optional
  @RunTime
  public void setMimirUsername(String mimirUsername) {
    this.mimirUsername = mimirUsername;
    // invalidate the connector
    this.mimirConnector = null;
  }

  public String getMimirPassword() {
    return mimirPassword;
  }

  @CreoleParameter(comment="Password for authenticating to the Mímir server. Leave empty if no authentication is required.")
  @Optional
  @RunTime
  public void setMimirPassword(String mimirPassword) {
    this.mimirPassword = mimirPassword;
    // invalidate the connector    
    this.mimirConnector = null;
  }

  @Override
  public void cleanup() {
    try {
      mimirConnector.close();
    } catch(Exception e) {
      throw new GateRuntimeException("Execption while closing Mímir connector", 
          e);
    }
    mimirConnector = null;
  }

  @Override
  public void execute() throws ExecutionException {
    try {
      if(mimirConnector == null) {
        // first run or config has changed: [re-]create
        if(mimirUsername != null && mimirUsername.length() > 0) {
          mimirConnector = new MimirConnector(mimirIndexUrl,
            new WebUtils(mimirUsername, mimirPassword));          
        } else {
          mimirConnector = new MimirConnector(mimirIndexUrl);  
        }
      }
      mimirConnector.sendToMimir(getDocument(), null);
    } catch(Exception e) {
      throw new ExecutionException(
        "Error communicating with the Mímir server", e);
    }
  }
  
}
