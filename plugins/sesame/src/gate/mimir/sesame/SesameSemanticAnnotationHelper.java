/*
 * SesameSemanticAnnotationHelper.java
 * 
 * Copyright (c) 2007-2011, The University of Sheffield.
 * 
 * This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html),
 * and is free software, licenced under the GNU Affero General Public License,
 * Version 3, November 2007 (also included with this distribution as file
 * LICENCE-AGPL3.html).
 * 
 * A commercial licence is also available for organisations whose business
 * models preclude the adoption of open source and is subject to a licence fee
 * charged by the University of Sheffield. Please contact the GATE team (see
 * http://gate.ac.uk/g8/contact) if you require a commercial licence.
 * 
 * $Id$
 */
package gate.mimir.sesame;

import static gate.mimir.sesame.SesameUtils.SESAME_CONNECTION_COUNT_KEY;
import static gate.mimir.sesame.SesameUtils.SESAME_INDEX_DIRNAME;
import static gate.mimir.sesame.SesameUtils.SESAME_RMANAGER_KEY;
import static gate.mimir.sesame.SesameUtils.SESAME_CONFIG_FILENAME;
import static gate.mimir.sesame.SesameUtils.SESAME_REPOSITORY_NAME_KEY;
import static gate.mimir.sesame.SesameUtils.MIMIR_NAMESPACE;
import static gate.mimir.sesame.SesameUtils.HAS_MENTION;
import static gate.mimir.sesame.SesameUtils.HAS_LENGTH;
import static gate.mimir.sesame.SesameUtils.HAS_FEATURES;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openrdf.OpenRDFException;
import org.openrdf.model.Graph;
import org.openrdf.model.Literal;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.GraphImpl;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.model.vocabulary.XMLSchema;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.algebra.TupleExpr;
import org.openrdf.query.algebra.evaluation.QueryBindingSet;
import org.openrdf.query.impl.DatasetImpl;
import org.openrdf.query.parser.sparql.SPARQLParser;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.config.RepositoryConfig;
import org.openrdf.repository.config.RepositoryConfigException;
import org.openrdf.repository.config.RepositoryConfigUtil;
import org.openrdf.repository.manager.LocalRepositoryManager;
import org.openrdf.repository.manager.RepositoryManager;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.Rio;

import gate.Annotation;
import gate.Gate;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.IndexConfig;
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.mimir.index.Mention;
import gate.mimir.search.QueryEngine;
import gate.util.GateRuntimeException;

public class SesameSemanticAnnotationHelper extends
		AbstractSemanticAnnotationHelper {
	/**
	 * Used to generate unique variable names in SPARQL queries.
	 */
	protected int sparqlVarUniqueId = 0;

	/**
	 * Flag for the static initialisation. Set to <code>true</code> the first
	 * time the static initialisation procedure is completed.
	 */
	private static boolean staticInitDone = false;

	/**
	 * Flag for the initialisation. Set to <code>true</code> after the
	 * initialisation has completed.
	 */
	private boolean initDone = false;

	/**
	 * Parser used for SPARQL queries.
	 */
	protected transient SPARQLParser parser;

	/**
	 * The URIs for the nominal feature predicates.
	 */
	protected URI[] nominalFeaturePredicates;

	/**
	 * the URIs for the numeric feature predicates.
	 */
	protected URI[] floatFeaturePredicates;

	/**
	 * The URIs for the text feature predicates.
	 */
	protected URI[] textFeaturePredicates;

	/**
	 * The URIs for the URI feature predicates.
	 */
	protected URI[] uriFeaturePredicates;

	/**
	 * The number of documents indexed so far
	 */
	protected int docsSoFar;

	/**
	 * The URI for the ANNOTATION_TEMPLATE class
	 */
	public static URI MIMIR_GRAPH_URI;

	/**
	 * The NULL value used for RDF properties
	 */
	public static URI MIMIR_NULL_URI;

	/**
	 * The semantic constraint feature name for querying with sparql.
	 */
	public static final String SEMANTIC_CONSTRAINT_FEATURE = "semanticConstraint";

	/**
	 * The NULL value used for String datatype properties.
	 */
	public static final String MIMIR_NULL_STRING = MIMIR_NAMESPACE + "NULL";

	/**
	 * The NULL value used for numeric datatype properties.
	 */
	public static final double MIMIR_NULL_DOUBLE = Double.MIN_VALUE;

	/**
	 * The URI for the ANNOTATION_TEMPALTE class
	 */
	public static URI ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI;

	/**
	 * The URI for the ANNOTATION_TEMPALTE class
	 */
	public static URI ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI;

	/**
	 * The URI for the MENTION class
	 */
	public static URI MENTION_CLASS_URI;

	/**
	 * URI for the hasSpecialisation object property
	 */
	public static URI HAS_SPECIALISATION_PROP_URI;

	/**
	 * URI for the hasMention object property
	 */
	public static URI HAS_MENTION_PROP_URI;

	/**
	 * URI for the hasLength datatype property
	 */
	public static URI HAS_LENGTH_PROP_URI;

	/**
	 * The URI for the level 1 annotation template ontology class corresponding
	 * to the current annotation type.
	 */
	protected URI annotationTemplateURILevel1;

	/**
	 * The URI for the level 2 annotation template ontology class corresponding
	 * to the current annotation type.
	 */
	protected URI annotationTemplateURILevel2;

	/**
	 * Used to generate unique URIs for annotation template instances.
	 */
	protected long atInstanceUniqueId = 0;

	/**
	 * Used to generate unique URIs for annotation template specialisation
	 * instances.
	 */
	protected long atInstanceSpecUniqueId = 0;

	/**
	 * Used to generate unique URIs for mention instances.
	 */
	protected long mentionUniqueId = 0;

	protected transient String[] uriFeatureNamesPlusSemanticConstraint;

	private static NumberFormat percentFormat;

	protected ValueFactory factory;

	private URICache uriCache;

	// private RepositoryManager manager;
	protected transient RepositoryConnection connection;

	private static final Logger logger = Logger
			.getLogger(SesameSemanticAnnotationHelper.class);

	protected String sesameConfigLocation = "resources/owlim.ttl";

	protected String absoluteConfigLocation = "";

	/**
	 * Constructs a new SesameSemanticAnnotationHelper.
	 * 
	 * @param annotationType
	 *            the type of the annotations handled by this helper.
	 * @param nominalFeatureNames
	 *            the names of the features to be indexed that have nominal
	 *            values (i.e. values from a small set of values).
	 * @param floatFeatureNames
	 *            the names of the features to be indexed that have numeric
	 *            values (i.e. values that can be converted to a double).
	 * @param textFeatureNames
	 *            the names of the features to be indexed that have arbitrary
	 *            text values.
	 * @param uriFeatureNames
	 *            the names of the features to be indexed that have URIs as
	 *            values.
	 */
	public SesameSemanticAnnotationHelper(String annotationType,
			String[] nominalFeatureNames, String[] integerFeatureNames,
			String[] floatFeatureNames, String[] textFeatureNames,
			String[] uriFeatureNames) {
		super(annotationType, nominalFeatureNames, integerFeatureNames,
				floatFeatureNames, textFeatureNames, uriFeatureNames);
		this.nominalFeaturePredicates = new URI[this.nominalFeatureNames.length];
		this.floatFeaturePredicates = new URI[this.floatFeatureNames.length];
		this.textFeaturePredicates = new URI[this.textFeatureNames.length];
		this.uriFeaturePredicates = new URI[this.uriFeatureNames.length];
	}

	/**
	 * Constructs a new SesameSemanticAnnotationHelper.
	 * 
	 * @param annotationType
	 *            the type of the annotations handled by this helper.
	 * @param nominalFeatureNames
	 *            the names of the features to be indexed that have nominal
	 *            values (i.e. values from a small set of values).
	 * @param floatFeatureNames
	 *            the names of the features to be indexed that have numeric
	 *            values (i.e. values that can be converted to a double).
	 * @param textFeatureNames
	 *            the names of the features to be indexed that have arbitrary
	 *            text values.
	 * @param uriFeatureNames
	 *            the names of the features to be indexed that have URIs as
	 *            values.
	 * @param settings
	 *            a Map containing settings for the location of the sesame
	 *            repository config file. Use "relativePath" or "abstractPath"
	 *            as keys
	 */
	public SesameSemanticAnnotationHelper(String annotationType,
			String[] nominalFeatureNames, String[] integerFeatureNames,
			String[] floatFeatureNames, String[] textFeatureNames,
			String[] uriFeatureNames, Map<String, Object> settings) {
		super(annotationType, nominalFeatureNames, integerFeatureNames,
				floatFeatureNames, textFeatureNames, uriFeatureNames);
		this.nominalFeaturePredicates = new URI[this.nominalFeatureNames.length];
		this.floatFeaturePredicates = new URI[this.floatFeatureNames.length];
		this.textFeaturePredicates = new URI[this.textFeatureNames.length];
		this.uriFeaturePredicates = new URI[this.uriFeatureNames.length];
		if (settings.containsKey("relativePath"))
			this.sesameConfigLocation = (String) settings.get("relativePath");
		if (settings.containsKey("absolutePath"))
			this.absoluteConfigLocation = (String) settings.get("absolutePath");
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1396623744631385943L;

	@Override
	public void init(Indexer indexer) {
		if (initDone)
			return;
		try {
			connection = getRepositoryConnection(indexer.getIndexConfig());
			connection.setAutoCommit(true);
			parser = new SPARQLParser();
			// ensure static initialization is done.
			factory = ValueFactoryImpl.getInstance();
			if (!staticInitDone)
				staticInit(connection, factory);
			uriCache = new URICache(this);
			docsSoFar = 0;
			initCommon();
			initDone = true;
		} catch (RepositoryException e) {
			logger.error(e);
		} catch (RepositoryConfigException e) {
			logger.error(e);
		} catch (OpenRDFException e) {
			logger.error(e);
		}
	}

	@Override
	public void init(QueryEngine queryEngine) {
		if (initDone)
			return;
		try {
			connection = getRepositoryConnection(queryEngine.getIndexConfig());
			parser = new SPARQLParser();
			// ensure static initialization is done.
			factory = ValueFactoryImpl.getInstance();
			if (!staticInitDone)
				staticInit(connection, factory);
			uriCache = new URICache(this);
			docsSoFar = 0;
			initCommon();
			initDone = true;
		} catch (RepositoryException e) {
			logger.error(e);
		} catch (RepositoryConfigException e) {
			logger.error(e);
		} catch (OpenRDFException e) {
			logger.error(e);
		}
	}

	@Override
	public String[] getMentionUris(Annotation annotation, int length,
			Indexer indexer) {
		if (!initDone) {
			init(indexer);
		}
		try {
			URI[] mentionURIs = uriCache.getMentionURIs(annotation, length);
			String[] res = new String[mentionURIs.length];
			for (int i = 0; i < mentionURIs.length; i++) {
				res[i] = mentionURIs[i].stringValue();
			}
			return res;
		} catch (Exception e) {
			// we could not find a mention URI ->just skip this annotation
			logger.error("Could not create mention URI for annotation", e);
			return new String[] {};
		}
	}

	@Override
	public List<Mention> getMentions(String annotationType,
			List<Constraint> constraints, QueryEngine engine) {
		if (!initDone)
			init(engine);
		StringBuilder atQuery = new StringBuilder(
				"?annotationTemplateInstance " + "<" + RDF.TYPE.stringValue()
						+ "> " + "<" + annotationTemplateURILevel1 + "> .\n");
		for (int i = 0; i < nominalFeatureNames.length; i++) {
			String aFeatureName = nominalFeatureNames[i];
			List<Constraint> constraintsForThisFeature = new LinkedList<Constraint>();
			for (Constraint aConstraint : constraints) {
				if (aConstraint.getFeatureName().equals(aFeatureName)) {
					constraintsForThisFeature.add(aConstraint);
				}
			}
			if (constraintsForThisFeature.size() > 0) {
				// we have at least one constraint
				atQuery.append(buildSPARQLConstraint(
						"annotationTemplateInstance",
						nominalFeaturePredicates[i], constraintsForThisFeature,
						false));
			}
		}
		boolean hasNonNominalConstraints = false;
		StringBuilder specQuery = new StringBuilder(
				"?annotationTemplateInstance " + "<"
						+ HAS_SPECIALISATION_PROP_URI.stringValue() + "> "
						+ "?specATInstance .\n");
		for (int i = 0; i < floatFeatureNames.length; i++) {
			String aFeatureName = floatFeatureNames[i];
			List<Constraint> constraintsForThisFeature = new LinkedList<Constraint>();
			for (Constraint aConstraint : constraints) {
				if (aConstraint.getFeatureName().equals(aFeatureName)) {
					constraintsForThisFeature.add(aConstraint);
				}
			}
			if (constraintsForThisFeature.size() > 0) {
				// we have at elast one constraint
				hasNonNominalConstraints = true;
				specQuery.append(buildSPARQLConstraint("specATInstance",
						floatFeaturePredicates[i], constraintsForThisFeature,
						true));
			}
		}
		for (int i = 0; i < textFeatureNames.length; i++) {
			String aFeatureName = textFeatureNames[i];
			List<Constraint> constraintsForThisFeature = new LinkedList<Constraint>();
			for (Constraint aConstraint : constraints) {
				if (aConstraint.getFeatureName().equals(aFeatureName)) {
					constraintsForThisFeature.add(aConstraint);
				}
			}
			if (constraintsForThisFeature.size() > 0) {
				// we have at least one constraint
				hasNonNominalConstraints = true;
				specQuery.append(buildSPARQLConstraint("specATInstance",
						textFeaturePredicates[i], constraintsForThisFeature,
						false));
			}
		}
		for (int i = 0; i < uriFeatureNames.length; i++) {
			String aFeatureName = uriFeatureNames[i];
			for (Constraint aConstraint : constraints) {
				if (aConstraint.getFeatureName().equals(aFeatureName)) {
					// we have a constraint
					hasNonNominalConstraints = true;
					// URIs only support EQ predicate
					if (aConstraint.getPredicate() == ConstraintType.EQ) {
						specQuery.append("?specATInstance " + "<"
								+ uriFeaturePredicates[i].stringValue() + "> "
								+ "<" + aConstraint.getValue().toString()
								+ "> .\n");
					} else {
						throw new IllegalArgumentException(
								"Attempt to use a constraint of"
										+ "type "
										+ aConstraint.getPredicate()
										+ "."
										+ "Feature \""
										+ aFeatureName
										+ "\" is of type URI, so only \"equals\" "
										+ "constraints are supported.");
					}
				}
			}
		}
		for (Constraint sConstraint : constraints) {
			if (sConstraint.getFeatureName()
					.equals(SEMANTIC_CONSTRAINT_FEATURE)) {
				specQuery.append("?specATInstance mimir:" + annotationType
						+ "_hasinst ?inst . \n");
				hasNonNominalConstraints = true;
				String constraintValue = sConstraint.getValue().toString();
				if (!constraintValue.trim().endsWith("."))
					constraintValue += " . ";
				if (!constraintValue.endsWith(" \n"))
					constraintValue += "\n";
				specQuery.append(constraintValue);
			}
		}
		// now build the top query
		StringBuilder query = new StringBuilder("PREFIX mimir: <"
				+ MIMIR_NAMESPACE + ">\n");
		query.append("PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n");
		query.append("PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>\n");
		query.append("SELECT ?mentionInstance ?length \n");
		query.append("WHERE\n");
		query.append("{\n");
		if (hasNonNominalConstraints) {
			query.append(atQuery);
			// query.append(
			// "?annotationTemplateInstance <" +
			// HAS_SPECIALISATION_PROP_URI.stringValue() +
			// "> ?specATInstance .\n");
			query.append(specQuery);
			query.append("?specATInstance <"
					+ HAS_MENTION_PROP_URI.stringValue()
					+ "> ?mentionInstance .\n");
			query.append("?mentionInstance <"
					+ HAS_LENGTH_PROP_URI.stringValue() + "> ?length .\n");
		} else {
			// no non-nominal constraints: just use the top level template
			query.append(atQuery);
			query.append("?annotationTemplateInstance <"
					+ HAS_MENTION_PROP_URI.stringValue()
					+ "> ?mentionInstance .\n");
			query.append("?mentionInstance <"
					+ HAS_LENGTH_PROP_URI.stringValue() + "> ?length .\n");
		}
		query.append("}");
		logger.debug("About to execute query:\n" + query.toString());
		try {
			TupleQuery preparedTupleQuery = connection.prepareTupleQuery(
					QueryLanguage.SPARQL, query.toString());
			TupleQueryResult result = preparedTupleQuery.evaluate();
			List<Mention> mentions = new ArrayList<Mention>();
			while (result.hasNext()) {
				BindingSet binding = result.next();
				String mentionURI = binding.getBinding("mentionInstance")
						.getValue().stringValue();
				mentions.add(new Mention(mentionURI, ((Literal) binding
						.getBinding("length").getValue()).intValue()));
			}
			return mentions;
		} catch (MalformedQueryException e) {
			throw new GateRuntimeException(
					"Error while generating SPARQL query. The generated query "
							+ "was: " + query.toString(), e);
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RepositoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new ArrayList<Mention>();
	}

	@Override
	public void close(Indexer indexer) {
		if (initDone) {
			logger.info("Closing Sesame Repository Connection");
			try {
				connection.commit();
				connection.close();
			} catch (RepositoryException e) {
				logger.error(
						"Error while closing Sesame Repository Connection", e);
			}
			connection = null;
			parser = null;
			initDone = false;
			// if we're the last helper, shutdown the ORDI source
			int clientCount = (Integer) indexer.getIndexConfig().getContext()
					.get(SESAME_CONNECTION_COUNT_KEY);
			clientCount--;
			indexer.getIndexConfig()
					.getContext()
					.put(SESAME_CONNECTION_COUNT_KEY,
							Integer.valueOf(clientCount));
			if (clientCount == 0) {
				// shutting down the ORDI Source
				RepositoryManager manager = (RepositoryManager) indexer
						.getIndexConfig().getContext().get(SESAME_RMANAGER_KEY);
				if (manager != null) {
					logger.info("Shutting down Sesame repositoryManager");
					manager.shutDown();
					indexer.getIndexConfig().getContext()
							.remove(SESAME_RMANAGER_KEY);
				}
			}
		}
	}

	@Override
	public void close(QueryEngine qEngine) {
		if (initDone) {
			logger.info("Closing Sesame Repository Connection");
			try {
				connection.commit();
				connection.close();
			} catch (RepositoryException e) {
				logger.error(
						"Error while closing Sesame Repository Connection", e);
			}
			connection = null;
			parser = null;
			initDone = false;
			// if we're the last helper, shutdown the ORDI source
			int clientCount = (Integer) qEngine.getIndexConfig().getContext()
					.get(SESAME_CONNECTION_COUNT_KEY);
			clientCount--;
			qEngine.getIndexConfig()
					.getContext()
					.put(SESAME_CONNECTION_COUNT_KEY,
							Integer.valueOf(clientCount));
			if (clientCount == 0) {
				// shutting down the ORDI Source
				RepositoryManager manager = (RepositoryManager) qEngine
						.getIndexConfig().getContext().get(SESAME_RMANAGER_KEY);
				if (manager != null) {
					logger.info("Shutting down Sesame repositoryManager");
					manager.shutDown();
					qEngine.getIndexConfig().getContext()
							.remove(SESAME_RMANAGER_KEY);
				}
			}
		}
	}

	public RepositoryConnection getRepositoryConnection(IndexConfig config)
			throws RepositoryException, RepositoryConfigException {
		RepositoryManager manager = (RepositoryManager) config.getContext()
				.get(SESAME_RMANAGER_KEY);
		String repositoryName = (String) config.getContext().get(
				SESAME_REPOSITORY_NAME_KEY);
		if (repositoryName == null)
			repositoryName = "owlim";
		if (manager == null) {
			// not initialised yet - > we create it and save it ourselves.
			File topDir = config.getIndexDirectory();
			File sesameIndexDir = new File(topDir, SESAME_INDEX_DIRNAME);
			sesameIndexDir.mkdirs();
			RepositoryConfig repositoryConfig;
			if (!absoluteConfigLocation.equals("")) {
				repositoryConfig = prepareConfig(absoluteConfigLocation);
			} else {
				URL configPath = resolveUrl(sesameConfigLocation);
				repositoryConfig = prepareConfig(configPath.getPath());
			}
			manager = new LocalRepositoryManager(sesameIndexDir);
			try {
				manager.initialize();
				manager.addRepositoryConfig(repositoryConfig);
			} catch (RepositoryException e) {
				logger.error(e);
			} catch (RepositoryConfigException e) {
				logger.error(e);
			}
			config.getContext().put(SESAME_RMANAGER_KEY, manager);
			config.getContext().put(SESAME_CONNECTION_COUNT_KEY, 1);
		} else {
			int clientCount = (Integer) config.getContext().get(
					SESAME_CONNECTION_COUNT_KEY);
			config.getContext().put(SESAME_CONNECTION_COUNT_KEY,
					Integer.valueOf(clientCount + 1));
		}
		return manager.getRepository(repositoryName).getConnection();
	}

	/**
	 * Prepare repository configuration
	 */
	private RepositoryConfig prepareConfig(String configPath) {
		RepositoryConfig config;
		final Graph graph = new GraphImpl();
		try {
			try {
				RDFParser parser = Rio.createParser(RDFFormat.TURTLE);
				RDFHandler confHandler = new RDFHandler() {
					public void startRDF() throws RDFHandlerException {
					}

					public void endRDF() throws RDFHandlerException {
					}

					public void handleComment(String arg) {
					}

					public void handleNamespace(String arg0, String arg1) {
					}

					public void handleStatement(Statement arg) {
						graph.add(arg);
					}
				};
				parser.setRDFHandler(confHandler);
				try {
					File config_file = new File(configPath);
					FileReader reader = new FileReader(config_file);
					parser.parse(reader, "");
					reader.close();
				} catch (RDFParseException rpe) {
					logger.warn("Can not parse repository configuration path",
							rpe);
				}
			} catch (IOException ioe) {
				logger.warn(
						"Can not instantiate repository configuration graph",
						ioe);
			}
		} catch (RDFHandlerException rhe) {
			logger.warn("Internal graph initialization exception!", rhe);
		}
		Iterator<Statement> iterSt = graph.match(null, RDF.TYPE, new URIImpl(
				"http://www.openrdf.org/config/repository#Repository"));
		Resource repNode = null;
		if (iterSt.hasNext()) {
			Statement st = iterSt.next();
			repNode = st.getSubject();
		}
		try {
			config = RepositoryConfig.create(graph, repNode);
		} catch (RepositoryConfigException repo) {
			throw new RuntimeException(repo);
		}
		return config;
	}

	protected static void staticInit(RepositoryConnection connection,
			ValueFactory factory) throws OpenRDFException {
		if (staticInitDone)
			return;
		percentFormat = NumberFormat.getPercentInstance();
		percentFormat.setMinimumFractionDigits(2);
		// create the ontology resources required by all instances of
		// DefaultSemanticAnnotationHelper
		// create the MIMIR graph
		MIMIR_GRAPH_URI = factory.createURI(MIMIR_NAMESPACE, "graph");
		MIMIR_NULL_URI = factory.createURI(MIMIR_NAMESPACE, "NULL");
		// create ANNOTATION_TEMPLATE classes
		ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI = factory.createURI(
				MIMIR_NAMESPACE, "AnnotationTemplateL1");
		createClass(ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI, connection, factory);
		ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI = factory.createURI(
				MIMIR_NAMESPACE, "AnnotationTemplateL2");
		createClass(ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI, connection, factory);
		// define hasSpecialisation property
		HAS_SPECIALISATION_PROP_URI = factory.createURI(MIMIR_NAMESPACE,
				"hasSpecialisation");
		connection.add(HAS_SPECIALISATION_PROP_URI, RDF.TYPE,
				OWL.OBJECTPROPERTY, MIMIR_GRAPH_URI);
		connection.add(HAS_SPECIALISATION_PROP_URI, RDFS.DOMAIN,
				ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI, MIMIR_GRAPH_URI);
		connection.add(HAS_SPECIALISATION_PROP_URI, RDFS.RANGE,
				ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI, MIMIR_GRAPH_URI);
		// create MENTION class
		MENTION_CLASS_URI = factory.createURI(MIMIR_NAMESPACE, "Mention");
		createClass(MENTION_CLASS_URI, connection, factory);
		// add the ANNOTATION_TEMPLATE hasMention MENTION triple.
		HAS_MENTION_PROP_URI = factory.createURI(MIMIR_NAMESPACE, HAS_MENTION);
		connection.add(HAS_MENTION_PROP_URI, RDF.TYPE, OWL.OBJECTPROPERTY,
				MIMIR_GRAPH_URI);
		connection.add(HAS_MENTION_PROP_URI, RDFS.DOMAIN,
				ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI, MIMIR_GRAPH_URI);
		connection.add(HAS_MENTION_PROP_URI, RDFS.RANGE, MENTION_CLASS_URI,
				MIMIR_GRAPH_URI);
		// add MENTION hasLength property
		HAS_LENGTH_PROP_URI = factory.createURI(MIMIR_NAMESPACE, HAS_LENGTH);
		connection.add(HAS_LENGTH_PROP_URI, RDF.TYPE, OWL.DATATYPEPROPERTY,
				MIMIR_GRAPH_URI);
		connection.add(HAS_LENGTH_PROP_URI, RDFS.DOMAIN, MENTION_CLASS_URI,
				MIMIR_GRAPH_URI);
		connection.add(HAS_LENGTH_PROP_URI, RDFS.RANGE, XMLSchema.INT,
				MIMIR_GRAPH_URI);
		staticInitDone = true;
	}

	/**
	 * Runs the initialisation routines common to indexing and searching.
	 * 
	 * @throws RepositoryException
	 */
	protected void initCommon() throws RepositoryException {
		// create all ontology resources required by *this*
		// DefaultSemanticAnnotationHelper.
		// create the ANNOTATION_TEMPLATE sub-classes
		annotationTemplateURILevel1 = factory.createURI(MIMIR_NAMESPACE,
				annotationType + "L1");
		createClass(annotationTemplateURILevel1, connection, factory);
		connection.add(annotationTemplateURILevel1, RDFS.SUBCLASSOF,
				ANNOTATION_TEMPLATE_LEVEL1_CLASS_URI, MIMIR_GRAPH_URI);
		annotationTemplateURILevel2 = factory.createURI(MIMIR_NAMESPACE,
				annotationType + "L2");
		createClass(annotationTemplateURILevel2, connection, factory);
		connection.add(annotationTemplateURILevel2, RDFS.SUBCLASSOF,
				ANNOTATION_TEMPLATE_LEVEL2_CLASS_URI, MIMIR_GRAPH_URI);
		// define the required properties for the nominal features
		for (int i = 0; i < nominalFeatureNames.length; i++) {
			nominalFeaturePredicates[i] = factory.createURI(MIMIR_NAMESPACE,
					annotationType + "_has" + nominalFeatureNames[i]);
			createDatatypeProperty(nominalFeaturePredicates[i],
					annotationTemplateURILevel1, XMLSchema.STRING, connection,
					factory);
		}
		// define the required properties for the numeric features
		for (int i = 0; i < floatFeatureNames.length; i++) {
			floatFeaturePredicates[i] = factory.createURI(MIMIR_NAMESPACE,
					annotationType + "_has" + floatFeatureNames[i]);
			createDatatypeProperty(floatFeaturePredicates[i],
					annotationTemplateURILevel2, XMLSchema.DOUBLE, connection,
					factory);
		}
		// define the required properties for the text features
		for (int i = 0; i < textFeatureNames.length; i++) {
			textFeaturePredicates[i] = factory.createURI(MIMIR_NAMESPACE,
					annotationType + "+has" + textFeatureNames[i]);
			createDatatypeProperty(textFeaturePredicates[i],
					annotationTemplateURILevel2, XMLSchema.STRING, connection,
					factory);
		}
		// define the required properties for the URI features
		for (int i = 0; i < uriFeatureNames.length; i++) {
			uriFeaturePredicates[i] = factory.createURI(MIMIR_NAMESPACE,
					annotationType + "_has" + uriFeatureNames[i]);
			createRDFProperty(uriFeaturePredicates[i], connection, factory);
		}
		// prepare the value that will be returned as the uriFeatureNames we
		// report
		// to other clients.
		if (Arrays.asList(uriFeatureNames)
				.contains(SEMANTIC_CONSTRAINT_FEATURE)) {
			uriFeatureNamesPlusSemanticConstraint = uriFeatureNames;
		} else {
			uriFeatureNamesPlusSemanticConstraint = new String[uriFeatureNames.length + 1];
			System.arraycopy(uriFeatureNames, 0,
					uriFeatureNamesPlusSemanticConstraint, 0,
					uriFeatureNames.length);
			uriFeatureNamesPlusSemanticConstraint[uriFeatureNames.length] = SEMANTIC_CONSTRAINT_FEATURE;
		}
		initDone = true;
	}

	/**
	 * Creates a new ontology class with a given URI.
	 * 
	 * @param classURI
	 *            the URI for the new class
	 * @param ordiConnection
	 *            a connection to ORDI.
	 * @param ordiFactory
	 *            an ORDI triples factory.
	 * @return <code>true</code> if the new class was created successfully, or
	 *         <code>false</code> if the class was already present.
	 * @throws RepositoryException
	 *             if there are problems while creating the class.
	 */
	protected static boolean createClass(URI classURI,
			RepositoryConnection connection, ValueFactory factory)
			throws RepositoryException {
		// check if the class exists already
		RepositoryResult<Statement> result = connection.getStatements(classURI,
				RDF.TYPE, OWL.CLASS, false, MIMIR_GRAPH_URI);
		if (result.hasNext()) {
			// class already exists
			return false;
		}
		// else, create new class
		Statement statement = new StatementImpl(classURI, RDF.TYPE, OWL.CLASS);
		connection.add(statement, MIMIR_GRAPH_URI);
		return statement != null;
	}

	/**
	 * Creates a new datatype property.
	 * 
	 * @param propertyURI
	 *            the URI for the new property
	 * @param propertyRange
	 *            the URI for the property value type.
	 * @param ordiConnection
	 *            a connection to ORDI.
	 * @param ordiFactory
	 *            an ORDI triples factory.
	 * @return <code>true</code> if the new property was created successfully,
	 *         or <code>false</code> if the property was already present.
	 * @throws RepositoryException
	 */
	protected static boolean createDatatypeProperty(URI propertyURI,
			URI propertyDomain, URI propertyRange,
			RepositoryConnection connection, ValueFactory factory)
			throws RepositoryException {
		// check if the property exists already
		RepositoryResult<Statement> result = connection.getStatements(
				propertyURI, RDF.TYPE, OWL.DATATYPEPROPERTY, false,
				MIMIR_GRAPH_URI);
		if (result.hasNext()) {
			// property already exists
			return false;
		}
		// else, create new property
		Statement statement = new StatementImpl(propertyURI, RDF.TYPE,
				OWL.DATATYPEPROPERTY);
		connection.add(statement, MIMIR_GRAPH_URI);
		connection.add(propertyURI, RDFS.RANGE, propertyRange, MIMIR_GRAPH_URI);
		connection.add(propertyURI, RDFS.DOMAIN, propertyDomain,
				MIMIR_GRAPH_URI);
		return statement != null;
	}

	/**
	 * Creates a new plain RDF property.
	 * 
	 * @param propertyURI
	 *            the URI for the new property
	 * @param connection
	 *            a connection to the Sesame repository.
	 * @param factory
	 *            a sesame ValueFactory.
	 * @return <code>true</code> if the new property was created successfully,
	 *         or <code>false</code> if the property was already present.
	 * @throws RepositoryException
	 */
	protected static boolean createRDFProperty(URI propertyURI,
			RepositoryConnection connection, ValueFactory factory)
			throws RepositoryException {
		// check if the property exists already
		RepositoryResult<Statement> result = connection.getStatements(
				propertyURI, RDF.TYPE, RDF.PROPERTY, false, MIMIR_GRAPH_URI);
		if (result.hasNext()) {
			return false;
		}
		// else, create new property
		Statement statement = new StatementImpl(propertyURI, RDF.TYPE,
				RDF.PROPERTY);
		connection.add(statement, MIMIR_GRAPH_URI);
		return statement != null;
	}

	protected String buildSPARQLConstraint(String variableName, URI property,
			List<Constraint> constraints, boolean numeric) {
		StringBuilder query = new StringBuilder();
		if (numeric) {
			query.append("?" + variableName + " <" + property.stringValue()
					+ "> ");
			// we mark the value as a variable
			String valueVar = "var" + sparqlVarUniqueId++;
			query.append("?" + valueVar + " .\n");
			// and append all constraints into an AND FILTER
			// start with non -NULL
			query.append("FILTER (?" + valueVar + " != " + MIMIR_NULL_DOUBLE);
			for (Constraint constraint : constraints) {
				query.append(" && ?" + valueVar);
				String valueLiteral = constraint.getValue() instanceof Number ? ((Number) constraint
						.getValue()).toString() : "\""
						+ constraint.getValue().toString() + "\"";
				switch (constraint.getPredicate()) {
				case EQ:
					query.append(" = " + valueLiteral);
					break;
				case GT:
					query.append(" > " + valueLiteral);
					break;
				case LT:
					query.append(" < " + valueLiteral);
					break;
				case GE:
					query.append(" >= " + valueLiteral);
					break;
				case LE:
					query.append(" <= " + valueLiteral);
					break;
				default:
					throw new IllegalArgumentException(
							"Don't understand predicate type: "
									+ constraint.getPredicate() + "!");
				}
			}// for constraints
				// close the FILTER (...)
			query.append(") \n");
		} else {
			// non numeric constraints
			String valueVar = null;
			for (Constraint constraint : constraints) {
				if (constraint.getPredicate() == ConstraintType.EQ) {
					query.append("?" + variableName + " <"
							+ property.stringValue() + "> ");
					// it's an EQ predicate -> we can simply include it as a
					// condition
					query.append("\"" + constraint.getValue().toString()
							+ "\" .\n");
				} else {
					// we need to use FILTER
					// we mark the value as a variable
					if (valueVar == null) {
						valueVar = "var" + sparqlVarUniqueId++;
						query.append("?" + variableName + " <"
								+ property.stringValue() + "> ?" + valueVar
								+ " .\n");
					}
					if (constraint.getPredicate() == ConstraintType.REGEX) {
						query.append("FILTER regex(?" + valueVar + ", ");
						if (constraint.getValue() instanceof String) {
							query.append("\"" + (String) constraint.getValue()
									+ "\"");
						} else if (constraint.getValue() instanceof String[]) {
							query.append("\""
									+ ((String[]) constraint.getValue())[0]
									+ "\", \""
									+ ((String[]) constraint.getValue())[1]
									+ "\"");
						}
						query.append(") \n");
					} else {
						String valueLiteral = constraint.getValue() instanceof Number ? ((Number) constraint
								.getValue()).toString() : "\""
								+ constraint.getValue().toString() + "\"";
						query.append("FILTER (?" + valueVar);
						switch (constraint.getPredicate()) {
						case EQ:
							query.append(" = " + valueLiteral);
							break;
						case GT:
							query.append(" > " + valueLiteral);
							break;
						case LT:
							query.append(" < " + valueLiteral);
							break;
						case GE:
							query.append(" >= " + valueLiteral);
							break;
						case LE:
							query.append(" <= " + valueLiteral);
							break;
						default:
							throw new IllegalArgumentException(
									"Don't understand predicate type: "
											+ constraint.getPredicate() + "!");
						}
						// close the FILTER (...)
						query.append(") \n");
					}// not EQ, nor REGEX
				}
			}// for constraints
		}// non numeric
		return query.toString();
	}

	protected URL resolveUrl(String location) {
		try {
			return new URL(Gate.getCreoleRegister()
					.get(SesamePluginResource.class.getName()).getXmlFileUrl(),
					location);
		} catch (MalformedURLException e) {
			throw new GateRuntimeException("Could not resolve " + location
					+ " as a URL", e);
		}
	}
}
