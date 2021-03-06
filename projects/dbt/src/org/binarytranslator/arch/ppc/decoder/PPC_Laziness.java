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
package org.binarytranslator.arch.ppc.decoder;

import org.binarytranslator.generic.decoder.Laziness;

/**
 * A representation of the lazy state. This and a PC address define what a block
 * of code is good for. We don't use lazy evaluation for the PowerPC decoder.
 */
public final class PPC_Laziness extends Laziness {
  /**
   * Key for when laziness is stored in a hash table along with a PC
   */
  class PPC_LazinessKey extends Key {
    int pc;

    public int hashCode() {
      return pc;
    }

    public boolean equals(Object o) {
      return ((o instanceof PPC_LazinessKey) && ((PPC_LazinessKey) o).pc == pc);
    }

    PPC_LazinessKey(int pc) {
      this.pc = pc;
    }

    public String toString() {
      return "0x" + Integer.toHexString(pc);
    }
  }

  /**
   * Default constructor - nothing is lazy
   */
  PPC_Laziness() {
  }

  /**
   * Clone the object
   */
  public Object clone() {
    return new PPC_Laziness();
  }

  /**
   * Do these two lazy objects encode a sympathetic lazy state
   * 
   * @param other
   *          the other lazy object
   * @return true if lazy states are equivalent
   */
  public boolean equivalent(Laziness other) {
    return other instanceof PPC_Laziness;
  }

  /**
   * Generate a key value encoding this laziness and a PC value
   * 
   * @parm pc the PC value we're trying to make a key for
   */
  public Key makeKey(int pc) {
    return new PPC_LazinessKey(pc);
  }

  /**
   * Plant instructions modifying a lazy state into one with no laziness for the
   * specified condition register field
   * 
   * @param crf
   *          the condition register field
   */
  public void resolveCrField(int crf) {
  }

  /**
   * Plant instructions modifying a lazy state into one with no laziness
   */
  public void resolve() {
  }
}
