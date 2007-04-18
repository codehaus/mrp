package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder.ARM_InstructionFactory;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.OperandWrapper;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.DataProcessing.Opcode;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.Instruction.Condition;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.generic.decoder.Interpreter;

public class ARM_Interpreter implements Interpreter {

  protected final ARM_ProcessSpace ps;
  protected final ARM_Registers regs;
  protected final InterpreterFactory instructionFactory;

  public ARM_Interpreter(ARM_ProcessSpace ps) {
    this.ps = ps;
    this.regs = ps.registers;
    instructionFactory = new InterpreterFactory();
  }

  public Instruction decode(int pc) {

    int binaryInstruction = ps.memory.loadInstruction32(pc);
    ARM_Instruction instruction = ARM_InstructionDecoder.decode(binaryInstruction, instructionFactory);
    
    if (instruction.getCondition() != Condition.AL) {
      return new ConditionalDecorator(instruction);
    }
    
    return instruction;
  }
  
  private interface ARM_Instruction extends Interpreter.Instruction {
    Condition getCondition();
  }

  private final class ConditionalDecorator implements Interpreter.Instruction {

    protected final ARM_Instruction conditionalInstruction;

    protected ConditionalDecorator(ARM_Instruction i) {
      conditionalInstruction = i;
    }
    
    public void execute() {
      if (isConditionTrue())
        conditionalInstruction.execute();
    }

    public int getSuccessor(int pc) {
      return -1;
    }
    
    private boolean isConditionTrue() {
      switch (conditionalInstruction.getCondition()) {
      case AL:
        return true;
        
      case CC:
        return !regs.isCarrySet();
        
      case CS:
        return regs.isCarrySet();
        
      case EQ:
        return regs.isZeroSet();
        
      case GE:
        return regs.isNegativeSet() == regs.isOverflowSet();
        
      case GT:
        return (regs.isNegativeSet() == regs.isOverflowSet()) && regs.isZeroSet();
        
      case HI:
        return regs.isCarrySet() && !regs.isZeroSet();
        
      case LE:
        return regs.isZeroSet() || (regs.isNegativeSet() == regs.isOverflowSet());
        
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
  }

  private abstract class DataProcessing extends ARM_Instructions.DataProcessing
      implements ARM_Instruction {

    protected boolean shifterCarryOut;

    protected DataProcessing(int instr) {
      super(instr);
    }

    protected final int resolveShift(OperandWrapper operand) {

      int value = regs.get(operand.getRegister());
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
        return value >>> shiftAmount;

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
        return value >> shiftAmount;

      case ROR:

        if (shiftAmount == 0) {
          shifterCarryOut = regs.isCarrySet();
          return value;
        } else {
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

    protected int resolveOperand1() {

      if (operandRegister == ARM_Registers.PC) {
        return regs.get(operandRegister) + 8;
      }

      return regs.get(operandRegister);
    }

    protected int resolveOperand2() {
      int value;

      switch (operand2.getType()) {
      case Immediate:
        value = operand2.getImmediate();

        if (operand2.getShiftAmount() == 0)
          shifterCarryOut = regs.isCarrySet();
        else
          shifterCarryOut = (value & 0x80000000) != 0;
        
        return value;

      case Register:
        shifterCarryOut = regs.isCarrySet();
        return regs.get(operand2.getRegister());

      case RegisterShiftedRegister:
      case ImmediateShiftedRegister:
        return resolveShift(operand2);

      case PcRelative:
      default:
        throw new RuntimeException("Unexpected wrapped operand type: "
            + operand2.getType());
      }
    }

    public abstract void execute();

    protected final void setFlagsForResult(int result) {

      if (updateConditionCodes) {
        if (Rd != 15) {
          regs.setFlags(result < 0, result == 0, shifterCarryOut);
        } else {
          regs.restoreSPSR2CPSR();
        }
      }
    }

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

    public int getSuccessor(int pc) {
      if (Rd != 15)
        return pc + 4;
      else
        return -1;
    }
  }

  private final class DataProcessing_And extends DataProcessing {

    protected DataProcessing_And(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() & resolveOperand2();
      regs.set(Rd, result);
      setFlagsForResult(result);
    }
  }

  private final class DataProcessing_Eor extends DataProcessing {

    protected DataProcessing_Eor(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() ^ resolveOperand2();
      regs.set(Rd, result);
      setFlagsForResult(result);
    }
  }

  private class DataProcessing_Add extends DataProcessing {

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

  private final class DataProcessing_Sub extends DataProcessing_Add {

    public DataProcessing_Sub(int instr) {
      super(instr);
    }

    @Override
    protected int resolveOperand2() {
      return -super.resolveOperand2();
    }
  }

  private final class DataProcessing_Rsb extends DataProcessing_Add {

    protected DataProcessing_Rsb(int instr) {
      super(instr);
    }

    @Override
    protected int resolveOperand1() {
      return -super.resolveOperand1();
    }
  }

  private class DataProcessing_Adc extends DataProcessing_Add {

    protected int cachedOperand1;

    protected int cachedOperand2;

    protected DataProcessing_Adc(int instr) {
      super(instr);
    }

    @Override
    protected int resolveOperand1() {
      return cachedOperand1;
    }

    @Override
    protected int resolveOperand2() {
      return cachedOperand2;
    }

    @Override
    public void execute() {
      cachedOperand1 = super.resolveOperand1();
      cachedOperand2 = super.resolveOperand2();

      if (regs.isCarrySet()) {
        if (cachedOperand1 != Integer.MAX_VALUE) {
          cachedOperand1++;
        } else if (cachedOperand2 != Integer.MAX_VALUE) {
          cachedOperand2++;
        } else {
          regs.setFlags(cachedOperand1 > 0, cachedOperand1 != 0, true, true);
          return;
        }
      }

      super.execute();
    }
  }

  private class DataProcessing_Sbc extends DataProcessing_Adc {

    protected DataProcessing_Sbc(int instr) {
      super(instr);
    }

    @Override
    protected int resolveOperand2() {
      return -cachedOperand2;
    }
  }

  private class DataProcessing_Rsc extends DataProcessing_Adc {

    protected DataProcessing_Rsc(int instr) {
      super(instr);
    }

    @Override
    protected int resolveOperand1() {
      return -cachedOperand1;
    }
  }

  private class DataProcessing_Tst extends DataProcessing {

    protected DataProcessing_Tst(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForResult(resolveOperand1() & resolveOperand2());
    }
  }

  private class DataProcessing_Teq extends DataProcessing {

    protected DataProcessing_Teq(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForResult(resolveOperand1() ^ resolveOperand2());
    }
  }

  private class DataProcessing_Cmp extends DataProcessing {

    protected DataProcessing_Cmp(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForAdd(resolveOperand1(), -resolveOperand2());
    }
  }

  private class DataProcessing_Cmn extends DataProcessing {

    protected DataProcessing_Cmn(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForAdd(resolveOperand1(), resolveOperand2());
    }
  }

  private class DataProcessing_Orr extends DataProcessing {

    protected DataProcessing_Orr(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() | resolveOperand2();
      regs.set(Rd, result);
      setFlagsForResult(result);
    }
  }

  private class DataProcessing_Mov extends DataProcessing {

    protected DataProcessing_Mov(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand2();
      regs.set(Rd, result);
      setFlagsForResult(result);
    }
  }

  private class DataProcessing_Bic extends DataProcessing {

    protected DataProcessing_Bic(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = resolveOperand1() & (~resolveOperand2());
      regs.set(Rd, result);
      setFlagsForResult(result);
    }
  }

  private class DataProcessing_Mvn extends DataProcessing {

    protected DataProcessing_Mvn(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = ~resolveOperand2();
      regs.set(Rd, result);
      setFlagsForResult(result);
    }
  }
  
  private class DataProcessing_Clz extends DataProcessing {

    protected DataProcessing_Clz(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = Integer.numberOfLeadingZeros(resolveOperand1());
      regs.set(Rd, result);
    }
  }

  private class Swap extends ARM_Instructions.Swap implements
  ARM_Instruction {

    public Swap(int instr) {
      super(instr);
    }

    public void execute() {
      int memAddr = regs.get(Rn);

      //swap exchanges the value of a memory address with the value in a register
      int tmp = ps.memory.load32(memAddr);
      ps.memory.store16(memAddr, regs.get(Rm));

      //according to the ARM architecture reference, the value loaded from a memory address is rotated
      //according to the number of ones in the first two bits of the address
      regs.set(Rd, Integer.rotateRight(tmp, (memAddr & 0x3) * 8));
    }

    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }

  private class BlockDataTransfer extends ARM_Instructions.BlockDataTransfer
      implements ARM_Instruction {

    /** the lowest address that we're reading a register from / writing a register to */
    private final int startAddress;

    /** An array that contains the registers to be transferd in ascending order. 
     * The list is delimited by setting the entry after the last register index to -1.
     * The PC is not included in this list, if it shall be transferred.  */
    private final int[] registersToTransfer = new int[16];

    /** True if the PC should be transferred to, false otherwise. */
    private final boolean transferPC;

    public BlockDataTransfer(int instr) {
      super(instr);

      transferPC = transferRegister(15);
      int registerCount = 0;

      for (int i = 0; i < 14; i++)
        if (transferRegister(i)) {
          registersToTransfer[registerCount++] = i;
        }

      registersToTransfer[registerCount] = -1;

      //build the address, which generally ignores the last two bits
      if (!incrementBase) {
        if (postIndexing) {
          //post-indexing, backward reading
          startAddress = regs.get(baseRegister) & 0xFFFFFFFC
              - (registerCount + (transferPC ? -1 : 0)) * 4;
        } else {
          //pre-indexing, backward-reading
          startAddress = regs.get(baseRegister) & 0xFFFFFFFC
              - (registerCount + (transferPC ? 1 : 0)) * 4;
        }
      } else {
        if (postIndexing) {
          //post-indexing, forward reading
          startAddress = regs.get(baseRegister) & 0xFFFFFFFC - 4;
        } else {
          //pre-indexing, forward reading
          startAddress = regs.get(baseRegister) & 0xFFFFFFFC;
        }
      }
    }

    public void execute() {
      int nextAddress = startAddress;

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

          //shall we switch to thumb mode
          regs.setThumbMode((newpc & 0x1) != 0);
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
          ps.memory.store32(nextAddress, regs.get(15));
        }
      }

      if (writeBack) {
        //write the last address we read from back to a register
        //TODO: Check if we have to consider the different cases?
        if (!incrementBase)
          nextAddress = startAddress;

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

  private class Branch extends ARM_Instructions.Branch implements
  ARM_Instruction {

    public Branch(int instr) {
      super(instr);
    }

    public void execute() {
      int previousAddress = regs.get(ARM_Registers.PC);

      //jump to the new address
      regs.set(ARM_Registers.PC, previousAddress + getOffset());

      //if we're supposed to link, then write the previous address into the link register
      if (link)
        regs.set(ARM_Registers.LR, previousAddress + 4);
    }

    public int getSuccessor(int pc) {
      return pc + getOffset() + 8;
    }
  }

  private class BranchExchange extends ARM_Instructions.BranchExchange
      implements ARM_Instruction {

    public BranchExchange(int instr) {
      super(instr);
    }

    public void execute() {
      int previousAddress = regs.get(ARM_Registers.PC);
      boolean thumb;
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
        regs.set(ARM_Registers.LR, previousAddress + 4);
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

  private class IntMultiply extends ARM_Instructions.IntMultiply implements
  ARM_Instruction {

    protected IntMultiply(int instr) {
      super(instr);
    }

    public void execute() {
      int result = regs.get(Rm) * regs.get(Rs);

      if (accumulate)
        result += regs.get(Rn);

      regs.set(Rd, result);

      if (updateConditionCodes) {
        regs.setFlags(result < 0, result == 0);
      }
    }

    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }

  private class MoveFromStatusRegister extends
      ARM_Instructions.MoveFromStatusRegister implements
      ARM_Instruction {

    public MoveFromStatusRegister(int instr) {
      super(instr);
    }

    public void execute() {
      int statusRegisterValue;

      if (transferSavedPSR) {
        statusRegisterValue = regs.getSPSR();
      } else {
        statusRegisterValue = regs.getCPSR();
      }

      regs.set(Rd, statusRegisterValue);
    }

    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }

  private class SoftwareInterrupt extends ARM_Instructions.SoftwareInterrupt
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

  private class SingleDataTransfer extends ARM_Instructions.SingleDataTransfer
      implements ARM_Instruction {

    public SingleDataTransfer(int instr) {
      super(instr);
    }

    private int resolveAddress() {

      //acquire the base address
      int base = regs.get(Rn);
      
      //take ARM's PC offset into account
      if (Rn == 15)
        base += 8;

      //if we are not pre-indexing, then just use the base register for the memory access
      if (!preIndexing)
        return base;

      switch (offset.getType()) {
      case Immediate:
        if (positiveOffset)
          return base + offset.getImmediate();
        else
          return base - offset.getImmediate();

      case Register:
        int offsetRegister = regs.get(offset.getRegister());
        if (offset.getRegister() == ARM_Registers.PC) {
          offsetRegister += 8;
        }

        if (positiveOffset)
          return base + offsetRegister;
        else
          return base - offsetRegister;

      case ImmediateShiftedRegister:
        if (offset.getRegister() == 15)
          throw new RuntimeException(
              "PC-relative memory accesses are not yet supported.");

        int addrOffset = regs.get(offset.getRegister());

        switch (offset.getShiftType()) {
        case ASR:
          addrOffset = addrOffset >>> offset.getShiftAmount();
          break;

        case LSL:
          addrOffset = addrOffset << offset.getShiftAmount();
          break;

        case LSR:
          addrOffset = addrOffset >> offset.getShiftAmount();
          break;

        case ROR:
          addrOffset = Integer.rotateRight(addrOffset, offset.getShiftAmount());
          break;

        case RRE:
          if (regs.isCarrySet())
            addrOffset = (addrOffset >> 1) | 0x80000000;
          else
            addrOffset = addrOffset >> 1;
          break;

        default:
          throw new RuntimeException("Unexpected shift type: "
              + offset.getShiftType());
        }

      case PcRelative:
      case RegisterShiftedRegister:
      default:
        throw new RuntimeException("Unexpected operand type: "
            + offset.getType());
      }

    }

    public void execute() {
      if (forceUserMode) {
        //TODO: Implement user mode memory access
        throw new RuntimeException(
            "Forced user mode memory access is not yet supported.");
      }

      int address = resolveAddress();

      if (isLoad) {
        int value;

        switch (size) {
        case Byte:
          if (signExtend)
            value = ps.memory.loadSigned8(address);
          else
            value = ps.memory.loadUnsigned8(address);
          break;

        case HalfWord:
          if (signExtend)
            value = ps.memory.loadSigned16(address);
          else
            value = ps.memory.loadUnsigned16(address);
          break;

        case Word:
          value = ps.memory.load32(address);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + size);
        }

        regs.set(Rd, value);
      } else {
        int value = regs.get(Rd);

        switch (size) {
        case Byte:
          ps.memory.store8(address, value);
          break;

        case HalfWord:
          ps.memory.store16(address, value);
          break;

        case Word:
          ps.memory.store32(address, value);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + size);
        }
      }

      if (writeBack) {
        if (preIndexing)
          regs.set(Rn, address);
        else {
          //TODO: calculate the post-indexed address
          //and set it to Rn
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

  private class UndefinedInstruction implements ARM_Instruction {

    private final int instruction;

    public UndefinedInstruction(int instr) {
      this.instruction = instr;
    }

    public void execute() {
      throw new RuntimeException("Undefined instruction: " + instruction);
    }

    public int getSuccessor(int pc) {
      return -1;
    }

    public Condition getCondition() {
      return Condition.AL;
    }
  }

  class InterpreterFactory implements
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
      throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");
    }

    public ARM_Instruction createCoprocessorDataTransfer(int instr) {
      //    TODO: Implement coprocessor instructions
      throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");
    }

    public ARM_Instruction createCoprocessorRegisterTransfer(int instr) {
      //    TODO: Implement coprocessor instructions
      throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");
    }

    public ARM_Instruction createIntMultiply(int instr) {
      return new IntMultiply(instr);
    }

    public ARM_Instruction createLongMultiply(int instr) {
      throw new RuntimeException("Long Multiplications are not yet supported.");
    }

    public ARM_Instruction createMoveFromStatusRegister(int instr) {
      return new MoveFromStatusRegister(instr);
    }

    public ARM_Instruction createMoveToStatusRegister(int instr) {
      //TODO: Implement Register -> CPSR transfers
      throw new RuntimeException(
          "Modifying the status register using MSR is not yet supported.");
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
