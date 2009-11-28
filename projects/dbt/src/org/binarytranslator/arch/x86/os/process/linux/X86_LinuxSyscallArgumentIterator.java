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
package org.binarytranslator.arch.x86.os.process.linux;

import org.binarytranslator.DBT;
import org.binarytranslator.arch.x86.os.process.X86_Registers;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;

public class X86_LinuxSyscallArgumentIterator implements LinuxSystemCallGenerator.CallArgumentIterator {
  
  private final static int[] order = {X86_Registers.EBX,  X86_Registers.ECX, X86_Registers.EDX, X86_Registers.ESI, X86_Registers.EDI, X86_Registers.EBP};
  
  private final X86_LinuxProcessSpace ps;
  private int nextParameter;
  
  public X86_LinuxSyscallArgumentIterator(X86_LinuxProcessSpace ps) {
    this.ps = ps;
    this.nextParameter = 0;
  }

  public int nextInt() {
    if (DBT.VerifyAssertions) DBT._assert(nextParameter <= 6);
    
    return ps.registers.readGP32(order[nextParameter++]);
  }

  public long nextLong() {
    throw new UnsupportedOperationException("X86 System Calls do not support long arguments, yet.");
  }
  
  /**
   * We're actually reusing that class instead of initializing a new one each time syscall arguments are inspected.
   * Therefore, this function starts iterating over all arguments anew.
   */
  public void reset() {
    nextParameter = 0;
  }

}
