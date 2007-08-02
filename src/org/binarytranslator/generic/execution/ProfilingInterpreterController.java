package org.binarytranslator.generic.execution;

import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.process.ProcessSpace;

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
