/*
 *  MeasurementAnnotationHelper.java
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
 *  Valentin Tablan, 05 Aug 2009
 *  
 *  $Id$
 */
package gate.mimir.measurements;

import gate.Gate;
import gate.creole.measurements.Measurement;
import gate.creole.measurements.MeasurementsParser;
import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;
import gate.mimir.util.DelegatingSemanticAnnotationHelper;
import gate.util.GateRuntimeException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link SemanticAnnotationHelper} that supports querying of Measurement
 * annotations produced by the GATE <code>Tagger_Measurements</code> plugin.
 * The annotations are indexed by their normalized value and unit but can
 * be queried using a "virtual" feature named <code>spec</code>, expressing
 * a measurement specification in terms such as "1 to 3 feet".  The spec
 * is mapped into a range query over the appropriate normalized values, so
 * can match annotations that express the same measurement in different
 * terms (e.g. "400mm").
 */
public class MeasurementAnnotationHelper extends
                                        DelegatingSemanticAnnotationHelper {

  private static final long serialVersionUID = 1875846689536452288L;

  protected static final String ANNOTATION_TYPE = "Measurement";

  protected static final String TYPE_FEATURE = "type";

  protected static final String TYPE_SCALAR = "scalar";

  protected static final String TYPE_INTERVAL = "interval";

  protected static final String DIMENSION_FEATURE = "dimension";

  protected static final String NORM_UNIT_FEATURE = "normalisedUnit";

  protected static final String NORM_VALUE_FEATURE = "normalisedValue";

  protected static final String NORM_MIN_VALUE_FEATURE = "normalisedMinValue";

  protected static final String NORM_MAX_VALUE_FEATURE = "normalisedMaxValue";

  protected static final String SPEC_FAKE_FEATURE = "spec";
  
  public static final String DELEGATE_HELPER_TYPE_KEY = "delegateHelperType";
  
  public static final String UNITS_FILE_LOCATION_KEY = "unitsFile";
  
  public static final String COMMON_WORDS_LOCATION_KEY = "commonWords";
  
  public static final String ENCODING_KEY = "encoding";
  
  public static final String LOCALE_KEY = "locale";
  
  protected String unitsFileLocation = "resources/units.dat";
  
  protected String commonWordsLocation = "resources/common_words.txt";
  
  protected String locale = "en_GB";
  
  protected String encoding = "UTF-8";
  
  /**
   * The measurements parser used to parse the virtual "spec" feature.
   */
  protected transient MeasurementsParser measurementsParser;
  
  /**
   * Has this helper's measurement parser been initialized yet?
   */
  protected transient boolean inited = false;
  
  /**
   * Create a MeasurementAnnotationHelper delegating to an underlying helper
   * of the class specified by the {@value #DELEGATE_HELPER_TYPE_KEY} entry
   * in the settings map.
   * 
   * This helper relies on a {@link MeasurementsParser} (from the GATE
   * <code>Tagger_Measurements</code> plugin) to parse the virtual "spec"
   * feature at search time.  This parser is configured based on other
   * entries in the settings map:
   * <ul>
   *   <li>{@value #UNITS_FILE_LOCATION_KEY} - the location of the
   *   <code>units.dat</code> file.</li>
   *   <li>{@value #COMMON_WORDS_LOCATION_KEY} - the location of the
   *   common words list file.</li>
   *   <li>{@value #LOCALE_KEY} - the locale used to parse units
   *   (default en_GB).</li> 
   *   <li>{@value #ENCODING_KEY} - the encoding of the data files
   *   (default UTF-8).</li>
   * </ul>
   * The file locations can be specified as either absolute URLs or
   * as paths relative to the measurements plugin directory.  If not
   * specified, default files supplied with the plugin are used.
   */
  public MeasurementAnnotationHelper(
          Map<String, Object> settings) {
    super(ANNOTATION_TYPE,
            new String[] {SPEC_FAKE_FEATURE, TYPE_FEATURE,
                DIMENSION_FEATURE, NORM_UNIT_FEATURE},
            null,
            new String[] {NORM_VALUE_FEATURE, NORM_MIN_VALUE_FEATURE,
                NORM_MAX_VALUE_FEATURE},
            null,
            null,
            createHelper(
                  ((Class<? extends SemanticAnnotationHelper>)settings.get(DELEGATE_HELPER_TYPE_KEY)),
                  ANNOTATION_TYPE, 
                  new String[] {TYPE_FEATURE, DIMENSION_FEATURE,
                        NORM_UNIT_FEATURE},
                  null,
                  new String[] {NORM_VALUE_FEATURE, NORM_MIN_VALUE_FEATURE,
                      NORM_MAX_VALUE_FEATURE},
                  null,
                  null));
    if(settings.containsKey(UNITS_FILE_LOCATION_KEY)) {
      unitsFileLocation = getString(settings, UNITS_FILE_LOCATION_KEY);
    }
    if(settings.containsKey(COMMON_WORDS_LOCATION_KEY)) {
      commonWordsLocation = getString(settings, COMMON_WORDS_LOCATION_KEY);
    }
    if(settings.containsKey(LOCALE_KEY)) {
      locale = getString(settings, LOCALE_KEY);
    }
    if(settings.containsKey(ENCODING_KEY)) {
      encoding = getString(settings, ENCODING_KEY);
    }
  }

  /**
   * Given the class value for a {@link SemanticAnnotationHelper} 
   * implementation, this method locates the constructor that takes one String 
   * value and 5 String[] values as parameters, and uses it to instantiate one
   * helper object, which is then returned.
   * @param helperClass the class of the requested helper.
   * @return
   */
  static protected SemanticAnnotationHelper createHelper(
          Class<? extends SemanticAnnotationHelper> helperClass,
          String annotationType, String[] nominalFeatureNames, 
          String[] integerFeatureNames, String[] floatFeatureNames, 
          String[] textFeatureNames, String[] uriFeatureNames) {
    
    // locate the constructor
    Constructor<? extends SemanticAnnotationHelper> constructor = null;
    try {
      constructor = helperClass.getConstructor(String.class, String[].class, 
            String[].class, String[].class, String[].class, String[].class);
    } catch(NoSuchMethodException e) {
      throw new GateRuntimeException("Class " + helperClass.getName() + 
          " does not have the standard 6-argument SemanticAnnotationHelper " +
          "constructor.", e);
    }
    // create the new instance
    try {
      return constructor.newInstance(annotationType, nominalFeatureNames,
              integerFeatureNames, floatFeatureNames, textFeatureNames,
              uriFeatureNames);
    } catch(Exception e) {
      throw new GateRuntimeException("Could not create instance of "
              + helperClass.getName(), e);
    }
  }
  
  protected void init() {
    if(inited) return;
    URL commonUrl = resolveUrl(commonWordsLocation);
    URL unitsUrl = resolveUrl(unitsFileLocation);
    try {
      measurementsParser = new MeasurementsParser(encoding, locale,
              unitsUrl, commonUrl);
    } catch(IOException e) {
      throw new GateRuntimeException(
              "Could not create measurements parser for MeasurementAnnotationHelper", e);
    }
    inited = true;
  }
  
  protected URL resolveUrl(String location) {
    try {
      return new URL(Gate.getCreoleRegister().get(
              MeasurementPluginResource.class.getName()).getXmlFileUrl(), location);
    } catch(MalformedURLException e) {
      throw new GateRuntimeException("Could not resolve " + location + " as a URL", e);
    }
  }
  
  protected List<Constraint>[] convertSpecConstraint(Constraint specConstraint){
    if(!(specConstraint.getValue() instanceof String)) { throw new IllegalArgumentException(
            "The custom feature " + SPEC_FAKE_FEATURE
                    + " only accepts String values!"); }
    if(specConstraint.getPredicate() != ConstraintType.EQ) { throw new IllegalArgumentException(
            "The custom feature " + SPEC_FAKE_FEATURE
                    + " only accepts 'equals' constraints!"); }
    String specString = (String)specConstraint.getValue();
    // a spec string is one of:
    // "<number> <unit>", or
    // "<number> to <number> <unit>"
    String[] elements = specString.split("\\s+");
    if(elements.length < 2){
      throw new IllegalArgumentException(
              "Invalid measurement specification; the valid syntax is:\n" +
              "number unit, or" +
              "number to number unit");
    }
    if(elements[1].equalsIgnoreCase("to")) {
      //interval
      if(elements.length < 4){
        throw new IllegalArgumentException(
                "Invalid measurement specification; the valid syntax is:\n" +
                "number unit, or\n" +
                "number to number unit");
      }
      double minValue;
      try{
        minValue = Double.parseDouble(elements[0]);
      }catch(NumberFormatException e) {
        throw new IllegalArgumentException(elements[0] + 
                " is not a valid number", e);
      }
      double maxValue;
      try{
        maxValue = Double.parseDouble(elements[2]);
      }catch(NumberFormatException e) {
        throw new IllegalArgumentException(elements[2] + 
                " is not a valid number", e);
      }
      StringBuilder unitBuilder = new StringBuilder(elements[3]);
      for(int i = 4; i < elements.length; i++){
        unitBuilder.append(' ');
        unitBuilder.append(elements[i]);
      }
      String unit = unitBuilder.toString();
      //parse the two values
      Measurement minV = measurementsParser.parse(minValue, unit);
      if(minV == null){
        throw new IllegalArgumentException("Don't understand measurement "+
                minValue + " " + unit + ". Please rephrase."); 
      }
      Measurement maxV = measurementsParser.parse(maxValue, unit);
      if(maxV == null){
        throw new IllegalArgumentException("Don't understand measurement "+
                minValue + " " + unit + ". Please rephrase."); 
      }
      //finally, rephrase the query in terms that the standard helper 
      //understands.

      //we need to match simple measurements that fall inside the interval
      List<Constraint> alternative1 = new ArrayList<Constraint>();
//      alternative1.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_SCALAR));
      alternative1.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              maxV.getNormalizedUnit()));
      alternative1.add(new Constraint(ConstraintType.GE, NORM_VALUE_FEATURE, 
              minV.getNormalizedValue()));
      alternative1.add(new Constraint(ConstraintType.LE, NORM_VALUE_FEATURE, 
              maxV.getNormalizedValue()));
      //we need to match interval measurements whose start falls inside the interval
      List<Constraint> alternative2 = new ArrayList<Constraint>();
//      alternative2.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_INTERVAL));
      alternative2.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              maxV.getNormalizedUnit()));
      alternative2.add(new Constraint(ConstraintType.GE, NORM_MIN_VALUE_FEATURE, 
              minV.getNormalizedValue()));
      alternative2.add(new Constraint(ConstraintType.LE, NORM_MIN_VALUE_FEATURE, 
              maxV.getNormalizedValue()));
      //we need to match interval measurements whose end falls inside the interval
      List<Constraint> alternative3 = new ArrayList<Constraint>();
//      alternative3.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_INTERVAL));
      alternative3.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              maxV.getNormalizedUnit()));
      alternative3.add(new Constraint(ConstraintType.GE, NORM_MAX_VALUE_FEATURE, 
              minV.getNormalizedValue()));
      alternative3.add(new Constraint(ConstraintType.LE, NORM_MAX_VALUE_FEATURE, 
              maxV.getNormalizedValue()));
      return new List[]{alternative1, alternative2, alternative3};
    }else{
      //simple measurement
      double value;
      try{
        value = Double.parseDouble(elements[0]);
      }catch(NumberFormatException e) {
        throw new IllegalArgumentException(elements[0] + 
                " is not a valid number", e);
      }
      StringBuilder unitBuilder = new StringBuilder(elements[1]);
      for(int i = 2; i < elements.length; i++){
        unitBuilder.append(' ');
        unitBuilder.append(elements[i]);
      }
      String unit = unitBuilder.toString();
      //parse the single value
      Measurement valV = measurementsParser.parse(value, unit);
      if(valV == null){
        throw new IllegalArgumentException("Don't understand measurement "+
                value + " " + unit + ". Please rephrase."); 
      }
      
      //we need to match simple measurements that are equal to the value (with 
      //the double precision taken into account)      
      List<Constraint> alternative1 = new ArrayList<Constraint>();
//      alternative1.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_SCALAR));
      alternative1.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              valV.getNormalizedUnit()));
      alternative1.add(new Constraint(ConstraintType.GE, NORM_VALUE_FEATURE, 
              valV.getNormalizedValue() - Double.MIN_VALUE));
      alternative1.add(new Constraint(ConstraintType.LE, NORM_VALUE_FEATURE, 
              valV.getNormalizedValue() + Double.MIN_VALUE));
      //we need to match interval measurements which contain the value
      List<Constraint> alternative2 = new ArrayList<Constraint>();
//      alternative2.add(new Constraint(ConstraintType.EQ, TYPE_FEATURE, 
//              TYPE_INTERVAL));
      alternative2.add(new Constraint(ConstraintType.EQ, NORM_UNIT_FEATURE, 
              valV.getNormalizedUnit()));
      alternative2.add(new Constraint(ConstraintType.LE, NORM_MIN_VALUE_FEATURE, 
              valV.getNormalizedValue()));
      alternative2.add(new Constraint(ConstraintType.GE, NORM_MAX_VALUE_FEATURE, 
              valV.getNormalizedValue()));
      return new List[]{alternative1, alternative2};
    }
  }

  /**
   * Query for Measurement annotations, handling the "spec" feature by
   * converting it to the equivalent constraints on the indexed features.
   */
  @Override
  public List<Mention> getMentions(String annotationType,
          List<Constraint> constraints, QueryEngine engine) {
    if(!inited) init();
    List<Constraint> passThroughConstraints = new ArrayList<Constraint>(
            constraints.size());
    Constraint specConstraint = null;
    //filter custom constraints
    for(Constraint aConstraint : constraints) {
      if(aConstraint.getFeatureName().equals(SPEC_FAKE_FEATURE)) {
        if(specConstraint != null){
          throw new IllegalArgumentException("Only one constraint of type ." +
                  SPEC_FAKE_FEATURE + " is permitted!"); 
        }else{
          specConstraint = aConstraint;
        }
      } else {
        passThroughConstraints.add(aConstraint);
      }
    }
    if(specConstraint == null){
      //no custom constraints
      return super.getMentions(annotationType, passThroughConstraints, engine);
    }else{
      //process the custom constraints
      List<Constraint>[] alternatives = convertSpecConstraint(specConstraint);
      //use Set to avoid duplicates
      Set<Mention> results = new HashSet<Mention>();
      for(List<Constraint> anAlternative : alternatives){
        List<Constraint> superConstraints = new ArrayList<Constraint>(
                passThroughConstraints.size() + anAlternative.size());
        superConstraints.addAll(passThroughConstraints);
        superConstraints.addAll(anAlternative);
        results.addAll(super.getMentions(annotationType, superConstraints, 
                engine));
      }
      return new ArrayList<Mention>(results);
    }
  }
}
