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

import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.x86.os.process.X86_ProcessSpace;
import org.binarytranslator.arch.x86.os.process.X86_Registers;
import org.binarytranslator.generic.decoder.AbstractCodeTranslator;
import org.binarytranslator.generic.decoder.Laziness;
import org.binarytranslator.vmInterface.DBT_OptimizingCompilerException;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_BootstrapClassLoader;
import org.jikesrvm.classloader.VM_FieldReference;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OPT_Constants;
import org.jikesrvm.compilers.opt.ir.*;

public class X862IR extends AbstractCodeTranslator implements OPT_HIRGenerator,
    OPT_Operators, OPT_Constants {

  private static final VM_TypeReference  psTref;
  private static final VM_FieldReference registersFref;
  private static final VM_TypeReference  registersTref;
  private static final VM_FieldReference segRegFref;
  private static final VM_TypeReference  segRegTref;
  private static final VM_FieldReference gp32Fref;
  private static final VM_TypeReference  gp32Tref;
  private static final VM_FieldReference flagCFref;
  private static final VM_FieldReference flagSFref;
  private static final VM_FieldReference flagZFref;
  private static final VM_FieldReference flagOFref;
  private static final VM_FieldReference flagDFref;
  private static final VM_FieldReference gsBaseAddrFref;
  private static final VM_FieldReference mxcsrFref;
  static {
    psTref = VM_TypeReference.findOrCreate(
        VM_BootstrapClassLoader.getBootstrapClassLoader(),
        VM_Atom
            .findOrCreateAsciiAtom("Lorg/binarytranslator/arch/x86/os/process/X86_ProcessSpace;"));

    registersFref = VM_MemberReference
    .findOrCreate(
        psTref,
        VM_Atom.findOrCreateAsciiAtom("registers"),
        VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/x86/os/process/X86_Registers;"))
    .asFieldReference();
    
    registersTref = registersFref.getFieldContentsType();

    segRegFref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("segmentRegister"),
        VM_Atom.findOrCreateAsciiAtom("[C")).asFieldReference();

    segRegTref = segRegFref.getFieldContentsType();
    
    gp32Fref = VM_MemberReference.findOrCreate(
      registersTref, VM_Atom.findOrCreateAsciiAtom("gp32"),
      VM_Atom.findOrCreateAsciiAtom("[I")).asFieldReference();
    
    gp32Tref = gp32Fref.getFieldContentsType();

    flagCFref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("flag_CF"),
        VM_Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    flagSFref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("flag_SF"),
        VM_Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    flagZFref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("flag_ZF"),
        VM_Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    flagOFref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("flag_OF"),
        VM_Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    flagDFref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("flag_DF"),
        VM_Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    gsBaseAddrFref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("gsBaseAddr"),
        VM_Atom.findOrCreateAsciiAtom("I")).asFieldReference();

    mxcsrFref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("mxcsr"),
        VM_Atom.findOrCreateAsciiAtom("I")).asFieldReference();
    }

  /**
   * Constructor
   */
  public X862IR(OPT_GenerationContext context) {
    super(context);

    // Create the registers
    SegReg = new OPT_Register[6];
    SegRegInUse = new boolean[6];
    
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
    return X86_InstructionDecoder.translateInstruction((X862IR) this,
        (X86_ProcessSpace) ps, (X86_Laziness) lazy, pc);
  }

  /**
   * Perform entry from a predetermined address into the Linux kernel
   * @param lazy laziness state of registers
   * @param pc entry point address
   * @return -1
   */
  private int plantSystemCallGateEntry(X86_Laziness lazy, int pc) {
    appendSystemCall(lazy, pc);
    // Get return address
    X86_DecodedOperand source = X86_DecodedOperand.getStack(X86_ProcessSpace._16BIT ? 16 : 32,
        X86_ProcessSpace._16BIT ? 16 : 32);
    OPT_RegisterOperand temp = getTempInt(0);
    source.readToRegister(this, lazy, temp);

    // Increment stack pointer
    OPT_RegisterOperand esp = getGPRegister(lazy, X86_Registers.ESP, X86_ProcessSpace._16BIT ? 16 : 32);
    appendInstruction(Binary.create(INT_ADD, esp, esp.copyRO(), new OPT_IntConstantOperand(4)));
    
    // Branch
    setReturnValueResolveLazinessAndBranchToFinish((X86_Laziness) lazy.clone(), temp.copyRO());
    return -1;  
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
   * Registers holding 16bit segment values during the trace
   */
  private OPT_Register[] SegReg;

  /**
   * Which 16bit segment registers have been used during the trace - unused
   * registers can be eliminated
   */
  private boolean[] SegRegInUse;

  /**
   * Registers holding 32bit values during the trace
   */
  private OPT_Register[] GP32;

  /**
   * Which 32bit registers have been used during the trace - unused registers
   * can be eliminated
   */
  private boolean[] GP32InUse;

  /**
   * Registers holding 16bit values during the trace
   */
  private OPT_Register[] GP16;

  /**
   * Which 16bit registers have been used during the trace - unused registers
   * can be eliminated
   */
  private boolean[] GP16InUse;

  /**
   * Registers holding 8bit values during the trace
   */
  private OPT_Register[] GP8;

  /**
   * Which 8bit registers have been used during the trace - unused registers can
   * be eliminated
   */
  private boolean[] GP8InUse;

  /**
   * Resolve a 32bit register
   * @param laziness the lazy state, used to determine register mangling
   * @param r the register to resolve
   */
  private void resolveGPRegister32(X86_Laziness laziness, int r) {
    if (laziness.is32bitRegisterValid(r) == false) {
      OPT_RegisterOperand result = new OPT_RegisterOperand(GP32[r],
          VM_TypeReference.Int);
      // 32bit register isn't valid so combine from smaller registers
      if (laziness.is16bitRegisterValid(r)) {
        // EXX = (EXX & 0xFFFF0000) | (XX & 0xFFFF)
        OPT_RegisterOperand reg16 = new OPT_RegisterOperand(GP16[r],
            VM_TypeReference.Int);
        appendInstruction(Binary.create(INT_AND, result, result
            .copyRO(), new OPT_IntConstantOperand(0xFFFF0000)));
        appendInstruction(Binary.create(INT_AND, reg16, reg16
            .copyRO(), new OPT_IntConstantOperand(0xFFFF)));
        appendInstruction(Binary.create(INT_OR, result.copyRO(),
            result.copyRO(), reg16.copyRO()));
      } else { // 8bit registers
        // both XL and Xh are valid
        // EXX = (EXX & 0xFFFF0000) | ((XH & 0xFF)<<8) | (XL & 0xFF)
        OPT_RegisterOperand reg8_h = new OPT_RegisterOperand(GP8[r + 4],
            VM_TypeReference.Int);
        OPT_RegisterOperand reg8_l = new OPT_RegisterOperand(GP8[r],
            VM_TypeReference.Int);
        appendInstruction(Binary.create(INT_AND, result, result
            .copyRO(), new OPT_IntConstantOperand(0xFFFF0000)));
        appendInstruction(Binary.create(INT_AND, reg8_h, reg8_h
            .copyRO(), new OPT_IntConstantOperand(0xFF)));
        appendInstruction(Binary.create(INT_SHL, reg8_h.copyRO(),
            reg8_h.copyRO(), new OPT_IntConstantOperand(8)));
        appendInstruction(Binary.create(INT_AND, reg8_l, reg8_l
            .copyRO(), new OPT_IntConstantOperand(0xFF)));
        appendInstruction(Binary.create(INT_OR, result.copyRO(),
            result.copyRO(), reg8_l.copyRO()));
        appendInstruction(Binary.create(INT_OR, result.copyRO(),
            result.copyRO(), reg8_h.copyRO()));
      }
      laziness.set32bitRegisterValid(r);
    }
  }

  /**
   * Read a 32bit register
   * @param laziness the lazy state, used to determine register mangling
   * @param r the register to read
   */
  public OPT_RegisterOperand getSegRegister(X86_Laziness laziness, int r) {
    SegRegInUse[r] = true;
    return new OPT_RegisterOperand(SegReg[r], VM_TypeReference.Int);
  }

  /**
   * Add a segment base address to the given address
   * @param segment segment to get base address for
   * @param address the address to add the value onto
   */
  public void addSegmentBaseAddress(int segment, OPT_RegisterOperand address) {
    switch(segment) {
    case X86_Registers.GS: {
      OPT_RegisterOperand temp = getTempInt(9);
      appendInstruction(GetField.create(GETFIELD,
          temp, new OPT_RegisterOperand(ps_registers, registersTref),
          new OPT_AddressConstantOperand(gsBaseAddrFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(gsBaseAddrFref),
          new OPT_TrueGuardOperand()));
      appendInstruction(Binary.create(INT_ADD,
          address.copyRO(), address.copyRO(), temp.copyRO()));
      break;
    }
    case X86_Registers.FS:
      throw new Error("Unhandled segment override FS");
    default:
      break;
    }
  }
  
  /**
   * Read a 32bit register
   * @param laziness the lazy state, used to determine register mangling
   * @param r the register to read
   */
  public OPT_RegisterOperand getGPRegister32(X86_Laziness laziness, int r) {
    GP32InUse[r] = true;
    resolveGPRegister32(laziness, r);
    return new OPT_RegisterOperand(GP32[r], VM_TypeReference.Int);
  }

  /**
   * Read a 16bit register
   * @param laziness the lazy state, used to determine register mangling
   * @param r the register to read
   */
  public OPT_RegisterOperand getGPRegister16(X86_Laziness laziness, int r) {
    GP32InUse[r] = true;
    GP16InUse[r] = true;
    OPT_RegisterOperand result;
    // Get or create 16bit result register
    if (GP16[r] != null) {
      result = new OPT_RegisterOperand(GP16[r], VM_TypeReference.Int);
    } else {
      result = makeTemp(VM_TypeReference.Int);
      GP16[r] = result.register;
    }
    if (laziness.is16bitRegisterValid(r) == false) {
      // 16bit register isn't valid so either combine from smaller
      // registers or take from 32bit register
      if (laziness.is32bitRegisterValid(r)) {
        // 32bit register is valid so just move that to use the lower 16bits
        appendInstruction(Move.create(INT_MOVE, result.copyRO(),
            new OPT_RegisterOperand(GP32[r], VM_TypeReference.Int)));
      } else { // 8bit registers
        // both XL and XH are valid
        // XX = ((?H & 0xFF)<<8) | (?L & 0xFF)
        OPT_RegisterOperand reg8_h = new OPT_RegisterOperand(GP8[r + 4],
            VM_TypeReference.Int);
        OPT_RegisterOperand reg8_l = new OPT_RegisterOperand(GP8[r],
            VM_TypeReference.Int);
        appendInstruction(Binary.create(INT_AND, result.copyRO(),
            reg8_h.copyRO(), new OPT_IntConstantOperand(0xFF)));
        appendInstruction(Binary.create(INT_SHL, result.copyRO(),
            result.copyRO(), new OPT_IntConstantOperand(8)));
        appendInstruction(Binary.create(INT_AND, reg8_l, reg8_l
            .copyRO(), new OPT_IntConstantOperand(0xFF)));
        appendInstruction(Binary.create(INT_OR, result.copyRO(),
            result.copyRO(), reg8_l.copyRO()));
      }
      laziness.set16bitRegisterValid(r);
    }
    return result;
  }

  /**
   * Read a 8bit register
   * @param laziness the lazy state, used to determine register mangling
   * @param r the register to read
   */
  public OPT_RegisterOperand getGPRegister8(X86_Laziness laziness, int r) {
    int rl, rh; // low and high 8bit registers
    if (r > 4) {
      rh = r;
      rl = r - 4;
    } else {
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
        rlOp = new OPT_RegisterOperand(GP8[rl], VM_TypeReference.Int);
      } else {
        rlOp = makeTemp(VM_TypeReference.Int);
        GP8[rl] = rlOp.register;
      }
      if (GP8[rh] != null) {
        rhOp = new OPT_RegisterOperand(GP8[rh], VM_TypeReference.Int);
      } else {
        rhOp = makeTemp(VM_TypeReference.Int);
        GP8[rh] = rhOp.register;
      }
      // 8bit register isn't valid so take from either 32bit or 16bit
      // register
      if (laziness.is32bitRegisterValid(rl)) { // 32bit register is valid
        appendInstruction(Move.create(INT_MOVE, rlOp,
            new OPT_RegisterOperand(GP32[rl], VM_TypeReference.Int)));
      } else { // 16bit register is valid
        appendInstruction(Move.create(INT_MOVE, rlOp,
            new OPT_RegisterOperand(GP16[rl], VM_TypeReference.Int)));
      }
      appendInstruction(Binary.create(INT_SHL, rhOp, rlOp
          .copyRO(), new OPT_IntConstantOperand(8)));
      laziness.set8bitRegisterValid(rl);
    }
    return new OPT_RegisterOperand(GP8[r], VM_TypeReference.Int);
  }

  /**
   * Read a 8bit register
   * @param laziness the lazy state, used to determine register mangling
   * @param r the register to read
   */
  public OPT_RegisterOperand getGPRegister(X86_Laziness laziness, int r,
      int size) {
    switch (size) {
    case 32:
      return getGPRegister32(laziness, r);
    case 16:
      return getGPRegister16(laziness, r);
    case 8:
      return getGPRegister8(laziness, r);
    default:
      DBT_OptimizingCompilerException.UNREACHABLE();
      return null; // keep jikes happy
    }
  }

  /**
   * Read the MXCSR register
   */
  public OPT_RegisterOperand getMXCSR() {
    ps_registers_mxcsr_InUse = true;
    return new OPT_RegisterOperand(ps_registers_mxcsr, VM_TypeReference.Int);
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
   * X86 flag register constituants - bit 4 - AF or auxiliary carry flag or
   * adjust flag
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
   * Wrap the direction flag up in a register operand and return
   */
  OPT_RegisterOperand getDirectionFlag() {
    flag_DF_InUse = true;
    return new OPT_RegisterOperand(flag_DF, VM_TypeReference.Boolean);
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
    appendInstruction(Move.create(INT_MOVE, result.copyRO(),
        new OPT_IntConstantOperand(0)));
    return result;
  }

  // -- All registers

  /**
   * A register holding a reference to ps.registers
   */
  private OPT_Register ps_registers;

  /**
   * A register holding a reference to ps.registers.segmentRegister
   */
  private OPT_Register ps_registers_segReg;

  /**
   * A register holding a reference to ps.registers.gp32
   */
  private OPT_Register ps_registers_gp32;

  /**
   * X87 mxcsr register
   */
  private OPT_Register ps_registers_mxcsr;

  /**
   * Was the register used during the trace?
   */
  private boolean ps_registers_mxcsr_InUse;

  /**
   * Fill all the registers from the ProcessSpace, that is take the register
   * values from the process space and place them in the traces registers.
   */
  protected void fillAllRegisters() {
    OPT_RegisterOperand ps_registersOp;
    // Get the registers
    if (ps_registers == null) {
      // Set up the reference to memory
      ps_registersOp = gc.temps.makeTemp(registersTref);
      ps_registers = ps_registersOp.register;
      appendInstruction(GetField.create(GETFIELD, ps_registersOp,
          gc.makeLocal(1, psTref), new OPT_AddressConstantOperand(registersFref
              .peekResolvedField().getOffset()), new OPT_LocationOperand(
              registersFref), new OPT_TrueGuardOperand()));
    } else {
      ps_registersOp = new OPT_RegisterOperand(ps_registers, registersTref);
    }
    // Get the array of segment registers
    OPT_RegisterOperand ps_registers_segRegOp;
    if (ps_registers_segReg == null) {
      ps_registers_segRegOp = gc.temps.makeTemp(segRegTref);
      appendInstruction(GetField.create(GETFIELD,
          ps_registers_segRegOp, ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(segRegFref.peekResolvedField()
              .getOffset()), new OPT_LocationOperand(segRegFref),
          new OPT_TrueGuardOperand()));
      ps_registers_segReg = ps_registers_segRegOp.register;
    } else {
      ps_registers_segRegOp = new OPT_RegisterOperand(ps_registers_segReg, segRegTref);
    }
    // Get the array of general purpose registers
    OPT_RegisterOperand ps_registers_gp32Op;
    if (ps_registers_gp32 == null) {
      ps_registers_gp32Op = gc.temps.makeTemp(gp32Tref);
      appendInstruction(GetField.create(GETFIELD,
          ps_registers_gp32Op, ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(gp32Fref.peekResolvedField()
              .getOffset()), new OPT_LocationOperand(gp32Fref),
          new OPT_TrueGuardOperand()));
      ps_registers_gp32 = ps_registers_gp32Op.register;
    } else {
      ps_registers_gp32Op = new OPT_RegisterOperand(ps_registers_gp32, gp32Tref);
    }
    // Fill segment registers
    for (int i = 0; i < SegReg.length; i++) {
      OPT_RegisterOperand segRegOp;
      if (GP32[i] == null) {
        segRegOp = makeTemp(VM_TypeReference.Char);
        SegReg[i] = segRegOp.register;
      } else {
        segRegOp = new OPT_RegisterOperand(SegReg[i], VM_TypeReference.Char);
      }
      appendInstruction(ALoad.create(USHORT_ALOAD, segRegOp,
          ps_registers_segRegOp.copyRO(), new OPT_IntConstantOperand(i),
          new OPT_LocationOperand(VM_TypeReference.Char),
          new OPT_TrueGuardOperand()));
    }
    // Fill general purpose registers
    for (int i = 0; i < GP32.length; i++) {
      OPT_RegisterOperand gp32op;
      if (GP32[i] == null) {
        gp32op = makeTemp(VM_TypeReference.Int);
        GP32[i] = gp32op.register;
      } else {
        gp32op = new OPT_RegisterOperand(GP32[i], VM_TypeReference.Int);
      }
      appendInstruction(ALoad.create(INT_ALOAD, gp32op,
          ps_registers_gp32Op.copyRO(), new OPT_IntConstantOperand(i),
          new OPT_LocationOperand(VM_TypeReference.Int),
          new OPT_TrueGuardOperand()));
    }
    // Fill MXCSR
    {
      OPT_RegisterOperand ps_registers_mxcsr_Op;
      if (ps_registers_mxcsr == null) {
        ps_registers_mxcsr_Op = makeTemp(VM_TypeReference.Int);
        ps_registers_mxcsr = ps_registers_mxcsr_Op.register;
      } else {
        ps_registers_mxcsr_Op = new OPT_RegisterOperand(flag_CF, VM_TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, ps_registers_mxcsr_Op,
          ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(mxcsrFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(mxcsrFref),
          new OPT_TrueGuardOperand()));
    }
    // Fill flags
    {
      OPT_RegisterOperand flag_CF_Op;
      if (flag_CF == null) {
        flag_CF_Op = makeTemp(VM_TypeReference.Boolean);
        flag_CF = flag_CF_Op.register;
      } else {
        flag_CF_Op = new OPT_RegisterOperand(flag_CF, VM_TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_CF_Op,
          ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagCFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagCFref),
          new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_SF_Op;
      if (flag_SF == null) {
        flag_SF_Op = makeTemp(VM_TypeReference.Boolean);
        flag_SF = flag_SF_Op.register;
      } else {
        flag_SF_Op = new OPT_RegisterOperand(flag_SF, VM_TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_SF_Op,
          ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagSFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagSFref),
          new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_ZF_Op;
      if (flag_ZF == null) {
        flag_ZF_Op = makeTemp(VM_TypeReference.Boolean);
        flag_ZF = flag_ZF_Op.register;
      } else {
        flag_ZF_Op = new OPT_RegisterOperand(flag_ZF, VM_TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_ZF_Op,
          ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagZFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagZFref),
          new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_OF_Op;
      if (flag_OF == null) {
        flag_OF_Op = makeTemp(VM_TypeReference.Boolean);
        flag_OF = flag_OF_Op.register;
      } else {
        flag_OF_Op = new OPT_RegisterOperand(flag_OF, VM_TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_OF_Op,
          ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagOFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagOFref),
          new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_DF_Op;
      if (flag_DF == null) {
        flag_DF_Op = makeTemp(VM_TypeReference.Boolean);
        flag_DF = flag_DF_Op.register;
      } else {
        flag_DF_Op = new OPT_RegisterOperand(flag_DF, VM_TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_DF_Op,
          ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagDFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagDFref),
          new OPT_TrueGuardOperand()));
    }
  }

  /**
   * Spill all the registers, that is put them from the current running trace
   * into the process space
   */
  protected void spillAllRegisters() {
    // spill segment registers
    OPT_RegisterOperand ps_registers_segRegOp =
      new OPT_RegisterOperand(ps_registers_segReg, segRegTref);
    for (int i = 0; i < SegReg.length; i++) {
      // We can save spills if the trace has no syscalls and the register was
      // never used
      if ((DBT_Options.singleInstrTranslation == false)
          || (SegRegInUse[i] == true)) {
        appendInstruction(AStore.create(SHORT_ASTORE,
            new OPT_RegisterOperand(GP32[i], VM_TypeReference.Int),
            ps_registers_segRegOp.copyRO(), new OPT_IntConstantOperand(i),
            new OPT_LocationOperand(VM_TypeReference.Char),
            new OPT_TrueGuardOperand()));
      }
    }
    // spill general purpose registers
    OPT_RegisterOperand ps_registers_gp32Op =
      new OPT_RegisterOperand(ps_registers_gp32, gp32Tref);
    for (int i = 0; i < GP32.length; i++) {
      // We can save spills if the trace has no syscalls and the register was
      // never used
      if ((DBT_Options.singleInstrTranslation == false)
          || (GP32InUse[i] == true)) {
        appendInstruction(AStore.create(INT_ASTORE,
            new OPT_RegisterOperand(GP32[i], VM_TypeReference.Int),
            ps_registers_gp32Op.copyRO(), new OPT_IntConstantOperand(i),
            new OPT_LocationOperand(VM_TypeReference.Int),
            new OPT_TrueGuardOperand()));
      }
    }
    OPT_RegisterOperand ps_registersOp =
      new OPT_RegisterOperand(ps_registers, registersTref);
    // Spill mxcsr
    {
      OPT_RegisterOperand ps_registers_mxcsr_Op =
        new OPT_RegisterOperand(ps_registers_mxcsr, VM_TypeReference.Int);
      appendInstruction(GetField.create(PUTFIELD,
          ps_registers_mxcsr_Op, ps_registersOp,
          new OPT_AddressConstantOperand(mxcsrFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(mxcsrFref),
          new OPT_TrueGuardOperand()));
    }

    // Spill flags
    {
      OPT_RegisterOperand flag_CF_Op =
        new OPT_RegisterOperand(flag_CF, VM_TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_CF_Op, ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagCFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagCFref),
          new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_SF_Op = new OPT_RegisterOperand(flag_SF,
          VM_TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_SF_Op, ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagSFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagSFref),
          new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_ZF_Op =
        new OPT_RegisterOperand(flag_ZF, VM_TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_ZF_Op, ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagZFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagZFref),
          new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_OF_Op =
        new OPT_RegisterOperand(flag_OF, VM_TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_OF_Op, ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagOFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagOFref),
          new OPT_TrueGuardOperand()));
    }
    {
      OPT_RegisterOperand flag_DF_Op =
        new OPT_RegisterOperand(flag_DF, VM_TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_DF_Op, ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(flagDFref.peekResolvedField().getOffset()),
          new OPT_LocationOperand(flagDFref),
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
   * Plant instructions modifying a lazy state into one with no laziness
   * @param laziness the laziness to modify
   */
  public void resolveLaziness(Laziness laziness) {
    for (int i = 0; i < GP32.length; i++) {
      resolveGPRegister32((X86_Laziness) laziness, i);
    }
  }

  /**
   * Return an array of unused registers
   */
  protected OPT_Register[] getUnusedRegisters() {
    ArrayList<OPT_Register> unusedRegisterList = new ArrayList<OPT_Register>();
    // Add general purpose registers
    for (int i = 0; i < SegRegInUse.length; i++) {
      if (SegRegInUse[i] == false) {
        unusedRegisterList.add(SegReg[i]);
      }
    }
    // Add general purpose registers
    for (int i = 0; i < GP32InUse.length; i++) {
      if (GP32InUse[i] == false) {
        unusedRegisterList.add(GP32[i]);
      }
    }
    // ignore GP16 and GP8 registers as they are only created lazily,
    // and so must be in use

    // Add MXCSR
    if (ps_registers_mxcsr_InUse == false) {
      unusedRegisterList.add(ps_registers_mxcsr);
    }
    // Add flags
    if (flag_CF_InUse == false) {
      unusedRegisterList.add(flag_CF);
    }
    if (flag_SF_InUse == false) {
      unusedRegisterList.add(flag_SF);
    }
    if (flag_ZF_InUse == false) {
      unusedRegisterList.add(flag_ZF);
    }
    if (flag_OF_InUse == false) {
      unusedRegisterList.add(flag_OF);
    }
    return unusedRegisterList.toArray(
        new OPT_Register[unusedRegisterList.size()]);
  }
}
