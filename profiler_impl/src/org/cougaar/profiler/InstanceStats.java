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

  //
  // RFE: we could add these fields to the instance itself, since
  // we're modifying its bytecode anyways.  We would need to tag
  // the class with a new InstanceStats interface and add the
  // necessary fields and methods.  A change in profiling detail
  // may require modifying the class to add/remove fields.
  // 

  /** A WeakReference to the object. */
  private final WeakReference ref;

  /** for ClassTracker use! */
  InstanceStats next;

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

  protected InstanceStats(WeakReference ref) {
    this.ref = ref;
  }

  // factory method:
  static InstanceStats newInstanceStats(
      Object obj,
      boolean plusTime,
      boolean plusStack,
      boolean plusSize,
      boolean plusContext) {
    // get field values
    WeakReference ref = new WeakReference(obj);
    long time = (plusTime ? System.currentTimeMillis() : -1);
    Throwable stack = (plusStack ? new Throwable() : null);
    InstanceContext context = 
      (plusContext ? InstanceContext.getInstanceContext() : null);

    // allocate subclass with minimal number of field slots
    if (context != null) {
      // a context is relatively expensive, so for simplicity we
      // don't implement all 8 permutations:
      //   (time x stack x size) + context
      // and instead use our catch-all implementation with all
      // four field slots. 
      return new WithTimeStackSizeContext(ref, time, stack, context);
    } else if (plusTime) {
      if (plusStack) {
        if (plusSize) {
          return new WithTimeStackSize(ref, time, stack);
        } else {
          return new WithTimeStack(ref, time, stack);
        }
      } else if (plusSize) {
        return new WithTimeSize(ref, time);
      } else {
        return new WithTime(ref, time);
      }
    } else if (plusStack) {
      // a stack is relatively expensive, so for simplicity we
      // don't implement these permutations:
      //    StackSize
      //    Stack
      // and waste 8 bytes for an unused time slot:
      //    TimeStackSize
      //    TimeStack
      if (plusSize) {
        return new WithTimeStackSize(ref, -1, stack);
      } else {
        return new WithTimeStack(ref, -1, stack);
      }
    } else if (plusSize) {
      return new WithSize(ref);
    } else {
      // the minimal case
      return new InstanceStats(ref);
    }
  }

  //
  // impls with additional fields
  //
  // we define 7 of the possible 15 permutations.
  // The other 8 permutations are not worth optimizing.
  // See "newInstanceStats(..)" for details.
  //

  private static class WithSize extends InstanceStats {
    // this adds 20 bytes to the basic size, for a total
    // of 60 bytes
    private int size;
    private int capacity;
    private int maxSize;
    private int maxCapacity;
    public WithSize(WeakReference ref) {
      super(ref);
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
  }
  private static class WithTime extends InstanceStats {
    // this adds 8 bytes to the basic size, for a total
    // of 48 bytes
    private final long time;
    public WithTime(WeakReference ref, long time) {
      super(ref);
      this.time = time;
    }
    public long getAllocationTime() {
      return time;
    }
  }
  private static class WithTimeSize extends WithSize {
    // this adds 8 bytes to the super's size, for a total
    // of 68 bytes
    private final long time;
    public WithTimeSize(WeakReference ref, long time) {
      super(ref);
      this.time = time;
    }
    public long getAllocationTime() {
      return time;
    }
  }
  private static class WithTimeStack extends WithTime {
    // The initial Throwable representation uses a lazy native
    // "backtrace" that's used to fill in the elements when they're
    // requested.  A request is any "toString()", "getStackTrace()",
    // "writeObject()", or other accesor.  Once resolved the
    // backtrace is presumably freed.  A backtrace is likely a native
    // array of 32-bit PC addresses.  The resolved StackTraceElement
    // strings are interned, so that cost is amortized out.
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
  private static class WithTimeStackSize extends WithTimeSize {
    // same as above WithTimeStack + 20 bytes for size metrics
    private final Throwable stack;
    public WithTimeStackSize(
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
  private static class WithTimeStackSizeContext extends WithTimeStackSize {
    // The memory cost is the super's cost plus context, which
    // costs somewhere around 80+ bytes, depending upon the stack
    // and number of principles.
    private final InstanceContext context;
    public WithTimeStackSizeContext(
        WeakReference ref,
        long time,
        Throwable stack,
        InstanceContext context) {
      super(ref, time, stack);
      this.context = context;
      if (context == null) {
        throw new InternalError("null context");
      }
    }
    public InstanceContext getInstanceContext() {
      return context;
    }
    public String getAgentName() {
      return getInstanceContext().getAgentName();
    }
  }
}
