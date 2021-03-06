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

import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.binarytranslator.vmInterface.DynamicCodeRunner;
import org.jikesrvm.compilers.common.CodeArray;

/**
 * Runtime loop, goes through the binary and looks in a Hashtable 
 * to see if we have already translated/compiled this piece of code, if not it
 * is compiled. The compiled code is then run.
 */
public class DynamicTranslationController extends ExecutionController {

  public DynamicTranslationController(ProcessSpace ps) {
    super(ps);
  }

  @Override
  public void run() {
    // The current block of compiled code.
    CodeArray code;

    try {
      while (ps.finished == false) {
        // Get the compiled code
        code = getCodeForPC(ps.getCurrentInstructionAddress());
        
        // Run the compiled code.
        ps.setCurrentInstructionAddress(DynamicCodeRunner.invokeCode(code, ps));
      }
    } catch (BadInstructionException e) {
      System.out.println(e.toString());
    }
  }  

  /**
   * Returns an array of code starting at the given program counter.
   * @param pc
   *  The program counter at which the code is supposed to start.
   * @return
   *  An executable CodeArray, which contains a trace starting at the given address.
   */
  private CodeArray getCodeForPC(int pc) {
    DBT_Trace trace = ps.codeCache.tryGet(pc);

    if (trace == null) {
      trace = translateCode(pc);
    }
    
    return trace.getCurrentCompiledMethod().getEntryCodeArray();
  }

  /**
   * Translates a piece of code starting at the given program counter value.
   * 
   * @param pc
   *          The memory address of the first instruction that is translated
   *          into the code array.
   * @return An code array containing target system assembly language, which has
   *         been translated from the subject executable.
   */
  private DBT_Trace translateCode(int pc) {

    synchronized (ps) {
      DBT_Trace trace = new DBT_Trace(ps, pc);

      if (DBT_Options.debugRuntime) {
        report("Translating code for 0x" + Integer.toHexString(trace.pc));
      }

      // compile the given trace
      trace.compile();

      // store the compiled code in the code cache
      ps.codeCache.add(pc, trace);
      
      return trace;
    }
  }

  /** Outputs a debug message */
  private void report(String msg) {
    System.out.println("Dynamic Translation Controller: " + msg);
  }
}
