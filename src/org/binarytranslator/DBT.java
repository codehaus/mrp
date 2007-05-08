/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright The University of Manchester 2003-2007
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
  public static final boolean VerifyAssertions = true;

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
