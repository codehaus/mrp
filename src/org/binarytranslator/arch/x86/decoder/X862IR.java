/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.x86.decoder;

import java.util.ArrayList;
import java.util.HashMap;

import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.arch.x86.os.process.X86_ProcessSpace;

import org.binarytranslator.vmInterface.TranslationHelper;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.binarytranslator.vmInterface.DBT_OptimizingCompilerException;
import org.binarytranslator.*;
import org.binarytranslator.generic.decoder.DecoderUtils;
import org.binarytranslator.generic.decoder.Laziness;

import com.ibm.jikesrvm.opt.ir.OPT_HIRGenerator;
import com.ibm.jikesrvm.classloader.VM_TypeReference;
import com.ibm.jikesrvm.classloader.VM_Atom;

// General VM class
import com.ibm.jikesrvm.VM;
// Classes to get at class types
import com.ibm.jikesrvm.classloader.VM_Class;
import com.ibm.jikesrvm.classloader.VM_Method;
import com.ibm.jikesrvm.classloader.VM_TypeReference;
import com.ibm.jikesrvm.classloader.VM_MethodReference;
import com.ibm.jikesrvm.classloader.VM_MemberReference;
import com.ibm.jikesrvm.classloader.VM_FieldReference;
import com.ibm.jikesrvm.classloader.VM_BootstrapClassLoader;
import com.ibm.jikesrvm.classloader.VM_Atom;
// OPT interface
import com.ibm.jikesrvm.opt.OPT_Constants;
import com.ibm.jikesrvm.opt.ir.OPT_GenerationContext;
import com.ibm.jikesrvm.opt.ir.OPT_HIRGenerator;
import com.ibm.jikesrvm.opt.ir.OPT_IR;
import com.ibm.jikesrvm.opt.ir.OPT_BasicBlock;
// Instructions
import com.ibm.jikesrvm.opt.ir.OPT_Instruction;
import com.ibm.jikesrvm.opt.ir.OPT_Operator;
import com.ibm.jikesrvm.opt.ir.OPT_Operators;
import com.ibm.jikesrvm.opt.ir.ALoad;
import com.ibm.jikesrvm.opt.ir.AStore;
import com.ibm.jikesrvm.opt.ir.Athrow;
import com.ibm.jikesrvm.opt.ir.Binary;
import com.ibm.jikesrvm.opt.ir.BBend;
import com.ibm.jikesrvm.opt.ir.Call;
import com.ibm.jikesrvm.opt.ir.CondMove;
import com.ibm.jikesrvm.opt.ir.GetField;
import com.ibm.jikesrvm.opt.ir.Goto;
import com.ibm.jikesrvm.opt.ir.IfCmp;
import com.ibm.jikesrvm.opt.ir.Move;
import com.ibm.jikesrvm.opt.ir.New;
import com.ibm.jikesrvm.opt.ir.LookupSwitch;
import com.ibm.jikesrvm.opt.ir.PutField;
import com.ibm.jikesrvm.opt.ir.Unary;
// Operands
import com.ibm.jikesrvm.opt.ir.OPT_AddressConstantOperand;
import com.ibm.jikesrvm.opt.ir.OPT_BranchOperand;
import com.ibm.jikesrvm.opt.ir.OPT_BranchProfileOperand;
import com.ibm.jikesrvm.opt.ir.OPT_ConditionOperand;
import com.ibm.jikesrvm.opt.ir.OPT_IntConstantOperand;
import com.ibm.jikesrvm.opt.ir.OPT_LocationOperand;
import com.ibm.jikesrvm.opt.ir.OPT_MethodOperand;
import com.ibm.jikesrvm.opt.ir.OPT_Operand;
import com.ibm.jikesrvm.opt.ir.OPT_Register;
import com.ibm.jikesrvm.opt.ir.OPT_RegisterOperand;
import com.ibm.jikesrvm.opt.ir.OPT_TrueGuardOperand;
import com.ibm.jikesrvm.opt.ir.OPT_TypeOperand;

public class X862IR extends DecoderUtils implements OPT_HIRGenerator, OPT_Operators, OPT_Constants {

  /**
   * Constructor
   */
  public X862IR(OPT_GenerationContext context) {      
    super (context);

    // Create the registers
    GP32 = new OPT_Register[8];
    GP32InUse = new boolean[8];
   
    GP16 = new OPT_Register[8];
    GP16InUse = new boolean[8];
   
    GP8 = new OPT_Register[8];
    GP8InUse = new boolean[8];
  }
    
  /**
   * Translate the instruction at the given pc
   * @param lazy the status of the lazy evaluation
   * @param pc the program counter for the instruction
   * @return the next instruction address or -1
   */
  protected int translateInstruction(Laziness lazy, int pc) {
    return X86_InstructionDecoder.translateInstruction((X862IR) this, (X86_ProcessSpace)ps, (X86_Laziness) lazy, pc);
  }

  // -oO Debug Oo-

  /**
   * Report some debug output
   */
  protected void report(String str) {
    System.out.print("X862IR: ");
    System.out.println(str);
  }


  // -oO Register Manipulation Oo-

  /**
   * Registers holding 32bit values during the trace
   */
  private OPT_Register[] GP32;
  /**
   *  Which 32bit registers have been used during the trace - unused
   *  registers can be eliminated
   */
  private boolean[] GP32InUse;
  /**
   * Registers holding 16bit values during the trace
   */
  private OPT_Register[] GP16;
  /**
   *  Which 16bit registers have been used during the trace - unused
   *  registers can be eliminated
   */
  private boolean[] GP16InUse;
  /**
   * Registers holding 8bit values during the trace
   */
  private OPT_Register[] GP8;
  /**
   *  Which 8bit registers have been used during the trace - unused
   *  registers can be eliminated
   */
  private boolean[] GP8InUse;

  /**
   * Resolve a 32bit register
   * @param laziness the lazy state, used to determine register
   * mangling
   * @param r the register to resolve
   */
  private void resolveGPRegister32(X86_Laziness laziness, int r) {
    if (laziness.is32bitRegisterValid(r) == false) {
      OPT_RegisterOperand result = new OPT_RegisterOperand (GP32[r], VM_TypeReference.Int);
      // 32bit register isn't valid so combine from smaller registers
      if(laziness.is16bitRegisterValid(r)) {
        // EXX = (EXX & 0xFFFF0000) | (XX & 0xFFFF)
        OPT_RegisterOperand reg16 = new OPT_RegisterOperand (GP16[r], VM_TypeReference.Int);
        appendInstructionToCurrentBlock(Binary.create(INT_AND, result,
                                                      result.copyRO(), new OPT_IntConstantOperand(0xFFFF0000)));
        appendInstructionToCurrentBlock(Binary.create(INT_AND, reg16,
                                                      reg16.copyRO(), new OPT_IntConstantOperand(0xFFFF)));
        appendInstructionToCurrentBlock(Binary.create(INT_OR, result.copyRO(), result.copyRO(), reg16.copyRO()));
      }
      else { // 8bit registers
        // both XL and Xh are valid
        // EXX = (EXX & 0xFFFF0000) | ((XH & 0xFF)<<8) | (XL & 0xFF)
        OPT_RegisterOperand reg8_h = new OPT_RegisterOperand (GP8[r+4], VM_TypeReference.Int);
        OPT_RegisterOperand reg8_l = new OPT_RegisterOperand (GP8[r], VM_TypeReference.Int);
        appendInstructionToCurrentBlock(Binary.create(INT_AND, result,
                                                      result.copyRO(), new OPT_IntConstantOperand(0xFFFF0000)));
        appendInstructionToCurrentBlock(Binary.create(INT_AND, reg8_h,
                                                      reg8_h.copyRO(), new OPT_IntConstantOperand(0xFF)));
        appendInstructionToCurrentBlock(Binary.create(INT_SHL, reg8_h.copyRO(),
                                                      reg8_h.copyRO(), new OPT_IntConstantOperand(8)));
        appendInstructionToCurrentBlock(Binary.create(INT_AND, reg8_l,
                                                      reg8_l.copyRO(), new OPT_IntConstantOperand(0xFF)));
        appendInstructionToCurrentBlock(Binary.create(INT_OR, result.copyRO(), result.copyRO(), reg8_l.copyRO()));
        appendInstructionToCurrentBlock(Binary.create(INT_OR, result.copyRO(), result.copyRO(), reg8_h.copyRO()));
      }
      laziness.set32bitRegisterValid(r);
    }
  }

  /**
   * Read a 32bit register
   * @param laziness the lazy state, used to determine register
   * mangling
   * @param r the register to read
   */
  public OPT_RegisterOperand getGPRegister32(X86_Laziness laziness, int r) {
    GP32InUse[r] = true;
    resolveGPRegister32(laziness, r);
    return new OPT_RegisterOperand (GP32[r], VM_TypeReference.Int);
  }

  /**
   * Read a 16bit register
   * @param laziness the lazy state, used to determine register
   * mangling
   * @param r the register to read
   */
  public OPT_RegisterOperand getGPRegister16(X86_Laziness laziness, int r) {
    GP32InUse[r] = true;
    GP16InUse[r] = true;
    OPT_RegisterOperand result;
    // Get or create 16bit result register
    if (GP16[r] != null) {
      result = new OPT_RegisterOperand (GP16[r], VM_TypeReference.Int);
    }
    else {
      result = makeTemp(VM_TypeReference.Int);
      GP16[r] = result.register;
    }
    if (laziness.is16bitRegisterValid(r) == false) {
      // 16bit register isn't valid so either combine from smaller
      // registers or take from 32bit register
      if(laziness.is32bitRegisterValid(r)) {
        // 32bit register is valid so just move that to use the lower 16bits
        appendInstructionToCurrentBlock(Move.create(INT_MOVE, result.copyRO(),
                                                    new OPT_RegisterOperand (GP32[r], VM_TypeReference.Int)));
      }
      else { // 8bit registers
        // both XL and XH are valid
        // XX = ((?H & 0xFF)<<8) | (?L & 0xFF)
        OPT_RegisterOperand reg8_h = new OPT_RegisterOperand (GP8[r+4], VM_TypeReference.Int);
        OPT_RegisterOperand reg8_l = new OPT_RegisterOperand (GP8[r], VM_TypeReference.Int);
        appendInstructionToCurrentBlock(Binary.create(INT_AND, result.copyRO(), reg8_h.copyRO(), new OPT_IntConstantOperand(0xFF)));
        appendInstructionToCurrentBlock(Binary.create(INT_SHL, result.copyRO(), result.copyRO(), new OPT_IntConstantOperand(8)));
        appendInstructionToCurrentBlock(Binary.create(INT_AND, reg8_l, reg8_l.copyRO(), new OPT_IntConstantOperand(0xFF)));
        appendInstructionToCurrentBlock(Binary.create(INT_OR, result.copyRO(), result.copyRO(), reg8_l.copyRO()));
      }
      laziness.set16bitRegisterValid(r);
    }
    return result;
  }

  /**
   * Read a 8bit register
   * @param laziness the lazy state, used to determine register
   * mangling
   * @param r the register to read
   */
  public OPT_RegisterOperand getGPRegister8(X86_Laziness laziness, int r) {
    int rl, rh; // low and high 8bit registers
    if (r > 4) {
      rh = r;
      rl = r - 4;
    }
    else {
      rh = r + 4;
      rl = r;
    }
    GP32InUse[rl] = true;
    GP8InUse[rl] = true;
    GP8InUse[rh] = true;
    if (laziness.is8bitRegisterValid(rl) == false) {      
      OPT_RegisterOperand rlOp, rhOp;
      // Get or create registers to hold 8bit values
      if (GP8[rl] != null) {
        rlOp = new OPT_RegisterOperand (GP8[rl], VM_TypeReference.Int);
      }
      else {
        rlOp = makeTemp(VM_TypeReference.Int);
        GP8[rl] = rlOp.register;
      }
      if (GP8[rh] != null) {
        rhOp = new OPT_RegisterOperand (GP8[rh], VM_TypeReference.Int);
      }
      else {
        rhOp = makeTemp(VM_TypeReference.Int);
        GP8[rh] = rhOp.register;
      }
      // 8bit register isn't valid so take from either 32bit or 16bit
      // register
      if(laziness.is32bitRegisterValid(rl)) { // 32bit register is valid
        appendInstructionToCurrentBlock(Move.create(INT_MOVE, rlOp,
                                                    new OPT_RegisterOperand (GP32[rl], VM_TypeReference.Int)));
      }
      else { // 16bit register is valid
        appendInstructionToCurrentBlock(Move.create(INT_MOVE, rlOp,
                                                    new OPT_RegisterOperand (GP16[rl], VM_TypeReference.Int)));
      }
      appendInstructionToCurrentBlock(Binary.create(INT_SHL, rhOp,
                                                    rlOp.copyRO(), new OPT_IntConstantOperand(8)));
      laziness.set8bitRegisterValid(rl);
    }
    return new OPT_RegisterOperand (GP8[r], VM_TypeReference.Int);
  }

  /**
   * Read a 8bit register
   * @param laziness the lazy state, used to determine register
   * mangling
   * @param r the register to read
   */
  public OPT_RegisterOperand getGPRegister(X86_Laziness laziness, int r, int size) {
    switch (size) {
    case 32: return getGPRegister32(laziness, r);
    case 16: return getGPRegister16(laziness, r);
    case 8:  return getGPRegister8(laziness, r);
    default: DBT_OptimizingCompilerException.UNREACHABLE(); return null; // keep jikes happy
    }
  }

  // -- status flags
  /**
   * X86 flag register constituants - bit 0 - CF or carry flag
   */
  private OPT_Register flag_CF;
  /**
   * Was the register used during the trace?
   */
  private boolean flag_CF_InUse;

  /**
   * X86 flag register constituants - bit 2 - PF or parity flag
   */
  private OPT_Register flag_PF;
  /**
   * Was the register used during the trace?
   */
  private boolean flag_PF_InUse;

  /**
   * X86 flag register constituants - bit 4 - AF or auxiliary carry
   * flag or adjust flag
   */
  private OPT_Register flag_AF;
  /**
   * Was the register used during the trace?
   */
  private boolean flag_AF_InUse;

  /**
   * X86 flag register constituants - bit 6 - ZF or zero flag
   */
  private OPT_Register flag_ZF;
  /**
   * Was the register used during the trace?
   */
  private boolean flag_ZF_InUse;

  /**
   * X86 flag register constituants - bit 7 - SF or sign flag
   */
  private OPT_Register flag_SF;
  /**
   * Was the register used during the trace?
   */
  private boolean flag_SF_InUse;

  /**
   * X86 flag register constituants - bit 11 - OF or overflow flag
   */
  private OPT_Register flag_OF;
  /**
   * Was the register used during the trace?
   */
  private boolean flag_OF_InUse;

  /**
   * X86 flag register constituants - bit 10 - DF or direction flag
   */
  private OPT_Register flag_DF;
  /**
   * Was the register used during the trace?
   */
  private boolean flag_DF_InUse;

  /**
   * Wrap the carry flag up in a register operand and return
   */
  OPT_RegisterOperand getCarryFlag() {
    flag_CF_InUse = true;
    return new OPT_RegisterOperand(flag_CF, VM_TypeReference.Boolean);
  }
  /**
   * Wrap the sign flag up in a register operand and return
   */
  OPT_RegisterOperand getSignFlag() {
    flag_SF_InUse = true;
    return new OPT_RegisterOperand(flag_SF, VM_TypeReference.Boolean);
  }
  /**
   * Wrap the zero flag up in a register operand and return
   */
  OPT_RegisterOperand getZeroFlag() {
    flag_ZF_InUse = true;
    return new OPT_RegisterOperand(flag_ZF, VM_TypeReference.Boolean);
  }
  /**
   * Wrap the overflow flag up in a register operand and return
   */
  OPT_RegisterOperand getOverflowFlag() {
    flag_OF_InUse = true;
    return new OPT_RegisterOperand(flag_OF, VM_TypeReference.Boolean);
  }

  // -- FPU registers
  /**
   * Control word
   */
  OPT_RegisterOperand getFPU_CW() {
    OPT_RegisterOperand result = makeTemp(VM_TypeReference.Int);
    appendInstructionToCurrentBlock(Move.create(INT_MOVE, result.copyRO(),
                                                new OPT_IntConstantOperand(0)));
    return result;
  }

  // -- All registers

  /**
   * A register holding a reference to ps.registers
   */
  private OPT_Register ps_registers;
  /**
   * The type of ps.registers
   */
  private VM_TypeReference registersTref;
  /**
   * A register holding a reference to ps.registers.gp32
   */
  private OPT_Register ps_registers_gp32;
  /**
   * The type of ps.registers.gp32
   */
  private VM_TypeReference gp32Tref;
  /**
   * Fill all the registers from the ProcessSpace, that is take the
   * register values from the process space and place them in the
   * traces registers.
   */
  protected void fillAllRegisters() {
    OPT_RegisterOperand ps_registersOp;
    // Get the registers
    if (ps_registers == null)  {
      // Set up the reference to memory
      VM_TypeReference psTref =  VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                               VM_Atom.findOrCreateAsciiAtom
                                                               ("Lorg/binarytranslator/arch/x86/os/process/X86_ProcessSpace;")
                                                               );
      VM_FieldReference registersFref = VM_MemberReference.findOrCreate(psTref, VM_Atom.findOrCreateAsciiAtom("registers"),
                                                                      VM_Atom.findOrCreateAsciiAtom
                                                                        ("Lorg/binarytranslator/arch/x86/os/process/X86_Registers;")
                                                                        ).asFieldReference();
      registersTref = registersFref.getFieldContentsType();
      ps_registersOp = gc.temps.makeTemp(registersTref);
      ps_registers = ps_registersOp.register;
      appendInstructionToCurrentBlock(GetField.create(GETFIELD, ps_registersOp,
                                                      gc.makeLocal(1,psTref),
                                                      new OPT_AddressConstantOperand(registersFref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(registersFref),
                                                      new OPT_TrueGuardOperand()));
    }
    else {
      ps_registersOp = new OPT_RegisterOperand(ps_registers, registersTref);
    }
    // Get the array of general purpose registers
    OPT_RegisterOperand ps_registers_gp32Op;
    if (ps_registers_gp32 == null) {
      VM_FieldReference gp32Fref = VM_MemberReference.findOrCreate(registersTref, VM_Atom.findOrCreateAsciiAtom("gp32"),
                                                                   VM_Atom.findOrCreateAsciiAtom
                                                                   ("[I")
                                                                   ).asFieldReference();
      gp32Tref = gp32Fref.getFieldContentsType();
      ps_registers_gp32Op = gc.temps.makeTemp(gp32Tref);
      appendInstructionToCurrentBlock(GetField.create(GETFIELD, ps_registers_gp32Op,
                                                      ps_registersOp.copyRO(),
                                                      new OPT_AddressConstantOperand(gp32Fref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(gp32Fref),
                                                      new OPT_TrueGuardOperand()));
      ps_registers_gp32 = ps_registers_gp32Op.register;
    }
    else {
      ps_registers_gp32Op = new OPT_RegisterOperand(ps_registers_gp32, gp32Tref);
    }
    // Fill general purpose registers
    for(int i=0; i < GP32.length; i++) {
      OPT_RegisterOperand gp32op;
      if(GP32[i] == null) {
        gp32op = makeTemp(VM_TypeReference.Int);
        GP32[i] = gp32op.register;
      }
      else {
        gp32op = new OPT_RegisterOperand (GP32[i], VM_TypeReference.Int);
      }
      appendInstructionToCurrentBlock(ALoad.create(INT_ALOAD, gp32op,
                                                   ps_registers_gp32Op.copyRO(),
                                                   new OPT_IntConstantOperand(i),
                                                   new OPT_LocationOperand(VM_TypeReference.Int),
                                                   new OPT_TrueGuardOperand()));                                   
    }
    // Fill flags
    {
      OPT_RegisterOperand flag_CF_Op;
      if (flag_CF == null) {
        flag_CF_Op = makeTemp(VM_TypeReference.Boolean);
        flag_CF = flag_CF_Op.register;
      }
      else {
        flag_CF_Op = new OPT_RegisterOperand(flag_CF, VM_TypeReference.Boolean);
      }
      VM_FieldReference flagFref = VM_MemberReference.findOrCreate(registersTref, VM_Atom.findOrCreateAsciiAtom("flag_CF"),
                                                                   VM_Atom.findOrCreateAsciiAtom
                                                                   ("Z")
                                                                   ).asFieldReference();
      VM_TypeReference flagTref = flagFref.getFieldContentsType();
      appendInstructionToCurrentBlock(GetField.create(GETFIELD, flag_CF_Op,
                                                      ps_registersOp.copyRO(),
                                                      new OPT_AddressConstantOperand(flagFref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(flagFref),
                                                      new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_SF_Op;
      if (flag_SF == null) {
        flag_SF_Op = makeTemp(VM_TypeReference.Boolean);
        flag_SF = flag_SF_Op.register;
      }
      else {
        flag_SF_Op = new OPT_RegisterOperand(flag_SF, VM_TypeReference.Boolean);
      }
      VM_FieldReference flagFref = VM_MemberReference.findOrCreate(registersTref, VM_Atom.findOrCreateAsciiAtom("flag_SF"),
                                                                   VM_Atom.findOrCreateAsciiAtom
                                                                   ("Z")
                                                                   ).asFieldReference();
      VM_TypeReference flagTref = flagFref.getFieldContentsType();
      appendInstructionToCurrentBlock(GetField.create(GETFIELD, flag_SF_Op,
                                                      ps_registersOp.copyRO(),
                                                      new OPT_AddressConstantOperand(flagFref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(flagFref),
                                                      new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_ZF_Op;
      if (flag_ZF == null) {
        flag_ZF_Op = makeTemp(VM_TypeReference.Boolean);
        flag_ZF = flag_ZF_Op.register;
      }
      else {
        flag_ZF_Op = new OPT_RegisterOperand(flag_ZF, VM_TypeReference.Boolean);
      }
      VM_FieldReference flagFref = VM_MemberReference.findOrCreate(registersTref, VM_Atom.findOrCreateAsciiAtom("flag_ZF"),
                                                                   VM_Atom.findOrCreateAsciiAtom
                                                                   ("Z")
                                                                   ).asFieldReference();
      VM_TypeReference flagTref = flagFref.getFieldContentsType();
      appendInstructionToCurrentBlock(GetField.create(GETFIELD, flag_ZF_Op,
                                                      ps_registersOp.copyRO(),
                                                      new OPT_AddressConstantOperand(flagFref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(flagFref),
                                                      new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_OF_Op;
      if (flag_OF == null) {
        flag_OF_Op = makeTemp(VM_TypeReference.Boolean);
        flag_OF = flag_OF_Op.register;
      }
      else {
        flag_OF_Op = new OPT_RegisterOperand(flag_OF, VM_TypeReference.Boolean);
      }
      VM_FieldReference flagFref = VM_MemberReference.findOrCreate(registersTref, VM_Atom.findOrCreateAsciiAtom("flag_OF"),
                                                                   VM_Atom.findOrCreateAsciiAtom
                                                                   ("Z")
                                                                   ).asFieldReference();
      VM_TypeReference flagTref = flagFref.getFieldContentsType();
      appendInstructionToCurrentBlock(GetField.create(GETFIELD, flag_OF_Op,
                                                      ps_registersOp.copyRO(),
                                                      new OPT_AddressConstantOperand(flagFref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(flagFref),
                                                      new OPT_TrueGuardOperand()));
    }
  }
  /**
   * Spill all the registers, that is put them from the current
   * running trace into the process space
   */
  protected void spillAllRegisters() {
    // spill general purpose registers
    OPT_RegisterOperand ps_registers_gp32Op = new OPT_RegisterOperand(ps_registers_gp32, gp32Tref);
    for(int i=0; i < GP32.length; i++) {
      // We can save spills if the trace has no syscalls and the register was never used
      if((DBT_Options.singleInstrTranslation == false) ||(GP32InUse[i] == true)) {
        appendInstructionToCurrentBlock(AStore.create(INT_ASTORE, new OPT_RegisterOperand (GP32[i], VM_TypeReference.Int),
                                                      ps_registers_gp32Op.copyRO(),
                                                      new OPT_IntConstantOperand(i),
                                                      new OPT_LocationOperand(VM_TypeReference.Int),
                                                      new OPT_TrueGuardOperand()));
      }
    }
    // Spill flags
    OPT_RegisterOperand ps_registersOp = new OPT_RegisterOperand(ps_registers, registersTref);
    {
      OPT_RegisterOperand flag_CF_Op = new OPT_RegisterOperand(flag_CF, VM_TypeReference.Boolean);
      VM_FieldReference flagFref = VM_MemberReference.findOrCreate(registersTref, VM_Atom.findOrCreateAsciiAtom("flag_CF"),
                                                                   VM_Atom.findOrCreateAsciiAtom
                                                                   ("Z")
                                                                   ).asFieldReference();
      VM_TypeReference flagTref = flagFref.getFieldContentsType();
      appendInstructionToCurrentBlock(GetField.create(PUTFIELD, flag_CF_Op,
                                                      ps_registersOp,
                                                      new OPT_AddressConstantOperand(flagFref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(flagFref),
                                                      new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_SF_Op = new OPT_RegisterOperand(flag_SF, VM_TypeReference.Boolean);
      VM_FieldReference flagFref = VM_MemberReference.findOrCreate(registersTref, VM_Atom.findOrCreateAsciiAtom("flag_SF"),
                                                                   VM_Atom.findOrCreateAsciiAtom
                                                                   ("Z")
                                                                   ).asFieldReference();
      VM_TypeReference flagTref = flagFref.getFieldContentsType();
      appendInstructionToCurrentBlock(GetField.create(PUTFIELD, flag_SF_Op,
                                                      ps_registersOp.copyRO(),
                                                      new OPT_AddressConstantOperand(flagFref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(flagFref),
                                                      new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_ZF_Op = new OPT_RegisterOperand(flag_ZF, VM_TypeReference.Boolean);
      VM_FieldReference flagFref = VM_MemberReference.findOrCreate(registersTref, VM_Atom.findOrCreateAsciiAtom("flag_ZF"),
                                                                   VM_Atom.findOrCreateAsciiAtom
                                                                   ("Z")
                                                                   ).asFieldReference();
      VM_TypeReference flagTref = flagFref.getFieldContentsType();
      appendInstructionToCurrentBlock(GetField.create(PUTFIELD, flag_ZF_Op,
                                                      ps_registersOp.copyRO(),
                                                      new OPT_AddressConstantOperand(flagFref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(flagFref),
                                                      new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_OF_Op = new OPT_RegisterOperand(flag_OF, VM_TypeReference.Boolean);
      VM_FieldReference flagFref = VM_MemberReference.findOrCreate(registersTref, VM_Atom.findOrCreateAsciiAtom("flag_OF"),
                                                                   VM_Atom.findOrCreateAsciiAtom
                                                                   ("Z")
                                                                   ).asFieldReference();
      VM_TypeReference flagTref = flagFref.getFieldContentsType();
      appendInstructionToCurrentBlock(GetField.create(PUTFIELD, flag_OF_Op,
                                                      ps_registersOp,
                                                      new OPT_AddressConstantOperand(flagFref.peekResolvedField().getOffset()),
                                                      new OPT_LocationOperand(flagFref),
                                                      new OPT_TrueGuardOperand()));
    }
  }

  // -oO Laziness Oo-

  /**
   * Create the initial object for capturing lazy information
   */
  protected Laziness createInitialLaziness() {
    return new X86_Laziness();
  }

  /**
   * Plant instructions modifying a lazy state into one with no
   * laziness
   * @param laziness the laziness to modify
   */
  public void resolveLaziness(Laziness laziness) {
    for(int i=0; i < GP32.length; i++) {
      resolveGPRegister32((X86_Laziness)laziness, i);
    }
  }

  /**
   * Return an array of unused registers
   */
  protected OPT_Register[] getUnusedRegisters() {
    ArrayList unusedRegisterList = new ArrayList();
    // Add general purpose registers
    for(int i=0; i < GP32InUse.length; i++) {
      if (GP32InUse[i] == false) {
        unusedRegisterList.add(GP32[i]);
      }
    }
    // ignore GP16 and GP8 registers as they are only created lazily,
    // and so must be in use

    // Add flags
    if(flag_CF_InUse == false) {
      unusedRegisterList.add(flag_CF);
    }
    if(flag_SF_InUse == false) {
      unusedRegisterList.add(flag_SF);
    }
    if(flag_ZF_InUse == false) {
      unusedRegisterList.add(flag_ZF);
    }
    if(flag_OF_InUse == false) {
      unusedRegisterList.add(flag_OF);
    }
    return (OPT_Register[])unusedRegisterList.toArray(new OPT_Register[unusedRegisterList.size()]);
  }
}
