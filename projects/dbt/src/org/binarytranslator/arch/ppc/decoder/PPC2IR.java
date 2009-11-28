/*
 *  This file is part of MRP (http://mrp.codehaus.org/).
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
package org.binarytranslator.arch.ppc.decoder;

// DBT classes
import java.util.ArrayList;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.ppc.os.process.PPC_ProcessSpace;
import org.binarytranslator.generic.decoder.CodeTranslator;
import org.binarytranslator.generic.decoder.Laziness;
import org.binarytranslator.vmInterface.DBT_OptimizingCompilerException;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.HIRGenerator;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;

/**
 * Translation from PPC machine code to HIR.
 * 
 * @author Richard Matley, Ian Rogers
 */
public final class PPC2IR extends CodeTranslator implements HIRGenerator,
    Operators, OptConstants {

  // -oO Caches of references to process space entities Oo-

  /** Type reference to the PPC process space */
  private static final TypeReference psTref;

  /** References to PPC process space GPR register fields */
  private static final FieldReference[] gprFieldRefs;

  /** References to PPC process space FPR register fields */
  private static final FieldReference[] fprFieldRefs;

  /** Reference to PPC process space condition register lt array field */
  private static final FieldReference crf_ltFieldRef;

  /** Reference to PPC process space condition register gt array field */
  private static final FieldReference crf_gtFieldRef;

  /** Reference to PPC process space condition register eq array field */
  private static final FieldReference crf_eqFieldRef;

  /** Reference to PPC process space condition register so array field */
  private static final FieldReference crf_soFieldRef;

  /** Reference to PPC process space xer_so field */
  private static final FieldReference xer_soFieldRef;

  /** Reference to PPC process space xer_ov field */
  private static final FieldReference xer_ovFieldRef;

  /** Reference to PPC process space xer_ca field */
  private static final FieldReference xer_caFieldRef;

  /** Reference to PPC process space xer_byteCount field */
  private static final FieldReference xer_byteCountFieldRef;

  /** Reference to PPC process space fpscr field */
  private static final FieldReference fpscrFieldRef;

  /** Reference to PPC process space ctr field */
  private static final FieldReference ctrFieldRef;

  /** Reference to PPC process space lr fild */
  private static final FieldReference lrFieldRef;

  /** Reference to PPC process space pc field */
  private static final FieldReference pcFieldRef;

  /* Static initializer */
  static {
    psTref = TypeReference.findOrCreate(PPC_ProcessSpace.class);
    gprFieldRefs = new FieldReference[32];
    fprFieldRefs = new FieldReference[32];
    final Atom intAtom = Atom.findOrCreateAsciiAtom("I");
    final Atom doubleAtom = Atom.findOrCreateAsciiAtom("D");
    for (int i = 0; i < 32; i++) {
      gprFieldRefs[i] = MemberReference.findOrCreate(psTref,
          Atom.findOrCreateAsciiAtom("r" + i), intAtom).asFieldReference();
      fprFieldRefs[i] = MemberReference.findOrCreate(psTref,
          Atom.findOrCreateAsciiAtom("f" + i), doubleAtom)
          .asFieldReference();
    }
    final Atom boolArrayAtom = Atom.findOrCreateAsciiAtom("[Z");
    crf_ltFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("crf_lt"), boolArrayAtom)
        .asFieldReference();
    crf_gtFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("crf_gt"), boolArrayAtom)
        .asFieldReference();
    crf_eqFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("crf_eq"), boolArrayAtom)
        .asFieldReference();
    crf_soFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("crf_so"), boolArrayAtom)
        .asFieldReference();
    final Atom boolAtom = Atom.findOrCreateAsciiAtom("Z");
    xer_soFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("xer_so"), boolAtom).asFieldReference();
    xer_ovFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("xer_ov"), boolAtom).asFieldReference();
    xer_caFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("xer_ca"), boolAtom).asFieldReference();
    final Atom byteAtom = Atom.findOrCreateAsciiAtom("B");
    xer_byteCountFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("xer_byteCount"), byteAtom)
        .asFieldReference();
    fpscrFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("fpscr"), intAtom).asFieldReference();
    ctrFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("ctr"), intAtom).asFieldReference();
    lrFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("lr"), intAtom).asFieldReference();
    pcFieldRef = MemberReference.findOrCreate(psTref,
        Atom.findOrCreateAsciiAtom("pc"), intAtom).asFieldReference();
  }

  // -oO PPC register to HIR register mappings Oo-

  /**
   * The mapping of PPC general purpose registers to HIR registers. All GP
   * registers are loaded into these by the preFill block. This avoids potential
   * inconsistencies caused by using lazy allocation and backward branches.
   */
  private final Register intRegMap[];

  /**
   * Which PPC general purpose registers are in use
   */
  private final boolean intRegInUseMap[];

  /**
   * The mapping of PPC floating point registers to HIR registers.
   */
  private final Register fpRegMap[];

  /**
   * Which PPC floating point registers are in use
   */
  private final boolean fpRegInUseMap[];

  /**
   * The HIR register holding the PPC FPSCR register.
   */
  private Register fpscrRegMap;

  /**
   * Is the PPC FPSCR register in use
   */
  private boolean fpscrRegInUse;

  /**
   * The HIR register holding the PPC CTR register.
   */
  private Register ctrRegMap;

  /**
   * Is the PPC CTR register in use
   */
  private boolean ctrRegInUse;

  /**
   * The HIR register holding the PPC LR register.
   */
  private Register lrRegMap;

  /**
   * Is the PPC LR register in use
   */
  private boolean lrRegInUse;

  /**
   * The HIR register holding the PPC XER register's byte count (bits 25 to 31)
   */
  private Register xerRegMap_ByteCount;

  /**
   * The HIR boolean register holding thr PPC XER CA (carry) bit.
   */
  private Register xerRegMap_CA;

  /**
   * The HIR register holding thr PPC XER OV (overflow) bit. If this register is
   * non-zero then the OV bit should be set.
   */
  private Register xerRegMap_OV;

  /**
   * The HIR register holding thr PPC XER SO (summary overflow) bit. If this
   * register is non-zero then the SO bit should be set.
   */
  private Register xerRegMap_SO;

  /**
   * Is the PPC XER register in use
   */
  private boolean xerRegInUse;

  /**
   * These 8 registers hold a zero or non-zero value depending on the value of
   * the corresponding condition register field's SO bit.
   */
  private final Register crFieldMap_Lt[];

  /**
   * These 8 registers hold a zero or non-zero value depending on the value of
   * the corresponding condition register field's SO bit.
   */
  private final Register crFieldMap_Gt[];

  /**
   * These 8 registers hold a zero or non-zero value depending on the value of
   * the corresponding condition register field's SO bit.
   */
  private final Register crFieldMap_Eq[];

  /**
   * These 8 registers hold a zero or non-zero value depending on the value of
   * the corresponding condition register field's SO bit.
   */
  private final Register crFieldMap_SO[];

  /**
   * What condition register fields are in use?
   */
  private final boolean crFieldInUseMap[];

  /**
   * Construct the PPC2IR object for the generation context; then we'll be ready
   * to start generating the HIR.
   */
  public PPC2IR(GenerationContext context, DBT_Trace trace) {
    super(context, trace);
    // Create register maps PPC -> Register
    intRegMap = new Register[32];
    intRegInUseMap = new boolean[32];
    fpRegMap = new Register[32];
    fpRegInUseMap = new boolean[32];
    crFieldMap_Lt = new Register[8];
    crFieldMap_Gt = new Register[8];
    crFieldMap_Eq = new Register[8];
    crFieldMap_SO = new Register[8];
    crFieldInUseMap = new boolean[8];

    // Debug
    if (DBT_Options.debugCFG) {
      report("CFG at end of constructor:\n" + gc.cfg);
    }
  }
  
  /**
   * Should a trace follow a branch and link instruction or should it terminate
   * the trace?
   * 
   * @param pc
   *          the address of the branch and link instruction
   * @return whether the trace should continue
   */
  public boolean traceContinuesAfterBranchAndLink(int pc) {
    return shallTraceStop() == false;
  }

  /**
   * Translate the instruction at the given pc
   * 
   * @param lazy
   *          the status of the lazy evaluation
   * @param pc
   *          the program counter for the instruction
   * @return the next instruction address or -1
   */
  protected int translateInstruction(Laziness lazy, int pc) {
    return PPC_InstructionDecoder.translateInstruction(this,
        (PPC_ProcessSpace) ps, (PPC_Laziness) lazy, pc);
  }

  // -oO Fill/spill registers between Registers and the PPC_ProcessSpace Oo-

  /**
   * Copy the value of a general purpose (int) register into its temporary
   * location from the PPC_ProcessSpace.
   * 
   * @param r
   *          the number of the register to fill.
   */
  private void fillGPRegister(int r) {
    if (DBT.VerifyAssertions)
      DBT._assert(r < 32);

    RegisterOperand result;
    if (intRegMap[r] == null) {
      result = gc.temps.makeTempInt();
      intRegMap[r] = result.register; // Set mapping
    } else {
      result = new RegisterOperand(intRegMap[r], TypeReference.Int);
    }
    appendInstruction(GetField.create(GETFIELD, result, gc
        .makeLocal(1, psTref), new AddressConstantOperand(gprFieldRefs[r]
        .peekResolvedField().getOffset()), new LocationOperand(
        gprFieldRefs[r]), new TrueGuardOperand()));
  }

  /**
   * Copy the value of a general purpose (int) register into the
   * PPC_ProcessSpace from its temporary location.
   * 
   * @param r
   *          the number of the register to spill.
   */
  private void spillGPRegister(int r) {
    if (DBT.VerifyAssertions)
      DBT._assert(r < 32);
    if (DBT.VerifyAssertions)
      DBT._assert(intRegMap[r] != null);

    RegisterOperand regOp = new RegisterOperand(intRegMap[r],
        TypeReference.Int);

    appendInstruction(PutField.create(PUTFIELD, regOp, gc
        .makeLocal(1, psTref), new AddressConstantOperand(gprFieldRefs[r]
        .peekResolvedField().getOffset()), new LocationOperand(
        gprFieldRefs[r]), new TrueGuardOperand()));
  }

  /**
   * Copy the value of a floating point register into its temporary location
   * from the PPC_ProcessSpace.
   * 
   * @param r
   *          the number of the register to fill.
   */
  private void fillFPRegister(int r) {
    if (DBT.VerifyAssertions)
      DBT._assert(r < 32);

    RegisterOperand result;
    if (fpRegMap[r] == null) {
      result = gc.temps.makeTempDouble();
      fpRegMap[r] = result.register; // Set mapping
    } else {
      result = new RegisterOperand(fpRegMap[r], TypeReference.Double);
    }

    appendInstruction(GetField.create(GETFIELD, result, gc
        .makeLocal(1, psTref), new AddressConstantOperand(fprFieldRefs[r]
        .peekResolvedField().getOffset()), new LocationOperand(
        fprFieldRefs[r]), new TrueGuardOperand()));
  }

  /**
   * Copy the value of a floating point register into the PPC_ProcessSpace from
   * its temporary location.
   * 
   * @param r
   *          the number of the register to spill.
   */
  private void spillFPRegister(int r) {
    if (DBT.VerifyAssertions)
      DBT._assert(r < 32 && fpRegMap[r] != null);
    
    RegisterOperand regOp = new RegisterOperand(fpRegMap[r],
        TypeReference.Double);

    appendInstruction(PutField.create(PUTFIELD, regOp, gc
        .makeLocal(1, psTref), new AddressConstantOperand(fprFieldRefs[r]
        .peekResolvedField().getOffset()), new LocationOperand(
        fprFieldRefs[r]), new TrueGuardOperand()));
  }

  /**
   * Copy the values of the condition register field into its temporary
   * locations from the PPC_ProcessSpace.
   * 
   * @param crf
   *          the condition register field to copy
   */
  private void fillCRRegister(int crf) {
    if (crFieldMap_Lt[crf] == null) {
      crFieldMap_Lt[crf] = gc.temps.getReg(TypeReference.Boolean);
      crFieldMap_Gt[crf] = gc.temps.getReg(TypeReference.Boolean);
      crFieldMap_Eq[crf] = gc.temps.getReg(TypeReference.Boolean);
      crFieldMap_SO[crf] = gc.temps.getReg(TypeReference.Boolean);
    }
    RegisterOperand lt = new RegisterOperand(crFieldMap_Lt[crf],
        TypeReference.Boolean);
    RegisterOperand gt = new RegisterOperand(crFieldMap_Gt[crf],
        TypeReference.Boolean);
    RegisterOperand eq = new RegisterOperand(crFieldMap_Eq[crf],
        TypeReference.Boolean);
    RegisterOperand so = new RegisterOperand(crFieldMap_SO[crf],
        TypeReference.Boolean);

    RegisterOperand arrayref = gc.temps
        .makeTemp(TypeReference.BooleanArray);

    appendInstruction(GetField.create(GETFIELD, arrayref, gc
        .makeLocal(1, psTref), new AddressConstantOperand(crf_ltFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        crf_ltFieldRef), new TrueGuardOperand()));
    appendInstruction(ALoad.create(UBYTE_ALOAD, lt, arrayref,
        new IntConstantOperand(crf), new LocationOperand(
            TypeReference.BooleanArray), new TrueGuardOperand()));
    appendInstruction(GetField.create(GETFIELD, arrayref, gc
        .makeLocal(1, psTref), new AddressConstantOperand(crf_gtFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        crf_gtFieldRef), new TrueGuardOperand()));
    appendInstruction(ALoad.create(UBYTE_ALOAD, gt, arrayref,
        new IntConstantOperand(crf), new LocationOperand(
            TypeReference.BooleanArray), new TrueGuardOperand()));
    appendInstruction(GetField.create(GETFIELD, arrayref, gc
        .makeLocal(1, psTref), new AddressConstantOperand(crf_eqFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        crf_eqFieldRef), new TrueGuardOperand()));
    appendInstruction(ALoad.create(UBYTE_ALOAD, eq, arrayref,
        new IntConstantOperand(crf), new LocationOperand(
            TypeReference.BooleanArray), new TrueGuardOperand()));
    appendInstruction(GetField.create(GETFIELD, arrayref, gc
        .makeLocal(1, psTref), new AddressConstantOperand(crf_soFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        crf_soFieldRef), new TrueGuardOperand()));
    appendInstruction(ALoad.create(UBYTE_ALOAD, so, arrayref,
        new IntConstantOperand(crf), new LocationOperand(
            TypeReference.BooleanArray), new TrueGuardOperand()));
  }

  /**
   * Copy the values of the condition register field into the PPC_ProcessSpace
   * from its temporary location.
   * 
   * @param crf
   *          the condition register field to copy
   */
  private void spillCRRegister(int crf) {
    RegisterOperand lt = new RegisterOperand(crFieldMap_Lt[crf],
        TypeReference.Boolean);
    RegisterOperand gt = new RegisterOperand(crFieldMap_Gt[crf],
        TypeReference.Boolean);
    RegisterOperand eq = new RegisterOperand(crFieldMap_Eq[crf],
        TypeReference.Boolean);
    RegisterOperand so = new RegisterOperand(crFieldMap_SO[crf],
        TypeReference.Boolean);

    RegisterOperand arrayref = gc.temps
        .makeTemp(TypeReference.BooleanArray);

    appendInstruction(GetField.create(GETFIELD, arrayref, gc
        .makeLocal(1, psTref), new AddressConstantOperand(crf_ltFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        crf_ltFieldRef), new TrueGuardOperand()));
    appendInstruction(AStore.create(BYTE_ASTORE, lt, arrayref,
        new IntConstantOperand(crf), new LocationOperand(
            TypeReference.BooleanArray), new TrueGuardOperand()));
    appendInstruction(GetField.create(GETFIELD, arrayref, gc
        .makeLocal(1, psTref), new AddressConstantOperand(crf_gtFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        crf_gtFieldRef), new TrueGuardOperand()));
    appendInstruction(AStore.create(BYTE_ASTORE, gt, arrayref,
        new IntConstantOperand(crf), new LocationOperand(
            TypeReference.BooleanArray), new TrueGuardOperand()));
    appendInstruction(GetField.create(GETFIELD, arrayref, gc
        .makeLocal(1, psTref), new AddressConstantOperand(crf_eqFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        crf_eqFieldRef), new TrueGuardOperand()));
    appendInstruction(AStore.create(BYTE_ASTORE, eq, arrayref,
        new IntConstantOperand(crf), new LocationOperand(
            TypeReference.BooleanArray), new TrueGuardOperand()));
    appendInstruction(GetField.create(GETFIELD, arrayref, gc
        .makeLocal(1, psTref), new AddressConstantOperand(crf_soFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        crf_soFieldRef), new TrueGuardOperand()));
    appendInstruction(AStore.create(BYTE_ASTORE, so, arrayref,
        new IntConstantOperand(crf), new LocationOperand(
            TypeReference.BooleanArray), new TrueGuardOperand()));
  }

  /**
   * Copy the value of the fpscr register into its temporary location from the
   * PPC_ProcessSpace.
   */
  private void fillFPSCRRegister() {
    RegisterOperand result;
    if (fpscrRegMap == null) {
      result = gc.temps.makeTempInt();
      fpscrRegMap = result.register; // Set mapping
    } else {
      result = new RegisterOperand(fpscrRegMap, TypeReference.Int);
    }
    appendInstruction(GetField.create(GETFIELD, result, gc
        .makeLocal(1, psTref), new AddressConstantOperand(fpscrFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        fpscrFieldRef), new TrueGuardOperand()));
  }

  /**
   * Copy the value of the condition register into the PPC_ProcessSpace from its
   * temporary location.
   */
  private void spillFPSCRRegister() {
    RegisterOperand regOp = new RegisterOperand(fpscrRegMap,
        TypeReference.Int);

    appendInstruction(PutField.create(PUTFIELD, regOp, gc
        .makeLocal(1, psTref), new AddressConstantOperand(fpscrFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(
        fpscrFieldRef), new TrueGuardOperand()));
  }

  /**
   * Copy the value of the count register into its temporary location from the
   * PPC_ProcessSpace.
   */
  private void fillCTRRegister() {
    RegisterOperand result;
    if (ctrRegMap == null) {
      result = gc.temps.makeTempInt();
      ctrRegMap = result.register; // Set mapping
    } else {
      result = new RegisterOperand(ctrRegMap, TypeReference.Int);
    }
    appendInstruction(GetField.create(GETFIELD, result, gc
        .makeLocal(1, psTref), new AddressConstantOperand(ctrFieldRef
        .peekResolvedField().getOffset()),
        new LocationOperand(ctrFieldRef), new TrueGuardOperand()));
  }

  /**
   * Copy the value of the count register into the PPC_ProcessSpace from its
   * temporary location.
   */
  private void spillCTRRegister() {
    RegisterOperand regOp = new RegisterOperand(ctrRegMap,
        TypeReference.Int);

    appendInstruction(PutField.create(PUTFIELD, regOp, gc
        .makeLocal(1, psTref), new AddressConstantOperand(ctrFieldRef
        .peekResolvedField().getOffset()),
        new LocationOperand(ctrFieldRef), new TrueGuardOperand()));
  }

  /**
   * Copy the value of the XER register into its temporary location from the
   * PPC_ProcessSpace.
   */
  private void fillXERRegister() {
    RegisterOperand xerRegMap_SO_Op;
    RegisterOperand xerRegMap_OV_Op;
    RegisterOperand xerRegMap_ByteCountOp;
    RegisterOperand xerRegMap_CA_Op;
    if (xerRegMap_SO == null) {
      xerRegMap_SO_Op = gc.temps.makeTempBoolean();
      xerRegMap_OV_Op = gc.temps.makeTempBoolean();
      xerRegMap_CA_Op = gc.temps.makeTempBoolean();
      xerRegMap_ByteCountOp = gc.temps.makeTemp(TypeReference.Byte);

      xerRegMap_SO = xerRegMap_SO_Op.register;
      xerRegMap_OV = xerRegMap_OV_Op.register;
      xerRegMap_CA = xerRegMap_CA_Op.register;
      xerRegMap_ByteCount = xerRegMap_ByteCountOp.register;
    } else {
      xerRegMap_SO_Op = new RegisterOperand(xerRegMap_SO,
          TypeReference.Boolean);
      xerRegMap_OV_Op = new RegisterOperand(xerRegMap_OV,
          TypeReference.Boolean);
      xerRegMap_CA_Op = new RegisterOperand(xerRegMap_CA,
          TypeReference.Boolean);
      xerRegMap_ByteCountOp = new RegisterOperand(xerRegMap_ByteCount,
          TypeReference.Byte);
    }

    appendInstruction(GetField.create(GETFIELD, xerRegMap_SO_Op,
        gc.makeLocal(1, psTref), new AddressConstantOperand(xer_soFieldRef
            .peekResolvedField().getOffset()), new LocationOperand(
            xer_soFieldRef), new TrueGuardOperand()));
    appendInstruction(GetField.create(GETFIELD, xerRegMap_OV_Op,
        gc.makeLocal(1, psTref), new AddressConstantOperand(xer_ovFieldRef
            .peekResolvedField().getOffset()), new LocationOperand(
            xer_ovFieldRef), new TrueGuardOperand()));
    appendInstruction(GetField.create(GETFIELD, xerRegMap_CA_Op,
        gc.makeLocal(1, psTref), new AddressConstantOperand(xer_caFieldRef
            .peekResolvedField().getOffset()), new LocationOperand(
            xer_caFieldRef), new TrueGuardOperand()));
    appendInstruction(GetField.create(GETFIELD,
        xerRegMap_ByteCountOp, gc.makeLocal(1, psTref),
        new AddressConstantOperand(xer_byteCountFieldRef
            .peekResolvedField().getOffset()), new LocationOperand(
            xer_byteCountFieldRef), new TrueGuardOperand()));
  }

  /**
   * Copy the value of the XER register into the PPC_ProcessSpace from its
   * temporary location.
   */
  private void spillXERRegister() {
    RegisterOperand xerRegMap_SO_Op = new RegisterOperand(xerRegMap_SO,
        TypeReference.Boolean);
    appendInstruction(PutField.create(PUTFIELD, xerRegMap_SO_Op,
        gc.makeLocal(1, psTref), new AddressConstantOperand(xer_soFieldRef
            .peekResolvedField().getOffset()), new LocationOperand(
            xer_soFieldRef), new TrueGuardOperand()));

    RegisterOperand xerRegMap_OV_Op = new RegisterOperand(xerRegMap_OV,
        TypeReference.Boolean);
    appendInstruction(PutField.create(PUTFIELD, xerRegMap_OV_Op,
        gc.makeLocal(1, psTref), new AddressConstantOperand(xer_ovFieldRef
            .peekResolvedField().getOffset()), new LocationOperand(
            xer_ovFieldRef), new TrueGuardOperand()));

    RegisterOperand xerRegMap_CA_Op = new RegisterOperand(xerRegMap_CA,
        TypeReference.Boolean);
    appendInstruction(PutField.create(PUTFIELD, xerRegMap_CA_Op,
        gc.makeLocal(1, psTref), new AddressConstantOperand(xer_caFieldRef
            .peekResolvedField().getOffset()), new LocationOperand(
            xer_caFieldRef), new TrueGuardOperand()));

    RegisterOperand xerRegMap_ByteCountOp = new RegisterOperand(
        xerRegMap_ByteCount, TypeReference.Byte);
    appendInstruction(PutField.create(PUTFIELD,
        xerRegMap_ByteCountOp, gc.makeLocal(1, psTref),
        new AddressConstantOperand(xer_byteCountFieldRef
            .peekResolvedField().getOffset()), new LocationOperand(
            xer_byteCountFieldRef), new TrueGuardOperand()));
  }

  /**
   * Copy the value of the LR register into its temporary location from the
   * PPC_ProcessSpace.
   */
  private void fillLRRegister() {
    RegisterOperand result;
    if (lrRegMap == null) {
      result = gc.temps.makeTempInt();
      lrRegMap = result.register; // Set mapping
    } else {
      result = new RegisterOperand(lrRegMap, TypeReference.Int);
    }
    appendInstruction(GetField.create(GETFIELD, result, gc
        .makeLocal(1, psTref), new AddressConstantOperand(lrFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(lrFieldRef),
        new TrueGuardOperand()));
  }

  /**
   * Copy the value of the LR register into the PPC_ProcessSpace from its
   * temporary location.
   */
  private void spillLRRegister() {
    RegisterOperand regOp = new RegisterOperand(lrRegMap,
        TypeReference.Int);
    appendInstruction(PutField.create(PUTFIELD, regOp, gc
        .makeLocal(1, psTref), new AddressConstantOperand(lrFieldRef
        .peekResolvedField().getOffset()), new LocationOperand(lrFieldRef),
        new TrueGuardOperand()));
  }

  /**
   * Fill all the registers from the PPC_ProcessSpace.
   */
  protected void fillAllRegisters() {
    for (int r = 0; r < 32; r++) {
      fillGPRegister(r);
    }
    for (int r = 0; r < 32; r++) {
      fillFPRegister(r);
    }
    for (int crf = 0; crf < 8; crf++) {
      fillCRRegister(crf);
    }

    fillCTRRegister();

    fillXERRegister();

    fillLRRegister();

    fillFPSCRRegister();
  }

  /**
   * Spill all the registers
   */
  protected void spillAllRegisters() {
    for (int r = 0; r < 32; r++) {
      spillGPRegister(r);
    }
    for (int r = 0; r < 32; r++) {
      spillFPRegister(r);
    }
    for (int crf = 0; crf < 8; crf++) {
      spillCRRegister(crf);
    }

    spillCTRRegister();

    spillXERRegister();

    spillLRRegister();

    spillFPSCRRegister();

    // spillPC(pc);
  }

  /**
   * Spill a given PC value into the process space
   */
  private void spillPC(int pc) {
    appendInstruction(PutField.create(PUTFIELD,
        new IntConstantOperand(pc), gc.makeLocal(1, psTref),
        new AddressConstantOperand(pcFieldRef.peekResolvedField()
            .getOffset()), new LocationOperand(pcFieldRef),
        new TrueGuardOperand()));
  }

  // -oO Find a register Oo-

  /**
   * Return the HIR RegisterOperand (temporary register) corresponding to a
   * general purpose PPC register.
   * 
   * @param r
   *          the number of the PPC GP register
   */
  public RegisterOperand getGPRegister(int r) {
    if (DBT.VerifyAssertions)
      DBT._assert(r < 32);
    
    intRegInUseMap[r] = true;
    return new RegisterOperand(intRegMap[r], TypeReference.Int);
  }

  /**
   * Return the HIR RegisterOperand (temporary register) corresponding to a
   * floating point PPC register
   * 
   * @param r
   *          the number of the PPC FP register
   */
  public RegisterOperand getFPRegister(int r) {
    if (DBT.VerifyAssertions)
      DBT._assert(r < 32);
    fpRegInUseMap[r] = true;
    return new RegisterOperand(fpRegMap[r], TypeReference.Double);
  }

  /**
   * Return a boolean register operand encoding the lt bit of the given
   * condition register field
   * 
   * @param crf
   *          the condition register field to be read
   * @return a register holding the crf bit
   */
  public RegisterOperand getCR_Lt_Register(int crf) {
    crFieldInUseMap[crf] = true;
    return new RegisterOperand(crFieldMap_Lt[crf], TypeReference.Boolean);
  }

  /**
   * Return a boolean register operand encoding the gt bit of the given
   * condition register field
   * 
   * @param crf
   *          the condition register field to be read
   * @return a register holding the crf bit
   */
  public RegisterOperand getCR_Gt_Register(int crf) {
    crFieldInUseMap[crf] = true;
    return new RegisterOperand(crFieldMap_Gt[crf], TypeReference.Boolean);
  }

  /**
   * Return a boolean register operand encoding the eq bit of the given
   * condition register field
   * 
   * @param crf
   *          the condition register field to be read
   * @return a register holding the crf bit
   */
  public RegisterOperand getCR_Eq_Register(int crf) {
    crFieldInUseMap[crf] = true;
    return new RegisterOperand(crFieldMap_Eq[crf], TypeReference.Boolean);
  }

  /**
   * Return a boolean register operand encoding the so bit of the given
   * condition register field
   * 
   * @param crf
   *          the condition register field to be read
   * @return a register holding the crf bit
   */
  public RegisterOperand getCR_SO_Register(int crf) {
    crFieldInUseMap[crf] = true;
    return new RegisterOperand(crFieldMap_SO[crf], TypeReference.Boolean);
  }

  public RegisterOperand getCRB_Register(int crb) {
    int crf = crb >> 2;
    switch (crb & 0x3) {
    case 0:
      return getCR_Lt_Register(crf);
    case 1:
      return getCR_Gt_Register(crf);
    case 2:
      return getCR_Eq_Register(crf);
    case 3:
      return getCR_SO_Register(crf);
    default:
      DBT_OptimizingCompilerException.UNREACHABLE();
    }
    return null; // stop compiler warnings
  }

  /**
   * Combine condition register fields into a single 32bit condition register
   * 
   * @return a register holding the CR register
   */
  public RegisterOperand getCRRegister() {
    throw new Error("TODO");
  }

  /**
   * Return the HIR RegisterOperand (temporary register) corresponding to
   * the PPC fpscr register.
   */
  public RegisterOperand getFPSCRRegister() {
    fpscrRegInUse = true;
    return new RegisterOperand(fpscrRegMap, TypeReference.Int);
  }

  /**
   * Return the HIR RegisterOperand (temporary register) corresponding to
   * the PPC ctr register
   */
  public RegisterOperand getCTRRegister() {
    ctrRegInUse = true;
    return new RegisterOperand(ctrRegMap, TypeReference.Int);
  }

  /**
   * Return the HIR RegisterOperand (temporary register) corresponding to
   * the PPC xer register byte count bits
   */
  public RegisterOperand getXER_ByteCountRegister() {
    xerRegInUse = true;
    return new RegisterOperand(xerRegMap_ByteCount, TypeReference.Byte);
  }

  /**
   * Return the HIR RegisterOperand (temporary register) corresponding to
   * the PPC xer register SO bit
   */
  public RegisterOperand getXER_SO_Register() {
    xerRegInUse = true;
    return new RegisterOperand(xerRegMap_SO, TypeReference.Boolean);
  }

  /**
   * Return the HIR RegisterOperand (temporary register) corresponding to
   * the PPC xer register OV bit
   */
  public RegisterOperand getXER_OV_Register() {
    xerRegInUse = true;
    return new RegisterOperand(xerRegMap_OV, TypeReference.Boolean);
  }

  /**
   * Return the HIR RegisterOperand (temporary register) corresponding to
   * the PPC xer register CA bit
   */
  public RegisterOperand getXER_CA_Register() {
    xerRegInUse = true;
    return new RegisterOperand(xerRegMap_CA, TypeReference.Boolean);
  }

  /**
   * Return the HIR RegisterOperand (temporary register) corresponding to
   * the PPC lr register.
   */
  public RegisterOperand getLRRegister() {
    lrRegInUse = true;
    return new RegisterOperand(lrRegMap, TypeReference.Int);
  }

  // -oO Lazy state Oo-

  /**
   * Plant instructions modifying a lazy state into one with no laziness for the
   * specified condition register field
   * 
   * @param laziness
   *          the laziness to modify
   * @param crf
   *          the condition register field
   */
  public void resolveLazinessCrField(PPC_Laziness laziness, int crf) {
    laziness.resolveCrField(crf);
  }

  /**
   * Create the initial object for capturing lazy information
   */
  protected Laziness createInitialLaziness() {
    return new PPC_Laziness();
  }

  /**
   * Plant instructions modifying a lazy state into one with no laziness
   * 
   * @param laziness
   *          the laziness to modify
   */
  public void resolveLaziness(Laziness laziness) {
    ((PPC_Laziness) laziness).resolve();
  }

  /**
   * Plant instructions modifying a lazy state into one with no laziness for the
   * specified bit
   * 
   * @param laziness
   *          the laziness to modify
   * @param bit
   *          the bit required to be resolved
   */
  public void resolveLazinessCrBit(Laziness laziness, int bit) {
    int crf = bit >> 2;
    resolveLazinessCrField((PPC_Laziness) laziness, crf);
  }

  // -oO Trace control Oo-

  /**
   * Register a branch and link instruction
   * 
   * @param pc
   *          the address of the branch instruction (implicity the link value
   *          will be assumed to be pc+4)
   * @param dest
   *          the destination of the branch instruction
   */
  public void registerBranchAndLink(int pc, int dest) {
    registerBranchAndLink(pc, pc + 4, dest);
  }

  // -oO Optimisations on the generated HIR Oo-

  /**
   * Return an array of unused registers
   */
  protected Register[] getUnusedRegisters() {
    ArrayList<Register> unusedRegisterList = new ArrayList<Register>();
    for (int i = 0; i < 32; i++) {
      if (intRegInUseMap[i] == false) {
        unusedRegisterList.add(intRegMap[i]);
      }
      if (fpRegInUseMap[i] == false) {
        unusedRegisterList.add(fpRegMap[i]);
      }
    }
    if (fpscrRegInUse == false) {
      unusedRegisterList.add(fpscrRegMap);
    }
    if (ctrRegInUse == false) {
      unusedRegisterList.add(ctrRegMap);
    }
    for (int crf = 0; crf < 8; crf++) {
      if (crFieldInUseMap[crf] == false) {
        unusedRegisterList.add(crFieldMap_Lt[crf]);
        unusedRegisterList.add(crFieldMap_Gt[crf]);
        unusedRegisterList.add(crFieldMap_Eq[crf]);
        unusedRegisterList.add(crFieldMap_SO[crf]);
      }
    }
    if (lrRegInUse == false) {
      unusedRegisterList.add(lrRegMap);
    }
    if (xerRegInUse == false) {
      unusedRegisterList.add(xerRegMap_SO);
      unusedRegisterList.add(xerRegMap_OV);
      unusedRegisterList.add(xerRegMap_ByteCount);
      unusedRegisterList.add(xerRegMap_CA);
    }
    return (Register[]) unusedRegisterList
        .toArray(new Register[unusedRegisterList.size()]);
  }

  // -oO Debug Oo-

  /**
   * Report some debug output
   */
  protected void report(String str) {
    System.out.print("PPC2IR: ");
    System.out.println(str);
  }

  /**
   * Dump current state of translation
   * 
   * @todo implement this
   */
  private void dump() {
  }
} // End of class PPC2IR
