/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
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
