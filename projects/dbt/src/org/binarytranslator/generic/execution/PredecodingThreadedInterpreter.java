package org.binarytranslator.generic.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * Execution controller that implements predecoding, threaded interpretation.
 *
 */
public class PredecodingThreadedInterpreter extends ExecutionController {
  /** The controller keeps a cache of commonly used code traces. Each trace is essentially a dynamic basic block. 
   * This HashMap maps the address of a trace's first instruction to its dynamic basic block. */
  private final HashMap<Integer, List<Interpreter.Instruction>> traceCache = new HashMap<Integer, List<Interpreter.Instruction>>();
  
  /** The interpreter that is used to perform the actual execution of single instructions. */
  private final Interpreter interpreter;
  
  /** Default constructor. */
  public PredecodingThreadedInterpreter(ProcessSpace ps) {
    super(ps);
    interpreter = ps.createInterpreter();
  }
  
  /**
   * Returns the dynamic basic block of instructions, starting it at address <code>pc</code>.
   * This function also manages the trace cache ({@link #traceCache}).
   * 
   * @param pc
   *  The address at which the dynamic basic block starts.
   * @return
   *  A list of instructions that form the dynamic basic block.
   */
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
  
  /**
   * Executes a list of instructions. It is assumed that the first instruction starts at address <code>pc</code>.
   * 
   * @param trace
   *  The list of instructions that is to be executed.
   * @param pc
   *  The address of the first instruction.
   */
  protected void executeTrace(List<Interpreter.Instruction> trace, int pc) {
    
    Iterator<Interpreter.Instruction> instructions = trace.iterator();
    while (true) {
      Interpreter.Instruction instr = instructions.next();
      instr.execute();
      
      if (instructions.hasNext()) {
        pc = instr.getSuccessor(pc);
        ps.setCurrentInstructionAddress(pc);
      }
      else
        break;
    }
  }

  @Override
  public final void run() {
    int pc = ps.getCurrentInstructionAddress();

    while (!ps.finished) {
      
      List<Interpreter.Instruction> trace = getTrace(pc);
      executeTrace(trace, pc);
      pc = ps.getCurrentInstructionAddress();
    }
  }
}
