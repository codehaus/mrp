/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package com.ibm.jikesrvm;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Wrappers around IA32-specific code common to both 32 & 64 bit
 * 
 * $Id: OPT_IA32ConditionOperand.java 10996 2006-11-16 23:37:12Z dgrove-oss $
 * 
 * @author Steve Blackburn
 */
public abstract class VM_MachineSpecificIA extends VM_MachineSpecific implements VM_Constants {

  /**
   * A well-known memory location used to manipulate the FPU control word.
   */
  static int FPUControlWord;

  /**
   * Wrappers around IA32-specific code (32-bit specific)
   */
  public static final class IA32 extends VM_MachineSpecificIA {
    public static final IA32 singleton = new IA32();
  }
  
  /**
   * Wrappers around EMT64-specific code (64-bit specific)
   */
  public static final class EM64T extends VM_MachineSpecificIA {
    public static final EM64T singleton = new EM64T();
  }
 
  
  /* 
   * Generic (32/64 neutral) IA support
   */
  
  /* common to all ISAs */
  /**
   * The following method will emit code that moves a reference to an
   * object's TIB into a destination register.
   *
   * @param asm the assembler object to emit code with
   * @param dest the number of the destination register
   * @param object the number of the register holding the object reference
   * @param tibOffset the offset of the tib from the object header
   */
  @Interruptible
  public final void baselineEmitLoadTIB(VM_Assembler asm, int dest, int object, Offset tibOffset) { 
    asm.emitMOV_Reg_RegDisp((byte) dest, (byte) object, tibOffset);
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
  public final void initializeStack(VM_Registers contextRegisters, Address ip, Address sp) {
    Address fp;
    sp = sp.minus(STACKFRAME_HEADER_SIZE);                   // last word of header
    fp = sp.minus(BYTES_IN_ADDRESS + STACKFRAME_BODY_OFFSET);  
    VM_Magic.setCallerFramePointer(fp, STACKFRAME_SENTINEL_FP);
    VM_Magic.setCompiledMethodID(fp, INVISIBLE_METHOD_ID);

    sp = sp.minus(BYTES_IN_ADDRESS);                                 // allow for one local
    contextRegisters.gprs.set(ESP, sp.toWord());
    contextRegisters.gprs.set(VM_BaselineConstants.JTOC,
                              VM_Magic.objectAsAddress(VM_Magic.getJTOC()).toWord());
    contextRegisters.fp  = fp;
    contextRegisters.ip  = ip;    
  }
 
  /* unique to IA */
  
  /**
   * A thread's stack has been moved or resized.
   * Adjust the ESP register to reflect new position.
   * 
   * @param VM_Registers The registers for this thread
   * @param delta The displacement to be applied
   * @param traceAdjustments Log all adjustments to stderr if true
   */
  @Uninterruptible
  @Override
  public final void adjustESP(VM_Registers registers, Offset delta, boolean traceAdjustments) {
    Word old = registers.gprs.get(ESP);
    registers.gprs.set(ESP, old.plus(delta));
    if (traceAdjustments) {
      VM.sysWrite(" esp =");
      VM.sysWrite(registers.gprs.get(ESP));
    }   
  }
}