package org.binarytranslator.generic.execution;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.process.ProcessSpace;

public class InterpreterController extends ExecutionController {

  public InterpreterController(ProcessSpace ps) {
    super(ps);
  }

  @Override
  public void run() {
    Interpreter interpreter = ps.createInterpreter();
    
    int pc = ps.getCurrentInstructionAddress();

    while (!ps.finished) {
      
      Interpreter.Instruction instruction = interpreter.decode(pc);
      //System.out.println(String.format("[0x%x] %s", pc, instruction.toString()));
      
      instruction.execute();
      //System.out.println(ps.toString());
      int nextInstruction = instruction.getSuccessor(pc);
      
      if (nextInstruction == -1) {
        nextInstruction = ps.getCurrentInstructionAddress();
        
        if (DBT_Options.profileDuringInterpretation)
          ps.branchInfo.profileBranch(pc, ps.getCurrentInstructionAddress());
      }
      else {
        ps.setCurrentInstructionAddress(nextInstruction);
      }
      
      pc = nextInstruction;
    }
  }
}
