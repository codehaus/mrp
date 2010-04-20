/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
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
package org.jikesrvm.compilers.opt.regalloc.ppc;

import org.jikesrvm.ppc.RegisterConstants;

/**
 * This class holds constants that describe PowerPC register set.
 *
 * @see org.jikesrvm.compilers.opt.RegisterAllocator
 */
public interface PhysicalRegisterConstants extends RegisterConstants {

  // There are different types of hardware registers, so we define
  // the following register classes:
  // NOTE: they must be in consecutive ordering
  // TODO: Kill this?
  byte INT_REG = 0;
  byte DOUBLE_REG = 1;
  byte CONDITION_REG = 2;
  byte SPECIAL_REG = 3;
  byte NUMBER_TYPE = 4;

  // Derived constants for use by the register pool.
  // In the register pool, the physical registers are assigned integers
  // based on these constants.
  int FIRST_INT = 0;
  int FIRST_DOUBLE = NUM_GPRS;
  int FIRST_CONDITION = NUM_GPRS + NUM_FPRS;
  int FIRST_SPECIAL = NUM_GPRS + NUM_FPRS + NUM_CRS;

  // Derived constants for use by the register pool.
  int NUMBER_INT_NONVOLAT = LAST_NONVOLATILE_GPR.value() - FIRST_NONVOLATILE_GPR.value() + 1;
  int NUMBER_DOUBLE_NONVOLAT = LAST_NONVOLATILE_FPR.value() - FIRST_NONVOLATILE_FPR.value() + 1;

  // Derived constants for use by the register pool.
  // These constants give the register pool numbers for parameters
  GPR FIRST_INT_PARAM = GPR.lookup(FIRST_VOLATILE_GPR.value() + FIRST_INT);
  int NUMBER_INT_PARAM = LAST_VOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value() + 1;
  FPR FIRST_DOUBLE_PARAM = FPR.lookup(FIRST_VOLATILE_FPR.value() + FIRST_DOUBLE);
  int NUMBER_DOUBLE_PARAM = LAST_VOLATILE_FPR.value() - FIRST_VOLATILE_FPR.value() + 1;

  // Derived constants for use by the register pool.
  // These constants give the register pool numbers for caller saved registers
  // (or volatile registers, preserved across function calls).
  // NOTE: the order is used by the register allocator
  // TODO: fix this.
  GPR FIRST_INT_RETURN = GPR.lookup(FIRST_VOLATILE_GPR.value() + FIRST_INT);
  int NUMBER_INT_RETURN = 2;
  FPR FIRST_DOUBLE_RETURN = FPR.lookup(FIRST_VOLATILE_FPR.value() + FIRST_DOUBLE);
  int NUMBER_DOUBLE_RETURN = 1;

  // special PowerPC registers
  int XER = FIRST_SPECIAL + 0;     // extended register
  int LR = FIRST_SPECIAL + 1;      // link register
  int CTR = FIRST_SPECIAL + 2;     // count register
  int CR = FIRST_SPECIAL + 3;      // condition register
  int TU = FIRST_SPECIAL + 4;      // time upper
  int TL = FIRST_SPECIAL + 5;      // time lower

}
