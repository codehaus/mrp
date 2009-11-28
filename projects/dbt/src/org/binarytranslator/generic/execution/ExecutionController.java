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
package org.binarytranslator.generic.execution;

import org.binarytranslator.DBT;
import org.binarytranslator.generic.os.process.ProcessSpace;

public abstract class ExecutionController {
  
  public enum Type {
    Translator,
    Interpreter,
    PredecodingInterpreter,
    StagedEmulation,
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
