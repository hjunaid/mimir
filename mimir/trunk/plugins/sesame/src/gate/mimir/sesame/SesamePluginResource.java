/*
 * SesamePluginResource.java
 * 
 * Copyright (c) 2007-2011, The University of Sheffield.
 * 
 * This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html),
 * and is free software, licenced under the GNU Affero General Public License,
 * Version 3, November 2007 (also included with this distribution as file
 * LICENCE-AGPL3.html).
 * 
 * A commercial licence is also available for organisations whose business
 * models preclude the adoption of open source and is subject to a licence fee
 * charged by the University of Sheffield. Please contact the GATE team (see
 * http://gate.ac.uk/g8/contact) if you require a commercial licence.
 * 
 * $Id$
 */
package gate.mimir.sesame;

import gate.Resource;
import gate.creole.AbstractResource;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleResource;

/**
 * A simple no-function CREOLE resource only used so that we have some CREOLE
 * metadata. We use that to locate the JAR containing the classes, which we then
 * use to locate the associated resource files.
 */
@CreoleResource(isPrivate = true, comment = "A resource type only defined to use "
        + "its configuration side effects. It cannot be instantiated!")
public class SesamePluginResource extends AbstractResource {
  private static final long serialVersionUID = -3138987553373647753L;

  @Override
  public Resource init() throws ResourceInstantiationException {
    throw new ResourceInstantiationException("This resource has no actual "
            + "function and cannot be instantaited.");
  }
}
