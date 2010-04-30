/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
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
package org.jikesrvm.ia32;

import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.jikesrvm.ia32.RegisterConstants.GPR;

/**
 * This class provides a layer of abstraction that the rest of the VM must
 * use in order to access the current <code>RVMThread</code> object.
 *
 * @see RVMThread
 */
public abstract class ThreadLocalState {

  protected static final GPR THREAD_REGISTER = RegisterConstants.ESI;

  /**
   * The C bootstrap program has placed a pointer to the initial
   * RVMThread in ESI.
   */
  @Uninterruptible
  public
  static void boot() {
    // do nothing - everything is already set up.
  }

  /**
   * Return the current RVMThread object
   */
  @Uninterruptible
  public static RVMThread getCurrentThread() {
    return Magic.getESIAsThread();
  }

  /**
   * Set the current RVMThread object
   */
  @Uninterruptible
  public static void setCurrentThread(RVMThread p) {
    Magic.setESIAsThread(p);
  }
}

