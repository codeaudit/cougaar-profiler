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
 * Internal configuration options.
 */
interface Configure {

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

  /**
   * InstanceStats size/capacity methods should lookup the
   * current value, as opposed to returning zero.
   * <p>
   * This is usually fine, except that Comparators should save the
   * value first <i>before</i> sorting to avoid concurrent changes.
   * A concurrent change won't throw an exception but may result in
   * a bad sort order.
   */
  boolean SHOW_CURRENT_SIZE = true;

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
   * Period for MemoryStatsImpl cleanup thread.
   * <p>
   * Each ClassTracker's InstancesTable cleans a bit of itself every
   * time an instance is added.  This thread periodically cleans the
   * entire table in case it hasn't been accessed in a while.  If
   * this thread is disabled then the stats and GC may be delay
   * until UI update.
   */
  int UPDATE_FREQUENCY = 2 * 60 * 1000;

  /**
   * Rehash factor for InstancesTable capacity.
   * <p>
   * This is the average bucket size for the InstanceTable.  If the
   * table size outgrows this factor then the table will be rehashed.
   * The factor also controls the table's self-clean, where every
   * "put" scans the other entries in the bucket for GC.
   */
  int REHASH_FACTOR = 7;

}
