/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.abi.linux;

import org.binarytranslator.generic.memory.MemoryMapException;

/**
 * Class encapsulating the interface between Linux system calls and
 * the memory and registers of various architectures.
 */
public interface LinuxSystemCallGenerator {
  
  /**
   * This interface allows you to iteratively grab all arguments to a system call.
   */
  public interface CallArgumentIterator {
    /**
     * Interpret the next system call argument as an integer and return it.
     */
    int nextInt();
    
    /**
     * Interpret the next system call argument as a long and return it.
     */
    long nextLong();
  }
  
  /**
	* Return the system call number from the generator
	*/
  public int getSysCallNumber();
  /**
	* Create an array of arguments for the system call
	* @param n number of system call arguments to read
	* @return array of system call argument values
	*/
  public CallArgumentIterator getSysCallArguments();
  /**
	* Set the return value for a system call
	* @param r the return value
	*/
  public void setSysCallReturn(int r);
  /**
	* Set an error value for a system call
	* @param r the error value
	*/
  public void setSysCallError(int r);
  /**
	* Write to the memory of the system call generator a 32bit value
	* @param address where to write
	* @param data value to store
	*/
  public void memoryStore32(int address, int data);
  /**
	* Write to the memory of the system call generator an 8bit value
	* @param address where to write
	* @param data value to store
	*/
  public void memoryStore8(int address, byte data);
  /**
	* Load from memory of the system call generator an 8bit value
	* @param address where to read
	* @return value read
	*/
  public byte memoryLoad8(int address);
  /**
	* Load from memory of the system call generator a 32bit value
	* @param address where to read
	* @return value read
	*/
  public int memoryLoad32(int address);
  /**
	* Load an ASCIIZ string from the memory of the system call
	* generator and return it as a Java String.
	* @param address where to read
	* @return the String read
	*/
  public String memoryReadString(int address);
  /**
	* Store an ASCIIZ string to the memory of the system call generator
	* @param address where to read
	* @param data the String to write
	*/
  public void memoryWriteString(int address, String data);
  /**
	* Get the top of the BSS segment (the heap that reside below the
	* stack in memory)
	* @return top of BSS segment
	*/
  public int getBrk();
  /**
	* Set the top of the BSS segment (the heap that reside below the
	* stack in memory)
	* @param address new top of BSS segment
	*/
  public void setBrk(int address);
  /**
   * Map an anonymous page of memory
   * @param addr the address to map or NULL if don't care
   * @param len  the amount of memory to map
   * @param read is the page readable
   * @param write is the page writable
   * @param exec is the page executable
   */
  public int memoryMap(int addr, int len, boolean read, boolean write, boolean exec) throws MemoryMapException;
 }
