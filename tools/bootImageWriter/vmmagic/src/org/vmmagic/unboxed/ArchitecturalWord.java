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
package org.vmmagic.unboxed;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.RawStorage;

@RawStorage(lengthInWords = true, length = 1)
@Uninterruptible
abstract class ArchitecturalWord {

  private final long value;

  long getValue() {
    if(VM.BuildFor32Addr) {
      return (int)value;
    } else {
      return value;
    }
  }
  ArchitecturalWord() {
    this.value = 0;
  }
  ArchitecturalWord(int value, boolean zeroExtend) {
    this.value = (zeroExtend) ? ((long)value) & 0x00000000ffffffffL : value;
  }

  ArchitecturalWord(long value) {
    this.value = value;
  }

  @Interruptible
  public String toString() {
    return ""+value;
  }

  public int hashCode() {
    return (int)value;
  }

  public boolean equals(Object that) {
    if (that instanceof ArchitecturalWord) {
      return getValue() == ((ArchitecturalWord)that).getValue();
    } else {
      return false;
    }
  }
}
