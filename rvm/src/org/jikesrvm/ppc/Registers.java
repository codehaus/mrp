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
package org.jikesrvm.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.architecture.AbstractRegisters;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import static org.jikesrvm.ppc.RegisterConstants.FRAME_POINTER;

/**
 * The machine state comprising a thread's execution context.
 */
@Uninterruptible
public final class Registers extends AbstractRegisters {
  // The following are used by exception delivery.
  // They are set by either Runtime.athrow or the C hardware exception
  // handler and restored by "Magic.restoreHardwareExceptionState".
  // They are not used for context switching.
  //
  public Address lr;     // link register

  private static final Address invalidIP = Address.max();

  public Registers() {
    ip = invalidIP;
  }

  @Override
  public final void clear() {
    lr=Address.zero();
    super.clear();
  }
  public final void dump() {
    super.dump();
    VM.sysWriteln("lr = ",lr);
  }

  /** @return framepointer for the deepest stackframe */
  public final Address getInnermostFramePointer() {
    return getGPRs().get(FRAME_POINTER.value()).toAddress();
  }

  /** @return next instruction address for the deepest stackframe */
  public final Address getInnermostInstructionAddress() {
    if (ip.NE(invalidIP)) return ip; // ip set by hardware exception handler or Magic.threadSwitch
    return Magic.getNextInstructionAddress(getInnermostFramePointer()); // ip set to -1 because we're unwinding
  }

  /** Update the machine state to unwind the deepest stackframe. */
  public final void unwindStackFrame() {
    ip = invalidIP; // if there was a valid value in ip, it ain't valid anymore
    getGPRs().set(FRAME_POINTER.value(), Magic.getCallerFramePointer(getInnermostFramePointer()).toWord());
  }

  /**
   * Set ip &amp; fp. used to control the stack frame at which a scan of
   * the stack during GC will start, for ex., the top java frame for
   * a thread that is blocked in native code during GC.
   */
  public final void setInnermost(Address newip, Address newfp) {
    ip = newip;
    getGPRs().set(FRAME_POINTER.value(), newfp.toWord());
  }

  /**
   * Set ip and fp values to those of the caller. used just prior to entering
   * sigwait to set fp &amp; ip so that GC will scan the threads stack
   * starting at the frame of the method that called sigwait.
   */
  public final void setInnermost() {
    Address fp = Magic.getFramePointer();
    ip = Magic.getReturnAddress(fp);
    getGPRs().set(FRAME_POINTER.value(), Magic.getCallerFramePointer(fp).toWord());
  }

 /**
   * The following method initializes a thread stack as if
   * "startoff" method had been called by an empty baseline-compiled
   *  "sentinel" frame with one local variable
   *
   * @param contextRegisters The context registers for this thread
   * @param ip The instruction pointer for the "startoff" method
   * @param sp The base of the stack
   */
  @Uninterruptible
  public final void initializeStack(Address ip, Address sp) {
    Address fp;
    // align stack frame
    int INITIAL_FRAME_SIZE = StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
    fp = Memory.alignDown(sp.minus(INITIAL_FRAME_SIZE), StackframeLayoutConstants.STACKFRAME_ALIGNMENT);
    fp.plus(StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET).store(StackframeLayoutConstants.STACKFRAME_SENTINEL_FP);
    fp.plus(StackframeLayoutConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET).store(ip); // need to fix
    fp.plus(StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET).store(StackframeLayoutConstants.INVISIBLE_METHOD_ID);

    getGPRs().set(FRAME_POINTER.value(), fp.toWord());
    this.ip = ip;
  }

  @Override
  public void adjustESP(Offset delta, boolean traceAdjustments) {
    // TODO Auto-generated method stub
    throw new Error("TODO");
  }
}
