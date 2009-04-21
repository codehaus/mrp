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
