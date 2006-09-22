/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import org.vmmagic.pragma.*;

/**
 * A VM_NullListener is an object that is invoked when
 * online measurement information must be collected.
 *
 * Defines update's interface.
 *
 * @author Peter Sweeney
 * @date   2 June 2000
 */

abstract class VM_NullListener extends VM_Listener implements Uninterruptible {
  /**
   * Entry point when listener is awoken.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *             EPILOGUE?
   */
  abstract public void update(int whereFrom);
}
