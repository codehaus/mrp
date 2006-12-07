/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.ppc.decoder;

import org.binarytranslator.generic.decoder.Laziness;

import com.ibm.jikesrvm.VM;

/**
 * A representation of the lazy state. This and a PC address define
 * what a block of code is good for. We don't use lazy evaluation for
 * the PowerPC decoder.
 */
public final class PPC_Laziness extends Laziness {
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
     * @param other the other lazy object
     * @return true if lazy states are equivalent
     */
    public boolean equivalent(Laziness other) {
        return other instanceof PPC_Laziness;
    }
    /**
     * Generate a key value encoding this laziness and a PC value
     * @parm pc the PC value we're trying to make a key for
     */
    public Object makeKey(int pc) {
        class Key {
            int pc;
            public int hashCode() {
                return pc;
            }
            public boolean equals(Object o) {
                return ((o instanceof Key) && ((Key)o).pc == pc);
            }
            Key (int pc) {
                this.pc = pc;
            }
            public String toString() {
                return "0x" + Integer.toHexString(pc);
            }
        }
        return new Key(pc);
    }

    /**
     * Plant instructions modifying a lazy state into one with no
     * laziness for the specified condition register field
     * @param crf the condition register field
     */
    public void resolveCrField(int crf) {
    }

    /**
     * Plant instructions modifying a lazy state into one with no
     * laziness
     */
    public void resolve() {
    }
}
