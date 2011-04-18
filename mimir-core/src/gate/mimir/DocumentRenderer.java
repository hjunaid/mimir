/*
 *  DocumentRenderer.java
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
