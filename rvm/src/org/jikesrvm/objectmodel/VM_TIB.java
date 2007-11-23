/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.ArchitectureSpecific.VM_ArchConstants;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.ArchitectureSpecific.VM_LazyCompilationTrampoline;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.Intrinsic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * This class represents an instance of a type information block.
 *
 * #see {@link VM_TIBLayoutConstants}
 */
@Uninterruptible
@NonMoving
public final class VM_TIB implements VM_TIBLayoutConstants, VM_SizeConstants {
  /**
   * Calculate the number of words required to hold the lazy method invoker trampoline.
   * @return
   */
  public static int lazyMethodInvokerTrampolineWords() {
    int codeWords = VM.BuildFor32Addr ? 3 : 2;
    if (VM.runningVM && VM.VerifyAssertions) {
      int codeBytes = VM_LazyCompilationTrampoline.instructions.length() << VM_ArchConstants.LG_INSTRUCTION_WIDTH;
      VM._assert(codeWords == ((codeBytes + BYTES_IN_ADDRESS - 1) >>> LOG_BYTES_IN_ADDRESS));
    }
    return codeWords;
  }

  /**
   * Calculate the size of a TIB
   */
  @NoInline
  public static int computeSize(int numVirtualMethods) {
    return TIB_FIRST_INTERFACE_METHOD_INDEX + numVirtualMethods + lazyMethodInvokerTrampolineWords();
  }

  /**
   * Calculate the virtual method offset for the given index.
   * @param virtualMethodIndex The index to calculate the offset for
   * @return The offset.
   */
  public static Offset getVirtualMethodOffset(int virtualMethodIndex) {
    return Offset.fromIntZeroExtend((TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex) << LOG_BYTES_IN_ADDRESS);
  }

  /**
   * Calculate the virtual method index for the given offset.
   * @param virtualMethodOffset The offset to calculate the index for
   * @return The index.
   */
  public static int getVirtualMethodIndex(Offset virtualMethodOffset) {
    return (virtualMethodOffset.toInt() >>> LOG_BYTES_IN_ADDRESS) - TIB_FIRST_VIRTUAL_METHOD_INDEX;
  }

  /**
   * Calculate the virtual method index for the given raw slot index.
   *
   * @param slot The raw slot to find the virtual method index for.
   * @return The index.
   */
  public static int getVirtualMethodIndex(int slot) {
    if (VM.VerifyAssertions) VM._assert(slot > TIB_FIRST_VIRTUAL_METHOD_INDEX);
    return slot - TIB_FIRST_VIRTUAL_METHOD_INDEX;
  }

  /**
   * The backing data used during boot image writing.
   */
  private final Object[] data;

  /**
   * Private constructor. Can not create instances.
   */
  private VM_TIB(int size) {
    this.data = new Object[size];
  }

  /**
   * Return the backing array (for boot image writing)
   */
  public Object[] getBacking() {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    return data;
  }

  /**
   * Create a new TIB of the specified size.
   *
   * @param size The size of the TIB
   * @return The created TIB instance.
   */
  @NoInline
  @Interruptible
  public static VM_TIB allocate(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_TIB(size);
  }

  /**
   * Get a TIB entry.
   *
   * @param index The index of the entry to get
   * @return The value of that entry
   */
  @Intrinsic
  public Object get(int index) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return data[index];
  }

  /**
   * Set a TIB entry.
   *
   * @param index The index of the entry to set
   * @param value The value to set the entry to.
   */
  @Intrinsic
  @UninterruptibleNoWarn // hijacked at runtime
  protected void set(int index, Object value) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    data[index] = value;
  }

  /**
   * Return the length of the TIB
   */
  @Intrinsic
  public int length() {
    return data.length;
  }

  /**
   * Get the type for this TIB.
   */
  @Inline
  public VM_Type getType() {
    return VM_Magic.objectAsType(get(TIB_TYPE_INDEX));
  }

  /**
   * Set the type for this TIB.
   */
  public void setType(VM_Type type) {
    set(TIB_TYPE_INDEX, type);
  }

  /**
   * Get the superclass id set for this type.
   */
  @Inline
  public short[] getSuperclassIds() {
    return VM_Magic.objectAsShortArray(get(TIB_SUPERCLASS_IDS_INDEX));
  }

  /**
   * Set the superclass id set for this type.
   */
  public void setSuperclassIds(short[] superclassIds) {
    set(TIB_SUPERCLASS_IDS_INDEX, superclassIds);
  }

  /**
   * Get the ITable array for this type.
   */
  @Interruptible
  public VM_ITableArray getITableArray() {
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    return (VM_ITableArray)get(TIB_ITABLES_TIB_INDEX);
  }

  /**
   * Set the ITable array for this type.
   */
  public void setITableArray(VM_ITableArray iTableArray) {
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    set(TIB_ITABLES_TIB_INDEX, iTableArray);
  }

  /**
   * Get the does implement entry of the TIB
   */
  @Inline
  public int[] getDoesImplement() {
    return VM_Magic.objectAsIntArray(get(TIB_DOES_IMPLEMENT_INDEX));
  }

  /**
   * Set the does implement entry of the TIB
   */
  public void setDoesImplement(int[] doesImplement) {
    set(TIB_DOES_IMPLEMENT_INDEX, doesImplement);
  }

  /**
   * Get the IMT from the TIB
   */
  @Interruptible
  public VM_IMT getImt() {
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    return (VM_IMT)get(TIB_IMT_TIB_INDEX);
  }

  /**
   * Set the IMT of the TIB
   */
  public void setImt(VM_IMT imt) {
    if (VM.VerifyAssertions) VM._assert(imt.length() == IMT_METHOD_SLOTS);
    if (VM.VerifyAssertions) VM._assert(getType().isClassType());
    set(TIB_IMT_TIB_INDEX, imt);
  }

  /**
   * Set the TIB of the elements of this array (null if not an array).
   */
  public void setArrayElementTib(VM_TIB arrayElementTIB) {
    if (VM.VerifyAssertions) VM._assert(getType().isArrayType());
    set(TIB_ARRAY_ELEMENT_TIB_INDEX, VM_Magic.tibAsObject(arrayElementTIB));
  }

  /**
   * Get a virtual method from this TIB.
   *
   * When running the VM, we must translate requests to return the internal
   * lazy compilation trampoline marker.
   */
  @NoInline
  @Interruptible
  public VM_CodeArray getVirtualMethod(int virtualMethodIndex) {
    int index = TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex;
    if (VM.runningVM && isInternalLazyCompilationTrampoline(virtualMethodIndex)) {
      return VM_LazyCompilationTrampoline.instructions;
    }
    return (VM_CodeArray) get(index);
  }

  /**
   * Determine if a virtual method is the internal lazy compilation trampoline.
   */
  @NoInline
  public boolean isInternalLazyCompilationTrampoline(int virtualMethodIndex) {
    int index = TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex;
    Address tibAddress = VM_Magic.objectAsAddress(this);
    Address callAddress = tibAddress.loadAddress(Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS));
    Address maxAddress = tibAddress.plus(Offset.fromIntZeroExtend(length() << LOG_BYTES_IN_ADDRESS));
    return callAddress.GE(tibAddress) && callAddress.LT(maxAddress);
  }

  /**
   * Get a virtual method from this TIB by offset.
   */
  @Interruptible
  public VM_CodeArray getVirtualMethod(Offset virtualMethodOffset) {
    return getVirtualMethod(getVirtualMethodIndex(virtualMethodOffset));
  }

  /**
   * Set a virtual method in this TIB.
   *
   * When running the VM, we must translate requests to use the internal
   * lazy compilation trampoline.
   */
  @NoInline
  public void setVirtualMethod(int virtualMethodIndex, VM_CodeArray code) {
    if (VM.VerifyAssertions) VM._assert(virtualMethodIndex >= 0);

    if (VM.runningVM && code == VM_LazyCompilationTrampoline.instructions) {
      Address tibAddress = VM_Magic.objectAsAddress(this);
      Address callAddress = tibAddress.plus(Offset.fromIntZeroExtend(lazyMethodInvokerTrampolineIndex() << LOG_BYTES_IN_ADDRESS));
      set(TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex, callAddress);
    } else {
      set(TIB_FIRST_VIRTUAL_METHOD_INDEX + virtualMethodIndex, code);
    }
  }

  /**
   * Set a virtual method in this TIB by offset.
   */
  public void setVirtualMethod(Offset virtualMethodOffset, VM_CodeArray code) {
    setVirtualMethod(getVirtualMethodIndex(virtualMethodOffset), code);
  }

  /**
   * Calculate the address that is the call target for the lazy method invoker trampoline.
   * @return
   */
  public int lazyMethodInvokerTrampolineIndex() {
    return length() - lazyMethodInvokerTrampolineWords();
  }

  /**
   * Initialize the lazy method invoker trampoline for this tib.
   */
  @NoInline
  public void initializeInternalLazyCompilationTrampoline() {
    VM_CodeArray source = VM_LazyCompilationTrampoline.instructions;
    int targetSlot = lazyMethodInvokerTrampolineIndex();
    int logIPW = LOG_BYTES_IN_ADDRESS - VM_ArchConstants.LG_INSTRUCTION_WIDTH;
    int logIPI = LOG_BYTES_IN_INT - VM_ArchConstants.LG_INSTRUCTION_WIDTH;
    if (VM.VerifyAssertions) VM._assert(VM_ArchConstants.LG_INSTRUCTION_WIDTH <= LOG_BYTES_IN_INT);
    int mask = 0xFFFFFFFF >>> (((1 << logIPI) - 1) << LOG_BITS_IN_BYTE);
    for(int i = 0; i < lazyMethodInvokerTrampolineWords(); i++) {
      Word currentWord = Word.zero();
      int base = i << logIPW;
      for(int j=0; j < (1 << logIPW) && (base + j) < source.length(); j++) {
        Word currentEntry = Word.fromIntZeroExtend(source.get(base + j) & mask);
        currentEntry = currentEntry.lsh(((VM.LittleEndian ? j : (1 << logIPW) - (j+1)) << VM_ArchConstants.LG_INSTRUCTION_WIDTH) << LOG_BITS_IN_BYTE);
        currentWord = currentWord.or(currentEntry);
      }
      set(targetSlot + i, currentWord);
    }
  }


  /**
   * Set a specialized method in this TIB.
   */
  public void setSpecializedMethod(int specializedMethodIndex, VM_CodeArray code) {
    if (VM.VerifyAssertions) VM._assert(specializedMethodIndex >= 0);
    set(TIB_FIRST_SPECIALIZED_METHOD_INDEX + specializedMethodIndex, code);
  }

  /**
   * Set an IMT entry in this TIB.
   */
  public void setImtEntry(int imtEntryIndex, VM_CodeArray code) {
    set(TIB_FIRST_INTERFACE_METHOD_INDEX + imtEntryIndex, code);
  }

  /**
   * The number of virtual methods in this TIB.
   */
  public int numVirtualMethods() {
    return length() - TIB_FIRST_VIRTUAL_METHOD_INDEX - lazyMethodInvokerTrampolineWords();
  }

  /**
   * Does this slot in the TIB hold a TIB entry?
   * @param slot the TIB slot
   * @return true if this the array element TIB
   */
  public boolean slotContainsTib(int slot) {
    if (slot == TIB_ARRAY_ELEMENT_TIB_INDEX && getType().isArrayType()) {
      if (VM.VerifyAssertions) VM._assert(get(slot) != null);
      return true;
    }
    return false;
  }

  /**
   * Does this slot in the TIB hold code?
   * @param slot the TIB slot
   * @return true if slot is one that holds a code array reference
   */
  public boolean slotContainsCode(int slot) {
    if (VM.VerifyAssertions) {
      VM._assert(slot < length());
    }
    return slot >= TIB_FIRST_VIRTUAL_METHOD_INDEX;
  }
}
