package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.*;

/**
 * This class decodes an ARM instruction and uses a user-supplied ARM_InstructionFactory to create a class
 * that represents the given instruction.
 * 
 * I'm not happy with the structure of this module, but looking at the opcode map of the ARM, it's hard to
 * provide a structured way of decoding primary and secondary opcodes efficiently. This class first looks at
 * bits 25-27 and tries to decode as much from these as possible, using the decode_xxx functions. However,
 * sometimes bits have to be checked in quite a non-systematic fashion to really catch all the cases that 
 * have been squeezed into newer ARM architectures.
 * 
 * @author Michael Baer
 *
 */
public class ARM_InstructionDecoder {
  
  /** This static field caches the default {@link ARM_InstructionFactory} implementation, which is used by {@link #decode(int)}.
   * It is being lazily initialized once it is used for the first time. */
  private static DefaultFactory _defaultFactory;
  
  /**
   * Decodes a given ARM instruction and returns an object representation of it.
   * @param instruction
   *  A binary ARM instruction, that is to be decoded.
   * @return
   *  A version of the instruction, which has been decoded into an instance of {@link Instruction}.
   *  Use the {@link Instruction#visit(ARM_InstructionVisitor)} method to further interact with the
   *  returned instance.
   */
  public static Instruction decode(int instruction) {
    if (_defaultFactory == null)
      _defaultFactory = new DefaultFactory();
    
    return decode(instruction, _defaultFactory);
  }

  /**
   * Decodes a binary ARM instruction. This method will use the supplied {@link ARM_InstructionFactory}
   * to create an object representation of the decoded instruction.
   * @param <T>
   *  The return type depends on whatever the {@link ARM_InstructionFactory} actually creates.
   * @param instruction
   *  A binary representation of the instruction that is to be decoded.
   * @param factory
   *  A factory, that will create object instances of the instruction.
   * @return
   *  An object representation of the decoded instruction.
   */
  static <T> T decode(int instruction, ARM_InstructionFactory<T> factory) {
    if (Utils.getBit(instruction, 27)) {
      return decode_1xx(instruction, factory);
    }
    else {
      return decode_0xx(instruction, factory);
    }
  }
  
  private static <T> T decode_0xx(int instr, ARM_InstructionFactory<T> factory) {
    if ((instr & 0xF0000000) == 0xF0000000) {
      return factory.createUndefinedInstruction(instr);
    }
    
    if (Utils.getBit(instr, 26)) {
      //opcode: 01
      if (Utils.getBit(instr, 25) && Utils.getBit(instr, 4))
        return factory.createUndefinedInstruction(instr);
      else
        return factory.createSingleDataTransfer(instr);
    }
    else {
      //opcode: 00
      return decode_00x(instr, factory);
    }
  }
  
  private static <T> T decode_1xx(int instr, ARM_InstructionFactory<T> factory) {
    if (Utils.getBit(instr, 26)) {
      //opcode: 11
      return decode_11x( instr, factory);
    }
    else {
      //opcode: 10
      return decode_10x(instr, factory);
    }
  }
  
  private static <T> T decode_00x(int instr, ARM_InstructionFactory<T> factory) {
    if (Utils.getBit(instr, 25))
      return decode_001(instr, factory);
    else
      return decode_000(instr, factory);
  }
  
  private static <T> T decode_10x(int instr, ARM_InstructionFactory<T> factory) {
    if (Utils.getBit(instr, 25)) {
      //opcode: 101
      if ((instr & 0xF0000000) == 0xF0000000)
        return factory.createBranchExchange(instr);
      else
        return factory.createBranch(instr);
    }
    else {
      //opcode: 100
      if ((instr & 0xF0000000) == 0xF0000000) 
        return factory.createUndefinedInstruction(instr);
      else
        return factory.createBlockDataTransfer(instr);
    }
  }
  
  private static <T> T decode_000(int instr, ARM_InstructionFactory<T> factory) {
    //opcode: 000
    if (Utils.getBit(instr, 24) && !Utils.getBit(instr, 23) && !Utils.getBit(instr, 20)) {
      //opcode: 00010xx0 - those are the new instructions, which the ARM ref. manual calls "misc. instructions" 
      return decode_00010xx0(instr, factory);
    }
    else {
      if (Utils.getBit(instr, 4) == false || Utils.getBit(instr, 7) == false)
        return factory.createDataProcessing(instr);
      
      return decode_multiplies_extra_load_stores(instr, factory);
    }
  }
  
  private static <T> T decode_001(int instr, ARM_InstructionFactory<T> factory) {
    //opcode: 001
    if (!Utils.getBit(instr, 24) || Utils.getBit(instr, 23) || Utils.getBit(instr, 20)) {
      return factory.createDataProcessing(instr);
    }
    
    if (Utils.getBit(instr, 21))
      return factory.createMoveToStatusRegister(instr);
    else
      return factory.createUndefinedInstruction(instr);
  }
  
  private static <T> T decode_11x(int instr, ARM_InstructionFactory<T> factory) {
    
    if (Utils.getBit(instr, 25) == false) {
      //opcode: 110
      return factory.createCoprocessorDataTransfer(instr);
    }
    
    //opcode: 111
    if (Utils.getBit(instr, 24)) {
      //opcode: 1111
      if ((instr & 0xF0000000) == 0xF0000000)
        return factory.createUndefinedInstruction(instr);
      else
        return factory.createSoftwareInterrupt(instr);
    }
    else {
      //opcode: 1110
      if (Utils.getBit(instr, 4)) {
        return factory.createCoprocessorDataTransfer(instr);
      }
      else {
        return factory.createCoprocessorRegisterTransfer(instr);
      }
    }
  }
  /** Decodes instructions with the opcode 00010xx0 - those are the new instructions, which
   *  the ARM ref. manual calls "misc. instructions".
   *  
   *  @see Page A3-4 in the ARM Reference Manual (ARM DDI 0100 E) / 2000
   */
  private static <T> T decode_00010xx0(int instr, ARM_InstructionFactory<T> factory) {
    //
    if (Utils.getBit(instr, 6) || Utils.getBit(instr, 7)) {
      //enhanced DSP multiplications, DSP add/subtracts and software breakpoints
      //we might want to support these in the future, so when in debug mode, catch if any program actually uses them
      if (DBT.VerifyAssertions) DBT._assert(false);
      return factory.createUndefinedInstruction(instr);
    }
    else {
      //bit 6 and 7 are clear 
      if (Utils.getBit(instr, 4)) {
        if (Utils.getBit(instr, 22))
          return factory.createCountLeadingZeros(instr);
        else
          return factory.createBranchExchange(instr);
      }
      else {
        if (Utils.getBit(instr, 21))
          return factory.createMoveToStatusRegister(instr);
        else
          return factory.createMoveFromStatusRegister(instr);
      }
    }
  }
  
  /** This might appear even more weird, but I didn't design the ARM ISA. This function decodes
   *  all the operations defined on p. A3-3 in the ARM reference manual from 2000 (ARM DDI 0100 E).
   *  
   *  @see ARM Reference Manual (ARM DDI 0100 E) / 2000
   */
  private static <T> T decode_multiplies_extra_load_stores(int instr, ARM_InstructionFactory<T> factory) {
    //Here, we already know that bits 4 and 7 are set, while bit 25-27 are clear
    if (Utils.getBit(instr, 6)) {
      //load/store signed half-word or two words
      if (Utils.getBit(instr, 20))
        return factory.createSingleDataTransfer(instr);
      else
        return factory.createUndefinedInstruction(instr); //two words immediate offset
    }
    else {
      if (Utils.getBit(instr, 5)) {
        //load/store half-word
        return factory.createSingleDataTransfer(instr);
      }
      else {
        //Multiply, multiply long or Swap
        if (Utils.getBit(instr, 24)) {
          return factory.createSwap(instr);
        }
        else {
          if (Utils.getBit(instr, 23))
            return factory.createLongMultiply(instr);
          else
            return factory.createIntMultiply(instr);
        }
      }
    }
  }
  
  /**
   * An interface to a factory class, which will create the actual object representations of the
   * instruction classes decoded by {@link ARM_InstructionDecoder}.
   *
   * @param <T>
   *  The type of the object representations, that shall be created when an ARM instruction is decoded.
   */
  interface ARM_InstructionFactory<T> {
    T createDataProcessing(int instr);
    T createSingleDataTransfer(int instr);
    T createBlockDataTransfer(int instr);
    T createIntMultiply(int instr);
    T createLongMultiply(int instr);
    T createSwap(int instr);
    T createSoftwareInterrupt(int instr);
    T createBranch(int instr);
    T createBranchExchange(int instr);
    T createCoprocessorDataTransfer(int instr);
    T createCoprocessorDataProcessing(int instr);
    T createCoprocessorRegisterTransfer(int instr);
    T createMoveFromStatusRegister(int instr);
    T createMoveToStatusRegister(int instr);
    T createCountLeadingZeros(int instr);
    T createUndefinedInstruction(int instr);
  }
  
  /**
   * A default implementation of the ARM instruction factory, which will create the 
   * appropriate classes from the {@link ARM_Instructions} namespace.
   */
  static class DefaultFactory implements ARM_InstructionFactory<ARM_Instructions.Instruction> {

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
      return new CoprocessorDataProcessing(instr);
    }

    public Instruction createCoprocessorDataTransfer(int instr) {
      return new CoprocessorDataTransfer(instr);
    }

    public Instruction createCoprocessorRegisterTransfer(int instr) {
      return new CoprocessorRegisterTransfer(instr);
    }

    public Instruction createCountLeadingZeros(int instr) {
      return new CountLeadingZeros(instr);
    }

    public Instruction createDataProcessing(int instr) {
      return new DataProcessing(instr);
    }

    public Instruction createIntMultiply(int instr) {
      return new IntMultiply(instr);
    }

    public Instruction createLongMultiply(int instr) {
      return new LongMultiply(instr);
    }

    public Instruction createMoveFromStatusRegister(int instr) {
      return new MoveFromStatusRegister(instr);
    }

    public Instruction createMoveToStatusRegister(int instr) {
      return new MoveToStatusRegister(instr);
    }

    public Instruction createSingleDataTransfer(int instr) {
      return new SingleDataTransfer(instr);
    }

    public Instruction createSoftwareInterrupt(int instr) {
      return new SoftwareInterrupt(instr);
    }

    public Instruction createSwap(int instr) {
      return new Swap(instr);
    }

    public Instruction createUndefinedInstruction(int instr) {
      return null;
    }
    
  }
}
