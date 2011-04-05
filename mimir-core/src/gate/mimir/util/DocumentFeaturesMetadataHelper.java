/*
 *  DocumentMetadataHelper.java
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
package gate.mimir.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import gate.mimir.DocumentMetadataHelper;
import gate.mimir.index.mg4j.GATEDocument;
import gate.mimir.index.mg4j.zipcollection.DocumentData;
import gate.mimir.search.QueryEngine;


/**
 * A simple {@link DocumentMetadataHelper} that copies the values of some GATE 
 * document features as metadata fields in the index. Note that the values of 
 * the specified features must be {@link Serializable}; values that are not will
 * not be saved in the index.
 * 
 * The values thus saved can be retrieved at search time by calling 
 * {@link QueryEngine#getDocumentMetadataField(int, String)}.
 */
public class DocumentFeaturesMetadataHelper implements DocumentMetadataHelper {
  
  /**
   * A map storing the correspondence between the  GATE document feature name 
   * and the metadata field name in the Mimir index. 
   */
  protected Map<String, String> featureNameToFieldName;
  
  private static Logger logger = Logger.getLogger(
          DocumentFeaturesMetadataHelper.class);
  
  /**
   * Creates a new DocumentFeaturesMetadataHelper.
   * @param featureNameToFieldName a map storing the correspondence between the 
   * GATE document feature name and the metadata field name; keys are names of
   * document features; values are names of metadata fields. 
   */
  public DocumentFeaturesMetadataHelper(
          Map<String, String> featureNameToFieldName) {
    this.featureNameToFieldName = featureNameToFieldName;
  }
  
  /**
   * Creates a new DocumentFeaturesMetadataHelper.
   * @param featureNames an array of feature names. For each indexed document,
   * the values for the features specified here are obtained and stored in the
   *  index, as document metadata fields with the same names as the GATE 
   *  document features. If you need the names of the Mimir document metadata
   *  fields to be different from the GATE document features, then you should 
   *  use the {@link #DocumentFeaturesMetadataHelper(Map)} variant. 
   */  
  public DocumentFeaturesMetadataHelper(String... featureNames) {
    this.featureNameToFieldName = new HashMap<String, String>();
    for(String f : featureNames) {
      featureNameToFieldName.put(f, f);
    }
  }

  @Override
  public void documentStart(GATEDocument document) {
    // do nothing
  }

  @Override
  public void documentEnd(GATEDocument document, DocumentData documentData) {
    for(Map.Entry<String, String> mapping : featureNameToFieldName.entrySet()) {
      Object value = document.getDocument().getFeatures().get(mapping.getKey());
      if(value instanceof Serializable) {
        documentData.putMetadataField(mapping.getValue(), (Serializable)value);
      } else {
        logger.warn("Value for document feature \"" + mapping.getKey() + 
                "\" on document with title \"" + 
                (document.title() == null ? "<null>" : document.title()) +
                "\", and URI: \"" +
                (document.uri() == null ? "<null>" : document.uri()) +
                "\" is not serializable. Document metadata filed NOT saved.");
      }
    }
  }
}
