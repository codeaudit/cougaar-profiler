/*
 * <copyright>
 *  Copyright 1997-2003 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */
package org.cougaar.profiler;

/**
 * Class data for objects of the same type.
 */
public class ClassStats {
  private long      instances;
  private long      collected;

  private ClassStats() { }

  static final ClassStats newClassStats(boolean plusSize) {
    if (plusSize) {
      return new PlusSize();
    } else {
      return new ClassStats();
    }
  }

  /** The number of live objects */
  public final long getInstances() {
    return instances;
  }

  /** The number of objects that have been garbage collected. */
  public final long getGarbageCollected() {
    return collected;
  }

  /** The sum of all sizes of live objects. */
  public long getTotalSize() { return 0; }

  /** The maximum object size of live objects.  */
  public long getMaximumSize() { return 0; }

  /** The maximum object size ever observed (live or dead). */
  public long getMaximumSizeEver() { return 0; }

  /** The sum of all capacities of live objects. */
  public long getTotalCapacity() { return 0; }

  /** The maximum object capacity of live objects. */
  public long getMaximumCapacity() { return 0; }

  /** The maximum object capacity ever observed (live or dead). */
  public long getMaximumCapacityEver() { return 0; }

  void reset() {
    instances = 0;
  }
  void update(
    long size,
    long maxSize,
    long capacity,
    long maxCapacity) {
  }

  // allocate/gc an instance.  We pass the instance stats to allow
  // future enhancements.
  //
  // For example, we could use the instance timestamp to build a
  // history and answer questions like:
  //   "How many of the objects allocated 10-5 minutes
  //    ago how been gc'ed?" 
  // or we could calculate the life expectancy for each unique
  // allocation stacktrace, etc.
  final void allocate(InstanceStats is) {
    instances++;
  }
  final void gc(InstanceStats is) {
    instances--;
    collected++;
  }

  public String toString() {
    return
      "(stats"+
      " instances="+instances+
      " collected="+collected+
      ")";
  }

  /** impl with additional size/capacity fields */
  private static class PlusSize extends ClassStats {

    private long totalSize;
    private long maxSize;
    private long maxSizeEver;
    private long totalCapacity;
    private long maxCapacity;
    private long maxCapacityEver;

    public long getTotalSize() { return totalSize; }
    public long getMaximumSize() { return maxSize; }
    public long getMaximumSizeEver() { return maxSizeEver; }
    public long getTotalCapacity() { return totalCapacity; }
    public long getMaximumCapacity() { return maxCapacity; }
    public long getMaximumCapacityEver() { return maxCapacityEver; }

    void reset() {
      super.reset();
      totalSize = 0;
      totalCapacity = 0;
      maxCapacity = 0;
      maxCapacity = 0;
    }

    void update(
        long size,
        long maxSize,
        long capacity,
        long maxCapacity) {
      totalSize += size;
      totalCapacity += capacity;
      if (this.maxSize < maxSize) {
        this.maxSize = maxSize;
        if (maxSizeEver < maxSize) {
          maxSizeEver = maxSize;
        }
      }
      if (this.maxCapacity < maxCapacity) {
        this.maxCapacity = maxCapacity;
        if (maxCapacityEver < maxCapacity) {
          maxCapacityEver = maxCapacity;
        }
      }
    }

    public String toString() {
      return
        "("+super.toString()+
        " totalSize="+totalSize+
        " maxSize="+maxSize+
        " maxSizeEver="+maxSizeEver+
        " totalCapacity="+totalCapacity+
        " maxCapacity="+maxCapacity+
        " maxCapacityEver="+maxCapacityEver+
        ")";
    }
  }
}
