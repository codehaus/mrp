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
package org.binarytranslator.arch.x86.decoder;

public class X86_RegisterSyncLaziness {
  private int registerSyncLaziness;

  public static final int NOT_LAZY = 0;

  public static final int XL_VALID = 1;

  public static final int XH_VALID = 2;

  public static final int XX_VALID = 4;

  public static final int EXX_VALID = 8;

  X86_RegisterSyncLaziness() {
  }

  X86_RegisterSyncLaziness(int registerSyncLaziness) {
    this.registerSyncLaziness = registerSyncLaziness;
  }

  public Object clone() {
    return new X86_RegisterSyncLaziness(registerSyncLaziness);
  }

  boolean equivalent(X86_RegisterSyncLaziness other) {
    return this.registerSyncLaziness == other.registerSyncLaziness;
  }

  public void setRegisterSyncLaziness(int regField, int lazy) {
    registerSyncLaziness &= ~(0x0000000f << (regField * 4));
    registerSyncLaziness |= lazy << (regField * 4);
  }

  public int getRegisterSyncLaziness(int regField) {
    return registerSyncLaziness >>> (regField * 4);
  }

  public Object makeKey(int pc) {
    class Key {
      int pc, registerSyncLaziness;

      public int hashCode() {
        return pc;
      }

      public boolean equals(Object o) {
        return (o instanceof Key) && (((Key) o).pc == this.pc)
            && (((Key) o).registerSyncLaziness == this.registerSyncLaziness);
      }

      Key(int pc, int registerSyncLaziness) {
        this.pc = pc;
        this.registerSyncLaziness = registerSyncLaziness;
      }

      public String toString() {
        return "0x" + Integer.toHexString(this.pc) + ".0x"
            + Integer.toHexString(this.registerSyncLaziness);
      }
    }
    return new Key(pc, this.registerSyncLaziness);
  }
}
