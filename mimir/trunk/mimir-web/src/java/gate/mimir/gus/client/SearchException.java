/*
 *  SearchException.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  $Id$
 */
package gate.mimir.gus.client;

import com.google.gwt.user.client.rpc.IsSerializable;

public class SearchException extends Exception implements IsSerializable {
  private static final long serialVersionUID = 1L;

  public SearchException() {
    super();
  }

  public SearchException(String message) {
    super(message);
  }
}
