/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.memory;

import org.jikesrvm.opt.ir.OPT_Operand;
import org.jikesrvm.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.classloader.VM_MethodReference;
import org.binarytranslator.vmInterface.TranslationHelper;
import java.io.RandomAccessFile;

/**
 * Memory abstraction
 */
public abstract class Memory {
  /**
   * Map an anonymous page of memory
   * 
   * @param addr
   *          the address to map or NULL if don't care
   * @param len
   *          the amount of memory to map
   * @param read
   *          is the page readable
   * @param write
   *          is the page writable
   * @param exec
   *          is the page executable
   */
  public abstract int map(int addr, int len, boolean read, boolean write,
      boolean exec) throws MemoryMapException;

  /**
   * Map a page of memory from file
   * 
   * @param file
   *          the file map in from
   * @param offset
   *          the offset of the file to map from
   * @param addr
   *          the address to map or NULL if don't care
   * @param len
   *          the amount of memory to map
   * @param read
   *          is the page readable
   * @param write
   *          is the page writable
   * @param exec
   *          is the page executable
   */
  public abstract int map(RandomAccessFile file, long offset, int addr,
      int len, boolean read, boolean write, boolean exec)
      throws MemoryMapException;

  /**
   * Unmap a page of memory
   * 
   * @param addr
   *          the address to unmap
   * @param len
   *          the amount of memory to unmap
   */
  public abstract void unmap(int addr, int len);

  /**
   * Is the given address aligned on a page boundary?
   * 
   * @param addr
   *          the address to check
   * @return whether the address is aligned
   */
  public abstract boolean isPageAligned(int addr);

  /**
   * Make the given address page aligned to the page beneath it
   * 
   * @param addr
   *          the address to truncate
   * @return the truncated address
   */
  public abstract int truncateToPage(int addr);

  /**
   * Make the given address page aligned to the page above it
   * 
   * @param addr
   *          the address to truncate
   * @return the truncated address
   */
  public abstract int truncateToNextPage(int addr);

  /**
   * Perform a byte load where the sign extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the sign extended result
   */
  public abstract int loadSigned8(int addr);

  /**
   * Perform a byte load where the zero extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the zero extended result
   */
  public abstract int loadUnsigned8(int addr);

  /**
   * Perform a 16bit load where the sign extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the sign extended result
   */
  public abstract int loadSigned16(int addr);

  /**
   * Perform a 16bit load where the zero extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the zero extended result
   */
  public abstract int loadUnsigned16(int addr);

  /**
   * Perform a 32bit load
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  public abstract int load32(int addr);

  /**
   * Perform a 8bit load from memory that must be executable
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  public abstract int loadInstruction8(int addr);

  /**
   * Perform a 32bit load from memory that must be executable
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  public abstract int loadInstruction32(int addr);

  /**
   * Perform a byte store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  public abstract void store8(int addr, int value);

  /**
   * Perform a 16bit store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  public abstract void store16(int addr, int value);

  /**
   * Perform a 32bit store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  public abstract void store32(int addr, int value);

  /**
   * Generate memory prologue,... for the beignning of a trace. e.g. Loading the
   * page table into a register
   */
  public abstract void initTranslate(TranslationHelper helper);

  /**
   * Generate the IR code for a byte load where the sign extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public abstract void translateLoadSigned8(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a byte load where the zero extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public abstract void translateLoadUnsigned8(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a 16bit load where the sign extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public abstract void translateLoadSigned16(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a 16bit load where the zero extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public abstract void translateLoadUnsigned16(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a 32bit load
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public abstract void translateLoad32(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a byte store
   * 
   * @param src
   *          the register that holds the value to store
   * @param addr
   *          the address of the value to store
   */
  public abstract void translateStore8(OPT_Operand addr,
      OPT_RegisterOperand src);

  /**
   * Generate the IR code for a 16bit store
   * 
   * @param src
   *          the register that holds the value to store
   * @param addr
   *          the address of the value to store
   */
  public abstract void translateStore16(OPT_Operand addr,
      OPT_RegisterOperand src);

  /**
   * Generate the IR code for a 32bit store
   * 
   * @param src
   *          the register that holds the value to store
   * @param addr
   *          the address of the value to store
   */
  public abstract void translateStore32(OPT_Operand addr,
      OPT_RegisterOperand src);

  /**
   * Get method reference if linking a call
   * 
   * @param callAddress
   *          the address associated with this call
   */
  public VM_MethodReference getMethodRef(int callAddress) {
    throw new Error("Error linking method at " + callAddress
        + " for memory model " + this.getClass());
  }
}
