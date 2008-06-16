/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.inlining;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.opt.OptOptions;

/**
 * Instances of this class represent decisions to inline.
 */
public final class InlineDecision {

  /**
   * Return a decision NOT to inline.
   *
   * @param target the method that is not being inlined.
   * @param reason a rationale for not inlining
   * @return a decision NOT to inline
   */
  public static InlineDecision NO(RVMMethod target, String reason) {
    RVMMethod[] targets = new RVMMethod[1];
    targets[0] = target;
    return new InlineDecision(targets, null, DECIDE_NO, reason);
  }

  /**
   * Return a decision NOT to inline.
   *
   * @param reason a rationale for not inlining
   * @return a decision NOT to inline
   */
  public static InlineDecision NO(String reason) {
    return new InlineDecision(null, null, DECIDE_NO, reason);
  }

  /**
   * Return a decision to inline without a guard.
   * @param target the method to inline
   * @param reason a rationale for inlining
   * @return a decision YES to inline
   */
  public static InlineDecision YES(RVMMethod target, String reason) {
    RVMMethod[] targets = new RVMMethod[1];
    targets[0] = target;
    return new InlineDecision(targets, null, DECIDE_YES, reason);
  }

  /**
   * Return a decision YES to do a guarded inline.
   *
   * @param target the method to inline
   * @param guard  the type of guard to use
   * @param reason a rationale for inlining
   * @return a decision YES to inline, but it is not always safe.
   */
  public static InlineDecision guardedYES(RVMMethod target, byte guard, String reason) {
    RVMMethod[] targets = new RVMMethod[1];
    byte[] guards = new byte[1];
    targets[0] = target;
    guards[0] = guard;
    return new InlineDecision(targets, guards, GUARDED_YES, reason);
  }

  /**
   * Return a decision YES to do a guarded inline.
   *
   * @param targets   The methods to inline
   * @param guards  the types of guard to use
   * @param reason   A rationale for inlining
   * @return a decision YES to inline, but it is not always safe.
   */
  public static InlineDecision guardedYES(RVMMethod[] targets, byte[] guards, String reason) {
    return new InlineDecision(targets, guards, GUARDED_YES, reason);
  }

  /**
   * Is this inline decision a YES?
   */
  public boolean isYES() {
    return !isNO();
  }

  /**
   * Is this inline decision a NO?
   */
  public boolean isNO() {
    return (code == DECIDE_NO);
  }

  /**
   * Does this inline site need a guard?
   */
  public boolean needsGuard() {
    return (code == GUARDED_YES);
  }

  /**
   * Return the methods to inline according to this decision.
   */
  public RVMMethod[] getTargets() {
    return targets;
  }

  /**
   * Return the guards to use according to this decision.
   */
  public byte[] getGuards() {
    return guards;
  }

  /**
   * Return the number methods to inline.
   */
  public int getNumberOfTargets() {
    if (targets == null) {
      return 0;
    }
    return targets.length;
  }

  /**
   * Should the test-failed block be replaced with an OSR point?
   */
  private boolean testFailedOSR = false;

  public void setOSRTestFailed() { testFailedOSR = true; }

  public boolean OSRTestFailed() { return testFailedOSR; }

  /**
   * Symbolic constant coding internal state.
   */
  private static final short DECIDE_NO = 0;
  /**
   * Symbolic constant coding internal state.
   */
  private static final short DECIDE_YES = 1;
  /**
   * Symbolic constant coding internal state.
   */
  private static final short GUARDED_YES = 2;
  /**
   * Rationale for this decision
   */
  private String rationale;
  /**
   * Holds characterization of this decision.
   */
  private short code;
  /**
   * The set of methods to inline.
   */
  private RVMMethod[] targets;
  /**
   * The set of guards to use
   * (only valid when code == GUARDED_YES)
   */
  private byte[] guards;

  /**
   * @param targets   The methods to inline
   * @param code the decision code
   * @param reason a string rationale
   */
  private InlineDecision(RVMMethod[] targets, byte[] guards, short code, String reason) {
    this(code, reason);
    this.targets = targets;
    this.guards = guards;
  }

  /**
   * @param code the decision code
   * @param reason a string rationale
   */
  private InlineDecision(short code, String reason) {
    this.code = code;
    this.rationale = reason;
  }

  public String toString() {
    String s = null;
    if (code == DECIDE_NO) {
      s = "DECIDE_NO";
    } else if (code == DECIDE_YES) {
      s = "DECIDE_YES";
    } else if (code == GUARDED_YES) {
      s = "GUARDED_YES";
    }
    if (testFailedOSR) {
      s += "(OSR off-branch)";
    }
    s += ":" + rationale;
    if (targets != null) {
      for (int i = 0; i < targets.length; i++) {
        s += " " + targets[i];
        if (guards != null) {
          switch (guards[i]) {
            case OptOptions.IG_METHOD_TEST:
              s += " (method test)";
              break;
            case OptOptions.IG_CLASS_TEST:
              s += " (class test)";
              break;
            case OptOptions.IG_CODE_PATCH:
              s += " (code patch)";
              break;
          }
        }
      }
    }
    return s;
  }
}



