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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.binarytranslator.vmInterface.DynamicCodeRunner;
import org.jikesrvm.compilers.common.CodeArray;

/**
 * This controller implements staged emulation, i.e. switching between interpretation
 * and translation dynamically.
 *
 */
public class StagedEmulationController extends ExecutionController {

  /** Represents a dynamic basic block of instructions. */
  private final class DynamicBasicBlock {
    /** The instructions within this dynamic basic block. */
    public List<Interpreter.Instruction> instructions;
    
    /** A value describing how "hot" the basic block is, i.e. how often it has been executed.*/
    public int value;
    
    /** A handle to the compiled version of this dynamic basic block or null, if there is none. */
    public DBT_Trace compiledTrace;
    
    public DynamicBasicBlock(List<Interpreter.Instruction> instructions) {
      this.instructions = instructions;
      value = 0;
    }
  }
  
  /** Maps a dynamic basic block to the address of the first instruction within that block. */
  private final TreeMap<Integer, DynamicBasicBlock> traceCache = new TreeMap<Integer, DynamicBasicBlock>();
  
  /** The interpreter that is used to perform the actual execution of single instructions. */
  private final Interpreter interpreter;
  
  /**
   * Returns the dynamic basic block starting at address <code>pc</code>.
   * 
   * @param pc
   *  The starting address of a dynamic basic block.
   * @return
   *  An object representation of the dynamic basic block.
   */
  private DynamicBasicBlock getBlock(int pc) {
    DynamicBasicBlock cachedTrace = traceCache.get(pc);
    
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
        DynamicBasicBlock newTrace = new DynamicBasicBlock(instructions);
        
//      add this trace to the trace cache
        traceCache.put(traceStart, newTrace);
        
        /*if (instructions.size() > 3) {
          //add this trace to the trace cache, if it contains enough instructions
          traceCache.put(traceStart, newTrace);
        }*/
        
        return newTrace;
      }
    }
  }
  
  /**
   * Compiles a dynamic basic block into a trace.
   * 
   * @param trace
   *  The dynamic basic block to compile.
   * @param pc
   *  The address of the first instruction within the dynamic basic block.
   */
  private void compileBlock(DynamicBasicBlock trace, int pc) {
    if (DBT.VerifyAssertions) DBT._assert(trace.compiledTrace == null);
    
    trace.compiledTrace = new DBT_Trace(ps, pc);
    trace.compiledTrace.compile();
    trace.instructions = null;
    
    ps.codeCache.add(pc, trace.compiledTrace);
  }
  
  /**
   * Executes the instructions within a dynamic basic block.
   * 
   * @param trace
   *  The dynamic basic block whose instructions shall be executed.
   * @param pc
   *  The address of the first instruction within that dynamic basic block.
   */
  private void executeBlock(DynamicBasicBlock trace, int pc) {
    
    //check if the trace is being executed very frequently... 
    if (trace.value > DBT_Options.minTraceValue) {
      
      if (DBT_Options.debugTranslation)
        System.out.println("Switching to interpretation at address 0x" + Integer.toHexString(pc));
      
      //yes, so we should rather try to execute a translated version
      if (trace.compiledTrace == null) {
        //compile the trace, if necessary
        compileBlock(trace, pc);
        if (DBT.VerifyAssertions) DBT._assert(trace.compiledTrace != null);
      }
      
      //execute the trace
      CodeArray code = trace.compiledTrace.getCurrentCompiledMethod().getEntryCodeArray();
      ps.setCurrentInstructionAddress(DynamicCodeRunner.invokeCode(code, ps));
      
      if (DBT_Options.debugTranslation)
        System.out.println("Returning from interpretation at 0x" + Integer.toHexString(ps.getCurrentInstructionAddress()));
      
      return;
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
  
  /** Default constructor */
  public StagedEmulationController(ProcessSpace ps) {
    super(ps);
    interpreter = ps.createInterpreter();
  }

  @Override
  public void run() {
    int pc = ps.getCurrentInstructionAddress();

    while (!ps.finished) {
      
      DynamicBasicBlock trace = getBlock(pc);
      executeBlock(trace, pc);
      pc = ps.getCurrentInstructionAddress();
    }
  }
}
