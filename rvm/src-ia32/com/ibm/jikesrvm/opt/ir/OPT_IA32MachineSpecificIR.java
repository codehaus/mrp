/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package com.ibm.jikesrvm.opt.ir;

import java.util.Enumeration;

import org.vmmagic.pragma.Inline;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.classloader.VM_TypeReference;
import com.ibm.jikesrvm.opt.OPT_LiveIntervalElement;
import com.ibm.jikesrvm.opt.OPT_OptimizingCompilerException;

import static com.ibm.jikesrvm.opt.ir.OPT_Operators.*;

/**
 * Wrappers around IA32-specific IR
 * 
 * $Id: OPT_IA32ConditionOperand.java 10996 2006-11-16 23:37:12Z dgrove-oss $
 * 
 * @author Steve Blackburn
 */
public final class OPT_IA32MachineSpecificIR extends OPT_IAMachineSpecificIR {
  /* common to all ISAs */
  @Override
  public boolean mayEscapeThread(OPT_Instruction instruction) {
    switch (instruction.getOpcode()) {
    case PREFETCH_opcode:
      return false;
    case GET_JTOC_opcode: case GET_CURRENT_PROCESSOR_opcode:
      return true;
    default:
      throw  new OPT_OptimizingCompilerException("OPT_SimpleEscapge: Unexpected " + instruction);
    }
  }
  @Override
  public boolean mayEscapeMethod(OPT_Instruction instruction) {
    return mayEscapeThread(instruction); // at this stage we're no more specific
  }  /* unique to IA32 */
}