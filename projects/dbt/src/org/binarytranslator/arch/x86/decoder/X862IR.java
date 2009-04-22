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
import org.binarytranslator.generic.decoder.CodeTranslator;
import org.binarytranslator.generic.decoder.Laziness;
import org.binarytranslator.vmInterface.DBT_OptimizingCompilerException;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.*;
import org.jikesrvm.compilers.opt.ir.operand.*;

public class X862IR extends CodeTranslator implements HIRGenerator,
    Operators, OptConstants {

  private static final TypeReference  psTref;
  private static final FieldReference registersFref;
  private static final TypeReference  registersTref;
  private static final FieldReference segRegFref;
  private static final TypeReference  segRegTref;
  private static final FieldReference gp32Fref;
  private static final TypeReference  gp32Tref;
  private static final FieldReference flagCFref;
  private static final FieldReference flagSFref;
  private static final FieldReference flagZFref;
  private static final FieldReference flagOFref;
  private static final FieldReference flagDFref;
  private static final FieldReference gsBaseAddrFref;
  private static final FieldReference mxcsrFref;
  static {
    psTref = TypeReference.findOrCreate(
        BootstrapClassLoader.getBootstrapClassLoader(),
        Atom
            .findOrCreateAsciiAtom("Lorg/binarytranslator/arch/x86/os/process/X86_ProcessSpace;"));

    registersFref = MemberReference
    .findOrCreate(
        psTref,
        Atom.findOrCreateAsciiAtom("registers"),
        Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/x86/os/process/X86_Registers;"))
    .asFieldReference();
    
    registersTref = registersFref.getFieldContentsType();

    segRegFref = MemberReference.findOrCreate(
        registersTref, Atom.findOrCreateAsciiAtom("segmentRegister"),
        Atom.findOrCreateAsciiAtom("[C")).asFieldReference();

    segRegTref = segRegFref.getFieldContentsType();
    
    gp32Fref = MemberReference.findOrCreate(
      registersTref, Atom.findOrCreateAsciiAtom("gp32"),
      Atom.findOrCreateAsciiAtom("[I")).asFieldReference();
    
    gp32Tref = gp32Fref.getFieldContentsType();

    flagCFref = MemberReference.findOrCreate(
        registersTref, Atom.findOrCreateAsciiAtom("flag_CF"),
        Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    flagSFref = MemberReference.findOrCreate(
        registersTref, Atom.findOrCreateAsciiAtom("flag_SF"),
        Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    flagZFref = MemberReference.findOrCreate(
        registersTref, Atom.findOrCreateAsciiAtom("flag_ZF"),
        Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    flagOFref = MemberReference.findOrCreate(
        registersTref, Atom.findOrCreateAsciiAtom("flag_OF"),
        Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    flagDFref = MemberReference.findOrCreate(
        registersTref, Atom.findOrCreateAsciiAtom("flag_DF"),
        Atom.findOrCreateAsciiAtom("Z")).asFieldReference();

    gsBaseAddrFref = MemberReference.findOrCreate(
        registersTref, Atom.findOrCreateAsciiAtom("gsBaseAddr"),
        Atom.findOrCreateAsciiAtom("I")).asFieldReference();

    mxcsrFref = MemberReference.findOrCreate(
        registersTref, Atom.findOrCreateAsciiAtom("mxcsr"),
        Atom.findOrCreateAsciiAtom("I")).asFieldReference();
    }

  /**
   * Constructor
   */
  public X862IR(GenerationContext context, DBT_Trace trace) {
    super(context, trace);

    // Create the registers
    SegReg = new Register[6];
    SegRegInUse = new boolean[6];
    
    GP32 = new Register[8];
    GP32InUse = new boolean[8];

    GP16 = new Register[8];
    GP16InUse = new boolean[8];

    GP8 = new Register[8];
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
    appendSystemCall(lazy);
    // Get return address
    X86_DecodedOperand source = X86_DecodedOperand.getStack(X86_ProcessSpace._16BIT ? 16 : 32,
        X86_ProcessSpace._16BIT ? 16 : 32);
    RegisterOperand temp = getTempInt(0);
    source.readToRegister(this, lazy, temp);

    // Increment stack pointer
    RegisterOperand esp = getGPRegister(lazy, X86_Registers.ESP, X86_ProcessSpace._16BIT ? 16 : 32);
    appendInstruction(Binary.create(INT_ADD, esp, esp.copyRO(), new IntConstantOperand(4)));
    
    // Branch
    appendTraceExit((X86_Laziness) lazy.clone(), temp.copyRO());
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
  private Register[] SegReg;

  /**
   * Which 16bit segment registers have been used during the trace - unused
   * registers can be eliminated
   */
  private boolean[] SegRegInUse;

  /**
   * Registers holding 32bit values during the trace
   */
  private Register[] GP32;

  /**
   * Which 32bit registers have been used during the trace - unused registers
   * can be eliminated
   */
  private boolean[] GP32InUse;

  /**
   * Registers holding 16bit values during the trace
   */
  private Register[] GP16;

  /**
   * Which 16bit registers have been used during the trace - unused registers
   * can be eliminated
   */
  private boolean[] GP16InUse;

  /**
   * Registers holding 8bit values during the trace
   */
  private Register[] GP8;

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
      RegisterOperand result = new RegisterOperand(GP32[r],
          TypeReference.Int);
      // 32bit register isn't valid so combine from smaller registers
      if (laziness.is16bitRegisterValid(r)) {
        // EXX = (EXX & 0xFFFF0000) | (XX & 0xFFFF)
        RegisterOperand reg16 = new RegisterOperand(GP16[r],
            TypeReference.Int);
        appendInstruction(Binary.create(INT_AND, result, result
            .copyRO(), new IntConstantOperand(0xFFFF0000)));
        appendInstruction(Binary.create(INT_AND, reg16, reg16
            .copyRO(), new IntConstantOperand(0xFFFF)));
        appendInstruction(Binary.create(INT_OR, result.copyRO(),
            result.copyRO(), reg16.copyRO()));
      } else { // 8bit registers
        // both XL and Xh are valid
        // EXX = (EXX & 0xFFFF0000) | ((XH & 0xFF)<<8) | (XL & 0xFF)
        RegisterOperand reg8_h = new RegisterOperand(GP8[r + 4],
            TypeReference.Int);
        RegisterOperand reg8_l = new RegisterOperand(GP8[r],
            TypeReference.Int);
        appendInstruction(Binary.create(INT_AND, result, result
            .copyRO(), new IntConstantOperand(0xFFFF0000)));
        appendInstruction(Binary.create(INT_AND, reg8_h, reg8_h
            .copyRO(), new IntConstantOperand(0xFF)));
        appendInstruction(Binary.create(INT_SHL, reg8_h.copyRO(),
            reg8_h.copyRO(), new IntConstantOperand(8)));
        appendInstruction(Binary.create(INT_AND, reg8_l, reg8_l
            .copyRO(), new IntConstantOperand(0xFF)));
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
  public RegisterOperand getSegRegister(X86_Laziness laziness, int r) {
    SegRegInUse[r] = true;
    return new RegisterOperand(SegReg[r], TypeReference.Int);
  }

  /**
   * Add a segment base address to the given address
   * @param segment segment to get base address for
   * @param address the address to add the value onto
   */
  public void addSegmentBaseAddress(int segment, RegisterOperand address) {
    switch(segment) {
    case X86_Registers.GS: {
      RegisterOperand temp = getTempInt(9);
      appendInstruction(GetField.create(GETFIELD,
          temp, new RegisterOperand(ps_registers, registersTref),
          new AddressConstantOperand(gsBaseAddrFref.peekResolvedField().getOffset()),
          new LocationOperand(gsBaseAddrFref),
          new TrueGuardOperand()));
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
  public RegisterOperand getGPRegister32(X86_Laziness laziness, int r) {
    GP32InUse[r] = true;
    resolveGPRegister32(laziness, r);
    return new RegisterOperand(GP32[r], TypeReference.Int);
  }

  /**
   * Read a 16bit register
   * @param laziness the lazy state, used to determine register mangling
   * @param r the register to read
   */
  public RegisterOperand getGPRegister16(X86_Laziness laziness, int r) {
    GP32InUse[r] = true;
    GP16InUse[r] = true;
    RegisterOperand result;
    // Get or create 16bit result register
    if (GP16[r] != null) {
      result = new RegisterOperand(GP16[r], TypeReference.Int);
    } else {
      result = makeTemp(TypeReference.Int);
      GP16[r] = result.register;
    }
    if (laziness.is16bitRegisterValid(r) == false) {
      // 16bit register isn't valid so either combine from smaller
      // registers or take from 32bit register
      if (laziness.is32bitRegisterValid(r)) {
        // 32bit register is valid so just move that to use the lower 16bits
        appendInstruction(Move.create(INT_MOVE, result.copyRO(),
            new RegisterOperand(GP32[r], TypeReference.Int)));
      } else { // 8bit registers
        // both XL and XH are valid
        // XX = ((?H & 0xFF)<<8) | (?L & 0xFF)
        RegisterOperand reg8_h = new RegisterOperand(GP8[r + 4],
            TypeReference.Int);
        RegisterOperand reg8_l = new RegisterOperand(GP8[r],
            TypeReference.Int);
        appendInstruction(Binary.create(INT_AND, result.copyRO(),
            reg8_h.copyRO(), new IntConstantOperand(0xFF)));
        appendInstruction(Binary.create(INT_SHL, result.copyRO(),
            result.copyRO(), new IntConstantOperand(8)));
        appendInstruction(Binary.create(INT_AND, reg8_l, reg8_l
            .copyRO(), new IntConstantOperand(0xFF)));
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
  public RegisterOperand getGPRegister8(X86_Laziness laziness, int r) {
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
      RegisterOperand rlOp, rhOp;
      // Get or create registers to hold 8bit values
      if (GP8[rl] != null) {
        rlOp = new RegisterOperand(GP8[rl], TypeReference.Int);
      } else {
        rlOp = makeTemp(TypeReference.Int);
        GP8[rl] = rlOp.register;
      }
      if (GP8[rh] != null) {
        rhOp = new RegisterOperand(GP8[rh], TypeReference.Int);
      } else {
        rhOp = makeTemp(TypeReference.Int);
        GP8[rh] = rhOp.register;
      }
      // 8bit register isn't valid so take from either 32bit or 16bit
      // register
      if (laziness.is32bitRegisterValid(rl)) { // 32bit register is valid
        appendInstruction(Move.create(INT_MOVE, rlOp,
            new RegisterOperand(GP32[rl], TypeReference.Int)));
      } else { // 16bit register is valid
        appendInstruction(Move.create(INT_MOVE, rlOp,
            new RegisterOperand(GP16[rl], TypeReference.Int)));
      }
      appendInstruction(Binary.create(INT_SHL, rhOp, rlOp
          .copyRO(), new IntConstantOperand(8)));
      laziness.set8bitRegisterValid(rl);
    }
    return new RegisterOperand(GP8[r], TypeReference.Int);
  }

  /**
   * Read a 8bit register
   * @param laziness the lazy state, used to determine register mangling
   * @param r the register to read
   */
  public RegisterOperand getGPRegister(X86_Laziness laziness, int r,
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
  public RegisterOperand getMXCSR() {
    ps_registers_mxcsr_InUse = true;
    return new RegisterOperand(ps_registers_mxcsr, TypeReference.Int);
  }
  
  // -- status flags
  /**
   * X86 flag register constituants - bit 0 - CF or carry flag
   */
  private Register flag_CF;

  /**
   * Was the register used during the trace?
   */
  private boolean flag_CF_InUse;

  /**
   * X86 flag register constituants - bit 2 - PF or parity flag
   */
  private Register flag_PF;

  /**
   * Was the register used during the trace?
   */
  private boolean flag_PF_InUse;

  /**
   * X86 flag register constituants - bit 4 - AF or auxiliary carry flag or
   * adjust flag
   */
  private Register flag_AF;

  /**
   * Was the register used during the trace?
   */
  private boolean flag_AF_InUse;

  /**
   * X86 flag register constituants - bit 6 - ZF or zero flag
   */
  private Register flag_ZF;

  /**
   * Was the register used during the trace?
   */
  private boolean flag_ZF_InUse;

  /**
   * X86 flag register constituants - bit 7 - SF or sign flag
   */
  private Register flag_SF;

  /**
   * Was the register used during the trace?
   */
  private boolean flag_SF_InUse;

  /**
   * X86 flag register constituants - bit 11 - OF or overflow flag
   */
  private Register flag_OF;

  /**
   * Was the register used during the trace?
   */
  private boolean flag_OF_InUse;

  /**
   * X86 flag register constituants - bit 10 - DF or direction flag
   */
  private Register flag_DF;

  /**
   * Was the register used during the trace?
   */
  private boolean flag_DF_InUse;

  /**
   * Wrap the carry flag up in a register operand and return
   */
  RegisterOperand getCarryFlag() {
    flag_CF_InUse = true;
    return new RegisterOperand(flag_CF, TypeReference.Boolean);
  }

  /**
   * Wrap the sign flag up in a register operand and return
   */
  RegisterOperand getSignFlag() {
    flag_SF_InUse = true;
    return new RegisterOperand(flag_SF, TypeReference.Boolean);
  }

  /**
   * Wrap the zero flag up in a register operand and return
   */
  RegisterOperand getZeroFlag() {
    flag_ZF_InUse = true;
    return new RegisterOperand(flag_ZF, TypeReference.Boolean);
  }

  /**
   * Wrap the direction flag up in a register operand and return
   */
  RegisterOperand getDirectionFlag() {
    flag_DF_InUse = true;
    return new RegisterOperand(flag_DF, TypeReference.Boolean);
  }

  /**
   * Wrap the overflow flag up in a register operand and return
   */
  RegisterOperand getOverflowFlag() {
    flag_OF_InUse = true;
    return new RegisterOperand(flag_OF, TypeReference.Boolean);
  }

  // -- FPU registers
  /**
   * Control word
   */
  RegisterOperand getFPU_CW() {
    RegisterOperand result = makeTemp(TypeReference.Int);
    appendInstruction(Move.create(INT_MOVE, result.copyRO(),
        new IntConstantOperand(0)));
    return result;
  }

  // -- All registers

  /**
   * A register holding a reference to ps.registers
   */
  private Register ps_registers;

  /**
   * A register holding a reference to ps.registers.segmentRegister
   */
  private Register ps_registers_segReg;

  /**
   * A register holding a reference to ps.registers.gp32
   */
  private Register ps_registers_gp32;

  /**
   * X87 mxcsr register
   */
  private Register ps_registers_mxcsr;

  /**
   * Was the register used during the trace?
   */
  private boolean ps_registers_mxcsr_InUse;

  /**
   * Fill all the registers from the ProcessSpace, that is take the register
   * values from the process space and place them in the traces registers.
   */
  protected void fillAllRegisters() {
    RegisterOperand ps_registersOp;
    // Get the registers
    if (ps_registers == null) {
      // Set up the reference to memory
      ps_registersOp = gc.temps.makeTemp(registersTref);
      ps_registers = ps_registersOp.register;
      appendInstruction(GetField.create(GETFIELD, ps_registersOp,
          gc.makeLocal(1, psTref), new AddressConstantOperand(registersFref
              .peekResolvedField().getOffset()), new LocationOperand(
              registersFref), new TrueGuardOperand()));
    } else {
      ps_registersOp = new RegisterOperand(ps_registers, registersTref);
    }
    // Get the array of segment registers
    RegisterOperand ps_registers_segRegOp;
    if (ps_registers_segReg == null) {
      ps_registers_segRegOp = gc.temps.makeTemp(segRegTref);
      appendInstruction(GetField.create(GETFIELD,
          ps_registers_segRegOp, ps_registersOp.copyRO(),
          new AddressConstantOperand(segRegFref.peekResolvedField()
              .getOffset()), new LocationOperand(segRegFref),
          new TrueGuardOperand()));
      ps_registers_segReg = ps_registers_segRegOp.register;
    } else {
      ps_registers_segRegOp = new RegisterOperand(ps_registers_segReg, segRegTref);
    }
    // Get the array of general purpose registers
    RegisterOperand ps_registers_gp32Op;
    if (ps_registers_gp32 == null) {
      ps_registers_gp32Op = gc.temps.makeTemp(gp32Tref);
      appendInstruction(GetField.create(GETFIELD,
          ps_registers_gp32Op, ps_registersOp.copyRO(),
          new AddressConstantOperand(gp32Fref.peekResolvedField()
              .getOffset()), new LocationOperand(gp32Fref),
          new TrueGuardOperand()));
      ps_registers_gp32 = ps_registers_gp32Op.register;
    } else {
      ps_registers_gp32Op = new RegisterOperand(ps_registers_gp32, gp32Tref);
    }
    // Fill segment registers
    for (int i = 0; i < SegReg.length; i++) {
      RegisterOperand segRegOp;
      if (GP32[i] == null) {
        segRegOp = makeTemp(TypeReference.Char);
        SegReg[i] = segRegOp.register;
      } else {
        segRegOp = new RegisterOperand(SegReg[i], TypeReference.Char);
      }
      appendInstruction(ALoad.create(USHORT_ALOAD, segRegOp,
          ps_registers_segRegOp.copyRO(), new IntConstantOperand(i),
          new LocationOperand(TypeReference.Char),
          new TrueGuardOperand()));
    }
    // Fill general purpose registers
    for (int i = 0; i < GP32.length; i++) {
      RegisterOperand gp32op;
      if (GP32[i] == null) {
        gp32op = makeTemp(TypeReference.Int);
        GP32[i] = gp32op.register;
      } else {
        gp32op = new RegisterOperand(GP32[i], TypeReference.Int);
      }
      appendInstruction(ALoad.create(INT_ALOAD, gp32op,
          ps_registers_gp32Op.copyRO(), new IntConstantOperand(i),
          new LocationOperand(TypeReference.Int),
          new TrueGuardOperand()));
    }
    // Fill MXCSR
    {
      RegisterOperand ps_registers_mxcsr_Op;
      if (ps_registers_mxcsr == null) {
        ps_registers_mxcsr_Op = makeTemp(TypeReference.Int);
        ps_registers_mxcsr = ps_registers_mxcsr_Op.register;
      } else {
        ps_registers_mxcsr_Op = new RegisterOperand(flag_CF, TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, ps_registers_mxcsr_Op,
          ps_registersOp.copyRO(),
          new AddressConstantOperand(mxcsrFref.peekResolvedField().getOffset()),
          new LocationOperand(mxcsrFref),
          new TrueGuardOperand()));
    }
    // Fill flags
    {
      RegisterOperand flag_CF_Op;
      if (flag_CF == null) {
        flag_CF_Op = makeTemp(TypeReference.Boolean);
        flag_CF = flag_CF_Op.register;
      } else {
        flag_CF_Op = new RegisterOperand(flag_CF, TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_CF_Op,
          ps_registersOp.copyRO(),
          new AddressConstantOperand(flagCFref.peekResolvedField().getOffset()),
          new LocationOperand(flagCFref),
          new TrueGuardOperand()));
    }
    {
      RegisterOperand flag_SF_Op;
      if (flag_SF == null) {
        flag_SF_Op = makeTemp(TypeReference.Boolean);
        flag_SF = flag_SF_Op.register;
      } else {
        flag_SF_Op = new RegisterOperand(flag_SF, TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_SF_Op,
          ps_registersOp.copyRO(),
          new AddressConstantOperand(flagSFref.peekResolvedField().getOffset()),
          new LocationOperand(flagSFref),
          new TrueGuardOperand()));
    }
    {
      RegisterOperand flag_ZF_Op;
      if (flag_ZF == null) {
        flag_ZF_Op = makeTemp(TypeReference.Boolean);
        flag_ZF = flag_ZF_Op.register;
      } else {
        flag_ZF_Op = new RegisterOperand(flag_ZF, TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_ZF_Op,
          ps_registersOp.copyRO(),
          new AddressConstantOperand(flagZFref.peekResolvedField().getOffset()),
          new LocationOperand(flagZFref),
          new TrueGuardOperand()));
    }
    {
      RegisterOperand flag_OF_Op;
      if (flag_OF == null) {
        flag_OF_Op = makeTemp(TypeReference.Boolean);
        flag_OF = flag_OF_Op.register;
      } else {
        flag_OF_Op = new RegisterOperand(flag_OF, TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_OF_Op,
          ps_registersOp.copyRO(),
          new AddressConstantOperand(flagOFref.peekResolvedField().getOffset()),
          new LocationOperand(flagOFref),
          new TrueGuardOperand()));
    }
    {
      RegisterOperand flag_DF_Op;
      if (flag_DF == null) {
        flag_DF_Op = makeTemp(TypeReference.Boolean);
        flag_DF = flag_DF_Op.register;
      } else {
        flag_DF_Op = new RegisterOperand(flag_DF, TypeReference.Boolean);
      }
      appendInstruction(GetField.create(GETFIELD, flag_DF_Op,
          ps_registersOp.copyRO(),
          new AddressConstantOperand(flagDFref.peekResolvedField().getOffset()),
          new LocationOperand(flagDFref),
          new TrueGuardOperand()));
    }
  }

  /**
   * Spill all the registers, that is put them from the current running trace
   * into the process space
   */
  protected void spillAllRegisters() {
    // spill segment registers
    RegisterOperand ps_registers_segRegOp =
      new RegisterOperand(ps_registers_segReg, segRegTref);
    for (int i = 0; i < SegReg.length; i++) {
      // We can save spills if the trace has no syscalls and the register was
      // never used
      if ((DBT_Options.singleInstrTranslation == false)
          || (SegRegInUse[i] == true)) {
        appendInstruction(AStore.create(SHORT_ASTORE,
            new RegisterOperand(GP32[i], TypeReference.Int),
            ps_registers_segRegOp.copyRO(), new IntConstantOperand(i),
            new LocationOperand(TypeReference.Char),
            new TrueGuardOperand()));
      }
    }
    // spill general purpose registers
    RegisterOperand ps_registers_gp32Op =
      new RegisterOperand(ps_registers_gp32, gp32Tref);
    for (int i = 0; i < GP32.length; i++) {
      // We can save spills if the trace has no syscalls and the register was
      // never used
      if ((DBT_Options.singleInstrTranslation == false)
          || (GP32InUse[i] == true)) {
        appendInstruction(AStore.create(INT_ASTORE,
            new RegisterOperand(GP32[i], TypeReference.Int),
            ps_registers_gp32Op.copyRO(), new IntConstantOperand(i),
            new LocationOperand(TypeReference.Int),
            new TrueGuardOperand()));
      }
    }
    RegisterOperand ps_registersOp =
      new RegisterOperand(ps_registers, registersTref);
    // Spill mxcsr
    {
      RegisterOperand ps_registers_mxcsr_Op =
        new RegisterOperand(ps_registers_mxcsr, TypeReference.Int);
      appendInstruction(GetField.create(PUTFIELD,
          ps_registers_mxcsr_Op, ps_registersOp,
          new AddressConstantOperand(mxcsrFref.peekResolvedField().getOffset()),
          new LocationOperand(mxcsrFref),
          new TrueGuardOperand()));
    }

    // Spill flags
    {
      RegisterOperand flag_CF_Op =
        new RegisterOperand(flag_CF, TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_CF_Op, ps_registersOp.copyRO(),
          new AddressConstantOperand(flagCFref.peekResolvedField().getOffset()),
          new LocationOperand(flagCFref),
          new TrueGuardOperand()));
    }
    {
      RegisterOperand flag_SF_Op = new RegisterOperand(flag_SF,
          TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_SF_Op, ps_registersOp.copyRO(),
          new AddressConstantOperand(flagSFref.peekResolvedField().getOffset()),
          new LocationOperand(flagSFref),
          new TrueGuardOperand()));
    }
    {
      RegisterOperand flag_ZF_Op =
        new RegisterOperand(flag_ZF, TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_ZF_Op, ps_registersOp.copyRO(),
          new AddressConstantOperand(flagZFref.peekResolvedField().getOffset()),
          new LocationOperand(flagZFref),
          new TrueGuardOperand()));
    }
    {
      RegisterOperand flag_OF_Op =
        new RegisterOperand(flag_OF, TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_OF_Op, ps_registersOp.copyRO(),
          new AddressConstantOperand(flagOFref.peekResolvedField().getOffset()),
          new LocationOperand(flagOFref),
          new TrueGuardOperand()));
    }
    {
      RegisterOperand flag_DF_Op =
        new RegisterOperand(flag_DF, TypeReference.Boolean);
      appendInstruction(GetField.create(PUTFIELD,
          flag_DF_Op, ps_registersOp.copyRO(),
          new AddressConstantOperand(flagDFref.peekResolvedField().getOffset()),
          new LocationOperand(flagDFref),
          new TrueGuardOperand()));
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
  protected Register[] getUnusedRegisters() {
    ArrayList<Register> unusedRegisterList = new ArrayList<Register>();
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
        new Register[unusedRegisterList.size()]);
  }
}
