/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.x86.os.process;

import java.io.*;
import java.util.Hashtable;

import org.jikesrvm.compilers.opt.ir.OPT_GenerationContext;
import org.jikesrvm.compilers.opt.ir.OPT_HIRGenerator;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.memory.ByteAddressedMemory;
import org.binarytranslator.generic.execution.GdbController.GdbTarget;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.arch.ppc.decoder.PPC_InstructionDecoder;
import org.binarytranslator.arch.x86.os.process.linux.X86_LinuxProcessSpace;
import org.binarytranslator.arch.x86.decoder.X862IR;
import org.binarytranslator.arch.x86.decoder.X86_InstructionDecoder;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.binarytranslator.vmInterface.DynamicCodeRunner;
import org.vmmagic.pragma.Uninterruptible;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;

/**
 * Encapsulate the parts of an X86 process that are common across operating systems
 */
public abstract class X86_ProcessSpace extends ProcessSpace implements GdbTarget {

  /*
   * Process defaults
   */

  /**
   * Decode 64bit extensions
   */
  public static final boolean X86_64 = false;

  /**
   * Decode as 16bit or 32bit registers/addresses - overridden by
   * address and operand prefixs
   */
  public static final boolean _16BIT = false;

  /*
   * Instance data
   */

  /**
   * Registers used by this process
   */
  public X86_Registers registers;

  /* GDB Interface */
  /**
   * Read a register and turn into a byte array conforming to the
   * endianness of the architecture
   */
  public byte[] readRegisterGDB(int regNum) {
    int value;
    switch(regNum) {
    case 0: value = registers.readGP32(X86_Registers.EAX); break;
    case 1: value = registers.readGP32(X86_Registers.ECX); break;
    case 2: value = registers.readGP32(X86_Registers.EDX); break;
    case 3: value = registers.readGP32(X86_Registers.EBX); break;
    case 4: value = registers.readGP32(X86_Registers.ESP); break;
    case 5: value = registers.readGP32(X86_Registers.EBP); break;
    case 6: value = registers.readGP32(X86_Registers.ESI); break;
    case 7: value = registers.readGP32(X86_Registers.EDI); break;
    case 8: value = registers.eip; break;
    case 9: value = registers.readEFlags(); break;
    default: System.err.println("Unknown GDB register " + regNum); value = 0; break;
    }
    System.out.println("Read " +regNum+" = " + value);
    byte[] result = new byte[4];
    result[0] = (byte)value;
    result[1] = (byte)(value>>8);
    result[2] = (byte)(value>>16);
    result[3] = (byte)(value>>24);
    return result;
  }

  /**
   * Has frame base register?
   */
  public boolean hasFrameBaseRegister() {
    return true;
  }

  /**
   * Get the value of the frame base register
   */
  public int getGDBFrameBaseRegister() {
    return 5;
  }
  /**
   * Get the value of the frame base register
   */
  public int getGDBStackPointerRegister() {
    return 4;
  }
  /**
   * Get the value of the frame base register
   */
  public int getGDBProgramCountRegister() {
    return 8;
  }
  
  public GdbTarget getGdbTarget() {
    return this;
  }

  /*
   * Utility functions
   */

  /**
   * Debug information
   * @param s string of debug information
   */
  private static void report(String s){
    if (DBT_Options.debugLoader) {
      System.out.print("X86 ProcessSpace:");
      System.out.println(s);
    }
  }

  /*
   * Methods
   */

  /**
   * Constructor
   */
  protected X86_ProcessSpace() {
    registers = new X86_Registers();
    memory = new ByteAddressedMemory();
  }

  /**
   * Create an optimizing compiler HIR code generator suitable for
   * this architecture
   * @param context the generation context for the HIR generation
   * @return a HIR generator
   */
  public OPT_HIRGenerator createHIRGenerator(OPT_GenerationContext context) {
    return new X862IR(context);
  }

  /**
   * Given an ELF binary loader, create the appropriate process space
   * @param elf the elf binary loader
   * @return the appropriate process space
   */
  public static ProcessSpace createProcessSpaceFromBinary (Loader loader) throws IOException {
    Loader.ABI abi = loader.getABI();

    switch (abi) {
    case Linux:
    case SystemV:
      report("Linux/SysV ABI");
      return new X86_LinuxProcessSpace(loader);
    default:
      throw new IOException("Binary of " + abi + " ABI is unsupported for the X86 architecture");
    }
  }

  Hashtable<Integer, DBT_Trace> singleInstrCodeHash = new Hashtable<Integer, DBT_Trace>();
  /**
   * Run a single instruction
   */
  public void runOneInstruction() throws BadInstructionException {
    try {
      // X86_InstructionDecoder.getDecoder(this,registers.eip).interpret(this, registers.eip);
      DBT_Trace trace = singleInstrCodeHash.get(registers.eip);
      if (trace == null) {
        trace = new DBT_Trace(this, registers.eip);
        if (DBT_Options.debugRuntime) {
          report("Translating code for 0x" + Integer.toHexString(trace.pc));
        }
        DBT_Options.singleInstrTranslation = true;
        trace.compile();
        singleInstrCodeHash.put(registers.eip, trace);
      }
      VM_CodeArray code = trace.getCurrentCompiledMethod().getEntryCodeArray();
      registers.eip = DynamicCodeRunner.invokeCode(code, this);
    } catch (NullPointerException e) {
      throw new BadInstructionException(registers.eip, this);
    }
  }

  /**
   * Return as an integer the current instruction's address
   */
  @Uninterruptible
  public int getCurrentInstructionAddress() {
    return registers.eip;
  }
  /**
   * Sets the current instruction's address
   */
  public void setCurrentInstructionAddress(int pc) {
    registers.eip = pc;
  }
  
  /**
   * Return a string disassembly of the instuction at the given address
   */
  @Uninterruptible
  public String disassembleInstruction(int pc) {
    return X86_InstructionDecoder.getDecoder(this,pc).disassemble(this, pc);
  }

  /**
   * Return as an integer the current instruction's address
   */
  public int getCurrentStackAddress() {
    return registers.readGP32(X86_Registers.ESP);
  }

  /**
   * Turn the process space into a string (for debug)
   */
  public String toString() {
    return registers.toString();
  }
}
