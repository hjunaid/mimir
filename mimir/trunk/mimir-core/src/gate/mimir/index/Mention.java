/*
 *  Mention.java
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
 *  Valentin Tablan, 19 Feb 2009
 *
 *  $Id$
 */
package gate.mimir.index;

/**
 * Simple holder class holding the URI and length of a mention.
 */
public class Mention {
  private int length;

  private String uri;

  public Mention(String uri, int length) {
    this.uri = uri;
    this.length = length;
  }

  @Override
  public boolean equals(Object obj) {
    if(this == obj) return true;
    if(obj == null) return false;
    Mention other = (Mention)obj;
    if(length != other.length) return false;
    if(uri == null) {
      if(other.uri != null) return false;
    } else if(!uri.equals(other.uri)) return false;
    return true;
  }

  public int getLength() {
    return length;
  }

  public String getUri() {
    return uri;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + length;
    result = prime * result + ((uri == null) ? 0 : uri.hashCode());
    return result;
  }
}
