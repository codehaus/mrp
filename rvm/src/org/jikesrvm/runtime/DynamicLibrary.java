/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
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
package org.jikesrvm.runtime;

import java.util.Iterator;
import org.jikesrvm.ArchitectureSpecific.StackframeLayoutConstants;
import org.jikesrvm.VM;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;
import org.jikesrvm.util.StringUtilities;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Interface to the dynamic libraries of our underlying operating system.
 */
public final class DynamicLibrary {

  /**
   * Currently loaded dynamic libraries.
   */
  private static final ImmutableEntryHashMapRVM<String, DynamicLibrary> dynamicLibraries =
      new ImmutableEntryHashMapRVM<String, DynamicLibrary>();

  /**
   * Add symbol for the boot image runner to find symbols within it.
   */
  public static void boot() {
    System.loadLibrary("jvm_jni");
  }

  /**
   * The name of the library
   */
  private final String libName;

  /**
   * Value returned from dlopen
   */
  private final Address libHandler;

  /**
   * Address of JNI_OnLoad method
   */
  private final Address jniOnLoad;

  /**
   * Address of JNI_OnUnLoad
   */
  private final Address jniOnUnload;

  /**
   * Maintain a loaded library, call it's JNI_OnLoad function if present
   * @param libName library name
   * @param libHandler handle of loaded library
   */
  private DynamicLibrary(String libName, Address libHandler) {
    this.libName = libName;
    this.libHandler = libHandler;
    jniOnLoad = getJNI_OnLoad();
    jniOnUnload = getJNI_OnUnload();
    try {
      callOnLoad();
    } catch (UnsatisfiedLinkError e) {
      unload();
      throw e;
    }

    if (VM.verboseJNI) {
      VM.sysWriteln("[Loaded native library: " + libName + "]");
    }
  }

  /**
   * Get the unique JNI_OnLoad symbol associated with this library
   * @return JNI_OnLoad address or zero if not present
   */
  private Address getJNI_OnLoad() {
    Address candidate = getSymbol("JNI_OnLoad", "IPP");
    Iterator<DynamicLibrary> libs = dynamicLibraries.valueIterator();
    while(libs.hasNext()) {
      DynamicLibrary lib = libs.next();
      if (lib.jniOnLoad == candidate) {
        return Address.zero();
      }
    }
    return candidate;
  }

  /**
   * Get the unique JNI_OnUnload symbol associated with this library
   * @return JNI_OnUnload address or zero if not present
   */
  private Address getJNI_OnUnload() {
    Address candidate = getSymbol("JNI_OnUnload", "IPP");
    Iterator<DynamicLibrary> libs = dynamicLibraries.valueIterator();
    while(libs.hasNext()) {
      DynamicLibrary lib = libs.next();
      if (lib.jniOnUnload == candidate) {
        return Address.zero();
      }
    }
    return candidate;
  }

  /**
   * Called after we've successfully loaded the shared library
   */
  private void callOnLoad() {
    // Run any JNI_OnLoad functions defined within the library
    if (!jniOnLoad.isZero()) {
      int version = runJNI_OnLoad(jniOnLoad);
      checkJNIVersion(version);
    }
  }

  /**
   * Method call to run the onload method. Performed as a native
   * method as the JNI_OnLoad method may contain JNI calls and we need
   * the RVMThread of the JNIEnv to be correctly populated (this
   * wouldn't happen with a SysCall)
   *
   * @param JNI_OnLoadAddress address of JNI_OnLoad function
   * @return the JNI version returned by the JNI_OnLoad function
   */
  private static native int runJNI_OnLoad(Address JNI_OnLoadAddress);

  /**
   * Check JNI version is &ge; 1 and &le; 1.4 and if not throw an
   * UnsatisfiedLinkError
   * @param version to check
   */
  private static void checkJNIVersion(int version) {
    int major = version >>> 16;
    int minor = version & 0xFFFF;
    if (major != 1 || minor > 4) {
      throw new UnsatisfiedLinkError("Unsupported JNI version: " + major + "." + minor);
    }
  }

  /**
   * @return the true name of the dynamic library
   */
  public String getLibName() { return libName; }

  /**
   * Look up a symbol in this dynamic library
   *
   * @param symbolName symbol name
   * @param argSignature optional encoding of argument signature (used
   * by Harmony in Windows). The first character encodes the result,
   * subsequent characters encode the arguments where the characters
   * used are the regular Java descriptor characters with the addition
   * of P for a pointer type.
   * @return The <code>Address</code> of the symbol or an AixLinkage
   * triplet.  -1 if not found or error.
   */
  private Address getSymbol(String symbolName, String argSignature) {
    // Convert file name from unicode to filesystem character set
    // (assume file name is ascii, for now).
    //
    final byte[] asciiName = StringUtilities.stringToBytesNullTerminated(symbolName);
    final byte[] asciiArgs = (argSignature != null) ? StringUtilities.stringToBytesNullTerminated(argSignature) : null;
    return SysCall.sysCall.sysDlsym(libHandler, asciiName, asciiArgs);
  }

  /**
   * unload a dynamic library
   */
  public void unload() {
    VM.sysWrite("DynamicLibrary.unload: not implemented yet \n");
  }

  /**
   * Tell the operating system to remove the dynamic library from the
   * system space.
   */
  public void clean() {
    VM.sysWrite("DynamicLibrary.clean: not implemented yet \n");
  }

  public String toString() {
    return "dynamic library " + libName + ", handler=0x" + Long.toHexString(libHandler.toWord().toLong());
  }

  /**
   * Load a dynamic library
   * @param libname the name of the library to load.
   * @return 0 on failure, 1 on success
   */
  public static synchronized int load(String libname) {
    DynamicLibrary dl = dynamicLibraries.get(libname);
    if (dl != null) {
      return 1; // success: already loaded
    } else {
      // Convert file name from unicode to filesystem character set.
      // (Assume file name is ASCII, for now).
      byte[] asciiName = StringUtilities.stringToBytesNullTerminated(libname);

      if (!VM.AutomaticStackGrowth) {
        // make sure we have enough stack to load the library.
        // This operation has been known to require more than 20K of stack.
        RVMThread myThread = RVMThread.getCurrentThread();
        Offset remaining = Magic.getFramePointer().diff(myThread.stackLimit);
        int stackNeededInBytes = StackframeLayoutConstants.STACK_SIZE_DLOPEN - remaining.toInt();
        if (stackNeededInBytes > 0) {
          if (myThread.hasNativeStackFrame()) {
            throw new java.lang.StackOverflowError("Not enough space to open shared library");
          } else {
            RVMThread.resizeCurrentStack(myThread.getStackLength() + stackNeededInBytes, null);
          }
        }
      }

      Address libHandler = SysCall.sysCall.sysDlopen(asciiName);

      if (!libHandler.isZero()) {
        dynamicLibraries.put(libname, new DynamicLibrary(libname, libHandler));
        return 1;
      } else {
        return 0; // fail; file does not exist
      }
    }
  }

  /**
   * Resolve a symbol to an address in a currently loaded dynamic library.
   * @param symbol the symbol to resolve
   * @param argSignature optional encoding of argument signature
   *        (used by Harmony in Windows)
   * @return the address of the symbol of Address.zero() if it cannot be resolved
   */
  public static synchronized Address resolveSymbol(String symbol, String argSignature) {
    for (DynamicLibrary lib : dynamicLibraries.values()) {
      Address symbolAddress = lib.getSymbol(symbol, argSignature);
      if (!symbolAddress.isZero()) {
        return symbolAddress;
      }
    }
    return Address.zero();
  }
}
