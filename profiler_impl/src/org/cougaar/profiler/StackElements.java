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
 * Wrapper around a Throwable for stack-based equality.
 * @see Groupings#STACK_GROUP
 */
public final class StackElements {

  private final Throwable throwable;
  private StackTraceElement[] elements;
  private int _hc;

  public StackElements(Throwable throwable) {
    this.throwable = throwable;
    if (throwable == null) {
      throw new IllegalArgumentException("null throwable");
    }
  }

  public Throwable getThrowable() {
    return throwable;
  }

  private StackTraceElement[] getStackTrace() {
    // cache the array, otherwise each access is a clone
    if (elements == null) {
      elements = throwable.getStackTrace();
    }
    return elements;
  }

  public int hashCode() {
    if (_hc == 0) {
      _hc = 1;
      StackTraceElement[] st = getStackTrace();
      for (int i = 0, n = st.length; i < n; i++) {
        _hc = 31*_hc + st[i].hashCode();
      }
    }
    return _hc;
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    } else if (!(o instanceof StackElements)) {
      return false;
    }
    StackTraceElement[] a_st = getStackTrace();
    StackTraceElement[] b_st = ((StackElements) o).getStackTrace();
    if (a_st.length != b_st.length) {
      return false;
    }
    for (int i = 0, n = a_st.length; i < n; i++) {
      if (!a_st[i].equals(b_st[i])) {
        return false;
      }
    }
    return true;
  }

  public String toString() {
    return throwable.toString();
  }
}
