package org.binarytranslator.generic.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.process.ProcessSpace;

public final class PredecodingThreadedInterpreter extends ExecutionController {
  private final HashMap<Integer, List<Interpreter.Instruction>> traceCache = new HashMap<Integer, List<Interpreter.Instruction>>();
  private final Interpreter interpreter;
  
  private List<Interpreter.Instruction> getTrace(int pc) {
    List<Interpreter.Instruction> cachedTrace = traceCache.get(pc);
    
    if (cachedTrace != null)
      return cachedTrace;
    
    int traceStart = pc;
    ArrayList<Interpreter.Instruction> newTrace = new ArrayList<Interpreter.Instruction>(5);
    
    while (true)  {
      Interpreter.Instruction instruction = interpreter.decode(pc);
      pc = instruction.getSuccessor(pc);
      newTrace.add(instruction);
      
      //is the successor to this instruction known?
      if (pc == -1) {
        
        //No, so stop the trace after this instruction
        if (newTrace.size() > 3) {
          //add this trace to the trace cache, if it contains enough instructions
          traceCache.put(traceStart, newTrace);
        }
        
        break;
      }
    }
    
    return newTrace;
  }
  
  private void executeTrace(List<Interpreter.Instruction> trace, int pc) {
    
    Iterator<Interpreter.Instruction> instructions = trace.iterator();
    while (instructions.hasNext()) {
      Interpreter.Instruction instr = instructions.next();
      instr.execute();
      pc = instr.getSuccessor(pc);
      
      if (pc != -1)
        ps.setCurrentInstructionAddress(pc);
    }
  }
  
  public PredecodingThreadedInterpreter(ProcessSpace ps) {
    super(ps);
    interpreter = ps.createInstructionInterpreter();
  }

  @Override
  public void run() {
    int pc = ps.getCurrentInstructionAddress();

    while (!ps.finished) {
      
      List<Interpreter.Instruction> trace = getTrace(pc);
      executeTrace(trace, pc);
      pc = ps.getCurrentInstructionAddress();
    }
  }
}
