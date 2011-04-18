/*
 *  IndexException.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007 (also included with this distribution as file 
 *  LICENCE-AGPL3.html).
 *
 *  A commercial licence is also available for organisations whose business
 *  models preclude the adoption of open source and is subject to a licence
 *  fee charged by the University of Sheffield. Please contact the GATE team
 *  (see http://gate.ac.uk/g8/contact) if you require a commercial licence.
 *
 *  Niraj Aswani, 19/March/07
 *
 *  $Id$
 */
package gate.mimir.index;

/**
 * Exception that should be thrown should something unexpected happens during
 * creating/updating/deleting index.
 * 
 * @author niraj
 * 
 */
public class IndexException extends Exception {

  /**
   * serial version id
   */
  private static final long serialVersionUID = 3257288036893931833L;

  /** Consructor of the class. */
  public IndexException(String msg) {
    super(msg);
  }

  public IndexException(Throwable t) {
    super(t);
  }

  public IndexException(String message, Throwable t) {
    super(message, t);
  }
}
