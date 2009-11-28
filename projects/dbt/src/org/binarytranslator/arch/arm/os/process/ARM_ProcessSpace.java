/*
 *  This file is part of MRP (http://mrp.codehaus.org/).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.binarytranslator.arch.arm.os.process;

import java.io.IOException;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.decoder.ARM2IR;
import org.binarytranslator.arch.arm.decoder.ARM_Disassembler;
import org.binarytranslator.arch.arm.decoder.ARM_Interpreter;
import org.binarytranslator.arch.arm.decoder.ARM_Options;
import org.binarytranslator.arch.arm.os.process.image.ARM_ImageProcessSpace;
import org.binarytranslator.arch.arm.os.process.linux.ARM_LinuxProcessSpace;
import org.binarytranslator.generic.decoder.CodeTranslator;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.memory.ByteAddressedBigEndianMemory;
import org.binarytranslator.generic.memory.ByteAddressedLittleEndianMemory;
import org.binarytranslator.generic.memory.IntAddressedBigEndianMemory;
import org.binarytranslator.generic.memory.IntAddressedLittleEndianMemory;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.loader.elf.ELF_Loader;
import org.binarytranslator.generic.os.loader.elf.ELF_File.ByteOrder;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;

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

  protected ARM_ProcessSpace(ByteOrder byteOrder) {
    registers = new ARM_Registers();
    
    switch (ARM_Options.memoryModel) {
    case ByteAddressed:
      
      switch (byteOrder)
      {
      case LittleEndian:
        memory = new ByteAddressedLittleEndianMemory();
        break;
        
      case BigEndian:
        memory = new ByteAddressedBigEndianMemory();
        break;
        
      default:
        throw new RuntimeException("Unexpected byte order: " + byteOrder);
      }
      
      break;
      
    case IntAddressed:

      switch (byteOrder)
      {
      case LittleEndian:
        memory = new IntAddressedLittleEndianMemory();
        break;
        
      case BigEndian:
        memory = new IntAddressedBigEndianMemory();
        break;
        
      default:
        throw new RuntimeException("Unexpected byte order: " + byteOrder);
      }
      
      break;
      
    default:
      throw new RuntimeException("Unexpected ARM memory model setting: " + ARM_Options.memoryModel);
    }
    
  }

  /**
   * Create an optimizing compiler HIR code generator suitable for this
   * architecture
   * 
   * @param context
   *          the generation context for the HIR generation
   * @return a HIR generator
   */
  @Override
  public CodeTranslator createTranslator(GenerationContext context, DBT_Trace trace) {
    return new ARM2IR(context, trace);
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
    
    //determine the byte order for the memory implementation
    ByteOrder byteOrder = ARM_Options.enforcedByteOrder;
    
    if (byteOrder == null) {
      if (loader instanceof ELF_Loader) {
        //read the byte order from the elf file
        byteOrder = ((ELF_Loader)loader).getFile().getByteOrder();
      }
      else {
        byteOrder = ByteOrder.LittleEndian;
        System.err.println("WARNING: Unable to deduce byte order from binary file. Defaulting to " + byteOrder);
      }
    }
    else {
      System.err.println("WARNING: Overriding byte order set by ELF file to " + byteOrder);
    }

    if (abi == Loader.ABI.ARM) {
      report("Creating ARM Linux ABI Process space");
      return new ARM_LinuxProcessSpace(byteOrder);
    } else {
      report("Creating ARM image process space.");
      return new ARM_ImageProcessSpace(byteOrder);
    }
  }
  
  @Override
  public Interpreter createInterpreter() throws UnsupportedOperationException {
    return new ARM_Interpreter(this);
  }

  /**
   * Return as an integer the current instruction's address
   */
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
