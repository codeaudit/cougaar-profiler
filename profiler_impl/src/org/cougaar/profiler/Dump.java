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

import java.io.PrintStream;
import java.util.Arrays;

/**
 * A simple utility class to print the basic profiler state to
 * a stream in CSV format.
 * <p>
 * This would typically be accessed by reflection, to avoid
 * compile-time dependencies on the profiler:
 * <pre>
 *   Class cl = Class.forName("org.cougaar.profiler.Dump");
 *   java.lang.reflect.Method m = 
 *     cl.getMethod("dumpTo", new Class[] {
 *       java.io.PrintStream.class}); 
 *   m.invoke(null, new Object[] {System.out}); 
 * </pre> 
 */
public class Dump {

  public static void main(String[] args) {
    dumpTo(System.out);
  }

  /** print CSV data to a stream */
  public static void dumpTo(PrintStream out) {
    out.println(
        "# Type, Sample%, Live, GC'd, Total, Bytes Each, *Live");

    MemoryStats memoryStats = MemoryStatsImpl.getInstance();
    if (memoryStats == null) {
      return;
    }

    String[] classes = memoryStats.getClassNames();
    Arrays.sort(classes);
    int n = classes.length;
    for (int i = 0; i < n; i++) {
      String cl = classes[i];
      ClassTracker ct = memoryStats.getClassTracker(cl);
      //ct.update();
      ClassStats cs = ct.getOverallStats();
      double trackRatio = ct.getOptions().getSampleRatio();
      int bytes = ct.getObjectSize();
      printType(out, cs, cl, trackRatio, bytes);
    }
  }

  private static void printType(
      PrintStream out,
      ClassStats cs,
      String cl,
      double trackRatio,
      int bytes) {
    long live = cs.getInstances();
    long dead = cs.getGarbageCollected();
    long cap = cs.getSumCapacityBytes();
    long sumSize = cs.getSumSize();
    long sumCap = cs.getSumCapacityCount();
    if (trackRatio < 1.0) {
      if (trackRatio > 0.0) {
        live = (long) ((double) live / trackRatio);
        dead = (long) ((double) dead / trackRatio);
        cap = (long) ((double) cap / trackRatio);
        sumSize = (long) ((double) sumSize / trackRatio);
        sumCap = (long) ((double) sumCap / trackRatio);
      } else {
        live = 0;
        dead = 0;
        cap = 0;
        sumSize = 0;
        sumCap = 0;
      }
    }
    out.println(
        cl+", "+
        format(100.0*trackRatio)+", "+
        live+", "+
        dead+", "+
        (live + dead)+", "+
        bytes+", "+
        (live * bytes));
  }

  private static String format(double d) {
    // we want "#0.0#"
    //
    // ideally we'd return this double with fewer decimal places:
    //   d = (((double) Math.round(d * 100)) / 100);
    //   return Double.toString(d); 
    // however this occasionally causes Sun's "Assertion botch"
    // bug 4916788.  I suspect that DecimalFormat has the same
    // problem, so here we do it manually.  We don't expect
    // oddities like  NaNs/infinites/etc.
    double floor = Math.floor(d);
    double rem = d - floor;
    long shortrem = Math.round(rem * 100);
    return 
      (((long) floor)+
       "."+
       shortrem+
       (shortrem < 10 ? "0" : ""));
  } 
}
