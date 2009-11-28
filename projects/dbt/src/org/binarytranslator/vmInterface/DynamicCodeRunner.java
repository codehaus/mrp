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
package org.binarytranslator.vmInterface;

import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.DynamicBridge;
import org.vmmagic.pragma.NoInline;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.fault.BadInstructionException;

/**
 * This class provides the bridge between the Java compiled world and the world
 * compiled using the PPC emulator.
 */
@DynamicBridge
public class DynamicCodeRunner {
  /**
   * The bridge into the PPC emulator code
   * 
   * @param code
   *          the code to be executed
   * @param ps
   *          the process space the code will work upon
   * @return the code will return the PC value of the next instruction
   */
  @NoInline
  public static int invokeCode(CodeArray code, ProcessSpace ps)
      throws BadInstructionException {
    // Useful when debugging in GDB:
    if (DBT_Options.debugRuntime) {
      VM.sysWriteln(ps.toString());
      //ps.dumpStack(20);
      VM.sysWrite("Running PC=");
      VM.sysWriteHex(ps.getCurrentInstructionAddress());
      VM.sysWrite(" ");
      VM.sysWriteln(ps.disassembleInstruction(ps.getCurrentInstructionAddress()));
      //VM.sysWrite("About to bridge to ");
      //VM.sysWriteHex(Magic.objectAsAddress(code).toInt());
      //VM.sysWriteln();
    }

    Magic.dynamicBridgeTo(code);
    // Never get here, dynamicBridgeTo returns to the thing that calls
    // this method; the return value is provided by a suitable
    // instruction in the CodeArray.

    // New pc value. Just to keep the compiler happy.
    return 0xEBADC0DE;
  }
}

/**
 * This class is a hoax used to point our DBT_Trace to. We can't use
 * DynamicCodeRunner as the opt compiler refuses to build something
 * that implements DynamicBridge.
 */
class DummyDynamicCodeRunner {
  /**
   * The method replaced by a trace
   */
  @NoInline
  public static int invokeCode(CodeArray code, ProcessSpace ps)
      throws BadInstructionException {
    throw new Error("This should never be executed");
  }
}
