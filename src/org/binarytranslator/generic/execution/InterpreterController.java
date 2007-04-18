package org.binarytranslator.generic.execution;

import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.process.ProcessSpace;

public class InterpreterController extends ExecutionController {

  public InterpreterController(ProcessSpace ps) {
    super(ps);
  }

  @Override
  public void run() {
    Interpreter interpreter = ps.createInstructionInterpreter();
    int pc = ps.getCurrentInstructionAddress();
    
    while (!ps.finished) {
      
      Interpreter.Instruction instruction = interpreter.decode(pc);
      pc = instruction.getSuccessor(pc);
      
      System.out.println("Interpreting instruction: " + instruction.toString());
      
      instruction.execute();
      
      if (pc == -1)
        pc = ps.getCurrentInstructionAddress();
    }
  }

}
