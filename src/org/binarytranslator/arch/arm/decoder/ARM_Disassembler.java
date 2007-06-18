package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.arch.arm.decoder.ARM_Instructions.Instruction;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.BlockDataTransfer;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.Branch;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.BranchExchange;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.CoprocessorDataProcessing;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.CoprocessorDataTransfer;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.CoprocessorRegisterTransfer;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.DataProcessing;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.IntMultiply;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.LongMultiply;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.MoveFromStatusRegister;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.MoveToStatusRegister;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.OperandWrapper;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.SingleDataTransfer;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.SoftwareInterrupt;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.Swap;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.Instruction.Condition;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.generic.decoder.DisassembledInstruction;

/**
 * This class transfers an ARM instruction into a human-readable assembly
 * format.
 * 
 * @author Michael Baer
 * 
 */
public final class ARM_Disassembler {

  /**
   * Disassembles an ARM instruction by reading it from a ProcessSpace.
   * 
   * @param pc
   *          The address of the ARM instruction within the process space's
   *          memory.
   * @param ps
   *          The process space from which the instruction is read.
   * @return A disassembled ARM instruction.
   */
  public final static DisassembledInstruction disassemble(int address,
      ARM_ProcessSpace ps) {

    Instruction decodedInstruction;
    
    if ((address & 0x1) == 1) {
      short binaryInstruction = (short)ps.memory.loadInstruction16(address & 0xFFFFFFFE);
      decodedInstruction = ARM_InstructionDecoder.Thumb.decode(binaryInstruction);
    }
    else {
      int binaryInstruction = ps.memory.loadInstruction32(address);
      decodedInstruction = ARM_InstructionDecoder.ARM32.decode(binaryInstruction);
    }
    
    DisassemblingVisitor disassembler = new DisassemblingVisitor();
    decodedInstruction.visit(disassembler);

    return disassembler.result;
  }

  /**
   * Directly disassembles an ARM instruction. This method only has package
   * visibility, because it is only used to provide the toString() functionality
   * for the ARM instruction decoder.
   * 
   * @param instruction
   *          The instruction that is to be decoded.
   * @return A human-readable version of the given instruction.
   */
  final static DisassembledInstruction disassemble(
      ARM_Instructions.Instruction instruction) {

    DisassemblingVisitor disassembler = new DisassemblingVisitor();
    instruction.visit(disassembler);

    return disassembler.result;
  }

  /** Represents a disassembled ARM instruction. */
  private final static class ARM_DisassembledInstruction implements
      DisassembledInstruction {

    /** A readable version of the diassembled instruction. */
    private final String instruction;

    public ARM_DisassembledInstruction(String instruction) {
      this.instruction = instruction;
    }

    /** @see DisassembledInstruction#asString() */
    public String asString() {
      return instruction;
    }
    
    @Override
    public String toString() {
      return asString();
    }

    /** @see DisassembledInstruction#getSuccessor(int) */
    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }

  /**
   * As this class has only static methods, there is no need to ever instantiate
   * it.
   */
  private ARM_Disassembler() {
  }

  /**
   * This class decodes an ARM instruction into an ARM_DisassembledInstruction.
   * It used a visitor pattern to decode the proper instructions and should only
   * be used by calling {@link Instruction#visit(ARM_InstructionVisitor)} method. The
   * disassembled instruction is saved within the {@link #result} member.
   * 
   * As this class is private (i.e. can only ever be instantiated by its
   * superclass), it should only be accessed using the methods provided by the
   * superclass.
   * 
   * @author Michael Baer
   */
  private static final class DisassemblingVisitor implements
      ARM_InstructionVisitor {

    /** This field receives the disassembled instruction. */
    private ARM_DisassembledInstruction result;

    private DisassemblingVisitor() {
    }

    /** Wraps a decoded assembly statement within an {@link ARM_DisassembledInstruction} object and
     * stores it as the result of this operation. */
    private void setResult(String assembly) {
      result = new ARM_DisassembledInstruction(assembly);
    }

    /**
     * Extracts the condition field from an instruction and provides a
     * human-readable assembly form of the condition.
     * 
     * @param instr
     *          The instruction, whose condition field shall be read.
     * @return A human readable form of this instructions condition.
     */
    private String cond(Instruction instr) {
      if (instr.getCondition() == Condition.AL)
        return "";
      else
        return instr.getCondition().name();
    }

    /**
     * Decodes an operand stored within a {@link OperandWrapper} object and
     * provides an human-readable, ARM assembly version of it.
     * 
     * @param op
     *          The operand that is to be decoded.
     * @return A readable version of the operand.
     */
    private final String operand(OperandWrapper op) {
      switch (op.getType()) {
      case Immediate:
        return String.format("#%d", op.getImmediate());

      case ImmediateShiftedRegister:
        return String.format("r%d %s #%d", op.getRegister(), op.getShiftType(),
            op.getShiftAmount());

      case RegisterOffset:
          return String.format("#<r%d + %d>", op.getRegister(), op.getOffset());

      case Register:
        return "r" + op.getRegister();

      case RegisterShiftedRegister:
        return String.format("r%d %s r%s", op.getRegister(), op.getShiftType(),
            op.getShiftingRegister());

      default:
        throw new RuntimeException("Unexpected operand wrapper type: "
            + op.getType());
      }
    }

    public void visit(DataProcessing instr) {
      
      DataProcessing.Opcode opcode = instr.getOpcode();
      String mnemonic = opcode.name();
      mnemonic += cond(instr);

      String parameters;

      if (opcode == DataProcessing.Opcode.CMN ||
          opcode == DataProcessing.Opcode.CMP ||
          opcode == DataProcessing.Opcode.TST ||
          opcode == DataProcessing.Opcode.TEQ) {
        //these functions don't use the destination register and always set the condition codes
        setResult(String.format("%s r%s, %s", mnemonic, instr.getRn(), operand(instr.getOperand2())));
        return;
      }
      
      if (instr.updateConditionCodes())
        mnemonic += 'S';

      // Filter instructions that only take one parameter
      if (instr.getOpcode() == DataProcessing.Opcode.MOV
          || instr.getOpcode() == DataProcessing.Opcode.MVN
          || instr.getOpcode() == DataProcessing.Opcode.CLZ) {

        parameters = String.format("%s", operand(instr.getOperand2()));
      } 
      else {
        parameters = String.format("r%d, %s", instr.getRn(), operand(instr.getOperand2()));
      }

      setResult(String.format("%s r%d, %s", mnemonic, instr.getRd(), parameters));
    }

    public void visit(SingleDataTransfer instr) {
      String mnemonic = instr.isLoad() ? "LDR" : "STR";
      String address = "[r" + instr.getRn();

      if (instr.preIndexing()) {
        
        OperandWrapper offset = instr.getOffset(); 
        if (offset.getType() != OperandWrapper.Type.Immediate || offset.getImmediate() != 0) {
          
          address += ", ";
          
          if (!instr.positiveOffset())
            address += '-';
          
          address += operand(instr.getOffset());
        }

        address += ']';
        
        if (instr.writeBack())
          address += '!';
      } else {
        address += "], ";
        
        if (!instr.positiveOffset())
          address += '-';
        
        address += operand(instr.getOffset());
      }
      mnemonic += cond(instr);

      switch (instr.getSize()) {
      case Byte:
        mnemonic += 'B';
        break;

      case HalfWord:
        mnemonic += 'H';
        break;

      case Word:
        break;

      default:
        throw new RuntimeException(
            "Unexpected transfer size for single data transfer: "
                + instr.getSize());
      }

      if (instr.forceUserMode())
        mnemonic += 'T';

      setResult(String.format("%s r%d, %s", mnemonic, instr.getRd(), address));
    }

    public void visit(IntMultiply instr) {

      String mnemonic;
      String accumParam;

      if (instr.accumulate) {
        mnemonic = "MLA";
        accumParam = ", r" + instr.getRn();
      } else {
        mnemonic = "MUL";
        accumParam = "";
      }

      mnemonic += cond(instr);

      if (instr.updateConditionCodes())
        mnemonic += "S";

      setResult(String.format("%s r%d, r%d, r%d%s", mnemonic, instr.getRd(),
          instr.getRm(), instr.getRs(), accumParam));
    }

    public void visit(LongMultiply instr) {
      String mnemonic = instr.isUnsigned() ? "U" : "S";

      mnemonic += instr.accumulate() ? "MLAL" : "MULL";
      mnemonic += cond(instr);

      if (instr.updateConditionCodes())
        mnemonic += "S";

      setResult(String.format("%s %s, %s, %s, %s", mnemonic, instr.getRdLow(),
          instr.getRdHigh(), instr.getRm(), instr.getRs()));
    }

    public void visit(Swap instr) {

      String mnemonic = instr.swapByte() ? "B" : "";
      setResult(String.format("SWP%s%s r%d, r%d, r%d", mnemonic, cond(instr),
          instr.getRd(), instr.getRm(), instr.getRn()));
    }

    public void visit(BlockDataTransfer instr) {
      String mnemonic = instr.isLoad() ? "LDM" : "STM";
      String baseRegister = "r" + instr.getBaseRegister();

      mnemonic += cond(instr);
      mnemonic += instr.incrementBase() ? "I" : "D";
      mnemonic += instr.postIndexing() ? "A" : "B";

      if (instr.writeBack())
        baseRegister += "!";

      String registers = "";

      for (int i = 0; i <= 15; i++)
        if (instr.transferRegister(i))
          registers += ", r" + i;

      registers = registers.substring(2);

      setResult(String.format("%s %s, {%s}%s", mnemonic, baseRegister,
          registers, instr.forceUser() ? "^" : ""));
    }

    public void visit(SoftwareInterrupt instr) {

      setResult(String.format("SWI%s #0x%x", cond(instr), instr
          .getInterruptNumber()));
    }

    public void visit(Branch instr) {

      String mnemonic = instr.link() ? "BL" : "B";
      setResult(String.format("%s%s [PC + %s]", mnemonic, cond(instr), operand(instr.offset)));
    }

    public void visit(BranchExchange instr) {

      String mnemonic = instr.link ? "BLX" : "BX";
      setResult(String.format("%s%s #%s", mnemonic, cond(instr), operand(instr
          .target())));
    }

    public void visit(CoprocessorDataTransfer instr) {

      String mnemonic = instr.isLoad() ? "LDC" : "STC";
      String cond = instr.getCondition() == Condition.NV ? "2" : cond(instr);
      String largeTransfer = instr.largeTransfer() ? "L" : "";
      String address = "[r" + instr.getBaseRegister();

      if (!instr.preIndexing() && !instr.writeBack()) {
        // this is a special instruction form, where the offset is actually a
        // coprocessor option
        address += "], " + instr.getOption();
      } else {

        String offset = "#" + instr.getOffset();

        if (!instr.preIndexing()) {
          address += "], " + offset;
        } else {
          address += ", " + offset + "]";

          if (instr.writeBack())
            address += "!";
        }
      }

      setResult(String.format("%s%s%s p%d, cp_r%d, %s", mnemonic, cond,
          largeTransfer, instr.getCoprocessorNumber(),
          instr.getCoprocessorRd(), address));
    }

    public void visit(CoprocessorDataProcessing instr) {

      String mnemonic = instr.getCondition() == Condition.NV ? "CDP2" : "CDP"
          + cond(instr);
      setResult(String.format("%s p%d, %d, cp_r%d, cp_r%d, cp_r%d, %d",
          mnemonic, instr.getCoprocessorNumber(), instr.getOpcode(), instr
              .getCoprocessorRd(), instr.getCoprocessorRn(), instr
              .getCoprocessorRm(), instr.getCoprocessorInfo()));
    }

    public void visit(CoprocessorRegisterTransfer instr) {
      String mnemonic = instr.isLoadFromCP() ? "MRC" : "MCR";
      String condition = instr.getCondition() == Condition.NV ? "2" : cond(instr);
      String opcode2 = instr.getCoprocessorInfo() != 0 ? ", "
          + instr.getCoprocessorInfo() : "";

      setResult(String.format("%s%s p%d, %d, r%d, cp_r%d, cp_r%d%s", mnemonic,
          condition, instr.getCoprocessorNumber(), instr.getOpcode(), instr
              .getRd(), instr.getCoprocessorRn(), instr.getCoprocessorRm(),
          opcode2));
    }

    public void visit(MoveFromStatusRegister instr) {

      String field = instr.transferSavedPSR() ? "SPSR" : "CPSR";
      setResult(String.format("MRS%s r%d, %s", cond(instr), instr.getRd(),
          field));
    }

    public void visit(MoveToStatusRegister instr) {

      String fields = instr.transferSavedPSR() ? "SPSR_" : "CPSR_";

      if (instr.transferControlField())
        fields += 'c';

      if (instr.transferExtensionField())
        fields += 'x';

      if (instr.transferStatusField())
        fields += 's';

      if (instr.transferFlagField())
        fields += 'f';

      setResult(String.format("MSR%s %s, %s", cond(instr), fields,
          operand(instr.getSource())));
    }
  }
}
