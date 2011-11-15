/*
 *  MimirScorer.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 14 September 2011
 *
 *  $Id$
 */
package gate.mimir.search.score;

import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import it.unimi.dsi.mg4j.search.DocumentIterator;
import it.unimi.dsi.mg4j.search.score.DelegatingScorer;

import java.io.IOException;

public interface MimirScorer extends DelegatingScorer {
  public abstract Binding nextHit() throws IOException;

  
  /**
   * The DocumentIterator provided <b>must</b> be a {@link QueryExecutor}.
   */
  @Override
  public void wrap(DocumentIterator queryExecutor) throws IOException;
  
  
  public int nextDocument(int greaterThan) throws IOException;
  
}