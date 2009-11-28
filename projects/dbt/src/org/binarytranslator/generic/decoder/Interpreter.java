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
package org.binarytranslator.generic.decoder;

/**
 * TODO: Add comments for this interface.
 *
 */
public interface Interpreter {
  
  public interface Instruction {
    
    void execute();
    int getSuccessor(int pc);
  }
  
  Instruction decode(int pc);
}
