package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;

/**
 * In the ARM decoder model, the decoding and usage (translating/interpreting/disassembling) of instructions
 * is strictly separated. This class provides a namespace for all decoder classes. A decoder works like a
 * mask than can be put upon a binary instruction to look at the different fields within that instruction.
 * 
 * Application might derive from these decoders to implement instruction-specific functionality.
 * Then, implement a version of the generic {@link ARM_InstructionFactory} interface, which returns 
 * your derived classes and pass it to the {@link ARM_InstructionDecoder} to let it create instances of
 * your class from binary instructions. 
 * 
 * @author Michael Baer
 *
 */
public class ARM_Instructions  {

  /**
   * Checks if a bit is set within a word.
   * @param word
   *  The word that is being examined.
   * @param bit
   *  The number of the bit that is to be checked, starting from zero.
   * @return
   *  True, if the given bit is set within the word, false otherwise.
   */
  private static final boolean getBit(int word, int bit) {
    if (DBT.VerifyAssertions)
      DBT._assert(bit >= 0 && bit <= 31);
    return (word & (1 << bit)) != 0;
  }

  /**
   * Extracts a subsequence of bits from a word.
   * A call to <code>getBits(0xFF, 2, 3)</code> would return 0x3.
   * @param word
   *  The word that is to be examined.
   * @param from
   *  The first bit (starting from 0) that is to be extracted.
   * @param to
   *  The last bit (starting from 0) that is to be extracted from the word.
   * @return
   *  A zero-based version of the bit sequence.
   */
  private static final int getBits(int word, int from, int to) {
    if (DBT.VerifyAssertions)
      DBT._assert(from < to && from >= 0 && to <= 31);
    return (word & ((1 << (to + 1)) - 1)) >> from;
  }
  
  /** 
   * Sign extends a given value.
   * @param value
   *  The value to sign extends.
   * @param bitsUsed
   *  The number bits used within this values.
   * @return
   *  A sign extended value.
   */
  public static int signExtend(int value, int bitsUsed) {
    return (value << (32 - bitsUsed)) >> (32 - bitsUsed);
  }

  /** A base class for all (conditional) ARM instructions. */
  public abstract static class Instruction {
    
    public enum Condition {
      EQ, NE, CS, CC, MI, PL, VS, VC, HI, LS, GE, LT, GT, LE, AL, NV
    }
    
    /** @see #getCondition() */
    protected final Condition condition;

    private Instruction(int instr) {
      condition = Condition.values()[(byte) getBits(instr, 28, 31)];
    }

    /** Returns the condition code that specifies, under which circumstances this operation shall be executed. */
    public final Condition getCondition() {
      return condition;
    }
    
    @Override
    public String toString() {
      return ARM_Disassembler.disassemble(this).asString();
    }
    
    /** All instruction classes are meant to implement the visitor pattern. This is the pattern's visit method. */
    public abstract void visit(ARM_InstructionVisitor visitor);
  }

  /** Base class for most instructions that use two registers. */
  private abstract static class TwoRegistersTemplate extends Instruction {

    /** @see #getRn() */
    protected final byte Rn;

    /** @see #getRd() */
    protected final byte Rd;

    public TwoRegistersTemplate(int instr) {
      super(instr);

      Rd = (byte) getBits(instr, 12, 15);
      Rn = (byte) getBits(instr, 16, 19);
    }

    /** Returns the number of the operation's destination register, starting from 0.*/
    public final byte getRd() {
      return Rd;
    }

    /** Returns the number of the operation's first operand register, starting from 0.*/
    public final byte getRn() {
      return Rn;
    }
  }

  /** Base class for most instructions that use three registers. */
  private abstract static class ThreeRegistersTemplate extends
      TwoRegistersTemplate {

    /** @see #getRm() */
    protected final byte Rm;

    public ThreeRegistersTemplate(int instr) {
      super(instr);

      Rm = (byte) getBits(instr, 0, 3);
    }

    /** Returns the number of the second operand register, starting from 0.*/
    public final byte getRm() {
      return Rm;
    }
  }
  
  /** Base class for multiply operations. */
  protected abstract static class MultiplyTemplate 
    extends ThreeRegistersTemplate {

    /** @see #updateConditionCodes() */
    protected final boolean updateConditionCodes;
    
    /** @see #accumulate() */
    protected final boolean accumulate;
    
    /** @see #getRs() */
    protected final byte Rs;

    protected MultiplyTemplate(int instr) {
      super(instr);
      
      updateConditionCodes = getBit(instr, 20);
      accumulate = getBit(instr, 21);
      Rs = (byte) getBits(instr, 8, 11);
    }

    /** Returns true, if the condition codes shall be updated by the result of this operation. */
    public final boolean updateConditionCodes() {
      return updateConditionCodes;
    }

    /** Returns true, if this is the accumulate version of the instruction. */
    public final boolean accumulate() {
      return accumulate;
    }
    
    /** Returns the register number of the Rs operand register. */
    public final byte getRs() {
      return Rs;
    }
  }
  
  /** Base class for coprocessor instructions. */
  protected static abstract class CoprocessorTemplate extends Instruction {

    /** This is a register id, which can either refer to the CPU or the coprocessor, depending on the instruction. */
    protected final byte Rd;
    
    /** This is a register id, which can either refer to the CPU or the coprocessor, depending on the instruction. */
    protected final byte Rn;
    
    /** @see #getCoprocessorNumber() */
    protected final byte cpNum;

    public CoprocessorTemplate(int instr) {
      super(instr);
      
      cpNum = (byte) getBits(instr, 8, 11);
      Rd = (byte) getBits(instr, 12, 15);
      Rn = (byte) getBits(instr, 16, 19);
    }
    
    /** Returns the coprocessor that shall process this instruction */
    public final byte getCoprocessorNumber() {
      return cpNum;
    }
  }
  
  /** Represents an operand, which might either be an immediate value, a register (shifted by an immediate) or a  register shifted by a register. */
  public abstract static class OperandWrapper {
    
    /** Describes the type of the operand. */
    public enum Type {
      Immediate,
      PcRelative,
      Register,
      ImmediateShiftedRegister,
      RegisterShiftedRegister,
    }
    
    /** Describes a type of shift, in case the operand is supposed to be shifted. */
    public enum ShiftType {
      LSL,
      LSR,
      ASR,
      ROR,
      RRE
    }
    
    /** Creates an operand wrapper around a 12 bit immediate value. */
    public static OperandWrapper createImmediate(int immediate) {
      return new ImmediateOperand(immediate);
    }

    /** Creates an operand wrapper that is a normal register value. */
    public static OperandWrapper createRegister(byte register) {
      return new RegisterOperand(register);
    }
    
    /** Creates an operand wrapper representing an offset to the pc.*/
    public static OperandWrapper createPcRelative(int offset) {
      return new PcRelativeOperand(offset);
    }
    
    /** Creates an operand wrapper, that represents a register shifted by an immediate or a register, depending on the instruction. */
    public static OperandWrapper decodeShiftedRegister(int instr) {
      ShiftType shift = ShiftType.values()[getBits(instr, 5, 6)];
      byte shiftedRegister = (byte) (instr & 0xF);
      
      if (getBit(instr, 4)) {
        //shift by a register
        byte shiftingRegister = (byte)getBits(instr, 8, 11);
        return new RegisterShiftRegisterOperand(shiftedRegister, shift, shiftingRegister);
      }
      else {
        //shift by an immediate
        byte immediate = (byte)getBits(instr, 7, 11);
        
        if (immediate == 0) {
          //if we are shifting by zero, we might forget about the shift
          if (shift == ShiftType.ROR) {
            //However, if the shift type was RotateRight, then ARM meant do a RotateRightExtend by 1
            return new RegisterShiftImmediateOperand(shiftedRegister, shift, (byte)1);
          }
          else {
            //Otherwise, really forget about the shifting
            return new RegisterOperand(shiftedRegister);
          }
        }
        else {
          return new RegisterShiftImmediateOperand(shiftedRegister, shift, immediate);
        }
      }
    }
    
    /** Returns the type of the operand that this class is actually representing. */
    public abstract Type getType();
    
    /** Returns the immediate, which is the 2nd operand of this instruction. Make sure that hasImmediate is true before calling this. */
    public int getImmediate() {
      throw new RuntimeException("Invalid call on an operand wrapper.");
    }
    
    /** Returns an offset that is to be applied to a register. */
    public int getOffset() {
      throw new RuntimeException("Invalid call on an operand wrapper.");
    }
    
    /** Returns the number of the register, which forms the 2nd operand. Only applicable if {@link #hasImmediate()} is false.*/
    public byte getRegister() {
      throw new RuntimeException("Invalid call on an operand wrapper.");
    }
    
    /** Returns the number of the register which is performing the shift. */
    public byte getShiftingRegister() {
      throw new RuntimeException("Invalid call on an operand wrapper.");
    }
    
    /** Returns the amount by which a value is supposed to be shifted*/
    public byte getShiftAmount() {
      throw new RuntimeException("Invalid call on an operand wrapper.");
    }

    /** Returns the shift type, in case this Operand includes shifting. */
    public ShiftType getShiftType() {
      throw new RuntimeException("Invalid call on an operand wrapper.");
    }
    
    /** Represents an immediate value operand. */
    protected static class ImmediateOperand extends OperandWrapper {
      
      /** @see #getImmediate() */
      protected final int immediate;
      
      protected ImmediateOperand(int immediate) {
        this.immediate = immediate;
      }
      
      @Override
      public int getImmediate() {
        return immediate;
      }

      @Override
      public Type getType() {
        return Type.Immediate;
      }
    }
    
    protected static class PcRelativeOperand extends OperandWrapper {
      
      protected final int offset;
      
      protected PcRelativeOperand(int offset) {
        this.offset = offset;
      }
      
      @Override
      public byte getRegister() {
        return 15;
      }
      
      @Override
      public int getOffset() {
        return offset;
      }

      @Override
      public Type getType() {
        return Type.PcRelative;
      }
    }
    
    /** Represents a register operand. */
    protected static class RegisterOperand extends OperandWrapper {
      
      /** @see #getRegister() */
      protected final byte register;
      
      protected RegisterOperand(byte register) {
        this.register = register;
      }
      
      @Override
      public byte getRegister() {
        return register;
      }

      @Override
      public Type getType() {
        return Type.Register;
      }
    }
    
    /** Represents an operand, which is a register shifted by an immediate value. */
    protected static class RegisterShiftImmediateOperand extends RegisterOperand {
      
      /** @see #getShiftType() */
      protected final ShiftType shiftType;
      
      /** @see #getShiftAmount() */
      protected final byte shiftAmount;
      
      protected RegisterShiftImmediateOperand(byte register, ShiftType shift, byte shiftAmount) {
        super(register);
        this.shiftAmount = shiftAmount;
        this.shiftType = shift;
      }

      @Override
      public Type getType() {
        return Type.ImmediateShiftedRegister;
      }
      
      @Override
      public byte getShiftAmount() {
        return shiftAmount;
      }
      
      @Override
      public ShiftType getShiftType() {
        return shiftType;
      }
    }
    
    /** Returns an operand, which is a register shifted by a register. */
    protected static class RegisterShiftRegisterOperand extends RegisterOperand {
      
      /** @see #getShiftType() */
      protected final ShiftType shiftType;
      
      /** @see #getShiftingRegister() */
      protected final byte shiftingRegister;
      
      protected RegisterShiftRegisterOperand(byte shiftedRegister, ShiftType shift, byte shiftingRegister) {
        super(shiftedRegister);
        this.shiftType = shift;
        this.shiftingRegister = shiftingRegister;
      }
      
      @Override
      public Type getType() {
        return Type.RegisterShiftedRegister;
      }
      
      @Override
      public byte getShiftingRegister() {
        return shiftingRegister;
      }
      
      @Override
      public ShiftType getShiftType() {
        return shiftType;
      }
    }
  }

  /** Represents a Data Processing instruction. */
  public static class DataProcessing extends
      TwoRegistersTemplate {
    
    /** A list of possible DataProcessing operations. The list is orded in ascendingly, with the
     * first opcode corresponding to opcode 0 (zero) in the opcode field of an ARM data processing
     * instruction. */
    public enum Opcode {
      AND, EOR, SUB, RSB, ADD, ADC, SBC, RSC, TST, TEQ, CMP, CMN, ORR, MOV, BIC, MVN
    }

    /** @see #hasSetConditionCodes() */
    protected final boolean updateConditionCodes;

    /** @see #getOpcode() */
    protected final Opcode opcode;
    
    /** @see #getOperand2() */
    protected final OperandWrapper operand2;
    
    public DataProcessing(int instr) {
      super(instr);

      updateConditionCodes = getBit(instr, 20);
      opcode = Opcode.values()[(byte) getBits(instr, 21, 24)];
          
      if (getBit(instr, 25))
        operand2 = OperandWrapper.createImmediate((instr & 0xFF) << getBits(instr, 8, 11));
      else
        operand2 = OperandWrapper.decodeShiftedRegister(instr); 
    }

    /** Returns the opcode, that specifies the data processing operation, which is to be performed. */
    public final Opcode getOpcode() {
      return opcode;
    }
    
    /** Returns true if the condition codes shall be set by this operation, false otherwise. */
    public final boolean updateConditionCodes() {
      return updateConditionCodes;
    }
    
    /** Returns the 2nd operand of this data processing instruction. */
    public final OperandWrapper getOperand2() {
      return operand2;
    }
    
    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** Represents a LDR/SDR instruction. */
  public static class SingleDataTransfer extends
      TwoRegistersTemplate {
    
    public enum TransferSize {
      Byte,
      HalfWord,
      Word
    }

    /** @see #preIndexing() */
    protected final boolean preIndexing;

    /** @see #positiveOffset() */
    protected final boolean positiveOffset;
    
    /** @see #signExtend() */
    protected final boolean signExtend;
    
    /** @see #forceUserMode() */
    protected final boolean forceUserMode;

    /** @see #getSize() */
    protected final TransferSize size;

    /** @see #writeBack() */
    protected final boolean writeBack;

    /** @see #isLoad() */
    protected final boolean isLoad;
    
    /** @see #getOffset() */
    protected final OperandWrapper offset;
    
    public SingleDataTransfer(int instr) {
      super(instr);

      preIndexing = getBit(instr, 24);
      positiveOffset = getBit(instr, 23);
      writeBack = getBit(instr, 21);
      isLoad = getBit(instr, 20);
      
      if (getBit(instr, 26)) {
        //this is an unsigned byte or word transfer
        signExtend = false;
        
        forceUserMode = !preIndexing && writeBack;
        
        if (getBit(instr, 22))
          size = TransferSize.Byte;
        else
          size = TransferSize.Word;
        
        if (getBit(instr, 25))
          offset = OperandWrapper.createImmediate(instr & 0xFF);
        else
          offset = OperandWrapper.decodeShiftedRegister(instr);
      }
      else {
        //this is a byte or half-word transfer
        if (getBit(instr, 5))
          size = TransferSize.HalfWord;
        else
          size = TransferSize.Byte;
        
        signExtend = getBit(instr, 6);
        forceUserMode = false;
        
        if (getBit(instr, 22)) {
          //immediate offset
          offset = OperandWrapper.createImmediate((getBits(instr, 8, 11) << 4) | (instr & 0xF));
        }
        else {
          //register offset
          offset = OperandWrapper.createRegister((byte)(instr & 0xF));
        }
        
        //The decoder should make sure that we're never being called with this combination
        if (DBT.VerifyAssertions) DBT._assert(!signExtend || isLoad);
      }
    }
    
    /** Returns true, if this memory access shall be treated as if it had been done in user mode. */
    public final boolean forceUserMode() {
      return forceUserMode;
    }
    
    /** Returns true, if the loaded/stored value shall be signed-extended.*/
    public final boolean signExtend() {
      return signExtend;
    }

    /** Returns the number of bytes that have to be transferred. */
    public final TransferSize getSize() {
      return size;
    }

    /** True if this is a LDM instruction, false if it is a STM instruction. */
    public final boolean isLoad() {
      return isLoad;
    }

    /** Returns true, if the offset from the base register (see {@link #getRn()} is positive, false if it is negative. */
    public final boolean positiveOffset() {
      return positiveOffset;
    }

    /** True if the base register shall be changed before the transfer, otherwise changed it after the transfer. */
    public final boolean preIndexing() {
      return preIndexing;
    }

    /** True if the incremented base register shall be persisted after this instruction. */
    public final boolean writeBack() {
      return writeBack;
    }
    
    /** Returns the offset operand for this data processing instruction. */
    public final OperandWrapper getOffset() {
      return offset;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
  
  /** Represents a normal (not long) multiply instruction. */
  public static class IntMultiply extends MultiplyTemplate {
   
    protected IntMultiply(int instr) {
      super(instr);
      
      if (DBT.VerifyAssertions) DBT._assert(accumulate || Rn == 0);
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
  
  /** Represents a long multiply instruction. */
  public static class LongMultiply extends MultiplyTemplate {
    
    /** @see #isUnsigned() */
    protected final boolean unsigned;
    
    public LongMultiply(int instr) {
      super(instr);
      
      unsigned = getBit(instr, 22);
    }
    
    /** Long multiplication stores its result in two registers. This function gets the register which receives the high int. */
    public final byte getRdHigh() {
      return Rd;
    }

    /** Long multiplication stores its result in two registers. This function gets the register which receives the low int. */
    public final byte getRdLow() {
      return Rn;
    }
    
    /** Returns true, if this is an unsigned multiplication or false if it is a signed multiplication. */
    public final boolean isUnsigned() {
      return unsigned;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** Represents a SWP/SWPB instruction. */
  public static class Swap extends ThreeRegistersTemplate {

    /** @see #swapByte() */
    protected final boolean swapByte;

    public Swap(int instr) {
      super(instr);
      swapByte = getBit(instr, 22);
    }

    /** Returns true, if a byte shall be swapped or false, if an int (32 bit) shall be swapped. */
    public final boolean swapByte() {
      return swapByte;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** Represents a LDM/STM instruction. */
  public static class BlockDataTransfer extends Instruction {

    /** @see #postIndexing() */
    protected final boolean postIndexing;

    /** @see #incrementBase() */
    protected final boolean incrementBase;

    /** @see #forceUser() */
    protected final boolean forceUser;

    /** @see #writeBack() */
    protected final boolean writeBack;

    /** @see #isLoad() */
    protected final boolean isLoad;

    /** @see #getBaseRegister() */
    protected final byte baseRegister;

    /** Contains a set bit at position N if rN should be transferred using this instruction.*/
    protected final int registerList;

    public BlockDataTransfer(int instr) {
      super(instr);

      postIndexing = getBit(instr, 24);
      incrementBase = getBit(instr, 23);
      forceUser = getBit(instr, 22);
      writeBack = getBit(instr, 21);
      isLoad = getBit(instr, 20);
      baseRegister = (byte) getBits(instr, 16, 19);
      registerList = instr;
    }

    /** @return True if register r should be transferred using this instruction. */
    public final boolean transferRegister(int r) {
      if (DBT.VerifyAssertions)
        DBT._assert(r >= 0 && r < 16);

      return getBit(registerList, r);
    }
    
    /** True if the base register shall be changed after each single transfer, otherwise changed it before each transfer. */
    public final boolean postIndexing() {
      return postIndexing;
    }

    /** True if the base register shall be incremented, false if it should be decremented. */
    public final boolean incrementBase() {
      return incrementBase;
    }

    /** Force user mode during this instruction? */
    public final boolean forceUser() {
      return forceUser;
    }

    /** True if the incremented base register shall be persisted after this instruction. */
    public final boolean writeBack() {
      return writeBack;
    }

    /** True if this is a LDM instruction, false if it is a STM instruction. */
    public final boolean isLoad() {
      return isLoad;
    }

    /** The number of the register which will provides the base address for this LDM/STM instruction. */
    public final byte getBaseRegister() {
      return baseRegister;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** Represents a SWI instruction*/
  public static class SoftwareInterrupt extends Instruction {

    /** @see #getInterruptNumber() */
    protected final int interruptNumber;

    public SoftwareInterrupt(int instr) {
      super(instr);
      interruptNumber = instr & 0xFFFFFF;
    }
    
    /** Returns the interrupt that is being called. The value is taken from the instruction's comment field. */
    public final int getInterruptNumber() {
      return interruptNumber;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }

  /** Represents a branch instruction. */
  public static class Branch extends Instruction {

    /** @see #link() */
    protected final boolean link;

    /** @see #getOffset() */
    protected final int offset;

    public Branch(int instr) {
      super(instr);
      link = getBit(instr, 24);
      offset = instr & 0xFFF;
    }

    /** Should the current PC be put into the lr? */
    public final boolean link() {
      return link;
    }

    /** The offset of the target address to the PC */
    public final int getOffset() {
      return offset;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
  
  /** Represents a BX instruction set */
  public static class BranchExchange extends Instruction {

    /** @see #target() */
    protected final OperandWrapper target;
    
    /** @see #link() */
    protected final boolean link;

    public BranchExchange(int instr) {
      super(getBit(instr, 27) ? 0xE0000000 : instr);
      
      if (getBit(instr, 27)) {
        //this is the immediate version of a BLX
        link = true;
        
        //sign extend jump target
        int jumpTarget = signExtend(instr & 0xFFF, 24) << 2;
        
        //are we addressing a half-byte?
        if (getBit(instr, 24))
          jumpTarget += 2;
        
        target = OperandWrapper.createPcRelative(jumpTarget);
      }
      else {
        link = getBit(instr, 5);
        target = OperandWrapper.createRegister((byte) (instr & 0xF));
      }
    }
    
    /** Returns, whether the return address for this jump shall be put into the lr. */
    public final boolean link() {
      return link;
    }
    
    /** Returns the address to which this instruction will branch. */
    public final OperandWrapper target() {
      return target;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
  
  /** Represents a LDC/STC instruction. */
  public static class CoprocessorDataTransfer extends CoprocessorTemplate {
    
    /** @see #getOffset() */
    protected final int offset;
    
    /** @see #preIndexing() */
    protected final boolean preIndexing;
    
    /** @see #largeTransfer() */
    protected final boolean largeTransfer;
    
    /** @see #writeBack() */
    protected final boolean writeBack;
    
    /** @see #isLoad() */
    protected final boolean isLoad;

    public CoprocessorDataTransfer(int instr) {
      super(instr);
      
      preIndexing = getBit(instr, 24);
      largeTransfer = getBit(instr, 22);
      writeBack = getBit(instr, 21);
      isLoad = getBit(instr, 20);
      
      if (!writeBack && !preIndexing) {
        offset = instr & 0xFF;
      }
      else {
        if (getBit(instr, 23))
          offset = (instr & 0xFF) << 2;
        else
          offset = - ((instr & 0xFF) << 2);
      }
    }
    
    /** Returns the number of the register, which contains the base address for this data transfer.*/
    public final byte getBaseRegister() {
      return Rn;
    }
    
    /** Returns the transfer register on the coprocessor. */
    public final byte getCoprocessorRd() {
      return Rd;
    }
    
    /** True if this is a LDC instruction, false if it is a STC instruction. */
    public final boolean isLoad() {
      return isLoad;
    }
    
    /** Returns the offset that should be added to the base register. Note that the offset may be negative. */
    public final int getOffset() {
      return offset;
    }
    
    /** In certain circumstances, the instruction might include an option to the coprocessor that is stored instead of the offset. */
    public final int getOption() {
      if (DBT.VerifyAssertions) DBT._assert(!writeBack && !preIndexing);
      
      return offset;
    }
    
    /** True if the changed base register shall be persisted after this instruction. */
    public final boolean writeBack() {
      return writeBack;
    }

    /** True if the base register shall be changed before the transfer, otherwise changed it after the transfer. */
    public final boolean preIndexing() {
      return preIndexing;
    }
    
    /** Returns true, if the flag which indicates a large transfer is set. The meaning of "large transfer" is dependend on the coprocessor.*/
    public final boolean largeTransfer() {
      return largeTransfer;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
  
  /** Represents a CDP instruction. */
  public static class CoprocessorDataProcessing extends CoprocessorTemplate {
    
    /** @see #getOpcode() */
    protected final byte opcode;
    
    /** @see #getCoprocessorRm() */
    protected final byte cpRm;
    
    /** @see #getCoprocessorInfo() */
    protected final byte cpInfo;

    public CoprocessorDataProcessing(int instr) {
      super(instr);
      
      opcode = (byte) getBits(instr, 20, 23);
      cpInfo = (byte) getBits(instr, 5, 7);
      cpRm = (byte) (instr & 0xF);
    }
    
    /** Returns the destination register of this operation. This register is a coprocessor register. */
    public final byte getCoprocessorRd() {
      return Rd; 
    }
    
    /** Returns the first operand register of this operation. This register is a coprocessor register. */
    public final byte getCoprocessorRn() {
      return Rn; 
    }
    
    /** Returns the second operand register of this operation. This register is a coprocessor register. */
    public final byte getCoprocessorRm() {
      return cpRm; 
    }
    
    /** The instruction contains three bits that may be used to control the details of the operation. This info is only of significance to the coprocessor. */
    public final byte getCoprocessorInfo() {
      return cpInfo;
    }
    
    /** Returns the opcode, that identifies the operation that shall be performed on the coprocessor. This vlaue is only of significance to the coprocessor. */
    public final byte getOpcode() {
      return opcode;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
  
  /** Represents a MRC/MCR instruction. */
  public static class CoprocessorRegisterTransfer extends CoprocessorTemplate {
    
    /** @see #getOpcode() */
    protected final byte opcode;
    
    /** @see #getCoprocessorRm() */
    protected final byte cpRm;
    
    /** @see #getCoprocessorInfo() */
    protected final byte cpInfo;
    
    /** @see #isLoadFromCP() */
    protected final boolean isLoad;

    public CoprocessorRegisterTransfer(int instr) {
      super(instr);
      
      opcode = (byte) getBits(instr, 21, 23);
      cpInfo = (byte) getBits(instr, 5, 7);
      cpRm = (byte) (instr & 0xF);
      isLoad = getBit(instr, 20);
    }
    
    /** Returns true if this operation is a load from a coprocessor or false if it is a store to coprocessor. */
    public final boolean isLoadFromCP() {
      return isLoad;
    }
    
    /** Returns the destination register of this operation.*/
    public final byte getRd() {
      return Rd; 
    }
    
    /** Returns the first operand register of this operation. This register is a coprocessor register. */
    public final byte getCoprocessorRn() {
      return Rn; 
    }
    
    /** Returns the second operand register of this operation. This register is a coprocessor register. */
    public final byte getCoprocessorRm() {
      return cpRm; 
    }
    
    /** The instruction contains three bits that may be used to control the details of the operation. This info is only of significance to the coprocessor. */
    public final byte getCoprocessorInfo() {
      return cpInfo;
    }
    
    /** Returns the opcode, that identifies the operation that shall be performed on the coprocessor. This vlaue is only of significance to the coprocessor. */
    public final byte getOpcode() {
      return opcode;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
  
  /** Represents a MRS instruction. */
  public static class MoveFromStatusRegister extends Instruction {
    
    /** @see #getRd() */
    protected final byte Rd;
    
    /** @see #transferSavedPSR() */
    protected final boolean transferSavedPSR;

    public MoveFromStatusRegister(int instr) {
      super(instr);
      
      Rd = (byte) getBits(instr, 12, 15);
      transferSavedPSR = getBit(instr, 22);
    }
    
    /** Returns the number of the destination register. */
    public final byte getRd() {
      return Rd;
    }
    
    /** Identifies the PSR that is to be transferred: true for the SPSR, false for the CPSR. */
    public final boolean transferSavedPSR() {
      return transferSavedPSR;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
  
  /** Represents a MSR instruction. */
  public static class MoveToStatusRegister extends Instruction {
    
    /** @see #transferControlField() */
    protected final boolean transferControl;
    
    /** @see #transferExtensionField() */
    protected final boolean transferExtension;
    
    /** @see #transferStatusField() */
    protected final boolean transferStatus;
    
    /** @see #transferFlagField() */
    protected final boolean transferFlags;
    
    /** @see #transferSavedPSR() */
    protected final boolean transferSavedPSR;
    
    /** @see #getSource() */
    protected final OperandWrapper sourceOperand;

    public MoveToStatusRegister(int instr) {
      super(instr);
      
      transferControl = getBit(instr, 16);
      transferExtension = getBit(instr, 17);
      transferStatus = getBit(instr, 18);
      transferFlags = getBit(instr, 19);
      
      transferSavedPSR = getBit(instr, 22);
      
      if (getBit(instr, 25))
        sourceOperand = OperandWrapper.createImmediate((instr & 0xFF) << getBits(instr, 8, 11));
      else
        sourceOperand = OperandWrapper.decodeShiftedRegister(instr);
    }
    
    /** Identifies the PSR that is to be transferred: true for the SPSR, false for the CPSR. */
    public final boolean transferSavedPSR() {
      return transferSavedPSR;
    }
    
    /** Returns true if the control field of the PSR shall be overwritten. */
    public final boolean transferControlField() {
      return transferControl;
    }
    
    /** Returns true if the extension field of the PSR shall be overwritten. */
    public final boolean transferExtensionField() {
      return transferExtension;
    }
    
    /** Returns true if the status field of the PSR shall be overwritten. */
    public final boolean transferStatusField() {
      return transferStatus;
    }
    
    /** Returns true if the flag field of the PSR shall be overwritten. */
    public final boolean transferFlagField() {
      return transferFlags;
    }
    
    /** Returns the operand, which is to be transfered into the status register. */
    public final OperandWrapper getSource() {
      return sourceOperand;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
  
  /** Represents a CLZ instruction. */
  public static class CountLeadingZeros extends Instruction {
    
    /** @see #getRm() */
    protected final byte Rm;
    
    /** @see #getRd() */
    protected final byte Rd;

    public CountLeadingZeros(int instr) {
      super(instr);
      
      Rm = (byte) (instr & 0xF);
      Rd = (byte) getBits(instr, 12, 15);
    }
    
    /** Returns the source register for this operation. */
    public final byte getRm() {
      return Rm;
    }
    
    /** Returns the destination register for this operation. */
    public final byte getRd() {
      return Rd;
    }

    @Override
    public void visit(ARM_InstructionVisitor visitor) {
      visitor.visit(this);
    }
  }
}
