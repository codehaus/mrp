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
package org.binarytranslator.arch.ppc.os.process.linux;

import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;

final class PPC_LinuxSyscallArgumentIterator implements
    LinuxSystemCallGenerator.CallArgumentIterator {

  /**
   * That process space that this class is enumerating the arguments from.
   */
  private final PPC_LinuxProcessSpace ps;

  /**
   * The index of the argument that is fetched by the next call to next<datatype>()
   */
  private int nextArgument;

  public PPC_LinuxSyscallArgumentIterator(PPC_LinuxProcessSpace ps) {
    this.ps = ps;
    this.nextArgument = 0;
  }

  /**
   * Return the next integer argument by reading it from a register, starting
   * with GPR3.
   */
  public int nextInt() {
    return ps.getRegister(3 + nextArgument++);
  }

  /**
   * Returns the next long argument. This implementation follows the information
   * on p. 3-19f of http://refspecs.freestandards.org/elf/elfspec_ppc.pdf
   */
  public long nextLong() {

    // Start reading long arguments only from uneven registers
    if ((nextArgument & 1) == 0) {
      nextArgument++;
    }

    long lowWord = nextInt();
    long highWord = nextInt();

    return highWord << 32 | lowWord;
  }

  /**
   * We're actually reusing that class instead of initializing a new one each
   * time syscall arguments are inspected. Therefore, this function starts
   * iterating over all arguments anew.
   */
  public void reset() {
    nextArgument = 0;
  }
}
