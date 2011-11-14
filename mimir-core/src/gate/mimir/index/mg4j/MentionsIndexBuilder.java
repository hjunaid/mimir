/*
 *  MentionsIndexBuilder.java
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
import gate.AnnotationSet;
import gate.Document;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.mimir.index.Mention;
import gate.util.OffsetComparator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
  protected Map<String, SemanticAnnotationHelper> annotationHelpers;
  
  protected List<SemanticAnnotationHelper> documentHelpers;
  
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
    annotationHelpers = new HashMap<String, SemanticAnnotationHelper>(
              config.getAnnotationTypes().length);
    documentHelpers = new LinkedList<SemanticAnnotationHelper>();
    for(int i = 0; i <  config.getAnnotationTypes().length; i++){
      SemanticAnnotationHelper theHelper = config.getHelpers()[i];
      if(theHelper.getMode() == SemanticAnnotationHelper.Mode.DOCUMENT) {
        documentHelpers.add(theHelper);
      } else {
        annotationHelpers.put(config.getAnnotationTypes()[i], theHelper);  
      }
      
    }
    offsetComparator = new OffsetComparator();
  }


  /**
   * Inform the helpers that a new document is about to start.
   */
  protected void documentStarting(GATEDocument gateDocument) {
    for(SemanticAnnotationHelper aHelper : annotationHelpers.values()){
      aHelper.documentStart(gateDocument.getDocument());
    }
    for(SemanticAnnotationHelper aHelper : documentHelpers){
      aHelper.documentStart(gateDocument.getDocument());
    }    
  }

  /**
   * Inform the helpers that we finished the current document.
   * @throws IndexException 
   */
  protected void documentEnding(GATEDocument gateDocument) throws IndexException {
    for(SemanticAnnotationHelper aHelper : annotationHelpers.values()){
      aHelper.documentEnd();
    }
    
    if(!documentHelpers.isEmpty()) {
      processAnnotation(null, gateDocument);
    }
    
    for(SemanticAnnotationHelper aHelper : documentHelpers){     
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
        semAnns = semAnnSet.get(annotationHelpers.keySet());
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
    if(ann == null) {
      // we're supposed index the document metadata
      tokenPosition = 0;
    } else {
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
    if(ann == null) {
      // obtain the URIs to be indexed for the *document* metadata
      List<String> terms = new LinkedList<String>();
      for(SemanticAnnotationHelper aHelper : documentHelpers) {
        String[] someTerms = aHelper.getMentionUris(null, Mention.NO_LENGTH, indexer);
        if(someTerms != null) {
          for(String aTerm : someTerms) {
            terms.add(aTerm);
          }
        }
      }
      return terms.toArray(new String[terms.size()]);
    } else {
      //calculate the annotation length (as number of terms)
      SemanticAnnotationHelper helper = annotationHelpers.get(ann.getType());
      int length = 1;
      while(tokenPosition + length <  gateDocument.getTokenAnnots().length &&
              gateDocument.getTokenAnnots()[tokenPosition + length].
                getStartNode().getOffset().longValue() < 
                ann.getEndNode().getOffset().longValue()){
          length++;
        }
      //get the annotation URI
      return helper.getMentionUris(ann, length, indexer);
    }
  }
}
