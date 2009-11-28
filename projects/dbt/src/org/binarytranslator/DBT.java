/*
 *  This file is part of MRP (http://mrp.codehaus.org/).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.binarytranslator;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Common utility routines for the running DBT. Generally this class is dressing
 * up methods from org.jikesrvm.VM.
 */
public final class DBT {
  /** Should the following assertion be checked? */
  public static final boolean VerifyAssertions = VM.VerifyAssertions;

  /**
   * Assert the following condition is true, if false then fail with stack trace
   * @param cond the condition that should be true
   */
  public static void _assert(boolean cond) {
    if (!VerifyAssertions) {
      // Checking an assertion in a production build is a bad idea
      fail("Assertion checked when assertions should be disabled.\n"
          + "Please guard the assertion with DBT.VerifyAssertions.");
    } else {
      //VM._assert(cond);
      if (!cond)
        throw new RuntimeException("Assertion failed.");
    }
  }

  /**
   * Exit and print stack trace
   * @param message failure message
   */
  @Uninterruptible
  public static void fail(String message) {
    VM.sysFail(message);
  }
  
  /**
   * Write the given message
   */
  @Uninterruptible
  public static void write(String message) {
    VM.write(message);
  }
  
  /**
   * Write the given int
   */
  @Uninterruptible
  public static void write(int message) {
    VM.write(message);
  }
}
