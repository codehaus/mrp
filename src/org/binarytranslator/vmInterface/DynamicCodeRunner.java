/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.vmInterface;

import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.pragma.DynamicBridge;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.NoInline;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.fault.BadInstructionException;

/**
 * This class provides the bridge between the Java compiled world and the world
 * compiled using the PPC emulator. Uninterruptible is used to prevent garbage
 * collection errors with the dynamic bridge code.
 */
//@Uninterruptible
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
  public static int invokeCode(VM_CodeArray code, ProcessSpace ps)
      throws BadInstructionException {
    // Useful when debugging in GDB:
    if (DBT_Options.debugRuntime) {
      VM.sysWriteln(ps.toString());
      //ps.dumpStack(20);
      VM.sysWrite("Running PC=");
      VM.sysWriteHex(ps.getCurrentInstructionAddress());
      VM.sysWrite(" ");
      VM.sysWriteln(ps.disassembleInstruction(ps.getCurrentInstructionAddress()));
      VM.sysWrite("About to bridge to ");
      VM.sysWriteHex(VM_Magic.objectAsAddress(code).toInt());
      VM.sysWriteln();
    }

    VM_Magic.dynamicBridgeTo(code);
    // Never get here, dynamicBridgeTo returns to the thing that calls
    // this method; the return value is provided by a suitable
    // instruction in the VM_CodeArray.

    // New pc value. Just to keep the compiler happy.
    return 0xEBADC0DE;
  }
}

/**
 * This class is a hoax used to point our VM_PPC_Trace to. We can't use
 * DynamicCodeRunner as OPT_Compiler refuses to build something that implements
 * VM_DynamicBridge. Uninterruptible is used to prevent garbage collection
 * errors with the dynamic bridge code.
 */
@Uninterruptible
class DummyDynamicCodeRunner {
  /**
   * The method replaced by a trace
   */
  @NoInline
  public static int invokeCode(VM_CodeArray code, ProcessSpace ps)
      throws BadInstructionException {
    throw new Error("This should never be executed");
  }
}
