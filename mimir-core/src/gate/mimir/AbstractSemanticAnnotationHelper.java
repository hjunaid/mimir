/*
 *  AbstractSemanticAnnotationHelper.java
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
 *  Valentin Tablan, 5 Aug 2009
 *
 *  $Id$
 */
package gate.mimir;

import gate.Document;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Simple abstract class that provides:
 * <ul>
 *   <li>an empty implementation for {@link #documentStart(Document)}</li>
 *   <li>an empty implementation for {@link #documentEnd()}</li>
 *   <li>an implementation for {@link #getMentions(String, Map, QueryEngine)},
 *   which simply calls {@link #getMentions(String, List, QueryEngine)} after
 *   creating the appropriate constraints.</li>
 * </ul>
 * Subclasses should provide a 6-argument constructor taking the following
 * parameters, in this order:
 * <ul>
 *   <li>String annotationType</li>
 *   <li>five String[] arguments for feature names of all kinds
 *     <ul>
 *     <li>nominal</li>
 *     <li>integer</li>
 *     <li>float</li>
 *     <li>text</li>
 *     <li>uri</li>
 *     </ul>
 * </ul>
 * If a particular helper does not support certain feature types it should
 * use the {@link #concatenateArrays} method to combine those features with
 * another kind that it can support.  For example, helpers that do not have
 * access to a full semantic repository may choose to treat URI features as
 * if they were simply text features with no semantics.
 */
public abstract class AbstractSemanticAnnotationHelper implements
                                                      SemanticAnnotationHelper {
	
	private static final long serialVersionUID = -5432862771431426914L;
	
  public AbstractSemanticAnnotationHelper(String annotationType,
          String[] nominalFeatureNames, String[] integerFeatureNames,
          String[] floatFeatureNames, String[] textFeatureNames,
          String[] uriFeatureNames) {
    this.annotationType = annotationType;
    this.nominalFeatureNames = 
        nominalFeatureNames != null ? nominalFeatureNames : new String[]{};
    this.integerFeatureNames = 
        integerFeatureNames != null? integerFeatureNames : new String[]{};
    this.floatFeatureNames = 
        floatFeatureNames != null ? floatFeatureNames : new String[]{};
    this.textFeatureNames = 
        textFeatureNames != null ? textFeatureNames : new String[]{};
    this.uriFeatureNames = 
        uriFeatureNames != null ? uriFeatureNames : new String[]{};
  }

  /**
   * The list of names for the nominal features.
   */
  protected String[] nominalFeatureNames;

  /**
   * The list of names for the numeric features.
   */
  protected String[] integerFeatureNames;

  /**
   * The list of names for the numeric features.
   */
  protected String[] floatFeatureNames;
  
  /**
   * The list of names for the text features.
   */
  protected String[] textFeatureNames;
	
  /**
   * The list of names for the URI features.
   */
  protected String[] uriFeatureNames;
  
  /**
   * The type of the annotations handled by this helper.
   */
  protected String annotationType;
	
  public String getAnnotationType() {
    return annotationType;
  }

  public String[] getNominalFeatureNames() {
    return nominalFeatureNames;
  }

  public String[] getIntegerFeatureNames() {
    return integerFeatureNames;
  }

  public String[] getFloatFeatureNames() {
    return floatFeatureNames;
  }

  public String[] getTextFeatureNames() {
    return textFeatureNames;
  }
  
  /**
   * @return the uriFeatureNames
   */
  public String[] getUriFeatureNames() {
    return uriFeatureNames;
  }
	
  /* (non-Javadoc)
   * @see gate.mimir.SemanticAnnotationHelper#documentEnd()
   */
  public void documentEnd() {}

  public void documentStart(Document document) { }


  /* (non-Javadoc)
   * @see gate.mimir.SemanticAnnotationHelper#getMentions(java.lang.String, java.util.Map, gate.mimir.search.QueryEngine)
   */
  public List<Mention> getMentions(String annotationType,
          Map<String, String> constraints, QueryEngine engine) {
    //convert the simple constraints to actual implementations.
    List<Constraint> predicates = new ArrayList<Constraint>(constraints.size());
    for(Entry<String, String> entry : constraints.entrySet()){
      predicates.add(new Constraint(ConstraintType.EQ, entry.getKey(), entry.getValue()));
    }
    return getMentions(annotationType, predicates, engine);
  }

  /**
   * Helper method to concatenate a number of arrays into one, for helpers
   * that don't support all the feature types and want to combine some of
   * them together.
   * @return null if all the supplied arrays are either null or empty,
   *        otherwise a single array containing the concatenation of
   *        all the supplied arrays in order.
   */
  protected static String[] concatenateArrays(String[]... arrays) {
    int totalLength = 0;
    for(String[] arr : arrays) {
      if(arr != null) totalLength += arr.length;
    }
    if(totalLength == 0) return null;
    String[] concat = new String[totalLength];
    int start = 0;
    for(String[] arr : arrays) {
      if(arr != null) {
        System.arraycopy(arr, 0, concat, start, arr.length);
        start += arr.length;
      }
    }
    return concat;
  }
}
