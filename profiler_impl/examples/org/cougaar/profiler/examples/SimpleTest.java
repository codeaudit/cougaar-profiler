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
package org.cougaar.profiler.examples;

import java.lang.ref.*;
import java.io.*;
import org.cougaar.profiler.*;
import java.util.*;

/**
 * Simple test for the MemoryTracker.
 */
public class SimpleTest {

  public static void main(String[] args) {
    System.out.println("begin");

    pause(1000);

    // create dummy map
    Map m = new HashMap();
    for (int i = 0; i < 1234; i++) {
      m.put("foo"+i, "bar"+i);
    }
    Object o = m;
    m = null;

    // serialize it
    byte[] b = serialize(o);
    System.out.println("serialized (hc="+o.hashCode()+")");
    o = null;
    printStats();

    pause(1000);

    // force gc
    System.out.println("gc");
    System.gc();
    printStats();

    pause(1000);

    // deserialize it
    o = deserialize(b);
    System.out.println("deserialized (hc="+o.hashCode()+")");
    printStats();

    System.out.println("end ");
  }

  public static void printStats() {
    System.out.println("-----------------------------------------------");

    MemoryStats tracker = MemoryStatsImpl.getInstance();

    // print class summary
    //printAllClassStats(tracker);

    // print largest Maps
    String classname = "java.util.HashMap";
    int maxInstances = 3;
    int maxToString = 100;
    int maxStackLines = 12;
    printInstanceStats(
        tracker,
        classname,
        maxInstances,
        maxToString,
        maxStackLines);

    System.out.println("-----------------------------------------------");
  }

  public static void printAllClassStats(MemoryStats tracker) {
    String[] classes = tracker.getClassNames();
    int n = classes.length;
    System.out.println("class_stats["+n+"]:");
    for (int i = 0 ; i < n; i++) {
      String classname = classes[i];
      ClassTracker ct = tracker.getClassTracker(classname);
      // force update
      InstanceStats[] ignoreme = ct.update();
      printClassStats(ct);
    }
  }

  public static void printClassStats(ClassTracker ct) {
    ClassStats cs = ct.getOverallStats();
    int bytes_each = ct.getObjectSize();
    long live = cs.getInstances();
    long dead = cs.getGarbageCollected();
    long sum_capacity_bytes = cs.getSumCapacityBytes();
    long mem = (live * bytes_each + sum_capacity_bytes);
    System.out.println(
        ct.getClassName()+"{"+
        "\n  memory:             "+mem+
        "\n  live:               "+live+
        "\n  gc'ed:              "+dead+
        "\n  total:              "+(live + dead)+
        "\n  sum_size:           "+cs.getSumSize()+
        "\n  sum_capacity_count: "+cs.getSumCapacityCount()+
        "\n  sum_capacity_bytes: "+sum_capacity_bytes+
        "\n}"
        );
  }

  public static void printInstanceStats(
      MemoryStats tracker,
      String classname,
      int maxInstances,
      int maxToString,
      int maxStackLines) {
    ClassTracker ct = tracker.getClassTracker(classname);
    if (ct == null) {
      System.out.println("not tracked: "+classname);
      return;
    }

    // force update
    InstanceStats[] iss = ct.update();
    int n = Math.min(maxInstances, iss.length);

    // print overview
    printClassStats(ct);

    // sort by "size()"
    Comparators.sort(iss, Comparators.DECREASING, Comparators.SIZE);

    System.out.println(
        classname+" instances sorted by largest \"size()\""+
       "\nshowing "+n+" of "+iss.length);

    for (int i = 0; i < n; i++) {
      System.out.println("instance["+i+"]:");
      printInstanceStats(iss[i], maxToString, maxStackLines);
    }
  }

  public static void printInstanceStats(
      InstanceStats is,
      int maxToString,
      int maxStackLines) {
    Object o = is.get();
    String str;
    int hc;
    if (o == null) {
      str = null;
      hc = -1;
    } else {
      try {
        str = o.toString();
        hc = o.hashCode();
      } catch (Exception e) {
        str = e.getMessage();
        hc = -1;
      }
    }
    if (str == null) {
      str = "null";
    }
    if (str.length() > maxToString) {
      str =
        str.substring(0, maxToString)+
        " +"+
        (str.length()-maxToString);
    }
    System.out.print(
        "    null:               "+(o == null)+
        "\n    toString:           "+str+
        "\n    hashcode:           "+Integer.toHexString(hc)+
        "\n    size:               "+is.getSize()+
        "\n    capacity_count:     "+is.getCapacityCount()+
        "\n    capacity_bytes:     "+is.getCapacityBytes()+
        "\n    agent:              "+is.getAgentName()+
        "\n    ");
    printStack(is.getThrowable(), maxStackLines);
  }

  private static void printStack(
      Throwable throwable,
      int maxStackLines) {
    if (throwable == null) {
      System.out.println("allocation-point stack[]");
      return;
    }
    StackTraceElement ste[] = throwable.getStackTrace();
    int LINES_TO_SKIP = 5; // lines within the profiler
    int lines = Math.min(ste.length, maxStackLines+LINES_TO_SKIP);
    System.out.println(
        "stack["+
        (lines-LINES_TO_SKIP)+" of "+ste.length+"]:");
    for (int j = LINES_TO_SKIP; j < lines; j++) {
      System.out.println(
          "      "+
          ste[j].getClassName()+"."+
          ste[j].getMethodName()+"("+
          ste[j].getFileName()+":"+
          ste[j].getLineNumber()+")");
    }
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(1000);
    } catch (Exception e) {
    }
  }

  private static byte[] serialize(Object o) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream os = new ObjectOutputStream(bos);
      os.writeObject(o);
      os.flush();
      byte[] b = bos.toByteArray();
      return b;
    } catch (Exception e) {
      throw new RuntimeException("serialize failure", e);
    }
  }

  private static Object deserialize(byte[] b) {
    try {
      ByteArrayInputStream bis = new ByteArrayInputStream(b);
      ObjectInputStream is = new ObjectInputStream(bis);
      Object newO = is.readObject();
      return newO;
    } catch (Exception e) {
      throw new RuntimeException("deserialize failure", e);
    }
  }
}
