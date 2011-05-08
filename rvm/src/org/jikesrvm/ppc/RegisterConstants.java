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
import org.jikesrvm.architecture.SizeConstants;
import org.jikesrvm.architecture.MachineRegister;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * Register Usage Conventions for PowerPC.
 */
public interface RegisterConstants extends SizeConstants {
  // Machine instructions.
  //
  int LG_INSTRUCTION_WIDTH = 2;                      // log2 of instruction width in bytes, powerPC
  int INSTRUCTION_WIDTH = 1 << LG_INSTRUCTION_WIDTH; // instruction width in bytes, powerPC

  /**
   * Representation of general purpose registers
   */
  public enum GPR implements MachineRegister {
    R0(0),   R1(1),   R2(2),   R3(3),   R4(4),   R5(5),   R6(6),   R7(7),   R8(8),   R9(9),
    R10(10), R11(11), R12(12), R13(13), R14(14), R15(15), R16(16), R17(17), R18(18), R19(19),
    R20(20), R21(21), R22(22), R23(23), R24(24), R25(25), R26(26), R27(27), R28(28), R29(29),
    R30(30), R31(31);

    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final GPR[] vals = values();

    /** Constructor a register with the given encoding value */
    GPR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    /** @return encoded value of this register */
    @UninterruptibleNoWarn("Interruptible code only called during boot image creation")
    @Pure
    public byte value() {
      byte result;
      if (!org.jikesrvm.VM.runningVM) {
        result = (byte)ordinal();
      } else {
        result = (byte)java.lang.JikesRVMSupport.getEnumOrdinal(this);
      }
      if (VM.VerifyAssertions) {
        VM._assert(result >=0 && result <= 31);
      }
      return result;
    }
    /**
     * Convert encoded value into the GPR it represents
     * @param num encoded value
     * @return represented GPR
     */
    @Uninterruptible
    @Pure
    public static GPR lookup(int num) {
      return vals[num];
    }
    /** @return register next register to this one (e.g. R1 for R0) */
    @Uninterruptible
    @Pure
    public GPR nextGPR() {
      return lookup(value()+1);
    }
  }

  /**
   * Super interface for floating point registers
   */
  public interface FloatingPointMachineRegister extends MachineRegister {
  }

  /**
   * Representation of floating point registers
   */
  public enum FPR implements FloatingPointMachineRegister {
    FR0(0),   FR1(1),   FR2(2),   FR3(3),   FR4(4),   FR5(5),   FR6(6),   FR7(7),   FR8(8),   FR9(9),
    FR10(10), FR11(11), FR12(12), FR13(13), FR14(14), FR15(15), FR16(16), FR17(17), FR18(18), FR19(19),
    FR20(20), FR21(21), FR22(22), FR23(23), FR24(24), FR25(25), FR26(26), FR27(27), FR28(28), FR29(29),
    FR30(30), FR31(31);

    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final FPR[] vals = values();

    /** Constructor a register with the given encoding value */
    FPR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    /** @return encoded value of this register */
    @UninterruptibleNoWarn("Interruptible code only called during boot image creation")
    @Pure
    public byte value() {
      byte result;
      if (!org.jikesrvm.VM.runningVM) {
        result = (byte)ordinal();
      } else {
        result = (byte)java.lang.JikesRVMSupport.getEnumOrdinal(this);
      }
      if (VM.VerifyAssertions) {
        VM._assert(result >=0 && result <= 31);
      }
      return result;
    }
    /**
     * Convert encoded value into the FPR it represents
     * @param num encoded value
     * @return represented GPR
     */
    @Pure
    public static FPR lookup(int num) {
      return vals[num];
    }
  }

  /**
   * Representation of condition registers
   */
  public enum CR implements MachineRegister {
    CR0(0),   CR1(1),   CR2(2),   CR3(3),   CR4(4),   CR5(5),   CR6(6),   CR7(7);
    /** Constructor a register with the given encoding value */
    CR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    /** @return encoded value of this register */
    @Pure
    public byte value() {
      return (byte)ordinal();
    }
  }

  // OS register convention (for mapping parameters in JNI calls)
  // These constants encode conventions for AIX, OSX, and Linux.
  GPR FIRST_OS_PARAMETER_GPR   = GPR.R3;
  GPR LAST_OS_PARAMETER_GPR    = GPR.R10;
  GPR FIRST_OS_VOLATILE_GPR    = GPR.R3;
  GPR LAST_OS_VOLATILE_GPR     = GPR.R12;
  GPR FIRST_OS_NONVOLATILE_GPR = (VM.BuildForAix && VM.BuildFor64Addr) ? GPR.R14 : GPR.R13;
  GPR LAST_OS_NONVOLATILE_GPR  = GPR.R31;
  FPR FIRST_OS_PARAMETER_FPR   = FPR.FR1;
  FPR LAST_OS_PARAMETER_FPR    = VM.BuildForLinux ? FPR.FR8 : FPR.FR13;
  FPR FIRST_OS_VOLATILE_FPR    = FPR.FR1;
  FPR LAST_OS_VOLATILE_FPR     = FPR.FR13;
  FPR FIRST_OS_NONVOLATILE_FPR = FPR.FR14;
  FPR LAST_OS_NONVOLATILE_FPR  = FPR.FR31;
  FPR LAST_OS_VARARG_PARAMETER_FPR = VM.BuildForAix ? FPR.FR6 : FPR.FR8;

  // Jikes RVM's general purpose register usage (32 or 64 bits wide based on VM.BuildFor64Addr).
  //
  GPR REGISTER_ZERO = GPR.R0; // special instruction semantics on this register

  GPR FRAME_POINTER = GPR.R1; // same as AIX/OSX/Linux
  GPR FIRST_VOLATILE_GPR = FIRST_OS_PARAMETER_GPR;
  //                                            ...
  GPR LAST_VOLATILE_GPR = LAST_OS_PARAMETER_GPR;
  GPR FIRST_SCRATCH_GPR = GPR.lookup(LAST_VOLATILE_GPR.value()+1);
  GPR LAST_SCRATCH_GPR = LAST_OS_VOLATILE_GPR;
  // AIX 64 bit ABI reserves R13 for use by libpthread; therefore Jikes RVM doesn't touch it.
  GPR FIRST_RVM_RESERVED_NV_GPR = VM.BuildFor64Addr ? GPR.R14 : GPR.R13;
  GPR THREAD_REGISTER = FIRST_RVM_RESERVED_NV_GPR;

  // 2 is used by Linux for thread context, on AIX it's the toc and on OS X it's a scratch.
  GPR JTOC_POINTER = GPR.lookup(THREAD_REGISTER.value() + (VM.BuildForLinux && VM.BuildFor32Addr ? 1 : 2));
  GPR KLUDGE_TI_REG = GPR.lookup(THREAD_REGISTER.value() + (VM.BuildForLinux && VM.BuildFor32Addr ? 2 : 1));

  GPR LAST_RVM_RESERVED_NV_GPR = KLUDGE_TI_REG; // will become PR when KLUDGE_TI dies.
  GPR FIRST_NONVOLATILE_GPR = GPR.values()[LAST_RVM_RESERVED_NV_GPR.value() + 1];
  //                                            ...
  GPR LAST_NONVOLATILE_GPR = LAST_OS_NONVOLATILE_GPR;
  int NUM_GPRS = 32;

  // Floating point register usage. (FPR's are 64 bits wide).
  //
  FPR FIRST_SCRATCH_FPR = FPR.FR0; // AIX/OSX/Linux is 0
  FPR LAST_SCRATCH_FPR  = FPR.FR0; // AIX/OSX/Linux is 0
  FPR FIRST_VOLATILE_FPR = FIRST_OS_VOLATILE_FPR;
  //                                            ...
  FPR LAST_VOLATILE_FPR = LAST_OS_VOLATILE_FPR;
  FPR FIRST_NONVOLATILE_FPR = FIRST_OS_NONVOLATILE_FPR;
  //                                            ...
  FPR LAST_NONVOLATILE_FPR = LAST_OS_NONVOLATILE_FPR;
  int NUM_FPRS = 32;

  int NUM_NONVOLATILE_GPRS = LAST_NONVOLATILE_GPR.value() - FIRST_NONVOLATILE_GPR.value() + 1;
  int NUM_NONVOLATILE_FPRS = LAST_NONVOLATILE_FPR.value() - FIRST_NONVOLATILE_FPR.value() + 1;

  // condition registers
  // TODO: fill table
  int NUM_CRS = 8;

  // special registers (user visible)
  int NUM_SPECIALS = 8;
}

