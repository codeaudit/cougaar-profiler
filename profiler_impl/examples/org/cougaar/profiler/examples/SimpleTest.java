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

    // create a map and serialize it
    Object o = new HashMap(5000);
    ((Map) o).put("foo", "bar");
    byte[] b = serialize(o);
    System.out.println("serialized "+o);
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
    System.out.println("deserialized "+o);
    printStats();

    System.out.println("end ");
  }

  public static void printStats() {
    System.out.println("-----------------------------------------------");

    MemoryStats tracker = MemoryStatsImpl.getInstance();

    // print class summary
    printClassStats(tracker);

    // print largest ObjectInputStreams
    int maxRows = 5;
    int maxLines = 5;
    String type = "java.io.ObjectOutputStream";
    printInstanceStats(tracker, type, maxRows, maxLines);

    System.out.println("-----------------------------------------------");
  }

  public static void printClassStats(MemoryStats tracker) {
    String[] classes = tracker.getClassNames();
    int n = classes.length;
    System.out.println("class_stats["+n+"]:");
    for (int i = 0 ; i < n; i++) {
      String cl = classes[i];
      ClassTracker ct = tracker.getClassTracker(cl);
      System.out.println(ct);
      System.out.println("update("+cl+")");
      // force an update
      InstanceStats[] ignoreme = ct.update();
      ClassStats cs = ct.getOverallStats();
      long instances = cs.getInstances();
      long collected = cs.getGarbageCollected();
      System.out.println(
          "  type="+cl+
          "\n    instances:           "+instances+
          "\n    gc'ed:               "+collected+
          "\n    total:               "+(instances + collected)+
          "\n    total_size:          "+cs.getTotalSize()+
          "\n    total_capacity:      "+cs.getTotalCapacity()+
          "\n    mean_size:           "+getMeanSize(cs)+
          "\n    mean_capacity:       "+getMeanCapacity(cs)
          );
    }
  }

  public static void printInstanceStats(
      MemoryStats tracker,
      String type,
      int maxRows,
      int maxLines) {
    ClassTracker ct = tracker.getClassTracker(type);
    if (ct == null) {
      System.out.println("not tracked: "+type);
      return;
    }
    ClassStats cs = ct.getOverallStats();
    long numInstances = cs.getInstances();
    InstanceStats[] iss = ct.update();

    List l = trim(Arrays.asList(iss), maxRows);
    Collections.sort(l, Comparators.DECREASING_SIZE_COMPARATOR);

    System.out.println(
        "instance_stats["+l.size()+ " of "+numInstances+"]:"+
        "\n  type:            "+type);

    for (int i = 0, n = l.size(); i < n; i++) {
      InstanceStats is = (InstanceStats) l.get(i);
      Object o = is.get();
      System.out.println(
          "    hashcode:         "+
          (o == null ? "null" : Integer.toHexString(o.hashCode())));
      System.out.println("    current_size:     "+is.getSize());
      System.out.println("    max_size:         "+is.getMaximumSize());
      System.out.println("    current_capacity: "+is.getCapacity());
      System.out.println("    max_capacity:     "+is.getMaximumCapacity());
      System.out.println("    agent:            "+is.getAgentName());
      System.out.print(  "    stack[");
      Throwable throwable = is.getThrowable();
      if (throwable == null) {
        System.out.println("]");
        continue;
      }
      StackTraceElement ste[] = throwable.getStackTrace();
      int LINES_TO_SKIP = 4; // lines within the profiler
      int lines = Math.min(ste.length, maxLines+LINES_TO_SKIP);
      System.out.println((lines-LINES_TO_SKIP)+" of "+ste.length+"]:");
      for (int j = LINES_TO_SKIP; j < lines; j++) {
        System.out.println(
            "    "+
            ste[j].getClassName()+"."+
            ste[j].getMethodName()+"("+
            ste[j].getFileName()+":"+
            ste[j].getLineNumber()+")");
      }
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

  private static List trim(List l, int max) {
    List ret = l;
    int total = (l == null ? 0 : l.size());
    int limit = Math.min(max, total);
    if (max > limit) {
      try {
        ret = new ArrayList(l.subList(0, limit));
      } catch (IndexOutOfBoundsException e) {
        System.out.println("Error: " + e);
      }
    }
    return ret;
  }

  private static double getMeanSize(ClassStats cs) {
    double mean =
      ((double)(cs.getTotalSize()) /
       (double)(cs.getInstances())) * 100;
    mean = ((double)Math.round(mean) ) / 100;
    return mean;
  }

  private static double getMeanCapacity(ClassStats cs) {
    double mean = 
      ((double)(cs.getTotalCapacity()) /
       (double)(cs.getInstances())) * 100;
    mean = ((double)Math.round(mean) ) / 100;
    return mean;
  }
}
