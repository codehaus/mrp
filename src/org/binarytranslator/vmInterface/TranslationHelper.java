/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.vmInterface;

import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.*;

/**
 * Translation helper interface
 */
public interface TranslationHelper {
  /**
   * Append a HIR instruction to the current basic block
   * 
   * @param i
   *          the HIR instruction
   */
  public void appendInstructionToCurrentBlock(OPT_Instruction i);

  /**
   * Get the generation context.
   */
  public OPT_GenerationContext getGenerationContext();

  /**
   * Get the method being translated
   */
  public VM_Method getMethod();

  /**
   * Make a temporary register
   */
  public OPT_RegisterOperand makeTemp(VM_TypeReference type);

  /**
   * Get the block which is currently having instructions inserted into it
   * 
   * @return the current block
   */
  public OPT_BasicBlock getCurrentBlock();

  /**
   * Set the block which is currently having instructions inserted into it
   * 
   * @param newCurrentBlock
   *          the new current basic block
   */
  public void setCurrentBlock(OPT_BasicBlock newCurrentBlock);

  /**
   * Get the block which will contain the translation of the next instruction
   * 
   * @return the next block
   */
  public OPT_BasicBlock getNextBlock();

  /**
   * Create a basic block immediately after the current block and link its edges
   * into the CFG and code ordering
   * 
   * @return the new basic block
   */
  public OPT_BasicBlock createBlockAfterCurrent();

  /**
   * Generate a branch profile operand for the current instruction
   * 
   * @param likely
   *          does this branch have a likely hint
   */
  public OPT_BranchProfileOperand getConditionalBranchProfileOperand(
      boolean likely);
}
