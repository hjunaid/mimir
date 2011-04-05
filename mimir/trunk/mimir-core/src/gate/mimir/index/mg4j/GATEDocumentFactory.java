/*
 *  Copyright (c) 1998-2009, The University of Sheffield.
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licensed under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *  
 *  Valentin Tablan, 15 Apr 2009
 *
 *  $Id$
 */
package gate.mimir.index.mg4j;

import gate.mimir.IndexConfig;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.mg4j.document.Document;
import it.unimi.dsi.mg4j.document.DocumentFactory;

import java.io.IOException;
import java.io.InputStream;


/**
 * An MG4J {@link DocumentFactory} for GATE documents, configured according 
 * to the current indexing requirements.
 */
public class GATEDocumentFactory implements DocumentFactory{

  /**
   * 
   */
  private static final long serialVersionUID = 6070650764387229146L;
  
  /**
   * The index configuration.
   */
  private IndexConfig indexConfig;
  
  public GATEDocumentFactory(IndexConfig indexConfig){
    this.indexConfig = indexConfig;
  }
  
  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#copy()
   */
  public DocumentFactory copy() {
    throw new UnsupportedOperationException(getClass().getName() + 
            " does not support copying!");
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#fieldIndex(java.lang.String)
   */
  public int fieldIndex(String fieldName) {
    for(int i = 0; i < indexConfig.getTokenIndexers().length; i++){
      if(indexConfig.getTokenIndexers()[i].getFeatureName().equals(fieldName)){
        return i;
      }
    }
    return -1;
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#fieldName(int)
   */
  public String fieldName(int field) {
    return indexConfig.getTokenIndexers()[field].getFeatureName();
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#fieldType(int)
   */
  public FieldType fieldType(int field) {
    // all GATE fields are TEXT
    return FieldType.TEXT;
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#getDocument(java.io.InputStream, it.unimi.dsi.fastutil.objects.Reference2ObjectMap)
   */
  public Document getDocument(InputStream rawContent,
          Reference2ObjectMap<Enum<?>, Object> metadata) throws IOException {
    //we do not support reading of documents from streams
    throw new UnsupportedOperationException(getClass().getName() + 
            " does not support reading from streams!");
  }

  /* (non-Javadoc)
   * @see it.unimi.dsi.mg4j.document.DocumentFactory#numberOfFields()
   */
  public int numberOfFields() {
    return indexConfig.getTokenIndexers().length;
  }
}