package org.binarytranslator.generic.execution;

import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * Implements straight-forward interpretation using the {@link Interpreter} 
 * and {@link Interpreter.Instruction} interfaces.
 */
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
      //System.out.println(String.format("[%x] %s", pc, instruction.toString()));      
      instruction.execute();
      int nextInstruction = instruction.getSuccessor(pc);
      
      if (nextInstruction == -1) {
        nextInstruction = ps.getCurrentInstructionAddress();
      }
      else {
        ps.setCurrentInstructionAddress(nextInstruction);
      }
      
      pc = nextInstruction;
    }
  }
}
