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

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import java.lang.reflect.*;
/**
 * Creates MemoryTracker instances.
 */
public class MemoryStatsImpl
implements MemoryStats
{
  private static long startupTime;
  private static MemoryStats instance;

  private static final Object lock = new Object();
  private static String[] names;
  private static ClassTracker[] trackers;
  private static int count;

  MemoryStatsImpl() { }

  public String[] getClassNames() {
    synchronized (lock) {
      String[] ret = new String[count];
      for (int i = 0; i < count; i++) {
        ret[i] = names[i];
      }
      return ret;
    }
  }

  public ClassTracker getClassTracker(String classname) {
    synchronized (lock) {
      for (int i = 0; i < count; i++) {
        if (classname.equals(names[i])) {
          return trackers[i];
        }
      }
      return null;
    }
  }

  public MemoryTracker getMemoryTracker(
      String classname,
      int bytes,
      boolean has_size,
      boolean has_capacity) {
    synchronized (lock) {
      // find
      for (int i = 0; i < count; i++) {
        if (classname.equals(names[i])) {
          return trackers[i];
        }
      }
      // ensure capacity
      if (count == 0) {
        names = new String[17];
        trackers = new ClassTracker[17];
      } else if ((count + 1) >= names.length) {
        int newCapacity = (count << 1);
        String[] new_cn = new String[newCapacity];
        ClassTracker[] new_ct = new ClassTracker[newCapacity];
        for (int i = 0; i < count; i++) {
          new_cn[i] = names[i];
          new_ct[i] = trackers[i];
        }
        names = new_cn;
        trackers = new_ct;
      }
      // add
      ClassTracker ct = ClassTracker.newClassTracker(
          classname, bytes, has_size, has_capacity);
      names[count] = classname;
      trackers[count] = ct;
      count++;
      return ct;
    }
  }

  public static synchronized MemoryStats getInstance() {
    // we want a true VM singleton even if there are multiple
    // classloaders

    // if we're profiling "java.util" and attempt:
    //    ClassLoader x = lock.getClass().getClassLoader();
    // we'll cause a fatal VM stack overflow, since ClassLoader
    // itself uses "java.util"!  It's also not safe to ask for a
    // stacktrace!  The ugly workaround is to assume we're in the
    // root until enough time has passed.
    if (Configure.DELAY_AFTER_STARTUP > 0) {
      long now = System.currentTimeMillis();
      if (startupTime == 0) {
        startupTime = now;
      }
      long diff = (now - startupTime);
      if (diff < Configure.DELAY_AFTER_STARTUP) {
        // assume we're the root classloader.  It's fine to
        // allocate instances since they're all static fields.
        // It's not clear if this is safe; we need to test with
        // more complex classloader namespace configurations.
        //
        // this may be fixable by queueing "add(o)" calls... 
        return new MemoryStatsImpl();
      }
    }

    // "Absolute Singleton" pattern by Inigo Surguy (www.surguy.net)
    if (instance == null) {
      ClassLoader loader = lock.getClass().getClassLoader();
      if (loader == null || loader.toString().startsWith("sun.")) {
        // root classloader
        instance = new MemoryStatsImpl();
      } else {
        // create proxy
        try {
          ClassLoader parentLoader = loader.getParent();
          final Class parentClass = parentLoader.loadClass(
              "org.cougaar.profiler.MemoryStatsImpl");
          Method getInstanceMethod = parentClass.getDeclaredMethod(
              "getInstance", new Class[] { });
          final Object parentInstance = getInstanceMethod.invoke(
              null, new Object[] { } );
          if (parentInstance == null) {
            throw new RuntimeException("Parent instance is null");
          }
          Class ifc = Class.forName("org.cougaar.profiler.MemoryStats");
          InvocationHandler ih = new InvocationHandler() {
            public Object invoke(
                Object proxy,
                Method method,
                Object[] args) throws Throwable {
              Method delegateMethod = 
                parentClass.getMethod(
                    method.getName(), method.getParameterTypes());
              return delegateMethod.invoke(parentInstance, args);
            }
          };
          instance = (MemoryStats) Proxy.newProxyInstance(
                loader, new Class[] { ifc }, ih);
        } catch (Exception e) {
          throw new RuntimeException("Unable to create proxy", e);
        }
      }
    }

    return instance;
  }

  static {
    // launch thread to periodically update our class trackers.
    //
    // This is important for GC, to make sure we eventually
    // free InstanceStats that reference GC'd objects.
    //
    // This thread also updates the "size" class stats.
    Runnable r = new Runnable() {
      public void run() {
        while (true) {
          try {
            Thread.sleep(Configure.UPDATE_STATS_FREQUENCY);
          } catch (InterruptedException ex) {
          }
          timerUpdate();
        }
      }
    };
    Thread t = new Thread(r, "MemoryTracker update");
    t.setDaemon(true);
    t.setPriority(Thread.MIN_PRIORITY);
    t.start();
  }

  private static void timerUpdate() {
    ClassTracker[] cts;
    synchronized (lock) {
      cts = new ClassTracker[count];
      for (int i = 0; i < count; i++) {
        cts[i] = trackers[i];
      }
    }
    for (int i = 0; i < cts.length; i++) {
      try {
        cts[i].timerUpdate();
      } catch (Exception e) {
      }
    }
  }
}
