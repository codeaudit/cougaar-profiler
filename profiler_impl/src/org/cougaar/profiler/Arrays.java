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
 * Placeholder for RFE:
 * <p>
 * Profiled classes can replace all "newarray" calls with equivalent
 * calls in this class, which will track the allocated primitive
 * array.
 */
public final class Arrays {

  // array header size in bytes
  private static final int HEADER = 8;

  // does InstanceStats support arrays yet?
  private static final boolean LENGTH = false;

  private Arrays() { }

  // "Object[][] multianewarray(count, dimensions)"
  //
  // source looks like:
  //   new int[7][42][13][][] 
  // bytecode is:
  //   iconst 7 
  //   iconst 42
  //   iconst 13
  //   multianewarray [[[[[I 3 
  public static final void multianewarray(Object[] a) {
    newObjectarray(a);
    for (int i = 0, n = a.length; i < n; i++) {
      Object oi = a[i];
      if (oi instanceof Object[]) {
        multianewarray((Object[]) oi);
      }
    }
  }
  // public static final Object[] multianewarray(
  //   int count, int dim) {
  //     ???  requires int[] of dimensions
  // } 

  /* #!/bin/sh
     for T in boolean byte char short int float double long Object; do 
       P=`echo ${T}_ARRAYS | tr [:lower:] [:upper:]`
       cat <<EOF

       public static final MemoryTracker ${P} =
         MemoryTracker.getInstance(
             "${T}[]", HEADER, false, LENGTH);
       public static final void new${T}array(${T}[] a) {
         ${P}.add(a);
       }
       public static final ${T}[] new${T}array(int count) {
         ${T}[] a = new ${T}[count];
         ${P}.add(a);
         return a;
       }
     EOF
     done 
   */
  public static final MemoryTracker BOOLEAN_ARRAYS =
    MemoryTracker.getInstance(
        "boolean[]", HEADER, false, LENGTH);
  public static final void newbooleanarray(boolean[] a) {
    BOOLEAN_ARRAYS.add(a);
  }
  public static final boolean[] newbooleanarray(int count) {
    boolean[] a = new boolean[count];
    BOOLEAN_ARRAYS.add(a);
    return a;
  }

  public static final MemoryTracker BYTE_ARRAYS =
    MemoryTracker.getInstance(
        "byte[]", HEADER, false, LENGTH);
  public static final void newbytearray(byte[] a) {
    BYTE_ARRAYS.add(a);
  }
  public static final byte[] newbytearray(int count) {
    byte[] a = new byte[count];
    BYTE_ARRAYS.add(a);
    return a;
  }

  public static final MemoryTracker CHAR_ARRAYS =
    MemoryTracker.getInstance(
        "char[]", HEADER, false, LENGTH);
  public static final void newchararray(char[] a) {
    CHAR_ARRAYS.add(a);
  }
  public static final char[] newchararray(int count) {
    char[] a = new char[count];
    CHAR_ARRAYS.add(a);
    return a;
  }

  public static final MemoryTracker SHORT_ARRAYS =
    MemoryTracker.getInstance(
        "short[]", HEADER, false, LENGTH);
  public static final void newshortarray(short[] a) {
    SHORT_ARRAYS.add(a);
  }
  public static final short[] newshortarray(int count) {
    short[] a = new short[count];
    SHORT_ARRAYS.add(a);
    return a;
  }

  public static final MemoryTracker INT_ARRAYS =
    MemoryTracker.getInstance(
        "int[]", HEADER, false, LENGTH);
  public static final void newintarray(int[] a) {
    INT_ARRAYS.add(a);
  }
  public static final int[] newintarray(int count) {
    int[] a = new int[count];
    INT_ARRAYS.add(a);
    return a;
  }

  public static final MemoryTracker FLOAT_ARRAYS =
    MemoryTracker.getInstance(
        "float[]", HEADER, false, LENGTH);
  public static final void newfloatarray(float[] a) {
    FLOAT_ARRAYS.add(a);
  }
  public static final float[] newfloatarray(int count) {
    float[] a = new float[count];
    FLOAT_ARRAYS.add(a);
    return a;
  }

  public static final MemoryTracker DOUBLE_ARRAYS =
    MemoryTracker.getInstance(
        "double[]", HEADER, false, LENGTH);
  public static final void newdoublearray(double[] a) {
    DOUBLE_ARRAYS.add(a);
  }
  public static final double[] newdoublearray(int count) {
    double[] a = new double[count];
    DOUBLE_ARRAYS.add(a);
    return a;
  }

  public static final MemoryTracker LONG_ARRAYS =
    MemoryTracker.getInstance(
        "long[]", HEADER, false, LENGTH);
  public static final void newlongarray(long[] a) {
    LONG_ARRAYS.add(a);
  }
  public static final long[] newlongarray(int count) {
    long[] a = new long[count];
    LONG_ARRAYS.add(a);
    return a;
  }

  public static final MemoryTracker OBJECT_ARRAYS =
    MemoryTracker.getInstance(
        "Object[]", HEADER, false, LENGTH);
  public static final void newObjectarray(Object[] a) {
    OBJECT_ARRAYS.add(a);
  }
  public static final Object[] newObjectarray(int count) {
    Object[] a = new Object[count];
    OBJECT_ARRAYS.add(a);
    return a;
  }
}
