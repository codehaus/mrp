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
package org.binarytranslator.arch.arm.decoder;

import java.util.ArrayList;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.decoder.ARM_Laziness.Flag;
import org.binarytranslator.arch.arm.decoder.ARM_Laziness.Operation;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.arch.arm.os.process.ARM_Registers.OperatingMode;
import org.binarytranslator.generic.branchprofile.BranchProfile.BranchType;
import org.binarytranslator.generic.decoder.CodeTranslator;
import org.binarytranslator.generic.decoder.Laziness;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.ir.*;
import org.jikesrvm.compilers.opt.ir.operand.*;

import static org.jikesrvm.compilers.opt.ir.Operators.*;

public class ARM2IR extends CodeTranslator {

  /** Mapping of ARM registers to HIR registers */
  private Register regMap[] = new Register[16];
  
  /** The ARM carry flag. */
  private boolean carryUsed;
  private Register carryFlag;
  
  /** The ARM zero flag. */
  private boolean zeroUsed;
  private Register zeroFlag;
  
  /** The ARM negative flag. */
  private boolean negativeUsed;
  private Register negativeFlag;
  
  /** The ARM overflow flag. */
  private boolean overflowUsed;
  private Register overflowFlag;
  
  /** Set to true for each register that is in use during the current trace */
  private boolean regUsed[] = new boolean[16];

  /** Type reference to the ARM process space */
  private static final TypeReference psTref;

  /** A field reference to the ARM registers class within the PS */
  private static final FieldReference registersFref;
  
  /** A type reference to the ARM registers class */
  private static final TypeReference registersTref;
  
  /** A field reference to the ARM registers array within the ARM_Registers class */
  private static final FieldReference registers_regs_Fref;
  
  /** A type reference to the ARM registers array within the ARM_Registers class */
  private static final TypeReference registers_regs_Tref;
  
  /** A field reference to the carry flag within the ARM registers. */
  private static final FieldReference registers_carryFlag_Fref;
  
  /** A field reference to the zero flag within the ARM registers. */
  private static final FieldReference registers_zeroFlag_Fref;
  
  /** A field reference to the negative flag within the ARM registers. */
  private static final FieldReference registers_negativeFlag_Fref;
  
  /** A field reference to the overflow flag within the ARM registers. */
  private static final FieldReference registers_overflowFlag_Fref;
  
  /** A register holding a reference to ps.registers */
  private Register ps_registers;
  
  /** A register holding a reference to ps.registers.regs */
  private Register ps_registers_regs;
  
  /** The class performing the actual translation of the bytecode. */
  private final ARM_Translator translator;
  
  /** Determines how flags are resolved and if laziness is used.*/
  private final ARM_FlagBehavior flagBehavior;
  
  static {
    psTref = TypeReference.findOrCreate(ARM_ProcessSpace.class);
    
    registersFref = FieldReference
        .findOrCreate(
            psTref,
            Atom.findOrCreateAsciiAtom("registers"),
            Atom
                .findOrCreateAsciiAtom("Lorg/binarytranslator/arch/arm/os/process/ARM_Registers;"))
        .asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registersFref != null);
    
    registersTref = registersFref.getFieldContentsType();
    
    if (DBT.VerifyAssertions) DBT._assert(registersTref != null);
    
    registers_regs_Fref = MemberReference.findOrCreate(
        registersTref, Atom.findOrCreateAsciiAtom("regs"),
        Atom.findOrCreateAsciiAtom("[I")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_regs_Fref != null);
    
    registers_regs_Tref = registers_regs_Fref.getFieldContentsType();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_regs_Tref != null);
    
    registers_carryFlag_Fref = MemberReference
    .findOrCreate(registersTref,
                    Atom.findOrCreateAsciiAtom("flagCarry"),
                    Atom.findOrCreateAsciiAtom("Z")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_carryFlag_Fref != null);
    
    registers_zeroFlag_Fref = MemberReference
    .findOrCreate(registersTref,
                    Atom.findOrCreateAsciiAtom("flagZero"),
                    Atom.findOrCreateAsciiAtom("Z")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_zeroFlag_Fref != null);
    
    registers_negativeFlag_Fref = MemberReference
    .findOrCreate(registersTref,
                    Atom.findOrCreateAsciiAtom("flagNegative"),
                    Atom.findOrCreateAsciiAtom("Z")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_negativeFlag_Fref != null);
    
    registers_overflowFlag_Fref = MemberReference
    .findOrCreate(registersTref,
                    Atom.findOrCreateAsciiAtom("flagOverflow"),
                    Atom.findOrCreateAsciiAtom("Z")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_overflowFlag_Fref != null);
  }

  public ARM2IR(GenerationContext context, DBT_Trace trace) {
    super(context, trace);
    translator = new ARM_Translator((ARM_ProcessSpace)ps, this);
    
    switch (ARM_Options.flagEvaluation) {
    case Immediate:
      flagBehavior = new ARM_ImmediateFlagBehavior();
      break;
      
    case Lazy:
      flagBehavior = new ARM_LazyFlagBehavior();
      break;
      
    default:
      throw new RuntimeException("Unexpected flag behaviour: " + ARM_Options.flagEvaluation);
    }
  }
  
  /** ARM has an interchangeable flag behavior. Flags can either be evaluated immediately or on demand using
   * lazy evaluation. This interface encapsulates the differences. */
  public abstract class ARM_FlagBehavior {
    
    /**
     * Interface helper function. If a flag behaviour wants to set a value of a flag, it shall set
     * the {@link RegisterOperand} returned by this function.
     */
    protected final RegisterOperand getFlag(Flag flag) {
      switch (flag) {
      case Zero:
        return new RegisterOperand(zeroFlag, TypeReference.Boolean);
        
      case Carry:
        return new RegisterOperand(carryFlag, TypeReference.Boolean);
        
      case Negative:
        return new RegisterOperand(negativeFlag, TypeReference.Boolean);
        
      case Overflow:
        return new RegisterOperand(overflowFlag, TypeReference.Boolean);
        
      default:
        throw new RuntimeException("Unexpected flag type: " + flag);
      }
    }
    /** Called before a flag is written to directly. */
    public abstract void onFlagWrite(Flag flag, ARM_Laziness lazy);
    
    /** Called before a flag is read. */
    public abstract void onFlagRead(Flag flag, ARM_Laziness lazy);
    
    /** Called when the ARM flags shall be set by a logical operation. This sets the zero and negative flag. */
    public abstract void appendLogicalFlags(ARM_Laziness lazy, Operand result);
    
    /** Called when the ARM flags shall be set by a ADD operation. This sets all ARM flags. */
    public abstract void appendAddFlags(ARM_Laziness lazy, Operand result, Operand op1, Operand op2);
    
    /** Called when the ARM flags shall be set by a SUB operation. This sets all ARM flags. */
    public abstract void appendSubFlags(ARM_Laziness lazy, Operand result, Operand op1, Operand op2);
    public abstract void appendReverseSubFlags(ARM_Laziness lazy, RegisterOperand result, Operand lhs, Operand rhs);
  }
  
  /** Implements a flag behavior that will immediately evaluate all flag values. */
  public final class ARM_ImmediateFlagBehavior extends ARM_FlagBehavior {

    @Override
    public void appendAddFlags(ARM_Laziness lazy, Operand result, Operand op1, Operand op2) {
      
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Zero), result.copy(), new IntConstantOperand(0), ConditionOperand.EQUAL(), new BranchProfileOperand()));
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Carry), result.copy(), op1.copy(), ConditionOperand.LOWER(), new BranchProfileOperand()));
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Negative), result.copy(), new IntConstantOperand(0), ConditionOperand.LESS(), new BranchProfileOperand()));
      
      if (ARM_Options.useOptimizedFlags) {
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Overflow), op1.copy(), op2.copy(), ConditionOperand.OVERFLOW_FROM_ADD(), BranchProfileOperand.unlikely()));
      }
      else {
        //resolve overflow
        RegisterOperand overflow = getFlag(Flag.Overflow);
        RegisterOperand tmp1 = getTempInt(5);
        RegisterOperand tmp2 = getTempInt(6);
        RegisterOperand tmp_bool = gc.temps.makeTempBoolean();

        appendInstruction(Binary.create(INT_SUB, tmp1.copyRO(), new IntConstantOperand(Integer.MAX_VALUE), op2.copy()));
        appendInstruction(Binary.create(INT_SUB, tmp2.copyRO(), new IntConstantOperand(Integer.MIN_VALUE), op2.copy()));
        appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, tmp_bool.copyRO(), op2.copy(), new IntConstantOperand(0), ConditionOperand.GREATER_EQUAL(), new BranchProfileOperand(), op1.copy(), tmp1.copy(), ConditionOperand.GREATER(), BranchProfileOperand.unlikely()));
        appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, overflow.copyRO(), op2.copy(), new IntConstantOperand(0), ConditionOperand.LESS() , new BranchProfileOperand(), op1.copy(), tmp2.copy(), ConditionOperand.LESS(), BranchProfileOperand.unlikely()));
        appendInstruction(Binary.create(INT_OR, overflow.copyRO(), overflow.copy(), tmp_bool.copy()));
      }
    }

    @Override
    public void appendLogicalFlags(ARM_Laziness lazy, Operand result) {
      
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Zero), result.copy(), new IntConstantOperand(0), ConditionOperand.EQUAL(), new BranchProfileOperand()));
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Negative), result.copy(), new IntConstantOperand(0), ConditionOperand.LESS(), new BranchProfileOperand()));
      
    }

    @Override
    public void appendSubFlags(ARM_Laziness lazy, Operand result, Operand op1, Operand op2) {
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Zero), result.copy(), new IntConstantOperand(0), ConditionOperand.EQUAL(), new BranchProfileOperand()));
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Negative), result.copy(), new IntConstantOperand(0), ConditionOperand.LESS(), new BranchProfileOperand()));      
      
      if (ARM_Options.useOptimizedFlags) {
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Carry), op1.copy(), op2.copy(), ConditionOperand.BORROW_FROM_SUB().flipCode(), new BranchProfileOperand()));
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Overflow), op1.copy(), op2.copy(), ConditionOperand.OVERFLOW_FROM_SUB(), BranchProfileOperand.unlikely()));
      }
      else {
        //resolve carry
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Carry), op1.copy(), op2.copy(), ConditionOperand.LOWER().flipCode(), new BranchProfileOperand()));

        //resolve overflow
        RegisterOperand overflow = getFlag(Flag.Overflow);
        RegisterOperand tmp1 = getTempInt(5);
        RegisterOperand tmp2 = getTempInt(6);
        RegisterOperand tmp_bool = gc.temps.makeTempBoolean();

        appendInstruction(Binary.create(INT_ADD, tmp1.copyRO(), new IntConstantOperand(Integer.MIN_VALUE), op2.copy()));
        appendInstruction(Binary.create(INT_ADD, tmp2.copyRO(), new IntConstantOperand(Integer.MAX_VALUE), op2.copy()));
        
        appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, tmp_bool.copyRO(), op2.copy(), new IntConstantOperand(0), ConditionOperand.GREATER_EQUAL(), new BranchProfileOperand(), op1.copy(), tmp1.copy(), ConditionOperand.LESS(), BranchProfileOperand.unlikely()));
        appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, overflow.copyRO(), op2.copy(), new IntConstantOperand(0), ConditionOperand.LESS() , new BranchProfileOperand(), op1.copy(), tmp2.copy(), ConditionOperand.GREATER(), BranchProfileOperand.unlikely()));
        appendInstruction(Binary.create(INT_OR, overflow.copyRO(), overflow.copy(), tmp_bool.copy()));
      }
    }

    @Override
    public void onFlagRead(Flag flag, ARM_Laziness lazy) {
      //nothing to do here, because the flags are already resolved
    }

    @Override
    public void onFlagWrite(Flag flag, ARM_Laziness lazy) {
      //nothing to do here, because the flags are already resolved
    }

    @Override
    public void appendReverseSubFlags(ARM_Laziness lazy, RegisterOperand result, Operand op1, Operand op2) {
      appendSubFlags(lazy, result, op2, op1);
    }
  }
  
  /** Implements a flag behavior that will use lazy evaluation to only determine a flag value
   * when it is necessary. */
  public final class ARM_LazyFlagBehavior extends ARM_FlagBehavior {
    
    /** Operands for lazy evaluation of condition codes. */
    private Register lazyOperand1;
    private Register lazyOperand2;
    private Register lazyLogicalOperand;
    
    public ARM_LazyFlagBehavior() {
      //prepare the laziness registers
      lazyOperand1 = makeTemp(TypeReference.Int).register;
      lazyOperand2 = makeTemp(TypeReference.Int).register;
      lazyLogicalOperand = makeTemp(TypeReference.Int).register;
    }

    @Override
    public void appendAddFlags(ARM_Laziness lazy, Operand result, Operand op1, Operand op2) {

      appendInstruction(Move.create(INT_MOVE, new RegisterOperand(lazyOperand1, TypeReference.Int), op1.copy()));
      appendInstruction(Move.create(INT_MOVE, new RegisterOperand(lazyOperand2, TypeReference.Int), op2.copy()));
      
      lazy.setValid(Flag.Zero, false);
      lazy.setValid(Flag.Negative, false);
      lazy.setValid(Flag.Carry, false);
      lazy.setValid(Flag.Overflow, false);
      lazy.setOperation(Operation.Add);
      
      if (DBT_Options.debugTranslation) {
        System.out.println("New Lazy state: " + lazy);
      }
    }

    @Override
    public void appendLogicalFlags(ARM_Laziness lazy, Operand result) {
      appendInstruction(Move.create(INT_MOVE, new RegisterOperand(lazyLogicalOperand, TypeReference.Int), result.copy()));
      
      lazy.setValid(Flag.Zero, false);
      lazy.setValid(Flag.Negative, false);
      
      switch (lazy.getOperation()) {
      case Add:
        lazy.setOperation(Operation.LogicalOpAfterAdd);
        break;

      case Sub:
        lazy.setOperation(Operation.LogicalOpAfterSub);
        break;
        
      case LogicalOpAfterAdd:
      case LogicalOpAfterSub:
        break;
        
      default:
        throw new RuntimeException("Unhandled laziness operation: " + lazy.getOperation());
      }
      
      if (DBT_Options.debugTranslation) {
        System.out.println("New Lazy state: " + lazy);
      }
    }

    @Override
    public void appendSubFlags(ARM_Laziness lazy, Operand result, Operand op1, Operand op2) {
      
      appendInstruction(Move.create(INT_MOVE, new RegisterOperand(lazyOperand1, TypeReference.Int), op1.copy()));
      appendInstruction(Move.create(INT_MOVE, new RegisterOperand(lazyOperand2, TypeReference.Int), op2.copy()));
      
      lazy.setValid(Flag.Zero, false);
      lazy.setValid(Flag.Negative, false);
      lazy.setValid(Flag.Carry, false);
      lazy.setValid(Flag.Overflow, false);
      lazy.setOperation(Operation.Sub);
      
      if (DBT_Options.debugTranslation) {
        System.out.println("New Lazy state: " + lazy);
      }
    }
    
    private void resolveFlag(ARM_Laziness.Flag flag, ARM_Laziness lazy) {
      
      if (lazy.isValid(flag))
        return;
      
      if (DBT_Options.debugTranslation) {
        System.out.println("Resolving " + flag + " flag.");
      }
      
      RegisterOperand flagRegister = getFlag(flag);
      RegisterOperand op1 = new RegisterOperand(lazyOperand1, TypeReference.Int);
      RegisterOperand op2 = new RegisterOperand(lazyOperand2, TypeReference.Int);
      RegisterOperand result;
      
      switch (lazy.getOperation()) {
      case Add:
        result = gc.temps.makeTempInt(); 
        appendInstruction(Binary.create(INT_ADD, result, op1, op2));
        break;
        
      case Sub:
        result = gc.temps.makeTempInt();
        appendInstruction(Binary.create(INT_SUB, result, op1, op2));
        break;
        
      case LogicalOpAfterAdd:
      case LogicalOpAfterSub:
        result = new RegisterOperand(lazyLogicalOperand, TypeReference.Int);
        break;
        
      default:
        throw new RuntimeException("Unhandled laziness operation: " + lazy.getOperation());
      }
      
      switch (flag) {
      case Zero:
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, result.copy(), new IntConstantOperand(0), ConditionOperand.EQUAL(), new BranchProfileOperand()));
        break;
        
      case Carry:
        switch (lazy.getOperation()) {
        case LogicalOpAfterAdd:
        case Add: 
          appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, result.copy(), op1.copy(), ConditionOperand.LOWER(), new BranchProfileOperand()));
          break;
        
        case LogicalOpAfterSub:
        case Sub:

          if (ARM_Options.useOptimizedFlags) {
            appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, op1.copy(), op2.copy(), ConditionOperand.BORROW_FROM_SUB().flipCode(), new BranchProfileOperand()));
          }
          else {
            appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, op1.copy(), op2.copy(), ConditionOperand.LOWER().flipCode(), new BranchProfileOperand()));
          }
          break;
          
        default:
          throw new RuntimeException("Unhandled laziness operation: " + lazy.getOperation());
        }
        break;
        
      case Negative:
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, result.copy(), new IntConstantOperand(0), ConditionOperand.LESS(), new BranchProfileOperand()));
        break;
        
      case Overflow:
        switch (lazy.getOperation()) {
        case Add: 
        case LogicalOpAfterAdd:
          if (ARM_Options.useOptimizedFlags) {
            appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, op1.copy(), op2.copy(), ConditionOperand.OVERFLOW_FROM_ADD(), BranchProfileOperand.unlikely()));
          }
          else {
            RegisterOperand tmp1 = getTempInt(5);
            RegisterOperand tmp2 = getTempInt(6);
            RegisterOperand tmp_bool = gc.temps.makeTempBoolean();

            appendInstruction(Binary.create(INT_SUB, tmp1.copyRO(), new IntConstantOperand(Integer.MAX_VALUE), op2.copy()));
            appendInstruction(Binary.create(INT_SUB, tmp2.copyRO(), new IntConstantOperand(Integer.MIN_VALUE), op2.copy()));
            appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, tmp_bool.copyRO(), op2.copy(), new IntConstantOperand(0), ConditionOperand.GREATER_EQUAL(), new BranchProfileOperand(), op1.copy(), tmp1.copy(), ConditionOperand.GREATER(), BranchProfileOperand.unlikely()));
            appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, getFlag(Flag.Overflow), op2.copy(), new IntConstantOperand(0), ConditionOperand.LESS() , new BranchProfileOperand(), op1.copy(), tmp2.copy(), ConditionOperand.LESS(), BranchProfileOperand.unlikely()));
            appendInstruction(Binary.create(INT_OR, flagRegister, getFlag(Flag.Overflow), tmp_bool.copy()));
          }
          break;
          
        case Sub:
        case LogicalOpAfterSub:
          if (ARM_Options.useOptimizedFlags) {
            appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, op1.copy(), op2.copy(), ConditionOperand.OVERFLOW_FROM_SUB(), BranchProfileOperand.unlikely()));
          }
          else {
            //resolve overflow
            RegisterOperand tmp1 = getTempInt(5);
            RegisterOperand tmp2 = getTempInt(6);
            RegisterOperand tmp_bool = gc.temps.makeTempBoolean();

            appendInstruction(Binary.create(INT_ADD, tmp1.copyRO(), new IntConstantOperand(Integer.MIN_VALUE), op2.copy()));
            appendInstruction(Binary.create(INT_ADD, tmp2.copyRO(), new IntConstantOperand(Integer.MAX_VALUE), op2.copy()));
            
            appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, tmp_bool.copyRO(), op2.copy(), new IntConstantOperand(0), ConditionOperand.GREATER_EQUAL(), new BranchProfileOperand(), op1.copy(), tmp1.copy(), ConditionOperand.LESS(), BranchProfileOperand.unlikely()));
            appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, flagRegister, op2.copy(), new IntConstantOperand(0), ConditionOperand.LESS() , new BranchProfileOperand(), op1.copy(), tmp2.copy(), ConditionOperand.GREATER(), BranchProfileOperand.unlikely()));
            appendInstruction(Binary.create(INT_OR, flagRegister.copyRO(), flagRegister.copy(), tmp_bool.copy()));
          }
          break;
          
        default:
          throw new RuntimeException("Unhandled laziness operation: " + lazy.getOperation());
        }

        break;
        
      default:
        throw new RuntimeException("Unhandled flag type: " + flag);
      }
      
      lazy.setValid(flag, true);
      
      if (DBT_Options.debugTranslation) {
        System.out.println("New Lazy state: " + lazy);
      }
    }

    @Override
    public void onFlagRead(Flag flag, ARM_Laziness lazy) {
      resolveFlag(flag, lazy);
    }

    @Override
    public void onFlagWrite(Flag flag, ARM_Laziness lazy) {
      lazy.setValid(flag, true);
    }

    @Override
    public void appendReverseSubFlags(ARM_Laziness lazy, RegisterOperand result, Operand op1, Operand op2) {
      appendSubFlags(lazy, result, op2, op1);
    }
  }

  @Override
  protected Laziness createInitialLaziness() {
    return new ARM_Laziness();
  }
  
  private TypeReference OperatingMode_TypeRef = null;
  
  /**
   * Returns a register operand for values of type <code>OperatingMode</code>.
   * @return
   */
  public RegisterOperand getTempOperatingMode() {
    if (OperatingMode_TypeRef == null) {
      OperatingMode_TypeRef = TypeReference.findOrCreate(ARM_Registers.OperatingMode.class);
    }
    
    return gc.temps.makeTemp(OperatingMode_TypeRef);
  }
  
  /**
   * Returns an register operand that is equivalent to the given operating mode.
   */
  public RegisterOperand getTempOperatingMode(OperatingMode mode) {
    //We are going to insert a GETFIELD, which will grab the appropriate OperatingMode static instance from
    //the OperatingMode class.
    RegisterOperand result = getTempOperatingMode();
    
    //the type reference should have been initialized by the previous call to getTempOperatingMode.
    //For the sake of defensive programming, we are doing it again here...
    if (OperatingMode_TypeRef == null) {
      OperatingMode_TypeRef = TypeReference.findOrCreate(ARM_Registers.OperatingMode.class);
    }

    //grab the field reference to the corresponding field
    FieldReference requestedMode_FieldReference = FieldReference.findOrCreate(OperatingMode_TypeRef, Atom.findOrCreateAsciiAtom(mode.name()), Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/arm/os/process/ARM_Registers/OperatingMode;")).asFieldReference();
    
    //Finally, use a getfield to grab the (static) member field 
    appendInstruction(GetField.create(GETFIELD, result,
        null, new AddressConstantOperand(requestedMode_FieldReference
            .peekResolvedField().getOffset()), new LocationOperand(
                requestedMode_FieldReference), new TrueGuardOperand()));
    
    return result;
  }
  
  @Override
  protected boolean inlineBranchInstruction(int targetPc, UnresolvedJumpInstruction jump) {
    
    switch (ARM_Options.inlining)
    {
    case NoInlining:
      return false;
      
    case Default:
      return super.inlineBranchInstruction(targetPc, jump);
      
    case DynamicJumps:
      return jump.type == BranchType.INDIRECT_BRANCH;
      
    case DirectBranches:
      return jump.type == BranchType.DIRECT_BRANCH;
      
    case Functions:
      return jump.type == BranchType.CALL || jump.type == BranchType.RETURN;
      
    case All:
      return true;
      
    default:
      throw new RuntimeException("Unexpected inlining type.");
    }
    
  }
  
  /**
   * Returns a RegisterOperand that contains a reference to the currently used ARM_Registers instance.
   * Use this reference when calling functions on ARM_Registers.
   */
  public RegisterOperand getArmRegistersReference() {
    RegisterOperand ps_registersOp;
    
    if (ps_registers == null) {
      ps_registersOp = gc.temps.makeTemp(registersTref);
      ps_registers = ps_registersOp.register;
      appendInstruction(GetField.create(GETFIELD, ps_registersOp,
          gc.makeLocal(1, psTref), new AddressConstantOperand(registersFref
              .peekResolvedField().getOffset()), new LocationOperand(
              registersFref), new TrueGuardOperand()));
    }
    else {
      ps_registersOp = new RegisterOperand(ps_registers, registersTref);
    }
    
    return ps_registersOp;
  }
  
  /**
   * Writes all current flag values back to their respective registers
   */
  public void spillAllFlags(Laziness lazyState) {
    
    //first resolve the current lazy state (i.e. calculate the values of registers that are not yet resolved)
    resolveLaziness(lazyState);
    spillAllFlags();
  }
  
  private void spillAllFlags() {

    RegisterOperand ps_registersOp = getArmRegistersReference();
    RegisterOperand flag;
    
    //store the carry flag
    if (carryUsed) {
      flag = new RegisterOperand(carryFlag, TypeReference.Boolean);
      appendInstruction(PutField.create(PUTFIELD, flag, ps_registersOp.copyRO(), new AddressConstantOperand(registers_carryFlag_Fref.peekResolvedField().getOffset()), new LocationOperand(registers_carryFlag_Fref), new TrueGuardOperand()) );
    }
    
    //store the negative flag
    if (negativeUsed) {
      flag = new RegisterOperand(negativeFlag, TypeReference.Boolean);
      appendInstruction(PutField.create(PUTFIELD, flag, ps_registersOp.copyRO(), new AddressConstantOperand(registers_negativeFlag_Fref.peekResolvedField().getOffset()), new LocationOperand(registers_negativeFlag_Fref), new TrueGuardOperand()) );
    }
    
    //store the zero flag
    if (zeroUsed) {
      flag = new RegisterOperand(zeroFlag, TypeReference.Boolean);
      appendInstruction(PutField.create(PUTFIELD, flag, ps_registersOp.copyRO(), new AddressConstantOperand(registers_zeroFlag_Fref.peekResolvedField().getOffset()), new LocationOperand(registers_zeroFlag_Fref), new TrueGuardOperand()) );
    }

    //store the overflow flag
    if (overflowUsed) {
      flag = new RegisterOperand(overflowFlag, TypeReference.Boolean);
      appendInstruction(PutField.create(PUTFIELD, flag, ps_registersOp.copyRO(), new AddressConstantOperand(registers_overflowFlag_Fref.peekResolvedField().getOffset()), new LocationOperand(registers_overflowFlag_Fref), new TrueGuardOperand()) );
    }
  }
  
  public void fillAllFlags() {

    //get an operand that contains a reference to the current ps.registers field.
    RegisterOperand ps_registersOp = getArmRegistersReference();
    
    if (carryFlag == null) {
      carryFlag = gc.temps.getReg(TypeReference.Boolean);
      negativeFlag = gc.temps.getReg(TypeReference.Boolean);
      zeroFlag = gc.temps.getReg(TypeReference.Boolean);
      overflowFlag = gc.temps.getReg(TypeReference.Boolean);
    }

    //get the carry flag
    RegisterOperand flag = new RegisterOperand(carryFlag, TypeReference.Boolean);
    appendInstruction(GetField.create(GETFIELD, flag, ps_registersOp.copyRO(), new AddressConstantOperand(registers_carryFlag_Fref.peekResolvedField().getOffset()), new LocationOperand(registers_carryFlag_Fref), new TrueGuardOperand()) );
    
    //get the negative flag
    flag = new RegisterOperand(negativeFlag, TypeReference.Boolean);
    appendInstruction(GetField.create(GETFIELD, flag, ps_registersOp.copyRO(), new AddressConstantOperand(registers_negativeFlag_Fref.peekResolvedField().getOffset()), new LocationOperand(registers_negativeFlag_Fref), new TrueGuardOperand()) );
    
    //get the zero flag
    flag = new RegisterOperand(zeroFlag, TypeReference.Boolean);
    appendInstruction(GetField.create(GETFIELD, flag, ps_registersOp.copyRO(), new AddressConstantOperand(registers_zeroFlag_Fref.peekResolvedField().getOffset()), new LocationOperand(registers_zeroFlag_Fref), new TrueGuardOperand()) );
    
    //get the overflow flag
    flag = new RegisterOperand(overflowFlag, TypeReference.Boolean);
    appendInstruction(GetField.create(GETFIELD, flag, ps_registersOp.copyRO(), new AddressConstantOperand(registers_overflowFlag_Fref.peekResolvedField().getOffset()), new LocationOperand(registers_overflowFlag_Fref), new TrueGuardOperand()) );    
  }

  @Override
  protected void fillAllRegisters() {
    
    //get an operand that contains a reference to the current ps.registers field.
    RegisterOperand ps_registersOp = getArmRegistersReference();

    // Get the array of general purpose registers
    RegisterOperand ps_registers_regsOp;
    if (ps_registers_regs == null) {

      ps_registers_regsOp = gc.temps.makeTemp(registers_regs_Tref);
      appendInstruction(GetField.create(GETFIELD,
          ps_registers_regsOp, ps_registersOp.copyRO(),
          new AddressConstantOperand(registers_regs_Fref.peekResolvedField()
              .getOffset()), new LocationOperand(registers_regs_Fref),
          new TrueGuardOperand()));
      ps_registers_regs = ps_registers_regsOp.register;
    } else {
      ps_registers_regsOp = new RegisterOperand(ps_registers_regs, registers_regs_Tref);
    }

    // Fill general purpose registers
    for (int i = 0; i < regMap.length; i++) {
      RegisterOperand regOp;
      if (regMap[i] == null) {
        regOp = makeTemp(TypeReference.Int);
        regMap[i] = regOp.register;
      } else {
        regOp = new RegisterOperand(regMap[i], TypeReference.Int);
      }
      appendInstruction(ALoad.create(INT_ALOAD, regOp,
          ps_registers_regsOp.copyRO(), new IntConstantOperand(i),
          new LocationOperand(TypeReference.Int),
          new TrueGuardOperand()));
    }
    
    //fill all flags from the process space
    fillAllFlags();
  }
  
  public RegisterOperand getRegister(int r) {
    regUsed[r] = true;
    return new RegisterOperand(regMap[r], TypeReference.Int);
  }
  
  public Operand readCarryFlag(ARM_Laziness lazy) {
    carryUsed = true;
    flagBehavior.onFlagRead(Flag.Carry, lazy);
    return new RegisterOperand(carryFlag, TypeReference.Boolean);
  }
  
  public Operand readZeroFlag(ARM_Laziness lazy) {
    zeroUsed = true;
    flagBehavior.onFlagRead(Flag.Zero, lazy);
    return new RegisterOperand(zeroFlag, TypeReference.Boolean);
  }
  
  public Operand readNegativeFlag(ARM_Laziness lazy) {
    negativeUsed = true;
    flagBehavior.onFlagRead(Flag.Negative, lazy);
    return new RegisterOperand(negativeFlag, TypeReference.Boolean);
  }
  
  public Operand readOverflowFlag(ARM_Laziness lazy) {
    overflowUsed = true;
    flagBehavior.onFlagRead(Flag.Overflow, lazy);
    return new RegisterOperand(overflowFlag, TypeReference.Boolean);
  }
  
  public RegisterOperand writeCarryFlag(ARM_Laziness lazy) {
    carryUsed = true;
    flagBehavior.onFlagWrite(Flag.Carry, lazy);
    return new RegisterOperand(carryFlag, TypeReference.Boolean);
  }
  
  public RegisterOperand writeZeroFlag(ARM_Laziness lazy) {
    zeroUsed = true;
    flagBehavior.onFlagWrite(Flag.Zero, lazy);
    return new RegisterOperand(zeroFlag, TypeReference.Boolean);
  }
  
  public RegisterOperand writeNegativeFlag(ARM_Laziness lazy) {
    negativeUsed = true;
    flagBehavior.onFlagWrite(Flag.Negative, lazy);
    return new RegisterOperand(negativeFlag, TypeReference.Boolean);
  }
  
  public RegisterOperand writeOverflowFlag(ARM_Laziness lazy) {
    overflowUsed = true;
    flagBehavior.onFlagWrite(Flag.Overflow, lazy);
    return new RegisterOperand(overflowFlag, TypeReference.Boolean);
  }
  
  public void appendLogicalFlags(ARM_Laziness lazy, Operand result) {
    zeroUsed = negativeUsed = true;
    flagBehavior.appendLogicalFlags(lazy, result);
  }
  
  public void appendSubFlags(ARM_Laziness lazy, Operand result, Operand op1, Operand op2) {
    zeroUsed = negativeUsed = carryUsed = overflowUsed = true;
    flagBehavior.appendSubFlags(lazy, result, op1, op2);
  }
  

  public void appendReverseSubFlags(ARM_Laziness lazy, RegisterOperand result, Operand lhs, Operand rhs) {
    zeroUsed = negativeUsed = carryUsed = overflowUsed = true;
    flagBehavior.appendReverseSubFlags(lazy, result, lhs, rhs);    
  }
  
  public void appendAddFlags(ARM_Laziness lazy, Operand result, Operand op1, Operand op2) {
    zeroUsed = negativeUsed = carryUsed = overflowUsed = true;
    flagBehavior.appendAddFlags(lazy, result, op1, op2);
  }
   
  @Override
  protected Register[] getUnusedRegisters() {
    
    ArrayList<Register> unusedRegisters = new ArrayList<Register>();
    
    for (int i = 0; i < regUsed.length; i++) {
      if (!regUsed[i]) {
        unusedRegisters.add(regMap[i]);
      }
    }
    
    if (!carryUsed)
      unusedRegisters.add(carryFlag);
    
    if (!negativeUsed)
      unusedRegisters.add(negativeFlag);
    
    if (!overflowUsed)
      unusedRegisters.add(overflowFlag);
    
    if (!zeroUsed)
      unusedRegisters.add(zeroFlag);
    
    return unusedRegisters.toArray(new Register[unusedRegisters.size()]);
  }

  @Override
  protected void report(String str) {
    System.out.println("ARM2IR: " + str);
  }

  @Override
  public void resolveLaziness(Laziness laziness) {
    ARM_Laziness lazy = (ARM_Laziness)laziness;
    
    if (carryUsed)
      flagBehavior.onFlagRead(Flag.Carry, lazy);
    
    if (negativeUsed)
      flagBehavior.onFlagRead(Flag.Negative, lazy);
    
    if (overflowUsed)
      flagBehavior.onFlagRead(Flag.Overflow, lazy);
    
    if (zeroUsed)
      flagBehavior.onFlagRead(Flag.Zero, lazy);
  }
  
  @Override
  public void appendSystemCall(Laziness lazy) {
    super.appendSystemCall(lazy);
    
    //ARM system calls may change all registers
    for (int i = 0; i < 15; i++)
      regUsed[i] = true;
  }

  @Override
  protected void spillAllRegisters() {
    
    // spill general purpose registers
    RegisterOperand ps_registers_regsOp = new RegisterOperand(
        ps_registers_regs, registers_regs_Tref);
    for (int i = 0; i < regMap.length; i++) {
      // We can save spills if the register was never used
      if (regUsed[i] == true) {
        appendInstruction(AStore.create(INT_ASTORE,
            new RegisterOperand(regMap[i], TypeReference.Int),
            ps_registers_regsOp.copyRO(), new IntConstantOperand(i),
            new LocationOperand(TypeReference.Int),
            new TrueGuardOperand()));
      }
    }
    
    //spill all flags to the process space
    spillAllFlags();
  }

  @Override
  protected int translateInstruction(Laziness lazy, int pc) {
    
    int nextAddr = translator.translateInstruction(pc, (ARM_Laziness)lazy);
    return nextAddr;
  }
  
  /**
   * Adds code to the current block that will rotate <code>rotatedOperand</code> by
   * <code>rotation</code> and stores the rotated integer into <code>result</code>.
   * @param result
   *  The register into which the rotated value is stored.
   * @param rotatedOperand
   *  The operand which is to be rotated.
   * @param rotation
   *  The amount of rotation that is to be applied to the operand.
   *  
   * @param inline
   *  Shall the invokation of this rotate right be inlined?
   */
  public void appendRotateRight(RegisterOperand result, Operand rotatedOperand, Operand rotation) {
    TypeReference IntegerType = TypeReference
        .findOrCreate(Integer.class);

    MethodReference rotateRightMethodRef = MemberReference
        .findOrCreate(IntegerType,
            Atom.findOrCreateAsciiAtom("rotateRight"),
            Atom.findOrCreateAsciiAtom("(II)I")).asMethodReference();

    RVMMethod rotateRightMethod = rotateRightMethodRef.resolve();

    Instruction s = Call.create(CALL, null, null, null, null, 2);
    MethodOperand methOp = MethodOperand
        .STATIC(rotateRightMethod);

    Call.setParam(s, 0, rotatedOperand);
    Call.setParam(s, 1, rotation);
    Call.setResult(s, result);
    Call.setGuard(s, new TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new AddressConstantOperand(rotateRightMethod
        .getOffset()));

    appendInstruction(s);
  }

  protected void appendBitTest(RegisterOperand target, Operand wordToTest, Operand bit) {
    if (DBT.VerifyAssertions) DBT._assert(wordToTest != target && bit != target);
  
    appendInstruction(Binary.create(Operators.INT_SHL, target, new IntConstantOperand(1), bit));
    appendInstruction(Binary.create(Operators.INT_AND, target, wordToTest, target));
    appendInstruction(BooleanCmp.create(Operators.BOOLEAN_CMP_INT, target, target, new IntConstantOperand(0), ConditionOperand.NOT_EQUAL(), new BranchProfileOperand()));
  }

  protected void appendBitTest(RegisterOperand target, Operand wordToTest, int bit) {
    if (DBT.VerifyAssertions) DBT._assert(wordToTest != target);
    if (DBT.VerifyAssertions) DBT._assert(bit <= 31 && bit >= 0);
    
    appendInstruction(Binary.create(Operators.INT_AND, target, wordToTest, new IntConstantOperand(1 << bit)));
    appendInstruction(BooleanCmp.create(Operators.BOOLEAN_CMP_INT, target, target, new IntConstantOperand(0), ConditionOperand.NOT_EQUAL(), new BranchProfileOperand()));
  }


}
