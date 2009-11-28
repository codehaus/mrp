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
package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.generic.decoder.Laziness;

public final class ARM_Laziness extends Laziness {
  
  public enum Operation {
    Add, Sub, LogicalOpAfterAdd, LogicalOpAfterSub
  }
  
  public enum Flag {
    Carry, Zero, Negative, Overflow
  }
  
  /** The state of the different ARM flags, compressed as bit fields within an integer. */
  private int validFlags;
  
  /** The operation that has to be performed to evaluate the remaining registers */
  private Operation lazinessOperation;
  
  final class ARM_LazinessKey extends Key {
    private final int pc;
    private final byte flagState;

    public int hashCode() {
      return pc | flagState;
    }

    public boolean equals(Object o) {
      if (!(o instanceof ARM_LazinessKey))
        return false;
      
      ARM_LazinessKey otherKey = (ARM_LazinessKey)o;
      return otherKey.pc == pc && otherKey.flagState == flagState;
    }

    ARM_LazinessKey(int pc, ARM_Laziness lazy) {
      this.pc = pc;
      int tmpFlagState = lazy.validFlags & 0xF;
      tmpFlagState |= (lazinessOperation.ordinal() + 1) << 4;
      
      this.flagState = (byte)tmpFlagState;
    }

    public String toString() {
      return String.format("0x%x (%d)", pc, flagState);
    }
  }
  
  public ARM_Laziness() {
    validFlags = 0xF; //all flags are valid
    lazinessOperation = Operation.Add;
  }
  
  private ARM_Laziness(ARM_Laziness other) {
    set(other);
  }
  
  public void setValid(Flag flag, boolean valid) {
    if (valid)
      validFlags |= 1 << flag.ordinal();
    else
      validFlags &= ~(1 << flag.ordinal()); 
  }

  public boolean isValid(Flag flag) {
    return (validFlags & (1 << flag.ordinal())) != 0;
  }

  public Operation getOperation() {
    return lazinessOperation;
  }
  
  public void setOperation(Operation lazinessOperation) {
    this.lazinessOperation = lazinessOperation;
  }
  
  public void set(ARM_Laziness other) {
    validFlags = other.validFlags;
    lazinessOperation = other.lazinessOperation;
  }
 
  @Override
  public Object clone() {
    return new ARM_Laziness(this);
  }

  @Override
  public boolean equivalent(Laziness o) {
    if (!(o instanceof ARM_Laziness))
      return false;
    
    ARM_Laziness other = (ARM_Laziness)o;
    return validFlags == other.validFlags && lazinessOperation == other.lazinessOperation;
  }

  @Override
  public Key makeKey(int pc) {
    return new ARM_LazinessKey(pc, this);
  }

  @Override
  public String toString() {
    return "Operation: " + lazinessOperation + ", C:" + (isValid(Flag.Carry) ? "1" : "0")
     + ", Z:" + (isValid(Flag.Zero) ? "1" : "0")
      + ", N:" + (isValid(Flag.Negative) ? "1" : "0")
       + ", O:" + (isValid(Flag.Overflow) ? "1" : "0");
  }
}
