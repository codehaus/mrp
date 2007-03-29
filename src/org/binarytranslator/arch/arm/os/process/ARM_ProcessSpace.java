package org.binarytranslator.arch.arm.os.process;

import java.io.IOException;
import org.jikesrvm.opt.ir.OPT_HIRGenerator;
import org.jikesrvm.opt.ir.OPT_GenerationContext;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.os.process.linux.ARM_LinuxProcessSpace;
import org.binarytranslator.arch.x86.decoder.X862IR;
import org.binarytranslator.arch.x86.os.process.X86_Registers;
import org.binarytranslator.arch.x86.os.process.linux.X86_LinuxProcessSpace;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.generic.memory.ByteAddressedMemory;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.process.ProcessSpace;

public abstract class ARM_ProcessSpace extends ProcessSpace {

  /*
   * Instance data
   */

  /**
   * Registers used by this process
   */
  public ARM_Registers registers;

  /* GDB Interface */
  /**
   * Read a register and turn into a byte array conforming to the endianness of
   * the architecture
   */
  public byte[] readRegisterGDB(int regNum) {
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * Has frame base register?
   */
  public boolean hasFrameBaseRegister() {
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * Get the value of the frame base register
   */
  public int getGDBFrameBaseRegister() {
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * Get the value of the frame base register
   */
  public int getGDBStackPointerRegister() {
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * Get the value of the frame base register
   */
  public int getGDBProgramCountRegister() {
    throw new RuntimeException("Not yet implemented");
  }

  /*
   * Utility functions
   */

  /**
   * Debug information
   * 
   * @param s
   *          string of debug information
   */
  private static void report(String s) {
    if (DBT_Options.debugLoader) {
      System.out.print("ARM ProcessSpace:");
      System.out.println(s);
    }
  }

  /*
   * Methods
   */

  /**
   * Constructor
   */
  protected ARM_ProcessSpace() {
    registers = new ARM_Registers();
    memory = new ByteAddressedMemory();
  }

  /**
   * Create an optimizing compiler HIR code generator suitable for this
   * architecture
   * 
   * @param context
   *          the generation context for the HIR generation
   * @return a HIR generator
   */
  public OPT_HIRGenerator createHIRGenerator(OPT_GenerationContext context) {
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * Given an ELF binary loader, create the appropriate process space
   * 
   * @param elf
   *          the elf binary loader
   * @return the appropriate process space
   */
  public static ProcessSpace createProcessSpaceFromBinary(Loader loader)
      throws IOException {
    if (loader.isARM_ABI()) {
      report("ARM ABI");
      return new ARM_LinuxProcessSpace();
    } else {
      throw new UnsupportedOperationException("Binary of " + loader.getABIString()
          + " ABI is unsupported for the ARM architecture");
    }
  }

  /**
   * Run a single instruction
   */
  public void runOneInstruction() throws BadInstructionException {
    // TODO
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * Return as an integer the current instruction's address
   */
  public int getCurrentInstructionAddress() {
    return registers.getPC();
  }

  /**
   * Sets the current instruction's address
   */
  public void setCurrentInstructionAddress(int pc) {
    registers.setPC(pc);
  }

  /**
   * Return as an integer the current instruction's address
   */
  public int getCurrentStackAddress() {
    throw new RuntimeException("Not yet implemented");
  }

  /**
   * Turn the process space into a string (for debug)
   */
  public String toString() {
    return registers.toString();
  }
}
