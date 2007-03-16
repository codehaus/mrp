/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.vmInterface;

import org.jikesrvm.opt.OPT_OptimizingCompilerException;

/**
 * Use this exception if we encounter a runtime error in the binary translator
 *
 * @author Ian Rogers
 */
public class DBT_OptimizingCompilerException {
    public static void UNREACHABLE() {
	OPT_OptimizingCompilerException.UNREACHABLE();
    }
    public static void TODO() {
	OPT_OptimizingCompilerException.TODO();
    }
}
