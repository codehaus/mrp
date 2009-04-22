/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.memory;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.decoder.CodeTranslator;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.ir.*;
import org.jikesrvm.compilers.opt.ir.operand.*;
import org.vmmagic.pragma.Uninterruptible;

/**
 * CallBasedMemory abstraction:
 * 
 * By default the translation methods plant calls to the load/store calls, which
 * are still abstract, ie there's no memory backing store yet
 */
public abstract class CallBasedMemory extends Memory implements Operators {

  /**
   * The process space type reference
   */
  private static final TypeReference psTref;

  /**
   * Field reference to ps.memory
   */
  private static final FieldReference psMemoryRef;

  static {
    psTref = TypeReference.findOrCreate(ProcessSpace.class);
    psMemoryRef = MemberReference
        .findOrCreate(
            psTref,
            Atom.findOrCreateAsciiAtom("memory"),
            Atom
                .findOrCreateAsciiAtom("Lorg/binarytranslator/generic/memory/Memory;"))
        .asFieldReference();
  }

  /**
   * The store 8 method
   */
  private final RVMMethod store8;

  /**
   * The store 16 method
   */
  private final RVMMethod store16;

  /**
   * The store 32 method
   */
  private final RVMMethod store32;

  /**
   * The load signed 8 method
   */
  private final RVMMethod loadS8;

  /**
   * The load unsigned 8 method
   */
  private final RVMMethod loadU8;

  /**
   * The load signed 8 method
   */
  private final RVMMethod loadS16;

  /**
   * The load unsigned 8 method
   */
  private final RVMMethod loadU16;

  /**
   * The load 32 method
   */
  private final RVMMethod load32;

  /**
   * Type of underlying memory
   */
  final TypeReference memoryType;

  /**
   * A translation helper for generating code
   */
  protected CodeTranslator translator;

  /**
   * The generation context we're translating within
   */
  protected GenerationContext gc;

  /**
   * Register that references the memory object
   */
  protected Register memory;

  /**
   * Constructor
   * 
   * @param className
   *          the name of the over-riding class
   */
  protected CallBasedMemory(Class memoryClass) {
    //Debug initializations to run this stuff on SUN
    
    if (DBT_Options.buildForSunVM) {
      memoryType = null;
      loadS8 = loadU8 = loadS16 = loadU16 = load32 = null;
      store8 = store16 = store32 = null;
    }
    else {
    memoryType = TypeReference.findOrCreate(memoryClass);
      Atom storeDescriptor = Atom.findOrCreateAsciiAtom("(II)V");
      store8 = MemberReference.findOrCreate(memoryType,
          Atom.findOrCreateAsciiAtom("store8"), storeDescriptor)
          .asMethodReference().resolve();
      store16 = MemberReference.findOrCreate(memoryType,
          Atom.findOrCreateAsciiAtom("store16"), storeDescriptor)
          .asMethodReference().resolve();
      store32 = MemberReference.findOrCreate(memoryType,
          Atom.findOrCreateAsciiAtom("store32"), storeDescriptor)
          .asMethodReference().resolve();

      Atom loadDescriptor = Atom.findOrCreateAsciiAtom("(I)I");
      loadS8 = MemberReference.findOrCreate(memoryType,
          Atom.findOrCreateAsciiAtom("loadSigned8"), loadDescriptor)
          .asMethodReference().resolve();
      loadU8 = MemberReference.findOrCreate(memoryType,
          Atom.findOrCreateAsciiAtom("loadUnsigned8"), loadDescriptor)
          .asMethodReference().resolve();
      loadS16 = MemberReference.findOrCreate(memoryType,
          Atom.findOrCreateAsciiAtom("loadSigned16"), loadDescriptor)
          .asMethodReference().resolve();
      loadU16 = MemberReference.findOrCreate(memoryType,
          Atom.findOrCreateAsciiAtom("loadUnsigned16"), loadDescriptor)
          .asMethodReference().resolve();
      load32 = MemberReference.findOrCreate(memoryType,
          Atom.findOrCreateAsciiAtom("load32"), loadDescriptor)
          .asMethodReference().resolve();
    }
  }

  /**
   * Generate memory prologue,... for the beginning of a trace. e.g. Loading the
   * page table into a register
   */
  public void initTranslate(CodeTranslator helper) {
    this.translator = helper;
    this.gc = helper.getGenerationContext();
    RegisterOperand memoryOp = helper.makeTemp(memoryType);
    helper.appendInstruction(GetField.create(GETFIELD, memoryOp,
        gc.makeLocal(1, psTref), new AddressConstantOperand(psMemoryRef
            .peekResolvedField().getOffset()), new LocationOperand(
            psMemoryRef), new TrueGuardOperand()));
    memory = memoryOp.register;
  }

  /**
   * Generate the IR code for the specified load
   * 
   * @param loadMethod
   *          the load method to create
   * @param bcIndex
   *          the bytecode index in DummyDynamicCodeRunner.invokeCode of this
   *          method call - required for lazy method resolution.
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  private void translateLoad(RVMMethod loadMethod, int bcIndex,
      Operand addr, RegisterOperand dest) {
    Instruction s = Call.create(CALL, dest.copyRO(), null, null, null, 2);
    MethodReference loadMethRef = loadMethod.getMemberRef()
        .asMethodReference();

    MethodOperand methOp = MethodOperand.VIRTUAL(loadMethRef, loadMethod);
    RegisterOperand memoryOp = new RegisterOperand(memory, memoryType);
    Call.setParam(s, 0, memoryOp); // Sets 'this' pointer
    Call.setParam(s, 1, addr.copy());
    Call.setGuard(s, new TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new AddressConstantOperand(loadMethod.getOffset()));
    
    if (DBT_Options.inlineCallbasedMemory) {
      translator.appendInlinedCall(s);
    }
    else {
      s.position = gc.inlineSequence;
      s.bcIndex = bcIndex;
      translator.appendInstruction(s);
    }
  }

  /**
   * Generate the IR code for a byte load where the sign extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoadSigned8(Operand addr,
      RegisterOperand dest) {
    translateLoad(loadS8, DBT_Trace.MEMORY_LOAD8, addr, dest);
  }

  /**
   * Generate the IR code for a byte load where the zero extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoadUnsigned8(Operand addr,
      RegisterOperand dest) {
    translateLoad(loadU8, DBT_Trace.MEMORY_ULOAD8, addr, dest);
  }

  /**
   * Generate the IR code for a 16bit load where the sign extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoadSigned16(Operand addr,
      RegisterOperand dest) {
    translateLoad(loadS16, DBT_Trace.MEMORY_LOAD16, addr, dest);
  }

  /**
   * Generate the IR code for a 16bit load where the zero extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoadUnsigned16(Operand addr,
      RegisterOperand dest) {
    translateLoad(loadU16, DBT_Trace.MEMORY_ULOAD16, addr, dest);
  }

  /**
   * Generate the IR code for a 32bit load
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoad32(Operand addr, RegisterOperand dest) {
    translateLoad(load32, DBT_Trace.MEMORY_LOAD32, addr, dest);
  }

  /**
   * Generate the IR code for a 16bit load where the sign extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  protected void translateCallBasedLoadSigned16(Operand addr,
      RegisterOperand dest) {
    translateLoad(loadS16, DBT_Trace.MEMORY_LOAD16, addr, dest);
  }

  /**
   * Generate the IR code for a 16bit load where the zero extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  protected void translateCallBasedLoadUnsigned16(Operand addr,
      RegisterOperand dest) {
    translateLoad(loadU16, DBT_Trace.MEMORY_ULOAD16, addr, dest);
  }

  /**
   * Generate the IR code for a 32bit load
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  protected void translateCallBasedLoad32(Operand addr,
      RegisterOperand dest) {
    translateLoad(load32, DBT_Trace.MEMORY_LOAD32, addr, dest);
  }

  /**
   * Generate the IR code for the specified store
   * 
   * @param storeMethod
   *          the store method to call
   * @param bcIndex
   *          the bytecode index in DummyDynamicCodeRunner.invokeCode of this
   *          method call - required for lazy method resolution.
   * @param src
   *          the register that holds the value to store
   * @param addr
   *          the address of the value to store
   */
  private void translateStore(RVMMethod storeMethod, int bcIndex,
      Operand addr, Operand src) {
    Instruction s = Call.create(CALL, null, null, null, null, 3);
    MethodReference storeMethRef = storeMethod.getMemberRef()
        .asMethodReference();
    MethodOperand methOp = MethodOperand.VIRTUAL(storeMethRef,
        storeMethod);
    RegisterOperand memoryOp = new RegisterOperand(memory, memoryType);
    Call.setParam(s, 0, memoryOp); // Sets 'this' pointer
    Call.setParam(s, 1, addr.copy());
    Call.setParam(s, 2, src.copy());
    Call.setGuard(s, new TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new AddressConstantOperand(storeMethod.getOffset()));
    
    if (DBT_Options.inlineCallbasedMemory) {
      translator.appendInlinedCall(s);
    }
    else
    {
      s.position = gc.inlineSequence;
      s.bcIndex = bcIndex;
      translator.appendInstruction(s);
    }
  }

  /**
   * Generate the IR code for a byte store
   * 
   * @param src
   *          the register that holds the value to store
   * @param addr
   *          the address of the value to store
   */
  public void translateStore8(Operand addr, Operand src) {
    translateStore(store8, DBT_Trace.MEMORY_STORE8, addr, src);
  }

  /**
   * Generate the IR code for a 16bit store
   * 
   * @param src
   *          the register that holds the value to store
   * @param addr
   *          the address of the value to store
   */
  public void translateStore16(Operand addr, Operand src) {
    translateStore(store16, DBT_Trace.MEMORY_STORE16, addr, src);
  }

  /**
   * Generate the IR code for a 32bit store
   * 
   * @param src
   *          the register that holds the value to store
   * @param addr
   *          the address of the value to store
   */
  public void translateStore32(Operand addr, Operand src) {
    translateStore(store32, DBT_Trace.MEMORY_STORE32, addr, src);
  }

  /**
   * Get method reference if linking a call
   * 
   * @param callAddress
   *          the address associated with this call
   */
  @Uninterruptible
  public MethodReference getMethodRef(int callAddress) {
    switch (callAddress) {
    case DBT_Trace.MEMORY_STORE8:
      return store8.getMemberRef().asMethodReference();
    case DBT_Trace.MEMORY_STORE16:
      return store16.getMemberRef().asMethodReference();
    case DBT_Trace.MEMORY_STORE32:
      return store32.getMemberRef().asMethodReference();
    case DBT_Trace.MEMORY_LOAD8:
      return loadS8.getMemberRef().asMethodReference();
    case DBT_Trace.MEMORY_ULOAD8:
      return loadU8.getMemberRef().asMethodReference();
    case DBT_Trace.MEMORY_LOAD16:
      return loadS16.getMemberRef().asMethodReference();
    case DBT_Trace.MEMORY_ULOAD16:
      return loadU16.getMemberRef().asMethodReference();
    case DBT_Trace.MEMORY_LOAD32:
      return load32.getMemberRef().asMethodReference();
    default:
      DBT.write(callAddress);
      DBT.fail("Trying to dynamic link inside a DBT trace for an unknown dynamic link location");
      return null;
    }
  }
}
