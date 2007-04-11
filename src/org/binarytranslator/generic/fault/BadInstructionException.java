/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.fault;

import org.binarytranslator.generic.os.process.ProcessSpace;

public class BadInstructionException extends Exception {
  
  private final int pc;
  private final ProcessSpace ps;
  
  public BadInstructionException(int pc, ProcessSpace ps) {
    super("Bad instruction encountered at 0x" + Integer.toHexString(pc));
    this.pc = pc;
    this.ps = ps;
  }
  
  public int getInstructionAddress() {
    return pc;
  }
  
  public ProcessSpace getProcessSpace() {
    return ps;
  }
}
