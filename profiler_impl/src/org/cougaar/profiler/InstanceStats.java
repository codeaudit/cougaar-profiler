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

import java.lang.ref.WeakReference;

/**
 * Instance data, including a weak reference to the object
 * and optional allocation timestamp, stacktrace, size
 * metrics, and context.
 */
public class InstanceStats {

  // this baseclass, with just a weak reference to the instance,
  // costs 40 bytes

  /** A WeakReference to the object. */
  private final WeakReference ref;

  /** for ClassTracker use! */
  InstanceStats next;

  // factory method:
  static InstanceStats newInstanceStats(
      Object obj,
      boolean plusTime,
      boolean plusStack,
      boolean plusSize,
      boolean plusContext) {
    WeakReference ref = new WeakReference(obj);
    long time = (plusTime ? System.currentTimeMillis() : -1);
    Throwable stack = (plusStack ? new Throwable() : null);
    InstanceContext context = 
      (plusContext ? InstanceContext.getInstanceContext() : null);
    // return subclass with minimal fields, to save memory.
    // we only implement some of the permutations noted below.
    if (!plusSize && (context == null)) { 
      if (!plusStack) {
        if (!plusTime) {
          return new InstanceStats(ref);
        } else {
          return new InstanceStats.WithTime(ref, time);
        }
      }
      return new InstanceStats.WithTimeStack(ref, time, stack);
    }
    return new InstanceStats.WithTimeStackSizeContext(
        ref, time, stack, context);
  }

  /** Get the instance by accessing the weak reference */
  public final Object get() {
    return ref.get();
  }

  /** System time when allocated */
  public long getAllocationTime() {
    return -1;
  }
  /** Stacktrace when allocated */
  public Throwable getThrowable() {
    return null;
  }
  /** Most recent "size()" calculation */
  public int getSize() {
    return 0;
  }
  /** Most recent "capacity()" calculation */
  public int getCapacity() {
    return 0;
  }
  /** Maximum observed size */
  public int getMaximumSize() {
    return 0;
  }
  /** Maximum observed capacity */
  public int getMaximumCapacity() {
    return 0;
  }
  /** Context when allocated (based upon threadlocals */
  public InstanceContext getInstanceContext() {
    return InstanceContext.NULL;
  }
  /** Same as "getInstanceContext.getAgentName()" */
  public String getAgentName() {
    return null;
  }

  void update() {
  }

  InstanceStats(WeakReference ref) {
    this.ref = ref;
  }

  //
  // impls with additional fields
  //
  // all 15 permutations would be:
  //   - 
  //   time
  //   stack
  //   size
  //   context
  //   time+stack 
  //   time+size 
  //   time+context 
  //   stack+size 
  //   stack+context 
  //   size+context 
  //   time+stack+size 
  //   time+stack+context 
  //   stack+size+context 
  //   time+stack+size+context 
  //
  // we're only doing this to optionally save some memory, so for
  // now we'll implement some common permutations:
  //   -     (minimal)
  //   time  (minimal)
  //   time+stack (typical)
  //   time+stack+size+context  (catch-all)
  //

  private static class WithTime extends InstanceStats {
    // this adds 8 bytes to the basic size, for a total
    // of 48 bytes
    private final long time;
    public WithTime(
        WeakReference ref,
        long time) {
      super(ref);
      this.time = time;
    }
    public long getAllocationTime() {
      return time;
    }
  }
  private static class WithTimeStack extends WithTime {
    // I worked out an estimated memory cost for the stack
    // by looking at Throwable, StackTraceElement, and noting
    // that element strings are interned.
    //
    // The initial representation uses a lazy native "backtrace"
    // that's used to fill in the elements when the're requested.
    // A request is any "toString()", "getStackTrace()",
    // "writeObject()", or other accesor.  Once resolved the
    // backtrace is presumably freed.  A backtrace is likely
    // a native array of 32-bit PC addresses.
    //  
    // Cost estimates (in bytes) for a stack with N elements:
    //   initial:    32 + 4*N
    //   resolved:   44 + 24*N 
    // the "WithTime" baseclass costs 48 bytes, yielding:
    //   initial:    80 + 4*N
    //   resolved:   92 + 24*N 
    private final Throwable stack;
    public WithTimeStack(
        WeakReference ref,
        long time,
        Throwable stack) {
      super(ref, time);
      this.stack = stack;
    }
    public Throwable getThrowable() {
      return stack;
    }
  }
  private static class WithTimeStackSizeContext extends WithTimeStack {
    // The memory cost is the super's cost plus 20 bytes for
    // the fields in this class, plus more if the context is
    // non-null.  A context costs somewhere around 80+ bytes,
    // depending upon the stack and number of principles.
    private final InstanceContext context;
    private int size;
    private int capacity;
    private int maxSize;
    private int maxCapacity;
    public WithTimeStackSizeContext(
        WeakReference ref,
        long time,
        Throwable stack,
        InstanceContext context) {
      super(ref, time, stack);
      this.context = context;
    }
    void update() {
      Object o = get();
      if (o == null) {
        size = 0;
        capacity = 0;
        return;
      }
      // update capacity:
      if (o instanceof Capacity) {
        try {
          capacity = ((Capacity) o).$get_capacity();
        } catch (Exception e) {
          System.err.println("Failed \"$get_capacity()\":");
          e.printStackTrace();
        }
        if (maxCapacity < capacity) {
          maxCapacity = capacity;
        }
      } else {
        capacity = 0;
      }
      // update size:
      if (o instanceof Size) {
        try {
          size = ((Size) o).$get_size();
        } catch (Exception e) {
          System.err.println("Failed \"$get_size()\":");
          e.printStackTrace();
        }
      } else {
        size = capacity;
      } 
      if (maxSize < size) {
        maxSize = size;
      }
    }
    public int getSize() { return size; }
    public int getCapacity() { return capacity; }
    public int getMaximumSize() { return maxSize; }
    public int getMaximumCapacity() { return maxCapacity; }
    public InstanceContext getInstanceContext() {
      return (context == null ? InstanceContext.NULL : context);
    }
    public String getAgentName() {
      return getInstanceContext().getAgentName();
    }
  }
}
