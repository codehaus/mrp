/*
 * (C) The University of Manchester 2003 - 2006
 */
package org.jikesrvm.compilers.opt.ir;

/**
 * Interface implemented by all optimizing compiler HIR generators
 * such as OPT_BC2HIR and DBT_HIRGenerator
 */
public interface OPT_HIRGenerator {
  /**
   * Method to create the HIR
   */
  public void generateHIR();
}
