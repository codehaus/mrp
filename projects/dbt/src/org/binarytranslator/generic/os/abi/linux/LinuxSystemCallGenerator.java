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
package org.binarytranslator.generic.os.abi.linux;

import org.binarytranslator.generic.os.process.ProcessSpace;

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

  /** Returns the process space that this call originated from.*/
  public ProcessSpace getProcessSpace();
 }
