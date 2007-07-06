package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder.ARM_InstructionFactory;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.OperandWrapper;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.DataProcessing.Opcode;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.Instruction.Condition;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.arch.arm.os.process.ARM_Registers.OperatingMode;
import org.binarytranslator.generic.branchprofile.BranchProfile.BranchType;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.decoder.Utils;

import com.sun.org.apache.bcel.internal.generic.InstructionFactory;

/**
 * This class implements the {@link Interpreter} interface to interpret ARM instructions from
 * a process space. It uses the {@link ARM_InstructionDecoder} class with a custom {@link InstructionFactory}
 * implementations to create representations of the decoded instructions that implement the {@link Instruction} interface.
 * 
 * @author Michael Baer
 */
public class ARM_Interpreter implements Interpreter {

  /** The process space that we're interpreting.*/
  protected final ARM_ProcessSpace ps;
  
  /** A "quick" pointer to the ARM registers within the process space*/
  protected final ARM_Registers regs;
  
  /** The interpreter factory is creating the final instructions, which implement the Interpreter.Instruction interface. */
  protected final ARM_InstructionFactory<ARM_Instruction> instructionFactory;

  public ARM_Interpreter(ARM_ProcessSpace ps) {
    this.ps = ps;
    this.regs = ps.registers;
    instructionFactory = new InterpreterFactory(); //new CountingInstructionFactory<ARM_Instruction>(
  }

  /** Decodes the instruction at the given address.*/
  public Instruction decode(int pc) {
    
    ARM_Instruction instruction;
    
    if ((pc & 1) != 0) {
      short binaryInstruction = (short)ps.memory.loadInstruction16(pc & 0xFFFFFFFE);
      instruction = ARM_InstructionDecoder.Thumb.decode(binaryInstruction, instructionFactory);  
    }
    else {
      int binaryInstruction = ps.memory.loadInstruction32(pc);
      instruction = ARM_InstructionDecoder.ARM32.decode(binaryInstruction, instructionFactory);
    }
    
    if (instruction.getCondition() != Condition.AL) {
      return new ConditionalDecorator(instruction);
    }
    
    return instruction;
  }
  
  @Override
  public String toString() {
    return instructionFactory.toString();
  }
  
  private abstract static class ResolvedOperand {
    
    protected int value;
    
    public static ResolvedOperand resolveWithShifterCarryOut(ARM_Registers regs, OperandWrapper operand) {
      ResolvedOperand result = new ResolvedOperand_WithShifterCarryOut(regs, operand);
      return result;
    }
    
    public static int resolve(ARM_Registers regs, OperandWrapper operand) {
      ResolvedOperand result = new ResolvedOperand_WithoutShifterCarryOut(regs, operand);
      return result.getValue();
    }
    
    public final int getValue() {
      return value;
    }
    
    public abstract boolean getShifterCarryOut();
    
    private static class ResolvedOperand_WithoutShifterCarryOut 
    extends ResolvedOperand{
    
    private ResolvedOperand_WithoutShifterCarryOut(ARM_Registers regs, OperandWrapper operand) {
      _resolve(regs, operand);
    }
    
    public boolean getShifterCarryOut() {
      throw new RuntimeException("This class does not provide a shifter carry out value.");
    }
    
    private void _resolve(ARM_Registers regs, OperandWrapper operand) {

      switch (operand.getType()) {
      case Immediate:
        value = operand.getImmediate();
        return;

      case Register:
        int reg = operand.getRegister();
        
        //mind the arm pc offset
        if (reg == ARM_Registers.PC)
          value =  regs.readPC();
        else
          value = regs.get(reg);
        
        return;

      case RegisterShiftedRegister:
      case ImmediateShiftedRegister:
        value = resolveShift(regs, operand);
        return;

      case RegisterOffset:
        if (operand.getRegister() == ARM_Registers.PC)
          value = regs.readPC();
        else
          value =regs.get(operand.getRegister());
        
        value += operand.getOffset();
        return;
        
      default:
        throw new RuntimeException("Unexpected wrapped operand type: "
            + operand.getType());
      }
    }
    
    /** If the given OperandWrapper involves shifting a register, then this function will decoder the shift
     * and set the result of the barrel shifter accordingly. */
    private final int resolveShift(ARM_Registers regs, OperandWrapper operand) {
      if (DBT.VerifyAssertions) 
          DBT._assert(operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister ||
                      operand.getType() == OperandWrapper.Type.RegisterShiftedRegister);

      //consider the "usual" ARM program counter offset
      if (operand.getRegister() == ARM_Registers.PC)
        value = regs.readPC();
      else
        value = regs.get(operand.getRegister());
      
      byte shiftAmount;

      if (operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister)
        shiftAmount = operand.getShiftAmount();
      else {
        shiftAmount = (byte) (regs.get(operand.getShiftingRegister()));
      }

      switch (operand.getShiftType()) {
      case ASR:

        if (shiftAmount >= 32) {
          return Utils.getBit(value, 31) ? 0xFFFFFFFF : 0;
        }
        return value >> shiftAmount;

      case LSL:

        if (shiftAmount >= 32) {
          return 0;
        }

        return value << shiftAmount;

      case LSR:

        if (shiftAmount >= 32) {
          return 0;
        }

        return value >>> shiftAmount;

      case ROR:
        return Integer.rotateRight(value, shiftAmount);

      case RRX:
        if (regs.isCarrySet())
          return (value >> 1) | 0x80000000;
        else
          return value >>> 1;

      default:
        throw new RuntimeException("Unexpected shift type: "
            + operand.getShiftType());
      }
    }
  }
    
    private static class ResolvedOperand_WithShifterCarryOut 
      extends ResolvedOperand{
      
      private boolean shifterCarryOut;
      
      private ResolvedOperand_WithShifterCarryOut(ARM_Registers regs, OperandWrapper operand) {
        _resolve(regs, operand);
      }
      
      public boolean getShifterCarryOut() {
        return shifterCarryOut;
      }
      
      private void _resolve(ARM_Registers regs, OperandWrapper operand) {

        switch (operand.getType()) {
        case Immediate:
          value = operand.getImmediate();

          if (operand.getShiftAmount() == 0)
            shifterCarryOut = regs.isCarrySet();
          else
            shifterCarryOut = (value & 0x80000000) != 0;
          
          return;

        case Register:
          shifterCarryOut = regs.isCarrySet();
          int reg = operand.getRegister();
          
          //mind the arm pc offset
          if (reg == ARM_Registers.PC)
            value = regs.readPC();
          else
            value = regs.get(reg);
          
          return;

        case RegisterShiftedRegister:
        case ImmediateShiftedRegister:
          value = resolveShift(regs, operand);
          return;

        case RegisterOffset:
          throw new RuntimeException("This operand type does not produce a shifter carry out.");
          
        default:
          throw new RuntimeException("Unexpected wrapped operand type: "
              + operand.getType());
        }
      }
      
      /** If the given OperandWrapper involves shifting a register, then this function will decoder the shift
       * and set the result of the barrel shifter accordingly. */
      private final int resolveShift(ARM_Registers regs, OperandWrapper operand) {
        if (DBT.VerifyAssertions) 
            DBT._assert(operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister ||
                        operand.getType() == OperandWrapper.Type.RegisterShiftedRegister);

        int value;
        
        //consider the "usual" ARM program counter offset
        if (operand.getRegister() == ARM_Registers.PC)
          value = regs.readPC();
        else
          value = regs.get(operand.getRegister());
        
        byte shiftAmount;

        if (operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister)
          shiftAmount = operand.getShiftAmount();
        else {
          shiftAmount = (byte) (regs.get(operand.getShiftingRegister()));
        }

        switch (operand.getShiftType()) {
        case ASR:

          if (shiftAmount >= 32) {
            shifterCarryOut = Utils.getBit(value, 31);
            return shifterCarryOut ? 0xFFFFFFFF : 0;
          }

          if (shiftAmount == 0) {
            shifterCarryOut = regs.isCarrySet();
            return value;
          }
          
          shifterCarryOut = Utils.getBit(value, shiftAmount - 1);
          return value >> shiftAmount;

        case LSL:

          if (shiftAmount > 32) {
            shifterCarryOut = false;
            return 0;
          }

          if (shiftAmount == 32) {
            shifterCarryOut = (value & 0x1) != 0;
            return 0;
          }

          if (shiftAmount == 0) {
            shifterCarryOut = regs.isCarrySet();
            return value;
          }

          shifterCarryOut = Utils.getBit(value, 32 - shiftAmount);
          return value << shiftAmount;

        case LSR:

          if (shiftAmount > 32) {
            shifterCarryOut = false;
            return 0;
          }
          
          if (shiftAmount == 32) {
            shifterCarryOut = Utils.getBit(value, 31);
            return 0;
          }

          if (shiftAmount == 0) {
            shifterCarryOut = regs.isCarrySet();
            return value;
          }

          shifterCarryOut = Utils.getBit(value, shiftAmount - 1);
          return value >>> shiftAmount;

        case ROR:

          if (shiftAmount == 0) {
            shifterCarryOut = regs.isCarrySet();
            return value;
          } 
          else {
            shifterCarryOut = Utils.getBit(value, (shiftAmount-1) & 0x1F);
            return Integer.rotateRight(value, shiftAmount);
          }

        case RRX:
          shifterCarryOut = (value & 0x1) != 0;

          if (regs.isCarrySet())
            return (value >> 1) | 0x80000000;
          else
            return value >>> 1;

        default:
          throw new RuntimeException("Unexpected shift type: "
              + operand.getShiftType());
        }
      }
    }
  }
  
  /** All ARM interpreter instructions implement this interface. */
  private interface ARM_Instruction extends Interpreter.Instruction {
    Condition getCondition();
  }

  /** All ARM instructions that are supposed to be executed conditionally 
   * are decorated with this decorator. 
   * The decorator takes care of checking the individual condition and depending on it, executing the
   * instruction (or not). The instruction classes itself do not check any conditions. */
  private final class ConditionalDecorator implements Interpreter.Instruction {

    protected final ARM_Instruction conditionalInstruction;
    private final Condition condition;

    /** Decorates an ARM interpreter instruction, by making it execute conditionally. */
    protected ConditionalDecorator(ARM_Instruction i) {
      conditionalInstruction = i;
      this.condition = i.getCondition();
    }
    
    public void execute() {
      if (isConditionTrue()) {
        int nextInstruction = conditionalInstruction.getSuccessor(ps.getCurrentInstructionAddress());
        conditionalInstruction.execute();
        
        if (nextInstruction != -1)
          ps.setCurrentInstructionAddress(nextInstruction);
      }
      else {
        ps.setCurrentInstructionAddress(ps.getCurrentInstructionAddress() + (regs.getThumbMode() ? 2 : 4));
      }
    }

    public int getSuccessor(int pc) {
      //if this instruction is not a jump, then we can tell what the next instruction will be.

      int conditionalSuccessor = conditionalInstruction.getSuccessor(pc);
      boolean thumbMode = (pc & 0x1) == 1;
      
      if (conditionalSuccessor == pc + 4 && !thumbMode)
        return conditionalSuccessor; //ARM may have conditional non-jump instructions
      else
        return -1;
    }
    
   /** Return true if the condition required by the conditional instruction is fulfilled, false otherwise.*/
    private boolean isConditionTrue() {
      switch (condition) {
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
          throw new RuntimeException("Unexpected condition code: " + condition);
      }
    }
    
    @Override
    public String toString() {
      return conditionalInstruction.toString();
    }
  }

  /** A base class for all data processing interpreter instructions, including CLZ.*/
  private abstract class DataProcessing implements ARM_Instruction {
    
    protected final ARM_Instructions.DataProcessing i;

    /** If this bit is set, a special way of reading the program counter is enabled, that is only needed for a single Thumb instruction *sigh*. */
    private final boolean specialThumbPcMode; 

    protected DataProcessing(ARM_Instructions.DataProcessing instr) {
      i = instr;
      specialThumbPcMode = i.isThumb && !i.updateConditionCodes && i.opcode == Opcode.ADD && i.operand2.getType() == OperandWrapper.Type.Immediate;
    }
    
    @Override
    public String toString() {
      return i.toString();
    }

    /** Returns the value of operand 1 of the data processing instruction. This is always a register value. */
    protected int resolveOperand1() {

      if (i.Rn == ARM_Registers.PC) {
        int value = regs.readPC();
        
        //this is a very special instruction encoding that demands that the PC is read with an ARM32 mask.
        if (specialThumbPcMode)
          value = value & 0xFFFFFFFC;
        
        return value;
      }

      return regs.get(i.Rn);
    }

    /** Returns the value of the rhs-operand of the data processing instruction. */
    protected int resolveOperand2() {
      return ResolvedOperand.resolve(regs, i.operand2);
    }

    public abstract void execute();
    
    /**
     * Stores the result of adding <code>lhs</code> + <code>rhs</code> to <code>Rd</code>
     * and sets the flags accordingly, if <code>updateConditionCodes</code> is set.
     * @param lhs
     * @param rhs
     */
    protected final void setAddResult(int lhs, int rhs) {
      setFlagsForAdd(lhs, rhs);
      int result = lhs + rhs;
      
      if (i.Rd == ARM_Registers.PC) {
        if (regs.getThumbMode())
          result |= 1;
          
        if (DBT_Options.profileDuringInterpretation) {
          ps.branchInfo.registerBranch(regs.get(ARM_Registers.PC), result, BranchType.INDIRECT_BRANCH);
        }
      }
      
      regs.set(i.Rd, result);
    }
    
    /**
     * Stores the result of adding <code>lhs</code> - <code>rhs</code> to <code>Rd</code>
     * and sets the flags accordingly, if <code>updateConditionCodes</code> is set.
     * @param lhs
     * @param rhs
     */
    protected final void setSubResult(int lhs, int rhs) {
      setFlagsForSub(lhs, rhs);
      int result = lhs - rhs;
      
      if (i.Rd == ARM_Registers.PC) {
        if (regs.getThumbMode())
          result |= 1;
          
        if (DBT_Options.profileDuringInterpretation) {
          ps.branchInfo.registerBranch(regs.get(ARM_Registers.PC), result, BranchType.INDIRECT_BRANCH);
        }
      }
      
      regs.set(i.Rd, result);
    }

    /** Sets the processor flags according to the result of adding <code>lhs</code> and <code>rhs</code>.*/
    protected final void setFlagsForAdd(int lhs, int rhs) {

      if (i.updateConditionCodes) {
        if (i.Rd != ARM_Registers.PC) {
          int result = lhs + rhs;
          boolean carry = Utils.unsignedAddOverflow(lhs, rhs);
          boolean overflow = Utils.signedAddOverflow(lhs, rhs);
          regs.setFlags(result < 0, result == 0, carry, overflow);
        } 
        else {
          regs.restoreSPSR2CPSR();
        }
      }
    }
    
    /** Sets the processor flags according to the result of subtracting <code>rhs</code> from <code>lhs</code>.*/
    protected final void setFlagsForSub(int lhs, int rhs) {

      if (i.updateConditionCodes) {
        if (i.Rd != ARM_Registers.PC) {
          int result = lhs - rhs;
          boolean carry = !Utils.unsignedSubOverflow(lhs, rhs);
          boolean overflow = Utils.signedSubOverflow(lhs, rhs);
          regs.setFlags(result < 0, result == 0, carry, overflow);
        } 
        else {
          regs.restoreSPSR2CPSR();
        }
      }
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      if (i.Rd != ARM_Registers.PC)
        return pc + i.size();
      else
        return -1;
    }
  }
  
  private abstract class DataProcessing_Logical extends DataProcessing {
    
    /** Most data processing instructions may set the carry flag according to the barrel shifter's carry
     * out value. The value of the barrel shifter is stored within this variable. */
    protected boolean shifterCarryOut;

    protected DataProcessing_Logical(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }
    
    /** If the given OperandWrapper involves shifting a register, then this function will decoder the shift
     * and set the result of the barrel shifter accordingly. However, the shifter carry out is only calculated, when
     * the condition codes are to be modified by this function (because otherwise it won't be used anyway).*/
    protected int resolveOperand2() {
      
      if (i.updateConditionCodes) {
        ResolvedOperand operand = ResolvedOperand.resolveWithShifterCarryOut(regs, i.operand2);
        shifterCarryOut = operand.getShifterCarryOut();
        return operand.getValue();
      }
      else {
        return super.resolveOperand2();
      }
    }
    
    /** Stores the result of a logical operation to a register and, if <code>updateConditionFlags</code>
     * is set, also sets the flags accordingly. */
    protected final void setLogicalResult(int result) {
      
      if (i.Rd == ARM_Registers.PC) {
        
        if (regs.getThumbMode())
          result |= 1;
        
        if (DBT_Options.profileDuringInterpretation) {        
          if (i.getOpcode() == Opcode.MOV && i.operand2.getType() == OperandWrapper.Type.Register && i.operand2.getRegister() == ARM_Registers.LR)
            ps.branchInfo.registerReturn(regs.get(ARM_Registers.PC), result);
          else
            ps.branchInfo.registerBranch(regs.get(ARM_Registers.PC), result, BranchType.INDIRECT_BRANCH);
        }
      }
      
      regs.set(i.Rd, result);
      setFlagsForLogicalOperator(result);
    }

    /** Sets the condition field for logical operations. */
    protected final void setFlagsForLogicalOperator(int result) {
      
      if (i.updateConditionCodes) {
        if (i.Rd != ARM_Registers.PC) {
          regs.setFlags(result < 0, result == 0, shifterCarryOut);
        } else {
          regs.restoreSPSR2CPSR();
        }
      }
    }  
  }

  /** Binary and. <code>Rd = op1 & op2 </code>.*/
  private final class DataProcessing_And extends DataProcessing_Logical {

    protected DataProcessing_And(ARM_Instructions.DataProcessing  instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() & resolveOperand2();
      setLogicalResult(result);
    }
  }

  /** Exclusive or. <code>Rd = op1 ^ op2 </code>.*/
  private final class DataProcessing_Eor extends DataProcessing_Logical {

    protected DataProcessing_Eor(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() ^ resolveOperand2();
      setLogicalResult(result);
    }
  }

  /** Add. <code>Rd = op1 + op2 </code>.*/
  private final class DataProcessing_Add extends DataProcessing {

    public DataProcessing_Add(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();
      
      setAddResult(operand1, operand2);
    }
  }

  /** Subtract. <code>Rd = op1 - op2 </code>.*/
  private final class DataProcessing_Sub extends DataProcessing {

    public DataProcessing_Sub(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }
    
    @Override
    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();
      setSubResult(operand1, operand2);
    }
  }

  /** Reverse subtract. <code>Rd = op2 - op1</code>.*/
  private final class DataProcessing_Rsb extends DataProcessing {

    protected DataProcessing_Rsb(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();
      setSubResult(operand2, operand1);
    }
  }

  /** Add with carry. <code>Rd = op1 + op2 + CARRY</code>.
   * If the carry flag is set, the instruction will add 1 to one of the operands (whichever operands would
   * not cause an overflow). Then, the normal add-routine is being invoked. */
  private final class DataProcessing_Adc extends DataProcessing {

    protected DataProcessing_Adc(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();

      if (regs.isCarrySet()) {
        if (operand1 != Integer.MAX_VALUE) {
          operand1++;
        } else if (operand2 != Integer.MAX_VALUE) {
          operand2++;
        } else {
          regs.setFlags(true, true, true, true);
          
          //set the result to any of the operands
          regs.set(i.Rd, operand1);
          return;
        }
      }
      
      setAddResult(operand1, operand2);
    }
  }

  /** Subtract with carry. <code>Rd = op1 - op2 - NOT(CARRY)</code>.*/
  private class DataProcessing_Sbc extends DataProcessing {

    protected DataProcessing_Sbc(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }
    
    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();

      if (!regs.isCarrySet()) {
        if (operand1 != Integer.MIN_VALUE) {
          operand1--;
        } else if (operand2 != Integer.MAX_VALUE) {
          operand2++;
        } else {
          //TODO: Remove this exception, when the correct behavior has been verified.
          throw new RuntimeException("I'm interested in finding a case where this occurs, so this exception is sooner or later going to 'notify' me..");
          //regs.setFlags(operand1 > 0, operand1 != 0, true, true);
          //regs.set(Rd, operand1);
          //return;
        }
      }

      setSubResult(operand1, operand2);
    }
  }

  /** Reserve subtract with carry. <code>Rd = -op1 + op2 - NOT(CARRY)</code>.*/
  private final class DataProcessing_Rsc extends DataProcessing_Sbc {

    protected DataProcessing_Rsc(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }
    
    @Override
    protected int resolveOperand1() {
      return super.resolveOperand2();
    }
    
    @Override
    protected int resolveOperand2() {
      return super.resolveOperand1();
    }
  }

  /** Set the flags according to the logical-and of two values. 
   * <code>Flags = op1 & op2</code>*/
  private final class DataProcessing_Tst extends DataProcessing_Logical {

    protected DataProcessing_Tst(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForLogicalOperator(resolveOperand1() & resolveOperand2());
    }
  }

  /** Sets the flags according to the exclusive-or of two values.
   * <code>Flags = op1 ^ op2</code> */
  private final class DataProcessing_Teq extends DataProcessing_Logical {

    protected DataProcessing_Teq(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForLogicalOperator(resolveOperand1() ^ resolveOperand2());
    }
  }

  /** Set the flags according to the comparison of two values.
   * <code>Flags = op1 - op2</code> */
  private final class DataProcessing_Cmp extends DataProcessing {

    protected DataProcessing_Cmp(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForSub(resolveOperand1(), resolveOperand2());
    }
  }

  /** Set the flags according to the comparison of two values, negating the 2nd value on the way.
   * <code>Flags = op1 + op2</code>. */
  private final class DataProcessing_Cmn extends DataProcessing {

    protected DataProcessing_Cmn(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForAdd(resolveOperand1(), resolveOperand2());
    }
  }

  /** Binary or. <code>Rd = op1 | op2</code>. */
  private final class DataProcessing_Orr extends DataProcessing_Logical {

    protected DataProcessing_Orr(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() | resolveOperand2();
      setLogicalResult(result);
    }
  }

  private final class DataProcessing_Mov extends DataProcessing_Logical {

    protected DataProcessing_Mov(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    /** Moves a value into a register .*/
    public void execute() {
      int result = resolveOperand2();
      setLogicalResult(result);
    }
  }

  /** Bit clear. Clear bits in a register by a mask given by a second operand. 
   * <code>Rd =  op1 & (~op2)</code>.*/
  private final class DataProcessing_Bic extends DataProcessing_Logical {

    protected DataProcessing_Bic(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    /** Clear bits in a register by a mask given by a second operand. */
    public void execute() {
      int result = resolveOperand1() & (~resolveOperand2());
      setLogicalResult(result);
    }
  }

  /** Move and negate. Moves an integer between two registers, negating it on the way. 
   * <code>Rd = ~op2</code>.*/
  private final class DataProcessing_Mvn extends DataProcessing_Logical {

    protected DataProcessing_Mvn(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = ~resolveOperand2();
      setLogicalResult(result);
    }
  }
  
  /** Count the number of leading zeros in an integer.
   * <code>Rd = Number_Of_Leading_Zeroes(op2) </code> */
  private final class DataProcessing_Clz extends DataProcessing {

    protected DataProcessing_Clz(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = Integer.numberOfLeadingZeros(resolveOperand2());
      regs.set(i.Rd, result);
    }
  }

  /** Swap a register and a memory value. 
   * TODO: At the moment, Pearcolator does not support any way of locking the memory. However, once it does
   * any other memory accesses should be pending until the swap instruction succeeds.*/
  private final class Swap implements ARM_Instruction {
    
    private final ARM_Instructions.Swap i;

    public Swap(ARM_Instructions.Swap instr) {
      i = instr;
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public void execute() {
      int memAddr = regs.get(i.Rn);
      
      //swap exchanges the value of a memory address with the value in a register
      if (!i.swapByte) {
        int tmp = ps.memory.load32(memAddr);
        ps.memory.store32(memAddr, regs.get(i.Rm));
        
        //according to the ARM architecture reference, the value loaded from a memory address is rotated
        //by the number of ones in the first two bits of the address
        regs.set(i.Rd, Integer.rotateRight(tmp, (memAddr & 0x3) * 8));
      }
      else {
        int tmp = ps.memory.loadUnsigned8(memAddr);
        ps.memory.store8(memAddr, regs.get(i.Rm));
        regs.set(i.Rd, tmp);
      }
    }
    
    @Override
    public String toString() {
      return i.toString();
    }

    public int getSuccessor(int pc) {
      //according to the ARM Architecture reference, using the pc as Rd yields an undefined
      //result. Therefore, we can safely assume that this instruction never equals a branch
      return pc + i.size();
    }
  }

  /** Transfer multiple registers at once between the register bank and the memory. */
  private final class BlockDataTransfer
      implements ARM_Instruction {
    
    private final ARM_Instructions.BlockDataTransfer i;

    /** An array that contains the registers to be transferd in ascending order. 
     * The list is delimited by setting the entry after the last register index to -1.
     * The PC is not included in this list, if it shall be transferred.  */
    private final int[] registersToTransfer = new int[16];

    /** True if the PC should be transferred to, false otherwise. */
    private final boolean transferPC;
    
    /** Offset of the first memory address from the value stoted in the base register. */
    private final int startAddressOffset;
    
    /** After adding this offset to <code>startAddress</code>, the resulting value will be written back into the
     * base register. */
    private final int writebackOffset;
    
    /** Should writeback be performed? */
    private final boolean doWriteback;

    public BlockDataTransfer(ARM_Instructions.BlockDataTransfer instr) {
      i = instr;

      transferPC = i.transferRegister(ARM_Registers.PC);
      int regCount = 0;

      for (int i = 0; i <= 14; i++)
        if (this.i.transferRegister(i)) {
          registersToTransfer[regCount++] = i;
        }

      registersToTransfer[regCount] = -1;
      
      if (transferPC)
        regCount++;
      
      if (!i.incrementBase) {
        if (i.postIndexing) {
          //post-indexing, backward reading
          startAddressOffset = regCount * -4;
        } else {
          //pre-indexing, backward-reading
          startAddressOffset = (regCount + 1) * -4;
        }
      } else {
        if (i.postIndexing) {
          //post-indexing, forward reading
          startAddressOffset = -4;
        } else {
          //pre-indexing, forward reading
          //no need to adjust the start address
          startAddressOffset = 0;
        }
      }
      
      if (!i.writeBack || i.transferRegister(i.baseRegister)) {
        //do not change the register
        doWriteback = false;
        writebackOffset = 0;
      }
      else {
        doWriteback = true;
        
        if (!i.incrementBase) {
          //backward reading
          if (i.postIndexing) {
            //backward reading, post-indexing
            writebackOffset = 0;
          }
          else {
            //backward reading, pre-indexing
            writebackOffset = 4;
          }
        }
        else {
          //forward reading
          if (i.postIndexing) {
            writebackOffset = (regCount + 1) * 4;
          }
          else {
            writebackOffset = regCount * 4;
          }
        }
      }
    }

    public void execute() {
      //build the address, which generally ignores the last two bits
      final int startAddress = (regs.get(i.baseRegister) & 0xFFFFFFFC) + startAddressOffset;
      int nextAddress = startAddress;
      
      OperatingMode previousMode = ps.registers.getOperatingMode();
      
      //if we should transfer the user mode registers...
      if (i.forceUser) {
        //... then change the current register map, but do NOT change the current processor mode
        ps.registers.switchOperatingMode(OperatingMode.USR);
        ps.registers.setOperatingModeWithoutRegisterLayout(previousMode);
      }

      //are we supposed to load or store multiple registers?
      if (i.isLoad) {
        int nextReg = 0;

        while (registersToTransfer[nextReg] != -1) {
          nextAddress += 4;
          regs.set(registersToTransfer[nextReg++], ps.memory
              .load32(nextAddress));
        }

        //Are we also supposed to transfer the program counter?
        if (transferPC) {
          nextAddress += 4;
          int newpc = ps.memory.load32(nextAddress);
          
          if (DBT_Options.profileDuringInterpretation)
            ps.branchInfo.registerReturn(regs.get(ARM_Registers.PC), newpc);

          if (i.forceUser) {
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
            regs.set(ARM_Registers.PC, newpc);
            regs.setThumbMode((newpc & 0x1) != 0);
          }
        }
      } else {
        int nextReg = 0;

        while (registersToTransfer[nextReg] != -1) {
          nextAddress += 4;
          ps.memory.store32(nextAddress, regs
              .get(registersToTransfer[nextReg++]));
        }

        //also transfer the program counter, if requested so
        if (transferPC) {
          nextAddress += 4;
          ps.memory.store32(nextAddress, regs.readPC());
        }
      }

      //restore the register layout, if we were transferring the user mode registers
      if (i.forceUser) {
        ps.registers.setOperatingModeWithoutRegisterLayout(OperatingMode.USR);
        ps.registers.switchOperatingMode(previousMode);
      }

      if (doWriteback)
        regs.set(i.baseRegister, startAddress + writebackOffset);
    }

    public Condition getCondition() {
      return i.condition;
    }
    
    @Override
    public String toString() {
      return i.toString();
    }
    
    public int getSuccessor(int pc) {
      //if we're loading values into the PC, then we can't tell where this instruction will be going
      if (i.isLoad && transferPC)
        return -1;
      else
        return pc + i.size();
    }
  }

  /** Branch to another instruction address. */
  private final class Branch implements ARM_Instruction {
    
    private final ARM_Instructions.Branch i;

    public Branch(ARM_Instructions.Branch instr) {
      i = instr;
    }

    public void execute() {

      int destination;
      BranchType branchType;
      
      if (i.offset.getType() != OperandWrapper.Type.Immediate) {
        branchType = BranchType.INDIRECT_BRANCH;
      }
      else {
        branchType = BranchType.DIRECT_BRANCH;
      }
      
      destination = regs.readPC() + ResolvedOperand.resolve(regs, i.offset); 
      
      if (DBT_Options.profileDuringInterpretation) {
        if (i.link) {
          ps.branchInfo.registerCall(regs.get(ARM_Registers.PC), destination, regs.get(ARM_Registers.PC) + i.size());
        }
        else { 
          ps.branchInfo.registerBranch(regs.get(ARM_Registers.PC), destination, branchType);
        }
      }
      
      //if we're supposed to link, then write the previous address into the link register
      if (i.link) {
        regs.set(ARM_Registers.LR, regs.get(ARM_Registers.PC) + i.size());
      }
      
      if (regs.getThumbMode())
        destination |= 1;
      
      regs.set(ARM_Registers.PC, destination);
    }
    
    public Condition getCondition() {
      return i.condition;
    }
    
    @Override
    public String toString() {
      return i.toString();
    }

    public int getSuccessor(int pc) {
      if (i.offset.getType() == OperandWrapper.Type.Immediate)
        return (pc + 2*i.size() + i.getOffset().getImmediate()) | (pc & 1);
      else
        return -1;
    }
  }

  /** Branch to another instruction  address and switch between ARM32 and Thumb code on the way.*/
  private final class BranchExchange 
      implements ARM_Instruction {
    
    private final ARM_Instructions.BranchExchange i;

    public BranchExchange(ARM_Instructions.BranchExchange instr) {
      i = instr;
    }

    public void execute() {
      //are we supposed to jump to thumb (thumb=true) or ARM32 (thumb=false)?
      boolean thumb;
      
      //the address of the instruction we're jumping to
      int targetAddress;

      switch (i.target.getType()) {
      case RegisterOffset:
        int previousAddress; 
        
        if (i.target.getRegister() == ARM_Registers.PC)
          previousAddress = regs.readPC();
        else
          previousAddress = regs.get(i.target.getRegister());
        
        targetAddress = (previousAddress + i.target.getOffset()) | 1;
        thumb = true;
        break;

      case Register:
        if (i.target.getRegister() != ARM_Registers.PC)
          targetAddress = regs.get(i.target.getRegister());
        else
          targetAddress = regs.readPC();
        
        thumb = (targetAddress & 0x1) != 0;
        //targetAddress = targetAddress & 0xFFFFFFFE;
        break;

      default:
        throw new RuntimeException("Unexpected Operand type: "
            + i.target.getType());
      }

      //if we're supposed to link, then write the previous address into the link register
      if (i.link) {
        regs.set(ARM_Registers.LR, regs.readPC() - (regs.getThumbMode() ? 2 : 4));
        ps.branchInfo.registerCall(regs.get(ARM_Registers.PC), targetAddress, regs.get(ARM_Registers.PC) + i.size());
      }
      else {
        ps.branchInfo.registerBranch(regs.get(ARM_Registers.PC), targetAddress, BranchType.DIRECT_BRANCH);
      }

      //jump to the new address
      regs.set(ARM_Registers.PC, targetAddress);
      regs.setThumbMode(thumb);
    }
    
    public Condition getCondition() {
      return i.condition;
    }
    
    @Override
    public String toString() {
      return i.toString();
    }

    public int getSuccessor(int pc) {
      //if we're jumping relative to the PC, then we can predict the next instruction
      if (i.target.getType() == OperandWrapper.Type.RegisterOffset && i.target.getRegister() == ARM_Registers.PC) {
        return pc + i.target.getOffset();
      } else {
        //otherwise we can't predict it
        return -1;
      }
    }
  }

  /** Multiply two integers into a register, possibly adding the value of a third register on the way. */
  private final class IntMultiply implements
  ARM_Instruction {
    
    private final ARM_Instructions.IntMultiply i;

    protected IntMultiply(ARM_Instructions.IntMultiply instr) {
      i = instr;
    }

    public void execute() {
      //get the two operands
      //we don't need to consider that any operand might be the PC, because the ARM
      //Ref. manual specifies the usage of the PC has undefined results in this operation
      int operand1 = regs.get(i.Rm);
      int operand2 = regs.get(i.Rs);
      
      //calculate the result
      int result = operand1 * operand2;

      if (i.accumulate) {
        result += regs.get(i.Rn);
      }

      //and finally, update the register map
      regs.set(i.Rd, result);

      if (i.updateConditionCodes) {
        regs.setFlags(result < 0, result == 0);
      }
    }
    
    public Condition getCondition() {
      return i.condition;
    }
    
    @Override
    public String toString() {
      return i.toString();
    }

    public int getSuccessor(int pc) {
      return pc + i.size();
    }
  }
  
  /** Multiply two longs into a register, possibly adding the value of a third register on the way. */
  private final class LongMultiply implements
  ARM_Instruction {
    
    private final ARM_Instructions.LongMultiply i;

    protected LongMultiply(ARM_Instructions.LongMultiply instr) {
      i = instr;
    }

    public void execute() {

      // get the two operands
      // We don't need to consider that any operand might be the PC, because the
      // ARM Ref. manual specifies the usage of the PC has undefined results in this
      // operation
      long operand1 = regs.get(i.Rm);
      long operand2 = regs.get(i.Rs);
      
      //get rid of the signs, if we're supposed to do unsigned multiplication
      if (i.unsigned) {
        operand1 = operand1 & 0xFFFFFFFF;
        operand2 = operand2 & 0xFFFFFFFF;
      }

      // calculate the result
      long result = operand1 * operand2;

      if (i.accumulate) {
        //treat the register as an unsigned value
        long operand = regs.get(i.getRdLow());
        operand &= 0xFFFFFFFF;
        result += operand; 

        result += regs.get(i.getRdHigh()) << 32;
      }

      // and finally, update the register map
      regs.set(i.getRdLow(), (int) result);
      regs.set(i.getRdHigh(), (int) (result >>> 32));

      if (i.updateConditionCodes) {
        regs.setFlags(result < 0, result == 0);
      }
    }
    
    public Condition getCondition() {
      return i.condition;
    }
    
    @Override
    public String toString() {
      return i.toString();
    }

    public int getSuccessor(int pc) {
      return pc + i.size();
    }
  }

  /** Move the value of the program status register into a register. */
  private final class MoveFromStatusRegister implements ARM_Instruction {
    
    private final ARM_Instructions.MoveFromStatusRegister i;

    public MoveFromStatusRegister(ARM_Instructions.MoveFromStatusRegister instr) {
      i = instr;
    }

    public void execute() {

      //do we have to transfer the saved or the current PSR?
      if (i.transferSavedPSR) {
        regs.set(i.Rd, regs.getSPSR());
      } 
      else {
        regs.set(i.Rd, regs.getCPSR());
      }
    }
    
    @Override
    public String toString() {
      return i.toString();
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      //Rd should never be the PC, so we can safely predict the next instruction
      return pc + i.size();
    }
  }
  
  private final class MoveToStatusRegister implements ARM_Instruction {
    
    private final ARM_Instructions.MoveToStatusRegister i;

    public MoveToStatusRegister(ARM_Instructions.MoveToStatusRegister instr) {
      i = instr;
    }

    public void execute() {
      //this variable is going to receive the new psr, which we will set
      int new_psr = ResolvedOperand.resolve(regs, i.sourceOperand);
      
      //are we currently in a privileged mode?
      boolean inPrivilegedMode = (regs.getOperatingMode() != ARM_Registers.OperatingMode.USR);
      
      //this variable receives the psr that we're replacing
      int old_psr;
      
      //get the currect value for old_psr
      if (i.transferSavedPSR) {
        //if the current mode does not have a SPSR, then do nothing
        if (inPrivilegedMode && regs.getOperatingMode() != ARM_Registers.OperatingMode.SYS)
          return;
        
        old_psr = regs.getSPSR();
      }
      else {
        old_psr = regs.getCPSR();
      }

      //create a new CPSR value according to what pieces of the CPSR we are actually required to set
      if (!i.transferControl || !inPrivilegedMode) {
        new_psr &= 0xFFFFFF00;
        new_psr |= (old_psr & 0xFF);
      }
      
      if (!i.transferExtension || !inPrivilegedMode) {
        new_psr &= 0xFFFF00FF;
        new_psr |= (old_psr & 0xFF00);
      }
      
      if (!i.transferStatus || !inPrivilegedMode) {
        new_psr &= 0xFF00FFFF;
        new_psr |= (old_psr & 0xFF0000);
      }
      
      if (!i.transferFlags) {
        new_psr &= 0x00FFFFFF;
        new_psr |= (old_psr & 0xFF000000);
      }
      
      if (i.transferSavedPSR)
        regs.setSPSR(new_psr);
      else
        regs.setCPSR(new_psr);
    }
    
    public Condition getCondition() {
      return i.condition;
    }
    
    @Override
    public String toString() {
      return i.toString();
    }

    public int getSuccessor(int pc) {
      return pc + i.size();
    }
  }

  /** Invoke a software interrupt. */
  private final class SoftwareInterrupt implements ARM_Instruction {
    
    private final ARM_Instructions.SoftwareInterrupt i;

    public SoftwareInterrupt(ARM_Instructions.SoftwareInterrupt instr) {
      i = instr;
    }

    public void execute() {
      ps.doSysCall();
    }
    
    public Condition getCondition() {
      return i.condition;
    }
    
    @Override
    public String toString() {
      return i.toString();
    }

    public int getSuccessor(int pc) {
      return -1;
    }
  }

  /** Transfers a single data item (either a byte, half-byte or word) between a register and memory.
   * This operation can either be a load from or a store to memory. */
  private final class SingleDataTransfer implements ARM_Instruction {
    
    private final ARM_Instructions.SingleDataTransfer i;

    public SingleDataTransfer(ARM_Instructions.SingleDataTransfer instr) {
      i = instr;
    }
    
    /** Resolves the offset, which is (when post-indexing is not used) to be added to the 
     * base address to create the final address. */
    private int resolveOffset() {
      int addrOffset = ResolvedOperand.resolve(regs, i.offset);

      if (i.positiveOffset)
        return addrOffset;
      else
        return -1 * addrOffset;
    }

    /** Resolves the address of the memory slot, that is involved in the transfer. */
    private int resolveAddress() {

      //acquire the base address
      int base; 
      
      //take ARM's PC offset into account
      if (i.Rn == ARM_Registers.PC) {
        base = regs.readPC();
        
        //Thumb mode has this weird way of accessing the PC sometimes
        if (i.isThumb && i.isLoad)
          base = base & 0xFFFFFFFC;
      }
      else
        base = regs.get(i.Rn);

      //if we are not pre-indexing, then just use the base register for the memory access
      if (!i.preIndexing)
        return base;
      
      return base + resolveOffset();
    }

    public void execute() {
      //should we simulate a user-mode memory access? If yes, store the current mode and fake a switch
      //to user mode.
      OperatingMode previousMode = null;
      if (i.forceUserMode) {
        previousMode = ps.registers.getOperatingMode();
        ps.registers.setOperatingModeWithoutRegisterLayout(ARM_Registers.OperatingMode.USR);
      }

      //get the address of the memory, that we're supposed access
      int address = resolveAddress();

      if (i.isLoad) {
        //we are loading a value from memory. Load it into this variable.
        int value;

        switch (i.size) {
        
        case Word:
          value = ps.memory.load32(address);
          
          //according to the ARM reference, the last two bits cause the value to be right-rotated
          value = Integer.rotateRight(value, (address & 0x3) * 8);
          break;

        case HalfWord:
          if (i.signExtend)
            value = ps.memory.loadSigned16(address);
          else
            value = ps.memory.loadUnsigned16(address);
          break;

        case Byte:
          if (i.signExtend)
            value = ps.memory.loadSigned8(address);
          else
            value = ps.memory.loadUnsigned8(address);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + i.size);
        }

        //finally, write the variable into a register
        regs.set(i.Rd, value);
        
        if (DBT_Options.profileDuringInterpretation) {
          if (i.Rd == ARM_Registers.PC)
            ps.branchInfo.registerBranch(regs.get(ARM_Registers.PC), value, BranchType.INDIRECT_BRANCH);
        }
      } 
      else {
        //we are store a value from a register to memory.
        int value = regs.get(i.Rd);

        switch (i.size) {
        case Word:
          ps.memory.store32(address & 0xFFFFFFFE, value);
          break;
          
        case HalfWord:
          ps.memory.store16(address, value);
          break;
          
        case Byte:
          ps.memory.store8(address, value);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + i.size);
        }
      }
      
      //if we were writing in user mode, then switch back to our previous operating mode
      if (i.forceUserMode) {
        ps.registers.setOperatingModeWithoutRegisterLayout(previousMode);
      }      

      //should the memory address, which we accessed, be written back into a register? 
      //This is used for continuous memory accesses
      if (i.writeBack) {
        if (i.preIndexing) {
          regs.set(i.Rn, address);
        }
        else {
          //add the offset to the base address and write the result back into Rn
          regs.set(i.Rn, address + resolveOffset());
        }
      }
    }
    
    public Condition getCondition() {
      return i.condition;
    }
    
    @Override
    public String toString() {
      return i.toString();
    }

    public int getSuccessor(int pc) {
      //if we're loading to the PC, then the next instruction is undefined
      if (i.Rd == ARM_Registers.PC && i.isLoad)
        return -1;

      return pc + i.size();
    }
  }

  /** Represents an undefined instruction, will throw a runtime error when this instruction
   * is executed. */
  private final class UndefinedInstruction implements ARM_Instruction {

    public UndefinedInstruction(int instr) {
    }

    public void execute() {
      ps.doUndefinedInstruction();
    }
    
    public Condition getCondition() {
      return Condition.AL;
    }

    public int getSuccessor(int pc) {
      return -1;
    }
  }
  
  private final class DebugNopInstruction implements ARM_Instruction {
    
    private boolean isThumb;
    
    public DebugNopInstruction(boolean isThumb) {
      this.isThumb = isThumb;
    }

    public void execute() {
    }
    
    public Condition getCondition() {
      return Condition.NV;
    }

    public int getSuccessor(int pc) {
      return pc + (isThumb ? 2 : 4);
    }
    
  }

  /** This class will create instances of the different interpreter instructions. It is being "controlled" by
   * the ARM_InstructionDecoder, which uses an abstract factory pattern to decode an instruction. */
  private class InterpreterFactory implements
      ARM_InstructionFactory<ARM_Instruction> {

    public ARM_Instruction createDataProcessing(int instr) {
      ARM_Instructions.DataProcessing i = new ARM_Instructions.DataProcessing(instr);

      switch (i.opcode) {
      case ADC:
        return new DataProcessing_Adc(i);
      case ADD:
        return new DataProcessing_Add(i);
      case AND:
        return new DataProcessing_And(i);
      case BIC:
        return new DataProcessing_Bic(i);
      case CMN:
        return new DataProcessing_Cmn(i);
      case CMP:
        return new DataProcessing_Cmp(i);
      case EOR:
        return new DataProcessing_Eor(i);
      case MOV:
        return new DataProcessing_Mov(i);
      case MVN:
        return new DataProcessing_Mvn(i);
      case ORR:
        return new DataProcessing_Orr(i);
      case RSB:
        return new DataProcessing_Rsb(i);
      case RSC:
        return new DataProcessing_Rsc(i);
      case SBC:
        return new DataProcessing_Sbc(i);
      case SUB:
        return new DataProcessing_Sub(i);
      case TEQ:
        return new DataProcessing_Teq(i);
      case TST:
        return new DataProcessing_Tst(i);
      case CLZ:
        return new DataProcessing_Clz(i);

      default:
        throw new RuntimeException("Unexpected Data Procesing opcode: " + i.opcode);
      }
    }

    public ARM_Instruction createBlockDataTransfer(int instr) {
      return new BlockDataTransfer(new ARM_Instructions.BlockDataTransfer(instr));
    }

    public ARM_Instruction createBranch(int instr) {
      return new Branch(new ARM_Instructions.Branch(instr));
    }

    public ARM_Instruction createBranchExchange(int instr) {
      return new BranchExchange(new ARM_Instructions.BranchExchange(instr));
    }

    public ARM_Instruction createCoprocessorDataProcessing(int instr) {
      //TODO: Implement coprocessor instructions
      /*throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");*/
      return new DebugNopInstruction(false);
    }

    public ARM_Instruction createCoprocessorDataTransfer(int instr) {
      //    TODO: Implement coprocessor instructions
      /*throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");*/
      return new DebugNopInstruction(false);
    }

    public ARM_Instruction createCoprocessorRegisterTransfer(int instr) {
      //    TODO: Implement coprocessor instructions
      /*throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");*/
      return new DebugNopInstruction(false);
    }

    public ARM_Instruction createIntMultiply(int instr) {
      return new IntMultiply(new ARM_Instructions.IntMultiply(instr));
    }

    public ARM_Instruction createLongMultiply(int instr) {
      return new LongMultiply(new ARM_Instructions.LongMultiply(instr));
    }

    public ARM_Instruction createMoveFromStatusRegister(int instr) {
      return new MoveFromStatusRegister(new ARM_Instructions.MoveFromStatusRegister(instr));
    }

    public ARM_Instruction createMoveToStatusRegister(int instr) {
      return new MoveToStatusRegister(new ARM_Instructions.MoveToStatusRegister(instr));
    }

    public ARM_Instruction createSingleDataTransfer(int instr) {
      return new SingleDataTransfer(new ARM_Instructions.SingleDataTransfer(instr));
    }

    public ARM_Instruction createSoftwareInterrupt(int instr) {
      return new SoftwareInterrupt(new ARM_Instructions.SoftwareInterrupt(instr));
    }

    public ARM_Instruction createSwap(int instr) {
      return new Swap(new ARM_Instructions.Swap(instr));
    }

    public ARM_Instruction createUndefinedInstruction(int instr) {
      return new UndefinedInstruction(instr);
    }

    public ARM_Instruction createBlockDataTransfer(short instr) {
      return new BlockDataTransfer(new ARM_Instructions.BlockDataTransfer(instr));
    }

    public ARM_Instruction createBranch(short instr) {
      return new Branch(new ARM_Instructions.Branch(instr));
    }

    public ARM_Instruction createBranchExchange(short instr) {
      return new BranchExchange(new ARM_Instructions.BranchExchange(instr));
    }

    public ARM_Instruction createDataProcessing(short instr) {
      ARM_Instructions.DataProcessing i = new ARM_Instructions.DataProcessing(instr);

      switch (i.opcode) {
      case ADC:
        return new DataProcessing_Adc(i);
      case ADD:
        return new DataProcessing_Add(i);
      case AND:
        return new DataProcessing_And(i);
      case BIC:
        return new DataProcessing_Bic(i);
      case CMN:
        return new DataProcessing_Cmn(i);
      case CMP:
        return new DataProcessing_Cmp(i);
      case EOR:
        return new DataProcessing_Eor(i);
      case MOV:
        return new DataProcessing_Mov(i);
      case MVN:
        return new DataProcessing_Mvn(i);
      case ORR:
        return new DataProcessing_Orr(i);
      case RSB:
        return new DataProcessing_Rsb(i);
      case RSC:
        return new DataProcessing_Rsc(i);
      case SBC:
        return new DataProcessing_Sbc(i);
      case SUB:
        return new DataProcessing_Sub(i);
      case TEQ:
        return new DataProcessing_Teq(i);
      case TST:
        return new DataProcessing_Tst(i);
      case CLZ:
        return new DataProcessing_Clz(i);

      default:
        throw new RuntimeException("Unexpected Data Procesing opcode: " + i.opcode);
      }
    }

    public ARM_Instruction createSingleDataTransfer(short instr) {
      return new SingleDataTransfer(new ARM_Instructions.SingleDataTransfer(instr));
    }

    public ARM_Instruction createSoftwareInterrupt(short instr) {
      return new SoftwareInterrupt(new ARM_Instructions.SoftwareInterrupt(instr));
    }

    public ARM_Instruction createUndefinedInstruction(short instr) {
      return new UndefinedInstruction(instr);
    }

    public ARM_Instruction createIntMultiply(short instr) {
      return new IntMultiply(new ARM_Instructions.IntMultiply(instr));
    }
  }
}
