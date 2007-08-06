/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.x86.decoder;

public class X86_FlagLaziness {
  private byte[] flagLaziness = new byte[32];

  public static final int NOT_LAZY = 0;

  public static final int UNSIGNED_BYTE = 1;

  public static final int SIGNED_BYTE = 2;

  public static final int UNSIGNED_SHORT = 3;

  public static final int SIGNED_SHORT = 4;

  public static final int UNSIGNED_INT = 5;

  public static final int SIGNED_INT = 6;

  public static final int FP_CMPU = 7;

  X86_FlagLaziness() {
  }

  X86_FlagLaziness(byte[] flagLaziness) {
    System.arraycopy(flagLaziness, 0, this.flagLaziness, 0, flagLaziness.length);
  }

  public Object clone() {
    return new X86_FlagLaziness(flagLaziness);
  }

  boolean equivalent(X86_FlagLaziness other) {
    for (int i = 0; i < flagLaziness.length; i++) {
      if (this.flagLaziness != other.flagLaziness) {
        return false;
      }
    }

    return true;
  }

  public void setConditionFieldLaziness(int field, byte lazy) {
    flagLaziness[field] = lazy;
    // &= ~(0x00000001 << field);
    /*
     * if (4 > lazy) { flagLaziness |= 0x00000001 << field; } else { throw new
     * Error("Invalid lazy state encountered"); }
     */
  }

  public byte getConditionFieldLaziness(int field) {
    return flagLaziness[field];
    // (flagLaziness >>> field) & 0x00000001;
  }

  public Object makeKey(int pc) {
    class Key {
      int pc;

      byte[] flagLaziness = new byte[32];

      public int hashCode() {
        return pc;
      }

      public boolean equals(Object o) {
        if (o instanceof Key) {
          Key other = (Key) o;
          for (int i = 0; i < flagLaziness.length; i++) {
            if (this.flagLaziness[i] != other.flagLaziness[i]) {
              return false;
            }
          }
          return (other.pc == this.pc);
        }

        return false;
      }

      Key(int pc, byte[] flagLaziness) {
        this.pc = pc;

        System.arraycopy(flagLaziness, 0, this.flagLaziness, 0, flagLaziness.length);
      }

      public String toString() {
        String returnString = "";

        for (int i = 0; i < flagLaziness.length; i++) {
          returnString += ".0x" + Integer.toHexString(this.flagLaziness[i]);
        }
        return "0x" + Integer.toHexString(this.pc) + returnString;
      }
    }
    return new Key(pc, this.flagLaziness);
  }
}
