/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.ppc.decoder;

// DBT classes
import org.binarytranslator.arch.ppc.os.process.PPC_ProcessSpace;
import org.binarytranslator.vmInterface.TranslationHelper;
import org.binarytranslator.vmInterface.DBT_OptimizingCompilerException;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.decoder.DecoderUtils;
import org.binarytranslator.generic.decoder.Laziness;
// General VM class
import org.jikesrvm.VM;
// Classes to get at class types
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_FieldReference;
import org.jikesrvm.classloader.VM_BootstrapClassLoader;
import org.jikesrvm.classloader.VM_Atom;
// OPT interface
import org.jikesrvm.opt.OPT_Constants;
import org.jikesrvm.opt.ir.OPT_GenerationContext;
import org.jikesrvm.opt.ir.OPT_HIRGenerator;
import org.jikesrvm.opt.ir.OPT_IR;
// Instructions
import org.jikesrvm.opt.ir.OPT_Instruction;
import org.jikesrvm.opt.ir.OPT_Operator;
import org.jikesrvm.opt.ir.OPT_Operators;
import org.jikesrvm.opt.ir.ALoad;
import org.jikesrvm.opt.ir.AStore;
import org.jikesrvm.opt.ir.Athrow;
import org.jikesrvm.opt.ir.Binary;
import org.jikesrvm.opt.ir.BBend;
import org.jikesrvm.opt.ir.BooleanCmp;
import org.jikesrvm.opt.ir.Call;
import org.jikesrvm.opt.ir.CondMove;
import org.jikesrvm.opt.ir.GetField;
import org.jikesrvm.opt.ir.Goto;
import org.jikesrvm.opt.ir.IfCmp;
import org.jikesrvm.opt.ir.Move;
import org.jikesrvm.opt.ir.New;
import org.jikesrvm.opt.ir.LookupSwitch;
import org.jikesrvm.opt.ir.PutField;
import org.jikesrvm.opt.ir.Unary;
// Operands
import org.jikesrvm.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.opt.ir.OPT_BasicBlock;
import org.jikesrvm.opt.ir.OPT_BranchOperand;
import org.jikesrvm.opt.ir.OPT_BranchProfileOperand;
import org.jikesrvm.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.opt.ir.OPT_LocationOperand;
import org.jikesrvm.opt.ir.OPT_MethodOperand;
import org.jikesrvm.opt.ir.OPT_Operand;
import org.jikesrvm.opt.ir.OPT_Register;
import org.jikesrvm.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.opt.ir.OPT_TrueGuardOperand;
import org.jikesrvm.opt.ir.OPT_TypeOperand;
// Java utilities
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;
import java.util.HashSet;

/** 
 * Translation from PPC machine code to HIR.
 *
 * @author Richard Matley, Ian Rogers
 */
public final class PPC2IR extends DecoderUtils implements OPT_HIRGenerator, OPT_Operators, OPT_Constants {

    /** 
     * Construct the PPC2IR object for the generation context; then
     * we'll be ready to start generating the HIR.
     */
    public PPC2IR(OPT_GenerationContext context) {
        super(context);
        // Create register maps PPC -> OPT_Register
        intRegMap = new OPT_Register[32];
        intRegInUseMap = new boolean[32];
        fpRegMap = new OPT_Register[32];
        fpRegInUseMap = new boolean[32];
        crFieldMap_Lt = new OPT_Register[8];
        crFieldMap_Gt = new OPT_Register[8];
        crFieldMap_Eq = new OPT_Register[8];
        crFieldMap_SO = new OPT_Register[8];
        crFieldInUseMap = new boolean[8];

        // Debug
        if(DBT_Options.debugCFG) {
            report("CFG at end of constructor:\n" + gc.cfg); 
        }
    }

    /**
     * Translate the instruction at the given pc
     * @param lazy the status of the lazy evaluation
     * @param pc the program counter for the instruction
     * @return the next instruction address or -1
     */
    protected int translateInstruction(Laziness lazy, int pc) {
        return PPC_InstructionDecoder.translateInstruction(this, (PPC_ProcessSpace)ps, (PPC_Laziness)lazy, pc);
    }

    // -oO Creations for being a PPC translator Oo-

    // -oO PPC register to HIR register mappings Oo-

    /**
     * The mapping of PPC general purpose registers to HIR
     * registers. All GP registers are loaded into these by the preFill
     * block. This avoids potential inconsistencies caused by using lazy
     * allocation and backward branches.
     */
    private OPT_Register intRegMap[];
    /**
     * Which PPC general purpose registers are in use
     */
    private boolean intRegInUseMap[];

    /**
     * The mapping of PPC floating point registers to HIR registers.
     */
    private OPT_Register fpRegMap[];
    /**
     * Which PPC floating point registers are in use
     */
    private boolean fpRegInUseMap[];

    /**
     * The HIR register holding the PPC FPSCR register.
     */
    private OPT_Register fpscrRegMap;
    /**
     * Is the PPC FPSCR register in use
     */
    private boolean fpscrRegInUse;

    /**
     * The HIR register holding the PPC CTR register.
     */
    private OPT_Register ctrRegMap;
    /**
     * Is the PPC CTR register in use
     */
    private boolean ctrRegInUse;

    /**
     * The HIR register holding the PPC LR register.
     */
    private OPT_Register lrRegMap;
    /**
     * Is the PPC LR register in use
     */
    private boolean lrRegInUse;

    /**
     * The HIR register holding the PPC XER register when it is combined
     * in a fillAll or spillAll block
     */
    private OPT_Register xerRegMap;

    /**
     * The HIR register holding the PPC XER register's byte count (bits
     * 25 to 31)
     */
    private OPT_Register xerRegMap_ByteCount;

    /**
     * The HIR boolean register holding thr PPC XER CA (carry) bit.
     */
    private OPT_Register xerRegMap_CA;

    /**
     * The HIR register holding thr PPC XER OV (overflow) bit. If this
     * register is non-zero then the OV bit should be set.
     */
    private OPT_Register xerRegMap_OV;
    /**
     * The HIR register holding thr PPC XER SO (summary overflow)
     * bit. If this register is non-zero then the SO bit should be set.
     */
    private OPT_Register xerRegMap_SO;
    /**
     * Is the PPC XER register in use
     */
    private boolean xerRegInUse;
  
    /**
     * These 8 registers hold a zero or non-zero value depending on the
     * value of the corresponding condition register field's SO
     * bit.
     */
    private OPT_Register crFieldMap_Lt[];

    /**
     * These 8 registers hold a zero or non-zero value depending on the
     * value of the corresponding condition register field's SO
     * bit.
     */
    private OPT_Register crFieldMap_Gt[];

    /**
     * These 8 registers hold a zero or non-zero value depending on the
     * value of the corresponding condition register field's SO
     * bit.
     */
    private OPT_Register crFieldMap_Eq[];

    /**
     * These 8 registers hold a zero or non-zero value depending on the
     * value of the corresponding condition register field's SO
     * bit.
     */
    private OPT_Register crFieldMap_SO[];

    /**
     * What condition register fields are in use?
     */
    private boolean crFieldInUseMap[];

    // -oO Fill/spill registers between OPT_Registers and the PPC_ProcessSpace Oo-

    /**
     * Copy the value of a general purpose (int) register into its
     * temporary location from the PPC_ProcessSpace.
     * @param r the number of the register to fill.
     */
    private void fillGPRegister(int r) {
        if (VM.VerifyAssertions) VM._assert(r < 32);

        OPT_RegisterOperand result;
        if (intRegMap[r] == null) {
            result = gc.temps.makeTempInt();
            intRegMap[r]=result.register; // Set mapping
        }
        else {
            result = new OPT_RegisterOperand(intRegMap[r], VM_TypeReference.Int);
        }

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("r"+r),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, result,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }


    /**
     * Copy the value of a general purpose (int) register into the
     * PPC_ProcessSpace from its temporary location.
     * @param r the number of the register to spill.
     */
    private void spillGPRegister(int r) {
        if (VM.VerifyAssertions) VM._assert(r < 32);
        if (VM.VerifyAssertions) VM._assert(intRegMap[r] != null);

        OPT_RegisterOperand regOp = new OPT_RegisterOperand(intRegMap[r], VM_TypeReference.Int);

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("r"+r),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(PutField.create(PUTFIELD, regOp,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }


    /**
     * Copy the value of a floating point register into its temporary
     * location from the PPC_ProcessSpace.
     * @param r the number of the register to fill.
     */
    private void fillFPRegister(int r) {
        if (VM.VerifyAssertions) VM._assert(r < 32);

        OPT_RegisterOperand result;
        if (fpRegMap[r] == null) {
            result = gc.temps.makeTempDouble();
            fpRegMap[r]=result.register; // Set mapping
        }
        else {
            result = new OPT_RegisterOperand(fpRegMap[r], VM_TypeReference.Double);
        }

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("f"+r),
                                                                VM_Atom.findOrCreateAsciiAtom("D")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, result,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }


    /**
     * Copy the value of a floating point register into the
     * PPC_ProcessSpace from its temporary location.
     * @param r the number of the register to spill.
     */
    private void spillFPRegister(int r) {
        if (VM.VerifyAssertions) VM._assert(r < 32);
        if (VM.VerifyAssertions) VM._assert(fpRegMap[r] != null);

        OPT_RegisterOperand regOp = new OPT_RegisterOperand(fpRegMap[r], VM_TypeReference.Double);

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("f"+r),
                                                                VM_Atom.findOrCreateAsciiAtom("D")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(PutField.create(PUTFIELD, regOp,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }

    /**
     * Copy the values of the condition register field into its
     * temporary locations from the PPC_ProcessSpace.
     * @param crf the condition register field to copy
     */
    private void fillCRRegister(int crf)
    {
	if (crFieldMap_Lt[crf] == null) {
	    crFieldMap_Lt[crf] = gc.temps.getReg(VM_TypeReference.Boolean);
	    crFieldMap_Gt[crf] = gc.temps.getReg(VM_TypeReference.Boolean);
	    crFieldMap_Eq[crf] = gc.temps.getReg(VM_TypeReference.Boolean);
	    crFieldMap_SO[crf] = gc.temps.getReg(VM_TypeReference.Boolean);
        }
	OPT_RegisterOperand lt = new OPT_RegisterOperand(crFieldMap_Lt[crf], VM_TypeReference.Boolean);
	OPT_RegisterOperand gt = new OPT_RegisterOperand(crFieldMap_Gt[crf], VM_TypeReference.Boolean);
	OPT_RegisterOperand lt = new OPT_RegisterOperand(crFieldMap_Eq[crf], VM_TypeReference.Boolean);
	OPT_RegisterOperand so = new OPT_RegisterOperand(crFieldMap_SO[crf], VM_TypeReference.Boolean);

	OPT_RegisterOperand arrayref = gc.temps.makeTemp(VM_TypeReference.BooleanArray);

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("crf_lt"),
                                                                VM_Atom.findOrCreateAsciiAtom("[Z")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, arrayref,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
	appendInstructionToCurrentBlock(ALoad.create(UBYTE_ALOAD, lt,
						     arrayref, new OPT_IntConstantOperand(crf),
						     new OPT_LocationOperand(VM_TypeReference.BooleanArray),
						     new OPT_TrueGuardOperand()));
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("crf_gt"),
                                                                VM_Atom.findOrCreateAsciiAtom("[Z")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, arrayref,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
	appendInstructionToCurrentBlock(ALoad.create(UBYTE_ALOAD, gt,
						     arrayref, new OPT_IntConstantOperand(crf),
						     new OPT_LocationOperand(VM_TypeReference.BooleanArray),
						     new OPT_TrueGuardOperand()));
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("crf_eq"),
                                                                VM_Atom.findOrCreateAsciiAtom("[Z")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, arrayref,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
	appendInstructionToCurrentBlock(ALoad.create(UBYTE_ALOAD, eq,
						     arrayref, new OPT_IntConstantOperand(crf),
						     new OPT_LocationOperand(VM_TypeReference.BooleanArray),
						     new OPT_TrueGuardOperand()));
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("crf_so"),
                                                                VM_Atom.findOrCreateAsciiAtom("[Z")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, arrayref,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
	appendInstructionToCurrentBlock(ALoad.create(UBYTE_ALOAD, so,
						     arrayref, new OPT_IntConstantOperand(crf),
						     new OPT_LocationOperand(VM_TypeReference.BooleanArray),
						     new OPT_TrueGuardOperand()));
    }

    /**
     * Copy the values of the condition register field into the
     * PPC_ProcessSpace from its temporary location.
     * @param crf the condition register field to copy
     */
    private void spillCRRegister(int crf)
    {
	OPT_RegisterOperand lt = new OPT_RegisterOperand(crFieldMap_Lt[crf], VM_TypeReference.Boolean);
	OPT_RegisterOperand gt = new OPT_RegisterOperand(crFieldMap_Gt[crf], VM_TypeReference.Boolean);
	OPT_RegisterOperand lt = new OPT_RegisterOperand(crFieldMap_Eq[crf], VM_TypeReference.Boolean);
	OPT_RegisterOperand so = new OPT_RegisterOperand(crFieldMap_SO[crf], VM_TypeReference.Boolean);

	OPT_RegisterOperand arrayref = gc.temps.makeTemp(VM_TypeReference.BooleanArray);

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("crf_lt"),
                                                                VM_Atom.findOrCreateAsciiAtom("[Z")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, arrayref,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
	appendInstructionToCurrentBlock(AStore.create(BYTE_ASTORE, lt,
						      arrayref, new OPT_IntConstantOperand(crf),
						      new OPT_LocationOperand(VM_TypeReference.BooleanArray),
						      new OPT_TrueGuardOperand()));
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("crf_gt"),
                                                                VM_Atom.findOrCreateAsciiAtom("[Z")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, arrayref,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
	appendInstructionToCurrentBlock(AStore.create(BYTE_ASTORE, gt,
						      arrayref, new OPT_IntConstantOperand(crf),
						      new OPT_LocationOperand(VM_TypeReference.BooleanArray),
						      new OPT_TrueGuardOperand()));
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("crf_eq"),
                                                                VM_Atom.findOrCreateAsciiAtom("[Z")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, arrayref,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
	appendInstructionToCurrentBlock(AStore.create(BYTE_ASTORE, eq,
						      arrayref, new OPT_IntConstantOperand(crf),
						      new OPT_LocationOperand(VM_TypeReference.BooleanArray),
						      new OPT_TrueGuardOperand()));
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("crf_so"),
                                                                VM_Atom.findOrCreateAsciiAtom("[Z")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, arrayref,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
	appendInstructionToCurrentBlock(AStore.create(BYTE_ASTORE, so,
						      arrayref, new OPT_IntConstantOperand(crf),
						      new OPT_LocationOperand(VM_TypeReference.BooleanArray),
						      new OPT_TrueGuardOperand()));
    }

    /**
     * Copy the value of the fpscr register into its temporary location
     * from the PPC_ProcessSpace.
     */
    private void fillFPSCRRegister()
    {
        OPT_RegisterOperand result;
        if (fpscrRegMap == null) {
            result = gc.temps.makeTempInt();
            fpscrRegMap=result.register; // Set mapping
        }
        else {
            result = new OPT_RegisterOperand(fpscrRegMap, VM_TypeReference.Int);
        }

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("fpscr"),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, result,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );

    }

    /**
     * Copy the value of the condition register into the PPC_ProcessSpace from its temporary location.
     */
    private void spillFPSCRRegister()
    {
        OPT_RegisterOperand regOp = new OPT_RegisterOperand(fpscrRegMap, VM_TypeReference.Int);

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("fpscr"),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(PutField.create(PUTFIELD, regOp,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }

    /**
     * Copy the value of the count register into its temporary location
     * from the PPC_ProcessSpace.
     */
    private void fillCTRRegister()
    {
        OPT_RegisterOperand result;
        if (ctrRegMap == null) {
            result = gc.temps.makeTempInt();
            ctrRegMap=result.register; // Set mapping
        }
        else {
            result = new OPT_RegisterOperand(ctrRegMap, VM_TypeReference.Int);
        }
        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("ctr"),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, result,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }

    /**
     * Copy the value of the count register into the PPC_ProcessSpace
     * from its temporary location.
     */
    private void spillCTRRegister()
    {
        OPT_RegisterOperand regOp = new OPT_RegisterOperand(ctrRegMap, VM_TypeReference.Int);

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("ctr"),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(PutField.create(PUTFIELD, regOp,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }


    /**
     * Copy the value of the XER register into its temporary location
     * from the PPC_ProcessSpace.
     */
    private void fillXERRegister()
    {
        OPT_RegisterOperand xerRegMap_Op;
        OPT_RegisterOperand xerRegMap_SO_Op;
        OPT_RegisterOperand xerRegMap_OV_Op;
        OPT_RegisterOperand xerRegMap_ByteCountOp;
        OPT_RegisterOperand xerRegMap_CA_Op;
        if (xerRegMap_SO == null) {
            xerRegMap_Op = gc.temps.makeTempInt();
            xerRegMap_SO_Op = gc.temps.makeTempInt();
            xerRegMap_OV_Op = gc.temps.makeTempInt();
            xerRegMap_ByteCountOp = gc.temps.makeTempInt();
            xerRegMap_CA_Op = gc.temps.makeTempBoolean();

            xerRegMap = xerRegMap_Op.register;
            xerRegMap_SO = xerRegMap_SO_Op.register;
            xerRegMap_OV = xerRegMap_OV_Op.register;
            xerRegMap_ByteCount = xerRegMap_ByteCountOp.register;
            xerRegMap_CA = xerRegMap_CA_Op.register;
        }
        else {
            xerRegMap_Op = new OPT_RegisterOperand(xerRegMap, VM_TypeReference.Int);
            xerRegMap_SO_Op = new OPT_RegisterOperand(xerRegMap_SO, VM_TypeReference.Int);
            xerRegMap_OV_Op = new OPT_RegisterOperand(xerRegMap_OV, VM_TypeReference.Int);
            xerRegMap_ByteCountOp = new OPT_RegisterOperand(xerRegMap_ByteCount, VM_TypeReference.Int);
            xerRegMap_CA_Op = new OPT_RegisterOperand(xerRegMap_CA, VM_TypeReference.Boolean);
        }

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("xer"),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, xerRegMap_Op,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
        // Copy SO bit into XER[SO] register
        appendInstructionToCurrentBlock(Binary.create(INT_AND, xerRegMap_SO_Op,
                                                      xerRegMap_Op.copyRO(),
                                                      new OPT_IntConstantOperand(1 << 31)));

        // Copy OV bit into XER[OV] register
        appendInstructionToCurrentBlock(Binary.create(INT_AND, xerRegMap_OV_Op,
                                                      xerRegMap_Op.copyRO(),
                                                      new OPT_IntConstantOperand(1 << 30)));

        // Copy CA bit into XER[CA] register    
        OPT_RegisterOperand tempInt = getTempInt(0);
        appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt,
                                                      xerRegMap_Op.copyRO(),
                                                      new OPT_IntConstantOperand(1 << 29)));
        appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, xerRegMap_CA_Op,
                                                          tempInt.copyRO(),
                                                          new OPT_IntConstantOperand(0),
                                                          OPT_ConditionOperand.NOT_EQUAL(),
                                                          OPT_BranchProfileOperand.unlikely()));

        // Copy byte count bits out
        appendInstructionToCurrentBlock(Binary.create(INT_AND, xerRegMap_ByteCountOp,
                                                      xerRegMap_Op.copyRO(),
                                                      new OPT_IntConstantOperand(0x3f)));
    }

    /**
     * Copy the value of the XER register into the PPC_ProcessSpace from
     * its temporary location.
     */
    private void spillXERRegister()
    {
        // Set up xer register with byte count
        OPT_RegisterOperand  xer = new OPT_RegisterOperand(xerRegMap, VM_TypeReference.Int);
        appendInstructionToCurrentBlock(Move.create(INT_MOVE, xer,
                                                    new OPT_RegisterOperand(xerRegMap_ByteCount, VM_TypeReference.Int))
                                        );
        // SO bit
        OPT_RegisterOperand tempInt = getTempInt(0);
        appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, 
                                                        tempInt,
                                                        new OPT_RegisterOperand(xerRegMap_SO, VM_TypeReference.Int),
                                                        new OPT_IntConstantOperand(0),
                                                        OPT_ConditionOperand.EQUAL(),
                                                        new OPT_IntConstantOperand(0),
                                                        new OPT_IntConstantOperand(1 << 31)));
        appendInstructionToCurrentBlock(Binary.create(INT_OR, xer.copyRO(),
                                                      xer.copy(),
                                                      tempInt.copyRO())
                                        );
        // OV bit
        appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, 
                                                        tempInt.copyRO(),
                                                        new OPT_RegisterOperand(xerRegMap_OV, VM_TypeReference.Int),
                                                        new OPT_IntConstantOperand(0),
                                                        OPT_ConditionOperand.EQUAL(),
                                                        new OPT_IntConstantOperand(0),
                                                        new OPT_IntConstantOperand(1 << 30)));
        appendInstructionToCurrentBlock(Binary.create(INT_OR, xer.copyRO(),
                                                      xer.copy(),
                                                      tempInt.copyRO())
                                        );
    
        // CA bit
        appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, 
                                                        tempInt.copyRO(),
                                                        new OPT_RegisterOperand(xerRegMap_CA, VM_TypeReference.Boolean),
                                                        new OPT_IntConstantOperand(0),
                                                        OPT_ConditionOperand.EQUAL(),
                                                        new OPT_IntConstantOperand(0),
                                                        new OPT_IntConstantOperand(1 << 29)));
        appendInstructionToCurrentBlock(Binary.create(INT_OR, xer.copyRO(),
                                                      xer.copy(),
                                                      tempInt.copyRO()));
    
        // Store to process space
        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("xer"),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(PutField.create(PUTFIELD, xer.copy(),
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }


    /**
     * Copy the value of the LR register into its temporary location
     * from the PPC_ProcessSpace.
     */
    private void fillLRRegister()
    {
        OPT_RegisterOperand result;
        if (lrRegMap == null) {
            result = gc.temps.makeTempInt();
            lrRegMap = result.register; // Set mapping
        }
        else {
            result = new OPT_RegisterOperand(lrRegMap, VM_TypeReference.Int);
        }
        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("lr"),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(GetField.create(GETFIELD, result,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }

    /**
     * Copy the value of the LR register into the PPC_ProcessSpace from
     * its temporary location.
     */
    private void spillLRRegister()
    {
        OPT_RegisterOperand regOp = new OPT_RegisterOperand(lrRegMap, VM_TypeReference.Int);

        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("lr"),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(PutField.create(PUTFIELD, regOp,
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand())
                                        );
    }

    /**
     * Fill all the registers from the PPC_ProcessSpace.
     */
    protected void fillAllRegisters() {
        for(int r = 0 ; r < 32 ; r++) {
            fillGPRegister(r);
        }
        for(int r = 0 ; r < 32 ; r++) {
            fillFPRegister(r);
        }
	for(int crf=0; crf < 8; crf++) {
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
        for(int r = 0 ; r < 32 ; r++) {
            spillGPRegister(r);
        }
        for(int r = 0 ; r < 32 ; r++) {
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
        VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;")
                                                                );
        VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref,VM_Atom.findOrCreateAsciiAtom("pc"),
                                                                VM_Atom.findOrCreateAsciiAtom("I")
                                                                ).asFieldReference();
        appendInstructionToCurrentBlock(PutField.create(PUTFIELD, new OPT_IntConstantOperand(pc),
                                                        gc.makeLocal(1,psTref),
                                                        new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                        new OPT_LocationOperand(ref),
                                                        new OPT_TrueGuardOperand()));
    }
    // -oO Find a register Oo-

    /**
     * Return the HIR OPT_RegisterOperand (temporary register)
     * corresponding to a general purpose PPC register.
     * @param r the number of the PPC GP register
     */
    public OPT_RegisterOperand getGPRegister(int r) {
        if (VM.VerifyAssertions) VM._assert(r < 32);
        intRegInUseMap[r] = true;
        return new OPT_RegisterOperand (intRegMap[r], VM_TypeReference.Int);
    }

    /**
     * Return the HIR OPT_RegisterOperand (temporary register)
     * corresponding to a floating point PPC register
     * @param r the number of the PPC FP register
     */
    public OPT_RegisterOperand getFPRegister(int r) {
        if (VM.VerifyAssertions) VM._assert(r < 32);
        fpRegInUseMap[r] = true;
        return new OPT_RegisterOperand (fpRegMap[r], VM_TypeReference.Double);
    }

    /**
     * Return a boolean register operand encoding the lt bit of the
     * given condition register field
     * @param crf the condition register field to be read
     * @return a register holding the crf bit
     */
    public OPT_RegisterOperand getCR_Lt_Register(int crf) {
	crFieldInUseMap[crf] = true;
        return new OPT_RegisterOperand (crFieldMap_Lt[crf], VM_TypeReference.Boolean);
    }
    /**
     * Return a boolean register operand encoding the gt bit of the
     * given condition register field
     * @param crf the condition register field to be read
     * @return a register holding the crf bit
     */
    public OPT_RegisterOperand getCR_Gt_Register(int crf) {
	crFieldInUseMap[crf] = true;
        return new OPT_RegisterOperand (crFieldMap_Gt[crf], VM_TypeReference.Boolean);
    }
    /**
     * Return a boolean register operand encoding the eq bit of the
     * given condition register field
     * @param crf the condition register field to be read
     * @return a register holding the crf bit
     */
    public OPT_RegisterOperand getCR_Eq_Register(int crf) {
	crFieldInUseMap[crf] = true;
        return new OPT_RegisterOperand (crFieldMap_Eq[crf], VM_TypeReference.Boolean);
    }
    /**
     * Return a boolean register operand encoding the so bit of the
     * given condition register field
     * @param crf the condition register field to be read
     * @return a register holding the crf bit
     */
    public OPT_RegisterOperand getCR_SO_Register(int crf) {
	crFieldInUseMap[crf] = true;
        return new OPT_RegisterOperand (crFieldMap_SO[crf], VM_TypeReference.Boolean);
    }
    public OPT_RegisterOperand getCRB_Register(int crb) {
	int crf = crb >> 2;
	switch(crb & 0x3) {
	case 0:
	    return ppc2ir.getCR_Lt_Register(crf);
	case 1:
	    return ppc2ir.getCR_Gt_Register(crf);
	case 2:
	    return ppc2ir.getCR_Eq_Register(crf);
	case 3:
	    return ppc2ir.getCR_SO_Register(crf);
	default:
	    DBT_OptimizingCompilerException.UNREACHABLE();
	}
    }

    /**
     * Combine condition register fields into a single 32bit condition
     * register
     * @return a register holding the CR register
     */
    public OPT_RegisterOperand getCRRegister() {
	throw new Error("TODO");
    }

    /**
     * Return the HIR OPT_RegisterOperand (temporary register)
     * corresponding to the PPC fpscr register.
     */
    public OPT_RegisterOperand getFPSCRRegister() {
        fpscrRegInUse = true;
        return new OPT_RegisterOperand (fpscrRegMap, VM_TypeReference.Int);
    }

    /**
     * Return the HIR OPT_RegisterOperand (temporary register)
     * corresponding to the PPC ctr register
     */
    public OPT_RegisterOperand getCTRRegister() {
        ctrRegInUse = true;
        return new OPT_RegisterOperand (ctrRegMap, VM_TypeReference.Int);
    }

    /**
     * Return the HIR OPT_RegisterOperand (temporary register)
     * corresponding to the PPC xer register byte count bits
     */
    public OPT_RegisterOperand getXER_ByteCountRegister() {
        xerRegInUse = true;
        return new OPT_RegisterOperand (xerRegMap_ByteCount, VM_TypeReference.Int);
    }

    /**
     * Return the HIR OPT_RegisterOperand (temporary register)
     * corresponding to the PPC xer register SO bit
     */
    public OPT_RegisterOperand getXER_SO_Register() {
        xerRegInUse = true;
        return new OPT_RegisterOperand (xerRegMap_SO, VM_TypeReference.Int);
    }

    /**
     * Return the HIR OPT_RegisterOperand (temporary register)
     * corresponding to the PPC xer register OV bit
     */
    public OPT_RegisterOperand getXER_OV_Register() {
        xerRegInUse = true;
        return new OPT_RegisterOperand (xerRegMap_OV, VM_TypeReference.Int);
    }

    /**
     * Return the HIR OPT_RegisterOperand (temporary register)
     * corresponding to the PPC xer register CA bit
     */
    public OPT_RegisterOperand getXER_CA_Register() {
        xerRegInUse = true;
        return new OPT_RegisterOperand (xerRegMap_CA, VM_TypeReference.Boolean);
    }

    /**
     * Return the HIR OPT_RegisterOperand (temporary register)
     * corresponding to the PPC lr register.
     */
    public OPT_RegisterOperand getLRRegister() {
        lrRegInUse = true;
        return new OPT_RegisterOperand (lrRegMap, VM_TypeReference.Int);
    }

    // -oO Lazy state Oo-

    /**
     * Plant instructions modifying a lazy state into one with no
     * laziness for the specified condition register field
     * @param laziness the laziness to modify
     * @param crf the condition register field
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
     * Plant instructions modifying a lazy state into one with no
     * laziness
     * @param laziness the laziness to modify
     */
    public void resolveLaziness(Laziness laziness) {
        ((PPC_Laziness)laziness).resolve();
    }

    /**
     * Plant instructions modifying a lazy state into one with no
     * laziness for the specified bit
     * @param laziness the laziness to modify
     * @param bit the bit required to be resolved
     */
    public void resolveLazinessCrBit(Laziness laziness, int bit) {
        int crf=bit >> 2;
        resolveLazinessCrField((PPC_Laziness)laziness, crf);
    }

    // -oO Trace control Oo-

    /**
     * Register a branch and link instruction
     * @param pc the address of the branch instruction (implicity the
     * link value will be assumed to be pc+4)
     * @param dest the destination of the branch instruction
     */
    public void registerBranchAndLink(int pc, int dest) {
        registerBranchAndLink(pc, pc+4, dest);
    }

    // -oO Trace helping methods Oo-

    /**
     * Plant a record bclr call. NB register state won't get resolved for call
     * @param pc the address of the bclr instruction
     * @param lr the link register value
     */
    public void plantRecordUncaughtBclr(int pc, OPT_RegisterOperand lr) {
        // Is it sensible to record this information?
        if((gc.options.getOptLevel() > 0) && (DBT_Options.plantUncaughtBclrWatcher)) {
            // Plant call
            OPT_Instruction s = Call.create(CALL, null, null, null, null, 3);
            VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                    VM_Atom.findOrCreateAsciiAtom
                                                                    ("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;"));                       
            VM_MethodReference methRef = (VM_MethodReference)VM_MemberReference.findOrCreate(psTref,
                                                                                             VM_Atom.findOrCreateAsciiAtom("recordUncaughtBclr"),
                                                                                             VM_Atom.findOrCreateAsciiAtom("(II)V"));
            VM_Method method = methRef.resolve();
            OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL(methRef, method);

            OPT_Operand psRef = gc.makeLocal(1,psTref); 
            Call.setParam(s, 0, psRef); // Reference to ps, sets 'this' pointer
            Call.setParam(s, 1, new OPT_IntConstantOperand(pc)); // Address of bclr instruction
            Call.setParam(s, 2, lr);    // Link register value
            Call.setGuard(s, new OPT_TrueGuardOperand());
            Call.setMethod(s, methOp);
            Call.setAddress(s, new OPT_AddressConstantOperand(methRef.peekResolvedMethod().getOffset()));
            s.position = gc.inlineSequence;
            s.bcIndex = 7;
            appendInstructionToCurrentBlock(s);
        }
    }

    /**
     * Plant a record bcctr call. NB register state won't get resolved for call
     * @param pc the address of the bclr instruction
     * @param ctr the count register value
     */
    public void plantRecordUncaughtBcctr(int pc, OPT_RegisterOperand ctr) {
        if(DBT_Options.plantUncaughtBcctrWatcher) {
            // Plant call
            OPT_Instruction s = Call.create(CALL, null, null, null, null, 3);
            VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                                    VM_Atom.findOrCreateAsciiAtom
                                                                    ("Lorg/binarytranslator/arch/ppc/os/process/PPC_ProcessSpace;"));                       
            VM_MethodReference methRef = (VM_MethodReference)VM_MemberReference.findOrCreate(psTref,
                                                                                             VM_Atom.findOrCreateAsciiAtom("recordUncaughtBcctr"),
                                                                                             VM_Atom.findOrCreateAsciiAtom("(II)V"));
            VM_Method method = methRef.resolve();
            OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL(methRef, method);
            OPT_Operand psRef = gc.makeLocal(1,psTref); 
            Call.setParam(s, 0, psRef); // Reference to ps, sets 'this' pointer
            Call.setParam(s, 1, new OPT_IntConstantOperand(pc)); // Address of bcctr instruction
            Call.setParam(s, 2, ctr);   // Count register value
            Call.setGuard(s, new OPT_TrueGuardOperand());
            Call.setMethod(s, methOp);
            Call.setAddress(s, new OPT_AddressConstantOperand(methRef.peekResolvedMethod().getOffset()));
            s.position = gc.inlineSequence;
            s.bcIndex = 13;
            appendInstructionToCurrentBlock(s);
        }
    }

    // -oO Optimisations on the generated HIR Oo-

    /**
     * Return an array of unused registers
     */
    protected OPT_Register[] getUnusedRegisters() {
        ArrayList unusedRegisterList = new ArrayList();
        for(int i=0; i < 32; i++) {
            if(intRegInUseMap[i] == false) {
                unusedRegisterList.add(intRegMap[i]);
            }
            if(fpRegInUseMap[i] == false) {
                unusedRegisterList.add(fpRegMap[i]);
            }
        }
        if(fpscrRegInUse == false) {
            unusedRegisterList.add(fpscrRegMap);
        }
        if(ctrRegInUse == false) {
            unusedRegisterList.add(ctrRegMap);
        }
	for(int crf=0; crf < 8; crf++) {
	    if(crFieldInUseMap[crf] == false) {
		unusedRegisterList.add(crFieldMap_Lt[crf]);
		unusedRegisterList.add(crFieldMap_Gt[crf]);
		unusedRegisterList.add(crFieldMap_Eq[crf]);
		unusedRegisterList.add(crFieldMap_SO[crf]);
	    }
	}
        if(lrRegInUse == false) {
            unusedRegisterList.add(lrRegMap);
        }
        if(xerRegInUse == false) {
            unusedRegisterList.add(xerRegMap);
            unusedRegisterList.add(xerRegMap_SO);
            unusedRegisterList.add(xerRegMap_OV);
            unusedRegisterList.add(xerRegMap_ByteCount);
            unusedRegisterList.add(xerRegMap_CA);
        }
        return (OPT_Register[])unusedRegisterList.toArray(new OPT_Register[unusedRegisterList.size()]);
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
     * @todo implement this
     */
    private void dump() {
    }
} // End of class PPC2IR
