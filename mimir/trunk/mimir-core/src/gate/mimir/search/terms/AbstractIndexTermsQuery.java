/*
 *  AbstractIndexTermsQuery.java
 *
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GATE Mímir (see http://gate.ac.uk/family/mimir.html), 
 *  and is free software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (also included with this distribution as file
 *  LICENCE-LGPL3.html).
 *
 *  Valentin Tablan, 17 Jul 2012
 *
 *  $Id$
 */
package gate.mimir.search.terms;

import gate.mimir.SemanticAnnotationHelper;
import gate.mimir.search.IndexReaderPool;
import gate.mimir.search.QueryEngine;
import gate.mimir.search.QueryEngine.IndexType;
import it.unimi.dsi.big.mg4j.search.DocumentIterator;
import it.unimi.dsi.big.mg4j.search.visitor.CounterCollectionVisitor;
import it.unimi.dsi.big.mg4j.search.visitor.CounterSetupVisitor;
import it.unimi.dsi.big.mg4j.search.visitor.TermCollectionVisitor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Base class for terms queries that use an MG4J direct index for their search.
 */
public abstract class AbstractIndexTermsQuery extends AbstractTermsQuery {
  
  /**
   * The name of the subindex in which the terms are sought. Each Mímir 
   * index includes multiple sub-indexes (some storing tokens, other storing 
   * annotations), identified by a name. For token indexes, the index name is
   * the name of the token feature being indexed; for annotation indexes, the
   * index name is the annotation type.
   */
  protected final String indexName; 

  /**
   * The type of index being searched (tokens or annotations).
   */
  protected final IndexType indexType;
  
  /**
   * The direct index used for executing the query. This value is non-null only 
   * if a direct index was configured as part of the Mímir index being searched.
   */
  protected IndexReaderPool directIndexPool;
  
  /**
   * The indirect index used for executing the query.
   */
  protected IndexReaderPool indirectIndexPool;
  
  /**
   * The semantic annotation helper for the correct annotation type (as 
   * given by {@link #indexName}), if {@link #indexType} is 
   * {@link IndexType#ANNOTATIONS}, <code>null</code> otherwise. 
   */
  protected SemanticAnnotationHelper annotationHelper;
  
  /**
   * Should stop words be filtered out of the results? 
   */
  protected boolean stopWordsBlocked = false;
  
  /**
   * Stop words set used for filtering out stop words. See 
   * {@link #stopWordsBlocked}. 
   */
  protected Set<String> stopWords = null;
  
  /**
   * The query engine used to execute this query.
   */
  protected QueryEngine engine;

  /**
   * The default set of stop words.
   */
  public static final String[] DEFAULT_STOP_WORDS = new String[] {
      ",", ".", "?", "!", ":", ";", "#", "~", "^", "@", "%", "&", "(", ")", 
      "[", "]", "{", "}", "|", "\\", "<", ">", "-", "+", "*", "/", "=", 
      "a", "about", "above", "above", "across", "after", "afterwards", "again", 
      "against", "all", "almost", "alone", "along", "already", "also",
      "although","always","am","among", "amongst", "amoungst", "amount", "an", 
      "and", "another", "any","anyhow","anyone","anything","anyway", "anywhere",
      "are", "around", "as",  "at", "back","be","became", "because", "become",
      "becomes", "becoming", "been", "before", "beforehand", "behind", "being", 
      "below", "beside", "besides", "between", "beyond", "bill", "both", 
      "bottom","but", "by", "call", "can", "cannot", "cant", "co", "con", 
      "could", "couldnt", "cry", "de", "describe", "detail", "do", "done", 
      "down", "due", "during", "each", "eg", "eight", "either", "eleven",
      "else", "elsewhere", "empty", "enough", "etc", "even", "ever", "every", 
      "everyone", "everything", "everywhere", "except", "few", "fifteen", 
      "fify", "fill", "find", "fire", "first", "five", "for", "former", 
      "formerly", "forty", "found", "four", "from", "front", "full", "further", 
      "get", "give", "go", "had", "has", "hasnt", "have", "he", "hence", "her", 
      "here", "hereafter", "hereby", "herein", "hereupon", "hers", "herself", 
      "him", "himself", "his", "how", "however", "hundred", "ie", "if", "in", 
      "inc", "indeed", "interest", "into", "is", "it", "its", "itself", "keep", 
      "last", "latter", "latterly", "least", "less", "ltd", "made", "many", 
      "may", "me", "meanwhile", "might", "mill", "mine", "more", "moreover", 
      "most", "mostly", "move", "much", "must", "my", "myself", "name", 
      "namely", "neither", "never", "nevertheless", "next", "nine", "no", 
      "nobody", "none", "noone", "nor", "not", "nothing", "now", "nowhere", 
      "of", "off", "often", "on", "once", "one", "only", "onto", "or", "other",
      "others", "otherwise", "our", "ours", "ourselves", "out", "over", "own",
      "part", "per", "perhaps", "please", "put", "rather", "re", "same", "see", 
      "seem", "seemed", "seeming", "seems", "serious", "several", "she", 
      "should", "show", "side", "since", "sincere", "six", "sixty", "so", 
      "some", "somehow", "someone", "something", "sometime", "sometimes", 
      "somewhere", "still", "such", "system", "take", "ten", "than", "that", 
      "the", "their", "them", "themselves", "then", "thence", "there", 
      "thereafter", "thereby", "therefore", "therein", "thereupon", "these", 
      "they", "thickv", "thin", "third", "this", "those", "though", "three", 
      "through", "throughout", "thru", "thus", "to", "together", "too", "top", 
      "toward", "towards", "twelve", "twenty", "two", "un", "under", "until", 
      "up", "upon", "us", "very", "via", "was", "we", "well", "were", "what", 
      "whatever", "when", "whence", "whenever", "where", "whereafter", 
      "whereas", "whereby", "wherein", "whereupon", "wherever", "whether", 
      "which", "while", "whither", "who", "whoever", "whole", "whom", "whose",
      "why", "will", "with", "within", "without", "would", "yet", "you", "your",
      "yours", "yourself", "yourselves"
  };
  
  /**
   * 
   * @param indexName The name of the subindex in which the terms are sought. 
   *    Each Mímir index includes multiple sub-indexes (some storing tokens, 
   *    other storing annotations), identified by a name. For token indexes, 
   *    the index name is the name of the token feature being indexed; for 
   *    annotation indexes, the index name is the annotation type.    
   * @param indexType The type of index to be searched (tokens or annotations).
   * 
   * @param stringsEnabled should term strings be obtained?
   * 
   * @param countsEnabled should term counts be obtained?
   * 
   * @param limit the maximum number of terms to return.
   */
  public AbstractIndexTermsQuery(String indexName, IndexType indexType, 
      boolean stringsEnabled, boolean countsEnabled, int limit) {
    super(stringsEnabled, countsEnabled, limit);
    this.indexName = indexName;
    this.indexType = indexType;
  }

  /**
   * Populates the internal state by obtaining references to the direct and
   * indirect indexes from the {@link QueryEngine}.
   *   
   * @param engine the {@link QueryEngine} used to execute this query.
   * 
   * @throws IllegalArgumentException if the index represented by the provided
   * query engine does not have a direct index for the given sub-index (as 
   * specified by {@link #indexType} and {@link #indexName}).
   */
  protected void prepare(QueryEngine engine) {
    this.engine = engine;
    switch(indexType){
      case ANNOTATIONS:
        directIndexPool = engine.getAnnotationDirectIndex(indexName);
        indirectIndexPool = engine.getAnnotationIndex(indexName);
        annotationHelper = engine.getAnnotationHelper(indexName);
        break;
      case TOKENS:
        directIndexPool = engine.getTokenDirectIndex(indexName);
        indirectIndexPool = engine.getTokenIndex(indexName);
        break;
      default:
        throw new IllegalArgumentException("Invalid index type: " + 
            indexType.toString());
    }
    if(directIndexPool == null) {
      throw new IllegalArgumentException("This type of query requires a " +
      		"direct index, but one was not found for (" + 
          indexType.toString().toLowerCase() + ") sub-index \"" + 
      		indexName + "\"");
    }
  }
  
  protected TermsResultSet buildResultSet(DocumentIterator documentIterator) 
      throws IOException {
    // prepare local data
    LongArrayList termIds = new LongArrayList();
    ObjectArrayList<String> termStrings = stringsEnabled ? 
        new ObjectArrayList<String>() : null;
    IntArrayList termCounts = countsEnabled ? new IntArrayList() : null;
    TermCollectionVisitor termCollectionVisitor = null;
    CounterSetupVisitor counterSetupVisitor = null;
    CounterCollectionVisitor counterCollectionVisitor = null;
    if(countsEnabled) {
      termCollectionVisitor = new TermCollectionVisitor();
      counterSetupVisitor = new CounterSetupVisitor( termCollectionVisitor );
      counterCollectionVisitor = new CounterCollectionVisitor( counterSetupVisitor );  
      termCollectionVisitor.prepare();
      documentIterator.accept( termCollectionVisitor );
      counterSetupVisitor.prepare();
      documentIterator.accept( counterSetupVisitor ); 
    }
    if(stopWordsBlocked) {
      // use the default list if no custom one was set
      if(stopWords == null) setStopWords(DEFAULT_STOP_WORDS);
    }
    
    long termId = documentIterator.nextDocument();
    terms:while(termId != DocumentIterator.END_OF_LIST && termId != -1 &&
        termIds.size() < limit) {
      String termString = null;
      // get the term string, if required
      if(// if stop words are blocked, we need to check the term string  
         isStopWordsBlocked() || 
         // if strings enabled, we need the term string so we can return it
         stringsEnabled ||
         // if annotation index, we need the term to check for the right 
         // annotation type inside the index (which may include multiple types)
         indexType == IndexType.ANNOTATIONS) {
        termString = indirectIndexPool.getTerm(termId);
      }
      if(stopWordsBlocked && stopWords.contains(termString)) {
        // skip this term
        termId = documentIterator.nextDocument();
        continue terms;
      }
      if(indexType == IndexType.ANNOTATIONS && 
         (!annotationHelper.isMentionUri(termString))){
        // skip this term
        termId = documentIterator.nextDocument();
        continue terms;
      }
      termIds.add(termId);
      if(countsEnabled){
        counterSetupVisitor.clear();
        documentIterator.acceptOnTruePaths( counterCollectionVisitor );
        int count = 0;
        for (int aCount : counterSetupVisitor.count ) count +=  aCount;
        termCounts.add(count);
      }
      if(stringsEnabled){
        termStrings.add(termString);
      }
      termId = documentIterator.nextDocument();
    }
    // construct the result
    return new TermsResultSet(termIds.toLongArray(),
      stringsEnabled ? termStrings.toArray(new String[termStrings.size()]) : null,
      null,
      countsEnabled ? termCounts.toIntArray() : null);
  }

  /**
   * Should stop words be filtered out from the results? Defaults to 
   * <code>false</code>.
   * 
   * @return the stopWordsBlocked
   */
  public boolean isStopWordsBlocked() {
    return stopWordsBlocked;
  }

  /**
   * Enables or disables the filtering of stop words from the results. If a 
   * custom list of stop words has been set (by calling 
   * {@link #setStopWords(String[])}) then it is used, otherwise the 
   * {@link #DEFAULT_STOP_WORDS} list is used.
   * 
   * @param stopWordsBlocked the stopWordsBlocked to set
   */
  public void setStopWordsBlocked(boolean stopWordsBlocked) {
    this.stopWordsBlocked = stopWordsBlocked;
  }

  /**
   * Gets the current custom list of stop words.
   * @return the stopWords
   */
  public Set<String> getStopWords() {
    return stopWords;
  }

  public void setStopWords(Set<String> stopWords) {
    this.stopWords = new HashSet<String>(stopWords);
  }
  
  /**
   * Sets the custom list of stop words that should be blocked from query 
   * results. The actual blocking also needs to be enabled by calling 
   * {@link #setStopWordsBlocked(boolean)}. 
   * If this array is set to <code>null<code>, then the 
   * {@link #DEFAULT_STOP_WORDS} are used.
   * 
   * @param stopWords the stopWords to set
   */
  public void setStopWords(String[] stopWords) {
    this.stopWords = new HashSet<String>(stopWords.length);
    for(String sw : stopWords) this.stopWords.add(sw); 
  }
  
}
