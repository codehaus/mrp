package org.binarytranslator.generic.gdbstub;

import org.binarytranslator.generic.fault.BadInstructionException;

public interface GDBTarget {
  /**
   * Read a register from the target machine.
   * 
   * @param regNum
   *          A register number, starting from 0.
   */
  byte[] readRegisterGDB(int regNum);

  /**
   * Run a single instruction
   */
  void runOneInstruction() throws BadInstructionException;

  /**
   * Has frame base register?
   */
  boolean hasFrameBaseRegister();

  /**
   * Get the value of the frame base register
   */
  int getGDBFrameBaseRegister();

  /**
   * Get the value of the frame base register
   */
  int getGDBStackPointerRegister();

  /**
   * Get the value of the frame base register
   */
  int getGDBProgramCountRegister();

  /**
   * Return the address of the current instruction.
   */
  int getCurrentInstructionAddress();

  /**
   * Store the given bye of data at the given address within the process.
   */
  void memoryStore8(int address, byte data);

  /**
   * Load a byte from the given address within the process.
   */
  byte memoryLoad8(int address);
}
