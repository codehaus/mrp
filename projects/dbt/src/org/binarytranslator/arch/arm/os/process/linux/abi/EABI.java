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

import org.binarytranslator.arch.arm.os.process.linux.ARM_LinuxProcessSpace;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;
import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * ARM EABI linux system call ABI.
 * <code>
 * Example call to long ftruncate64(unsigned int fd, loff_t length):
 * 
 * EABI ABI:
 * - put fd into r0
 * - put length into r2-r3 (skipping over r1)
 * - put 194 into r7
 * - use "swi #0" to call the kernel
 * 
 * </code>
 * 
 * Therefore, note that long values are aligned to even registers.
 * The holes, which are created within the register map are not filled, though.
 * 
 * @author Michael Baer
 *
 */
public class EABI implements LinuxSystemCallGenerator {
  
  /** The process space that we're running on. */
  private final ARM_LinuxProcessSpace ps;
  private final ArgumentIterator args; 
  
  public EABI(ARM_LinuxProcessSpace ps) {
    this.ps = ps;
    this.args = new ArgumentIterator();
  }

  public ProcessSpace getProcessSpace() {
    return ps;
  }

  public CallArgumentIterator getSysCallArguments() {
    args.reset();
    return args;
  }

  public int getSysCallNumber() {
    return ps.registers.get(7);
  }

  public void setSysCallError(int r) {
    ps.registers.set(0, -r);
  }

  public void setSysCallReturn(int r) {
    ps.registers.set(0, r);
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
      // only start reading longs from even registers
      if ((currentArgument & 1) != 0)
        currentArgument++;

      // TODO: This code is actually assuming that we're on little endian
      long lowWord = nextInt();
      long highWord = nextInt();

      return highWord << 32 | lowWord;
    }
    
    /** Restarts argument reading from the first argument. Allows this object to be reused for multiple sys calls. */
    public void reset() {
      currentArgument = 0;
    }
  }

}
