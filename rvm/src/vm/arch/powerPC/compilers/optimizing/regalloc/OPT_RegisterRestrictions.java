/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * An instance of this class encapsulates restrictions on register
 * allocation.
 * 
 * @author Stephen Fink
 */
public final class OPT_RegisterRestrictions extends OPT_GenericRegisterRestrictions {
  /**
   * Default Constructor
   */
  public OPT_RegisterRestrictions(OPT_PhysicalRegisterSet phys) {
    super(phys);
  }
  /**
   * Is it forbidden to assign symbolic register symb to physical register r
   * in instruction s?
   */
  public boolean isForbidden(OPT_Register symb, OPT_Register r,
                             OPT_Instruction s) {
    return false;
  }

}
