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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** 
 * Utility methods to count unique instances.
 */
public class Groupings {

  private Groupings() {}

  /**
   * Count unique keys in an array of InstanceStats.
   * @return an array of "Groupings.Count"s
   */
  public static final Count[] uniq(
      InstanceStats[] iss,
      String group) {
    return uniq(iss, getGroup(group));
  }

  /** A simple (object, int) pair */
  public static final class Count {
    private final int count;
    private final Object obj;
    public Count(int count, Object obj) {
      this.count = count;
      this.obj = obj;
    }
    public int getCount() {
      return count;
    }
    public Object getObject() {
      return obj;
    }
    // RFE: add "example key" field(s)?
    public String toString() {
      return "(count="+count+" object="+obj+")";
    }
  }

  public interface Group {
    Object getKey(Object o);
  }

  public static final String STACK = "stack";
  public static final String TIME = "time";
  public static final String SECOND= "second";
  public static final String MINUTE= "minute";
  public static final String HOUR = "hour";
  public static final String HASHCODE = "hashcode";
  public static final String EQUALS = "equals";
  public static final String TO_STRING = "toString";
  /** get group by name */
  public static final Group getGroup(String name) {
    Group ret;
    if (name == null) {
      ret = null;
    } else if (name.equals(STACK)) {
      ret = STACK_GROUP;
    } else if (name.equals(TIME)) {
      ret = TIME_GROUP;
    } else if (name.equals(SECOND)) {
      ret = SECOND_GROUP;
    } else if (name.equals(MINUTE)) {
      ret = MINUTE_GROUP;
    } else if (name.equals(HOUR)) {
      ret = HOUR_GROUP;
    } else if (name.equals(HASHCODE)) {
      ret = HASHCODE_GROUP;
    } else if (name.equals(EQUALS)) {
      ret = EQUALS_GROUP;
    } else if (name.equals(TO_STRING)) {
      ret = TO_STRING_GROUP;
    } else {
      ret = null;
    }
    return ret;
  }

  /** group by allocation stacktrace */
  public static final Group STACK_GROUP =
    new Group() {
      public Object getKey(Object o) {
        InstanceStats is = (InstanceStats) o;
        Throwable throwable = is.getThrowable();
        if (throwable == null) {
          return null;
        }
        return new StackElements(throwable);
      }
    };
  /** group by allocation time */
  public static final class TimeGroup implements Group {
    private final long mod;
    public TimeGroup(long mod) {
      this.mod = mod;
    }
    public Object getKey(Object o) {
      InstanceStats is = (InstanceStats) o;
      long time = is.getAllocationTime();
      if (mod > 0) {
        time -= (time % mod);
      }
      return new Long(time);
    }
  }
  public static final TimeGroup TIME_GROUP = new TimeGroup(0);
  public static final TimeGroup SECOND_GROUP = new TimeGroup(1000);
  public static final TimeGroup MINUTE_GROUP = new TimeGroup(60*1000);
  public static final TimeGroup HOUR_GROUP = new TimeGroup(60*60*1000);
  /** group by object hashcode */
  public static final Group HASHCODE_GROUP =
    new Group() {
      public Object getKey(Object o) {
        InstanceStats is = (InstanceStats) o;
        Object obj = is.get();
        int hc = 0;
        if (obj != null) {
          // watch out for concurrent mods!
          // (e.g. ArrayList) 
          try {
            hc = obj.hashCode();
          } catch (Exception e) {
          }
        }
        return new Integer(hc);
      }
    };
  /** group by object equals */
  public static final Group EQUALS_GROUP =
    new Group() {
      public Object getKey(Object o) {
        InstanceStats is = (InstanceStats) o;
        Object obj = is.get();
        // wrap the object in case of concurrent mods!
        // (e.g. ArrayList equality) 
        return (obj == null ? null : new Wrapper(obj));
      }
    };
  /** group by object toString */
  public static final Group TO_STRING_GROUP =
    new Group() {
      public Object getKey(Object o) {
        InstanceStats is = (InstanceStats) o;
        Object obj = is.get();
        String s;
        if (obj == null) {
          s = "null";
        } else {
          try {
            s = obj.toString();
          } catch (Exception e) {
            // concurrent mod?
            s = e.toString();
          }
        }
        return s;
      }
    };

  /**
   * Given a list of objects, extract the key from each
   * object, count the number of unique keys, and return
   * a list of (count, key) pairs.
   */
  public static final Count[] uniq(
      Object[] objs,
      Group groupCalc) {
    Map map = new HashMap();
    int n = (objs == null ? 0 : objs.length);
    for (int i = 0; i < n; i++) {
      Object oi = objs[i];
      Object key =
        (groupCalc == null ? oi : groupCalc.getKey(oi));
      Num num = (Num) map.get(key);
      if (num == null) {
        num = new Num();
        map.put(key, num);
      }
      num.i++;
    }
    int m = map.size();
    Count[] ret = new Count[m]; 
    Iterator iter = map.entrySet().iterator();
    for (int j = 0; j < m; j++) {
      Map.Entry me = (Map.Entry) iter.next();
      Object key = me.getKey();
      if (key instanceof Wrapper) {
        key = ((Wrapper) key).getObject();
      } else if (key instanceof StackElements) {
        key = ((StackElements) key).getThrowable();
      }
      Num num = (Num) me.getValue();
      ret[j] = new Count(num.i, key);
    }
    return ret;
  }

  private static final class Num {
    int i;
  }

  private static final class Wrapper {
    private final Object obj;
    private int hc;
    public Wrapper(Object obj) {
      this.obj = obj;
    }
    public Object getObject() {
      return obj;
    }
    public int hashCode() {
      if (hc == 0) {
        try {
          hc = obj.hashCode();
        } catch (Exception e) {
          hc = -12345;
        }
      }
      return hc;
    }
    public boolean equals(Object x) {
      try {
        return obj.equals(x);
      } catch (Exception e) {
        return false;
      }
    }
    public String toString() {
      return obj.toString();
    }
  }
}
