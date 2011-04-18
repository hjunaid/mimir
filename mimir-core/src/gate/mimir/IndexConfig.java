/*
 *  IndexConfig.java
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
 * Valentin Tablan, 18 Feb 2009
 *
 *  $Id$
 */
package gate.mimir;

import gate.Gate;
import gate.mimir.index.IndexException;
import it.unimi.dsi.mg4j.index.NullTermProcessor;
import it.unimi.dsi.mg4j.index.TermProcessor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import com.thoughtworks.xstream.io.xml.QNameMap;
import com.thoughtworks.xstream.io.xml.StaxDriver;
import com.thoughtworks.xstream.io.xml.StaxReader;

/**
 * Interface for indexer configurations.
 */
public class IndexConfig implements Serializable {
  /**
   * Object storing the configuration for a Token indexer.
   */
  public static class TokenIndexerConfig implements Serializable {
    /**
     * Serialisation ID.
     */
    private static final long serialVersionUID = 1868954146230945676L;

    /**
     * The name of the feature on Token annotations that need to be indexed.
     */
    private String featureName;

    /**
     * The term processor to be used for this indexer.
     */
    private TermProcessor termProcessor;

    /**
     * Creates a new TokenIndexerConfig.
     * 
     * @param featureName
     *          the name of the feature (on Token annotations) that needs to be
     *          indexed.
     * @param termProcessor
     *          The {@link TermProcessor} to be used by this indexer. If
     *          <code>null</code> is given, then a {@link NullTermProcessor} is
     *          used.
     */
    public TokenIndexerConfig(String featureName, TermProcessor termProcessor) {
      this.featureName = featureName;
      this.termProcessor =
              termProcessor == null
                      ? NullTermProcessor.getInstance()
                      : termProcessor;
    }

    /**
     * Obtains the name of the feature (on Token annotations) that needs to be
     * indexed by this token indexer.
     * 
     * @return the featureName
     */
    public String getFeatureName() {
      return featureName;
    }

    /**
     * Obtains the instance of {@link TermProcessor} that needs to be used by
     * this token indexer.
     * 
     * @return the termProcessor
     */
    public TermProcessor getTermProcessor() {
      return termProcessor;
    }
  }

  /**
   * Object storing the configuration for a semantic annotation indexer.
   */
  public static class SemanticIndexerConfig implements Serializable {
    /**
     * Serialisation ID.
     */
    private static final long serialVersionUID = -8714423642897958538L;

    /**
     * The types of the annotation that need to be indexed by this indexer.
     */
    private String[] annotationTypes;

    /**
     * The {@link SemanticAnnotationHelper}s used by this indexer.
     */
    private SemanticAnnotationHelper[] helpers;

    /**
     * Creates a SemanticIndexerConfig. The two arrays given as parameters must
     * have the same length, the helper at a given position in the helpers array
     * is used to index the annotations with the type at the same position in
     * the annotationTypes array.
     * 
     * @param annotationTypes
     *          the types of the annotations that need to be indexed by this
     *          indexer.
     * @param helper
     *          the {@link SemanticAnnotationHelper}s used by this indexer.
     */
    public SemanticIndexerConfig(String[] annotationTypes,
            SemanticAnnotationHelper[] helpers) {
      this.annotationTypes = annotationTypes;
      this.helpers = helpers;
    }

    /**
     * Gets the types of annotations indexed by this indexer.
     * 
     * @return the annotationTypes
     */
    public String[] getAnnotationTypes() {
      return annotationTypes;
    }

    /**
     * Gets the {@link SemanticAnnotationHelper}s used to index annotations.
     * 
     * @return the helpers
     */
    public SemanticAnnotationHelper[] getHelpers() {
      return helpers;
    }
  }

  /**
   * 
   */
  private static final long serialVersionUID = -8127630936829037489L;

  /**
   * The default feature name for obtaining document URIs (provided as features
   * on documents).
   */
  public static final String DOCUMENT_URI_FEATURE_DEFAULT_NAME =
          "gate.mimir.uri";
  
  /**
   * A Map storing values that need to be passed between the various pluggable
   * components used by this index (e.g. ORDI-based annotation helpers may
   * pass references to the ORDI Factory between each other). 
   */
  private transient Map<String, Object> context;
  

  
  /**
   * Gets the map used for passing values between the various pluggable elements
   * in this index (such as annotation helpers). The returned map is live, 
   * meaning that all changes made to it are available to all other clients 
   * requesting it.    
   * @return a {@link Map}, with {@link String} keys and arbitrary values. 
   */
  public Map<String, Object> getContext() {
    // lazy creation
    if(context == null) {
      context = Collections.synchronizedMap(new HashMap<String, Object>());
    }
    return context;
  }

  /**
   * Constructs an index configuration object.
   * 
   * @param indexDirectory
   *          indexDirectory the top level directory to be used for storing the
   *          index.
   * @param tokenAnnotationSetName
   *          the name for the annotation set where token annotations can be
   *          found. Use <tt>null</tt> for the default annotation set.
   * @param tokenAnnotationType
   *          the type of annotations used as tokens.
   * @param semanticAnnotationSetName
   *          the name for the annotation set where semantic annotations should
   *          be collected from.
   * @param tokenIndexers
   *          an array of {@link TokenIndexerConfig} values, describing the
   *          configuration for the indexing of each token feature.
   * @param semanticIndexers
   *          an array of {@link SemanticIndexerConfig} values, describing the
   *          the configuration for indexing semantic annotations.
   */
  public IndexConfig(File indexDirectory, String tokenAnnotationSetName,
          String tokenAnnotationType, String semanticAnnotationSetName,
          TokenIndexerConfig[] tokenIndexers,
          SemanticIndexerConfig[] semanticIndexers,
          DocumentMetadataHelper[] docMetadataHelpers,
          DocumentRenderer documentRenderer) {
    
    this.indexDirectory = indexDirectory;
    this.tokenAnnotationSetName = tokenAnnotationSetName;
    this.tokenAnnotationType = tokenAnnotationType;
    this.tokenIndexers = tokenIndexers;
    this.semanticAnnotationSetName = semanticAnnotationSetName;
    this.semanticIndexers = semanticIndexers;
    this.docMetadataHelpers = docMetadataHelpers;
    this.documentRenderer = documentRenderer;
    this.options = new HashMap<String, String>();
  }

  /**
   * Gets the top level directory of an index.
   * 
   * @return a {@link File} object.
   */
  public File getIndexDirectory() {
    return indexDirectory;
  }

  /**
   * Gets the annotation type to be used for obtaining tokens.
   * 
   * @return an {@link String} object.
   */
  public String getTokenAnnotationType() {
    return tokenAnnotationType;
  }

  /**
   * Gets the name for the annotation set where token annotations can be found.
   * 
   * @return the tokenAnnotationSet
   */
  public String getTokenAnnotationSetName() {
    return tokenAnnotationSetName;
  }

  /**
   * Gets the configuration for all the token indexers used.
   * 
   * @return an array of {@link TokenIndexerConfig} values.
   */
  public TokenIndexerConfig[] getTokenIndexers() {
    return tokenIndexers;
  }

  /**
   * Gets the name of the annotation set containing semantic annotations.
   * 
   * @return the semanticAnnotationSetName
   */
  public String getSemanticAnnotationSetName() {
    return semanticAnnotationSetName;
  }

  /**
   * Gets the configuration for all the semantic annotation indexers used.
   * 
   * @return an array of {@link SemanticIndexerConfig} values.
   */
  public SemanticIndexerConfig[] getSemanticIndexers() {
    return semanticIndexers;
  }

  /**
   * Gets the options map - a Map with arbitrary configuration options, which 
   * is made available to all sub-elements of this index (e.g. the various 
   * annotation helpers).  
   */
  public Map<String, String> getOptions() {
    return options;
  }

  /**
   * Gets the renderer to be used for displaying documents and hits.
   * 
   * @return the documentRenderer
   */
  public DocumentRenderer getDocumentRenderer() {
    return documentRenderer;
  }

  /**
   * Sets the renderer to be used for displaying documents and hits.
   * 
   * @param documentRenderer
   *          the documentRenderer to set
   */
  public void setDocumentRenderer(DocumentRenderer documentRenderer) {
    this.documentRenderer = documentRenderer;
  }

  /**
   * Gets the array of document metadata helpers.
   * 
   * @return the docMetadataHelpers
   */
  public DocumentMetadataHelper[] getDocMetadataHelpers() {
    return docMetadataHelpers;
  }

  /**
   * @return the documentUriFeatureName
   */
  public String getDocumentUriFeatureName() {
    return documentUriFeatureName;
  }

  /**
   * @param documentUriFeatureName
   *          the documentUriFeatureName to set
   */
  public void setDocumentUriFeatureName(String documentUriFeatureName) {
    this.documentUriFeatureName = documentUriFeatureName;
  }

  /**
   * Creates an XStream object suitable for loading and saving Mimir index
   * configurations.
   */
  private static XStream newXStream() {
    XStream xs = new XStream(new StaxDriver());
    xs.setClassLoader(Gate.getClassLoader());
    xs.alias("indexConfig", IndexConfig.class);
    xs.alias("tokenIndexer", TokenIndexerConfig.class);
    xs.alias("semanticIndexer", SemanticIndexerConfig.class);
    return xs;
  }

  /**
   * Saves an {@link IndexConfig} object to a file via XML serialisation.
   * 
   * @param config
   *          the object to be saved.
   * @param file
   *          the file to write to.
   * @throws IOException
   */
  public static void writeConfigToFile(IndexConfig config, File file)
          throws IOException {
    XStream xstream = newXStream();
    FileWriter fileWriter = new FileWriter(file);
    HierarchicalStreamWriter xmlWriter = new PrettyPrintWriter(fileWriter);
    xstream.marshal(config, xmlWriter);
  }

  /**
   * Loads an index config object from a file. The file should have been created
   * using the {@link #writeConfigToFile(IndexConfig, File)} method.
   * 
   * @param file
   *          the file to read.
   * @return an {@link IndexConfig} object.
   * @throws IOException
   *           if the provided config file cannot be found.
   * @throws IndexException
   *           if the parsing of the config file fails.
   */
  public static IndexConfig readConfigFromFile(File file) throws IOException,
          IndexException {
    return readConfigFromUrl(file.toURI().toURL());
  }

  /**
   * Loads an index config object from a URL. The file should have been created
   * using the {@link #writeConfigToFile(IndexConfig, File)} method.
   * 
   * @param u
   *          the URL to read.
   * @return an {@link IndexConfig} object.
   * @throws IOException
   *           if the provided config file cannot be found.
   * @throws IndexException
   *           if the parsing of the config file fails.
   */
  public static IndexConfig readConfigFromUrl(URL u) throws IOException,
          IndexException {
    try {
      XMLInputFactory inputFactory = XMLInputFactory.newInstance();
      InputStream configStream = new BufferedInputStream(u.openStream()); 
      XMLStreamReader xsr =
              inputFactory.createXMLStreamReader(configStream);
      HierarchicalStreamReader xmlReader = new StaxReader(new QNameMap(), xsr);
      try {
        return (IndexConfig)newXStream().unmarshal(xmlReader);
      } finally {
        xmlReader.close();
        configStream.close();
      }
    } catch(XMLStreamException e) {
      throw new IndexException("Exception while reading config from " + u, e);
    }
  }

  /**
   * Loads an index config object from a file, but allows the caller to override
   * the index directory stored in the file. This is useful if the index was
   * created on one machine but is being used on another.
   * 
   * @param configFile
   *          the file to read
   * @param indexDir
   *          the top-level index directory, which will be used instead of the
   *          value stored in the config file.
   * @throws FileNotFoundException
   *           if the provided config file cannot be found.
   * @throws IndexException
   *           if the parsing of the config file fails.
   */
  public static IndexConfig readConfigFromFile(File configFile, File indexDir)
          throws IOException, IndexException {
    IndexConfig conf = readConfigFromFile(configFile);
    // indexDirectory is private but this method is inside the IndexConfig
    // class so this assignment is legal.
    conf.indexDirectory = indexDir;
    return conf;
  }

  /**
   * Loads an index config object from a URL, but allows the caller to override
   * the index directory stored in the file. This is useful if the index was
   * created on one machine but is being used on another.
   * 
   * @param configFile
   *          the file to read
   * @param indexDir
   *          the top-level index directory, which will be used instead of the
   *          value stored in the config file.
   * @throws FileNotFoundException
   *           if the provided config file cannot be found.
   * @throws IndexException
   *           if the parsing of the config file fails.
   */
  public static IndexConfig readConfigFromUrl(URL configFile, File indexDir)
          throws IOException, IndexException {
    IndexConfig conf = readConfigFromUrl(configFile);
    // indexDirectory is private but this method is inside the IndexConfig
    // class so this assignment is legal.
    conf.indexDirectory = indexDir;
    return conf;
  }

  /**
   * The top level directory of the index.
   */
  private File indexDirectory;

  /**
   * The annotation type used for tokens.
   */
  private String tokenAnnotationType;

  /**
   * The annotation set where token annotations can be found.
   */
  private String tokenAnnotationSetName;

  /**
   * The configuration for all the token indexers used.
   */
  private TokenIndexerConfig[] tokenIndexers;

  /**
   * The configuration for all the semantic indexers used.
   */
  private SemanticIndexerConfig[] semanticIndexers;

  /**
   * The helpers used for generating document metadata.
   */
  private DocumentMetadataHelper[] docMetadataHelpers;

  /**
   * The document renderer used to render documents and hits.
   */
  private DocumentRenderer documentRenderer;

  /**
   * The name of the annotation set containing the semantic annotations
   */
  private String semanticAnnotationSetName;

  /**
   * The name for the document feature containing the document URI. Defaults to
   * {@link #DOCUMENT_URI_FEATURE_DEFAULT_NAME}.
   */
  private String documentUriFeatureName = DOCUMENT_URI_FEATURE_DEFAULT_NAME;
  
  /**
   * A Map with arbitrary configuration options, which is made available to all
   * sub-elements of this index (e.g. the various annotation helpers).  
   */
  private Map<String, String> options;
}
