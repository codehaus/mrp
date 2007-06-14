/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.memory;

import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.classloader.VM_MethodReference;
import org.binarytranslator.generic.decoder.AbstractCodeTranslator;
import org.vmmagic.pragma.Uninterruptible;
import java.io.RandomAccessFile;

/**
 * Memory abstraction
 */
public abstract class Memory {
  /**
   * Map an anonymous page of memory
   * @param addr the address to map or NULL if don't care
   * @param len the amount of memory to map
   * @param read is the page readable
   * @param write is the page writable
   * @param exec is the page executable
   */
  public abstract int map(int addr, int len, boolean read, boolean write,
      boolean exec) throws MemoryMapException;

  /**
   * Map a page of memory from file
   * @param file the file map in from
   * @param offset the offset of the file to map from
   * @param addr the address to map or NULL if don't care
   * @param len the amount of memory to map
   * @param read is the page readable
   * @param write is the page writable
   * @param exec is the page executable
   */
  public abstract int map(RandomAccessFile file, long offset, int addr,
      int len, boolean read, boolean write, boolean exec)
      throws MemoryMapException;
  
  /**
   * Changes the protection of a region of memory. Note that this function only guarantees
   * protection to be changed on a page level. Therefore, if <code>address</code> is not page
   * aligned or len is not a muliple of {@link #getPageSize()}, then the function may
   * change the protection of a larger memory range than initally requested. 
   * 
   * @param address
   *  The start address of a memory block, whose protection shall be changed. 
   * @param len
   *  The length of the memory block.
   * @param newRead
   *  Set to true to allow reading from this memory block, false otherwise.
   * @param newWrite
   *  Set to true to allow writing to this memory block, false otherwise.
   * @param newExec
   *  Set to true to allow executing code from this memory block, false otherwise.
   */
  public abstract void changeProtection(int address, int len, boolean newRead, 
      boolean newWrite, boolean newExec);

  /**
   * Unmap a page of memory
   * @param addr the address to unmap
   * @param len the amount of memory to unmap
   */
  public abstract void unmap(int addr, int len);

  /**
   * Is the given address mapped into memory?
   * @param addr to check
   * @return true => memory is mapped
   */
  public abstract boolean isMapped(int addr);
  
  /**
   * Ensure memory between start and end is mapped
   * @param startAddr starting address for mapped memory
   * @param endAddr ending address for mapped memory
   */
  public void ensureMapped(int startAddr, int endAddr) throws MemoryMapException {
    startAddr = truncateToPage(startAddr);
    endAddr = truncateToNextPage(endAddr);
    for (;startAddr < endAddr; startAddr += getPageSize()) {
      if (!isMapped(startAddr)) {
        map(startAddr, getPageSize(), true, true, false);
      }
    }
  }

  /**
   * Is the given address aligned on a page boundary?
   * @param addr the address to check
   * @return whether the address is aligned
   */
  public abstract boolean isPageAligned(int addr);

  /**
   * Make the given address page aligned to the page beneath it
   * @param addr the address to truncate
   * @return the truncated address
   */
  public abstract int truncateToPage(int addr);

  /**
   * Make the given address page aligned to the page above it
   * @param addr the address to truncate
   * @return the truncated address
   */
  public abstract int truncateToNextPage(int addr);

  /**
   * @return the size of a page
   */
  public abstract int getPageSize();
  
  /**
   * Perform a byte load where the sign extended result fills the return value
   * @param addr the address of the value to load
   * @return the sign extended result
   */
  public abstract int loadSigned8(int addr);

  /**
   * Perform a byte load where the zero extended result fills the return value
   * @param addr the address of the value to load
   * @return the zero extended result
   */
  public abstract int loadUnsigned8(int addr);

  /**
   * Perform a 16bit load where the sign extended result fills the return value
   * @param addr the address of the value to load
   * @return the sign extended result
   */
  public abstract int loadSigned16(int addr);

  /**
   * Perform a 16bit load where the zero extended result fills the return value
   * @param addr the address of the value to load
   * @return the zero extended result
   */
  public abstract int loadUnsigned16(int addr);

  /**
   * Perform a 32bit load
   * @param addr the address of the value to load
   * @return the result
   */
  public abstract int load32(int addr);

  /**
   * Perform a 8bit load from memory that must be executable
   * @param addr the address of the value to load
   * @return the result
   */
  public abstract int loadInstruction8(int addr);
  
  /**
   * Perform a 8bit load from memory that must be executable
   * @param addr the address of the value to load
   * @return the result
   */
  public abstract int loadInstruction16(int addr);

  /**
   * Perform a 32bit load from memory that must be executable
   * @param addr the address of the value to load
   * @return the result
   */
  public abstract int loadInstruction32(int addr);

  /**
   * Perform a byte store
   * @param value the value to store
   * @param addr the address of where to store
   */
  public abstract void store8(int addr, int value);

  /**
   * Perform a 16bit store
   * @param value the value to store
   * @param addr the address of where to store
   */
  public abstract void store16(int addr, int value);

  /**
   * Perform a 32bit store
   * @param value the value to store
   * @param addr the address of where to store
   */
  public abstract void store32(int addr, int value);

  /**
   * Generate memory prologue,... for the beignning of a trace. e.g. Loading the
   * page table into a register
   */
  public abstract void initTranslate(AbstractCodeTranslator helper);

  /**
   * Generate the IR code for a byte load where the sign extended result fills
   * the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public abstract void translateLoadSigned8(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a byte load where the zero extended result fills
   * the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public abstract void translateLoadUnsigned8(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a 16bit load where the sign extended result fills
   * the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public abstract void translateLoadSigned16(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a 16bit load where the zero extended result fills
   * the register
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public abstract void translateLoadUnsigned16(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a 32bit load
   * @param dest the register to hold the result
   * @param addr the address of the value to load
   */
  public abstract void translateLoad32(OPT_Operand addr,
      OPT_RegisterOperand dest);

  /**
   * Generate the IR code for a byte store
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  public abstract void translateStore8(OPT_Operand addr, OPT_Operand src);

  /**
   * Generate the IR code for a 16bit store
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  public abstract void translateStore16(OPT_Operand addr,
      OPT_Operand src);

  /**
   * Generate the IR code for a 32bit store
   * @param src the register that holds the value to store
   * @param addr the address of the value to store
   */
  public abstract void translateStore32(OPT_Operand addr,
      OPT_Operand src);

  /**
   * Get method reference if linking a call
   * @param callAddress the address associated with this call
   */
  @Uninterruptible
  public VM_MethodReference getMethodRef(int callAddress) {
    throw new Error("Error linking method at " + callAddress
        + " for memory model " + this.getClass());
  }
  
  /**
   * Helper function that prints a hexadecimal version of a memory region. 
   * Quite useful for debugging. 
   * @param address
   *  The address to start printing from.
   * @param length
   *  The number of bytes to print from <code>address</code>.
   * @return
   *  A string with a hexdecimal representation of that memory region.
   *  The string is only useful for printing, no assumptions about its format
   *  shall be made.
   */
  public String hexDump(int address, int length) {
    
    StringBuilder output = new StringBuilder();
    int printed = 0;
    
    while (printed != length) {
      
      //make a line break and print the current address every 8 bytes
      if (printed % 8 == 0) {
        if (printed != 0)
          output.append('\n');
        
        output.append("[0x");
        output.append(Integer.toHexString(address));
        output.append("] ");
      }
      
      output.append(String.format("%02x", loadUnsigned8(address++)));
      output.append(' ');
      printed++;
    }
    
    return output.toString();
  }
}