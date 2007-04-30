package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder.ARM_InstructionFactory;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.OperandWrapper;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.DataProcessing.Opcode;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.Instruction.Condition;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.arch.arm.os.process.ARM_Registers.OperatingMode;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.*;

/**
 */
public class ARM_Translator implements OPT_Operators {

  /** The process space that we're interpreting.*/
  protected final ARM_ProcessSpace ps;
  
  protected final ARM2IR arm2ir;
  
  protected ARM_Laziness lazy;
  
  protected int pc;
  
  /** A "quick" pointer to the ARM registers within the process space*/
  protected final ARM_Registers regs = null;

  public ARM_Translator(ARM_ProcessSpace ps, ARM2IR arm2ir) {
    this.ps = ps;
    this.arm2ir = arm2ir;
  }


  private abstract static class ResolvedOperand {

    protected OPT_Operand value;

    protected ARM_Translator translator;

    public static ResolvedOperand resolveWithShifterCarryOut(
        ARM_Translator translator, OperandWrapper operand) {
      throw new RuntimeException("Not yet implemented");
    }

    public static OPT_Operand resolve(ARM_Translator translator,
        OperandWrapper operand) {
      ResolvedOperand result = new ResolvedOperand_WithoutShifterCarryOut(
          translator, operand);
      return result.getValue();
    }

    public final OPT_Operand getValue() {
      return value;
    }

    public abstract OPT_Operand getShifterCarryOut();

    protected OPT_RegisterOperand getTempInt() {
      return translator.arm2ir.getTempInt(0);
    }

    protected OPT_RegisterOperand getTempValidation() {
      return translator.arm2ir.getGenerationContext().temps
          .makeTempValidation();
    }

    private static class ResolvedOperand_WithoutShifterCarryOut extends
        ResolvedOperand {

      private ResolvedOperand_WithoutShifterCarryOut(ARM_Translator translator,
          OperandWrapper operand) {
        this.translator = translator;
        _resolve(operand);
      }

      public OPT_Operand getShifterCarryOut() {
        throw new RuntimeException(
            "This class does not provide a shifter carry out value.");
      }

      private void _resolve(OperandWrapper operand) {

        switch (operand.getType()) {
        case Immediate:
          value = new OPT_IntConstantOperand(operand.getImmediate());
          return;

        case Register:
          int reg = operand.getRegister();

          if (reg == 15) {
            // mind the ARM pc offset
            value = new OPT_IntConstantOperand(translator.pc + 8);
            return;
          }

          value = translator.arm2ir.getRegister(reg);
          return;

        case RegisterShiftedRegister:
        case ImmediateShiftedRegister:
          value = resolveShift(operand);
          return;

        case PcRelative:
          value = new OPT_IntConstantOperand(translator.pc + 8
              + operand.getOffset());
          return;

        default:
          throw new RuntimeException("Unexpected wrapped operand type: "
              + operand.getType());
        }
      }

      /**
       * If the given OperandWrapper involves shifting a register, then this
       * function will decoder the shift and set the result of the barrel
       * shifter accordingly.
       */
      private final OPT_Operand resolveShift(OperandWrapper operand) {
        if (DBT.VerifyAssertions)
          DBT
              ._assert(operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister
                  || operand.getType() == OperandWrapper.Type.RegisterShiftedRegister);

        // consider the "usual" ARM program counter offset
        OPT_Operand shiftedOperand;
        if (operand.getRegister() == 15)
          shiftedOperand = new OPT_IntConstantOperand(translator.pc + 8);
        else
          shiftedOperand = translator.arm2ir.getRegister(operand.getRegister());

        OPT_Operand shiftAmount;

        if (operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister) {
          // the amount of shift is a constant
          shiftAmount = new OPT_IntConstantOperand(operand.getShiftAmount());
        } else {
          // the amount of shifting is determined by a register
          shiftAmount = translator.arm2ir.getRegister(operand
              .getShiftingRegister());
        }

        OPT_BasicBlock currentBlock = translator.arm2ir.getCurrentBlock();
        OPT_BasicBlock nextBlock = translator.arm2ir.getNextBlock();

        OPT_BasicBlock ifBlock;
        OPT_BasicBlock elseBlock;

        OPT_RegisterOperand resultRegister = getTempInt();

        switch (operand.getShiftType()) {
        case ASR:
          /*
           * if (shiftAmout >= 32) { value = shiftedOperand >> 31; else value =
           * shiftedOperand >> shiftAmount;
           */
          ifBlock = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          elseBlock = translator.arm2ir.createBlockAfterCurrent();
          ifBlock.insertOut(nextBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(IfCmp.create(
              INT_IFCMP, getTempValidation(), shiftAmount,
              new OPT_IntConstantOperand(32), OPT_ConditionOperand
                  .GREATER_EQUAL(), ifBlock.makeJumpTarget(),
              OPT_BranchProfileOperand.unlikely()));
          currentBlock.insertOut(ifBlock);

          translator.arm2ir.setCurrentBlock(ifBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(Binary.create(
              INT_SHR, resultRegister, shiftedOperand,
              new OPT_IntConstantOperand(31)));

          translator.arm2ir.setCurrentBlock(elseBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(Binary.create(
              INT_SHR, resultRegister, shiftedOperand, shiftAmount));

          return resultRegister;

        case LSL:
          /*
           * if (shiftAmout >= 32) { value = 0; else value = shiftedOperand <<
           * shiftAmount;
           */
          ifBlock = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          elseBlock = translator.arm2ir.createBlockAfterCurrent();
          ifBlock.insertOut(nextBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(IfCmp.create(
              INT_IFCMP, getTempValidation(), shiftAmount,
              new OPT_IntConstantOperand(32), OPT_ConditionOperand
                  .GREATER_EQUAL(), ifBlock.makeJumpTarget(),
              OPT_BranchProfileOperand.unlikely()));
          currentBlock.insertOut(ifBlock);

          translator.arm2ir.setCurrentBlock(ifBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(Unary.create(
              INT_MOVE, resultRegister, new OPT_IntConstantOperand(0)));

          translator.arm2ir.setCurrentBlock(elseBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(Binary.create(
              INT_SHL, resultRegister, shiftedOperand, shiftAmount));

          return resultRegister;

        case LSR:

          /*
           * if (shiftAmout >= 32) { value = 0; else value = shiftedOperand >>>
           * shiftAmount;
           */
          ifBlock = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          elseBlock = translator.arm2ir.createBlockAfterCurrent();
          ifBlock.insertOut(nextBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(IfCmp.create(
              INT_IFCMP, getTempValidation(), shiftAmount,
              new OPT_IntConstantOperand(32), OPT_ConditionOperand
                  .GREATER_EQUAL(), ifBlock.makeJumpTarget(),
              OPT_BranchProfileOperand.unlikely()));
          currentBlock.insertOut(ifBlock);

          translator.arm2ir.setCurrentBlock(ifBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(Unary.create(
              INT_MOVE, resultRegister, new OPT_IntConstantOperand(0)));

          translator.arm2ir.setCurrentBlock(elseBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(Binary.create(
              INT_USHR, resultRegister, shiftedOperand, shiftAmount));

          return resultRegister;

        case ROR:
          /*
           * return Integer.rotateRight(value, shiftAmount);
           */
          translator.arm2ir.appendRotateRight(resultRegister, shiftedOperand, shiftAmount);
          return resultRegister;

        case RRE:
          /*
           * if (regs.isCarrySet()) return (resultRegister >> 1) | 0x80000000;
           * else return resultRegister >> 1;
           */

          // resultRegister = resultRegister >> 1
          translator.arm2ir.appendInstructionToCurrentBlock(Unary.create(
              INT_USHR_ACC, resultRegister, new OPT_IntConstantOperand(1)));

          ifBlock = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          ifBlock.insertOut(nextBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(IfCmp.create(
              INT_IFCMP, getTempValidation(), shiftAmount,
              new OPT_IntConstantOperand(32), OPT_ConditionOperand
                  .GREATER_EQUAL(), ifBlock.makeJumpTarget(),
              OPT_BranchProfileOperand.unlikely()));
          currentBlock.insertOut(ifBlock);

          translator.arm2ir.setCurrentBlock(ifBlock);
          translator.arm2ir.appendInstructionToCurrentBlock(Unary.create(
              INT_AND_ACC, resultRegister, new OPT_IntConstantOperand(
                  0x80000000)));

          return resultRegister;

        default:
          throw new RuntimeException("Unexpected shift type: "
              + operand.getShiftType());
        }
      }
    }
  }

  /** All ARM interpreter instructions implement this interface. */
  private interface ARM_Instruction  {
    /** Returns the condition, under which the given instruction will be executed. */
    Condition getCondition();
    
    /** performs the actual translation.*/
    void translate();
  }

  /** All ARM instructions that are supposed to be executed conditionally 
   * are decorated with this decorator. 
   * The decorator takes care of checking the individual condition and depending on it, executing the
   * instruction (or not). The instruction classes itself do not check any conditions. */
  private final class ConditionalDecorator implements ARM_Instruction {

    protected final ARM_Instruction conditionalInstruction;

    /** Decorates an ARM interpreter instruction, by making it execute conditionally. */
    protected ConditionalDecorator(ARM_Instruction i) {
      conditionalInstruction = i;
    }
    
    public void translate() {
      throw new RuntimeException("Not yet implemented");
    }

    public Condition getCondition() {
      return conditionalInstruction.getCondition();
    }
    
   /** Return true if the condition required by the conditional instruction is fulfilled, false otherwise.*/
    private boolean isConditionTrue() {
      switch (conditionalInstruction.getCondition()) {
      case AL:
        throw new RuntimeException("ARM32 instructions with a condition of AL (always) should not be decorated with a ConditionalDecorator.");
        
      case CC:
        return !regs.isCarrySet();
        
      case CS:
        return regs.isCarrySet();
        
      case EQ:
        return regs.isZeroSet();
        
      case GE:
        return regs.isNegativeSet() == regs.isOverflowSet();
        
      case GT:
        return (regs.isNegativeSet() == regs.isOverflowSet()) && !regs.isZeroSet();
        
      case HI:
        return regs.isCarrySet() && !regs.isZeroSet();
        
      case LE:
        return regs.isZeroSet() || (regs.isNegativeSet() != regs.isOverflowSet());
        
      case LS:
        return !regs.isCarrySet() || regs.isZeroSet();
        
      case LT:
        return regs.isNegativeSet() != regs.isOverflowSet();
        
      case MI:
        return regs.isNegativeSet();
        
      case NE:
        return !regs.isZeroSet();
        
      case NV:
        return false;
        
      case PL:
        return !regs.isNegativeSet();
        
      case VC:
        return !regs.isOverflowSet();
        
      case VS:
        return regs.isOverflowSet();
        
        default:
          throw new RuntimeException("Unexpected condition code: " + conditionalInstruction.getCondition());
      }
    }
    
    @Override
    public String toString() {
      return conditionalInstruction.toString();
    }
  }

  /** A base class for all data processing interpreter instructions, including CLZ.*/
  private abstract class DataProcessing extends ARM_Instructions.DataProcessing
      implements ARM_Instruction {

    protected DataProcessing(int instr) {
      super(instr);
    }

    /** Returns the value of operand 1 of the data processing instruction. This is always a register value. */
    protected OPT_Operand resolveOperand1() {

      if (Rn == ARM_Registers.PC) {
        return new OPT_IntConstantOperand(pc + 8);
      }

      return arm2ir.getRegister(Rn);
    }

    /** Returns the value of the rhs-operand of the data processing instruction. */
    protected OPT_Operand resolveOperand2() {
      return ResolvedOperand.resolve(ARM_Translator.this, operand2);
    }
    
    /** Returns teh register into which the result of a data processing operation shall be stored. */
    protected OPT_RegisterOperand getResultRegister() {
      return arm2ir.getRegister(Rd);
    }
    
    /** Sets the result of an operation. */
    protected void setResult(OPT_RegisterOperand result) {
      if (Rd == 15) {
        //TODO: This is a jump, handle it accordingly
        throw new RuntimeException("Not yet implemented");
      }
    }

    public abstract void translate();

    /** Sets the processor flags according to the result of adding <code>lhs</code> and <code>rhs</code>.*/
    protected final void setFlagsForAdd(OPT_Operand result, OPT_Operand lhs, OPT_Operand rhs) {

      if (updateConditionCodes) {
        if (Rd != 15) {

          //set the carry flag
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getCarryFlag(), lhs, rhs, OPT_ConditionOperand.CARRY_FROM_ADD(), new OPT_BranchProfileOperand()));
          
          //set the overflow flag
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getOverflowFlag(), lhs, rhs, OPT_ConditionOperand.OVERFLOW_FROM_ADD(), OPT_BranchProfileOperand.unlikely()));
          
          //set the negative flag
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
          
          //set the zero flag
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
        } 
        else {
          //TODO: Implement this
          regs.restoreSPSR2CPSR();
          throw new RuntimeException("Not yet implemented");
        }
      }
    }
    
    /** Sets the processor flags according to the result of subtracting <code>rhs</code> from <code>lhs</code>.*/
    protected final void setFlagsForSub(OPT_Operand result, OPT_Operand lhs, OPT_Operand rhs) {

      if (updateConditionCodes) {
        if (Rd != 15) {
          //set the carry flag to not(Borrow)
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getCarryFlag(), lhs, rhs, OPT_ConditionOperand.BORROW_FROM_SUB(), new OPT_BranchProfileOperand()));
          arm2ir.appendInstructionToCurrentBlock(Unary.create(BOOLEAN_NOT, arm2ir.getCarryFlag(), arm2ir.getCarryFlag()));
          
          //set the overflow flag
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getOverflowFlag(), lhs, rhs, OPT_ConditionOperand.OVERFLOW_FROM_SUB(), OPT_BranchProfileOperand.unlikely()));
          
          //set the negative flag
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
          
          //set the zero flag
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
        } 
        else {
          //TODO: Implement this
          regs.restoreSPSR2CPSR();
          throw new RuntimeException("Not yet implemented");
        }
      }
    }

    public int getSuccessor(int pc) {
      if (Rd != 15)
        return pc + 4;
      else
        return -1;
    }
  }
  
  private abstract class DataProcessing_Logical extends DataProcessing {
    
    /** Most data processing instructions may set the carry flag according to the barrel shifter's carry
     * out value. The (supposed) value of the barrel shifter is stored within this variable. */
    protected OPT_Operand shifterCarryOut;
    
    protected DataProcessing_Logical(int instr) {
      super(instr);
    }
    
    /** 
     * Returns the value of the rhs-operand of the data processing instruction. 
     * This function also retrieves a value for the shifter carry out, which may be set when resolving
     * the 2nd operand.*/
    protected OPT_Operand resolveOperand2() {
      ResolvedOperand resolvedOperand2 = ResolvedOperand.resolveWithShifterCarryOut(ARM_Translator.this, operand2);
      shifterCarryOut = resolvedOperand2.getShifterCarryOut();
      return resolvedOperand2.getValue();
    }
    
    /** Sets the condition field for logical operations. */
    protected final void setFlagsForLogicalOperator(OPT_Operand result) {

      if (updateConditionCodes) {
        if (Rd != 15) {
          //TODO: Find an equivalent for BOOLEAN_MOVE
          arm2ir.appendInstructionToCurrentBlock(Unary.create(BOOLEAN_NOT, arm2ir.getCarryFlag(), shifterCarryOut));
          arm2ir.appendInstructionToCurrentBlock(Unary.create(BOOLEAN_NOT, arm2ir.getCarryFlag(), arm2ir.getCarryFlag()));
          
          //set the negative flag
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
          
          //set the zero flag
          arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
              BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));          

        } else {
          //TODO: Implement this
          regs.restoreSPSR2CPSR();
          throw new RuntimeException("Not yet implemented");
        }
      }
    }
  }

  /** Binary and. <code>Rd = op1 & op2 </code>.*/
  private final class DataProcessing_And extends DataProcessing_Logical {

    protected DataProcessing_And(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, result, resolveOperand1(), resolveOperand2()));
      
      setFlagsForLogicalOperator(result);
      setResult(result);
    }
  }

  /** Exclusive or. <code>Rd = op1 ^ op2 </code>.*/
  private final class DataProcessing_Eor extends DataProcessing_Logical {

    protected DataProcessing_Eor(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_XOR, result, resolveOperand1(), resolveOperand2()));
      
      setFlagsForLogicalOperator(result);
      setResult(result);
    }
  }

  /** Add. <code>Rd = op1 + op2 </code>.*/
  private final class DataProcessing_Add extends DataProcessing {

    public DataProcessing_Add(int instr) {
      super(instr);
    }

    public void translate() {
      
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, result, operand1, operand2));
      
      setFlagsForAdd(result, operand1, operand2);
      setResult(result);
    }
  }

  /** Subtract. <code>Rd = op1 - op2 </code>.*/
  private final class DataProcessing_Sub extends DataProcessing {

    public DataProcessing_Sub(int instr) {
      super(instr);
    }
    
    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, result, operand1, operand2));
      
      setFlagsForSub(result, operand1, operand2);
      setResult(result);
    }
  }

  /** Reverse subtract. <code>Rd = op2 - op1</code>.*/
  private final class DataProcessing_Rsb extends DataProcessing {

    protected DataProcessing_Rsb(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, result, operand2, operand1));
      
      setFlagsForSub(result, operand2, operand1);
      setResult(result);
    }
  }

  /** Add with carry. <code>Rd = op1 + op2 + CARRY</code>.
   * If the carry flag is set, the instruction will add 1 to one of the operands (whichever operands would
   * not cause an overflow). Then, the normal add-routine is being invoked. */
  private final class DataProcessing_Adc extends DataProcessing {

    protected DataProcessing_Adc(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      
      throw new RuntimeException("Not yet implemented");
      
      /*
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      OPT_RegisterOperand long_op1 = arm2ir.getTempLong(0);
      OPT_RegisterOperand long_op2 = arm2ir.getTempLong(1);
      OPT_RegisterOperand long_result = arm2ir.getTempLong(2);
      OPT_RegisterOperand long_tmp = arm2ir.getTempLong(3);
      
      //convert the operands to longs. Be careful to treat them as unsigned ints during the conversion
      arm2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, long_op1, operand1));
      arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, long_op1, long_op1, new OPT_LongConstantOperand(0xFFFFFFFF)));
      arm2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, long_op2, operand2));
      arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, long_op2, long_op2, new OPT_LongConstantOperand(0xFFFFFFFF)));
      
      //perform the actual addition
      arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_ADD, long_result, long_op1, long_op2));
      arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_ADD, long_result, long_result, new OPT_LongConstantOperand(1)));
      arm2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, result, long_result));
      
      //set the carry flag if the upper 32 bit of the result are != 0
      arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, long_tmp, long_result, new OPT_LongConstantOperand(0xFFFFFFFF)));
      arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, arm2ir.getCarryFlag(), long_tmp, new OPT_LongConstantOperand(0), OPT_ConditionOperand.NOT_EQUAL(), OPT_BranchProfileOperand.unlikely()));
      
      //set the negative flag
      arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
      
      //set the zero flag
      arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      
      //commit the result
      setResult(result);*/
    }
  }

  /** Subtract with carry. <code>Rd = op1 - op2 + CARRY</code>.*/
  private class DataProcessing_Sbc extends DataProcessing {

    protected DataProcessing_Sbc(int instr) {
      super(instr);
    }
    
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      
      //TODO: Implement
      throw new RuntimeException("Not yet implemented");
      
      /*
      if (!regs.isCarrySet()) {
        if (operand1 != Integer.MIN_VALUE) {
          operand1--;
        } else if (operand2 != Integer.MIN_VALUE) {
          operand2--;
        } else {
          //TODO: Remove this exception, when the correct behavior has been verified.
          throw new RuntimeException("I'm interested in finding a case where this occurs, so this exception is sooner or later going to 'notify' me..");
          //regs.setFlags(operand1 > 0, operand1 != 0, true, true);
          //return;
        }
      }

      int result = operand1 - operand2;

      regs.set(Rd, result);
      setFlagsForSub(operand1, operand2);*/
    }
  }

  /** Reserve subtract with carry. <code>Rd = -op1 + op2 + CARRY</code>.*/
  private final class DataProcessing_Rsc extends DataProcessing_Sbc {

    protected DataProcessing_Rsc(int instr) {
      super(instr);
    }
    
    @Override
    protected OPT_Operand resolveOperand1() {
      return super.resolveOperand2();
    }
    
    @Override
    protected OPT_Operand resolveOperand2() {
      return super.resolveOperand1();
    }
  }

  /** Set the flags according to the logical-and of two values. 
   * <code>Flags = op1 & op2</code>*/
  private final class DataProcessing_Tst extends DataProcessing_Logical {

    protected DataProcessing_Tst(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = arm2ir.getTempInt(0);
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, result, resolveOperand1(), resolveOperand2()));
      setFlagsForLogicalOperator(result);
    }
  }

  /** Sets the flags according to the exclusive-or of two values.
   * <code>Flags = op1 ^ op2</code> */
  private final class DataProcessing_Teq extends DataProcessing_Logical {

    protected DataProcessing_Teq(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = arm2ir.getTempInt(0);
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_XOR, result, resolveOperand1(), resolveOperand2()));
      setFlagsForLogicalOperator(result);
    }
  }

  /** Set the flags according to the comparison of two values.
   * <code>Flags = op1 - op2</code> */
  private final class DataProcessing_Cmp extends DataProcessing {

    protected DataProcessing_Cmp(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = arm2ir.getTempInt(0);
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, result, operand1, operand2));      
      setFlagsForSub(result, operand1, operand2);
    }
  }

  /** Set the flags according to the comparison of two values, negating the 2nd value on the way.
   * <code>Flags = op1 + op2</code>. */
  private final class DataProcessing_Cmn extends DataProcessing {

    protected DataProcessing_Cmn(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = arm2ir.getTempInt(0);
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, result, operand1, operand2));      
      setFlagsForAdd(result, operand1, operand2);
    }
  }

  /** Binary or. <code>Rd = op1 | op2</code>. */
  private final class DataProcessing_Orr extends DataProcessing_Logical {

    protected DataProcessing_Orr(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, result, operand1, operand2));
      setFlagsForLogicalOperator(result);
      setResult(result);
    }
  }

  private final class DataProcessing_Mov extends DataProcessing_Logical {

    protected DataProcessing_Mov(int instr) {
      super(instr);
    }

    @Override
    /** Moves a value into a register .*/
    public void translate() {
      OPT_Operand operand = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, result, operand));
      
      setResult(result);
      setFlagsForLogicalOperator(result);
    }
  }

  /** Bit clear. Clear bits in a register by a mask given by a second operand. 
   * <code>Rd =  op1 & (~op2)</code>.*/
  private final class DataProcessing_Bic extends DataProcessing_Logical {

    protected DataProcessing_Bic(int instr) {
      super(instr);
    }

    @Override
    /** Clear bits in a register by a mask given by a second operand. */
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
      arm2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, tmp, operand2));
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, result, operand1, tmp));
      setFlagsForLogicalOperator(result);
      setResult(result);
    }
  }

  /** Move and negate. Moves an integer between two registers, negating it on the way. 
   * <code>Rd = -op2</code>.*/
  private final class DataProcessing_Mvn extends DataProcessing_Logical {

    protected DataProcessing_Mvn(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = getResultRegister();
      arm2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, result, resolveOperand2()));
      setFlagsForLogicalOperator(result);
    }
  }
  
  /** Count the number of leading zeros in an integer.
   * <code>Rd = Number_Of_Leading_Zeroes(op2) </code> */
  private final class DataProcessing_Clz extends DataProcessing {

    protected DataProcessing_Clz(int instr) {
      super(instr);
    }

    @Override
    public void translate() {
      
      OPT_RegisterOperand result = getResultRegister();

      VM_TypeReference IntegerType = VM_TypeReference
          .findOrCreate(Integer.class);

      VM_MethodReference clzMethodRef = VM_MemberReference
          .findOrCreate(IntegerType,
              VM_Atom.findOrCreateAsciiAtom("numberOfLeadingZeros"),
              VM_Atom.findOrCreateAsciiAtom("(I)I")).asMethodReference();

      VM_Method clzMethod = clzMethodRef.resolve();

      OPT_Instruction s = Call.create(CALL, null, null, null, null, 1);
      OPT_MethodOperand methOp = OPT_MethodOperand.STATIC(clzMethod);

      Call.setParam(s, 1, resolveOperand2());
      Call.setResult(s, result);
      Call.setGuard(s, new OPT_TrueGuardOperand());
      Call.setMethod(s, methOp);
      Call.setAddress(s, new OPT_AddressConstantOperand(clzMethod
          .getOffset()));

      arm2ir.appendInstructionToCurrentBlock(s);
      setResult(result);
    }
  }

  /** Swap a register and a memory value. 
   * TODO: At the moment, Pearcolator does not support any way of locking the memory. However, once it does
   * any other memory accesses should be pending until the swap instruction succeeds.*/
  private final class Swap extends ARM_Instructions.Swap implements
  ARM_Instruction {

    public Swap(int instr) {
      super(instr);
    }

    public void translate() {
      OPT_Operand memAddr = arm2ir.getRegister(Rn);
      OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
      OPT_RegisterOperand result = arm2ir.getRegister(Rd);
      
      //swap exchanges the value of a memory address with the value in a register
      if (!swapByte) {
        ps.memory.translateLoad32(memAddr, tmp);
        ps.memory.translateStore32(memAddr, arm2ir.getRegister(Rm));
        
        //according to the ARM architecture reference, the value loaded from a memory address is rotated
        //by the number of ones in the first two bits of the address
        OPT_RegisterOperand rotation = arm2ir.getTempInt(1);
        
        //rotation = (memAddr & 0x3) * 8
        arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, rotation, memAddr, new OPT_IntConstantOperand(0x3)));
        arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, rotation, rotation, new OPT_IntConstantOperand(3))); 
        arm2ir.appendRotateRight(result, tmp, rotation);
      }
      else {
        ps.memory.translateLoadUnsigned8(memAddr, tmp);
        ps.memory.translateStore8(memAddr, arm2ir.getRegister(Rm));
        arm2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, result, tmp));
      }
    }

    public int getSuccessor(int pc) {
      //according to the ARM Architecture reference, using the pc as Rd yields an undefined
      //result. Therefore, we can safely assume that this instruction never equals a branch
      return pc + 4;
    }
  }

  /** Transfer multiple registers at once between the register bank and the memory. */
  private final class BlockDataTransfer extends ARM_Instructions.MultipleDataTransfer
      implements ARM_Instruction {

    /** the lowest address that we're reading a register from / writing a register to */
    private final int registerCount;

    /** An array that contains the registers to be transferd in ascending order. 
     * The list is delimited by setting the entry after the last register index to -1.
     * The PC is not included in this list, if it shall be transferred.  */
    private final int[] registersToTransfer = new int[16];

    /** True if the PC should be transferred to, false otherwise. */
    private final boolean transferPC;

    public BlockDataTransfer(int instr) {
      super(instr);

      transferPC = transferRegister(15);
      int regCount = 0;

      for (int i = 0; i <= 14; i++)
        if (transferRegister(i)) {
          registersToTransfer[regCount++] = i;
        }

      registersToTransfer[regCount] = -1;      
      registerCount = regCount;
    }

    public void translate() {
      //build the address, which generally ignores the last two bits
      OPT_RegisterOperand startAddress = arm2ir.getTempInt(0);
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, startAddress, arm2ir.getRegister(baseRegister), new OPT_IntConstantOperand(0xFFFFFFFC)));
      
      if (!incrementBase) {
        if (postIndexing) {
          //post-indexing, backward reading
          //startAddress -= (registerCount + (transferPC ? 1 : 0)) * 4;
          OPT_Operand offset = new OPT_IntConstantOperand((registerCount + (transferPC ? 1 : 0)) * 4);
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, startAddress, startAddress, offset));
        } else {
          //pre-indexing, backward-reading
          //startAddress -= (registerCount + (transferPC ? 2 : 1)) * 4
          OPT_Operand offset = new OPT_IntConstantOperand((registerCount + (transferPC ? 2 : 1)) * 4);
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, startAddress, startAddress, offset));
        }
      } else {
        if (postIndexing) {
          //post-indexing, forward reading
          //startAddress -= 4;
          OPT_Operand offset = new OPT_IntConstantOperand(4);
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, startAddress, startAddress, offset));
        } else {
          //pre-indexing, forward reading
          //no need to adjust the start address
        }
      }
      
      OPT_RegisterOperand nextAddress = arm2ir.getTempInt(1);
      arm2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, nextAddress, startAddress));
      
      //TODO: implement
      OperatingMode previousMode = ps.registers.getOperatingMode();
      
      //if we should transfer the user mode registers...
      if (forceUser) {
        //... then change the current register map, but do NOT change the current processor mode
        ps.registers.switchOperatingMode(OperatingMode.USR);
        ps.registers.setOperatingModeWithoutRegisterLayout(previousMode);
        
        //TODO: implement
        throw new RuntimeException("Not yet implemented");
      }

      //are we supposed to load or store multiple registers?
      if (isLoad) {
        int nextReg = 0;

        while (registersToTransfer[nextReg] != -1) {
          //nextAddress += 4;
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, nextAddress, nextAddress, new OPT_IntConstantOperand(4)));
          
          OPT_RegisterOperand target = arm2ir.getRegister(registersToTransfer[nextReg++]);
          ps.memory.translateLoad32(nextAddress, target);
        }

        //if we also transferred the program counter
        if (transferPC) {
          //nextAddress += 4;
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, nextAddress, nextAddress, new OPT_IntConstantOperand(4)));

          OPT_RegisterOperand target = arm2ir.getRegister(ARM_Registers.PC);
          ps.memory.translateLoad32(nextAddress, target);
          
          //TODO: Use the first bit of (target) before the following instruction to determine if we should be in thumb mode...
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, target, target, new OPT_IntConstantOperand(0xFFFFFFFE)));
          
          throw new RuntimeException("Not yet implemented");
          /*
          if (forceUser) {
            //when we are transferring the PC with a forced-user transfer, then we also want to
            //restore the CPSR from the SPSR.
            //However, at the moment our register layout is different from our operating mode.
            //Therefore, sync both first by switching the operating mode to user (which is what our register layout
            //is anyway).
            regs.setOperatingModeWithoutRegisterLayout(OperatingMode.USR);
            regs.restoreSPSR2CPSR();
            
            if (regs.getThumbMode())
              newpc = newpc & 0xFFFFFFFE;
            else
              newpc = newpc & 0xFFFFFFFC;
            
            regs.set(ARM_Registers.PC, newpc);
            //there is no write-back for this instruction.
            return;
          }
          else {
            //shall we switch to thumb mode
            regs.set(ARM_Registers.PC, newpc & 0xFFFFFFFE);
            regs.setThumbMode((newpc & 0x1) != 0);
          }*/
        }
      } else {
        int nextReg = 0;

        while (registersToTransfer[nextReg] != -1) {
          //nextAddress += 4;
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, nextAddress, nextAddress, new OPT_IntConstantOperand(4)));          
          ps.memory.translateStore32(nextAddress, arm2ir.getRegister(registersToTransfer[nextReg++]));
        }

        //also transfer the program counter, if requested so
        if (transferPC) {
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, nextAddress, nextAddress, new OPT_IntConstantOperand(4)));
          ps.memory.translateStore32(nextAddress, new OPT_IntConstantOperand(pc + 8));
        }
      }

      //restore the register layout, if we were transferring the user mode registers
      if (forceUser) {
        //TODO: Implement....
        ps.registers.setOperatingModeWithoutRegisterLayout(OperatingMode.USR);
        ps.registers.switchOperatingMode(previousMode);
      }

      if (writeBack) {
        OPT_RegisterOperand writeBackTarget = arm2ir.getRegister(baseRegister);
        
        //write the last address we read from back to a register
        if (!incrementBase) {
          //backward reading
          if (postIndexing) {
            //backward reading, post-indexing
            arm2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, writeBackTarget, startAddress));
          }
          else {
            //backward reading, pre-indexing
            arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, writeBackTarget, startAddress, new OPT_IntConstantOperand(4)));
          }
        }
        else {
          //forward reading
          if (postIndexing) {
            arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, writeBackTarget, nextAddress, new OPT_IntConstantOperand(4)));
          }
          else {
            arm2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, writeBackTarget, nextAddress));
          }
        }
      }
    }

    public int getSuccessor(int pc) {
      //if we're loading values into the PC, then we can't tell where this instruction will be going
      if (isLoad && transferPC)
        return -1;
      else
        return pc + 4;
    }
  }

  /** Branch to another instruction address. */
  private final class Branch extends ARM_Instructions.Branch implements
  ARM_Instruction {

    public Branch(int instr) {
      super(instr);
    }

    public void translate() {
      
      //if we're supposed to link, then write the previous address into the link register
      if (link) {        
        arm2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, arm2ir.getRegister(ARM_Registers.LR), new OPT_IntConstantOperand(pc + 4)));
      }
      
      arm2ir.setReturnValueResolveLazinessAndBranchToFinish(lazy, new OPT_IntConstantOperand(pc+8 + getOffset()));
    }

    public int getSuccessor(int pc) {
      return pc + getOffset() + 8;
    }
  }

  /** Branch to another instruction address and switch between ARM32 and Thumb code on the way.*/
  private final class BranchExchange extends ARM_Instructions.BranchExchange
      implements ARM_Instruction {

    public BranchExchange(int instr) {
      super(instr);
    }

    public void translate() {
      //TODO: Implement
      throw new RuntimeException("Not yet implemented");
      
      /*
      //remember the previous address
      int previousAddress = regs.get(ARM_Registers.PC) + 8;
      
      //are we supposed to jump to thumb (thumb=true) or ARM32 (thumb=false)?
      boolean thumb;
      
      //the address of the instruction we're jumping to
      int targetAddress;

      switch (target.getType()) {
      case PcRelative:
        targetAddress = previousAddress + target.getOffset();
        thumb = true;
        break;

      case Register:
        targetAddress = regs.get(target.getRegister());
        thumb = (targetAddress & 0x1) != 0;
        targetAddress = targetAddress & 0xFFFFFFFE;
        break;

      default:
        throw new RuntimeException("Unexpected Operand type: "
            + target.getType());
      }

      //jump to the new address
      regs.set(ARM_Registers.PC, targetAddress);
      regs.setThumbMode(thumb);

      //if we're supposed to link, then write the previous address into the link register
      if (link)
        regs.set(ARM_Registers.LR, previousAddress - 4);*/
    }

    public int getSuccessor(int pc) {
      //if we're jumping relative to the PC, then we can predict the next instruction
      if (target.getType() == OperandWrapper.Type.PcRelative) {
        return pc + target.getOffset();
      } else {
        //otherwise we can't predict it
        return -1;
      }
    }
  }

  /** Multiply two integers into a register, possibly adding the value of a third register on the way. */
  private final class IntMultiply extends ARM_Instructions.IntMultiply implements
  ARM_Instruction {

    protected IntMultiply(int instr) {
      super(instr);
    }

    public void translate() {
      //get the two operands
      //we don't need to consider that any operand might be the PC, because the ARM
      //Ref. manual specifies the usage of the PC has undefined results in this operation
      OPT_Operand operand1 = arm2ir.getRegister(Rm);
      OPT_Operand operand2 = arm2ir.getRegister(Rs);
      OPT_RegisterOperand result = arm2ir.getRegister(Rd);

      //calculate the result
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_MUL, result, operand1, operand2));

      if (accumulate) {
        OPT_Operand operand3 = arm2ir.getRegister(Rn);        
        arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, result, result, operand3));
      }

      if (updateConditionCodes) {
        //set the negative flag
        arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
            BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
        
        //set the zero flag
        arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
            BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      }
    }
    
    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }
    
  /** Multiply two longs into a register, possibly adding the value of a third register on the way. */
  private final class LongMultiply extends ARM_Instructions.LongMultiply implements
  ARM_Instruction {

    protected LongMultiply(int instr) {
      super(instr);
    }

    public void translate() {
      //get the two operands
      //we don't need to consider that any operand might be the PC, because the ARM
      //Ref. manual specifies the usage of the PC has undefined results in this operation
      
      OPT_RegisterOperand operand1 = arm2ir.getTempLong(0);
      OPT_RegisterOperand operand2 = arm2ir.getTempLong(1);
      OPT_RegisterOperand result = arm2ir.getTempLong(2);
      
      //fill the two operands
      arm2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, operand1, arm2ir.getRegister(Rm)));
      arm2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, operand2, arm2ir.getRegister(Rs)));
      
      if (unsigned) {
        //treat the original ints as unsigned, so get rid of the signs for the longs
        arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, operand1, operand1, new OPT_LongConstantOperand(0xFFFFFFFF)));
        arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, operand2, operand2, new OPT_LongConstantOperand(0xFFFFFFFF)));
      }

      //multiply the two operands
      arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_MUL, result, operand1, operand2));

      if (accumulate) {          
        //treat the accum. value as an unsigned value
        OPT_Operand operand3 = arm2ir.getRegister(getRdLow());
        OPT_RegisterOperand tmp = arm2ir.getTempLong(0);
        arm2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, tmp, operand3));
        arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, tmp, tmp, new OPT_LongConstantOperand(0xFFFFFFFF)));
        arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_ADD, result, result, tmp));
        
        operand3 = arm2ir.getRegister(getRdHigh());
        arm2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, tmp, operand3));
        arm2ir.appendInstructionToCurrentBlock(Binary.create(LONG_SHL, tmp, tmp, new OPT_IntConstantOperand(32)));
        arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, result, result, operand3));
      }

      if (updateConditionCodes) {
        //set the negative flag
        arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
            BOOLEAN_CMP_LONG, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
        
        //set the zero flag
        arm2ir.appendInstructionToCurrentBlock(BooleanCmp.create(
            BOOLEAN_CMP_LONG, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      }
    }

    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }
    
  /** Move the value of the program status register into a register. */
  private final class MoveFromStatusRegister extends
      ARM_Instructions.MoveFromStatusRegister implements
      ARM_Instruction {

    public MoveFromStatusRegister(int instr) {
      super(instr);
    }

    public void translate() {

      throw new RuntimeException("Not yet implemented");
      /*
      //do we have to transfer the saved or the current PSR?
      if (transferSavedPSR) {
        regs.set(Rd, regs.getSPSR());
      } 
      else {
        regs.set(Rd, regs.getCPSR());
      }*/
    }

    public int getSuccessor(int pc) {
      //Rd should never be the PC, so we can safely predict the next instruction
      return pc + 4;
    }
  }
  
  private final class MoveToStatusRegister extends
    ARM_Instructions.MoveToStatusRegister implements
      ARM_Instruction {

    public MoveToStatusRegister(int instr) {
      super(instr);
    }

    public void translate() {
      
      //TODO: implement
      throw new RuntimeException("Not yet implemented");
      
      /*
      //this variable is going to receive the new psr, which we will set
      int new_psr = ResolvedOperand.resolve(regs, sourceOperand);
      
      //are we currently in a privileged mode?
      boolean inPrivilegedMode = (regs.getOperatingMode() != ARM_Registers.OperatingMode.USR);
      
      //this variable receives the psr that we're replacing
      int old_psr;
      
      //get the currect value for old_psr
      if (transferSavedPSR) {
        //if the current mode does not have a SPSR, then do nothing
        if (inPrivilegedMode && regs.getOperatingMode() != ARM_Registers.OperatingMode.SYS)
          return;
        
        old_psr = regs.getSPSR();
      }
      else {
        old_psr = regs.getCPSR();
      }
      */

      //create a new CPSR value according to what pieces of the CPSR we are actually required to set
      /*if (!transferControl || !inPrivilegedMode) {
        new_psr &= 0xFFFFFF00;
        new_psr |= (old_psr & 0xFF);
      }
      
      if (!transferExtension || !inPrivilegedMode) {
        new_psr &= 0xFFFF00FF;
        new_psr |= (old_psr & 0xFF00);
      }
      
      if (!transferStatus || !inPrivilegedMode) {
        new_psr &= 0xFF00FFFF;
        new_psr |= (old_psr & 0xFF0000);
      }
      
      if (!transferFlags) {
        new_psr &= 0x00FFFFFF;
        new_psr |= (old_psr & 0xFF000000);
      }
      
      if (transferSavedPSR)
        regs.setSPSR(new_psr);
      else
        regs.setCPSR(new_psr);*/
    }

    public int getSuccessor(int pc) {
      return pc+4;
    }
  }

  /** Invoke a software interrupt. */
  private final class SoftwareInterrupt extends ARM_Instructions.SoftwareInterrupt
      implements ARM_Instruction {

    public SoftwareInterrupt(int instr) {
      super(instr);
    }

    public void translate() {
      arm2ir.plantSystemCall(lazy, pc);
    }

    public int getSuccessor(int pc) {
      return -1;
    }
  }

  /** Transfers a single data item (either a byte, half-byte or word) between a register and memory.
   * This operation can either be a load from or a store to memory. */
  private final class SingleDataTransfer extends ARM_Instructions.SingleDataTransfer
      implements ARM_Instruction {

    public SingleDataTransfer(int instr) {
      super(instr);
    }
    
    /** Resolves the offset, which is (when post-indexing is not used) to be added to the 
     * base address to create the final address. */
    private OPT_Operand resolveOffset() {
      OPT_Operand positiveOffset = ResolvedOperand.resolve(ARM_Translator.this, offset);
      
      if (this.positiveOffset) {
        return positiveOffset;
      }
      else {
        OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
        arm2ir.appendInstructionToCurrentBlock(Unary.create(INT_NEG, tmp, positiveOffset));
        return tmp;
      }
    }

    /** Resolves the address of the memory slot, that is involved in the transfer. */
    private OPT_Operand resolveAddress() {
      
      OPT_Operand base;

      //acquire the base address
      if (Rn == 15)
        base = new OPT_IntConstantOperand(pc + 8);
      else
        base = arm2ir.getRegister(Rn);

      //if we are not pre-indexing, then just use the base register for the memory access
      if (!preIndexing)
        return base;
      
      //add the offset to the base register
      OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, tmp, base, resolveOffset()));
      return tmp;
    }

    public void translate() {
      //should we simulate a user-mode memory access? If yes, store the current mode and fake a switch
      //to user mode.
      OperatingMode previousMode = null;
      if (forceUserMode) {
        //TODO: implement
        previousMode = ps.registers.getOperatingMode();
        ps.registers.setOperatingModeWithoutRegisterLayout(ARM_Registers.OperatingMode.USR);
        
        throw new RuntimeException("Not yet implemented");
      }

      //get the address of the memory, that we're supposed access
      OPT_Operand address = resolveAddress();

      if (isLoad) {
        //we are loading a value from memory. Load it into this variable.
        OPT_RegisterOperand value = arm2ir.getRegister(Rd);

        switch (size) {
        
        case Word:
          ps.memory.translateLoad32(address, value);
          
          //according to the ARM reference, the last two bits cause the value to be right-rotated
          OPT_RegisterOperand rotation = arm2ir.getTempInt(0);
          
          //rotation = (address & 0x3) * 8
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, rotation, address, new OPT_IntConstantOperand(0x3)));
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, rotation, rotation, new OPT_IntConstantOperand(3)));
          arm2ir.appendRotateRight(value, value, rotation);
          break;

        case HalfWord:
          if (signExtend)
            ps.memory.translateLoadSigned16(address, value);
          else
            ps.memory.translateLoadUnsigned16(address, value);
          break;

        case Byte:
          if (signExtend)
            ps.memory.translateLoadSigned8(address, value);
          else
            ps.memory.translateLoadUnsigned8(address, value);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + size);
        }
      } 
      else {
        //we are store a value from a register to memory.
        OPT_RegisterOperand value = arm2ir.getRegister(Rd);
        
        switch (size) {
        case Word:
          OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tmp, address, new OPT_IntConstantOperand(0xFFFFFFFE)));
          ps.memory.translateStore32(tmp, value);
          break;
          
        case HalfWord:
          ps.memory.translateStore16(address, value);
          break;
          
        case Byte:
          ps.memory.translateStore8(address, value);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + size);
        }
      }
      
      //if we were writing in user mode, then switch back to our previous operating mode
      if (forceUserMode) {
        //TODO: implement
        ps.registers.setOperatingModeWithoutRegisterLayout(previousMode);
        throw new RuntimeException("Not yet implemented");
      }      

      //should the memory address, which we accessed, be written back into a register? 
      //This is used for continuous memory accesses
      if (writeBack) {
        OPT_RegisterOperand writeBackTarget = arm2ir.getRegister(Rn);
        
        if (preIndexing) {
          arm2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, writeBackTarget, address));
        }
        else {
          //add the offset to the base address and write the result back into Rn
          OPT_Operand resolvedOffset = resolveOffset();

          arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, writeBackTarget, address, resolvedOffset));
        }
      }
    }

    public int getSuccessor(int pc) {
      //if we're loading to the PC, then the next instruction is undefined
      if (Rd == ARM_Registers.PC && isLoad)
        return -1;

      return pc + 4;
    }
  }

  /** Represents an undefined instruction, will throw a runtime error when this instruction
   * is executed. */
  private final class UndefinedInstruction implements ARM_Instruction {

    public UndefinedInstruction(int instr) {
    }

    public void translate() {
      arm2ir.plantThrowBadInstruction(lazy, pc);
    }

    public int getSuccessor(int pc) {
      return -1;
    }

    public Condition getCondition() {
      return Condition.AL;
    }
  }
  
  private final class DebugNopInstruction implements ARM_Instruction {

    public Condition getCondition() {
      return Condition.AL;
    }

    public void translate() {
    }

    public int getSuccessor(int pc) {
      return pc+4;
    }
    
  }

  /** This class will create instances of the different interpreter instructions. It is being "controlled" by
   * the ARM_InstructionDecoder, which uses an abstract factory pattern to decode an instruction. */
  private class InterpreterFactory implements
      ARM_InstructionFactory<ARM_Instruction> {

    public ARM_Instruction createDataProcessing(int instr) {
      Opcode opcode = Opcode.values()[Utils.getBits(instr, 21, 24)];

      switch (opcode) {
      case ADC:
        return new DataProcessing_Adc(instr);
      case ADD:
        return new DataProcessing_Add(instr);
      case AND:
        return new DataProcessing_And(instr);
      case BIC:
        return new DataProcessing_Bic(instr);
      case CMN:
        return new DataProcessing_Cmn(instr);
      case CMP:
        return new DataProcessing_Cmp(instr);
      case EOR:
        return new DataProcessing_Eor(instr);
      case MOV:
        return new DataProcessing_Mov(instr);
      case MVN:
        return new DataProcessing_Mvn(instr);
      case ORR:
        return new DataProcessing_Orr(instr);
      case RSB:
        return new DataProcessing_Rsb(instr);
      case RSC:
        return new DataProcessing_Rsc(instr);
      case SBC:
        return new DataProcessing_Sbc(instr);
      case SUB:
        return new DataProcessing_Sub(instr);
      case TEQ:
        return new DataProcessing_Teq(instr);
      case TST:
        return new DataProcessing_Tst(instr);
      case CLZ:
        return new DataProcessing_Clz(instr);

      default:
        throw new RuntimeException("Unexpected Data Procesing opcode: "
            + opcode);
      }
    }

    public ARM_Instruction createBlockDataTransfer(int instr) {
      return new BlockDataTransfer(instr);
    }

    public ARM_Instruction createBranch(int instr) {
      return new Branch(instr);
    }

    public ARM_Instruction createBranchExchange(int instr) {
      return new BranchExchange(instr);
    }

    public ARM_Instruction createCoprocessorDataProcessing(int instr) {
      //TODO: Implement coprocessor instructions
      /*throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");*/
      return new DebugNopInstruction();
    }

    public ARM_Instruction createCoprocessorDataTransfer(int instr) {
      //    TODO: Implement coprocessor instructions
      /*throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");*/
      return new DebugNopInstruction();
    }

    public ARM_Instruction createCoprocessorRegisterTransfer(int instr) {
      //    TODO: Implement coprocessor instructions
      /*throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");*/
      return new DebugNopInstruction();
    }

    public ARM_Instruction createIntMultiply(int instr) {
      return new IntMultiply(instr);
    }

    public ARM_Instruction createLongMultiply(int instr) {
      return new LongMultiply(instr);
    }

    public ARM_Instruction createMoveFromStatusRegister(int instr) {
      return new MoveFromStatusRegister(instr);
    }

    public ARM_Instruction createMoveToStatusRegister(int instr) {
      return new MoveToStatusRegister(instr);
    }

    public ARM_Instruction createSingleDataTransfer(int instr) {
      return new SingleDataTransfer(instr);
    }

    public ARM_Instruction createSoftwareInterrupt(int instr) {
      return new SoftwareInterrupt(instr);
    }

    public ARM_Instruction createSwap(int instr) {
      return new Swap(instr);
    }

    public ARM_Instruction createUndefinedInstruction(int instr) {
      return new UndefinedInstruction(instr);
    }
  }
}
