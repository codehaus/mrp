package org.binarytranslator.generic.execution;

import org.binarytranslator.generic.os.process.ProcessSpace;

public abstract class ExecutionController {
  
  protected final ProcessSpace ps;
  
  public ExecutionController(ProcessSpace ps) {
    this.ps = ps;
  }
  
  public abstract void run();
}
