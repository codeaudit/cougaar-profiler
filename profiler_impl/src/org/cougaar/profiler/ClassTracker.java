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
  private final Options options;

  protected final ClassStats overall_stats;

  private Object lock = new Object();

  private InstancesTable objs;
  protected AgentsTable agents;

  static ClassTracker newClassTracker(
      String classname,
      int bytes,
      Options options) {
    boolean plus_size = 
      (options.isSizeEnabled() ||
       options.isCapacityEnabled());
    boolean plus_sample = 
      (options.getSampleRatio() < 1.0);

    if (plus_size) {
      if (plus_sample) {
        return new PlusSizeSample(classname, bytes, options);
      } else {
        return new PlusSize(classname, bytes, options);
      }
    } else if (plus_sample) {
      return new PlusSample(classname, bytes, options);
    } else {
      return new ClassTracker(classname, bytes, options);
    }
  }

  private ClassTracker(String classname, int bytes, Options options) {
    this.classname = classname;
    this.bytes = bytes;
    this.options = options;
    this.overall_stats = newClassStats();
  }

  /** @return name of profiled class */
  public final String getClassName() {
    return classname;
  }

  /** @return estimated object size in bytes */
  public final int getObjectSize() {
    return bytes;
  }

  /** @return profiling options */
  public final Options getOptions() {
    return options;
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
    InstanceStats new_is = 
      InstanceStats.newInstanceStats(new_o, options);
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
    public PlusSize(String classname, int bytes, Options options) {
      super(classname, bytes, options);
    }

    protected void updateInstanceStats(InstanceStats current) {
      // get the current size/capacity values
      long size = (long) current.currentSize();
      long capacity_count = (long) current.currentCapacityCount();
      long capacity_bytes = (long) current.currentCapacityBytes();

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
        // We could increment a counter every time a collection is
        // created, but this would require us to lookup the subject
        // information.
        cs.allocate(current);
        cs.update(size, capacity_count, capacity_bytes);
      }
      overall_stats.update(size, capacity_count, capacity_bytes);
    }
    protected ClassStats newClassStats() {
      return ClassStats.newClassStats(true);
    }
  }
  
  // impl with random sampling support
  private static class PlusSample extends ClassTracker {
    private final double sample;
    private final Random random = new Random();
    public PlusSample(
        String classname, int bytes, Options options) {
      super(classname, bytes, options);
      this.sample = options.getSampleRatio();
    }
    public void add(Object new_o) {
      if (random.nextDouble() <= sample) {
        super.add(new_o);
      }
    }
  }

  // impl with random sampling support and size/capacity
  //
  // this is cut-n-paste of the above "PlusSample" impl, but
  // required to get the right behavior with minimal overhead. 
  private static class PlusSizeSample extends PlusSize {
    private final double sample;
    private final Random random = new Random();
    public PlusSizeSample(
        String classname, int bytes, Options options) {
      super(classname, bytes, options);
      this.sample = options.getSampleRatio();
    }
    public void add(Object new_o) {
      if (random.nextDouble() <= sample) {
        super.add(new_o);
      }
    }
  }
}
