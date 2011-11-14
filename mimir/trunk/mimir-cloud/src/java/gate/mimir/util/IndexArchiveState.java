/*
*  IndexArchiveState.java
*
*  Copyright (c) 2011, The University of Sheffield.
*
*  Valentin Tablan, 04 May 2011
*
*  $Id$
*/
package gate.mimir.util;

/**
 *  Enum for the current state of an index download.    
 */
public enum IndexArchiveState {
  /**
   * The archive is being prepared
   */
  PENDING,
  
  /**
   * We could not create the local archive for whatever reason (e.g. out of 
   * disk space). Downloads are not available. 
   */
  FAILED,
  
  /**
   * The local archive exists and is ready for download.
   */
  AVAILABLE
}
