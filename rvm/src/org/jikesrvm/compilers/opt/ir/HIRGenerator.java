/*
 * (C) The University of Manchester 2003 - 2006
 */
package org.jikesrvm.compilers.opt.ir;

/**
 * Interface implemented by all optimizing compiler HIR generators
 * such as BC2IR
 */
public interface HIRGenerator {
  /**
   * Method to create the HIR
   */
  public void generateHIR();
}
