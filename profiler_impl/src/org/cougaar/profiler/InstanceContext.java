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

import java.util.Set;
import java.util.List;
import java.util.Iterator;
import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Principal;
import javax.security.auth.Subject;

/**
 * The context of an instance allocation, which is based upon
 * the security stack.
 */
public class InstanceContext {

  public static final InstanceContext getInstanceContext() {
    AccessControlContext _acc = AccessController.getContext();
    if (_acc == null) {
      // can we find our agent though a threadlocal?
      return null;
    }
    return new InstanceContext(_acc);
  }

  public static final InstanceContext NULL = new InstanceContext() {
    public Subject getSubject() {
      return null;
    }
    public Set getPrincipals() {
      return null;
    }
    public String getAgentName() {
      return null;
    }
    public String getComponentName() {
      return null;
    }
  };

  // Cougaar security constants:
  private static final String ROLE_CLASS = 
      "org.cougaar.core.security.auth.role.RoleExecutionContext";
  private static final String ROLE_GET_AGENT = "getAgent";
  private static final String ROLE_GET_COMPONENT = "getComponent";
  private static final String PRINCIPLE_CLASS =
    "org.cougaar.core.security.auth.ChainedPrincipal";
  private static final String PRINCIPLE_GET_CHAIN = "getChain";

  private final AccessControlContext _acc;

  private Subject _subject;
  private Set     _principals;
  private String  _agentName;
  private String  _componentName;

  private InstanceContext() {
    this._acc = null;
  }

  private InstanceContext(AccessControlContext _acc) {
    this._acc = _acc;
    if (_acc == null) {
      throw new IllegalArgumentException("null _acc");
    }
  }

  public Subject getSubject() {
    ensureSubject();
    return _subject;
  }

  public Set getPrincipals() {
    ensureSubject();
    return _principals;
  }

  /** The name of the agent that created the object. */
  public String getAgentName() {
    ensureSubject();
    return _agentName;
  }

  /** The name of the component that created the object. */
  public String getComponentName() {
    ensureSubject();
    return _componentName;
  }

  // must do this lazily to avoid possible VM init error
  private void ensureSubject() {
    if (_subject != null) {
      // we have already retrieved the agent and component names.
      return;
    }
    _subject = (Subject)
      AccessController.doPrivileged(new PrivilegedAction() {
        public Object run() {
          return Subject.getSubject(_acc);
        }
      });
    if (_subject == null) {
      // FIXME save a non-null "_subject" to avoid future calls!
    } else {
      _principals = _subject.getPrincipals();
    }
    // Update agent and component name
    try {
      if (_principals != null) {
        Iterator it = _principals.iterator();
        while (it.hasNext()) {
          Principal p = (Principal) it.next();
          // can't use "X.class", since we may be running very early
          // in the classloading sequence.
          Class pclass = p.getClass();
          String pname = pclass.getName(); 
          if (pname.equals(ROLE_CLASS)) {
            try {
              Method m = pclass.getDeclaredMethod(
                  ROLE_GET_AGENT, null);
              _agentName = m.invoke(p, null).toString();

              m = pclass.getDeclaredMethod(
                  ROLE_GET_COMPONENT, null);
              _componentName = (String) m.invoke(p, null);
            } catch (Exception e) {
              System.err.println("Unable to get principal: " + e);
            }
          } else if (pname.equals(PRINCIPLE_CLASS)) {
            List plist = null;
            try {
              Method m = pclass.getDeclaredMethod(
                  PRINCIPLE_GET_CHAIN, null);
              plist = (List) m.invoke(p, null);
            } catch (Exception e) {
              System.err.println("Unable to get principal: " + e);
            }

            if (plist != null) {
              switch (plist.size()) {
              case 1:
                _agentName = plist.get(0).toString();
                break;
              case 2:
                _agentName = plist.get(1).toString();
                break;
              case 3:
                _componentName = plist.get(2).toString();
                _agentName = plist.get(1).toString();
                break;
              default:
                _agentName = "chain: " + plist.size();
              }
            }
          }
        }
      }
    } catch (Throwable e) {
      System.err.println("Error: " + e);
      e.printStackTrace();
    }
  }
}
