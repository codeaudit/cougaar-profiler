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
 * Profiling options.
 *
 * @see DefaultOptionsFactory
 */
public final class Options {

  public static final int TIME_MASK     = (1<<0);
  public static final int STACK_MASK    = (1<<1);
  public static final int SIZE_MASK     = (1<<2);
  public static final int CAPACITY_MASK = (1<<3);
  public static final int CONTEXT_MASK  = (1<<4);

  private final int flags;
  private final double sampleRatio;

  public Options(
      boolean timeEnabled,
      boolean stackEnabled,
      boolean sizeEnabled,
      boolean capacityEnabled,
      boolean contextEnabled,
      double sampleRatio) {
    int flags = 0;
    if (timeEnabled) {
      flags |= TIME_MASK;
    }
    if (stackEnabled) {
      flags |= STACK_MASK;
    }
    if (sizeEnabled) {
      flags |= SIZE_MASK;
    }
    if (capacityEnabled) {
      flags |= CAPACITY_MASK;
    }
    if (contextEnabled) {
      flags |= CONTEXT_MASK;
    }
    this.flags = flags;
    this.sampleRatio = sampleRatio;
  }

  public Options(
      int flags,
      double sampleRatio) {
    this.flags = flags;
    this.sampleRatio = sampleRatio;
  }

  /** 
   * Mask out "isSizeEnabled()" and/or "isCapacityEnabled()" flags
   * on an instance, returning a new instance if necessary.
   * <p>
   * This is used to disable size/capacity profiling on classes
   * that lack these fields.
   */ 
  public Options mask(boolean size, boolean capacity) {
    if ((!size && isSizeEnabled()) ||
        (!capacity && isCapacityEnabled())) {
      int f = flags;
      if (!size) {
        f &= ~SIZE_MASK;
      }
      if (!capacity) {
        f &= ~CAPACITY_MASK;
      }
      return new Options(f, sampleRatio);
    }
    return this;
  }

  public boolean isTimeEnabled() {
    return ((flags & TIME_MASK) != 0);
  }

  public boolean isStackEnabled() {
    return ((flags & STACK_MASK) != 0);
  }

  public boolean isSizeEnabled() {
    return ((flags & SIZE_MASK) != 0);
  }

  public boolean isCapacityEnabled() {
    return ((flags & CAPACITY_MASK) != 0);
  }

  public boolean isContextEnabled() {
    return ((flags & CONTEXT_MASK) != 0);
  }

  public int getFlags() {
    return flags;
  }

  public  double getSampleRatio() {
    return sampleRatio;
  }

  public String toString() {
    return 
      "(options"+
     " time="+isTimeEnabled()+
     " size="+isSizeEnabled()+
     " capacity="+isCapacityEnabled()+
     " context="+isContextEnabled()+
     " sampleRatio="+getSampleRatio()+
     ")";
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (o instanceof Options) {
      Options x = (Options) o;
      return 
        x.flags == flags && 
        x.sampleRatio == sampleRatio;
    } else {
      return false;
    }
  }

  public int hashCode() {
    return flags ^ ((int) sampleRatio);
  }
}
