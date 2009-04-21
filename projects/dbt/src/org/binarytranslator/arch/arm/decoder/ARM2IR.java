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
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_FieldReference;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.*;

public class ARM2IR extends CodeTranslator implements OPT_HIRGenerator {

  /** Mapping of ARM registers to HIR registers */
  private OPT_Register regMap[] = new OPT_Register[16];
  
  /** The ARM carry flag. */
  private boolean carryUsed;
  private OPT_Register carryFlag;
  
  /** The ARM zero flag. */
  private boolean zeroUsed;
  private OPT_Register zeroFlag;
  
  /** The ARM negative flag. */
  private boolean negativeUsed;
  private OPT_Register negativeFlag;
  
  /** The ARM overflow flag. */
  private boolean overflowUsed;
  private OPT_Register overflowFlag;
  
  /** Set to true for each register that is in use during the current trace */
  private boolean regUsed[] = new boolean[16];

  /** Type reference to the ARM process space */
  private static final VM_TypeReference psTref;

  /** A field reference to the ARM registers class within the PS */
  private static final VM_FieldReference registersFref;
  
  /** A type reference to the ARM registers class */
  private static final VM_TypeReference registersTref;
  
  /** A field reference to the ARM registers array within the ARM_Registers class */
  private static final VM_FieldReference registers_regs_Fref;
  
  /** A type reference to the ARM registers array within the ARM_Registers class */
  private static final VM_TypeReference registers_regs_Tref;
  
  /** A field reference to the carry flag within the ARM registers. */
  private static final VM_FieldReference registers_carryFlag_Fref;
  
  /** A field reference to the zero flag within the ARM registers. */
  private static final VM_FieldReference registers_zeroFlag_Fref;
  
  /** A field reference to the negative flag within the ARM registers. */
  private static final VM_FieldReference registers_negativeFlag_Fref;
  
  /** A field reference to the overflow flag within the ARM registers. */
  private static final VM_FieldReference registers_overflowFlag_Fref;
  
  /** A register holding a reference to ps.registers */
  private OPT_Register ps_registers;
  
  /** A register holding a reference to ps.registers.regs */
  private OPT_Register ps_registers_regs;
  
  /** The class performing the actual translation of the bytecode. */
  private final ARM_Translator translator;
  
  /** Determines how flags are resolved and if laziness is used.*/
  private final ARM_FlagBehavior flagBehavior;
  
  static {
    psTref = VM_TypeReference.findOrCreate(ARM_ProcessSpace.class);
    
    registersFref = VM_FieldReference
        .findOrCreate(
            psTref,
            VM_Atom.findOrCreateAsciiAtom("registers"),
            VM_Atom
                .findOrCreateAsciiAtom("Lorg/binarytranslator/arch/arm/os/process/ARM_Registers;"))
        .asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registersFref != null);
    
    registersTref = registersFref.getFieldContentsType();
    
    if (DBT.VerifyAssertions) DBT._assert(registersTref != null);
    
    registers_regs_Fref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("regs"),
        VM_Atom.findOrCreateAsciiAtom("[I")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_regs_Fref != null);
    
    registers_regs_Tref = registers_regs_Fref.getFieldContentsType();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_regs_Tref != null);
    
    registers_carryFlag_Fref = VM_MemberReference
    .findOrCreate(registersTref,
                    VM_Atom.findOrCreateAsciiAtom("flagCarry"),
                    VM_Atom.findOrCreateAsciiAtom("Z")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_carryFlag_Fref != null);
    
    registers_zeroFlag_Fref = VM_MemberReference
    .findOrCreate(registersTref,
                    VM_Atom.findOrCreateAsciiAtom("flagZero"),
                    VM_Atom.findOrCreateAsciiAtom("Z")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_zeroFlag_Fref != null);
    
    registers_negativeFlag_Fref = VM_MemberReference
    .findOrCreate(registersTref,
                    VM_Atom.findOrCreateAsciiAtom("flagNegative"),
                    VM_Atom.findOrCreateAsciiAtom("Z")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_negativeFlag_Fref != null);
    
    registers_overflowFlag_Fref = VM_MemberReference
    .findOrCreate(registersTref,
                    VM_Atom.findOrCreateAsciiAtom("flagOverflow"),
                    VM_Atom.findOrCreateAsciiAtom("Z")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_overflowFlag_Fref != null);
  }

  public ARM2IR(OPT_GenerationContext context, DBT_Trace trace) {
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
     * the {@link OPT_RegisterOperand} returned by this function.
     */
    protected final OPT_RegisterOperand getFlag(Flag flag) {
      switch (flag) {
      case Zero:
        return new OPT_RegisterOperand(zeroFlag, VM_TypeReference.Boolean);
        
      case Carry:
        return new OPT_RegisterOperand(carryFlag, VM_TypeReference.Boolean);
        
      case Negative:
        return new OPT_RegisterOperand(negativeFlag, VM_TypeReference.Boolean);
        
      case Overflow:
        return new OPT_RegisterOperand(overflowFlag, VM_TypeReference.Boolean);
        
      default:
        throw new RuntimeException("Unexpected flag type: " + flag);
      }
    }
    /** Called before a flag is written to directly. */
    public abstract void onFlagWrite(Flag flag, ARM_Laziness lazy);
    
    /** Called before a flag is read. */
    public abstract void onFlagRead(Flag flag, ARM_Laziness lazy);
    
    /** Called when the ARM flags shall be set by a logical operation. This sets the zero and negative flag. */
    public abstract void appendLogicalFlags(ARM_Laziness lazy, OPT_Operand result);
    
    /** Called when the ARM flags shall be set by a ADD operation. This sets all ARM flags. */
    public abstract void appendAddFlags(ARM_Laziness lazy, OPT_Operand result, OPT_Operand op1, OPT_Operand op2);
    
    /** Called when the ARM flags shall be set by a SUB operation. This sets all ARM flags. */
    public abstract void appendSubFlags(ARM_Laziness lazy, OPT_Operand result, OPT_Operand op1, OPT_Operand op2);
    public abstract void appendReverseSubFlags(ARM_Laziness lazy, OPT_RegisterOperand result, OPT_Operand lhs, OPT_Operand rhs);
  }
  
  /** Implements a flag behavior that will immediately evaluate all flag values. */
  public final class ARM_ImmediateFlagBehavior extends ARM_FlagBehavior {

    @Override
    public void appendAddFlags(ARM_Laziness lazy, OPT_Operand result, OPT_Operand op1, OPT_Operand op2) {
      
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Zero), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Carry), result.copy(), op1.copy(), OPT_ConditionOperand.LOWER(), new OPT_BranchProfileOperand()));
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Negative), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
      
      if (ARM_Options.useOptimizedFlags) {
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Overflow), op1.copy(), op2.copy(), OPT_ConditionOperand.OVERFLOW_FROM_ADD(), OPT_BranchProfileOperand.unlikely()));
      }
      else {
        //resolve overflow
        OPT_RegisterOperand overflow = getFlag(Flag.Overflow);
        OPT_RegisterOperand tmp1 = getTempInt(5);
        OPT_RegisterOperand tmp2 = getTempInt(6);
        OPT_RegisterOperand tmp_bool = gc.temps.makeTempBoolean();

        appendInstruction(Binary.create(INT_SUB, tmp1.copyRO(), new OPT_IntConstantOperand(Integer.MAX_VALUE), op2.copy()));
        appendInstruction(Binary.create(INT_SUB, tmp2.copyRO(), new OPT_IntConstantOperand(Integer.MIN_VALUE), op2.copy()));
        appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, tmp_bool.copyRO(), op2.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.GREATER_EQUAL(), new OPT_BranchProfileOperand(), op1.copy(), tmp1.copy(), OPT_ConditionOperand.GREATER(), OPT_BranchProfileOperand.unlikely()));
        appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, overflow.copyRO(), op2.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS() , new OPT_BranchProfileOperand(), op1.copy(), tmp2.copy(), OPT_ConditionOperand.LESS(), OPT_BranchProfileOperand.unlikely()));
        appendInstruction(Binary.create(INT_OR, overflow.copyRO(), overflow.copy(), tmp_bool.copy()));
      }
    }

    @Override
    public void appendLogicalFlags(ARM_Laziness lazy, OPT_Operand result) {
      
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Zero), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Negative), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
      
    }

    @Override
    public void appendSubFlags(ARM_Laziness lazy, OPT_Operand result, OPT_Operand op1, OPT_Operand op2) {
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Zero), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Negative), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));      
      
      if (ARM_Options.useOptimizedFlags) {
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Carry), op1.copy(), op2.copy(), OPT_ConditionOperand.BORROW_FROM_SUB().flipCode(), new OPT_BranchProfileOperand()));
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Overflow), op1.copy(), op2.copy(), OPT_ConditionOperand.OVERFLOW_FROM_SUB(), OPT_BranchProfileOperand.unlikely()));
      }
      else {
        //resolve carry
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, getFlag(Flag.Carry), op1.copy(), op2.copy(), OPT_ConditionOperand.LOWER().flipCode(), new OPT_BranchProfileOperand()));

        //resolve overflow
        OPT_RegisterOperand overflow = getFlag(Flag.Overflow);
        OPT_RegisterOperand tmp1 = getTempInt(5);
        OPT_RegisterOperand tmp2 = getTempInt(6);
        OPT_RegisterOperand tmp_bool = gc.temps.makeTempBoolean();

        appendInstruction(Binary.create(INT_ADD, tmp1.copyRO(), new OPT_IntConstantOperand(Integer.MIN_VALUE), op2.copy()));
        appendInstruction(Binary.create(INT_ADD, tmp2.copyRO(), new OPT_IntConstantOperand(Integer.MAX_VALUE), op2.copy()));
        
        appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, tmp_bool.copyRO(), op2.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.GREATER_EQUAL(), new OPT_BranchProfileOperand(), op1.copy(), tmp1.copy(), OPT_ConditionOperand.LESS(), OPT_BranchProfileOperand.unlikely()));
        appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, overflow.copyRO(), op2.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS() , new OPT_BranchProfileOperand(), op1.copy(), tmp2.copy(), OPT_ConditionOperand.GREATER(), OPT_BranchProfileOperand.unlikely()));
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
    public void appendReverseSubFlags(ARM_Laziness lazy, OPT_RegisterOperand result, OPT_Operand op1, OPT_Operand op2) {
      appendSubFlags(lazy, result, op2, op1);
    }
  }
  
  /** Implements a flag behavior that will use lazy evaluation to only determine a flag value
   * when it is necessary. */
  public final class ARM_LazyFlagBehavior extends ARM_FlagBehavior {
    
    /** Operands for lazy evaluation of condition codes. */
    private OPT_Register lazyOperand1;
    private OPT_Register lazyOperand2;
    private OPT_Register lazyLogicalOperand;
    
    public ARM_LazyFlagBehavior() {
      //prepare the laziness registers
      lazyOperand1 = makeTemp(VM_TypeReference.Int).register;
      lazyOperand2 = makeTemp(VM_TypeReference.Int).register;
      lazyLogicalOperand = makeTemp(VM_TypeReference.Int).register;
    }

    @Override
    public void appendAddFlags(ARM_Laziness lazy, OPT_Operand result, OPT_Operand op1, OPT_Operand op2) {

      appendInstruction(Move.create(INT_MOVE, new OPT_RegisterOperand(lazyOperand1, VM_TypeReference.Int), op1.copy()));
      appendInstruction(Move.create(INT_MOVE, new OPT_RegisterOperand(lazyOperand2, VM_TypeReference.Int), op2.copy()));
      
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
    public void appendLogicalFlags(ARM_Laziness lazy, OPT_Operand result) {
      appendInstruction(Move.create(INT_MOVE, new OPT_RegisterOperand(lazyLogicalOperand, VM_TypeReference.Int), result.copy()));
      
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
    public void appendSubFlags(ARM_Laziness lazy, OPT_Operand result, OPT_Operand op1, OPT_Operand op2) {
      
      appendInstruction(Move.create(INT_MOVE, new OPT_RegisterOperand(lazyOperand1, VM_TypeReference.Int), op1.copy()));
      appendInstruction(Move.create(INT_MOVE, new OPT_RegisterOperand(lazyOperand2, VM_TypeReference.Int), op2.copy()));
      
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
      
      OPT_RegisterOperand flagRegister = getFlag(flag);
      OPT_RegisterOperand op1 = new OPT_RegisterOperand(lazyOperand1, VM_TypeReference.Int);
      OPT_RegisterOperand op2 = new OPT_RegisterOperand(lazyOperand2, VM_TypeReference.Int);
      OPT_RegisterOperand result;
      
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
        result = new OPT_RegisterOperand(lazyLogicalOperand, VM_TypeReference.Int);
        break;
        
      default:
        throw new RuntimeException("Unhandled laziness operation: " + lazy.getOperation());
      }
      
      switch (flag) {
      case Zero:
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
        break;
        
      case Carry:
        switch (lazy.getOperation()) {
        case LogicalOpAfterAdd:
        case Add: 
          appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, result.copy(), op1.copy(), OPT_ConditionOperand.LOWER(), new OPT_BranchProfileOperand()));
          break;
        
        case LogicalOpAfterSub:
        case Sub:

          if (ARM_Options.useOptimizedFlags) {
            appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, op1.copy(), op2.copy(), OPT_ConditionOperand.BORROW_FROM_SUB().flipCode(), new OPT_BranchProfileOperand()));
          }
          else {
            appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, op1.copy(), op2.copy(), OPT_ConditionOperand.LOWER().flipCode(), new OPT_BranchProfileOperand()));
          }
          break;
          
        default:
          throw new RuntimeException("Unhandled laziness operation: " + lazy.getOperation());
        }
        break;
        
      case Negative:
        appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
        break;
        
      case Overflow:
        switch (lazy.getOperation()) {
        case Add: 
        case LogicalOpAfterAdd:
          if (ARM_Options.useOptimizedFlags) {
            appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, op1.copy(), op2.copy(), OPT_ConditionOperand.OVERFLOW_FROM_ADD(), OPT_BranchProfileOperand.unlikely()));
          }
          else {
            OPT_RegisterOperand tmp1 = getTempInt(5);
            OPT_RegisterOperand tmp2 = getTempInt(6);
            OPT_RegisterOperand tmp_bool = gc.temps.makeTempBoolean();

            appendInstruction(Binary.create(INT_SUB, tmp1.copyRO(), new OPT_IntConstantOperand(Integer.MAX_VALUE), op2.copy()));
            appendInstruction(Binary.create(INT_SUB, tmp2.copyRO(), new OPT_IntConstantOperand(Integer.MIN_VALUE), op2.copy()));
            appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, tmp_bool.copyRO(), op2.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.GREATER_EQUAL(), new OPT_BranchProfileOperand(), op1.copy(), tmp1.copy(), OPT_ConditionOperand.GREATER(), OPT_BranchProfileOperand.unlikely()));
            appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, getFlag(Flag.Overflow), op2.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS() , new OPT_BranchProfileOperand(), op1.copy(), tmp2.copy(), OPT_ConditionOperand.LESS(), OPT_BranchProfileOperand.unlikely()));
            appendInstruction(Binary.create(INT_OR, flagRegister, getFlag(Flag.Overflow), tmp_bool.copy()));
          }
          break;
          
        case Sub:
        case LogicalOpAfterSub:
          if (ARM_Options.useOptimizedFlags) {
            appendInstruction(BooleanCmp.create(BOOLEAN_CMP_INT, flagRegister, op1.copy(), op2.copy(), OPT_ConditionOperand.OVERFLOW_FROM_SUB(), OPT_BranchProfileOperand.unlikely()));
          }
          else {
            //resolve overflow
            OPT_RegisterOperand tmp1 = getTempInt(5);
            OPT_RegisterOperand tmp2 = getTempInt(6);
            OPT_RegisterOperand tmp_bool = gc.temps.makeTempBoolean();

            appendInstruction(Binary.create(INT_ADD, tmp1.copyRO(), new OPT_IntConstantOperand(Integer.MIN_VALUE), op2.copy()));
            appendInstruction(Binary.create(INT_ADD, tmp2.copyRO(), new OPT_IntConstantOperand(Integer.MAX_VALUE), op2.copy()));
            
            appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, tmp_bool.copyRO(), op2.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.GREATER_EQUAL(), new OPT_BranchProfileOperand(), op1.copy(), tmp1.copy(), OPT_ConditionOperand.LESS(), OPT_BranchProfileOperand.unlikely()));
            appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, flagRegister, op2.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS() , new OPT_BranchProfileOperand(), op1.copy(), tmp2.copy(), OPT_ConditionOperand.GREATER(), OPT_BranchProfileOperand.unlikely()));
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
    public void appendReverseSubFlags(ARM_Laziness lazy, OPT_RegisterOperand result, OPT_Operand op1, OPT_Operand op2) {
      appendSubFlags(lazy, result, op2, op1);
    }
  }

  @Override
  protected Laziness createInitialLaziness() {
    return new ARM_Laziness();
  }
  
  private VM_TypeReference OperatingMode_TypeRef = null;
  
  /**
   * Returns a register operand for values of type <code>OperatingMode</code>.
   * @return
   */
  public OPT_RegisterOperand getTempOperatingMode() {
    if (OperatingMode_TypeRef == null) {
      OperatingMode_TypeRef = VM_TypeReference.findOrCreate(ARM_Registers.OperatingMode.class);
    }
    
    return gc.temps.makeTemp(OperatingMode_TypeRef);
  }
  
  /**
   * Returns an register operand that is equivalent to the given operating mode.
   */
  public OPT_RegisterOperand getTempOperatingMode(OperatingMode mode) {
    //We are going to insert a GETFIELD, which will grab the appropriate OperatingMode static instance from
    //the OperatingMode class.
    OPT_RegisterOperand result = getTempOperatingMode();
    
    //the type reference should have been initialized by the previous call to getTempOperatingMode.
    //For the sake of defensive programming, we are doing it again here...
    if (OperatingMode_TypeRef == null) {
      OperatingMode_TypeRef = VM_TypeReference.findOrCreate(ARM_Registers.OperatingMode.class);
    }

    //grab the field reference to the corresponding field
    VM_FieldReference requestedMode_FieldReference = VM_FieldReference.findOrCreate(OperatingMode_TypeRef, VM_Atom.findOrCreateAsciiAtom(mode.name()), VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/arm/os/process/ARM_Registers/OperatingMode;")).asFieldReference();
    
    //Finally, use a getfield to grab the (static) member field 
    appendInstruction(GetField.create(GETFIELD, result,
        null, new OPT_AddressConstantOperand(requestedMode_FieldReference
            .peekResolvedField().getOffset()), new OPT_LocationOperand(
                requestedMode_FieldReference), new OPT_TrueGuardOperand()));
    
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
  public OPT_RegisterOperand getArmRegistersReference() {
    OPT_RegisterOperand ps_registersOp;
    
    if (ps_registers == null) {
      ps_registersOp = gc.temps.makeTemp(registersTref);
      ps_registers = ps_registersOp.register;
      appendInstruction(GetField.create(GETFIELD, ps_registersOp,
          gc.makeLocal(1, psTref), new OPT_AddressConstantOperand(registersFref
              .peekResolvedField().getOffset()), new OPT_LocationOperand(
              registersFref), new OPT_TrueGuardOperand()));
    }
    else {
      ps_registersOp = new OPT_RegisterOperand(ps_registers, registersTref);
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

    OPT_RegisterOperand ps_registersOp = getArmRegistersReference();
    OPT_RegisterOperand flag;
    
    //store the carry flag
    if (carryUsed) {
      flag = new OPT_RegisterOperand(carryFlag, VM_TypeReference.Boolean);
      appendInstruction(PutField.create(PUTFIELD, flag, ps_registersOp.copyRO(), new OPT_AddressConstantOperand(registers_carryFlag_Fref.peekResolvedField().getOffset()), new OPT_LocationOperand(registers_carryFlag_Fref), new OPT_TrueGuardOperand()) );
    }
    
    //store the negative flag
    if (negativeUsed) {
      flag = new OPT_RegisterOperand(negativeFlag, VM_TypeReference.Boolean);
      appendInstruction(PutField.create(PUTFIELD, flag, ps_registersOp.copyRO(), new OPT_AddressConstantOperand(registers_negativeFlag_Fref.peekResolvedField().getOffset()), new OPT_LocationOperand(registers_negativeFlag_Fref), new OPT_TrueGuardOperand()) );
    }
    
    //store the zero flag
    if (zeroUsed) {
      flag = new OPT_RegisterOperand(zeroFlag, VM_TypeReference.Boolean);
      appendInstruction(PutField.create(PUTFIELD, flag, ps_registersOp.copyRO(), new OPT_AddressConstantOperand(registers_zeroFlag_Fref.peekResolvedField().getOffset()), new OPT_LocationOperand(registers_zeroFlag_Fref), new OPT_TrueGuardOperand()) );
    }

    //store the overflow flag
    if (overflowUsed) {
      flag = new OPT_RegisterOperand(overflowFlag, VM_TypeReference.Boolean);
      appendInstruction(PutField.create(PUTFIELD, flag, ps_registersOp.copyRO(), new OPT_AddressConstantOperand(registers_overflowFlag_Fref.peekResolvedField().getOffset()), new OPT_LocationOperand(registers_overflowFlag_Fref), new OPT_TrueGuardOperand()) );
    }
  }
  
  public void fillAllFlags() {

    //get an operand that contains a reference to the current ps.registers field.
    OPT_RegisterOperand ps_registersOp = getArmRegistersReference();
    
    if (carryFlag == null) {
      carryFlag = gc.temps.getReg(VM_TypeReference.Boolean);
      negativeFlag = gc.temps.getReg(VM_TypeReference.Boolean);
      zeroFlag = gc.temps.getReg(VM_TypeReference.Boolean);
      overflowFlag = gc.temps.getReg(VM_TypeReference.Boolean);
    }

    //get the carry flag
    OPT_RegisterOperand flag = new OPT_RegisterOperand(carryFlag, VM_TypeReference.Boolean);
    appendInstruction(GetField.create(GETFIELD, flag, ps_registersOp.copyRO(), new OPT_AddressConstantOperand(registers_carryFlag_Fref.peekResolvedField().getOffset()), new OPT_LocationOperand(registers_carryFlag_Fref), new OPT_TrueGuardOperand()) );
    
    //get the negative flag
    flag = new OPT_RegisterOperand(negativeFlag, VM_TypeReference.Boolean);
    appendInstruction(GetField.create(GETFIELD, flag, ps_registersOp.copyRO(), new OPT_AddressConstantOperand(registers_negativeFlag_Fref.peekResolvedField().getOffset()), new OPT_LocationOperand(registers_negativeFlag_Fref), new OPT_TrueGuardOperand()) );
    
    //get the zero flag
    flag = new OPT_RegisterOperand(zeroFlag, VM_TypeReference.Boolean);
    appendInstruction(GetField.create(GETFIELD, flag, ps_registersOp.copyRO(), new OPT_AddressConstantOperand(registers_zeroFlag_Fref.peekResolvedField().getOffset()), new OPT_LocationOperand(registers_zeroFlag_Fref), new OPT_TrueGuardOperand()) );
    
    //get the overflow flag
    flag = new OPT_RegisterOperand(overflowFlag, VM_TypeReference.Boolean);
    appendInstruction(GetField.create(GETFIELD, flag, ps_registersOp.copyRO(), new OPT_AddressConstantOperand(registers_overflowFlag_Fref.peekResolvedField().getOffset()), new OPT_LocationOperand(registers_overflowFlag_Fref), new OPT_TrueGuardOperand()) );    
  }

  @Override
  protected void fillAllRegisters() {
    
    //get an operand that contains a reference to the current ps.registers field.
    OPT_RegisterOperand ps_registersOp = getArmRegistersReference();

    // Get the array of general purpose registers
    OPT_RegisterOperand ps_registers_regsOp;
    if (ps_registers_regs == null) {

      ps_registers_regsOp = gc.temps.makeTemp(registers_regs_Tref);
      appendInstruction(GetField.create(GETFIELD,
          ps_registers_regsOp, ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(registers_regs_Fref.peekResolvedField()
              .getOffset()), new OPT_LocationOperand(registers_regs_Fref),
          new OPT_TrueGuardOperand()));
      ps_registers_regs = ps_registers_regsOp.register;
    } else {
      ps_registers_regsOp = new OPT_RegisterOperand(ps_registers_regs, registers_regs_Tref);
    }

    // Fill general purpose registers
    for (int i = 0; i < regMap.length; i++) {
      OPT_RegisterOperand regOp;
      if (regMap[i] == null) {
        regOp = makeTemp(VM_TypeReference.Int);
        regMap[i] = regOp.register;
      } else {
        regOp = new OPT_RegisterOperand(regMap[i], VM_TypeReference.Int);
      }
      appendInstruction(ALoad.create(INT_ALOAD, regOp,
          ps_registers_regsOp.copyRO(), new OPT_IntConstantOperand(i),
          new OPT_LocationOperand(VM_TypeReference.Int),
          new OPT_TrueGuardOperand()));
    }
    
    //fill all flags from the process space
    fillAllFlags();
  }
  
  public OPT_RegisterOperand getRegister(int r) {
    regUsed[r] = true;
    return new OPT_RegisterOperand(regMap[r], VM_TypeReference.Int);
  }
  
  public OPT_Operand readCarryFlag(ARM_Laziness lazy) {
    carryUsed = true;
    flagBehavior.onFlagRead(Flag.Carry, lazy);
    return new OPT_RegisterOperand(carryFlag, VM_TypeReference.Boolean);
  }
  
  public OPT_Operand readZeroFlag(ARM_Laziness lazy) {
    zeroUsed = true;
    flagBehavior.onFlagRead(Flag.Zero, lazy);
    return new OPT_RegisterOperand(zeroFlag, VM_TypeReference.Boolean);
  }
  
  public OPT_Operand readNegativeFlag(ARM_Laziness lazy) {
    negativeUsed = true;
    flagBehavior.onFlagRead(Flag.Negative, lazy);
    return new OPT_RegisterOperand(negativeFlag, VM_TypeReference.Boolean);
  }
  
  public OPT_Operand readOverflowFlag(ARM_Laziness lazy) {
    overflowUsed = true;
    flagBehavior.onFlagRead(Flag.Overflow, lazy);
    return new OPT_RegisterOperand(overflowFlag, VM_TypeReference.Boolean);
  }
  
  public OPT_RegisterOperand writeCarryFlag(ARM_Laziness lazy) {
    carryUsed = true;
    flagBehavior.onFlagWrite(Flag.Carry, lazy);
    return new OPT_RegisterOperand(carryFlag, VM_TypeReference.Boolean);
  }
  
  public OPT_RegisterOperand writeZeroFlag(ARM_Laziness lazy) {
    zeroUsed = true;
    flagBehavior.onFlagWrite(Flag.Zero, lazy);
    return new OPT_RegisterOperand(zeroFlag, VM_TypeReference.Boolean);
  }
  
  public OPT_RegisterOperand writeNegativeFlag(ARM_Laziness lazy) {
    negativeUsed = true;
    flagBehavior.onFlagWrite(Flag.Negative, lazy);
    return new OPT_RegisterOperand(negativeFlag, VM_TypeReference.Boolean);
  }
  
  public OPT_RegisterOperand writeOverflowFlag(ARM_Laziness lazy) {
    overflowUsed = true;
    flagBehavior.onFlagWrite(Flag.Overflow, lazy);
    return new OPT_RegisterOperand(overflowFlag, VM_TypeReference.Boolean);
  }
  
  public void appendLogicalFlags(ARM_Laziness lazy, OPT_Operand result) {
    zeroUsed = negativeUsed = true;
    flagBehavior.appendLogicalFlags(lazy, result);
  }
  
  public void appendSubFlags(ARM_Laziness lazy, OPT_Operand result, OPT_Operand op1, OPT_Operand op2) {
    zeroUsed = negativeUsed = carryUsed = overflowUsed = true;
    flagBehavior.appendSubFlags(lazy, result, op1, op2);
  }
  

  public void appendReverseSubFlags(ARM_Laziness lazy, OPT_RegisterOperand result, OPT_Operand lhs, OPT_Operand rhs) {
    zeroUsed = negativeUsed = carryUsed = overflowUsed = true;
    flagBehavior.appendReverseSubFlags(lazy, result, lhs, rhs);    
  }
  
  public void appendAddFlags(ARM_Laziness lazy, OPT_Operand result, OPT_Operand op1, OPT_Operand op2) {
    zeroUsed = negativeUsed = carryUsed = overflowUsed = true;
    flagBehavior.appendAddFlags(lazy, result, op1, op2);
  }
   
  @Override
  protected OPT_Register[] getUnusedRegisters() {
    
    ArrayList<OPT_Register> unusedRegisters = new ArrayList<OPT_Register>();
    
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
    
    return unusedRegisters.toArray(new OPT_Register[unusedRegisters.size()]);
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
    OPT_RegisterOperand ps_registers_regsOp = new OPT_RegisterOperand(
        ps_registers_regs, registers_regs_Tref);
    for (int i = 0; i < regMap.length; i++) {
      // We can save spills if the register was never used
      if (regUsed[i] == true) {
        appendInstruction(AStore.create(INT_ASTORE,
            new OPT_RegisterOperand(regMap[i], VM_TypeReference.Int),
            ps_registers_regsOp.copyRO(), new OPT_IntConstantOperand(i),
            new OPT_LocationOperand(VM_TypeReference.Int),
            new OPT_TrueGuardOperand()));
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
  public void appendRotateRight(OPT_RegisterOperand result, OPT_Operand rotatedOperand, OPT_Operand rotation) {
    VM_TypeReference IntegerType = VM_TypeReference
        .findOrCreate(Integer.class);

    VM_MethodReference rotateRightMethodRef = VM_MemberReference
        .findOrCreate(IntegerType,
            VM_Atom.findOrCreateAsciiAtom("rotateRight"),
            VM_Atom.findOrCreateAsciiAtom("(II)I")).asMethodReference();

    VM_Method rotateRightMethod = rotateRightMethodRef.resolve();

    OPT_Instruction s = Call.create(CALL, null, null, null, null, 2);
    OPT_MethodOperand methOp = OPT_MethodOperand
        .STATIC(rotateRightMethod);

    Call.setParam(s, 0, rotatedOperand);
    Call.setParam(s, 1, rotation);
    Call.setResult(s, result);
    Call.setGuard(s, new OPT_TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new OPT_AddressConstantOperand(rotateRightMethod
        .getOffset()));

    appendInstruction(s);
  }

  protected void appendBitTest(OPT_RegisterOperand target, OPT_Operand wordToTest, OPT_Operand bit) {
    if (DBT.VerifyAssertions) DBT._assert(wordToTest != target && bit != target);
  
    appendInstruction(Binary.create(OPT_Operators.INT_SHL, target, new OPT_IntConstantOperand(1), bit));
    appendInstruction(Binary.create(OPT_Operators.INT_AND, target, wordToTest, target));
    appendInstruction(BooleanCmp.create(OPT_Operators.BOOLEAN_CMP_INT, target, target, new OPT_IntConstantOperand(0), OPT_ConditionOperand.NOT_EQUAL(), new OPT_BranchProfileOperand()));
  }

  protected void appendBitTest(OPT_RegisterOperand target, OPT_Operand wordToTest, int bit) {
    if (DBT.VerifyAssertions) DBT._assert(wordToTest != target);
    if (DBT.VerifyAssertions) DBT._assert(bit <= 31 && bit >= 0);
    
    appendInstruction(Binary.create(OPT_Operators.INT_AND, target, wordToTest, new OPT_IntConstantOperand(1 << bit)));
    appendInstruction(BooleanCmp.create(OPT_Operators.BOOLEAN_CMP_INT, target, target, new OPT_IntConstantOperand(0), OPT_ConditionOperand.NOT_EQUAL(), new OPT_BranchProfileOperand()));
  }


}
