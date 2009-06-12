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
package org.jikesrvm.compilers.opt.lir2mir.ia32;

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.Simplifier;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.CondMove;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.ia32.ArchConstants;

/**
 * Reduce the number of ALU operators considered by BURS
 */
public class ConvertALUOperators extends CompilerPhase implements Operators, ArchConstants {

  @Override
  public final String getName() { return "ConvertALUOps"; }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public final void perform(IR ir) {
    // Calling Simplifier.simplify ensures that the instruction is
    // in normalized form. This reduces the number of cases we have to
    // worry about (and does last minute constant folding on the off
    // chance we've missed an opportunity...)
    // BURS assumes that this has been done
    for (InstructionEnumeration instrs = ir.forwardInstrEnumerator(); instrs.hasMoreElements();) {
      Instruction s = instrs.next();
      Simplifier.simplify(false, ir.regpool, ir.options, s);
    }

    // Pass over instructions
    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();

      switch (s.getOpcode()) {
      // BURS doesn't really care, so consolidate to reduce rule space
      case REF_ADD_opcode:
        s.operator = VM.BuildFor32Addr ? INT_ADD : LONG_ADD;
        break;
      case REF_SUB_opcode:
        s.operator = VM.BuildFor32Addr ? INT_SUB : LONG_SUB;
        break;
      case REF_NEG_opcode:
        s.operator = VM.BuildFor32Addr ? INT_NEG : LONG_NEG;
        break;
      case REF_NOT_opcode:
        s.operator = VM.BuildFor32Addr ? INT_NOT : LONG_NOT;
        break;
      case REF_AND_opcode:
        s.operator = VM.BuildFor32Addr ? INT_AND : LONG_AND;
        break;
      case REF_OR_opcode:
        s.operator = VM.BuildFor32Addr ? INT_OR  : LONG_OR;
        break;
      case REF_XOR_opcode:
        s.operator = VM.BuildFor32Addr ? INT_XOR : LONG_XOR;
        break;
      case REF_SHL_opcode:
        s.operator = VM.BuildFor32Addr ? INT_SHL : LONG_SHL;
        break;
      case REF_SHR_opcode:
        s.operator = VM.BuildFor32Addr ? INT_SHR : LONG_SHR;
        break;
      case REF_USHR_opcode:
        s.operator = VM.BuildFor32Addr ? INT_USHR : LONG_USHR;
        break;
      case BOOLEAN_CMP_ADDR_opcode:
        s.operator = VM.BuildFor32Addr ? BOOLEAN_CMP_INT : BOOLEAN_CMP_LONG;
        break;
      case REF_LOAD_opcode:
        s.operator = VM.BuildFor32Addr ? INT_LOAD : LONG_LOAD;
        break;
      case REF_STORE_opcode:
        s.operator = VM.BuildFor32Addr ? INT_STORE : LONG_STORE;
        break;
      case REF_ALOAD_opcode:
        s.operator = VM.BuildFor32Addr ? INT_ALOAD : LONG_ALOAD;
        break;
      case REF_ASTORE_opcode:
        s.operator = VM.BuildFor32Addr ? INT_ASTORE : LONG_ASTORE;
        break;
      case REF_MOVE_opcode:
        s.operator = VM.BuildFor32Addr ? INT_MOVE : LONG_MOVE;
        break;
      case REF_IFCMP_opcode:
        s.operator = VM.BuildFor32Addr ? INT_IFCMP : LONG_IFCMP;
        break;
      case ATTEMPT_ADDR_opcode:
        s.operator = VM.BuildFor32Addr ? ATTEMPT_INT : ATTEMPT_LONG;
        break;
      case PREPARE_ADDR_opcode:
        s.operator = VM.BuildFor32Addr ? PREPARE_INT : PREPARE_LONG;
        break;
      case INT_2ADDRSigExt_opcode:
        s.operator = VM.BuildFor32Addr ? INT_MOVE : INT_2LONG;
        break;
      case INT_2ADDRZerExt_opcode:
        if (VM.BuildFor32Addr) {
	  s.operator = INT_MOVE;
	}
        break;
      case ADDR_2INT_opcode:
        s.operator = VM.BuildFor32Addr ? INT_MOVE : LONG_2INT;
        break;
      case LONG_2ADDR_opcode:
        s.operator = VM.BuildFor32Addr ? LONG_2INT : LONG_MOVE;
        break;

      case FLOAT_ADD_opcode:
        if (!SSE2_FULL)
          s.operator = FP_ADD;
        break;
      case DOUBLE_ADD_opcode:
        if (!SSE2_FULL)
          s.operator = FP_ADD;
        break;
      case FLOAT_SUB_opcode:
        if (!SSE2_FULL)
          s.operator = FP_SUB;
        break;
      case DOUBLE_SUB_opcode:
        if (!SSE2_FULL)
          s.operator = FP_SUB;
        break;
      case FLOAT_MUL_opcode:
        if (!SSE2_FULL)
          s.operator = FP_MUL;
        break;
      case DOUBLE_MUL_opcode:
        if (!SSE2_FULL)
          s.operator = FP_MUL;
        break;
      case FLOAT_DIV_opcode:
        if (!SSE2_FULL)
          s.operator = FP_DIV;
        break;
      case DOUBLE_DIV_opcode:
        if (!SSE2_FULL)
          s.operator = FP_DIV;
        break;
      case FLOAT_REM_opcode:
        if (!SSE2_FULL)
          s.operator = FP_REM;
        break;
      case DOUBLE_REM_opcode:
        if (!SSE2_FULL)
          s.operator = FP_REM;
        break;
      case FLOAT_NEG_opcode:
        if (!SSE2_FULL)
          s.operator = FP_NEG;
        break;
      case DOUBLE_NEG_opcode:
        if (!SSE2_FULL)
          s.operator = FP_NEG;
        break;

      case INT_COND_MOVE_opcode:
      case REF_COND_MOVE_opcode:
        s.operator = CondMove.getCond(s).isFLOATINGPOINT() ? FCMP_CMOV : (CondMove.getVal1(s).isLong() ? LCMP_CMOV : CMP_CMOV);
        break;
      case FLOAT_COND_MOVE_opcode:
      case DOUBLE_COND_MOVE_opcode:
        s.operator = CondMove.getCond(s).isFLOATINGPOINT() ? FCMP_FCMOV : CMP_FCMOV;
        break;

      case GUARD_COND_MOVE_opcode:
      case LONG_COND_MOVE_opcode:
        OptimizingCompilerException.TODO("Unimplemented conversion" + s);
        break;

      case INT_2FLOAT_opcode:
        if (!SSE2_FULL)
          s.operator = INT_2FP;
        break;
      case INT_2DOUBLE_opcode:
        if (!SSE2_FULL)
          s.operator = INT_2FP;
        break;
      case LONG_2FLOAT_opcode:
        if (!SSE2_FULL)
          s.operator = LONG_2FP;
        break;
      case LONG_2DOUBLE_opcode:
        if (!SSE2_FULL)
          s.operator = LONG_2FP;
        break;
      }
    }
  }
}
