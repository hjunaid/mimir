/*
 *  TokenIndexBuilder.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Ian Roberts, 03 Mar 2009
 *
 *  $Id$
 */
package gate.mimir.index.mg4j;

import gate.Annotation;
import gate.FeatureMap;
import gate.mimir.DocumentMetadataHelper;
import gate.mimir.IndexConfig.TokenIndexerConfig;
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.mimir.index.mg4j.zipcollection.DocumentCollectionWriter;
import gate.mimir.index.mg4j.zipcollection.DocumentData;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;


/**
 * A index builder for token features.
 */
public class TokenIndexBuilder extends MimirIndexBuilder implements Runnable {

  private static Logger logger = Logger.getLogger(TokenIndexBuilder.class);

  /**
   * A constant (empty String array) used for filtering terms from indexing.
   * @see #calculateTermStringForAnnotation(Annotation, GATEDocument)
   * implementation.
   */
  private static final String[] DO_NOT_INDEX = new String[]{};
  
  public static final String TOKEN_INDEX_BASENAME = "token";
  
  protected static final CharsetEncoder UTF8_CHARSET_ENCODER = Charset.forName("UTF-8").newEncoder();
  
  protected static final CharsetDecoder UTF8_CHARSET_DECODER = Charset.forName("UTF-8").newDecoder();
  
  static {
    try {
      UTF8_CHARSET_ENCODER.replaceWith("[?]".getBytes("UTF-8"));
      UTF8_CHARSET_ENCODER.onMalformedInput(CodingErrorAction.REPLACE);
      UTF8_CHARSET_ENCODER.onUnmappableCharacter(CodingErrorAction.REPLACE);
    } catch(UnsupportedEncodingException e) {
      // this should never happen
      throw new RuntimeException("UTF-8 not supported");
    }
  }
  /**
   * A zip collection builder used to build a zip of the collection
   * if this has been requested.
   */
  protected DocumentCollectionWriter collectionWriter = null;
  
  /**
   * An array of helpers for creating document metadata. 
   */
  protected DocumentMetadataHelper[] docMetadataHelpers;
  
  /**
   * Stores the document URI for writing to the zip collection;
   */
  protected String documentURI;
  
  /**
   * Stores the document title for writing to the zip collection. 
   */
  protected String documentTitle;
  
  /**
   * Stores the document tokens for writing to the zip collection;
   */
  protected List<String> documentTokens;
  
  /**
   * Stores the document non-tokens for writing to the zip collection;
   */
  protected List<String> documentNonTokens;
  
  
  /**
   * GATE document factory used by the zip builder, and also to
   * translate field indexes to field names.
   */
  protected GATEDocumentFactory factory;
  
  /**
   * The (zero-based) index of the field we want to index.
   */
  protected int fieldToIndex;
  
  /**
   * The feature name corresponding to the field.
   */
  protected String featureName;
  
  
  public TokenIndexBuilder(BlockingQueue<GATEDocument> inputQueue,
          BlockingQueue<GATEDocument> outputQueue, Indexer indexer,
          GATEDocumentFactory factory, boolean zipCollection,
          String baseName,
          TokenIndexerConfig config) {
    super(inputQueue, outputQueue, indexer, baseName, 
        config.isDirectIndexEnabled());
    this.termProcessor = config.getTermProcessor();
    this.docMetadataHelpers = indexer.getIndexConfig().getDocMetadataHelpers();
    this.featureName = config.getFeatureName();
    for(fieldToIndex = 0; 
        fieldToIndex < indexer.getIndexConfig().getTokenIndexers().length; 
        fieldToIndex++){
      if(this.featureName.equals(indexer.getIndexConfig().
              getTokenIndexers()[fieldToIndex].getFeatureName())){
        break;
      }
    }
    if(this.fieldToIndex >= indexer.getIndexConfig().getTokenIndexers().length){
      throw new IllegalArgumentException(
              "Could not find the token feature name \"" + this.featureName +
              "\" in the index config!");
    }
    this.factory = factory;
    
    if(zipCollection) {
      logger.info("Creating zipped collection for field \"" + featureName + "\"");
      collectionWriter = new DocumentCollectionWriter(indexer.getIndexDir());
    }
    
  }


  /**
   * If zipping, inform the collection builder that a new document
   * is about to start.
   */
  protected void documentStarting(GATEDocument gateDocument) throws IndexException {
    if(collectionWriter != null) {
      documentURI = gateDocument.uri().toString();
      documentTitle = gateDocument.title().toString();
      documentTokens = new LinkedList<String>();
      documentNonTokens = new LinkedList<String>();
      if(docMetadataHelpers != null){
        for(DocumentMetadataHelper aHelper : docMetadataHelpers){
          aHelper.documentStart(gateDocument);
        }
      }

    }
    // set lastTokenIndex to -1 so we don't have to special-case the first
    // token in the document in calculateStartPosition
    tokenPosition = -1;
  }

  /**
   * If zipping, inform the collection builder that we finished
   * the current document.
   */
  protected void documentEnding(GATEDocument gateDocument) throws IndexException {
    if(collectionWriter != null) {
      DocumentData docData = new DocumentData(documentURI, 
              documentTitle, 
              documentTokens.toArray(new String[documentTokens.size()]),
              documentNonTokens.toArray(new String[documentNonTokens.size()])); 
      if(docMetadataHelpers != null){
        for(DocumentMetadataHelper aHelper : docMetadataHelpers){
          aHelper.documentEnd(gateDocument, docData);
        }
      }
      collectionWriter.writeDocument(docData);
      documentTokens = null;
      documentNonTokens = null;
    }
  }

  /**
   * Get the token annotations from this document, in increasing
   * order of offset.
   */
  protected Annotation[] getAnnotsToProcess(GATEDocument gateDocument) {
    return gateDocument.getTokenAnnots();
  }

  /**
   * This indexer always adds one posting per token, so the start
   * position for the next annotation is always one more than the
   * previous one.
   * 
   * @param ann
   * @param gateDocument
   */
  protected void calculateStartPositionForAnnotation(Annotation ann,
          GATEDocument gateDocument) {
    tokenPosition++;
  }

  /**
   * For a token annotation, the "string" we index is the feature value
   * corresponding to the name of the field to index.  As well as
   * calculating the string, this method writes an entry to the zip
   * collection builder if it exists.
   * 
   * @param ann
   * @param gateDocument
   */
  protected String[] calculateTermStringForAnnotation(Annotation ann,
          GATEDocument gateDocument) throws IndexException {
    FeatureMap tokenFeatures = ann.getFeatures();
    String value = (String)tokenFeatures.get(featureName);
    // make sure we get valid UTF-8 content
    // illegal strings will simply be rendered as "?"
    try {
      CharBuffer cb = CharBuffer.wrap(value);
      ByteBuffer bb = UTF8_CHARSET_ENCODER.encode(cb);
      cb = UTF8_CHARSET_DECODER.decode(bb);
      value  = cb.toString();
    } catch(CharacterCodingException e) {
      // this should not happen
      value = null;
      logger.error("Error while normalizing input", e);
    }
    currentTerm.replace(value == null ? "" : value);
    //save the *unprocessed* term to the collection, if required.
    if(collectionWriter != null) {
      documentTokens.add(currentTerm.toString());
      documentNonTokens.add(gateDocument.getNonTokens()[tokenPosition]);
    }
    if(termProcessor.processTerm(currentTerm)){
      //the processor has changed the term, and allowed us to index it
      return null;  
    }else{
      //the processor has filtered the term -> don't index it.
      return DO_NOT_INDEX;
    }
    
  }

  /**
   * Overridden to close the zip collection builder.
   */
  @Override
  public void flush() throws ConfigurationException, IOException {
    if(collectionWriter != null) {
      logger.info("Saving zipped collection");
      collectionWriter.close();
    }
    super.flush();
  }
}
