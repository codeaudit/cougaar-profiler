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
 * Default profiling options, which controls which classes are
 * profiled and what information is tracked (timestamp, stacktrace,
 * etc).
 * <p> 
 * The bytecode modifier specifies the "OptionsFactory" class, where
 * the default is "org.cougaar.profiler.DefaultOptionsFactory".
 * The recommended usage is to specify your own "OptionsFactory"
 * class when transforming the classes (using "--config") and
 * place that class at the front of the "-Xbootclasspath/p".
 * <p> 
 * We can't set these defaults via system properties, since we may
 * want to profile "java.util.Properties" and would run into a stack
 * overflow error.
 * <p>
 * The class must contain this method:<pre>
 *   public static final Options getOptions(String classname) {..} 
 * </pre> The "Options" returned by the above method controls the
 * level of profiling for that class.  If null is returned then the
 * class will not be profiled.
 * <p> 
 * <b>NOTE:</b><br>
 * Custom "OptionsFactory" classes should minimize imports to avoid
 * VM loading errors.  For example, a call to "new HashSet()" will
 * fail if HashSet is profiled, due to a stack overflow caused by
 * the circular reference.  Similarily, if all of "java.lang" will
 * be profiled, then Strings should be carefully handled to avoid
 * string allocations, including any calls to "System.out"!
 * <p>
 * The following example is safe:<pre>
 *   public static final Options getOptions(
 *       String module, String classname) {
 *     if (classname.equals("org.cougaar.foo.Bar") ||
 *         classname.startsWith("org.cougar.foo.Bar$")) {
 *       // Bar and its inner classes 
 *       return new Options(..);
 *     } else if (classname.startsWith("java.util.")) {
 *       // java.util classes and subpackages
 *       return UTIL_OPTIONS;
 *     } else if (classname.indexOf("Plugin") != 0) {
 *       // any mention of "Plugin"
 *       return PLUGIN_OPTIONS;
 *     } else if ("foo".equals(module)) {
 *       // match module name, but watch out for null!
 *       return FOO_OPTIONS; 
 *     } else {
 *       // not profiled
 *       return null;
 *     }
 *   }
 * </pre>
 */
public final class DefaultOptionsFactory {

  /**
   * Disable all instance details to minimize CPU and memory
   * overhead.
   * <p>
   * This is equivalent to setting:<pre>
   *   CAPTURE_TIME = false;
   *   CAPTURE_STACK = false;
   *   CAPTURE_SIZE = false;
   *   CAPTURE_CAPACITY = false;
   *   CAPTURE_CONTEXT = false;
   * </pre>
   * <p>
   * Also see SAMPLE_RATIO to further reduce overhead.
   */
  private static final boolean MIN_OVERHEAD = true;

  // see InstanceStats for memory cost estimates 

  /**
   * Capture per-instance allocation timestamp.
   * <p> 
   * Costs 8 bytes per profiled instance.
   * Invokes "System.currentTimeMillis()".
   */ 
  private static final boolean CAPTURE_TIME = true;

  /**
   * Capture per-instance allocation stacktrace.
   * <p>
   * Cost estimates (in bytes) for a stack with N elements:<pre>
   *   initial:    32 + 4*N
   *   resolved:   44 + 24*N
   * </pre>
   * Invokes "new Throwable()".
   */ 
  private static final boolean CAPTURE_STACK = true;

  /**
   * Capture per-instance size metrics.
   */ 
  private static final boolean CAPTURE_SIZE = true;

  /**
   * Capture per-instance capacity metrics.
   */ 
  private static final boolean CAPTURE_CAPACITY = true;

  /**
   * Capture per-instance allocation "agent" context.
   * <p>
   * Costs about 80+ bytes, depending upon security context.
   * Invokes "AccessController.getContext()".
   */
  private static final boolean CAPTURE_CONTEXT = true;

  /**
   * Probability that an allocated object will be profiled.
   */
  private static final double SAMPLE_RATIO = 1.0;

  private static final Options DEFAULT_OPTIONS =
    new Options(
        (CAPTURE_TIME     && !MIN_OVERHEAD),
        (CAPTURE_STACK    && !MIN_OVERHEAD),
        (CAPTURE_SIZE     && !MIN_OVERHEAD),
        (CAPTURE_CAPACITY && !MIN_OVERHEAD),
        (CAPTURE_CONTEXT  && !MIN_OVERHEAD),
        SAMPLE_RATIO);

  /**
   * The profiled class will call this method to get its options.
   *
   * @param module optional module name, e.g. "xerces" or null
   * @param classname non-null class name, e.g.
   *   "org.apache.xerces.dom.DocumentImpl$LEntry"
   * @return profiling options for the class, or null if the
   *   class shouldn't be tracked.
   */
  public static final Options getOptions(
      String module,
      String classname) {
    return DEFAULT_OPTIONS;
  }
}
