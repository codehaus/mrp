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
import org.jikesrvm.scheduler.RVMThread;

@Uninterruptible("No preemption or GC wanted during debug output")
public class DebugEntrypoints {
  /** Debug methods */
  public static final int DUMP_THREAD=0;
  public static final int DUMP_STACK =1;

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
    }
  }
}
