package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder.ARM_InstructionFactory;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.OperandWrapper;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.DataProcessing.Opcode;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.Instruction.Condition;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.arch.arm.os.process.ARM_Registers.OperatingMode;
import org.binarytranslator.generic.decoder.Interpreter;

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
  protected final InterpreterFactory instructionFactory;

  public ARM_Interpreter(ARM_ProcessSpace ps) {
    this.ps = ps;
    this.regs = ps.registers;
    instructionFactory = new InterpreterFactory();
  }

  /** Decodes the instruction at the given address.*/
  public Instruction decode(int pc) {

    int binaryInstruction = ps.memory.loadInstruction32(pc);
    ARM_Instruction instruction = ARM_InstructionDecoder.decode(binaryInstruction, instructionFactory);
    
    if (instruction.getCondition() != Condition.AL) {
      return new ConditionalDecorator(instruction);
    }
    
    return instruction;
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
        value = regs.get(reg);
        
        if (reg == 15)
          value += 8;
        
        return;

      case RegisterShiftedRegister:
      case ImmediateShiftedRegister:
        value = resolveShift(regs, operand);
        return;

      case PcRelative:
        value = regs.get(ARM_Registers.PC) + 8 + operand.getOffset();
        break;
        
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

      int value = regs.get(operand.getRegister());
      
      //consider the "usual" ARM program counter offset
      if (operand.getRegister() == ARM_Registers.PC)
        value += 8;
      
      byte shiftAmount;

      if (operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister)
        shiftAmount = operand.getShiftAmount();
      else {
        shiftAmount = (byte) (regs.get(operand.getShiftingRegister()) & 0xF);
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

      case RRE:
        if (regs.isCarrySet())
          return (value >> 1) | 0x80000000;
        else
          return value >> 1;

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
          value = regs.get(reg);
          
          if (reg == 15)
            value += 8;
          
          return;

        case RegisterShiftedRegister:
        case ImmediateShiftedRegister:
          value = resolveShift(regs, operand);
          return;

        case PcRelative:
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

        int value = regs.get(operand.getRegister());
        
        //consider the "usual" ARM program counter offset
        if (operand.getRegister() == ARM_Registers.PC)
          value += 8;
        
        byte shiftAmount;

        if (operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister)
          shiftAmount = operand.getShiftAmount();
        else {
          shiftAmount = (byte) (regs.get(operand.getShiftingRegister()) & 0xF);
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
            shifterCarryOut = Utils.getBit(value, 31);
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
            shifterCarryOut = Utils.getBit(value, shiftAmount & 0x1F);
            return Integer.rotateRight(value, shiftAmount);
          }

        case RRE:
          shifterCarryOut = (value & 0x1) != 0;

          if (regs.isCarrySet())
            return (value >> 1) | 0x80000000;
          else
            return value >> 1;

        default:
          throw new RuntimeException("Unexpected shift type: "
              + operand.getShiftType());
        }
      }
    }
  }
  
  /** All ARM interpreter instructions implement this interface. */
  private interface ARM_Instruction extends Interpreter.Instruction {
    /** Returns the condition, under which the given instruction will be executed. */
    Condition getCondition();
  }

  /** All ARM instructions that are supposed to be executed conditionally 
   * are decorated with this decorator. 
   * The decorator takes care of checking the individual condition and depending on it, executing the
   * instruction (or not). The instruction classes itself do not check any conditions. */
  private final class ConditionalDecorator implements Interpreter.Instruction {

    protected final ARM_Instruction conditionalInstruction;

    /** Decorates an ARM interpreter instruction, by making it execute conditionally. */
    protected ConditionalDecorator(ARM_Instruction i) {
      conditionalInstruction = i;
    }
    
    public void execute() {
      if (isConditionTrue()) {
        conditionalInstruction.execute();
        
        int nextInstruction = conditionalInstruction.getSuccessor(ps.getCurrentInstructionAddress());
        
        if (nextInstruction != -1)
          ps.setCurrentInstructionAddress(nextInstruction);
      }
      else
        ps.setCurrentInstructionAddress(ps.getCurrentInstructionAddress()+4);
    }

    public int getSuccessor(int pc) {
      //if this instruction is not a jump, then we can tell what the next instruction will be.
      if (conditionalInstruction.getSuccessor(pc) == pc + 4)
        return pc + 4;
      else
        return -1;
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

    /** Most data processing instructions may set the carry flag according to the barrel shifter's carry
     * out value. The (supposed) value of the barrel shifter is stored within this variable. */
    protected boolean shifterCarryOut;

    protected DataProcessing(int instr) {
      super(instr);
    }

    /** If the given OperandWrapper involves shifting a register, then this function will decoder the shift
     * and set the result of the barrel shifter accordingly. */


    /** Returns the value of operand 1 of the data processing instruction. This is always a register value.
     * However, deriving classes may alter this behavior, for example to return a negative register
     * value for a RSB instruction. */
    protected int resolveOperand1() {

      if (Rn == ARM_Registers.PC) {
        return regs.get(Rn) + 8;
      }

      return regs.get(Rn);
    }

    /** Returns the value of the rhs-operand of the data processing instruction. */
    protected int resolveOperand2() {
      ResolvedOperand resolvedOperand2 = ResolvedOperand.resolveWithShifterCarryOut(regs, operand2);
      shifterCarryOut = resolvedOperand2.getShifterCarryOut();
      return resolvedOperand2.getValue();
    }

    public abstract void execute();

    /** Sets the condition field for logical operations. */
    protected final void setFlagsForLogicalOperator(int result) {

      if (updateConditionCodes) {
        if (Rd != 15) {
          regs.setFlags(result < 0, result == 0, shifterCarryOut);
        } else {
          regs.restoreSPSR2CPSR();
        }
      }
    }

    /** Sets the processor flags according to the result of adding <code>lhs</code> and <code>rhs</code>.*/
    protected final void setFlagsForAdd(int lhs, int rhs) {

      if (updateConditionCodes) {
        if (Rd != 15) {
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

      if (updateConditionCodes) {
        if (Rd != 15) {
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

    public int getSuccessor(int pc) {
      if (Rd != 15)
        return pc + 4;
      else
        return -1;
    }
  }

  /** Binary and. <code>Rd = op1 & op2 </code>.*/
  private final class DataProcessing_And extends DataProcessing {

    protected DataProcessing_And(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() & resolveOperand2();
      regs.set(Rd, result);
      setFlagsForLogicalOperator(result);
    }
  }

  /** Exclusive or. <code>Rd = op1 ^ op2 </code>.*/
  private final class DataProcessing_Eor extends DataProcessing {

    protected DataProcessing_Eor(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() ^ resolveOperand2();
      regs.set(Rd, result);
      setFlagsForLogicalOperator(result);
    }
  }

  /** Add. <code>Rd = op1 + op2 </code>.*/
  private final class DataProcessing_Add extends DataProcessing {

    public DataProcessing_Add(int instr) {
      super(instr);
    }

    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();
      int result = operand1 + operand2;

      regs.set(Rd, result);
      setFlagsForAdd(operand1, operand2);
    }
  }

  /** Subtract. <code>Rd = op1 - op2 </code>.*/
  private final class DataProcessing_Sub extends DataProcessing {

    public DataProcessing_Sub(int instr) {
      super(instr);
    }
    
    @Override
    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();
      int result = operand1 - operand2;

      regs.set(Rd, result);
      setFlagsForSub(operand1, operand2);
    }
  }

  /** Reverse subtract. <code>Rd = op2 - op1</code>.*/
  private final class DataProcessing_Rsb extends DataProcessing {

    protected DataProcessing_Rsb(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();
      int result = operand2 - operand1;

      regs.set(Rd, result);
      setFlagsForSub(operand2, operand1);
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
    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();

      if (regs.isCarrySet()) {
        if (operand1 != Integer.MAX_VALUE) {
          operand1++;
        } else if (operand2 != Integer.MAX_VALUE) {
          operand2++;
        } else {
          regs.setFlags(operand1 > 0, operand1 != 0, true, true);
          return;
        }
      }

      int result = operand1 + operand2;

      regs.set(Rd, result);
      setFlagsForAdd(operand1, operand2);
    }
  }

  /** Subtract with carry. <code>Rd = op1 - op2 + CARRY</code>.*/
  private class DataProcessing_Sbc extends DataProcessing {

    protected DataProcessing_Sbc(int instr) {
      super(instr);
    }
    
    public void execute() {
      int operand1 = resolveOperand1();
      int operand2 = resolveOperand2();

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
      setFlagsForSub(operand1, operand2);
    }
  }

  /** Reserve subtract with carry. <code>Rd = -op1 + op2 + CARRY</code>.*/
  private final class DataProcessing_Rsc extends DataProcessing_Sbc {

    protected DataProcessing_Rsc(int instr) {
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
  private final class DataProcessing_Tst extends DataProcessing {

    protected DataProcessing_Tst(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForLogicalOperator(resolveOperand1() & resolveOperand2());
    }
  }

  /** Sets the flags according to the exclusive-or of two values.
   * <code>Flags = op1 ^ op2</code> */
  private final class DataProcessing_Teq extends DataProcessing {

    protected DataProcessing_Teq(int instr) {
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

    protected DataProcessing_Cmp(int instr) {
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

    protected DataProcessing_Cmn(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForAdd(resolveOperand1(), resolveOperand2());
    }
  }

  /** Binary or. <code>Rd = op1 | op2</code>. */
  private final class DataProcessing_Orr extends DataProcessing {

    protected DataProcessing_Orr(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() | resolveOperand2();
      regs.set(Rd, result);
      setFlagsForLogicalOperator(result);
    }
  }

  private final class DataProcessing_Mov extends DataProcessing {

    protected DataProcessing_Mov(int instr) {
      super(instr);
    }

    @Override
    /** Moves a value into a register .*/
    public void execute() {
      int result = resolveOperand2();
      regs.set(Rd, result);
      setFlagsForLogicalOperator(result);
    }
  }

  /** Bit clear. Clear bits in a register by a mask given by a second operand. 
   * <code>Rd =  op1 & (~op2)</code>.*/
  private final class DataProcessing_Bic extends DataProcessing {

    protected DataProcessing_Bic(int instr) {
      super(instr);
    }

    @Override
    /** Clear bits in a register by a mask given by a second operand. */
    public void execute() {
      int result = resolveOperand1() & (~resolveOperand2());
      regs.set(Rd, result);
      setFlagsForLogicalOperator(result);
    }
  }

  /** Move and negate. Moves an integer between two registers, negating it on the way. 
   * <code>Rd = -op2</code>.*/
  private final class DataProcessing_Mvn extends DataProcessing {

    protected DataProcessing_Mvn(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = ~resolveOperand2();
      regs.set(Rd, result);
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
    public void execute() {
      int result = Integer.numberOfLeadingZeros(resolveOperand2());
      regs.set(Rd, result);
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

    public void execute() {
      int memAddr = regs.get(Rn);
      
      //swap exchanges the value of a memory address with the value in a register
      if (swapByte) {
        int tmp = ps.memory.load32(memAddr);
        ps.memory.store32(memAddr, regs.get(Rm));
        
        //according to the ARM architecture reference, the value loaded from a memory address is rotated
        //by the number of ones in the first two bits of the address
        regs.set(Rd, Integer.rotateRight(tmp, (memAddr & 0x3) * 8));
      }
      else {
        int tmp = ps.memory.loadUnsigned8(memAddr);
        ps.memory.store8(memAddr, regs.get(Rm) & 0xFF);
        regs.set(Rm, tmp);
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

    public void execute() {
      //build the address, which generally ignores the last two bits
      int startAddress = regs.get(baseRegister) & 0xFFFFFFFC;
      
      if (!incrementBase) {
        if (postIndexing) {
          //post-indexing, backward reading
          startAddress -= (registerCount + (transferPC ? 1 : 0)) * 4;
        } else {
          //pre-indexing, backward-reading
          startAddress -= (registerCount + (transferPC ? 2 : 1)) * 4;
        }
      } else {
        if (postIndexing) {
          //post-indexing, forward reading
          startAddress -= 4;
        } else {
          //pre-indexing, forward reading
          //no need to adjust the start address
        }
      }
      int nextAddress = startAddress;
      
      OperatingMode previousMode = ps.registers.getOperatingMode();
      
      //if we should transfer the user mode registers...
      if (forceUser) {
        //... then change the current register map, but do NOT change the current processor mode
        ps.registers.switchOperatingMode(OperatingMode.USR);
        ps.registers.setOperatingModeWithoutRegisterLayout(previousMode);
      }

      //are we supposed to load or store multiple registers?
      if (isLoad) {
        int nextReg = 0;

        while (registersToTransfer[nextReg] != -1) {
          nextAddress += 4;
          regs.set(registersToTransfer[nextReg++], ps.memory
              .load32(nextAddress));
        }

        //if we also transferred the program counter
        if (transferPC) {
          nextAddress += 4;
          int newpc = ps.memory.load32(nextAddress);
          regs.set(ARM_Registers.PC, newpc & 0xFFFFFFFE);

          if (forceUser) {
            //when we are transferring the PC with a forced-user transfer, then we also want to
            //restore the CPSR from the SPSR.
            //However, at the moment our register layout is different from our operating mode.
            //Therefore, sync both first by switching the operating mode to user (which is what our register layout
            //is anyway).
            regs.setOperatingModeWithoutRegisterLayout(OperatingMode.USR);
            regs.restoreSPSR2CPSR();
            
            //there is no write-back for this instruction.
            return;
          }
          else {
            //shall we switch to thumb mode
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
          ps.memory.store32(nextAddress, regs.get(15) + 8);
        }
      }

      //restore the register layout, if we were transferring the user mode registers
      if (forceUser) {
        ps.registers.setOperatingModeWithoutRegisterLayout(OperatingMode.USR);
        ps.registers.switchOperatingMode(previousMode);
      }

      if (writeBack) {
        //write the last address we read from back to a register
        if (!incrementBase) {
          //backward reading
          if (postIndexing) {
            //backward reading, post-indexing
            nextAddress = startAddress;
          }
          else {
            //backward reading, pre-indexing
            nextAddress = startAddress + 4;
          }
        }
        else {
          //forward reading
          if (postIndexing)
            nextAddress += 4;
        }

        regs.set(baseRegister, nextAddress);
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

    public void execute() {
      //remember the previous address, taking ARM's register offset into account
      int previousAddress = regs.get(ARM_Registers.PC);

      //if we're supposed to link, then write the previous address into the link register
      if (link)
        regs.set(ARM_Registers.LR, previousAddress + 4);
    }

    public int getSuccessor(int pc) {
      return pc + getOffset() + 8;
    }
  }

  /** Branch to another instruction  address and switch between ARM32 and Thumb code on the way.*/
  private final class BranchExchange extends ARM_Instructions.BranchExchange
      implements ARM_Instruction {

    public BranchExchange(int instr) {
      super(instr);
    }

    public void execute() {
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
        regs.set(ARM_Registers.LR, previousAddress - 4);
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

    public void execute() {
      //get the two operands
      int operand1 = regs.get(Rm);
      int operand2 = regs.get(Rs);
      
      //if any of the operands is the PC, consider ARM's PC offset
      if (Rm == ARM_Registers.PC)
        operand1 += 8;
      
      if (Rs == ARM_Registers.PC)
        operand2 += 8;
      
      //calculate the result
      int result = regs.get(Rm) * regs.get(Rs);

      if (accumulate) {
        result += regs.get(Rn);
        
        //also consider ARM's PC offset when adding the accumulate register
        if (Rn == ARM_Registers.PC)
          result += 8;
      }

      //and finally, update the register map
      regs.set(Rd, result);

      if (updateConditionCodes) {
        regs.setFlags(result < 0, result == 0);
      }
    }

    public int getSuccessor(int pc) {
      if (Rd != ARM_Registers.PC)
        return pc + 4;
      else
        return -1;
    }
  }

  /** Move the value of the program status register into a register. */
  private final class MoveFromStatusRegister extends
      ARM_Instructions.MoveFromStatusRegister implements
      ARM_Instruction {

    public MoveFromStatusRegister(int instr) {
      super(instr);
    }

    public void execute() {

      //do we have to transfer the saved or the current PSR?
      if (transferSavedPSR) {
        regs.set(Rd, regs.getSPSR());
      } 
      else {
        regs.set(Rd, regs.getCPSR());
      }
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

    public void execute() {
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

      //create a new CPSR value according to what pieces of the CPSR we are actually required to set
      if (!transferControl || !inPrivilegedMode) {
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
        regs.setCPSR(new_psr);
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

    public void execute() {
      ps.doSysCall();
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
    private int resolveOffset() {
      int addrOffset = ResolvedOperand.resolve(regs, offset);

      if (positiveOffset)
        return addrOffset;
      else
        return -1 * addrOffset;
    }

    /** Resolves the address of the memory slot, that is involved in the transfer. */
    private int resolveAddress() {

      //acquire the base address
      int base = regs.get(Rn);
      
      //take ARM's PC offset into account
      if (Rn == 15)
        base += 8;

      //if we are not pre-indexing, then just use the base register for the memory access
      if (!preIndexing)
        return base;
      
      return base + resolveOffset();
    }

    public void execute() {
      //should we simulate a user-mode memory access? If yes, store the current mode and fake a switch
      //to user mode.
      OperatingMode previousMode = null;
      if (forceUserMode) {
        previousMode = ps.registers.getOperatingMode();
        ps.registers.setOperatingModeWithoutRegisterLayout(ARM_Registers.OperatingMode.USR);
      }

      //get the address of the memory, that we're supposed access
      int address = resolveAddress();

      if (isLoad) {
        //we are loading a value from memory. Load it into this variable.
        int value;

        switch (size) {
        
        case Word:
          value = ps.memory.load32(address);
          
          //according to the ARM reference, the last two bits cause the value to be right-rotated
          value = Integer.rotateRight(value, (address & 0x3) * 8);
          break;

        case HalfWord:
          if (signExtend)
            value = ps.memory.loadSigned16(address);
          else
            value = ps.memory.loadUnsigned16(address);
          break;

        case Byte:
          if (signExtend)
            value = ps.memory.loadSigned8(address);
          else
            value = ps.memory.loadUnsigned8(address);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + size);
        }

        //finally, write the variable into a register
        regs.set(Rd, value);
      } 
      else {
        //we are store a value from a register to memory.
        int value = regs.get(Rd);

        switch (size) {
        case Word:
          ps.memory.store32(address & 0xFFFFFFFE, value);
          break;
          
        case HalfWord:
          ps.memory.store16(address, value & 0xFFFF);
          break;
          
        case Byte:
          ps.memory.store8(address, value & 0xFF);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + size);
        }
      }
      
      //if we were writing in user mode, then switch back to our previous operating mode
      if (forceUserMode) {
        ps.registers.setOperatingModeWithoutRegisterLayout(previousMode);
      }      

      //should the memory address, which we accessed, be written back into a register? 
      //This is used for continuous memory accesses
      if (writeBack) {
        if (preIndexing) {
          regs.set(Rn, address);
        }
        else {
          //add the offset to the base address and write the result back into Rn
          regs.set(Rn, address + resolveOffset());
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

    public void execute() {
      ps.doUndefinedInstruction();
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

    public void execute() {
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
      //TODO: Implement multiplications with longs
      throw new RuntimeException("Long Multiplications are not yet supported.");
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
