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
package org.binarytranslator.generic.fault;

import org.binarytranslator.generic.os.process.ProcessSpace;

public class BadInstructionException extends RuntimeException {
  
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
