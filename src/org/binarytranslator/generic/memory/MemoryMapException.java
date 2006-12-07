/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.memory;

import org.binarytranslator.vmInterface.DBT_OptimizingCompilerException;

/**
 * Captures exceptions that can occur during memory mangement
 */
final public class MemoryMapException extends Exception {
  /**
   * Attempt to allocate on a non-page boundary
   */
  private static final int UNALIGNED_ADDRESS=1;
  /**
   * Attempt to allocate from a file on an unaligned file offset
   */
  private static final int UNALIGNED_FILE_OFFSET=2;
  /**
   * The type of this memory map exception
   */
  private int type;
  /**
   * The file offset or address that was unaligned
   */
  private long offsetOrAddress;
  /**
   * Throw an unaligned address memory map exception
   */
  static void unalignedAddress(int addr) throws MemoryMapException {
    throw new MemoryMapException((long)addr, UNALIGNED_ADDRESS);
  }
  /**
   * Throw an unaligned file offset memory map exception
   */
  static void unalignedFileOffset(long offset) throws MemoryMapException {
    throw new MemoryMapException(offset, UNALIGNED_FILE_OFFSET);
  }
  /**
   * Constructor
   */
  private MemoryMapException(long addr, int type) {
    offsetOrAddress = addr;
    this.type = type;
  }
  /**
   * String representation of exception
   */
  public String toString() {
    switch(type) {
    case UNALIGNED_ADDRESS:
      return "Unaligned memory map address: 0x" + Integer.toHexString((int)offsetOrAddress);
    case UNALIGNED_FILE_OFFSET:
      return "Unaligned file offset: " + offsetOrAddress;
    }
    DBT_OptimizingCompilerException.UNREACHABLE();
    return null;
  }
}
