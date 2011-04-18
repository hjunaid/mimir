/*
 *  TestBatchesMerger.java
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
 *  $Id$
 */
package gate.mimir.test;

import static org.junit.Assert.assertEquals;
import gate.Gate;

import java.io.File;

import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test/example code for combining a set of batches.
 */
public class TestBatchesMerger {
  
  private static final Logger log = Logger.getLogger(TestBatchesMerger.class);
  
  public static final int MAXIMUM_BATCHES_TO_COMBINE = 50;

  @BeforeClass
  public static void init() {
    Gate.setGateHome(new File("web-app/WEB-INF/gate.home"));
  }
  
  @Test
  public void testCalculateNumberOfStages()
  {
    log.debug("Testing calculation of number of stages..");
    assertEquals(1, calculateNumberOfStages(5, MAXIMUM_BATCHES_TO_COMBINE));
    assertEquals(2, calculateNumberOfStages(12, MAXIMUM_BATCHES_TO_COMBINE));
    assertEquals(3, calculateNumberOfStages(50, MAXIMUM_BATCHES_TO_COMBINE));
  }

  @Test
  public void testCombineBatches() {
    int batchCount = 1000;
    String[] batches = new String[batchCount];
    for(int i = 0; i < batchCount; i++) {
      batches[i] = "b-" + i;
    }
    combineBatches(batches, "out-final");
  }

  protected static void combineBatches(String[] inputBasenames, String outputBaseName) {
    int totalStages = calculateNumberOfStages(inputBasenames.length, MAXIMUM_BATCHES_TO_COMBINE);
    double closingProgress = 0;
    int stage = 0;
    // start the actual hierarchical combine
    while(inputBasenames.length > MAXIMUM_BATCHES_TO_COMBINE) {
      int remainder = inputBasenames.length % MAXIMUM_BATCHES_TO_COMBINE;
      int stageSteps = inputBasenames.length / MAXIMUM_BATCHES_TO_COMBINE;
      // each stage step results in one output; there may be enough space left
      // for the remainder
      if(stageSteps + remainder <= MAXIMUM_BATCHES_TO_COMBINE) {
        // the remaining input batches, will just be copied over to the output
      } else {
        // we need an extra stage step
        stageSteps++;
        remainder = 0;
      }
      String[] stageOutputFiles = new String[stageSteps + remainder];
      // create and run the sub-batches
      for(int i = 0; i < stageOutputFiles.length; i++) {
        if(i < stageOutputFiles.length - remainder) {
          // normal combine step
          int start = i * MAXIMUM_BATCHES_TO_COMBINE;
          int end =
                  Math.min((i + 1) * MAXIMUM_BATCHES_TO_COMBINE,
                          inputBasenames.length);
          String[] subInputBasenames = new String[end - start];
          System.arraycopy(inputBasenames, start, subInputBasenames, 0,
                  subInputBasenames.length);
          // XXX TODO: changeme!
          stageOutputFiles[i] = "batch-file-stage" + stage + "@" + i;
          combineBatchesNonRec(subInputBasenames, stageOutputFiles[i]);
        } else {
          // we are in the remainder are -> just copy the input to the output
          stageOutputFiles[i] =
                  inputBasenames[i - stageOutputFiles.length
                          + inputBasenames.length];
        }
        // update progress value
        closingProgress =
        // completed stages
                (stage / totalStages) +
                // current fraction
                        ((double)(i + 1) / stageOutputFiles.length)
                        // of one stage
                        / totalStages;
        log.debug("PROGRESS: " + closingProgress + " [total stages: " + totalStages + "]");
      }
      // prepare for next step
      inputBasenames = stageOutputFiles;
      stage++;
    }
    // at this point, we need to do the last combine
    combineBatchesNonRec(inputBasenames, outputBaseName);
    closingProgress = 1;
    log.debug("PROGRESS: " + closingProgress);
    
    assertEquals(1.0, closingProgress);
    // TODO probably needs more assertions to check the merged batches for correctness 
  }

  // calculate how many stages there will be
  private static int calculateNumberOfStages(int inputSize, int maxBatches) {
    int inputCount = inputSize;
    int totalStages = 1;
    while(inputCount > maxBatches) {
      totalStages++;
      int remainder = inputSize % maxBatches;
      inputCount = inputCount / maxBatches;
      if(inputCount + remainder <= maxBatches) {
        inputCount += remainder;
      } else {
        inputCount++;
      }
    }
    return totalStages;
  }

  protected static void combineBatchesNonRec(String[] inputBasenames, String outputBaseName) {
    StringBuilder str = new StringBuilder();
    str.append('[');
    boolean first = true;
    for(String aName : inputBasenames) {
      if(first) {
        first = false;
      } else {
        str.append(", ");
      }
      str.append(aName);
    }
    str.append("] -> " + outputBaseName);
    log.debug(str.toString());
  }
}
