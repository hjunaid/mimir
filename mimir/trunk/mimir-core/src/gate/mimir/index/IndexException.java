/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licensed under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
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
