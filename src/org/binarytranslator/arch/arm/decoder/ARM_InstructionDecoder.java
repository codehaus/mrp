package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;

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

  public static <T> T decode(int instr, ARM_InstructionFactory<T> factory) {
    if (getBit(instr, 27)) {
      return decode_1xx(instr, factory);
    }
    else {
      return decode_0xx(instr, factory);
    }
  }
  
  private static <T> T decode_0xx(int instr, ARM_InstructionFactory<T> factory) {
    if ((instr & 0xF0000000) == 0xF0000000) {
      return factory.createUndefinedInstruction(instr);
    }
    
    if (getBit(instr, 26)) {
      //opcode: 01
      if (getBit(instr, 25) && getBit(instr, 4))
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
    if (getBit(instr, 26)) {
      //opcode: 11
      return decode_11x( instr, factory);
    }
    else {
      //opcode: 10
      return decode_10x(instr, factory);
    }
  }
  
  private static <T> T decode_00x(int instr, ARM_InstructionFactory<T> factory) {
    if (getBit(instr, 25))
      return decode_001(instr, factory);
    else
      return decode_000(instr, factory);
  }
  
  private static <T> T decode_10x(int instr, ARM_InstructionFactory<T> factory) {
    if (getBit(instr, 25)) {
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
    if (getBit(instr, 24) && !getBit(instr, 23) && !getBit(instr, 20)) {
      //opcode: 00010xx0 - those are the new instructions, which the ARM ref. manual calls "misc. instructions" 
      return decode_00010xx0(instr, factory);
    }
    else {
      if (getBit(instr, 4) == false || getBit(instr, 7) == false)
        return factory.createDataProcessing(instr);
      
      return decode_multiplies_extra_load_stores(instr, factory);
    }
  }
  
  private static <T> T decode_001(int instr, ARM_InstructionFactory<T> factory) {
    //opcode: 001
    if (!getBit(instr, 24) || getBit(instr, 23) || getBit(instr, 20)) {
      return factory.createDataProcessing(instr);
    }
    
    if (getBit(instr, 21))
      return factory.createMoveToStatusRegister(instr);
    else
      return factory.createUndefinedInstruction(instr);
  }
  
  private static <T> T decode_11x(int instr, ARM_InstructionFactory<T> factory) {
    
    if (getBit(instr, 25) == false) {
      //opcode: 110
      return factory.createCoprocessorDataTransfer(instr);
    }
    
    //opcode: 111
    if (getBit(instr, 24)) {
      //opcode: 1111
      if ((instr & 0xF0000000) == 0xF0000000)
        return factory.createUndefinedInstruction(instr);
      else
        return factory.createSoftwareInterrupt(instr);
    }
    else {
      //opcode: 1110
      if (getBit(instr, 4)) {
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
    if (getBit(instr, 6) || getBit(instr, 7)) {
      //enhanced DSP multiplications, DSP add/subtracts and software breakpoints
      //we might want to support these in the future, so when in debug mode, catch if any program actually uses them
      if (DBT.VerifyAssertions) DBT._assert(false);
      return factory.createUndefinedInstruction(instr);
    }
    else {
      //bit 6 and 7 are clear 
      if (getBit(instr, 4)) {
        if (getBit(instr, 22))
          return factory.createCountLeadingZeros(instr);
        else
          return factory.createBranchExchange(instr);
      }
      else {
        if (getBit(instr, 21))
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
    if (getBit(instr, 6)) {
      //load/store signed half-word or two words
      if (getBit(instr, 20))
        return factory.createSingleDataTransfer(instr);
      else
        return factory.createUndefinedInstruction(instr); //two words immediate offset
    }
    else {
      if (getBit(instr, 5)) {
        //load/store half-word
        return factory.createSingleDataTransfer(instr);
      }
      else {
        //Multiply, multiply long or Swap
        if (getBit(instr, 24)) {
          return factory.createSwap(instr);
        }
        else {
          if (getBit(instr, 23))
            return factory.createLongMultiply(instr);
          else
            return factory.createIntMultiply(instr);
        }
      }
    }
  }
}
