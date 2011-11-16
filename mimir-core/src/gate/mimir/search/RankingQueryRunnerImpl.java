/*
 *  RankingQueryRunnerImpl.java
 *
 *  Copyright (c) 1995-2010, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 16 Nov 201119/Jan/00
 *
 *  $Id$
 */
package gate.mimir.search;

import gate.mimir.index.IndexException;
import gate.mimir.search.query.Binding;
import gate.mimir.search.query.QueryExecutor;
import gate.mimir.search.query.QueryNode;
import gate.mimir.search.score.MimirScorer;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * A QueryRunner implementation that does ranking.
 */
public class RankingQueryRunnerImpl implements QueryRunner, Runnable {
  
  protected Logger logger =  Logger.getLogger(RankingQueryRunnerImpl.class);
  
  protected QueryExecutor queryExecutor;
  
  protected MimirScorer scorer;

  protected IntList documentIds;
  
  protected DoubleList documentScores;
  
  protected ObjectList<Binding[]> documentHits;

  protected IntList documentsByRank;
  
  protected Thread runningThread;
  
  /**
   * Creates a query runner in ranking mode.
   * @param qNode the {@link QueryNode} for the query being executed.
   * @param scorer the {@link MimirScorer} to use for ranking.
   * @param qEngine the {@link QueryEngine} used for executing the queries.
   * @throws IOException
   */
  public RankingQueryRunnerImpl(QueryExecutor executor, MimirScorer scorer) throws IOException {
    this.queryExecutor = executor;
    this.scorer = scorer;
    // start the search
    getMoreHits();
  }
  
  
  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getMoreHits()
   */
  @Override
  public synchronized void getMoreHits() throws IOException {
    if(runningThread != null){
      //we're already running -> ignore
      return;
    }
    // get a thread from the executor, if one exists
    if(queryExecutor.getQueryEngine().getExecutor() != null){
      queryExecutor.getQueryEngine().getExecutor().execute(this);  
    }else{
      new Thread(this, getClass().getName()).start();
    }
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#setStageMaxHits(int)
   */
  @Override
  public void setStageMaxHits(int maxHits) throws IOException {
    // TODO Auto-generated method stub
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#setStageTimeout(int)
   */
  @Override
  public void setStageTimeout(int timeout) throws IOException {
    // TODO Auto-generated method stub
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getHitsCount()
   */
  @Override
  public int getHitsCount() {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentsCount()
   */
  @Override
  public int getDocumentsCount() {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentID(int)
   */
  @Override
  public int getDocumentID(int index) throws IndexOutOfBoundsException {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentHitsCount(int)
   */
  @Override
  public int getDocumentHitsCount(int index) throws IndexOutOfBoundsException {
    // TODO Auto-generated method stub
    return 0;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getHits(int, int)
   */
  @Override
  public List<Binding> getHits(int startIndex, int hitCount)
    throws IndexOutOfBoundsException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getHitsForDocument(int)
   */
  @Override
  public List<Binding> getHitsForDocument(int documentId)
    throws IndexOutOfBoundsException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#renderDocument(int, java.lang.Appendable)
   */
  @Override
  public void renderDocument(int documentId, Appendable out)
    throws IOException, IndexException {
    // TODO Auto-generated method stub
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentURI(int)
   */
  @Override
  public String getDocumentURI(int documentID) throws IndexException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentTitle(int)
   */
  @Override
  public String getDocumentTitle(int documentID) throws IndexException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataField(int, java.lang.String)
   */
  @Override
  public Serializable getDocumentMetadataField(int docID, String fieldName)
    throws IndexException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentMetadataFields(int, java.util.Set)
   */
  @Override
  public Map<String, Serializable> getDocumentMetadataFields(int docID,
                                                             Set<String> fieldNames)
    throws IndexException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#getDocumentText(int, int, int)
   */
  @Override
  public String[][] getDocumentText(int documentID, int termPosition, int length)
    throws IndexException {
    // TODO Auto-generated method stub
    return null;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#isActive()
   */
  @Override
  public boolean isActive() {
    // TODO Auto-generated method stub
    return false;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#isComplete()
   */
  @Override
  public boolean isComplete() {
    // TODO Auto-generated method stub
    return false;
  }

  /* (non-Javadoc)
   * @see gate.mimir.search.QueryRunner#close()
   */
  @Override
  public void close() throws IOException {
    // TODO Auto-generated method stub
  }
  
  public void run() {
    //store the running thread
    synchronized(this) {
      if(runningThread != null){
        //some task is already running
        return;
      }
      runningThread = Thread.currentThread();  
    }
    try {
      if(scorer != null) {
        // we're doing ranking
        if(documentIds == null) {
          // first stage: collect all documents and their scores
          documentIds = new IntArrayList();
          documentScores = new DoubleArrayList();
          documentHits = new ObjectArrayList<Binding[]>();
          documentsByRank = new IntArrayList(
            queryExecutor.getQueryEngine().getRankedDocumentsCount());
          
          scorer.wrap(queryExecutor);
          int docId = scorer.nextDocument(-1);
          while(docId >= 0) {
            documentIds.add(docId);
            documentScores.add(scorer.score());
            docId = scorer.nextDocument(-1);
          }
        }
        // collect some more ranked documents
        int rankRangeStart = documentsByRank.size();
        int rankRangeEnd = documentsByRank.size() + 
            queryExecutor.getQueryEngine().getRankedDocumentsCount();
        int docsByRankWriteIndex = rankRangeStart;
        
        // the document with the minimum score already ranked.
        int smallestOldScoreDocId = rankRangeStart > 0 ? 
          documentIds.getInt(documentsByRank.getInt(rankRangeStart -1))
          : -1;
        // the score for the document above, which is a the upper limit for new scores
        double smallestOldScore = rankRangeStart > 0 ? 
            documentScores.getDouble(documentsByRank.getInt(rankRangeStart -1))
            : -1;
        for(int i = 0; i < documentIds.size(); i++) {
          int documentId = documentIds.getInt(i);
          double documentScore = documentScores.getDouble(i);
          // XxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXx
          // !!! Note that the documentsByRank is sorted in DESCENDING order now
          
          // the index for the document with the smallest score, 
          // from the new ones being ranked 
          int smallestDocIndex = rankRangeStart < documentsByRank.size() ?
              documentsByRank.getInt(rankRangeStart) : -1;
          // the smallest score that's been seen in this new round 
          double smallestNewScore = smallestDocIndex == -1 ? 0.0 : 
              documentScores.getDouble(smallestDocIndex);
          // we care about this new document if:
          // - we haven't collected enough documents yet, or
          // - it has a better score than the smallest score so far, but a 
          // smaller score than the maximum permitted score (i.e. it has not 
          // already been ranked)., or
          // - it's a new document with the same score as the largest permitted score
          if(docsByRankWriteIndex < rankRangeEnd 
             || 
             (documentScore > smallestNewScore && 
                 (smallestOldScore < 0 || documentScore < smallestOldScore)) 
             ||
             documentScore == smallestOldScore && documentId != smallestOldScoreDocId) {
            if(docsByRankWriteIndex == rankRangeEnd) {
              // we need to remove the  newly ranked document 
              // with the smallest score
              docsByRankWriteIndex--;
              documentsByRank.removeInt(docsByRankWriteIndex);
            }
            
            // find the rank for the new doc
            int rank = rankRangeStart;
            while(rank < documentsByRank.size() && 
                  documentScore < documentScores.getDouble(documentsByRank.getInt(rank))){
              rank++;
            }
            documentsByRank.add(rank, i);
            docsByRankWriteIndex++;
            // XxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXxXx
          }
          // collect the hits for the newly ranked docs
        }
        
      } else {
        // TODO: non-ranking mode implementation
      }
    } catch(IOException e) {
      //something went bad!
      logger.error("IOException during search!", e);
    }finally{
      //this search stage has completed -> clear the running thread
      synchronized(this) {
        runningThread = null;  
      }
    } 
  }
}
