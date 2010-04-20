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
package org.jikesrvm.compilers.opt.bc2ir.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.MagicNotImplementedException;
import org.jikesrvm.compilers.opt.bc2ir.BC2IR;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.ia32.StackframeLayoutConstants;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.MagicNames;
import org.vmmagic.unboxed.Offset;

import static org.jikesrvm.compilers.opt.ir.Operators.*;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.*;
import static org.jikesrvm.ia32.StackframeLayoutConstants.*;

/**
 * This class implements the machine-specific magics for the opt compiler.
 *
 * @see org.jikesrvm.compilers.opt.bc2ir.GenerateMagic for the machine-independent magics
 */
public abstract class GenerateMachineSpecificMagic {

  /** Generate appropriately sized operand for given Offset */
  private static Operand offsetOperand(Offset o) {
    if (VM.BuildFor64Addr) {
      long v = o.toLong();
      return new LongConstantOperand(v);
    } else {
      int v = o.toInt();
      return new IntConstantOperand(v);
    }
  }

  /**
   * "Semantic inlining" of methods of the Magic class.
   * Based on the methodName, generate a sequence of opt instructions
   * that implement the magic, updating the stack as necessary
   *
   * @param bc2ir the bc2ir object generating the ir containing this magic
   * @param gc == bc2ir.gc
   * @param meth the RVMMethod that is the magic method
   */
  public static boolean generateMagic(BC2IR bc2ir, GenerationContext gc, MethodReference meth)
      throws MagicNotImplementedException {

    Atom methodName = meth.getName();
    PhysicalRegisterSet phys = gc.temps.getPhysicalRegisterSet().asIA32();

    if (methodName == MagicNames.getESIAsThread) {
      RegisterOperand rop = gc.temps.makeTROp();
      bc2ir.markGuardlessNonNull(rop);
      bc2ir.push(rop);
    } else if (methodName == MagicNames.setESIAsThread) {
      Operand val = bc2ir.popRef();
      if (val instanceof RegisterOperand) {
        bc2ir.appendInstruction(Move.create(REF_MOVE, gc.temps.makeTROp(), val));
      } else {
        String msg = " Unexpected operand Magic.setESIAsThread";
        throw MagicNotImplementedException.UNEXPECTED(msg);
      }
    } else if (methodName == MagicNames.getFramePointer) {
      gc.allocFrame = true;
      RegisterOperand val = gc.temps.makeTemp(TypeReference.Address);
      RVMField f = ArchEntrypoints.framePointerField;
      RegisterOperand pr = new RegisterOperand(phys.getESI(), TypeReference.Address);
      bc2ir.appendInstruction(GetField.create(GETFIELD,
                                              val,
                                              pr.copy(),
                                              new AddressConstantOperand(f.getOffset()),
                                              new LocationOperand(f),
                                              new TrueGuardOperand()));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.getJTOC || methodName == MagicNames.getTocPointer) {
      TypeReference t = (methodName == MagicNames.getJTOC ? TypeReference.IntArray : TypeReference.Address);
      RegisterOperand val = gc.temps.makeTemp(t);
      AddressConstantOperand addr = new AddressConstantOperand(Magic.getTocPointer());
      bc2ir.appendInstruction(Move.create(REF_MOVE, val, addr));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.isync) {
      // nothing required on Intel
    } else if (methodName == MagicNames.sync) {
      // nothing required on Intel
    } else if (methodName == MagicNames.prefetch) {
      bc2ir.appendInstruction(CacheOp.create(PREFETCH, bc2ir.popAddress()));
    } else if (methodName == MagicNames.pause) {
      bc2ir.appendInstruction(Empty.create(PAUSE));
    } else if (methodName == MagicNames.getCallerFramePointer) {
      Operand fp = bc2ir.popAddress();
      RegisterOperand val = gc.temps.makeTemp(TypeReference.Address);
      bc2ir.appendInstruction(Load.create(REF_LOAD,
                                          val,
                                          fp,
                                          offsetOperand(STACKFRAME_FRAME_POINTER_OFFSET),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setCallerFramePointer) {
      Operand val = bc2ir.popAddress();
      Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(REF_STORE,
                                           val,
                                           fp,
                                           offsetOperand(STACKFRAME_FRAME_POINTER_OFFSET),
                                           null));
    } else if (methodName == MagicNames.getCompiledMethodID) {
      Operand fp = bc2ir.popAddress();
      RegisterOperand val = gc.temps.makeTempInt();
      bc2ir.appendInstruction(Load.create(INT_LOAD,
                                          val,
                                          fp,
                                          offsetOperand(STACKFRAME_METHOD_ID_OFFSET),
                                          null));
      bc2ir.push(val.copyD2U());
    } else if (methodName == MagicNames.setCompiledMethodID) {
      Operand val = bc2ir.popInt();
      Operand fp = bc2ir.popAddress();
      bc2ir.appendInstruction(Store.create(INT_STORE,
                                           val,
                                           fp,
                                           offsetOperand(STACKFRAME_METHOD_ID_OFFSET),
                                           null));
    } else if (methodName == MagicNames.getReturnAddressLocation) {
      Operand fp = bc2ir.popAddress();
      Instruction s = bc2ir._binaryHelper(REF_ADD, fp, offsetOperand(STACKFRAME_RETURN_ADDRESS_OFFSET), TypeReference.Address);
      bc2ir.appendInstruction(s);
    } else {
      // Distinguish between magics that we know we don't implement
      // (and never plan to implement) and those (usually new ones)
      // that we want to be warned that we don't implement.
      String msg = " Magic method not implemented: " + meth;
      if (methodName == MagicNames.returnToNewStack) {
        throw MagicNotImplementedException.EXPECTED(msg);
      } else {
        return false;
        // throw MagicNotImplementedException.UNEXPECTED(msg);
      }
    }
    return true;
  }
}
