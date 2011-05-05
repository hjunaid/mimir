package gate.mimir.sesame;


public class SesameUtils {
	/**
	   * Key used to retrieve the Sesame RepositoryManager source from the 
	   * {@link IndexConfig#getContext()} context map.
	   */
	  public static final String SESAME_RMANAGER_KEY = "repositoryManager";

	  /**
	   * Key used to retrieve an Integer value, representing the number of
	   * Repository connections.
	   */
	  public static final String SESAME_CONNECTION_COUNT_KEY = "sesameRepositoryConnectionCount";
	  
	  /**
	   * The name of the index subdirectory storing Sesame repository data.
	   */
	  public static final String SESAME_INDEX_DIRNAME = "sesame";
	  
	  /**
	   * The name of the config file for the Sesame repository
	   */
	  public static final String SESAME_CONFIG_FILENAME = ".ttl";
	  
	  /**
	   * The key to retrieve the name(id) of the Sesame repository
	   */
	  public static final String SESAME_REPOSITORY_NAME_KEY = "sesameRepository";
	  
	  /**
	   * The namespace used for Mimir-specific URIs.
	   */
	  public static final String MIMIR_NAMESPACE = "urn:mimir:";

	  /**
	   * Predicate string.
	   */
	  public static final String HAS_FEATURES = "hasFeatures";

	  /**
	   * Predicate string.
	   */
	  public static final String HAS_MENTION = "hasMention";

	  /**
	   * Predicate string.
	   */
	  public static final String HAS_LENGTH = "hasLength";
	
}
