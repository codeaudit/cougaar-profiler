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

abstract class InstancesTable {

  // we keep something similar to an IdentityHashMap, but we don't
  // actually hash.  The caller guarantees that there will only be
  // one "add(obj)" per instance.
  //
  // We could keep a simple linked list, but we want to periodically
  // poll the list to trim out gc'ed entries.  Scanning the entire
  // list could be slow.
  //
  // Here we keep a simple array of lists, keyed by identity
  // hashcode.  When we add an entry we poll the entries on that
  // list.  This limits our scanning overhead to 1/N.  We don't
  // actually need our table to be a hash, but it's implemented that
  // way for simplicity.
  private InstanceStats[] objs;
  private int objs_size;
  private int objs_threshold;

  // cheap iterator:
  private int iter_i;
  private int iter_max;
  private InstanceStats iter_next;
  private InstanceStats iter_prev;

  public InstancesTable() {
  }

  protected abstract void allocate(InstanceStats is);
  protected abstract void gc(InstanceStats is);

  public final int size() {
    return objs_size;
  }

  public final void startIterator() {
    iter_i = -1;
    iter_max = (objs == null ? 0 : objs.length);
    iter_next = null;
    iter_prev = null;
  }
  public final InstanceStats next() {
    while (true) {
      if (iter_next == null) {
        iter_prev = null;
        if (++iter_i >= iter_max) {
          return null;
        }
        iter_next = objs[iter_i]; 
        continue;
      }
      if (iter_next.get() == null) {
        // gc'ed
        gc(iter_next);
        objs_size--;
        InstanceStats dead = iter_next;
        iter_next = dead.next;
        dead.next = null;
        if (iter_prev == null) {
          objs[iter_i] = iter_next;
        } else {
          iter_prev.next = iter_next;
        }
        continue;
      }
      InstanceStats ret = iter_next;
      iter_prev = ret;
      iter_next = ret.next;
      return ret;
    }
  }

  private static final int hash(Object x, int length) {
    int h = System.identityHashCode(x);
    // assert (length % 2 == 0)
    return h & (length-1);
  }

  private final void resize() {
    // length must be power of two for fast "&" hashing,
    // otherwise we would use "%"
    InstanceStats[] oldTable = objs;
    if (oldTable == null) {
      objs = new InstanceStats[8];
      objs_threshold = objs.length * 7;
      return;
    }
    int oldCapacity = oldTable.length;

    int newCapacity = (oldCapacity << 1);
    InstanceStats[] newTable = new InstanceStats[newCapacity];

    for (int i = 0; i < oldCapacity; i++) {
      InstanceStats next = null;
      for (InstanceStats is = oldTable[i];
          is != null;
          is = next) {
        next = is.next;
        is.next = null;
        Object o = is.get();
        if (o == null) {
          gc(is);
          objs_size--;
          continue;
        }
        int h = hash(o, newCapacity);
        InstanceStats new_is = newTable[h];
        if (new_is != null) {
          is.next = new_is;
        }
        newTable[h] = is;
      }
    }
    objs = newTable;
    objs_threshold = newCapacity * 7;
  }

  public final void put(Object new_o, InstanceStats new_is) {
    if (objs == null) {
      resize();
    }
    int h = hash(new_o, objs.length);
    // assume this is new and insert at the head
    InstanceStats old_is = objs[h];
    new_is.next = old_is;
    objs[h] = new_is;
    allocate(new_is);
    objs_size++;
    if (objs_size >= objs_threshold) {
      // resize, scan for gc'ed entries
      resize();
    } else {
      // while we're here, let's scan for gc'ed entries
      InstanceStats prev = new_is; 
      InstanceStats is = old_is; 
      while (is != null) {
        Object o = is.get();
        if (o == null) {
          gc(is);
          objs_size--;
          InstanceStats dead = is;
          is = dead.next;
          dead.next = null;
          prev.next = is;
        } else {
          // check for (o == new_o)?
          if (o == new_o) {
            System.err.println("put: already have object");
            (new Throwable()).printStackTrace();
            System.exit(1);
          } 
          prev = is;
          is = is.next;
        }
      }
    }
  }
}
