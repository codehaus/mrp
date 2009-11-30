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
import org.jikesrvm.Callbacks.ExitMonitor;
import org.jikesrvm.Callbacks.MethodCompileMonitor;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethod.DebugInformationVisitor;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.VM;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import static org.jikesrvm.runtime.SysCall.sysCall;

/**
 * The purpose of this class is to listen for OProfile events and to inform
 * OProfile about JIT events that have occured.
 */
public final class OProfileListener {
  /** Debug messages */
  private static final boolean DEBUG = true;

  /** Only instance of this class */
  private static OProfileListener singleton;

  /** Handle to OProfile agent */
  private final Address opHandle;

  /** Initialize oprofile's state with methods that exist in the boot image */
  public static void initializeOProfile() {
    // create singleton
    singleton = new OProfileListener();
    // ensure clean exits
    Callbacks.addExitMonitor(
        new ExitMonitor(){
          public void notifyExit(int unused) {
            singleton.closeAgent();
          }
        });
    // describe methods in the boot image to oprofile
    for (int i=1; i < CompiledMethods.numCompiledMethods(); i++) {
      CompiledMethod cm = CompiledMethods.getCompiledMethod(i);
      if (cm != null && cm.isCompiled()) singleton.describeCompiledMethod(cm);
    }
    Callbacks.addMethodCompileMonitor(
      new MethodCompileMonitor() {
        public void notifyMethodCompile(RVMMethod method, int compiler) {
          CompiledMethod cm = method.getCurrentCompiledMethod();
          if (cm != null && cm.isCompiled()) singleton.describeCompiledMethod(cm);
        }
      });
  }

  /** Private constructor */
  private OProfileListener() {
    opHandle = sysCall.sysOProfileOpenAgent();
  }

  /** Terminate OProfile session */
  private void closeAgent() {
    sysCall.sysOProfileCloseAgent(opHandle);
  }

  /**
   * Describe a compiled method to oprofile
   * @param cm compiled method to describe
   */
  private void describeCompiledMethod(CompiledMethod cm) {
    if (DEBUG) VM.sysWriteln("Describing compiled method: "+cm);
    String stringSymbolName = cm.symbolName();
    if (DEBUG) VM.sysWriteln("  symbol name: "+stringSymbolName);
    Atom symbolName =  Atom.findOrCreateUnicodeAtom(stringSymbolName);
    final Address codeAddress = Magic.objectAsAddress(cm.getEntryCodeArray());
    int codeLength = cm.getEntryCodeArray().length();
    sysCall.sysOProfileWriteNativeCode(opHandle, symbolName.toByteArray(), codeAddress, codeLength);

    final class CompileMapVisitor extends DebugInformationVisitor {
      final Address cmap = sysCall.sysOProfileStartCompileMap(opHandle, codeAddress);
      public void visit(Offset offs, Atom fileName, int lineNumber) {
        sysCall.sysOProfileAddToCompileMap(cmap, codeAddress.plus(offs), fileName.toByteArray(), lineNumber);
      }
      void finish() {
        sysCall.sysOProfileFinishCompileMap(cmap);
      }
    }
    CompileMapVisitor visitor = new CompileMapVisitor();
    cm.walkDebugInformation(visitor);
    visitor.finish();
  }
}
