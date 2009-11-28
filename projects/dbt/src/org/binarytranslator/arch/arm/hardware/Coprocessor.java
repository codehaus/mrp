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
package org.binarytranslator.arch.arm.hardware;

import org.binarytranslator.arch.arm.decoder.ARM2IR;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.*;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;

public abstract class Coprocessor {
  
  protected final ARM_ProcessSpace ps;
  protected final ARM_Registers regs;
  
  public abstract void interpretDataProcessing(CoprocessorDataProcessing instr);
  public abstract void interpretDataTransfer(CoprocessorDataTransfer instr);
  public abstract void interpretRegisterTransfer(CoprocessorRegisterTransfer instr);
  
  public abstract void translateDataProcessing(CoprocessorDataProcessing instr, ARM2IR arm2ir, int pc);
  public abstract void translateDataTransfer(CoprocessorDataTransfer instr, ARM2IR arm2ir, int pc);
  public abstract void translateRegisterTransfer(CoprocessorRegisterTransfer instr, ARM2IR arm2ir, int pc);
  
  protected Coprocessor(ARM_ProcessSpace ps) {
    this.ps = ps;
    this.regs = ps.registers;
  }
}
