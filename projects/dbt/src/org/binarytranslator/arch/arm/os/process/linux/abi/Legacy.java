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
package org.binarytranslator.arch.arm.os.process.linux.abi;

import org.binarytranslator.DBT;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions;
import org.binarytranslator.arch.arm.os.process.linux.ARM_LinuxProcessSpace;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;
import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * ARM legacy linux system call ABI.
 * <code>
 * Example call to long ftruncate64(unsigned int fd, loff_t length):
 * 
 * legacy ABI:
 * - put fd into r0
 * - put length into r1-r2
 * - use "swi #(0x900000 + 194)" to call the kernel (swi #194 for thumb linux).
 * 
 * </code>
 * 
 * @author Michael Baer
 *
 */
public class Legacy implements LinuxSystemCallGenerator {
  
  /** The process space that we're running on. */
  private final ARM_LinuxProcessSpace ps;
  
  /** A re-used iterator that allows enumerating the argument of the current
   *  system call */
  private final ArgumentIterator syscallArgs;
  
  public Legacy(ARM_LinuxProcessSpace ps) {
    this.ps = ps;
    syscallArgs = new ArgumentIterator();
  }

  public ProcessSpace getProcessSpace() {
    return ps;
  }

  public CallArgumentIterator getSysCallArguments() {
    syscallArgs.reset();
    return syscallArgs;
  }

  public int getSysCallNumber() {
    //ARM syscalls are trigged by a SWI instruction, which includes the syscall number within its opcode.
    //therefore, we are going to decode the instruction first
    ARM_Instructions.Instruction instr;
    
    if (ps.registers.getThumbMode()) {
      short instruction = (short)ps.memory.loadInstruction16(ps.getCurrentInstructionAddress());
      instr = ARM_InstructionDecoder.Thumb.decode(instruction);
    }
    else {
      int instruction = ps.memory.loadInstruction32(ps.getCurrentInstructionAddress());
      instr = ARM_InstructionDecoder.ARM32.decode(instruction);      
    }
    
    if (DBT.VerifyAssertions) {
      if (!(instr instanceof ARM_Instructions.SoftwareInterrupt)) {
        throw new Error("The current instruction is not a valid system call.");
      }
    }
    
    //Thumb system calls start from 0, while ARM calls start from 0x900000.
    //Use a mask to let both calls start from the same address
    int sysCallNr = ((ARM_Instructions.SoftwareInterrupt)instr).getInterruptNumber();
    return sysCallNr & 0xFFFFF;
  }

  public void setSysCallError(int r) {
    ps.registers.set(0, -r);
  }

  public void setSysCallReturn(int value) {
    ps.registers.set(0, value);
  }
  
  /** An argument iterator that hides the layout of syscall arguments on the ARM architecture */
  public final class ArgumentIterator implements
      LinuxSystemCallGenerator.CallArgumentIterator {

    /** The current argument number. Set to zero for the first agument.*/
    private int currentArgument;

    public ArgumentIterator() {
      this.currentArgument = 0;
    }

    public int nextInt() {
      return ps.registers.get(currentArgument++);
    }

    public long nextLong() {
      // TODO: This code is actually assuming that we're on little endian
      long lowWord = nextInt();
      long highWord = nextInt();

      return highWord << 32 | lowWord;
    }

    /** Restarts argument reading from the first argument. Allows this object to be reused for multiple sys calls. */
    private void reset() {
      currentArgument = 0;
    }
  }
}
