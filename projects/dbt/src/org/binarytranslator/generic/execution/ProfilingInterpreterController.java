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
package org.binarytranslator.generic.execution;

import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * Implements straight-forward interpretation using the {@link Interpreter} 
 * and {@link Interpreter.Instruction} interfaces. 
 * 
 * Additionally, this controller performs profiling during the interpretation.
 */
public class ProfilingInterpreterController extends ExecutionController {

  public ProfilingInterpreterController(ProcessSpace ps) {
    super(ps);
  }

  @Override
  public void run() {
    Interpreter interpreter = ps.createInterpreter();
    
    int pc = ps.getCurrentInstructionAddress();

    while (!ps.finished) {
      Interpreter.Instruction instruction = interpreter.decode(pc);
      
      instruction.execute();
      int nextInstruction = instruction.getSuccessor(pc);
      
      if (nextInstruction == -1) {
        nextInstruction = ps.getCurrentInstructionAddress();
        ps.branchInfo.profileBranch(pc, ps.getCurrentInstructionAddress());
      }
      else {
        ps.setCurrentInstructionAddress(nextInstruction);
      }
      
      pc = nextInstruction;
    }
  }

}
