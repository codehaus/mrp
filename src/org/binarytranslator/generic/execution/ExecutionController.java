package org.binarytranslator.generic.execution;

import org.binarytranslator.DBT;
import org.binarytranslator.generic.os.process.ProcessSpace;

public abstract class ExecutionController {
  
  public enum Type {
    Translator,
    Interpreter,
    GDB
  }
  
  protected final ProcessSpace ps;
  
  public ExecutionController(ProcessSpace ps) {
    if (DBT.VerifyAssertions)
      DBT._assert(ps != null);
    
    this.ps = ps;
  }
  
  public abstract void run();
}
