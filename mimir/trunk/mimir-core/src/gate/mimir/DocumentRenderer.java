/*
 *  DocumentRenderer.java
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licensed under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 6 Oct 2009
 *
 *  $Id$ 
 */
package gate.mimir;

import gate.mimir.index.mg4j.zipcollection.DocumentData;
import gate.mimir.search.query.Binding;

import java.io.IOException;
import java.util.List;



/**
 * A document renderer is used to display a document and, optionally, a set of
 * query hits. 
 */
public interface DocumentRenderer {
  
  /**
   * Generates the output format (e.g. HTML) for a given document and a set of 
   * hits.
   * @param documentData the document to be rendered.
   * @param hits the list of hits to be highlighted.
   * @param ouput an {@link Appendable} to which the output should be written.  
   */
  public void render(DocumentData documentData, List<Binding> hits, 
          Appendable ouput) throws IOException;
}
