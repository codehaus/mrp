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
package org.binarytranslator.generic.fault;

import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * Thrown when a process space runs out of (virtual) memory when performing an operation.
 *
 */
public class InsufficientMemoryException extends RuntimeException {
  private final String operation;
  
  public InsufficientMemoryException(ProcessSpace ps, String operation) {
    this.operation = operation;
  }
  
  @Override
  public String toString() {
    return "InsufficientMemoryException (Operation: " + operation + ")";
  }
}
