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
      //System.out.println(String.format("[0x%x] %s", pc, instruction.toString()));
      
      instruction.execute();
      pc = instruction.getSuccessor(pc);
      
      if (pc == -1)
        pc = ps.getCurrentInstructionAddress();
      else
        ps.setCurrentInstructionAddress(pc);
    }
  }

}
