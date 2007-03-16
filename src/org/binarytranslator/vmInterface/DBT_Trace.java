/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.vmInterface;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.jikesrvm.classloader.VM_BootstrapClassLoader;
import org.jikesrvm.VM_CompiledMethod;
import org.jikesrvm.VM_CompiledMethods;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.VM_Statics;
import org.jikesrvm.opt.ir.OPT_GenerationContext;
import org.jikesrvm.opt.ir.OPT_HIRGenerator;

/**
 * A method class which can be used in place of a VM_NormalMethod but
 * which includes a block of PPC machine code, since this is our
 * starting point for the PPC emulator, rather than Java byte
 * codes.
 */
public final class DBT_Trace extends VM_NormalMethod
{
  /*
   * Unique features of a trace
   */

  /**
   * The ProcessSpace within which we are running.
   */
  public ProcessSpace ps;

  /**
   * Local copy of the program counter. In future developments
   * (parallel compilation) this might not be the same as ps.pc.
   */
  public int pc; 

  /**
   * Create an optimizing compiler HIR code generator for this trace
   * @param context the generation context for the HIR generation
   * @return a HIR generator
   */
  public OPT_HIRGenerator createHIRGenerator(OPT_GenerationContext context){
    return ps.createHIRGenerator(context);
  }

  /**
   * Must this method be OPT compiled?
   * @param context the generation context for the HIR generation
   * @return a HIR generator
   */
  public boolean optCompileOnly() {
    return true;
  }

  /**
   * Traces appear as DynamicCodeRunner.invokeCode methods
   */
  private static VM_Class dummyRunner;
  private static VM_NormalMethod invokeCode;
  private static int invokeCode_modifiers;
  private static VM_TypeReference dummyRunnerTypeRef;
  private static VM_MemberReference dummyRunnerMemRef;
  private static VM_Atom invokeCodeDescriptor;

  static {
    configure();
  }

  /**
   * PPC traces masquerade as the method used to invoke them. Generate
   * necessary references to it.
   */
  public static void configure() {
    VM_Atom clsDescriptor = VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/vmInterface/DummyDynamicCodeRunner;");
    VM_Atom memName       = VM_Atom.findOrCreateAsciiAtom("invokeCode");
    invokeCodeDescriptor  = VM_Atom.findOrCreateAsciiAtom("(Lorg/jikesrvm/VM_CodeArray;Lorg/binarytranslator/generic/os/process/ProcessSpace;)I");
    VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                          clsDescriptor);
    dummyRunnerTypeRef = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                       clsDescriptor);
    dummyRunnerMemRef = VM_MemberReference.findOrCreate(dummyRunnerTypeRef, memName, invokeCodeDescriptor);
    if (dummyRunnerMemRef != null) {
      dummyRunner = (VM_Class)tRef.resolve();
      if (dummyRunner != null) {
        dummyRunner.resolve();
        invokeCode = (VM_NormalMethod)dummyRunner.findDeclaredMethod(memName, invokeCodeDescriptor);
        if (invokeCode == null) {
          throw new Error("Failed to find method " + memName + invokeCodeDescriptor + " in " + dummyRunner);
        }
        invokeCode_modifiers = invokeCode.modifiers;
      }
      else {
        throw new Error("Failed to resolve " + tRef);
      }
    }
    else {
      throw new Error("Failed to find " + memName);
    }
  }

  /**
   * Constructor
   *
   * @param ps process space containing code
   * @param startPC the address of the first instruction
   */
  public DBT_Trace(ProcessSpace ps, int startPC) {
    super(dummyRunnerTypeRef, 
          VM_MemberReference.findOrCreate(dummyRunnerTypeRef,
                                          VM_Atom.findOrCreateAsciiAtom("invokeCode" + "_PC_0x" + Integer.toHexString(startPC)),
                                          invokeCodeDescriptor),
          invokeCode_modifiers,
          invokeCode.getExceptionTypes(), 
          invokeCode.getLocalWords(),
          invokeCode.getOperandWords(),
          invokeCode.bytecodes,
          invokeCode.getExceptionHandlerMap(),
          new int[0], null, null, null, null, null, null, null);

    this.offset = VM_Statics.allocateSlot(VM_Statics.METHOD) << LOG_BYTES_IN_INT;

    this.summary |= HAS_ALLOCATION | HAS_THROW | HAS_INVOKE |
      HAS_FIELD_READ | HAS_FIELD_WRITE | HAS_ARRAY_READ | HAS_ARRAY_WRITE |
      HAS_COND_BRANCH | HAS_SWITCH | HAS_BACK_BRANCH |
      256;

    this.ps = ps;
    pc = startPC;

  }

  /**
   * Change machine code that will be used by future executions of this method 
   * (ie. optimized <-> non-optimized)
   * @param compiledMethod new machine code
   * Side effect: updates jtoc or method dispatch tables 
   * ("type information blocks")
   *              for this class and its subclasses
   */ 
  public final synchronized void replaceCompiledMethod(VM_CompiledMethod compiledMethod) {
    ps.replaceCompiledTrace(compiledMethod, this);

    // Grab version that is being replaced
    VM_CompiledMethod oldCompiledMethod = currentCompiledMethod;
    currentCompiledMethod = compiledMethod;

    // Now that we've updated the translation cache, old version is obsolete
    if (oldCompiledMethod != null) {
      VM_CompiledMethods.setCompiledMethodObsolete(oldCompiledMethod);
    }
  }
}
