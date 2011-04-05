/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licensed under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
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
