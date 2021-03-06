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

import java.util.Arrays;

import org.binarytranslator.generic.decoder.CodeTranslator;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DummyDynamicCodeRunner;
import org.binarytranslator.DBT;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.BytecodeStream;
import org.jikesrvm.classloader.TypeReference;
import static org.jikesrvm.classloader.BytecodeConstants.*;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.runtime.DynamicLink;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.ir.HIRGenerator;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A method class which can be used in place of a NormalMethod but which
 * includes a block of PPC machine code, since this is our starting point for
 * the PPC emulator, rather than Java byte codes.
 */
public final class DBT_Trace extends NormalMethod {
  /*
   * Fake bytecode indexes (FB1) for dynamic linking
   */

  /**
   * Fake bytecode index for performing a system call (ProcessSpace.doSysCall)
   */
  public static final int DO_SYSCALL = 0xFB11;

  /**
   * Fake bytecode index for performing a record branch
   * (ProcessSpace.recordBranch)
   */
  public static final int RECORD_BRANCH = 0xFB12;

  /**
   * Fake bytecode index for creating a bad instruction exception (new
   * BadInstruction)
   */
  public static final int BAD_INSTRUCTION_NEW = 0xFB13;

  /**
   * Fake bytecode index for initializing a bad instruction exception
   * (BadInstruction.<init>)
   */
  public static final int BAD_INSTRUCTION_INIT = 0xFB14;

  /**
   * Fake bytecode index for throwing a bad instruction exception (throw
   * BadInstruction)
   */
  public static final int BAD_INSTRUCTION_THROW = 0xFB15;

  public static final int MEMORY_STORE8 = 0xFB16;

  public static final int MEMORY_STORE16 = 0xFB17;

  public static final int MEMORY_STORE32 = 0xFB18;

  public static final int MEMORY_LOAD8 = 0xFB19;

  public static final int MEMORY_LOAD16 = 0xFB1A;

  public static final int MEMORY_LOAD32 = 0xFB1B;

  public static final int MEMORY_ULOAD8 = 0xFB1C;

  public static final int MEMORY_ULOAD16 = 0xFB1D;
  
  /** Bytecode index starting from which custom call bytecode indexes will be distributed.*/
  public static final int CUSTOM_CALL_BCINDEX_BASE = 0x8000; 

  /** The ProcessSpace within which we are running. */
  public final ProcessSpace ps;

  /**
   * Local copy of the program counter. In future developments (parallel
   * compilation) this might not be the same as ps.pc.
   */
  public final int pc;
  
  /** The number of guest instructions that have been compiled into this trace. */
  private int numberOfInstructions;
  
  /** 
   * In order to allow arbitrary calls within a trace, we have to store at which bytecode index
   * a method is called in which way. This class stores the necessary information. */
  private static class CustomCallInformation {
    public final MethodReference methodRef;
    public final int callType;
    
    public CustomCallInformation(MethodReference methodRef, int callType) {
      this.methodRef = methodRef;
      this.callType = callType;
    }
  }
  
  /** 
   * This list stores at which bytecode index a specific method call is executed. 
   * The index of an element plus {@link #CUSTOM_CALL_BCINDEX_BASE} equals the bytecode index
   * for the call.*/
  private CustomCallInformation[] customCalls;

  /**
   * Create an optimizing compiler HIR code generator for this trace
   * 
   * @param context
   *          the generation context for the HIR generation
   * @return a HIR generator
   */
  @Override
  public HIRGenerator createHIRGenerator(GenerationContext context) {
    
    return ps.createTranslator(context, this);
  }

  /**
   * Must this method be OPT compiled?
   * 
   * @param context
   *          the generation context for the HIR generation
   * @return a HIR generator
   */
  public boolean optCompileOnly() {
    return true;
  }

  /**
   * Traces appear as DynamicCodeRunner.invokeCode methods
   */
  private static final RVMClass dummyRunner;

  private static final NormalMethod invokeCode;

  private static final Atom invokeCodeDescriptor;

  static {
    dummyRunner = TypeReference.findOrCreate(DummyDynamicCodeRunner.class).resolve().asClass();

    Atom memName = Atom.findOrCreateAsciiAtom("invokeCode");
    invokeCodeDescriptor = Atom
        .findOrCreateAsciiAtom("(Lorg/jikesrvm/compilers/common/CodeArray;Lorg/binarytranslator/generic/os/process/ProcessSpace;)I");
    invokeCode = (NormalMethod) dummyRunner.findDeclaredMethod(memName,
        invokeCodeDescriptor);
    if (invokeCode == null) {
      throw new Error("Failed to find method " + memName + invokeCodeDescriptor
          + " in " + dummyRunner);
    }
    // force CustomCallInformation to be linked
    new CustomCallInformation(null, 0);
  }

  /** Only create a single zero length int array object */
  private static final int[] zeroLengthIntArray = new int[0];

  private static MethodReference getMethodReference(int startPC) {
    MethodReference m = MemberReference.findOrCreate(dummyRunner.getTypeRef(),
						     Atom.findOrCreateAsciiAtom("invokeCode"+"_PC_0x"+Integer.toHexString(startPC)),
						     invokeCodeDescriptor).asMethodReference();
    return m;
  }

  /**
   * Constructor
   * 
   * @param ps
   *          process space containing code
   * @param startPC
   *          the address of the first instruction
   */
  public DBT_Trace(ProcessSpace ps, int startPC) {
    super(dummyRunner.getTypeRef(),
	  getMethodReference(startPC),
          (short)invokeCode.getModifiers(),
          invokeCode.getExceptionTypes(),
          (short) invokeCode.getLocalWords(),
          (short) invokeCode.getOperandWords(),
          null, invokeCode.getExceptionHandlerMap(),
          zeroLengthIntArray, // lm
          null, // local variable table
          null, // constant pool
          null, // signature
          null, // annotations
          null, // parameter annotations
          null // annotation default
    );

    this.offset = Statics.allocateReferenceSlot(false).toInt();

    this.ps = ps;
    pc = startPC;
  }

  protected void computeSummary(int[] constantPool) {
    this.summaryFlags |= HAS_ALLOCATION | HAS_THROW | HAS_INVOKE
        | HAS_FIELD_READ | HAS_FIELD_WRITE | HAS_ARRAY_READ | HAS_ARRAY_WRITE
        | HAS_COND_BRANCH | HAS_SWITCH | HAS_BACK_BRANCH;
    this.summarySize = 256;
  }

  /**
   * Change machine code that will be used by future executions of this method
   * (ie. optimized <-> non-optimized)
   * 
   * @param compiledMethod
   *          new machine code Side effect: updates jtoc or method dispatch
   *          tables ("type information blocks") for this class and its
   *          subclasses
   */
  public final synchronized void replaceCompiledMethod(
      CompiledMethod compiledMethod) {

    // Grab version that is being replaced
    CompiledMethod oldCompiledMethod = currentCompiledMethod;
    currentCompiledMethod = compiledMethod;

    // Now that we've updated the translation cache, old version is obsolete
    if (oldCompiledMethod != null) {
      CompiledMethods.setCompiledMethodObsolete(oldCompiledMethod);
    }
  }

  /**
   * Map bytecode index to java source line number
   */
  @Uninterruptible
  public int getLineNumberForBCIndex(int bci) {
    return bci;
  }
  
  /**
   * Register that the function at bytecode index <code>bcIndex</code> calls the method <code>methodRef</code>
   * with a type of <code>callType</code>.
   * @param bcIndex
   *  The bytecode index of the call instruction.
   * @param methodRef
   *  A reference to the called method.
   * @param callType
   *  The type of the call. This must be one of JBC_invoke*.
   * @return
   *  The bytecode index for this call
   */
  public int registerDynamicLink(MethodReference methodRef, int callType) {
    if (DBT.VerifyAssertions) 
      DBT._assert(callType == JBC_invokeinterface || callType == JBC_invokespecial || 
                  callType == JBC_invokestatic ||  callType == JBC_invokevirtual);
    
    int nextBcIndex;
    if (customCalls != null) {
      nextBcIndex = customCalls.length;
      customCalls = Arrays.copyOf(customCalls, customCalls.length+1);
    } else {
      customCalls = new CustomCallInformation[1];
      nextBcIndex = 0;
    }

    CustomCallInformation mapping = new CustomCallInformation(methodRef, callType);
    customCalls[nextBcIndex] = mapping;
    
    return CUSTOM_CALL_BCINDEX_BASE + nextBcIndex;
  }

  /**
   * Fill in DynamicLink object for the invoke at the given bytecode index
   * 
   * @param dynamicLink
   *          the dynamicLink object to initialize
   * @param bcIndex
   *          the bcIndex of the invoke instruction
   */
  @Uninterruptible("Called from within GC map iterators")
  public void getDynamicLink(DynamicLink dynamicLink, int bcIndex) {
    switch (bcIndex) {
    case DO_SYSCALL:
      dynamicLink.set(CodeTranslator.sysCallMethod.getMemberRef()
          .asMethodReference(), JBC_invokevirtual);
      break;
    case RECORD_BRANCH:
      dynamicLink.set(CodeTranslator.recordUncaughtBranchMethod.getMemberRef()
          .asMethodReference(), JBC_invokevirtual);
      break;
    case BAD_INSTRUCTION_NEW:
      DBT.fail("Todo: dynamic linking for new bad instruction exception");
      break;
    case BAD_INSTRUCTION_INIT:
      dynamicLink.set(CodeTranslator.badInstrKlassInitMethod.getMemberRef()
          .asMethodReference(), JBC_invokespecial);
      break;
    case MEMORY_STORE8:
    case MEMORY_STORE16:
    case MEMORY_STORE32:
    case MEMORY_LOAD8:
    case MEMORY_ULOAD8:
    case MEMORY_LOAD16:
    case MEMORY_ULOAD16:
    case MEMORY_LOAD32:
      dynamicLink.set(ps.memory.getMethodRef(bcIndex), JBC_invokevirtual);
      break;
    default:
      //check if a custom call has been registered for this bytecode index.
      int callIdx = bcIndex - CUSTOM_CALL_BCINDEX_BASE;
    
      if (callIdx < 0 || callIdx >= customCalls.length) {
        DBT.write(bcIndex);
        DBT.fail("Trying to dynamic link inside a DBT trace for an unknown dynamic link location");
      }
      
      CustomCallInformation call = customCalls[callIdx];
      dynamicLink.set(call.methodRef, call.callType);
    }
  }

  /**
   * Get a representation of the bytecodes in the code attribute of this method.
   * 
   * @return object representing the bytecodes
   */
  public BytecodeStream getBytecodes() {
    return null;
  }

  /**
   * Size of bytecodes for this method
   */
  public int getBytecodeLength() {
    return numberOfInstructions == 0 ? 256 : numberOfInstructions;
  }

  public int getNumberOfInstructions() {
    return numberOfInstructions;
  }

  public void setNumberOfInstructions(int numberOfInstructions) {
    this.numberOfInstructions = numberOfInstructions;
  }
}
