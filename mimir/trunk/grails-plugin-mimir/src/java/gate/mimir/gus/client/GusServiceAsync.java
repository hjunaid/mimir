/*
 *  GusServiceAsync.java
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
 *  $Id$
 */
package gate.mimir.gus.client;

import java.util.List;

import com.google.gwt.user.client.rpc.AsyncCallback;

public interface GusServiceAsync {
  void search(String indexId, String query, AsyncCallback<String> callback);
  void releaseQuery(String queryID, AsyncCallback<Void> callback);
  void getHits(String queryID, int contextSize, int numHits, int startIndex,
               AsyncCallback<List<QueryResult>> callback);
  
  void getTotalResults(String queryID, AsyncCallback<TotalResults> callback);
  void runQuery(String queryID, AsyncCallback<Void> callback);
  
  void getAnnotationsConfig(String indexId, AsyncCallback<String[][]> callback);
}
