package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder.ARM_InstructionFactory;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.*;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.DataProcessing.Opcode;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.decoder.Interpreter.Instruction;

public class ARM_Interpreter {

  protected final ARM_ProcessSpace ps;

  protected final ARM_Registers regs;

  public ARM_Interpreter(ARM_ProcessSpace ps) {
    this.ps = ps;
    this.regs = ps.registers;
  }

  private final class ConditionalDecorator implements Interpreter.Instruction {

    protected final Interpreter.Instruction conditionalInstruction;

    protected ConditionalDecorator(Interpreter.Instruction i) {
      conditionalInstruction = i;
    }

    public void execute() {
      conditionalInstruction.execute();
    }

    public int getSuccessor(int pc) {
      return -1;
    }
  }

  private abstract class DataProcessing extends ARM_Instructions.DataProcessing
      implements Interpreter.Instruction {

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

        if (shiftAmount > 32)
          shiftAmount = 32;

        if (shiftAmount == 0) {
          shifterCarryOut = regs.isCarrySet();
          return value;
        } else {
          shifterCarryOut = Utils.getBit(value, shiftAmount - 1);
          return value >>> shiftAmount;
        }

      case LSL:

        if (shiftAmount > 32)
          shiftAmount = 32;

        if (shiftAmount == 0) {
          shifterCarryOut = regs.isCarrySet();
          return value;
        } else {
          shifterCarryOut = Utils.getBit(value, 32 - shiftAmount);
          return value << shiftAmount;
        }

      case LSR:

        if (shiftAmount > 32)
          shiftAmount = 32;

        if (shiftAmount == 0) {
          shifterCarryOut = regs.isCarrySet();
          return value;
        } else {
          shifterCarryOut = Utils.getBit(value, shiftAmount - 1);
          return value >> shiftAmount;
        }

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

    protected int resolveOperand2() {
      int value;

      switch (operand2.getType()) {
      case Immediate:
        value = operand2.getImmediate();

        if (operand2.getShiftAmount() == 0)
          shifterCarryOut = regs.isCarrySet();
        else
          shifterCarryOut = (value & 0x80000000) != 0;

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
      if (Rd == 15)
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
      int result = regs.get(Rn) & resolveOperand2();
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
      int result = regs.get(Rn) ^ resolveOperand2();
      regs.set(Rd, result);
      setFlagsForResult(result);
    }
  }

  private class DataProcessing_Add extends DataProcessing {

    public DataProcessing_Add(int instr) {
      super(instr);
    }
    
    protected int resolveOperand1() {
      return regs.get(Rn);
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
      setFlagsForResult(regs.get(Rn) & resolveOperand2());
    }
  }
  
  private class DataProcessing_Teq extends DataProcessing {

    protected DataProcessing_Teq(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForResult(regs.get(Rn) ^ resolveOperand2());
    }
  }
  
  private class DataProcessing_Cmp extends DataProcessing {

    protected DataProcessing_Cmp(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForAdd(regs.get(Rn), -resolveOperand2());
    }
  }
  
  private class DataProcessing_Cmn extends DataProcessing {

    protected DataProcessing_Cmn(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      setFlagsForAdd(regs.get(Rn), resolveOperand2());
    }
  }
  
  private class DataProcessing_Orr extends DataProcessing {

    protected DataProcessing_Orr(int instr) {
      super(instr);
    }

    @Override
    public void execute() {
      int result = regs.get(Rn) | resolveOperand2();
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
      int result = regs.get(Rn) & (~resolveOperand2());
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
  
  private class Swap extends ARM_Instructions.Swap implements Interpreter.Instruction {

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
      return pc+4;
    }
  }
  
  private class BlockDataTransfer extends ARM_Instructions.BlockDataTransfer implements Interpreter.Instruction {

    public BlockDataTransfer(int instr) {
      super(instr);
    }

    public void execute() {
      //start address ignores the last two bits
      int startAddress = regs.get(baseRegister) & 0xFFFFFFFC;

      //build a map of registers that are to be transfered
      int registerCount = 0;
      boolean transferPC = false;
      int[] registersToTransfer = new int[16];
      
      for (int i = 0; i < 14; i++)
        if (transferRegister(i)) {
          registersToTransfer[registerCount++] = i;
        }
      
      //also remember if we're supposed to transfer the pc, but don't include it in the register list
      transferPC = transferRegister(15);
      
      if (!incrementBase) {
        if (postIndexing)
          startAddress -= (registerCount + (transferPC ? -1 : 0)) * 4; //post-indexing, backward reading
        else
          startAddress -= (registerCount + (transferPC ? 1 : 0)) * 4; //pre-indexing, backward-reading
      }
      else if (postIndexing) {
        //post-indexing, forward reading
        startAddress -= 4;
      }
      
      //are we supposed to load or store multiple registers?
      if (isLoad) {
        //read the actual registers
        for (int i = 0; i < registerCount; i++) {
          startAddress += 4;
          regs.set(registersToTransfer[i], ps.memory.load32(startAddress));
        }
        
        //if we also transferred the program counter
        if (transferPC) {
          int newpc = ps.memory.load32(startAddress + 4);
          regs.set(ARM_Registers.PC, newpc & 0xFFFFFFFE);
          
          //shall we switch to thumb mode
          regs.setThumbMode((newpc & 0x1) != 0);
        }
      }
      else {
        //also transfer the program counter, if requested so
        if (transferPC)
          registersToTransfer[registerCount++] = 15;
        
        for (int i = 0; i < registerCount; i++) {
          startAddress += 4;
          ps.memory.store32(startAddress, regs.get(i));
        }
      }
    }

    public int getSuccessor(int pc) {
      //if we're loading values into the PC, then we can't tell where this instruction will be going
      if (isLoad && transferRegister(ARM_Registers.PC))
        return -1;
      else
        return pc+4;
    }
  }
  
  private class Branch extends ARM_Instructions.Branch implements Interpreter.Instruction {

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
      return pc + getOffset();
    }
  }
  
  private class BranchExchange extends ARM_Instructions.BranchExchange implements Interpreter.Instruction {

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
        throw new RuntimeException("Unexpected Operand type: " + target.getType());
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
      }
      else {
        //otherwise we can't predict it
        return -1;
      }
    }
  }
  
  private class CountLeadingZeros extends ARM_Instructions.CountLeadingZeros implements Interpreter.Instruction {

    public CountLeadingZeros(int instr) {
      super(instr);
    }

    public void execute() {
      int leadingZeros = Integer.numberOfLeadingZeros(regs.get(Rm));
      regs.set(Rd, leadingZeros);
    }

    public int getSuccessor(int pc) {
      return pc+4;
    }
  }
  
  private class IntMultiply extends ARM_Instructions.IntMultiply implements Interpreter.Instruction {

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
      return pc+4;
    }
  }

  private class MoveFromStatusRegister extends ARM_Instructions.MoveFromStatusRegister implements Interpreter.Instruction {

    public MoveFromStatusRegister(int instr) {
      super(instr);
    }

    public void execute() {
      int statusRegisterValue;
      
      if (transferSavedPSR) {
        statusRegisterValue = regs.getSPSR();
      }
      else {
        statusRegisterValue = regs.getCPSR();
      }
      
      regs.set(Rd, statusRegisterValue);
    }

    public int getSuccessor(int pc) {
      return pc+4;
    }
  }
  
  private class SoftwareInterrupt extends ARM_Instructions.SoftwareInterrupt implements Interpreter.Instruction {

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
  
  private class SingleDataTransfer extends ARM_Instructions.SingleDataTransfer implements Interpreter.Instruction {

    public SingleDataTransfer(int instr) {
      super(instr);
    }

    public void execute() {
      
    }

    public int getSuccessor(int pc) {
      //if we're loading to the PC, then the next instruction is undefined
      if (Rd == ARM_Registers.PC)
        return -1;
      
      return pc+4;
    }
    
  }
  
  private class UndefinedInstruction implements Interpreter.Instruction {
    
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
  }

  class InterpreterFactory implements
      ARM_InstructionFactory<Interpreter.Instruction> {

    public Interpreter.Instruction createDataProcessing(int instr) {
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

      default:
        throw new RuntimeException("Unexpected Data Procesing opcode: "
            + opcode);
      }
    }

    public Instruction createBlockDataTransfer(int instr) {
      return new BlockDataTransfer(instr);
    }

    public Instruction createBranch(int instr) {
      return new Branch(instr);
    }

    public Instruction createBranchExchange(int instr) {
      return new BranchExchange(instr);
    }

    public Instruction createCoprocessorDataProcessing(int instr) {
      //TODO: Implement coprocessor instructions
      throw new RuntimeException("Coprocessor instructions are not yet supported.");
    }

    public Instruction createCoprocessorDataTransfer(int instr) {
//    TODO: Implement coprocessor instructions
      throw new RuntimeException("Coprocessor instructions are not yet supported.");
    }

    public Instruction createCoprocessorRegisterTransfer(int instr) {
//    TODO: Implement coprocessor instructions
      throw new RuntimeException("Coprocessor instructions are not yet supported.");
    }

    public Instruction createCountLeadingZeros(int instr) {
      return new CountLeadingZeros(instr);
    }

    public Instruction createIntMultiply(int instr) {
      return new IntMultiply(instr);
    }

    public Instruction createLongMultiply(int instr) {
      throw new RuntimeException("Long Multiplications are not yet supported.");
    }

    public Instruction createMoveFromStatusRegister(int instr) {
      return new MoveFromStatusRegister(instr);
    }

    public Instruction createMoveToStatusRegister(int instr) {
      //TODO: Implement Register -> CPSR transfers
      throw new RuntimeException("Modifying the status register using MSR is not yet supported.");
    }

    public Instruction createSingleDataTransfer(int instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public Instruction createSoftwareInterrupt(int instr) {
      return new SoftwareInterrupt(instr);
    }

    public Instruction createSwap(int instr) {
      return new Swap(instr);
    }

    public Instruction createUndefinedInstruction(int instr) {
      return new UndefinedInstruction(instr);
    }
  }

}
