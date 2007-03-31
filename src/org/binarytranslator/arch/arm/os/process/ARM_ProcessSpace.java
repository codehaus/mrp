package org.binarytranslator.arch.arm.os.process;

import java.io.IOException;
import org.jikesrvm.opt.ir.OPT_HIRGenerator;
import org.jikesrvm.opt.ir.OPT_GenerationContext;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.os.process.image.ARM_ImageProcessSpace;
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
    System.out.println("Executing instr: " + memory.load32(0));
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
    if (loader.isARM_ABI() || loader.isSysV_ABI()) {
      report("Creating ARM Linux ABI [rocess space");
      return new ARM_LinuxProcessSpace();
    } else {
      report("Creating ARM image process space.");
      return new ARM_ImageProcessSpace();
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
    return registers.read(ARM_Registers.PC);
  }

  /**
   * Sets the current instruction's address
   */
  public void setCurrentInstructionAddress(int pc) {
    registers.write(ARM_Registers.PC, pc);
  }

  /**
   * Return as an integer the current instruction's address
   */
  public int getCurrentStackAddress() {
    return registers.read(14);
  }

  /**
   * Turn the process space into a string (for debug)
   */
  public String toString() {
    return registers.toString();
  }
}
