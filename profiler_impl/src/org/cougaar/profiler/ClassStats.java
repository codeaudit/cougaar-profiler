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
  private long live;
  private long dead;

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
    return live;
  }

  /** The number of objects that have been garbage collected. */
  public final long getGarbageCollected() {
    return dead;
  }

  /** The sum of all sizes of live objects. */
  public long getSumSize() { return 0; }

  /** The maximum object size of live objects.  */
  public long getMaximumSize() { return 0; }

  /** The maximum object size ever observed (live or dead). */
  public long getMaximumEverSize() { return 0; }

  /** The sum of all capacities of live objects. */
  public long getSumCapacityCount() { return 0; }

  /** The maximum object capacity of live objects. */
  public long getMaximumCapacityCount() { return 0; }

  /** The maximum object capacity ever observed (live or dead). */
  public long getMaximumEverCapacityCount() { return 0; }

  /** The sum of all capacities of live objects. */
  public long getSumCapacityBytes() { return 0; }

  /** The maximum object capacity of live objects. */
  public long getMaximumCapacityBytes() { return 0; }

  /** The maximum object capacity ever observed (live or dead). */
  public long getMaximumEverCapacityBytes() { return 0; }

  void reset() {
  }
  void update(
      long size,
      long capacityCount,
      long capacityBytes,
      long maxSize,
      long maxCapacityCount,
      long maxCapacityBytes) {
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
    live++;
  }
  final void gc(InstanceStats is) {
    live--;
    dead++;
  }
  final void resetInstances() {
    live = 0;
  }

  public String toString() {
    return
      "(stats"+
      " live="+live+
      " dead="+dead+
      ")";
  }

  /** impl with additional size/capacity fields */
  private static class PlusSize extends ClassStats {

    private long sumSize;
    private long maxSize;
    private long maxEverSize;

    private long sumCapacityCount;
    private long maxCapacityCount;
    private long maxEverCapacityCount;

    private long sumCapacityBytes;
    private long maxCapacityBytes;
    private long maxEverCapacityBytes;

    public long getSumSize() {
      return sumSize;
    }
    public long getMaximumSize() {
      return maxSize;
    }
    public long getMaximumEverSize() {
      return maxEverSize;
    }

    public long getSumCapacityCount() {
      return sumCapacityCount;
    }
    public long getMaximumCapacityCount() {
      return maxCapacityCount;
    }
    public long getMaximumEverCapacityCount() {
      return maxEverCapacityCount;
    }

    public long getSumCapacityBytes() {
      return sumCapacityBytes;
    }
    public long getMaximumCapacityBytes() {
      return maxCapacityBytes;
    }
    public long getMaximumEverCapacityBytes() {
      return maxEverCapacityBytes;
    }

    void reset() {
      sumSize = 0;
      sumCapacityCount = 0;
      sumCapacityBytes = 0;
      maxSize = 0;
      maxCapacityCount = 0;
      maxCapacityBytes = 0;
    }
    void update(
        long size,
        long capacityCount,
        long capacityBytes,
        long maxSize,
        long maxCapacityCount,
        long maxCapacityBytes) {
      sumSize += size;
      if (this.maxSize < maxSize) {
        this.maxSize = maxSize;
        if (maxEverSize < maxSize) {
          maxEverSize = maxSize;
        }
      }
      sumCapacityCount += capacityCount;
      if (this.maxCapacityCount < maxCapacityCount) {
        this.maxCapacityCount = maxCapacityCount;
        if (maxEverCapacityCount < maxCapacityCount) {
          maxEverCapacityCount = maxCapacityCount;
        }
      }
      sumCapacityBytes += capacityBytes;
      if (this.maxCapacityBytes < maxCapacityBytes) {
        this.maxCapacityBytes = maxCapacityBytes;
        if (maxEverCapacityBytes < maxCapacityBytes) {
          maxEverCapacityBytes = maxCapacityBytes;
        }
      }
    }

    public String toString() {
      return
        "("+super.toString()+
        " size=("+
        "sum="+sumSize+
        " max="+maxSize+
        " maxEver="+maxEverSize+
        ")"+
        " capacity_count=("+
        "sum="+sumCapacityCount+
        " max="+maxCapacityCount+
        " maxEver="+maxEverCapacityCount+
        ")"+
        " capacity_bytes=("+
        "sum="+sumCapacityBytes+
        " max="+maxCapacityBytes+
        " maxEver="+maxEverCapacityBytes+
        ")"+
        ")";
    }
  }
}
