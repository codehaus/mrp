/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.vmInterface;
import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_DynamicBridge;
import com.ibm.jikesrvm.VM_CodeArray;
import com.ibm.jikesrvm.VM_Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.NoInlinePragma;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.fault.BadInstructionException;

/**
 * This class provides the bridge between the Java compiled world and
 * the world compiled using the PPC emulator.  VM_Uninterruptible is
 * used to prevent garbage collection errors with the dynamic bridge
 * code.
 */
public class DynamicCodeRunner implements VM_DynamicBridge, Uninterruptible {
  /**
   * The bridge into the PPC emulator code
   *
   * @param code the code to be executed
   * @param ps the process space the code will work upon
   * @return the code will return the PC value of the next instruction
   */
  public static int invokeCode (VM_CodeArray code, ProcessSpace ps) throws BadInstructionException, NoInlinePragma
  {
    // Useful when debugging in GDB:
    if(DBT_Options.debugRuntime) {
      VM.sysWrite("Running PC=");
      VM.sysWriteHex(ps.getCurrentInstructionAddress());
      VM.sysWriteln();
      VM.sysWriteln(ps.toString());
      //ps.dumpStack(20);
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
 * This class is a hoax used to point our VM_PPC_Trace to. We can't
 * use DynamicCodeRunner as OPT_Compiler refuses to build something
 * that implements VM_DynamicBridge. VM_Uninterruptible is used to
 * prevent garbage collection errors with the dynamic bridge code.
 */
class DummyDynamicCodeRunner implements Uninterruptible {
  /**
   * Offset of ps.doSysCall bytecode
   */
  public static final int ps_doSysCall_offset = 7;
  /**
   * Offset of ps.recordBranch bytecode
   */
  public static final int ps_recordBranch_offset = 13;

  /** Don't won't the FALSE to be executed, but don't want the code to
   * be eliminated either */
  static boolean FALSE = false;
  /**
   * The method replaced by a trace
   */
  public static int invokeCode (VM_CodeArray code, ProcessSpace ps) throws BadInstructionException, NoInlinePragma
  {
    // Fake out calls to PPC_ProcessSpace to forge some bytecode
    // locations
    if(FALSE) {
      ps.doSysCall();                      // bytecode number -- 7
      ps.recordBranch(0,0);                // bytecode number -- 13
      new BadInstructionException(0,null); // bytecode number -- 16 (new) 21 (init)
      ps.memory.store8(0,0);               // bytecode number -- 30
      ps.memory.store16(0,0);              // bytecode number -- 39
      ps.memory.store32(0,0);              // bytecode number -- 48
      ps.memory.loadSigned8(0);            // bytecode number -- 56
      ps.memory.loadUnsigned8(0);          // bytecode number -- 65
      ps.memory.loadSigned16(0);           // bytecode number -- 74
      ps.memory.loadUnsigned16(0);         // bytecode number -- 83
      ps.memory.load32(0);                 // bytecode number -- 92
    }
    throw new Error("This should never be executed");
  }
}
