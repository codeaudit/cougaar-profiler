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
 * Simple map of (String, ClassStats) pairs.
 */
class AgentsTable {

  private int count;
  private String[] names;
  private ClassStats[] stats;

  public String[] getNames() {
    String[] ret = new String[count];
    for (int i = 0; i < count; i++) {
      ret[i] = names[i];
    }
    return ret;
  }

  public ClassStats get(String agent) {
    agent = (agent == null ? null : agent.intern());
    for (int j = 0; j < count; j++) {
      if (names[j] == agent) {
        return stats[j];
      }
    }
    return null;
  }

  public void put(String agent, ClassStats cs) {
    // assert (get(agent) == null);
    if (count == 0) {
      names = new String[17];
      stats = new ClassStats[17];
    }
    if ((count + 1) >= names.length) {
      int newCapacity = 2 * count;
      String[] oldAgentNames = names;
      names = new String[newCapacity];
      System.arraycopy(oldAgentNames, 0, names, 0, count);
      ClassStats[] oldAgentStats = stats;
      stats = new ClassStats[newCapacity];
      System.arraycopy(oldAgentStats, 0, stats, 0, count);
    }
    names[count] = agent;
    stats[count] = cs;
    ++count;
  }

  public void reset() {
    for (int i = 0; i < count; i++) {
      ClassStats cs = stats[i];
      cs.resetInstances();
      cs.reset();
    }
  }
}
