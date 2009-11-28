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
package org.binarytranslator.generic.memory;

/**
 * Captures exceptions that can occur during memory mangement
 */
final public class MemoryMapException extends RuntimeException {
  
  public enum Reason {
    /** Attempt to allocate on a non-page boundary */
    UNALIGNED_ADDRESS,
    /** Attempt to allocate from a file on an unaligned file offset */
    UNALIGNED_FILE_OFFSET
  }

  /**
   * The type of this memory map exception
   */
  private Reason reason;

  /**
   * The file offset or address that was unaligned
   */
  private long offsetOrAddress;

  /**
   * Throw an unaligned address memory map exception
   */
  static void unalignedAddress(int addr) throws MemoryMapException {
    throw new MemoryMapException((long) addr, Reason.UNALIGNED_ADDRESS);
  }

  /**
   * Throw an unaligned file offset memory map exception
   */
  static void unalignedFileOffset(long offset) throws MemoryMapException {
    throw new MemoryMapException(offset, Reason.UNALIGNED_FILE_OFFSET);
  }

  /**
   * Constructor
   */
  private MemoryMapException(long addr, Reason reason) {
    offsetOrAddress = addr;
    this.reason = reason;
  }

  /**
   * String representation of exception
   */
  public String toString() {
    switch (reason) {
    case UNALIGNED_ADDRESS:
      return String.format("Unaligned memory map address: 0x%x", offsetOrAddress);
      
    case UNALIGNED_FILE_OFFSET:
      return String.format("Unaligned file offset: 0x%x", offsetOrAddress);
      
    default:
      throw new RuntimeException("Unexpected MemoryMapException Reason: " + reason);
    }
  }
}
