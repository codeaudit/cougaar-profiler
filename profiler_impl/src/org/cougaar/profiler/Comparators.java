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

import java.util.Arrays;
import java.util.Comparator;

/**
 * Utility classes for comparing InstanceStats instances,
 * such as "sort by allocation time".
 */
public class Comparators {

  private Comparators() { }

  public static final boolean INCREASING = true;
  public static final boolean DECREASING = false;

  /**
   * Sort a list of InstanceStats.
   *
   * For example, to sort by "decreasing time" to see
   * the oldest instances.
   */
  public static final void sort(
      Object[] objs, boolean increasing, String name) {
    Comparator comp = getComparator(increasing, name);
    if (comp != null) {
      Arrays.sort(objs, comp);
    }
  }

  public static final String TIME = "time";
  public static final String COMPARE_TO = "compareTo";
  public static final String HASHCODE = "hashcode";
  public static final String SIZE = "size";
  public static final String MAX_SIZE = "max_size";
  public static final String CAPACITY_COUNT = "capacity_count";
  public static final String MAX_CAPACITY_COUNT = "max_capacity_count";
  public static final String CAPACITY_BYTES = "capacity_bytes";
  public static final String MAX_CAPACITY_BYTES = "max_capacity_bytes";
  public static final String EXCESS_CAPACITY = "excess_capacity";
  public static final String GROUP_COUNT = "group_count";

  /** get comparator names */
  public static final String[] getNames() {
    return new String[] {
      TIME,
      COMPARE_TO,
      HASHCODE,
      SIZE,
      MAX_SIZE,
      CAPACITY_COUNT,
      MAX_CAPACITY_COUNT,
      EXCESS_CAPACITY,
      // GROUP_COUNT,
    };
  }

  /** get comparator by name */
  public static final Comparator getComparator(
      boolean increasing, String name) {
    Comparator ret;
    // ugly switch:
    if (name == null) {
      ret = null;
    } else if (name.equals(TIME)) {
      if (increasing) {
        ret = INCREASING_TIME_COMPARATOR;
      } else {
        ret = DECREASING_TIME_COMPARATOR;
      }
    } else if (name.equals(COMPARE_TO)) {
      if (increasing) {
        ret = INCREASING_COMPARE_TO_COMPARATOR;
      } else {
        ret = DECREASING_COMPARE_TO_COMPARATOR;
      }
    } else if (name.equals(HASHCODE)) {
      if (increasing) {
        ret = INCREASING_HASHCODE_COMPARATOR;
      } else {
        ret = DECREASING_HASHCODE_COMPARATOR;
      }
    } else if (name.equals(SIZE)) {
      if (increasing) {
        ret = INCREASING_SIZE_COMPARATOR;
      } else {
        ret = DECREASING_SIZE_COMPARATOR;
      }
    } else if (name.equals(MAX_SIZE)) {
      if (increasing) {
        ret = INCREASING_MAX_SIZE_COMPARATOR;
      } else {
        ret = DECREASING_MAX_SIZE_COMPARATOR;
      }
    } else if (name.equals(CAPACITY_COUNT)) {
      if (increasing) {
        ret = INCREASING_CAPACITY_COUNT_COMPARATOR;
      } else {
        ret = DECREASING_CAPACITY_COUNT_COMPARATOR;
      }
    } else if (name.equals(MAX_CAPACITY_COUNT)) {
      if (increasing) {
        ret = INCREASING_MAX_CAPACITY_COUNT_COMPARATOR;
      } else {
        ret = DECREASING_MAX_CAPACITY_COUNT_COMPARATOR;
      }
    } else if (name.equals(CAPACITY_BYTES)) {
      if (increasing) {
        ret = INCREASING_CAPACITY_BYTES_COMPARATOR;
      } else {
        ret = DECREASING_CAPACITY_BYTES_COMPARATOR;
      }
    } else if (name.equals(MAX_CAPACITY_BYTES)) {
      if (increasing) {
        ret = INCREASING_MAX_CAPACITY_BYTES_COMPARATOR;
      } else {
        ret = DECREASING_MAX_CAPACITY_BYTES_COMPARATOR;
      }
    } else if (name.equals(EXCESS_CAPACITY)) {
      if (increasing) {
        ret = INCREASING_EXCESS_CAPACITY_COMPARATOR;
      } else {
        ret = DECREASING_EXCESS_CAPACITY_COMPARATOR;
      }
    } else if (name.equals(GROUP_COUNT)) {
      if (increasing) {
        ret = INCREASING_GROUP_COUNT_COMPARATOR;
      } else {
        ret = DECREASING_GROUP_COUNT_COMPARATOR;
      }
    } else {
      ret = null;
    }
    return ret;
  }

  public static final class ReverseComparator implements Comparator {
    private final Comparator c;
    public ReverseComparator(Comparator c) {
      this.c = c;
    }
    public int compare(Object o1, Object o2) {
      return c.compare(o2, o1);
    }
  }

  /** oldest time first */
  public static final Comparator DECREASING_TIME_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        long t1 = is1.getAllocationTime();
        long t2 = is2.getAllocationTime();
        if (t1 > t2) {
          return 1;
        } else if (t1 < t2) {
          return -1;
        } else {
          return 0;
        }
      }
    };
  public static final Comparator INCREASING_TIME_COMPARATOR =
    new ReverseComparator(DECREASING_TIME_COMPARATOR);

  /** reverse of the instance's "compareTo" (must be Comparable) */
  public static final Comparator DECREASING_COMPARE_TO_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        Object obj1 = is1.get();
        Object obj2 = is2.get();
        if (obj1 instanceof Comparable &&
            obj2 instanceof Comparable) {
          Comparable c1 = (Comparable) obj1;
          Comparable c2 = (Comparable) obj2;
          // watch out for concurrent mods!
          try {
            return c2.compareTo(c1);
          } catch (Exception e) {
            return 1;
          }
        }
        // either not comparable or gc'ed
        if (obj1 == null) {
          if (obj2 == null) {
            return 0;
          } else {
            return -1;
          }
        } else {
          if (obj2 == null) {
            return 1;
          } else {
            return 0;
          }
        } 
      }
    };
  public static final Comparator INCREASING_COMPARE_TO_COMPARATOR =
    new ReverseComparator(DECREASING_COMPARE_TO_COMPARATOR);

  /** largest hashcode first (to examine hash balance) */
  public static final int NULL_HASHCODE = -123450;
  public static final int FAILED_HASHCODE = -123451;
  public static final Comparator DECREASING_HASHCODE_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        Object obj1 = is1.get();
        Object obj2 = is2.get();
        int hc1;
        int hc2;
        if (obj1 == null) {
          hc1 = NULL_HASHCODE;
        } else {
          // watch out for concurrent mods!
          try {
            hc1 = obj1.hashCode();
          } catch (Exception e) {
            hc1 = FAILED_HASHCODE;
          }
        }
        if (obj2 == null) {
          hc2 = NULL_HASHCODE;
        } else {
          try {
            hc2 = obj2.hashCode();
          } catch (Exception e) {
            hc2 = FAILED_HASHCODE;
          }
        }
        return hc2 - hc1;
      }
    };
  public static final Comparator INCREASING_HASHCODE_COMPARATOR =
    new ReverseComparator(DECREASING_HASHCODE_COMPARATOR);

  /** largest size first */
  public static final Comparator DECREASING_SIZE_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        int i1 = is1.getSize();
        int i2 = is2.getSize();
        return i2 - i1;
      }
    };
  public static final Comparator INCREASING_SIZE_COMPARATOR =
    new ReverseComparator(DECREASING_SIZE_COMPARATOR);

  /** largest max_size first */
  public static final Comparator DECREASING_MAX_SIZE_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        int i1 = is1.getMaximumSize();
        int i2 = is2.getMaximumSize();
        return i2 - i1;
      }
    };
  public static final Comparator INCREASING_MAX_SIZE_COMPARATOR =
    new ReverseComparator(DECREASING_MAX_SIZE_COMPARATOR);

  /** largest capacity first */
  public static final Comparator DECREASING_CAPACITY_COUNT_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        int i1 = is1.getCapacityCount();
        int i2 = is2.getCapacityCount();
        return i2 - i1;
      }
    };
  public static final Comparator INCREASING_CAPACITY_COUNT_COMPARATOR =
    new ReverseComparator(DECREASING_CAPACITY_COUNT_COMPARATOR);

  /** largest max_capacity first */
  public static final Comparator DECREASING_MAX_CAPACITY_COUNT_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        int i1 = is1.getMaximumCapacityCount();
        int i2 = is2.getMaximumCapacityCount();
        return i2 - i1;
      }
    };
  public static final Comparator INCREASING_MAX_CAPACITY_COUNT_COMPARATOR =
    new ReverseComparator(DECREASING_MAX_CAPACITY_COUNT_COMPARATOR);

  /** largest capacity first */
  public static final Comparator DECREASING_CAPACITY_BYTES_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        int i1 = is1.getCapacityBytes();
        int i2 = is2.getCapacityBytes();
        return i2 - i1;
      }
    };
  public static final Comparator INCREASING_CAPACITY_BYTES_COMPARATOR =
    new ReverseComparator(DECREASING_CAPACITY_BYTES_COMPARATOR);

  /** largest max_capacity first */
  public static final Comparator DECREASING_MAX_CAPACITY_BYTES_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        int i1 = is1.getMaximumCapacityBytes();
        int i2 = is2.getMaximumCapacityBytes();
        return i2 - i1;
      }
    };
  public static final Comparator INCREASING_MAX_CAPACITY_BYTES_COMPARATOR =
    new ReverseComparator(DECREASING_MAX_CAPACITY_BYTES_COMPARATOR);

  /** largest (capacity - size) first */
  public static final Comparator DECREASING_EXCESS_CAPACITY_COMPARATOR = 
    new Comparator() {
      public int compare(Object o1, Object o2) {
        InstanceStats is1 = (InstanceStats) o1;
        InstanceStats is2 = (InstanceStats) o2;
        int c1 = is1.getCapacityCount();
        int s1 = is1.getSize();
        int i1 = c1 - s1;
        int c2 = is2.getCapacityCount();
        int s2 = is2.getSize();
        int i2 = c2 - s2;
        return i2 - i1;
      }
    };
  public static final Comparator INCREASING_EXCESS_CAPACITY_COMPARATOR =
    new ReverseComparator(DECREASING_EXCESS_CAPACITY_COMPARATOR);

  /** largest group count first */
  public static final Comparator DECREASING_GROUP_COUNT_COMPARATOR =
    new Comparator() {
      public int compare(Object o1, Object o2) {
        Groupings.Count gc1 = (Groupings.Count) o1;
        Groupings.Count gc2 = (Groupings.Count) o2;
        int i1 = gc1.getCount();
        int i2 = gc2.getCount();
        return i2 - i1;
      }
    };
  public static final Comparator INCREASING_GROUP_COUNT_COMPARATOR =
    new ReverseComparator(DECREASING_GROUP_COUNT_COMPARATOR);
}
