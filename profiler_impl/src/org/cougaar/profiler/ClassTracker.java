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
public class ClassTracker extends MemoryTracker {

  private final String classname;
  private final int bytes;

  protected final ClassStats overall_stats;

  private Object lock = new Object();

  private InstancesTable objs;
  protected AgentsTable agents;

  static ClassTracker newClassTracker(
      String classname,
      int bytes,
      boolean has_size,
      boolean has_capacity) {
    if (Configure.CAPTURE_STACK &&
        (has_size || has_capacity)) {
      return new PlusSize(classname, bytes);
    } else {
      return new ClassTracker(classname, bytes);
    }
  }

  private ClassTracker(String classname, int bytes) {
    this.classname = classname;
    this.bytes = bytes;
    this.overall_stats = newClassStats();
  }

  /** @return class that's being tracked */
  public final String getClassName() {
    return classname;
  }

  /** @return estimated object size in bytes */
  public final int getObjectSize() {
    return bytes;
  }

  /** @return summary statistics */
  public final ClassStats getOverallStats() {
    return overall_stats;
  }

  /** @return individual instance statistics */
  public final InstanceStats[] update() {
    return updateNow(true);
  }

  /** @return known agent names */
  public String[] getAgentNames() {
    synchronized (lock) {
      return (agents == null ? (new String[0]) : agents.getNames());
    }
  }
  /** @return stats for a specific agent */
  public ClassStats getAgentStats(String agent) {
    synchronized (lock) {
      return (agents == null ? null : agents.get(agent));
    }
  }

  public String toString() {
    return
      "(class_stats"+
      " classname="+classname+
      " bytes="+bytes+
      " overall_stats="+overall_stats+
      ")";
  }

  // for use by MemoryStatsImpl
  final void timerUpdate() { 
    updateNow(false);
  }
  public void add(Object new_o) {
    InstanceStats new_is = newInstanceStats(new_o);
    synchronized (lock) {
      if (objs == null) {
        objs = new InstancesTable() {
          protected void allocate(InstanceStats is) {
            overall_stats.allocate(is);
          }
          protected void gc(InstanceStats is) {
            overall_stats.gc(is);
          }
        };
      }
      objs.put(new_o, new_is);
    }
  }

  protected ClassStats newClassStats() {
    return ClassStats.newClassStats(false);
  }
  protected InstanceStats newInstanceStats(Object new_o) {
    return
      InstanceStats.newInstanceStats(
          new_o,
          Configure.CAPTURE_TIME,
          Configure.CAPTURE_STACK,
          false, // see subclass
          Configure.CAPTURE_CONTEXT);
  }
  protected void updateInstanceStats(InstanceStats current) {
    String agent = current.getAgentName();
    if (agent != null) {
      if (agents == null) {
        agents = new AgentsTable();
      }
      ClassStats cs = agents.get(agent);
      if (cs == null) {
        cs = newClassStats();
        agents.put(agent, cs);
      }
      // We could increment a counter every time a collection is created,
      // but this would require to lookup the subject information.
      cs.allocate(current);
    }
  }

  /** update, get a list of non-gc'ed entities */
  private InstanceStats[] updateNow(boolean returnEntities) {
    // prune out the freed objects, create a list of entries
    synchronized (lock) {

      if (objs == null) {
        if (returnEntities) {
          return new InstanceStats[0];
        } else {
          return  null;
        }
      }

      // clear size and capacity stats
      overall_stats.reset();
      if (agents != null) { 
        agents.reset();
      }

      InstanceStats[] ret = null;
      int ret_size = 0;
      if (returnEntities) {
        ret = new InstanceStats[objs.size()];
      }

      objs.startIterator();
      while (true) {
        InstanceStats is = objs.next();
        if (is == null) {
          break;
        }

        if (returnEntities) {
          if (ret_size >= ret.length) {
            InstanceStats[] old = ret;
            ret = new InstanceStats[2 * ret.length];
            System.arraycopy(old, 0, ret, 0, ret_size);
          }
          ret[ret_size++] = is;
        }

        // update the entry
        updateInstanceStats(is); 
      }

      if (returnEntities && 
          (ret_size != ret.length)) {
        // trim to size
        InstanceStats[] old = ret;
        ret = new InstanceStats[ret_size];
        System.arraycopy(old, 0, ret, 0, ret_size);
      }

      return ret;
    }
  }

  // impl with fields for size and capacity
  private static class PlusSize extends ClassTracker {
    public PlusSize(String classname, int bytes) {
      super(classname, bytes);
    }

    protected InstanceStats newInstanceStats(Object new_o) {
      return
        InstanceStats.newInstanceStats(
            new_o,
            Configure.CAPTURE_TIME,
            Configure.CAPTURE_STACK,
            true,
            Configure.CAPTURE_CONTEXT);
    }
    protected void updateInstanceStats(InstanceStats current) {
      // update the entry
      current.update();

      String agent = current.getAgentName();
      long size = (long) current.getSize();
      long capacity = (long) current.getCapacity();
      long maxSize = (long) current.getMaximumSize();
      long maxCapacity = (long) current.getMaximumCapacity();

      if (agent != null) {
        if (agents == null) {
          agents = new AgentsTable();
        }
        ClassStats cs = agents.get(agent);
        if (cs == null) {
          cs = newClassStats();
          agents.put(agent, cs);
        }
        // We could increment a counter every time a collection is created,
        // but this would require to lookup the subject information.
        cs.allocate(current);
        cs.update(size, maxSize, capacity, maxCapacity);
      }
      overall_stats.update(
          size, maxSize, capacity, maxCapacity);
    }
    protected ClassStats newClassStats() {
      return ClassStats.newClassStats(true);
    }
  }
}
