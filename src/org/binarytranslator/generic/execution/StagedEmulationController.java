package org.binarytranslator.generic.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.binarytranslator.vmInterface.DynamicCodeRunner;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;

public class StagedEmulationController extends ExecutionController {

  private final class Trace {
    public final List<Interpreter.Instruction> instructions;
    public int value;
    public DBT_Trace compiledTrace;
    
    public Trace(List<Interpreter.Instruction> instructions) {
      this.instructions = instructions;
      value = 0;
    }
  }
  
  private final HashMap<Integer, Trace> traceCache = new HashMap<Integer, Trace>();
  private final Interpreter interpreter;
  
  private Trace getTrace(int pc) {
    Trace cachedTrace = traceCache.get(pc);
    
    if (cachedTrace != null)
      return cachedTrace;
    
    int traceStart = pc;
    ArrayList<Interpreter.Instruction> instructions = new ArrayList<Interpreter.Instruction>(5);
    
    while (true)  {
      Interpreter.Instruction instruction = interpreter.decode(pc);
      pc = instruction.getSuccessor(pc);
      instructions.add(instruction);
      
      //is the successor to this instruction known?
      if (pc == -1) {
        //No, so stop and create a trace from the decoded instructions
        Trace newTrace = new Trace(instructions);
        
        if (instructions.size() > 3) {
          //add this trace to the trace cache, if it contains enough instructions
          traceCache.put(traceStart, newTrace);
        }
        
        return newTrace;
      }
    }
  }
  
  private void compileTrace(Trace trace, int pc) {
    if (DBT.VerifyAssertions) DBT._assert(trace.compiledTrace == null);
    
    trace.compiledTrace = new DBT_Trace(ps, pc);
    trace.compiledTrace.compile();
  }
  
  private void executeTrace(Trace trace, int pc) {
    
    //check if the trace is being executed very frequently... 
    if (trace.value > 20) {
      
      if (DBT_Options.debugTranslation)
        System.out.println("Switching to interpretation at address 0x" + Integer.toHexString(pc));
      
      //yes, so we should rather try to execute a translated version
      if (trace.compiledTrace == null) {
        //compile the trace, if necessary
        compileTrace(trace, pc);
        if (DBT.VerifyAssertions) DBT._assert(trace.compiledTrace != null);
      }
      
      //execute the trace
      VM_CodeArray code = trace.compiledTrace.getCurrentCompiledMethod().getEntryCodeArray();
      ps.setCurrentInstructionAddress(DynamicCodeRunner.invokeCode(code, ps));
    }
    else {
      trace.value += trace.instructions.size();
    }
    
    Iterator<Interpreter.Instruction> instructions = trace.instructions.iterator();
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
  
  public StagedEmulationController(ProcessSpace ps) {
    super(ps);
    interpreter = ps.createInterpreter();
  }

  @Override
  public void run() {
    int pc = ps.getCurrentInstructionAddress();

    while (!ps.finished) {
      
      Trace trace = getTrace(pc);
      executeTrace(trace, pc);
      pc = ps.getCurrentInstructionAddress();
    }
  }
}
