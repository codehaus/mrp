package org.binarytranslator.generic.execution;

import java.util.Iterator;
import java.util.List;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.decoder.Interpreter.Instruction;
import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * Adds runtime profiling capabilities to {@link PredecodingThreadedInterpreter}.
 */
public class ProfilingPredecodingInterpreter extends PredecodingThreadedInterpreter {

  public ProfilingPredecodingInterpreter(ProcessSpace ps) {
    super(ps);
  }
  
  @Override
  protected void executeTrace(List<Instruction> trace, int pc) {
    Iterator<Interpreter.Instruction> instructions = trace.iterator();
    while (true) {
      Interpreter.Instruction instr = instructions.next();
      
      if (instructions.hasNext()) {
        instr.execute();
        pc = instr.getSuccessor(pc);
        ps.setCurrentInstructionAddress(pc);
        
      }
      else {
        int instructionAdddress = ps.getCurrentInstructionAddress();
        instr.execute();
        pc = ps.getCurrentInstructionAddress();
        ps.branchInfo.profileBranch(instructionAdddress, pc);
        break;
      }
    }
  }

}
