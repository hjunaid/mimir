/*
 *  DBSemanticAnnotationHelper.java
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
 *  Valentin Tablan, 08 Feb 2011
 *   
 *  $Id$
 */
package gate.mimir.db;

import static gate.mimir.db.AnnotationTemplateCache.Tag.NO_ID;
import gate.Annotation;
import gate.Document;
import gate.Gate;
import gate.creole.ANNIEConstants;
import gate.mimir.AbstractSemanticAnnotationHelper;
import gate.mimir.Constraint;
import gate.mimir.ConstraintType;
import gate.mimir.DocumentMetadataHelper;
import gate.mimir.IndexConfig;
import gate.mimir.IndexConfig.SemanticIndexerConfig;
import gate.mimir.IndexConfig.TokenIndexerConfig;
import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.db.AnnotationTemplateCache.Tag;
import gate.mimir.index.IndexException;
import gate.mimir.index.Indexer;
import gate.mimir.index.Mention;
import gate.mimir.index.OriginalMarkupMetadataHelper;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryRunner;
import gate.mimir.search.query.Binding;
import it.unimi.dsi.mg4j.index.DowncaseTermProcessor;
import it.unimi.dsi.mg4j.index.NullTermProcessor;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.log4j.Logger;

/**
 * A Semantic annotation helper that uses an embedded RDBMS for storing 
 * annotation data. 
 */
public class DBSemanticAnnotationHelper extends AbstractSemanticAnnotationHelper{

  private static final long serialVersionUID = 2734946594117068194L;

  /**
   * The directory name for the database data (relative to the top level index
   * directory).
   */
  public static final String DB_DIR_NAME = "db";

  
  /**
   * Key used to retrieve the {@link List} of table base names (see 
   * {@link #tableBaseName}) from the {@link IndexConfig#getContext()} context.
   */
  public static final String DB_NAMES_CONTEXT_KEY = 
      DBSemanticAnnotationHelper.class.getName() + ":dbNames";
  
  protected static final String L1_TABLE_SUFFIX = "L1";
  
  protected static final String L2_TABLE_SUFFIX = "L2";
  
  protected static final String MENTIONS_TABLE_SUFFIX = "Mentions";

  /**
   * The key in the {@link IndexConfig#getOptions()} Map for the size of the 
   * memory cache to be used by the database. The cache size defaults to 1 GB.
   * Too small a cache size can lead to out of memory errors during indexing!
   */
  public static final String DB_CACHE_SIZE_OPTIONS_KEY = "databaseCacheSize";
  
  /**
   * The base name (prefix) used for all tables created by this helper.
   * The name is derived from the annotation name.
   */
  protected String tableBaseName;
  

  
  /**
   * Flag showing if the second level model is need (i.e. if the annotation
   * has any non-nominal features)
   */
  protected boolean level2Used = true;
  
  /**
   * Prepared statement used to obtain the Level-1 ID based on the values of 
   * nominal features. Only used at indexing time. 
   */
  protected transient PreparedStatement level1SelectStmt;

  /**
   * Prepared statement used to insert anew row into the Level-1 table. 
   * Only used at indexing time. 
   */
  protected transient PreparedStatement level1InsertStmt;
  
  /**
   * Prepared statement used to obtain the Level-2 ID based on the values of 
   * non-nominal features. Only used at indexing time.
   */  
  protected transient PreparedStatement level2SelectStmt;
  
  /**
   * Prepared statement used to insert anew row into the Level-2 table. 
   * Only used at indexing time. 
   */
  protected transient PreparedStatement level2InsertStmt;
  
  /**
   * Prepared statement used to obtain the Mention ID based on the Level-1 ID, 
   * the Level-2 ID and the annotation length. Only used at indexing time.
   */
  protected transient PreparedStatement mentionsSelectStmt;
  
  /**
   * Prepared statement used to insert anew row into the mentions table. 
   * Only used at indexing time. 
   */
  protected transient PreparedStatement mentionsInsertStmt;

  /**
   * The set of feature names for all the nominal features. 
   * Only used at search time.
   */
  protected transient Set<String> nominalFeatureNameSet;  
  
  /**
   * The set of feature names for all the non-nominal features. 
   * Only used at search time.
   */
  protected transient Set<String> nonNominalFeatureNameSet;
  
  /**
   * A cached connection used throughout the life of this helper.
   */
  protected transient Connection dbConnection;
  
  protected transient AnnotationTemplateCache cache;
  
  private transient int docsSoFar = 0;
  
  private static transient NumberFormat percentFormat = NumberFormat.getPercentInstance();
  
  private static transient Logger logger = Logger.getLogger(DBSemanticAnnotationHelper.class);
  
  public DBSemanticAnnotationHelper(String annotationType,
          String[] nominalFeatureNames, String[] integerFeatureNames,
          String[] floatFeatureNames, String[] textFeatureNames) {
    this(annotationType, nominalFeatureNames, integerFeatureNames,
            floatFeatureNames, textFeatureNames, null);
  }
  
  /**
   * Standard 6-argument SAH constructor.  This helper type does not support
   * URI features directly, they will be combined with the text features.
   */
  public DBSemanticAnnotationHelper(String annotationType,
          String[] nominalFeatureNames, String[] integerFeatureNames,
          String[] floatFeatureNames, String[] textFeatureNames,
          String[] uriFeatureNames) {
    super(annotationType, nominalFeatureNames, integerFeatureNames,
            floatFeatureNames, concatenateArrays(textFeatureNames, uriFeatureNames),
            null);
    if(uriFeatureNames != null && uriFeatureNames.length > 0) {
      logger.warn(
              "This helper type does not fully support URI features, "
              + "they will be indexed but only as text literals!");
    }
    
    cache = new AnnotationTemplateCache(this);
  }
  
  @Override
  public void init(Indexer indexer) {
    // calculate the basename
    // to avoid inter-locking between the multiple SB-based indexers, they each 
    // create their ow database.
    tableBaseName = annotationType.replaceAll("[^\\p{Alnum}_]", "_");
    List<String> baseNames = (List<String>)indexer.getIndexConfig()
        .getContext().get(DB_NAMES_CONTEXT_KEY);
    if(baseNames == null) {
      baseNames = new LinkedList<String>();
      indexer.getIndexConfig().getContext().put(DB_NAMES_CONTEXT_KEY, baseNames);
    }
    while(baseNames.contains(tableBaseName)) {
      tableBaseName += "_";
    }
    baseNames.add(tableBaseName);
    
    File dbDir = new File(indexer.getIndexDir(), DB_DIR_NAME);
    try {
      Class.forName("org.h2.Driver");
      String cacheSizeStr = indexer.getIndexConfig().getOptions().get(
              DB_CACHE_SIZE_OPTIONS_KEY);
      // default to 100 MiB, if not provided
      if(cacheSizeStr == null) cacheSizeStr = Integer.toString(100 *1024);
      dbConnection = DriverManager.getConnection(
              "jdbc:h2:file:" + dbDir.getAbsolutePath() + 
              "/" + tableBaseName + ";CACHE_SIZE=" + cacheSizeStr, "sa", "");
      dbConnection.setAutoCommit(true);
      dbConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      createDb(indexer);
    } catch(SQLException e) {
      throw new RuntimeException("Error while initialising the database", e);
    } catch(ClassNotFoundException e) {
      throw new RuntimeException("Database driver not loaded.", e);
    }
  }
  
  @Override
  public void init(QueryEngine qEngine) {
    File dbDir = new File(qEngine.getIndexDir(), DB_DIR_NAME);
    if(!dbDir.exists()) { throw new IllegalArgumentException(
            "Target database directory (at " + dbDir.getAbsolutePath()
                    + ") does not exist!"); 
    }
    try {
      Class.forName("org.h2.Driver");
      String cacheSizeStr = qEngine.getIndexConfig().getOptions().get(
              DB_CACHE_SIZE_OPTIONS_KEY);
      // default to 100 MiB, if not provided
      if(cacheSizeStr == null) cacheSizeStr = Integer.toString(100 *1024);
      dbConnection = DriverManager.getConnection(
              "jdbc:h2:file:" + dbDir.getAbsolutePath() + 
              "/" + tableBaseName + ";CACHE_SIZE=" + cacheSizeStr, "sa", "");
      dbConnection.setReadOnly(true);
    } catch(SQLException e) {
      throw new RuntimeException("Error while initialising the database", e);
    } catch(ClassNotFoundException e) {
      throw new RuntimeException("Database driver not loaded.", e);
    }
    
    nominalFeatureNameSet = new HashSet<String>();
    if(nominalFeatureNames != null){
      for(String name : nominalFeatureNames) nominalFeatureNameSet.add(name);
    }
    
    nonNominalFeatureNameSet = new HashSet<String>();
    if(integerFeatureNames != null){
      for(String name : integerFeatureNames) nonNominalFeatureNameSet.add(name);
    }
    if(floatFeatureNames != null){
      for(String name : floatFeatureNames) nonNominalFeatureNameSet.add(name);
    }
    if(textFeatureNames != null){
      for(String name : textFeatureNames) nonNominalFeatureNameSet.add(name);
    }
  }

  /**
   * Creates in the database the tables required by this helper for indexing.
   * Called at index creation, during the initialisation process.
   * 
   * During indexing, the only tests are equality tests (to check we're not 
   * inserting duplicate rows). To support those in the most efficient way 
   * possible, we're creating MEMORY tables (with the indexes stored in RAM) 
   * and HASH indexes for all data columns.
   * 
   * @throws SQLException 
   */
  protected void createDb(Indexer indexer) throws SQLException {
    Statement stmt = dbConnection.createStatement();
    // ////////////////////////////////
    // create the Level 1 table
    // ////////////////////////////////
    StringBuilder createStr = new StringBuilder();
    StringBuilder selectStr = new StringBuilder();
    StringBuilder insertStr = new StringBuilder();
    createStr.append("CREATE TABLE IF NOT EXISTS " + tableName(null, L1_TABLE_SUFFIX) +
            " (ID IDENTITY NOT NULL PRIMARY KEY");
    selectStr.append("SELECT ID FROM " + tableName(null, L1_TABLE_SUFFIX));
    insertStr.append("INSERT INTO " + tableName(null, L1_TABLE_SUFFIX) + " VALUES(DEFAULT");
    if(nominalFeatureNames != null && nominalFeatureNames.length > 0) {
      selectStr.append(" WHERE");
      boolean firstWhere = true;
      for(String aFeatureName : nominalFeatureNames) {
        createStr.append(", \"" + aFeatureName + "\" VARCHAR(255)");
        if(firstWhere) firstWhere = false; else selectStr.append(" AND");
        selectStr.append(" \"" + aFeatureName + "\" IS ?");
        insertStr.append(", ?");
      }
    }
    createStr.append(")");
    insertStr.append(")");
    logger.debug("Create statement:\n" + createStr.toString());
    stmt.execute(createStr.toString());
    logger.debug("Select Level 1:\n" + selectStr.toString());
    level1SelectStmt = dbConnection.prepareStatement(selectStr.toString());
    level1InsertStmt = dbConnection.prepareStatement(insertStr.toString());
    
    // ////////////////////////////////
    // create the Level 2 table
    // ////////////////////////////////
    int nonNominalFeats = 0;
    if(integerFeatureNames != null) nonNominalFeats += integerFeatureNames.length;
    if(floatFeatureNames != null) nonNominalFeats += floatFeatureNames.length;
    if(textFeatureNames != null) nonNominalFeats += textFeatureNames.length;
    level2Used = (nonNominalFeats > 0);
    if(level2Used) {
      createStr = new StringBuilder(
          "CREATE TABLE IF NOT EXISTS " + tableName(null, L2_TABLE_SUFFIX) + 
          " (ID IDENTITY NOT NULL PRIMARY KEY, L1_ID BIGINT");
      selectStr = new StringBuilder(
          "SELECT ID FROM " + tableName(null, L2_TABLE_SUFFIX) + " WHERE L1_ID IS ?");
      insertStr = new StringBuilder(
              "INSERT INTO " + tableName(null, L2_TABLE_SUFFIX) + " VALUES(DEFAULT, ?");
      if(integerFeatureNames != null && integerFeatureNames.length > 0) {
        for(String aFeatureName : integerFeatureNames) {
          createStr.append(", \"" + aFeatureName + "\" BIGINT");
          selectStr.append(" AND \"" + aFeatureName + "\" IS ?");
          insertStr.append(", ?");
        }
      }
      if(floatFeatureNames != null && floatFeatureNames.length > 0) {
        for(String aFeatureName : floatFeatureNames) {
          createStr.append(", \"" + aFeatureName + "\" DOUBLE");
          selectStr.append(" AND \"" + aFeatureName + "\" IS ?");
          insertStr.append(", ?");
        }
      }
      if(textFeatureNames != null && textFeatureNames.length > 0) {
        for(String aFeatureName : textFeatureNames) {
          createStr.append(", \"" + aFeatureName + "\" VARCHAR(255)");
          selectStr.append(" AND \"" + aFeatureName + "\" IS ?");
          insertStr.append(", ?");
        }
      }
      createStr.append(")");
      insertStr.append(")");
      logger.debug("Create statement:\n" + createStr.toString());
      stmt.execute(createStr.toString());
      // add the foreign key constraint
      String forKeyStr = "ALTER TABLE " + tableName(null, L2_TABLE_SUFFIX) + 
          " ADD CONSTRAINT " + tableName(null, "L1L2FK") + 
          " FOREIGN KEY(L1_ID) REFERENCES " + 
          tableName(null, L1_TABLE_SUFFIX) + "(ID)";
      logger.debug("Foreign key:\n" + forKeyStr);
      stmt.execute(forKeyStr);
      
      logger.debug("Select Level 2:\n" + selectStr.toString());
      level2SelectStmt = dbConnection.prepareStatement(selectStr.toString());
      level2InsertStmt = dbConnection.prepareStatement(insertStr.toString());
    }
    // /////////////////////////////
    // create the Mentions table
    // /////////////////////////////
    createStr = new StringBuilder(
        "CREATE TABLE IF NOT EXISTS " + tableName(null, MENTIONS_TABLE_SUFFIX) + 
        " (ID IDENTITY NOT NULL PRIMARY KEY, L1_ID BIGINT");
    selectStr = new StringBuilder(
            "SELECT ID FROM " + tableName(null, MENTIONS_TABLE_SUFFIX) + " WHERE L1_ID IS ?");
    insertStr = new StringBuilder(
            "INSERT INTO " + tableName(null, MENTIONS_TABLE_SUFFIX) + " VALUES(DEFAULT, ?");
    if(level2Used) {
      createStr.append(", L2_ID BIGINT");  
      selectStr.append(" AND L2_ID IS ?");
      insertStr.append(", ?");
    }
    createStr.append(", Length INT)");
    selectStr.append(" AND Length IS ?");
    insertStr.append(", ?)");
    logger.debug("Create statement:\n" + createStr.toString());
    stmt.execute(createStr.toString());
    // add the foreign keys
    String forKeyStr = "ALTER TABLE " + tableName(null, MENTIONS_TABLE_SUFFIX) + 
        " ADD CONSTRAINT " + tableName(null, "L1MenFK") + 
        " FOREIGN KEY (L1_ID) REFERENCES " + 
        tableName(null, L1_TABLE_SUFFIX) + "(ID)";
    logger.debug("Foreign key:\n" + forKeyStr);
    stmt.execute(forKeyStr);
    if(level2Used) {
      forKeyStr = "ALTER TABLE " + tableName(null, MENTIONS_TABLE_SUFFIX) + 
      " ADD CONSTRAINT " + tableName(null, "L2MenFK") + 
      " FOREIGN KEY (L2_ID) REFERENCES " + 
      tableName(null, L2_TABLE_SUFFIX) + "(ID)";
      logger.debug("Foreign key:\n" + forKeyStr);
      stmt.execute(forKeyStr);      
    }
    
    logger.debug("Select Mentions:\n" + selectStr.toString());
    mentionsSelectStmt = dbConnection.prepareStatement(selectStr.toString());
    mentionsInsertStmt = dbConnection.prepareStatement(insertStr.toString());
    
    // create all the indexes
    createIndexes(stmt);
    dbConnection.commit();
  }
  
  protected void createIndexes(Statement stmt) throws SQLException {
    // ////////////////////////////////
    // Level 1 table
    // ////////////////////////////////
    List<String> indexStatements = new LinkedList<String>();
    if(nominalFeatureNames != null && nominalFeatureNames.length > 0) {
      for(String aFeatureName : nominalFeatureNames) {
        // create the index statement
        indexStatements.add(
            "CREATE INDEX IF NOT EXISTS "+ tableName("IDX-", L1_TABLE_SUFFIX + aFeatureName)  + 
            " ON " + tableName(null, L1_TABLE_SUFFIX) + "(\"" + aFeatureName + "\")");
      }
    }
    for(String aStmt : indexStatements) {
      logger.debug("Index statement:\n" + aStmt);
      stmt.execute(aStmt);  
    }
    
    // ////////////////////////////////
    // Level 2 table
    // ////////////////////////////////
    if(level2Used) {
      indexStatements.clear();
      if(integerFeatureNames != null && integerFeatureNames.length > 0) {
        for(String aFeatureName : integerFeatureNames) {
          indexStatements.add(
              "CREATE INDEX IF NOT EXISTS " + tableName("IDX", L2_TABLE_SUFFIX + aFeatureName) +  
              " ON " + tableName(null, L2_TABLE_SUFFIX) + "(\"" + aFeatureName + "\")");
        }
      }
      if(floatFeatureNames != null && floatFeatureNames.length > 0) {
        for(String aFeatureName : floatFeatureNames) {
          indexStatements.add(
              "CREATE INDEX IF NOT EXISTS " + tableName("IDX", L2_TABLE_SUFFIX + aFeatureName) +  
              " ON " + tableName(null, L2_TABLE_SUFFIX) + "(\"" + aFeatureName + "\")");          
        }
      }
      if(textFeatureNames != null && textFeatureNames.length > 0) {
        for(String aFeatureName : textFeatureNames) {
          indexStatements.add(
                  "CREATE INDEX IF NOT EXISTS " + tableName("IDX", L2_TABLE_SUFFIX + aFeatureName) +  
                  " ON " + tableName(null, L2_TABLE_SUFFIX) + "(\"" + aFeatureName + "\")");
        }
      }
      // create the indexes
      for(String aStmt : indexStatements) {
        logger.debug("Index statement:\n" + aStmt);
        stmt.execute(aStmt);  
      }
    }
    
    // /////////////////////////////
    // Mentions table
    // /////////////////////////////
    // all other fields are either primary or foreign keys, so they get indexes
    String idxStmt = "CREATE INDEX IF NOT EXISTS " + 
        tableName("IDX", MENTIONS_TABLE_SUFFIX + "Length") + " ON " + 
        tableName(null, MENTIONS_TABLE_SUFFIX) + " (Length)";
    logger.debug("Index statement:\n" + idxStmt);
    stmt.execute(idxStmt);    
  }
  
  /**
   * Creates a table (index, etc.) name. Uses the value in 
   * {@link #tableBaseName} as a base name, to which it prepends the supplied 
   * prefix (if any), it appends the supplied suffix(if any). The constructed 
   * string is then surrounded with double quotes.  
   * @param suffix
   * @return
   */
  protected String tableName(String prefix, String suffix) {
    StringBuilder str = new StringBuilder("\"");
    if(prefix != null) str.append(prefix);
    str.append(tableBaseName);
    if(suffix != null) str.append(suffix);
    str.append("\"");
    return str.toString();
  }
  
  @Override
  public String[] getMentionUris(Annotation annotation, int length,
          Indexer indexer) {
    
    try {
      // find the level 1 ID
      Tag level1Tag = cache.getLevel1Tag(annotation);
      while(level1Tag.getId() == NO_ID){
        setStatementParameters(level1SelectStmt, annotation);
        ResultSet res = level1SelectStmt.executeQuery();
        if(res.next()) {
          // we have found the level 1 ID
          level1Tag.setId(res.getLong(1));
          // sanity check
          if(res.next()) throw new RuntimeException(
                  "Multiple Unique IDs foud in level 1 table for annotation " + 
                  annotation.toString());
        } else {
          // insert the new row
          setStatementParameters(level1InsertStmt, annotation);
          if(level1InsertStmt.executeUpdate() != 1) {
            // the update failed
            logger.error("Error while inserting into database. Annotation was lost!");
            return new String[]{};
          }
          dbConnection.commit();
        }
      }
      
      // find the Level-1 Mention ID (ignoring the L2 values)
      Tag mentionL1Tag = cache.getLevel3Tag(level1Tag, length);
      Tag mentionL2Tag = null;
      while(mentionL1Tag.getId() == NO_ID){
        mentionsSelectStmt.setLong(1, level1Tag.getId());
        if(level2Used) {
          mentionsSelectStmt.setNull(2, Types.BIGINT);
          mentionsSelectStmt.setInt(3,length);
        } else {
          mentionsSelectStmt.setInt(2,length);
        }
        ResultSet res = mentionsSelectStmt.executeQuery(); 
        if(res.next()) {
          // we have found the level 2 ID
          mentionL1Tag.setId(res.getLong(1));
          // sanity check
          if(res.next()) throw new RuntimeException(
                  "Multiple Unique IDs foud in mentions table for annotation " + 
                  "(of length "+ length + "):\n" +annotation.toString());
        } else {
          // insert the new row
          mentionsInsertStmt.setLong(1, level1Tag.getId());
          if(level2Used) {
            mentionsInsertStmt.setNull(2, Types.BIGINT);
            mentionsInsertStmt.setInt(3,length);
          } else {
            mentionsInsertStmt.setInt(2,length);
          }   
          if(mentionsInsertStmt.executeUpdate() != 1) {
            // the update failed
            logger.error("Error while inserting into database. Annotation was lost!");
            return new String[]{};
          }
          dbConnection.commit();
        }
      }
      
      if(level2Used){
        // find the level 2 ID
        Tag level2Tag = cache.getLevel2Tag(annotation, level1Tag);
        while(level2Tag.getId() == NO_ID){
          level2SelectStmt.setLong(1, level1Tag.getId());
          setStatementParameters(level2SelectStmt, annotation);

          ResultSet res = level2SelectStmt.executeQuery();
          if(res.next()) {
            // we have found the level 2 ID
            level2Tag.setId(res.getLong(1));
            // sanity check
            if(res.next()) throw new RuntimeException(
                    "Multiple Unique IDs foud in level 2 table for annotation " + 
                    annotation.toString());
          } else {
            // insert the new row
            level2InsertStmt.setLong(1, level1Tag.getId());
            setStatementParameters(level2InsertStmt, annotation);
            if(level2InsertStmt.executeUpdate() != 1) {
              // the update failed
              logger.error("Error while inserting into database. Annotation was lost!");
              return new String[]{};
            }
            dbConnection.commit();
          }
        }
        // find the Level-2 Mention ID
        mentionL2Tag = cache.getLevel3Tag(level2Tag, length);
        while(mentionL2Tag.getId() == NO_ID){
          mentionsSelectStmt.setLong(1,level1Tag.getId());
          mentionsSelectStmt.setLong(2, level2Tag.getId());
          mentionsSelectStmt.setInt(3,length);

          ResultSet res = mentionsSelectStmt.executeQuery();
          if(res.next()) {
            // we have found the level 2 ID
            mentionL2Tag.setId(res.getLong(1));
            // sanity check
            if(res.next()) throw new RuntimeException(
                    "Multiple Unique IDs foud in mentions table for annotation " + 
                    "(of length "+ length + "):\n" +annotation.toString());
          } else {
            // insert the new row
            mentionsInsertStmt.setLong(1, level1Tag.getId());
            mentionsInsertStmt.setLong(2, level2Tag.getId());
            mentionsInsertStmt.setInt(3,length);
            if(mentionsInsertStmt.executeUpdate() != 1) {
              // the update failed
              logger.error("Error while inserting into database. Annotation was lost!");
              return new String[]{};
            }
            dbConnection.commit();
          }
        }
      }
      
      // now we finally have the mention ID
      if(level2Used) {
        return new String[] {
                annotationType + ":" + mentionL1Tag.getId(), 
                annotationType + ":" + mentionL2Tag.getId()};
      } else {
        return new String[] {
                annotationType + ":" + mentionL1Tag.getId()};
      }
    } catch(SQLException e) {
      // something went bad: we can't fix it :(
      logger.error("Error while interogating database. Annotation was lost!", e);
      return new String[]{};
    }
  }

  /**
   * Sets all the values for a prepared statement (which must be one of the 
   * cached transient statements!)
   * For level-2 statements, it does not set the L1_ID parameter (i.e. it starts 
   * with the parameter at position 2).
   * @param stmt
   * @param annotation
   * @throws SQLException 
   */
  protected void setStatementParameters(PreparedStatement stmt, 
          Annotation annotation) throws SQLException {
    if(stmt == level1InsertStmt || stmt == level1SelectStmt) {
      if(nominalFeatureNames != null){
        int paramIdx = 1;
        for(String aFeatureName : nominalFeatureNames) {
          Object value = annotation.getFeatures().get(aFeatureName);
          if(value != null) {
            stmt.setString(paramIdx++, value.toString());
          } else {
            stmt.setNull(paramIdx++, Types.VARCHAR);
          }
        }
      }
    } else if(stmt == level2InsertStmt || stmt == level2SelectStmt) {
      if(!level2Used) throw new RuntimeException(
          "Was asked to populate a Level-2 statement, but Level-2 is not in use!");
      int paramIdx = 2;
      if(integerFeatureNames != null){
        for(String aFeatureName : integerFeatureNames) {
          Object valueObj = annotation.getFeatures().get(aFeatureName);
          Long value = null;
          if(valueObj != null){
            if(valueObj instanceof Number) {
              value = ((Number)valueObj).longValue();
            } else if(valueObj instanceof String) {
              try {
                value = Long.valueOf((String)valueObj);
              } catch(NumberFormatException e) {
                logger.warn("Value provided for feature \"" + aFeatureName
                                + "\" is a String that cannot be parsed to a Long. Value ("
                                + valueObj.toString() + ") will be ignored!");
              }
            } else {
              logger.warn("Value provided for feature \"" + aFeatureName
                      + "\" is not a subclass of java.lang.Number. Value ("
                      + valueObj.toString() + ") will be ignored!");
            }            
          }
          if(value != null) {
            stmt.setLong(paramIdx++, value);
          } else {
            stmt.setNull(paramIdx++, Types.BIGINT);
          }
        }
      }
      if(floatFeatureNames != null){
        for(String aFeatureName : floatFeatureNames) {
          Object valueObj = annotation.getFeatures().get(aFeatureName);
          Double value = null;
          if(valueObj != null){
            if(valueObj instanceof Number) {
              value = ((Number)valueObj).doubleValue();
            } else if(valueObj instanceof String) {
              try {
                value = Double.valueOf((String)valueObj);
              } catch(NumberFormatException e) {
                logger.warn("Value provided for feature \"" + aFeatureName
                                + "\" is a String that cannot be parsed to a Double. Value ("
                                + valueObj.toString() + ") will be ignored!");
              }
            } else {
              logger.warn("Value provided for feature \"" + aFeatureName
                      + "\" is not a subclass of java.lang.Number. Value ("
                      + valueObj.toString() + ") will be ignored!");
            }            
          }
          if(value != null) {
            stmt.setDouble(paramIdx++, value);
          } else {
            stmt.setNull(paramIdx++, Types.DOUBLE);
          }
        }  
      }
      if(textFeatureNames != null) {
        for(String aFeatureName : textFeatureNames) {
          Object valueObj = annotation.getFeatures().get(aFeatureName);
          if(valueObj != null) {
            stmt.setString(paramIdx++, valueObj.toString());
          } else {
            stmt.setNull(paramIdx++, Types.VARCHAR);
          }
        }
      }
    } else {
      throw new RuntimeException("Cannot recognise the the provided prepared statement!");
    }
  }

  @Override
  public List<Mention> getMentions(String annotationType,
          List<Constraint> constraints, QueryEngine engine) {
    if(!annotationType.equals(this.annotationType)) {
      throw new IllegalArgumentException("Wrong annotation type \"" + 
          annotationType + "\", this helper can only handle " + 
          this.annotationType + "!");
    }
    List<Mention> mentions = new LinkedList<Mention>();
    boolean hasLevel1Constraints = false;
    for(Constraint aConstraint : constraints) {
      if(nominalFeatureNameSet.contains(aConstraint.getFeatureName())) {
        hasLevel1Constraints = true;
        break;
      }
    }
    boolean hasLevel2Constraints = false;
    for(Constraint aConstraint : constraints) {
      if(nonNominalFeatureNameSet.contains(aConstraint.getFeatureName())) {
        hasLevel2Constraints = true;
        break;
      }
    }
    StringBuilder selectStr = new StringBuilder(
        "SELECT DISTINCT " + tableName(null, MENTIONS_TABLE_SUFFIX) + ".ID, " +
        tableName(null, MENTIONS_TABLE_SUFFIX) + ".Length FROM " + 
        tableName(null, MENTIONS_TABLE_SUFFIX));
    if(hasLevel1Constraints) {
      selectStr.append(", " + tableName(null, L1_TABLE_SUFFIX));
    }
    if(hasLevel2Constraints) {
      selectStr.append(", " + tableName(null, L2_TABLE_SUFFIX));
    }
    boolean firstWhere = true;
    // add constraints
    if(hasLevel1Constraints) {
      if(nominalFeatureNames != null) {
        for(String aFeatureName : nominalFeatureNames) {
          for(Constraint aConstraint : constraints) {
            if(aFeatureName.equals(aConstraint.getFeatureName())){
              if(firstWhere){
                firstWhere = false;
                selectStr.append(" WHERE");
              } else {
                selectStr.append(" AND");
              }
              selectStr.append(" " + tableName(null, L1_TABLE_SUFFIX) + ".\"" + 
                  aFeatureName + "\"");
              switch( aConstraint.getPredicate() ) {
                case EQ:
                  selectStr.append(" =");
                  break;
                case GT:
                  selectStr.append(" >");
                  break;
                case GE:
                  selectStr.append(" >=");
                  break;
                case LT:
                  selectStr.append(" <");
                  break;
                case LE:
                  selectStr.append(" <=");
                  break;
                case REGEX:
                  selectStr.append(" REGEXP");
              }
              if(aConstraint.getValue() instanceof String) {
                selectStr.append(" '" + aConstraint.getValue() +"'");
              } else if(aConstraint.getValue() instanceof String[]) {
                // this only makes sense for REGEX
                if(aConstraint.getPredicate() != ConstraintType.REGEX) {
                  throw new IllegalArgumentException("Got a two-valued constraint that is not a REGEXP!");
                }
                selectStr.append(" '(?" + ((String[])aConstraint.getValue())[1] + ")"
                        + ((String[])aConstraint.getValue())[0] + "'");
              }
            }
          }
        }        
      }
      // join L1 with Mentions
      selectStr.append(" AND " + tableName(null, L1_TABLE_SUFFIX) + ".ID = " +
          tableName(null, MENTIONS_TABLE_SUFFIX) + ".L1_ID");
      if(hasLevel2Constraints) {
        // join L1 with L2
        selectStr.append(" AND " + tableName(null, L1_TABLE_SUFFIX) + ".ID = " + 
                tableName(null, L2_TABLE_SUFFIX) + ".L1_ID");
      }
    }
    
    if(hasLevel2Constraints) {
      if(integerFeatureNames != null) {
        for(String aFeatureName : integerFeatureNames) {
          for(Constraint aConstraint : constraints) {
            if(aFeatureName.equals(aConstraint.getFeatureName())){
              if(firstWhere){
                firstWhere = false;
                selectStr.append(" WHERE");
              } else {
                selectStr.append(" AND");
              }
              selectStr.append(" " + tableName(null, L2_TABLE_SUFFIX) + ".\"" + 
                  aFeatureName + "\"");
              switch( aConstraint.getPredicate() ) {
                case EQ:
                  selectStr.append(" =");
                  break;
                case GT:
                  selectStr.append(" >");
                  break;
                case GE:
                  selectStr.append(" >=");
                  break;
                case LT:
                  selectStr.append(" <");
                  break;
                case LE:
                  selectStr.append(" <=");
                  break;
                case REGEX:
                  throw new IllegalArgumentException("Cannot use a REGEX predicate for numeric features!");
              }
              if(aConstraint.getValue() instanceof Number) {
                selectStr.append(" " + ((Number)aConstraint.getValue()).longValue());
              } else {
                selectStr.append(" " +  Long.parseLong(aConstraint.getValue().toString()));
              }
            }
          }
        }        
      }
      if(floatFeatureNames != null) {
        for(String aFeatureName : floatFeatureNames) {
          for(Constraint aConstraint : constraints) {
            if(aFeatureName.equals(aConstraint.getFeatureName())){
              if(firstWhere){
                firstWhere = false;
                selectStr.append(" WHERE");
              } else {
                selectStr.append(" AND");
              }
              selectStr.append(" " + tableName(null, L2_TABLE_SUFFIX) + ".\"" + 
                  aFeatureName + "\"");
              switch( aConstraint.getPredicate() ) {
                case EQ:
                  selectStr.append(" =");
                  break;
                case GT:
                  selectStr.append(" >");
                  break;
                case GE:
                  selectStr.append(" >=");
                  break;
                case LT:
                  selectStr.append(" <");
                  break;
                case LE:
                  selectStr.append(" <=");
                  break;
                case REGEX:
                  throw new IllegalArgumentException("Cannot use a REGEX predicate for numeric features!");
              }
              if(aConstraint.getValue() instanceof Number) {
                selectStr.append(" " + ((Number)aConstraint.getValue()).doubleValue());
              } else {
                selectStr.append(" " +  Double.parseDouble(aConstraint.getValue().toString()));
              }
            }
          }
        }        
      }
      if(textFeatureNames != null) {
        for(String aFeatureName : textFeatureNames) {
          for(Constraint aConstraint : constraints) {
            if(aFeatureName.equals(aConstraint.getFeatureName())){
              if(firstWhere){
                firstWhere = false;
                selectStr.append(" WHERE");
              } else {
                selectStr.append(" AND");
              }
              selectStr.append(" " + tableName(null, L2_TABLE_SUFFIX) + ".\"" + 
                  aFeatureName + "\"");
              switch( aConstraint.getPredicate() ) {
                case EQ:
                  selectStr.append(" =");
                  break;
                case GT:
                  selectStr.append(" >");
                  break;
                case GE:
                  selectStr.append(" >=");
                  break;
                case LT:
                  selectStr.append(" <");
                  break;
                case LE:
                  selectStr.append(" <=");
                  break;
                case REGEX:
                  selectStr.append(" REGEXP");
              }
              if(aConstraint.getValue() instanceof String) {
                selectStr.append(" '" + aConstraint.getValue() +"'");
              } else if(aConstraint.getValue() instanceof String[]) {
                // this only makes sense for REGEX
                if(aConstraint.getPredicate() != ConstraintType.REGEX) {
                  throw new IllegalArgumentException("Got a two-valued constraint that is not a REGEXP!");
                }
                selectStr.append(" '(?" + ((String[])aConstraint.getValue())[1] + ")"
                        + ((String[])aConstraint.getValue())[0] + "'");
              }
            }
          }
        }        
      }
      
      // join L2 with Mentions
      selectStr.append(" AND "+ tableName(null, L2_TABLE_SUFFIX) + ".ID = " + 
          tableName(null, MENTIONS_TABLE_SUFFIX) + ".L2_ID");
    }
    
    if(!hasLevel1Constraints && !hasLevel2Constraints && level2Used) {
      // no constraints at all!
      selectStr.append(" WHERE " + tableName(null, MENTIONS_TABLE_SUFFIX) + ".L2_ID IS NULL");
    }
    
    logger.debug("Select query:\n" + selectStr.toString());
    try {
      ResultSet res = dbConnection.createStatement().executeQuery(selectStr.toString());
      while(res.next()) {
        long id = res.getLong(1);
        int length = res.getInt(2);
        mentions.add(new Mention(annotationType + ":" + id, length));
      }
    } catch(SQLException e) {
      logger.error("DB error", e);
      throw new RuntimeException("DB error", e);
    }
    return mentions;
  }
  
  @Override
  public void documentEnd() {
    if(cache != null) {
      double l1ratio = cache.getL1CacheHitRatio();
      double l2ratio = cache.getL2CacheHitRatio();
      double l3ratio = cache.getL3CacheHitRatio();
      logger.debug("Cache size("
              + annotationType
              + "):"
              + cache.size()
              + ". Hit ratios L1, L2, L3: "
              + (Double.isNaN(l1ratio) ? "N/A" : percentFormat.format(l1ratio))
              + ", "
              + (Double.isNaN(l2ratio) ? "N/A" : percentFormat.format(l2ratio))
              + ", "
              + (Double.isNaN(l3ratio) ? "N/A" : percentFormat.format(l3ratio)));
      docsSoFar++;
      if(docsSoFar % 200 == 0) {
        // every 200 docs, adjust the cache sizes
        cache.adjustCacheSizes();
      }
    } else {
      logger.debug("Cache size(" + annotationType + "): null");
    }
  }

  @Override
  public void close(Indexer indexer) {
    try {
      dbConnection.close();
    } catch(SQLException e) {
      logger.warn("Error while closing DB COnnection", e);
    }
  }

  @Override
  public void close(QueryEngine qEngine) {
    try {
      dbConnection.close();
    } catch(SQLException e) {
      logger.warn("Error while closing DB COnnection", e);
    }
  }
  
  /**
   * test code: to be removed
   * @param args
   * @throws IndexException 
   */
  public static void main (String[] args) throws Exception{
    Gate.init();
    // load the tokeniser plugin
    Gate.getCreoleRegister().registerDirectories(new File("test/gate-home/plugins/ANNIE-tokeniser").toURI().toURL());
//    buildIndex();
    
    String queries[] = new String[] {
      "to {Measurement spec=\"2 to 20 seconds\"}"
//      ,"{Sentence}"
    };
    for(String aQuery : queries) queryIndex(aQuery);
    
    System.exit(0);
  }

  private static transient QueryEngine qEngine;
  
  private static void queryIndex(String query) throws Exception {
    if(qEngine == null)  qEngine = new QueryEngine(new File("index"));
    
    QueryRunner qRunner = qEngine.getQueryRunner(query);
    while(!qRunner.isComplete()) {
      if(qRunner.isActive()) {
        Thread.sleep(50);
      } else {
        qRunner.getMoreHits();
      }
    }
    System.out.println("Query \"" + query +"\" got " + qRunner.getHitsCount() + " hits:");
    for(Binding binding : qRunner.getHits(0, qRunner.getHitsCount() -1)){
      String[][] text = qEngine.getHitText(binding);
      for(int i = 0; i < text[0].length; i++){
        System.out.print(text[0][i]);
        if(text[1].length > i)System.out.print(text[1][i]);
      }
      System.out.println();
    }
  }
  
  /**
   * test code: to be removed
   */
  private static void buildIndex() throws Exception{
    // simple metadata helper for HTML tags
    OriginalMarkupMetadataHelper docHelper = new OriginalMarkupMetadataHelper(
        new HashSet<String>(Arrays.asList(
            new String[] {
              "b", "i", "li", "ol", "p", "sup", "sub", "u", "ul"})));
    IndexConfig config = new IndexConfig(
            new File("index"), 
            "mimir", 
            ANNIEConstants.TOKEN_ANNOTATION_TYPE, 
            "mimir", 
            new TokenIndexerConfig[]{
              new TokenIndexerConfig(
                      ANNIEConstants.TOKEN_STRING_FEATURE_NAME, 
                      DowncaseTermProcessor.getInstance()),
              new TokenIndexerConfig(
                      ANNIEConstants.TOKEN_CATEGORY_FEATURE_NAME, 
                      NullTermProcessor.getInstance()),
              new TokenIndexerConfig(
                      "root", 
                      NullTermProcessor.getInstance())
            }, 
            new SemanticIndexerConfig[]{
              new SemanticIndexerConfig(
                      new String[]{
                              "Measurement",
                              "PublicationAuthor", "PublicationDate",
                              "PublicationLocation", "PublicationPages",
                              "Reference", "Section", "Sentence"}, 
                      new SemanticAnnotationHelper[] {
                        new DBSemanticAnnotationHelper("Measurement",
                                      new String[]{"type", "dimension", "normalisedUnit"},
                                      null,
                                      new String[]{"normalisedValue", "normalisedMinValue", "normalisedMaxValue"},
                                      new String[] {"originalText", "originalUnit"}),
                        new DBSemanticAnnotationHelper("PublicationAuthor", null, null, null, null),                                  
                        new DBSemanticAnnotationHelper("PublicationDate", null, null, null, null),
                        new DBSemanticAnnotationHelper("PublicationLocation", null, null, null, null),                                  
                        new DBSemanticAnnotationHelper("PublicationPages", null, null, null, null),
                        new DBSemanticAnnotationHelper("Reference", new String[]{"type"}, null, null, null),                                  
                        new DBSemanticAnnotationHelper("Section", new String[]{"type"}, null, null, null),
                        new DBSemanticAnnotationHelper("Sentence", null, null, null, null)})
            }, 
            new DocumentMetadataHelper[] {docHelper}, 
            docHelper);
    Indexer indexer = new Indexer(config);
    
    String pathToZipFile = "test/data/gatexml-output.zip";
    File zipFile = new File(pathToZipFile);
    String fileURI = zipFile.toURI().toString();
    ZipFile zip = new ZipFile(pathToZipFile);
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while(entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if(entry.isDirectory()) {
        continue;
      }
      URL url = new URL("jar:" + fileURI + "!/" + entry.getName());
      Document doc = gate.Factory.newDocument(url, "UTF-8");
      indexer.indexDocument(doc);
    }
    indexer.close();
    System.out.println("Indexing complete");
  }
}
