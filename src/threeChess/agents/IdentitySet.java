package threeChess.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A set implementation based on object identity that is written
 * to be fast to modify, iterate, and check for containing items.
 *
 * @author Paddy Lamont, 22494652
 */
public class IdentitySet<V> {

  private final int hashMask;
  private final List[] buckets;
  private final List<V> allValues;

  public IdentitySet(int bucketCount, int bucketCapacity) {
    if (bucketCount < 0 || ((bucketCount & (bucketCount - 1)) != 0))
      throw new IllegalArgumentException("buckets must be a power of 2");

    hashMask = bucketCount - 1;
    buckets = new List[bucketCount];
    for (int index = 0; index < bucketCount; ++index) {
      buckets[index] = new ArrayList<>(bucketCapacity);
    }
    allValues = new ArrayList<>(bucketCount * bucketCapacity);
  }

  /** @return a list of all values in this set. **/
  public List<V> getAllValues() {
    return allValues;
  }

  /** Removes all values from this set. **/
  public void clear() {
    for (List bucket : buckets) {
      bucket.clear();
    }
    allValues.clear();
  }

  /**
   * Adds {@param value} to this set.
   * Assumes {@param value} is not already in this set.
   */
  public void add(V value) {
    buckets[value.hashCode() & hashMask].add(value);
    allValues.add(value);
  }

  /** @return whether this set contains {@param value}. **/
  public boolean contains(V value) {
    List bucket = buckets[value.hashCode() & hashMask];
    for (int index = bucket.size(); --index >= 0;) {
      if (bucket.get(index) == value)
        return true;
    }
    return false;
  }
}
