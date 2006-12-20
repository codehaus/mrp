/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import com.ibm.jikesrvm.opt.ir.*;
import com.ibm.jikesrvm.VM_ObjectModel;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * OPT compiler additions to VM_ObjectModel
 * 
 * @author Stephen Fink
 * @author Dave Grove
 */
public class OPT_ObjectModel extends VM_ObjectModel {

  /**
   * Mutate a GET_OBJ_TIB instruction to the LIR
   * instructions required to implement it.
   * 
   * @param s the GET_OBJ_TIB instruction to lower
   * @param ir the enclosing OPT_IR
   */
  public static void lowerGET_OBJ_TIB(OPT_Instruction s, OPT_IR ir) { 
    OPT_JavaHeader.lowerGET_OBJ_TIB(s, ir);
  }
}