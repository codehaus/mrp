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

import org.jikesrvm.classloader.Atom;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.CompiledMethod.DebugInformationVisitor;
import org.jikesrvm.runtime.Callbacks;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Callbacks.Callback;
import org.jikesrvm.util.StringUtilities;
import org.jikesrvm.VM;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import static org.jikesrvm.runtime.SysCall.sysCall;

/**
 * The purpose of this class is to listen for OProfile events and to inform
 * OProfile about JIT events that have occurred.
 */
public final class OProfileListener {
  /** Debug messages */
  private static final boolean DEBUG = false;

  /** Profile boot image compiled methods, can cause serious load on opannotate */
  private static final boolean profileBootImage = false;

  /** Only instance of this class */
  private static OProfileListener singleton;

  /** Handle to OProfile agent */
  private final Address opHandle;

  /** Initialize oprofile's state with methods that exist in the boot image */
  public static void initializeOProfile() {
    // create singleton
    singleton = new OProfileListener();
    // ensure clean exits
    Callbacks.vmExitCallbacks.addCallback(
      new Callback(){
        public void notify(Object... args) {
          singleton.closeAgent();
        }
      });
    // describe methods in the boot image to oprofile
    for (int i=1; i < CompiledMethods.numCompiledMethods(); i++) {
      CompiledMethod cm = CompiledMethods.getCompiledMethod(i);
      if (cm != null && cm.isCompiled()) singleton.describeCompiledMethod(cm);
    }

    Callbacks.methodCompileCompleteCallbacks.addCallback(
      new Callback() {
        public void notify(Object... args) {
          CompiledMethod cm = (CompiledMethod)args[0];
          if (cm != null && cm.isCompiled()) singleton.describeCompiledMethod(cm);
        }
      });
    Callbacks.methodCompileObsoleteCallbacks.addCallback(
      new Callback() {
        public void notify(Object... args) {
          CompiledMethod cm = (CompiledMethod)args[0];
          if (cm != null && cm.isCompiled()) singleton.removeCompiledMethod(cm);
        }
      });
  }

  /** Private constructor */
  private OProfileListener() {
    opHandle = sysCall.sysOProfileOpenAgent();
    if (opHandle.isZero()) {
      throw new Error("Error opening oprofile agent");
    }
  }

  /** Terminate OProfile session */
  private void closeAgent() {
    sysCall.sysOProfileCloseAgent(opHandle);
  }

  /**
   * Describe a compiled method to OProfile
   * @param cm compiled method to describe
   */
  private void describeCompiledMethod(CompiledMethod cm) {
    if (DEBUG) VM.sysWriteln("Describing compiled method: "+cm);
    String stringSymbolName = cm.symbolName();
    if (DEBUG) VM.sysWriteln("  symbol name: "+stringSymbolName);
    final byte[] symbolName =  StringUtilities.stringToBytesNullTerminated(stringSymbolName);
    final Address codeAddress = Magic.objectAsAddress(cm.getEntryCodeArray());
    int codeLength = cm.getEntryCodeArray().length();
    synchronized(this) {
      sysCall.sysOProfileWriteNativeCode(opHandle, symbolName, codeAddress, codeLength);

      final class CompileMapVisitor extends DebugInformationVisitor {
        final Address cmap = sysCall.sysOProfileStartCompileMap(opHandle, codeAddress);
        public void visit(Offset offs, Atom fileName, int lineNumber) {
          if (VM.VerifyAssertions) VM._assert(!cmap.isZero());
          if(fileName != null && lineNumber > 0) {
            byte[] fileNameBA;
            fileNameBA = StringUtilities.stringToBytesNullTerminated(fileName.toString());
            sysCall.sysOProfileAddToCompileMap(cmap, codeAddress.plus(offs), fileNameBA, lineNumber);
          }
        }
        void finish() {
          sysCall.sysOProfileFinishCompileMap(cmap);
        }
      }
      if(profileBootImage || !cm.getMethod().getDeclaringClass().isInBootImage()) {
        CompileMapVisitor visitor = new CompileMapVisitor();
        cm.walkDebugInformation(visitor);
        visitor.finish();
      }
    }
  }

  /**
   * Remove a compiled method from OProfile
   * @param cm compiled method to remove
   */
  private void removeCompiledMethod(CompiledMethod cm) {
    if (DEBUG) VM.sysWriteln("Removing compiled method: "+cm);
    final Address codeAddress = Magic.objectAsAddress(cm.getEntryCodeArray());
    sysCall.sysOProfileUnloadNativeCode(opHandle, codeAddress);
  }
}
