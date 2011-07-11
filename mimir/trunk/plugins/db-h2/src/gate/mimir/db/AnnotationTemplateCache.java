/*
 *  AnnotationTemplateCache.java
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
 *  Valentin Tablan, 11 Feb 2011
 *  
 *  $Id$
 */
package gate.mimir.db;

import gate.Annotation;
import gate.mimir.AbstractSemanticAnnotationHelper;
import it.unimi.dsi.fastutil.objects.Object2ShortMap;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

public class AnnotationTemplateCache {
  /**
   * Interface for the types of values returned by this cache. All Tags have a
   * long ID (if the value is -1, then the ID is not yet set). They may also
   * hold some other data used internally by the cache.
   */
  public static interface Tag {
    /**
     * Value for a Tag's ID when no ID as been set yet.
     */
    public static final long NO_ID = -1;

    /**
     * Value for a Tag's ID when no ID exists (i.e. an alternative 
     * representation for when the Tag value should be null).
     */
    public static final long NULL_ID = -2;
    
    /**
     * Gets the ID associated with this tag. If the values returned is -1, then
     * the has no ID.
     * 
     * @return
     */
    long getId();

    /**
     * Sets the ID that this tag should have. When a cache miss occurs, a new
     * tag is created and returned. Such a tag has no ID (the id value is
     * {@link #NO_ID}). Client code can then set the ID to whatever desired
     * value.
     * 
     * @param newId
     */
    void setId(long newId);
  }

  /**
   * The key that goes into the level 1 cache map.
   */
  protected class NominalFeatures {
    public NominalFeatures(Annotation ann) {
      // nominalValues is guaranteed to be non-null and to have the same size
      // as owner.getNominalFeatureNames() (i.e. 0, if no nominal features)
      features = new short[nominalvalues.length];
      for(int i = 0; i < nominalvalues.length; i++) {
        String value =
                (String)ann.getFeatures()
                        .get(owner.getNominalFeatureNames()[i]);
        if(value == null) {
          features[i] = NULL;
        } else {
          features[i] = nominalvalues[i].getShort(value.toString());
          if(features[i] == NULL) {
            // new value -> create an ID for it
            features[i] = (short)nominalvalues[i].size();
            nominalvalues[i].put(value.toString(), features[i]);
          }
        }
      }
      // calculate the hashcode
      hashcode = Arrays.hashCode(features);
    }

    @Override
    public boolean equals(Object obj) {
      return Arrays.equals(features, ((NominalFeatures)obj).features);
    }

    private short[] features;

    private int hashcode;

    @Override
    public int hashCode() {
      return hashcode;
    }
  }

  /**
   * The type of keys that go into the level2 cache.
   */
  protected class NonNominalFeatures {
    public NonNominalFeatures(Annotation ann) {
      int length = 0;
      if(owner.getIntegerFeatureNames() != null)
        length += owner.getIntegerFeatureNames().length;
      if(owner.getFloatFeatureNames() != null)
        length += owner.getFloatFeatureNames().length;
      if(owner.getTextFeatureNames() != null)
        length += owner.getTextFeatureNames().length;
      if(owner.getUriFeatureNames() != null)
        length += owner.getUriFeatureNames().length;
      values = new Object[length];
      int i = 0;
      if(owner.getIntegerFeatureNames() != null) {
        for(String aFeature : owner.getIntegerFeatureNames()) {
          values[i++] = ann.getFeatures().get(aFeature);
        }
      }
      if(owner.getFloatFeatureNames() != null) {
        for(String aFeature : owner.getFloatFeatureNames()) {
          values[i++] = ann.getFeatures().get(aFeature);
        }
      }
      if(owner.getTextFeatureNames() != null) {
        for(String aFeature : owner.getTextFeatureNames()) {
          values[i++] = ann.getFeatures().get(aFeature);
        }
      }
      if(owner.getUriFeatureNames() != null) {
        for(String aFeature : owner.getUriFeatureNames()) {
          values[i++] = ann.getFeatures().get(aFeature);
        }
      }
      // cache the hash code.
      hashcode = Arrays.hashCode(values);
    }

    Object[] values;

    @Override
    public int hashCode() {
      return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
      return Arrays.equals(values, ((NonNominalFeatures)obj).values);
    }

    int hashcode;
  }

  /**
   * Type of values that go in the the Level1 cache
   */
  protected class Level2Cache implements Tag {
    public Level2Cache() {
      this.id = NO_ID;
      level2Cache = new LinkedHashMap<NonNominalFeatures, LongTag>() {
        @Override
        protected boolean removeEldestEntry(
                Entry<NonNominalFeatures, LongTag> eldest) {
          return size() > l2CacheSize;
        }
      };
    }

    long id;

    Map<NonNominalFeatures, LongTag> level2Cache;

    public long getId() {
      return id;
    }

    public void setId(long newId) {
      this.id = newId;
    }
  }

  /**
   * Type for value going in the level 2 cache (a simple wrapper for a long).
   */
  protected static class LongTag implements Tag {
    public LongTag() {
      this.id = NO_ID;
    }

    public long getId() {
      return id;
    }

    public void setId(long newId) {
      this.id = newId;
    }

    long id;
  }

  /**
   * Type for keys going in the level 3 cache
   */
  protected class MentionKey {
    public MentionKey(long l1Id, long l2id, int mentionLength) {
      this.level1Id = l1Id;
      this.level2Id = l2id;
      this.mentionLength = mentionLength;
      this.hashcode = Arrays.hashCode(new long[]{level1Id, level2Id, mentionLength});
    }

    @Override
    public int hashCode() {
      return hashcode;
    }

    @Override
    public boolean equals(Object obj) {
      MentionKey other = (MentionKey)obj;
      return other != null && 
             level1Id == other.level1Id && 
             level2Id == other.level2Id && 
             mentionLength == other.mentionLength;
    }

    long level1Id;
    
    long level2Id;

    int mentionLength;

    int hashcode;
  }

  private static final int DEFAULT_L1_SIZE = 512;

  private static final int DEFAULT_L2_SIZE = 64;

  private static Logger logger = Logger
          .getLogger(AnnotationTemplateCache.class);

  /**
   * There is only one L3 cache, so we can have it quite large.
   */
  private static final int DEFAULT_L3_SIZE = 100000;

  private static final short NULL = -1;

  public AnnotationTemplateCache(AbstractSemanticAnnotationHelper owner) {
    this.owner = owner;
    this.l1CacheSize = DEFAULT_L1_SIZE;
    this.l2CacheSize = DEFAULT_L2_SIZE;
    this.l3CacheSize = DEFAULT_L3_SIZE;
    int length =
            (owner.getNominalFeatureNames() == null) ? 0 : owner
                    .getNominalFeatureNames().length;
    nominalvalues = new Object2ShortMap[length];
    for(int i = 0; i < nominalvalues.length; i++) {
      nominalvalues[i] = new Object2ShortOpenHashMap<String>();
      nominalvalues[i].defaultReturnValue(NULL);
    }
    level1Cache =
            new LinkedHashMap<AnnotationTemplateCache.NominalFeatures, AnnotationTemplateCache.Level2Cache>() {
              @Override
              protected boolean removeEldestEntry(
                      Entry<NominalFeatures, Level2Cache> eldest) {
                return size() > l1CacheSize;
              }
            };
    level3Cache = new LinkedHashMap<AnnotationTemplateCache.MentionKey, Tag>() {
      @Override
      protected boolean removeEldestEntry(Entry<MentionKey, Tag> eldest) {
        return size() > l3CacheSize;
      }
    };
    l1CacheHits = 0;
    l1CacheMisses = 0;
    l2CacheHits = 0;
    l2CacheMisses = 0;
    l3CacheHits = 0;
    l3CacheMisses = 0;
  }

  protected Map<NominalFeatures, Level2Cache> level1Cache;

  protected Map<MentionKey, Tag> level3Cache;

  /**
   * The helper using this cache.
   */
  private AbstractSemanticAnnotationHelper owner;

  /**
   * Each element in this array is a map corresponding to one of the nominal
   * features in our owner. In each map, keys are feature values, values are IDs
   * associated with that value.
   */
  private Object2ShortMap<String> nominalvalues[];

  protected int l1CacheSize;

  protected int l2CacheSize;

  protected int l3CacheSize;

  private long l1CacheHits;

  private long l1CacheMisses;

  private long l2CacheHits;

  private long l2CacheMisses;

  private long l3CacheHits;

  private long l3CacheMisses;

  /**
   * Given an annotation, obtain the associated Level-1 {@link Tag}, from which
   * the ID can be retrieved. If a cache miss occurs, the returned tag will have
   * an ID value of {@link #NO_ID} - it is the responsibility of the client code
   * to obtain the correct ID and set it on the tag.
   */
  public Tag getLevel1Tag(Annotation ann) {
    // build the nominal features value
    NominalFeatures nomFeats = new NominalFeatures(ann);
    Level2Cache l1tag = level1Cache.get(nomFeats);
    if(l1tag == null) {
      l1CacheMisses++;
      l1tag = new Level2Cache();
      level1Cache.put(nomFeats, l1tag);
    } else {
      l1CacheHits++;
    }
    return l1tag;
  }

  /**
   * Given an annotation and the level-1 Tag obtained previously, obtain the
   * associated level-2 {@link Tag}, from which the ID can be retrieved. If a
   * cache miss occurs, the returned tag will have an ID value of {@link #NO_ID}
   * - it is the responsibility of the client code to obtain the correct ID and
   * set it on the tag.
   */
  public Tag getLevel2Tag(Annotation ann, Tag level1Tag) {
    Level2Cache l1Value = (Level2Cache)level1Tag;
    NonNominalFeatures nonNonFeats = new NonNominalFeatures(ann);
    LongTag level2Tag = (LongTag)l1Value.level2Cache.get(nonNonFeats);
    if(level2Tag == null) {
      l2CacheMisses++;
      level2Tag = new LongTag();
      l1Value.level2Cache.put(nonNonFeats, level2Tag);
    } else {
      l2CacheHits++;
    }
    return level2Tag;
  }

  /**
   * Given a Level-1 or Level-2 tag, and a mention length, obtain the Level-3
   * tag associated with the desired mention (the ID of the mention can be
   * obtained from the returned tag). If a cache miss occurs, the returned tag
   * will have an ID value of {@link #NO_ID} - it is the responsibility of the
   * client code to obtain the correct ID and set it on the tag.
   */
  public Tag getLevel3Tag(Tag level1tag, Tag level2tag, int length) {
    MentionKey key = new MentionKey(
        level1tag == null ? Tag.NULL_ID : level1tag.getId(),
        level2tag == null ? Tag.NULL_ID : level2tag.getId(),
        length);
    LongTag l3Tag = (LongTag)level3Cache.get(key);
    if(l3Tag == null) {
      l3CacheMisses++;
      l3Tag = new LongTag();
      level3Cache.put(key, l3Tag);
    } else {
      l3CacheHits++;
    }
    return l3Tag;
  }

  /**
   * Optimises the sizes used for level 1 and level 2 caches.
   */
  protected void adjustCacheSizes() {
    double l1hit = getL1CacheHitRatio();
    double l2hit = getL2CacheHitRatio();
    if(l1hit - l2hit > 0.2) {
      // L1 better than L2, by more than 20% -> increase L2
      if(l1CacheSize >= (level1Cache.size() * 2)) {
        // L1 at less than 50% capacity
        l1CacheSize = l1CacheSize / 2;
        l2CacheSize = l2CacheSize * 2;
        logger.info("Decreasing L1 cache size to " + l1CacheSize
                + "; Increasing L2 cache size to " + l2CacheSize + ".");
      }
    } else if(l2hit - l1hit > 0.2) {
      // L2 better than L1, by more than 20% -> increase L1
      if((l1CacheSize * 2) > level1Cache.size()) {
        l1CacheSize = l1CacheSize * 2;
        l2CacheSize = l2CacheSize / 2;
        logger.info("Increasing L1 cache size to " + l1CacheSize
                + "; Decreasing L2 cache size to " + l2CacheSize + ".");
      }
    } else {
      // nothing to adjust
    }
  }

  /**
   * Returns the current size of the Level1 cache.
   * 
   * @return an int value.
   */
  public int size() {
    return level1Cache.size();
  }

  public long getL1CacheHits() {
    return l1CacheHits;
  }

  public long getL1CacheMisses() {
    return l1CacheMisses;
  }

  public long getL2CacheHits() {
    return l2CacheHits;
  }

  public long getL2CacheMisses() {
    return l2CacheMisses;
  }

  public long getL3CacheHits() {
    return l3CacheHits;
  }

  public long getL3CacheMisses() {
    return l3CacheMisses;
  }

  /**
   * Gets the ratio of level 1 cache hits from all accesses.
   * 
   * @return
   */
  public double getL1CacheHitRatio() {
    if(l1CacheHits == 0 && l1CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)l1CacheHits / (l1CacheHits + l1CacheMisses);
    }
  }

  /**
   * Gets the ratio of level 2 cache hits from all accesses.
   * 
   * @return
   */
  public double getL2CacheHitRatio() {
    if(l2CacheHits == 0 && l2CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)l2CacheHits / (l2CacheHits + l2CacheMisses);
    }
  }

  /**
   * Gets the ratio of level 3 (mentions) cache hits from all accesses.
   * 
   * @return
   */
  public double getL3CacheHitRatio() {
    if(l3CacheHits == 0 && l3CacheMisses == 0) {
      return Double.NaN;
    } else {
      return (double)l3CacheHits / (l3CacheHits + l3CacheMisses);
    }
  }
}
