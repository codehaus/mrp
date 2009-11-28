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
package org.binarytranslator.vmInterface;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;

/**
 * Use this exception if we encounter a runtime error in the binary translator
 * 
 * @author Ian Rogers
 */
public class DBT_OptimizingCompilerException {
  public static void UNREACHABLE() {
    OptimizingCompilerException.UNREACHABLE();
  }

  public static void TODO() {
    OptimizingCompilerException.TODO();
  }
}
