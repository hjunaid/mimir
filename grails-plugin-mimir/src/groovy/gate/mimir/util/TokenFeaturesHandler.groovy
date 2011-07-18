/*
 *  TokenFeaturesHandler.groovy
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
package gate.mimir.util

import gate.mimir.IndexConfig.TokenIndexerConfig
import it.unimi.dsi.mg4j.index.TermProcessor
import it.unimi.dsi.mg4j.index.DowncaseTermProcessor
import it.unimi.dsi.mg4j.index.NullTermProcessor

/**
 * Class used as closure delegate to handle the mimir.rpcIndexer.tokenFeatures
 * closure in mimir configuration.  Method calls are interpreted as the names
 * of features to index.  If a TermProcessor is passed as an argument to a
 * method call, that processor is used for the given feature.  If no processor
 * is passed in, the first feature is given a DowncaseTermProcessor and
 * subsequent features are unprocessed.
 */
class TokenFeaturesHandler {

  List indexerConfigs = []

  def invokeMethod(String name, args) {
    def firstFeature = indexerConfigs.isEmpty()

    TermProcessor processor = null
    if(args) {
      if(args[0] instanceof TermProcessor) {
        processor = (TermProcessor)args[0]
      }
      else {
        throw new IllegalArgumentException("${args[0]} is not a TermProcessor")
      }
    }
    else {
      if(firstFeature) {
        processor = DowncaseTermProcessor.getInstance()
      }
      else {
        processor = NullTermProcessor.getInstance()
      }
    }

    indexerConfigs << new TokenIndexerConfig(name, processor)
  }
}
