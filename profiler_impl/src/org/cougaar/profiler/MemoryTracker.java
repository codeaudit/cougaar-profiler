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
 * API to register objects with the memory tracker.
 */
public abstract class MemoryTracker {

  private static final MemoryTracker NULL = 
    new MemoryTracker() {
      public void add(Object o) {}
    };

  private static final MemoryStats ms;

  static {
    ms = MemoryStatsImpl.getInstance();
  }

  protected MemoryTracker() { }

  public static final MemoryTracker getInstance(
      String type,
      int bytes) {
    return getInstance(type, bytes, false, false);
  }

  public static final MemoryTracker getInstance(
      String type,
      int bytes,
      boolean has_size,
      boolean has_capacity) {
    if (ms != null) {
      MemoryTracker mt = ms.getMemoryTracker(
          type, bytes, has_size, has_capacity);
      if (mt != null) {
        return mt;
      }
    }
    return NULL;
  }

  public abstract void add(Object o);
}
