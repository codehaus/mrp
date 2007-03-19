/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.memory;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_BootstrapClassLoader;
import org.jikesrvm.classloader.VM_FieldReference;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.opt.ir.Call;
import org.jikesrvm.opt.ir.GetField;
import org.jikesrvm.opt.ir.Unary;
import org.jikesrvm.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.opt.ir.OPT_GenerationContext;
import org.jikesrvm.opt.ir.OPT_Instruction;
import org.jikesrvm.opt.ir.OPT_LocationOperand;
import org.jikesrvm.opt.ir.OPT_MethodOperand;
import org.jikesrvm.opt.ir.OPT_Operators;
import org.jikesrvm.opt.ir.OPT_Register;
import org.jikesrvm.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.opt.ir.OPT_TrueGuardOperand;
import org.binarytranslator.vmInterface.TranslationHelper;

/**
 * CallBasedMemory abstraction:
 *
 * By default the translation methods plant calls to the load/store calls,
 * which are still abstract, ie there's no memory backing store yet
 */
public abstract class CallBasedMemory extends Memory implements OPT_Operators {
  /**
   * The name of the class we are to plant calls to
   */
  private final VM_Atom memoryClassName;
  /**
   * The name of the store methods
   */
  private final VM_Atom store8, store16, store32;
  /**
   * The name of the load methods
   */
  private final VM_Atom loadS8, loadU8, loadS16, loadU16, load32;
  /**
   * Descriptors for the methods
   */
  private final VM_Atom storeDescriptor, loadDescriptor;
  /**
   * Type of underlying memory
   */
  final VM_TypeReference memoryType;
  /**
   * A translation helper for generating code
   */
  protected TranslationHelper helper;
  /**
   * The generation context we're translating within
   */
  private OPT_GenerationContext gc;
  /**
   * Register that references the memory object
   */
  OPT_Register memory;

  /**
   * Constructor
   * @param className the name of the over-riding class
   */
  protected CallBasedMemory(String className) {
    this.memoryClassName = VM_Atom.findOrCreateAsciiAtom("L"+className+";");
    store8  = VM_Atom.findOrCreateAsciiAtom("store8");
    store16 = VM_Atom.findOrCreateAsciiAtom("store16");
    store32 = VM_Atom.findOrCreateAsciiAtom("store32"); 
    storeDescriptor = VM_Atom.findOrCreateAsciiAtom("(II)V");
    loadS8  = VM_Atom.findOrCreateAsciiAtom("loadSigned8");
    loadU8  = VM_Atom.findOrCreateAsciiAtom("loadUnsigned8"); 
    loadS16 = VM_Atom.findOrCreateAsciiAtom("loadSigned16");
    loadU16 = VM_Atom.findOrCreateAsciiAtom("loadUnsigned16");
    load32  = VM_Atom.findOrCreateAsciiAtom("load32");
    loadDescriptor = VM_Atom.findOrCreateAsciiAtom("(I)I");
    memoryType = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                               memoryClassName);
  }
  /**
   * Generate memory prologue,... for the beignning of a
   * trace. e.g. Loading the page table into a register
   */
  public void initTranslate(TranslationHelper helper) {
    this.helper = helper;
    this.gc = helper.getGenerationContext();
    VM_TypeReference psTref = VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                                            VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/generic/os/process/ProcessSpace;")
                                                            );
    VM_FieldReference ref = VM_MemberReference.findOrCreate(psTref, VM_Atom.findOrCreateAsciiAtom("memory"),
                                                            VM_Atom.findOrCreateAsciiAtom
                                                            ("Lorg/binarytranslator/generic/memory/Memory;")
                                                            ).asFieldReference();
    OPT_RegisterOperand memoryOp = helper.makeTemp(memoryType);
    helper.appendInstructionToCurrentBlock(GetField.create(GETFIELD, memoryOp,
                                                           gc.makeLocal(1,psTref),
                                                           new OPT_AddressConstantOperand(ref.peekResolvedField().getOffset()),
                                                           new OPT_LocationOperand(ref),
                                                           new OPT_TrueGuardOperand()));
    memory = memoryOp.register;
  }
  /**
   * Generate the IR code for the specified load
   * @param loadType the atom name of the type of load
   * @param bcIndex the bytecode index in
   * DummyDynamicCodeRunner.invokeCode of this method call - required
   * for lazy method resolution.
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  private void translateLoad(VM_Atom loadType, int bcIndex, OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
    OPT_Instruction s = Call.create(CALL, dest, null, null, null, 
                                    2);
    VM_MemberReference methRef = VM_MemberReference.findOrCreate(memoryType,
                                                                 loadType,
                                                                 loadDescriptor
                                                                 );
    boolean unresolved = methRef.needsDynamicLink(helper.getMethod());
    VM_Method method = ((VM_MethodReference)methRef).resolveInvokeSpecial();
    OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL((VM_MethodReference)methRef, method);
    OPT_RegisterOperand memoryOp = new OPT_RegisterOperand(memory, memoryType);
    Call.setParam(s, 0, memoryOp); // Sets 'this' pointer
    Call.setParam(s, 1, addr);
    Call.setGuard(s, new OPT_TrueGuardOperand());
    Call.setMethod(s, methOp);
    if (unresolved) {
      OPT_RegisterOperand offsetrop = gc.temps.makeTempOffset();
      helper.appendInstructionToCurrentBlock(Unary.create(RESOLVE_MEMBER, offsetrop,
                                                          Call.getMethod(s).copy()));
      Call.setAddress(s, offsetrop.copyRO());
    }
    else {
      Call.setAddress(s, new OPT_AddressConstantOperand
                      (((VM_MethodReference)methRef).peekResolvedMethod().getOffset()));
    }
    s.position = gc.inlineSequence;
    s.bcIndex = bcIndex;
    helper.appendInstructionToCurrentBlock(s);
  }
  /**
   * Generate the IR code for a byte load where the sign extended
   * result fills the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public void translateLoadSigned8(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
    translateLoad(loadS8, 56, addr, dest);
  }
  /**
   * Generate the IR code for a byte load where the zero extended
   * result fills the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public void translateLoadUnsigned8(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
    translateLoad(loadU8, 65, addr, dest);
  }
  /**
   * Generate the IR code for a 16bit load where the sign extended
   * result fills the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public void translateLoadSigned16(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
    translateLoad(loadS16, 74, addr, dest);
  }
  /**
   * Generate the IR code for a 16bit load where the zero extended
   * result fills the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public void translateLoadUnsigned16(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
    translateLoad(loadU16, 83, addr, dest);
  }
  /**
   * Generate the IR code for a 32bit load
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public void translateLoad32(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
    translateLoad(load32, 92, addr, dest);
  }
  /**
   * Generate the IR code for a 16bit load where the sign extended
   * result fills the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  protected void translateCallBasedLoadSigned16(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
    translateLoad(loadS16, 74, addr, dest);
  }
  /**
   * Generate the IR code for a 16bit load where the zero extended
   * result fills the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  protected void translateCallBasedLoadUnsigned16(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
    translateLoad(loadU16, 83, addr, dest);
  }
  /**
   * Generate the IR code for a 32bit load
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  protected void translateCallBasedLoad32(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
    translateLoad(load32, 92, addr, dest);
  }
  /**
   * Generate the IR code for the specified store
   * @param storeType the atom name of the type of store
   * @param bcIndex the bytecode index in
   * DummyDynamicCodeRunner.invokeCode of this method call - required
   * for lazy method resolution.
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  private void translateStore(VM_Atom storeType, int bcIndex, OPT_RegisterOperand addr, OPT_RegisterOperand src) {
    OPT_Instruction s = Call.create(CALL, null, null, null, null, 3);
    VM_MemberReference methRef = VM_MemberReference.findOrCreate(memoryType,
                                                                 storeType,
                                                                 storeDescriptor
                                                                 );
    boolean unresolved = methRef.needsDynamicLink(helper.getMethod());
    VM_Method method = ((VM_MethodReference)methRef).resolveInvokeSpecial();
    OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL((VM_MethodReference)methRef, method);
    OPT_RegisterOperand memoryOp = new OPT_RegisterOperand(memory, memoryType);
    Call.setParam(s, 0, memoryOp); // Sets 'this' pointer
    Call.setParam(s, 1, addr);
    Call.setParam(s, 2, src);
    Call.setGuard(s, new OPT_TrueGuardOperand());
    Call.setMethod(s, methOp);
    if (unresolved) {
      OPT_RegisterOperand offsetrop = gc.temps.makeTempOffset();
      helper.appendInstructionToCurrentBlock(Unary.create(RESOLVE_MEMBER, offsetrop,
                                                          Call.getMethod(s).copy()));
      Call.setAddress(s, offsetrop.copyRO());
    }
    else {
      Call.setAddress(s, new OPT_AddressConstantOperand
                      (((VM_MethodReference)methRef).peekResolvedMethod().getOffset()));
    }
    s.position = gc.inlineSequence;
    s.bcIndex = bcIndex;
    helper.appendInstructionToCurrentBlock(s);
  }
  /**
   * Generate the IR code for a byte store
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  public void translateStore8(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
    translateStore(store8, 30, addr, src);
  }
  /**
   * Generate the IR code for a 16bit store
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  public void translateStore16(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
    translateStore(store16, 39, addr, src);
  }
  /**
   * Generate the IR code for a 32bit store
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  public void translateStore32(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
    translateStore(store32, 48, addr, src);
  }
  /**
   * Generate the IR code for a byte store
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  public void translateCallBasedStore8(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
    translateStore(store8, 30, addr, src);
  }
  /**
   * Generate the IR code for a 16bit store
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  public void translateCallBasedStore16(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
    translateStore(store16, 39, addr, src);
  }
  /**
   * Generate the IR code for a 32bit store
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  public void translateCallBasedStore32(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
    translateStore(store32, 48, addr, src);
  }
}
