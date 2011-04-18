/*
 *  MentionsIndexBuilder.java
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
 *  Ian Roberts, 03 Mar 2009
 *
 *  $Id$
 */
package gate.mimir.index.mg4j;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.index.Indexer;
import gate.util.OffsetComparator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.apache.log4j.Logger;


/**
 * A index builder for mentions of semantic annotations.
 */
public class MentionsIndexBuilder extends MimirIndexBuilder implements Runnable {
  
  private static Logger logger = Logger.getLogger(MentionsIndexBuilder.class);
  
  public static final String MENTIONS_INDEX_BASENAME = "mentions";
  
  /**
   * Helpers for each semantic annotation type.
   */
  protected Map<String, SemanticAnnotationHelper> helpers;
  
  /**
   * An {@link OffsetComparator} used to sort the annotations by offset before 
   * indexing.
   */
  protected OffsetComparator offsetComparator;
  
  public MentionsIndexBuilder(BlockingQueue<GATEDocument> inputQueue,
          BlockingQueue<GATEDocument> outputQueue,
          Indexer indexer, String baseName, SemanticIndexerConfig config){
    super(inputQueue, outputQueue, indexer, baseName);
    //get the helpers
    helpers = new HashMap<String, SemanticAnnotationHelper>(
              config.getAnnotationTypes().length);
    for(int i = 0; i <  config.getAnnotationTypes().length; i++){
      helpers.put(config.getAnnotationTypes()[i], config.getHelpers()[i]);
    }
    offsetComparator = new OffsetComparator();
  }


  /**
   * Inform the helpers that a new document is about to start.
   */
  protected void documentStarting(GATEDocument gateDocument) {
    for(SemanticAnnotationHelper aHelper : helpers.values()){
      aHelper.documentStart(gateDocument.getDocument());
    }
  }

  /**
   * Inform the helpers that we finished the current document.
   */
  protected void documentEnding(GATEDocument gateDocument) {
    for(SemanticAnnotationHelper aHelper : helpers.values()){
      aHelper.documentEnd();
    }
  }
  
  /**
   * Get the semantic annotations from this document, in increasing order
   * of offset.  These are all the annotations of any type for which we
   * have a registered {@link SemanticAnnotationHelper}.
   */
  protected Annotation[] getAnnotsToProcess(GATEDocument gateDocument) {
    Document document = gateDocument.getDocument();
    Annotation[] semanticAnnots;
    AnnotationSet semAnnSet = 
      (indexConfig.getSemanticAnnotationSetName() == null ||
      indexConfig.getSemanticAnnotationSetName().length() == 0) ?
      document.getAnnotations() :
      document.getAnnotations(indexConfig.getSemanticAnnotationSetName());
    if(semAnnSet.size() > 0){
      AnnotationSet semAnns = null;
      synchronized(semAnnSet) {
        semAnns = semAnnSet.get(helpers.keySet());
      }
      semanticAnnots = semAnns.toArray(new Annotation[semAnns.size()]);
      Arrays.sort(semanticAnnots, offsetComparator);
    }else{
      semanticAnnots  = new Annotation[0];
    }
    return semanticAnnots;
  }
  

  /**
   * The starting position for a mention is the token following the rightmost
   * token which ends to the left of the semantic annotation.  This somewhat
   * convoluted definition means that if a semantic annotation overlaps with
   * a token then we "extend" the semantic annotation to include the whole of
   * the token.  The semantic annotation is treated by mimir as if it spanned
   * the whole of any tokens with which it overlaps.
   * @param ann
   * @param gateDocument
   */
  protected void calculateStartPositionForAnnotation(Annotation ann,
          GATEDocument gateDocument) {
    //calculate the term position for the current semantic annotation
    while(tokenPosition <  gateDocument.getTokenAnnots().length &&
          gateDocument.getTokenAnnots()[tokenPosition].
            getEndNode().getOffset().longValue() <= 
            ann.getStartNode().getOffset().longValue()){
      tokenPosition++;
    }
    //check if lastTokenposition is valid
    if(tokenPosition >= gateDocument.getTokenAnnots().length){
      //malfunction
      logger.error(
              "Semantic annotation [Type:" + ann.getType() +
              ", start: " + ann.getStartNode().getOffset().toString() +
              ", end: " + ann.getEndNode().getOffset().toString() +
              "] outside of the tokens area in document" +
              " URI: " + gateDocument.uri() +
              " Title: " + gateDocument.title());
    }
  }

  /**
   * For a semantic annotation, the "string" we index is the mention URI
   * returned by the semantic annotation helper corresponding to the
   * annotation's type.
   * @param ann
   * @param gateDocument
   */
  protected String[] calculateTermStringForAnnotation(Annotation ann,
          GATEDocument gateDocument) {
    //calculate the annotation length (as number of terms)
    SemanticAnnotationHelper helper = helpers.get(ann.getType());
    int length = 1;
    while(tokenPosition + length <  gateDocument.getTokenAnnots().length &&
            gateDocument.getTokenAnnots()[tokenPosition + length].
              getStartNode().getOffset().longValue() < 
              ann.getEndNode().getOffset().longValue()){
        length++;
      }
    //get the annotation URI
    return helper.getMentionUris(ann, length, indexer);
//    currentTerm.replace(helper.getMentionUri(ann, length, indexer));
  }
}
