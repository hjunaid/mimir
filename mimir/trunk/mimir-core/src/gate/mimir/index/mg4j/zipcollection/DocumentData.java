/*
 *  DocumentData.java
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
 *  Valentin Tablan, 15 Apr 2009
 *
 *  $Id$
 */
package gate.mimir.index.mg4j.zipcollection;

import java.io.Serializable;
import java.util.HashMap;

/**
 * A container for the document data that gets stored in the zip collection.
 */
public class DocumentData implements Serializable{
  
  /**
   * Constructs a new DocumentData object.
   * @param documentURI the URI of the document.
   * @param documentTitle the title of the document.
   * @param tokens the document tokens.
   * @param nonTokens the document non-tokens (i.e. spaces).
   */
  public DocumentData(String documentURI, String documentTitle,
          String[] tokens, String[] nonTokens) {
    this.documentURI = documentURI;
    this.documentTitle = documentTitle;
    this.tokens = tokens;
    this.nonTokens = nonTokens;
  }

  /**
   * Adds a new arbitrary metadata field.
   * @param fieldName the name for the new field.
   * @param fieldValue the value for the new field. The value provided here must
   * be {@link Serializable}. The map of metadata fields is stored separately 
   * for each individual document; care should be taken to limit the size of
   * the object graph that is serialised! 
   */
  public void putMetadataField(String fieldName, Serializable fieldValue){
    if(metadata == null){
      metadata = new HashMap<String, Serializable>();
    }
    metadata.put(fieldName, fieldValue);
  }
  
  /**
   * Gets the value of a metadata field.
   * @param fieldName the name of field to be returned. 
   * @return the value previously stored in the metadata map for this field.
   */
  public Serializable getMetadataField(String fieldName){
    return metadata == null ? null : metadata.get(fieldName);
  }
  
  /**
   * @return the tokens
   */
  public String[] getTokens() {
    return tokens;
  }

  /**
   * @return the nonTokens
   */
  public String[] getNonTokens() {
    return nonTokens;
  }

  /**
   * @return the documentURI
   */
  public String getDocumentURI() {
    return documentURI;
  }

  /**
   * @return the documentTitle
   */
  public String getDocumentTitle() {
    return documentTitle;
  }


  /**
   * Serialisation UID
   */
  private static final long serialVersionUID = 7079350474333976576L;
  
  /**
   * The tokens of the document.
   */
  protected String[] tokens;
  
  /**
   * The non-tokens (i.e. spaces) of the document.
   */
  protected String[] nonTokens;
  
  /**
   * The Document URI
   */
  protected String documentURI;
  
  /**
   * The Document title.
   */
  protected String documentTitle;
  
  /**
   * A {@link HashMap} of arbitrary metadata (all fields must be 
   * {@link Serializable}).
   */
  protected HashMap<String, Serializable> metadata;
  
  
}
