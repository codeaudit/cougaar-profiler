/** Modified by cougaar
 * <copyright>
 *  Copyright 2003 BBNT Solutions, LLC
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
 **/

/*
 * @(#)Random.java	1.38 02/03/04
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.cougaar.profiler;

import sun.misc.AtomicLong;

/**
 * Trimmed "java.util.Random" for use within the profiler, since we
 * may want to profile "java.util.Random".
 */
public final class Random {
  private final AtomicLong seed;

  private final static long multiplier = 0x5DEECE66DL;
  private final static long addend = 0xBL;
  private final static long mask = (1L << 48) - 1;

  public Random() {
    this(System.currentTimeMillis());
  }

  public Random(long init) {
    seed = AtomicLong.newAtomicLong(0L);
    init = (init ^ multiplier) & mask;
    while (!seed.attemptSet(init)) {
    }
  }

  public float nextFloat() {
    int i = next(24);
    return i / ((float) (1 << 24));
  }

  public double nextDouble() {
    long l = ((long) (next(26)) << 27) + next(27);
    return l / (double) (1L << 53);
  }

  private final int next(int bits) {
    long oldseed, nextseed;
    do {
      oldseed = seed.get();
      nextseed = (oldseed * multiplier + addend) & mask;
    } while (!seed.attemptUpdate(oldseed, nextseed));
    return (int) (nextseed >>> (48 - bits));
  }
}
