/*
 *  This file is part of the Metacircular Research Platform (MRP)
 *
 *      http://mrp.codehaus.org/
 *
 *  This file is licensed to you under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the license at:
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package mrp.debug;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.jikesrvm.VM;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.CompiledMethod;

@Uninterruptible("No preemption or GC wanted during debug output")
public class DebugEntrypoints {
  /** Debug methods */
  public static final int DUMP_THREAD =0;
  public static final int DUMP_STACK  =1;
  public static final int DUMP_METHOD =2;

  /** Method to call */
  public static int debugMethod;

  /** Argument to debug method */
  public static Address debugArgs;

  /** Called when debug generation is requested */
  public static void debugEntry() {
    switch (debugMethod) {
    case DUMP_THREAD: {
      RVMThread t = (RVMThread)debugArgs.toObjectReference().toObject();
      t.dump();
      break;
    }
    case DUMP_STACK: {
      RVMThread t = (RVMThread)debugArgs.toObjectReference().toObject();
      RVMThread.dumpStack(t.framePointer);
      break;
    }
    case DUMP_METHOD: {
      int cmid = debugArgs.toInt();
      CompiledMethod cm;
      if(cmid < CompiledMethods.numCompiledMethods()) {
        VM.sysWriteln("Looking up compiled method from CMID: ", cmid);
        cm = CompiledMethods.getCompiledMethod(cmid);
      } else {
        VM.sysWriteln("Looking for compiled method associated with IP: ", debugArgs);
        cm = CompiledMethods.findMethodForInstruction(debugArgs);
        cmid = (cm == null) ? 0 : cm.getId();
      }
      if(cm == null) {
        VM.sysWriteln("Method not found");
      } else {
        VM.sysWrite(cm.getMethod().getDeclaringClass().getDescriptor());
        VM.sysWrite(cm.getMethod().getName());
        VM.sysWriteln(cm.getMethod().getDescriptor());
        VM.sysWriteln("CMID: ",cmid);
        Address code = (ObjectReference.fromObject(cm.getEntryCodeArray())).toAddress();
        VM.sysWriteln("Start of code: ",code);
        VM.sysWriteln("Compiler type: ",CompiledMethod.compilerTypeToString(cm.getCompilerType()));
      }
      break;
    }
    }
  }
}

