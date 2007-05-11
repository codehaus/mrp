package org.binarytranslator.arch.arm.os.process;

import java.io.IOException;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.decoder.ARM2IR;
import org.binarytranslator.arch.arm.decoder.ARM_Disassembler;
import org.binarytranslator.arch.arm.decoder.ARM_Interpreter;
import org.binarytranslator.arch.arm.os.process.image.ARM_ImageProcessSpace;
import org.binarytranslator.arch.arm.os.process.linux.ARM_LinuxProcessSpace;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.memory.DebugMemory;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.jikesrvm.compilers.opt.ir.OPT_GenerationContext;
import org.jikesrvm.compilers.opt.ir.OPT_HIRGenerator;
import org.vmmagic.pragma.Uninterruptible;

public abstract class ARM_ProcessSpace extends ProcessSpace {


  /** Registers used by this process */
  public ARM_Registers registers;


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

  protected ARM_ProcessSpace() {
    registers = new ARM_Registers();
    memory = new DebugMemory();
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
    return new ARM2IR(context);
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
    Loader.ABI abi = loader.getABI();
    if (abi == Loader.ABI.ARM) {
      report("Creating ARM Linux ABI Process space");
      return new ARM_LinuxProcessSpace();
    } else {
      report("Creating ARM image process space.");
      return new ARM_ImageProcessSpace();
    }
  }
  
  @Override
  public Interpreter createInstructionInterpreter() throws UnsupportedOperationException {
    return new ARM_Interpreter(this);
  }

  /**
   * Return as an integer the current instruction's address
   */
  @Uninterruptible
  public int getCurrentInstructionAddress() {
    return registers.get(ARM_Registers.PC);
  }

  /**
   * Sets the current instruction's address
   */
  public void setCurrentInstructionAddress(int pc) {
    registers.set(ARM_Registers.PC, pc);
  }

  /**
   * Return a string disassembly of the instuction at the given address
   */
  @Uninterruptible
  public String disassembleInstruction(int pc) {
    return ARM_Disassembler.disassemble(pc, this).asString();
  }
  
  /**
   * Return as an integer the current instruction's address
   */
  public int getCurrentStackAddress() {
    return registers.get(ARM_Registers.SP);
  }

  /**
   * Turn the process space into a string (for debug)
   */
  public String toString() {
    return registers.toString();
  }
  
  /**
   * ARM undefined instruction handler. This method is called when the ARM processor
   * is asked to execute an undefined instruction. By default, this throws a runtime exception.
   * 
   * However, derived classes may re-implement this behaviour to achieve full system emulation.
   *
   */
  public void doUndefinedInstruction() {
    throw new RuntimeException("Undefined instruction at 0x" + Integer.toHexString(getCurrentInstructionAddress()));
  }
}
