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
 * Configuration options.
 * <p> 
 * We can't set these via system properties, since we may want to
 * profile "java.util.Properties" and would run into a stack
 * overflow error.
 */
interface Configure {

  /**
   * Disable all instance details to minimize CPU and memory
   * overhead.
   * <p>
   * This is equivalent to setting:<pre>
   *   boolean CAPTURE_TIME = false;
   *   boolean CAPTURE_STACK = false;
   *   boolean CAPTURE_SIZE = false;
   *   boolean CAPTURE_CONTEXT = false;
   * </pre>
   */
  boolean MIN_OVERHEAD = false;

  /**
   * Capture overall size/capacity metrics on a per-class basis,
   * even if CAPTURE_SIZE is false.
   */
  boolean SUMMARY_SIZE = true;

  /**
   * Context data is disabled since we may want to profile
   * "java.util.WeakHashMap", which is required by the context
   * implementation.
   * <p>
   * This would cause a VM load time stack overflow error:<pre>
   *   at org.cougaar.profiler.InstanceContext.getInstanceContext(InstanceContext.java:40)
   *   at org.cougaar.profiler.InstanceStats.newInstanceStats(InstanceStats.java:51)
   *   at org.cougaar.profiler.ClassTracker.newInstanceStats(ClassTracker.java:124)
   *   at org.cougaar.profiler.ClassTracker.add(ClassTracker.java:104)
   *   at java.util.WeakHashMap$Entry.$profile_java_util_WeakHashMap$Entry(WeakHashMap.java)
   *   at java.util.WeakHashMap$Entry.&lt;init&gt;(WeakHashMap.java:636)
   *   at java.util.WeakHashMap.put(WeakHashMap.java:402)
   *   at javax.security.auth.SubjectDomainCombiner.combine(SubjectDomainCombiner.java:223)
   *   at java.security.AccessControlContext.goCombiner(AccessControlContext.java:387)
   *   at java.security.AccessControlContext.optimize(AccessControlContext.java:311)
   *   at java.security.AccessController.getContext(AccessController.java:362)
   *   at org.cougaar.profiler.InstanceContext.getInstanceContext(InstanceContext.java:40)
   *   ...
   * </pre> 
   */
  boolean CAN_CAPTURE_CONTEXT = false;

  // see InstanceStats for memory cost estimates 

  /**
   * Capture per-instance allocation timestamp.
   * <p> 
   * Costs 8 bytes per profiled instance.
   * Invokes "System.currentTimeMillis()".
   */ 
  boolean CAPTURE_TIME = (true && !MIN_OVERHEAD);

  /**
   * Capture per-instance allocation stacktrace.
   * <p>
   * Cost estimates (in bytes) for a stack with N elements:<pre>
   *   initial:    32 + 4*N
   *   resolved:   44 + 24*N
   * </pre>
   * Invokes "new Throwable()".
   */ 
  boolean CAPTURE_STACK = (true && !MIN_OVERHEAD);

  /**
   * Capture per-instance size and capacity metrics.
   * <p> 
   * Costs 24 bytes per profiled instance.
   * Periodically invokes Size and Capacity methods.
   */ 
  boolean CAPTURE_SIZE = (true && !MIN_OVERHEAD);

  /**
   * Capture per-instance allocation "agent" context.
   * <p>
   * Costs about 80+ bytes, depending upon security context.
   * Invokes "AccessController.getContext()".
   */
  boolean CAPTURE_CONTEXT = (true && CAN_CAPTURE_CONTEXT && !MIN_OVERHEAD);

  /**
   * MemoryStatsImpl singleton resolution delay in milliseconds.
   * <p> 
   * This is the delay between loading the MemoryStatsImpl class
   * and creating a classloader-safe singleton proxy.  This is
   * tricky if we're profiling "java.util.*", since ClassLoader
   * requires "java.util".  See MemoryStatsImpl for details.
   */
  long DELAY_AFTER_STARTUP = 500;

  /**
   * Period for InstancesTable cleanup thread.
   * <p>
   * The InstancesTable cleans a bit of itself every time an
   * instance is added.  This thread periodically cleans the
   * entire table in case it hasn't been accessed in a while.
   */
  int UPDATE_STATS_FREQUENCY = 2 * 60 * 1000;

}
