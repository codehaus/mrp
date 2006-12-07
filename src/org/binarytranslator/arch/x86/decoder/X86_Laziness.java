/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.x86.decoder;

import org.binarytranslator.generic.decoder.Laziness;

/**
 * Capture lazy information for X86
 */
public class X86_Laziness extends Laziness {
  /**
   * In this state, is the 32bit register valid? (default)
   */
  private static final int EXX_VALID = 0;
  /**
   * In this state, are 8bit registers valid?
   */
  private static final int XL_XH_VALID = 1;
  /**
   * In this state, is the 16bit register valid?
   */
  private static final int XX_VALID = 2;

  /**
   * The status of the registers
   */
  private int mangledRegisterState;

  /**
   * Constructor
   */
  X86_Laziness() {
    mangledRegisterState = 0;
  }
  /**
   * Copy constructor
   */
  X86_Laziness(X86_Laziness other) {
    mangledRegisterState = other.mangledRegisterState;
  }

  /**
   * Do two lazy states encode the same meaning?
   */
  public boolean equivalent(Laziness other) {
    if(other instanceof X86_Laziness) {
      return  mangledRegisterState == ((X86_Laziness)other).mangledRegisterState;
    }
    else {
      return false;
    }
  }

  /**
   * Create a copy of this lazy state - usually to record where one
   * translation was upto whilst we mutate another lazy state.
   */
  public Object clone() {
    return new X86_Laziness(this);
  }

  /**
   * Given the current program position make a key object that will
   * allow 
   */
  public Object makeKey(int pc) {
    return new Long(((long)mangledRegisterState << 32) | pc);
  }

  /**
   * Change the state for a register that its 8 bit value is valid
   */
  void set8bitRegisterValid(int r) {
    mangledRegisterState &= ~(3 << (r * 2));
    mangledRegisterState |= XL_XH_VALID << (r * 2);
  }
  /**
   * Change the state for a register that its 16 bit value is valid
   */
  void set16bitRegisterValid(int r) {
    mangledRegisterState &= ~(3 << (r * 2));
    mangledRegisterState |= XX_VALID << (r * 2);
  }
  /**
   * Change the state for a register that its 32 bit value is valid
   */
  void set32bitRegisterValid(int r) {
    mangledRegisterState &= ~(3 << (r * 2));
    mangledRegisterState |= EXX_VALID << (r * 2);
  }
  /**
   * Is the state of a register that its 32 bit value is valid?
   */
  boolean is32bitRegisterValid(int r) {
    return ((mangledRegisterState >> (r * 2)) & 3) == EXX_VALID;    
  }
  /**
   * Is the state of a register that its 32 bit value is valid?
   */
  boolean is16bitRegisterValid(int r) {
    return ((mangledRegisterState >> (r * 2)) & 3) == XX_VALID;    
  }
  /**
   * Is the state of a register that its 8 bit value is valid?
   */
  boolean is8bitRegisterValid(int r) {
    return ((mangledRegisterState >> (r * 2)) & 3) == XL_XH_VALID;    
  }
}


