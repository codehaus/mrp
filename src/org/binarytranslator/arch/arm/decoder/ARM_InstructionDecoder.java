package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.generic.decoder.InstructionDecoder;
import org.jikesrvm.compilers.opt.ir.*;

public class ARM_InstructionDecoder extends InstructionDecoder {
  
  abstract static class BasicDecoder implements OPT_Operators {
    /** Return the value of a single bit. Note that the bit is 0-based.*/
    public static final boolean getBit(int word, int bit) {
      if (DBT.VerifyAssertions) DBT._assert(bit >= 0 && bit <= 31);
      return (word & (1 << bit)) != 0;
    }
    
    /** Transfer a subsequence of bits within (word) into an integer. Note that all bits are 0-based. */
    public static final int getBits(int word, int from, int to) {
      if (DBT.VerifyAssertions) DBT._assert(from < to && from >= 0 && to <= 31);
      return (word & ((1 << (to+1)) - 1)) >> from;
    }
    
    protected abstract int translate(ARM2IR arm2ir, ARM_ProcessSpace ps, ARM_Laziness lazy, int pc, int instr);
  }
  
  /** Translates LDR and STR instructions */
  static class LDR_STR_Decoder extends BasicDecoder {

    @Override
    protected int translate(ARM2IR arm2ir, ARM_ProcessSpace ps, ARM_Laziness lazy, int pc, int instr) {
      boolean immediateOperand = getBit(instr, 25);
      boolean preIndex = getBit(instr, 24);
      boolean positiveOffset = getBit(instr, 23);
      boolean transferByte = getBit(instr, 22);
      boolean writeBack = getBit(instr, 21);
      boolean loadOperation = getBit(instr, 20);
      int offset = getBits(instr, 0, 11);
      
      int baseRegister = getBits(instr, 16, 19);
      int destRegister = getBits(instr, 12, 15);
      
      System.out.println("Decoding LDR/STR with: ");
      System.out.println("Imm: " + immediateOperand);
      System.out.println("Pre Index: " + preIndex);
      System.out.println("Offset: " + (positiveOffset ? "+" : "-") + offset);
      System.out.println("Base: " + baseRegister);
      System.out.println("Dest: " + destRegister);
      System.out.println("Load Operation: " + loadOperation);
      System.out.println("Write back: " + writeBack);
      
      //we are only handling a limitied subset of this instruction
      if (DBT.VerifyAssertions) DBT._assert(immediateOperand == false && transferByte == false && writeBack == false && preIndex == true);
      
      //resolve correspoding memory address for this operation
      OPT_Operand memoryAddr;
      
      //take the ARM pipeline into account when storing relative to the pc
      if (baseRegister == 15) {
        if (positiveOffset)
          memoryAddr = new OPT_IntConstantOperand(pc + 8 + offset);
        else
          memoryAddr = new OPT_IntConstantOperand(pc + 8 - offset);
      }
      else {
        OPT_Operand Rs = arm2ir.getRegister(baseRegister);
        memoryAddr = arm2ir.getTempInt(0);
        arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, (OPT_RegisterOperand)memoryAddr, Rs, new OPT_IntConstantOperand(offset)));
      }
      
      //resolve dest register
      OPT_RegisterOperand Rd = arm2ir.getRegister(destRegister);
      
      if (loadOperation) {
        //load a word
        ps.memory.translateLoad32(memoryAddr, Rd);
      }
      else {
        //store a word
        ps.memory.translateStore32(memoryAddr, Rd);
      }
      
      return pc+4;
    }
  }
  
  abstract static class DataProcessingOperation_Decoder extends BasicDecoder {
    
    protected int Rd;
    protected int Rs;
    protected boolean setConditionCodes;
    
    protected boolean hasImmediate;
    protected int immediate;
    
    @Override
    protected final int translate(ARM2IR arm2ir, ARM_ProcessSpace ps, ARM_Laziness lazy, int pc, int instr) {
      hasImmediate = getBit(instr, 25);
      setConditionCodes = getBit(instr, 20);
      Rd = getBits(instr, 12, 15);
      Rs = getBits(instr, 16, 19);
      
      //we're only handling a subset of the possible instruction options
      if (DBT.VerifyAssertions) DBT._assert(Rd != 15 && Rs != 15 && hasImmediate == true);
      
      if (hasImmediate)
        immediate = getBits(instr, 0, 7) << getBits(instr, 8, 11);
      
      System.out.println("Decoding Data Processing Instruction with: ");
      System.out.println("Rs: " + Rs);
      System.out.println("Rd: " + Rd);
      System.out.println("Op Code: " + getBits(instr, 21, 24));
      System.out.println("Set Condition Codes: " + setConditionCodes);
      
      if (hasImmediate)
        System.out.println("Immediate: " + immediate);
      
      translateActualOpcode(arm2ir, ps, lazy, pc, instr);
      
      if (setConditionCodes && Rd != 15) {
        throw new UnsupportedOperationException("Setting conditions codes is not yet supported");
      }
      
      return pc+4;
    }
    
    protected abstract void translateActualOpcode(ARM2IR arm2ir, ARM_ProcessSpace ps, ARM_Laziness lazy, int pc, int instr);
  }
  
  static class ADD_Decoder extends DataProcessingOperation_Decoder {

    @Override
    protected void translateActualOpcode(ARM2IR arm2ir, ARM_ProcessSpace ps, ARM_Laziness lazy, int pc, int instr) {

      arm2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, arm2ir.getRegister(Rd), arm2ir.getRegister(Rs), new OPT_IntConstantOperand(immediate)));
    }    
  }
  
  static class BranchDecoder extends BasicDecoder {

    @Override
    protected int translate(ARM2IR arm2ir, ARM_ProcessSpace ps, ARM_Laziness lazy, int pc, int instr) {
      boolean branchAndLink = getBit(instr, 24);
      int offset = getBits(instr, 0, 23);
      
      if (DBT.VerifyAssertions) DBT._assert(branchAndLink == false);

      return pc + 8 + offset;
    }
  }

  public static int translateInstruction(ARM2IR arm2ir, ARM_ProcessSpace ps, ARM_Laziness lazy, int pc) {
    int instr = ps.memory.loadInstruction32(pc);
    
    System.out.println("Translating Instruction:" + instr);
    BasicDecoder decoder = null;
    
    if (BasicDecoder.getBits(instr, 26, 27) == 1) {
      decoder = new LDR_STR_Decoder();
    }
    
    return decoder.translate(arm2ir, ps, lazy, pc, instr);
  }
}
