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

import org.jikesrvm.Callbacks;
import org.jikesrvm.Callbacks.MethodCompileMonitor;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Magic;

import org.vmmagic.unboxed.Address;

/**
 * The purpose of this class is to listen for OProfile events and to inform
 * OProfile about JIT events that have occured.
 */
public class OProfileListener {
  /** Debug messages */
  private static final boolean DEBUG = true;

  private static OProfileListener singleton;
  
  /** Handle to OProfile agent */
  private final Address opHandle;

  /** Initialize oprofile's state with methods that exist in the boot image */
  public static void initializeOProfile() {
    // create singleton
    singleton = new OProfileListener();
    // describe methods in the boot image to oprofile
    for (int i=0; i < CompiledMethods.numCompiledMethods(); i++) {
      CompiledMethod cm = CompiledMethods.getCompiledMethod(i);
      describeCompiledMethod(cm);
    }
    Callbacks.addMethodCompileMonitor(
      new MethodCompileMonitor() {
        void notifyMethodCompile(RVMMethod method, int compiler) {
          
        }
      });
    }

  }

  /** Private constructor */
  private OProfileListener() {
    opHandle = sysOProfileOpenAgent();
  }

  /**
   * Describe a compiled method to oprofile
   * @param cm compiled method to describe
   */
  private void describeCompiledMethod(CompiledMethod cm) {
    String stringSymbolName = cm.method.getDeclaringClass().toString() + cm.method.getName().toString();
    Atom symbolName =  Atom.findOrCreateUnicodeAtom(stringSymbolName);
    Address codeAddress = Magic.objectAsAddress(cm.getEntryCodeArray());
    int codeLength = cm.getEntryCodeArray().length();
    sysOProfileWriteNativeCode(opHandle, symbolName, codeAddress, codeLength);
  }
}
