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
package org.binarytranslator.arch.x86.decoder;

import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.decoder.CodeTranslator;
import org.binarytranslator.generic.fault.BadInstructionException;

/**
 * A decoder is a class capable of translating, interpretting or disassembling
 * an instruction
 */
public abstract class X86_AbstractInstructionDecoder {
  /**
   * Translate a single instruction
   * 
   * @param translationHelper
   *          the object containing the translation sequence
   * @param ps
   *          the process space of the translation
   * @param pc
   *          the address of the instruction to translate
   * @return the address of the next instruction or -1 if this instruction has
   *         branched to the end of the trace
   */
  public int translate(CodeTranslator translationHelper, ProcessSpace ps,
      Object lazy, int pc) {
    throw new Error("TODO");
  }

  /**
   * Interpret a single instruction
   * 
   * @param ps
   *          the process space of the interpretation, contains the fetched
   *          instruction and instruction address
   * @return the next instruction interpreter
   */
  public X86_AbstractInstructionDecoder interpret(ProcessSpace ps)
      throws BadInstructionException {
    throw new Error("TODO");
  }

  /**
   * Disassemble an instruction
   * 
   * @param ps
   */
  public String disassemble(ProcessSpace ps) {
    // In general this isn't complete
    throw new Error("Disassembly not yet complete");
  }
}
