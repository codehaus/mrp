/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.VM_TypeReference;

/**
 * A TypeOperand represents a type.
 * Used in checkcast, instanceof, new, etc.
 * It will contain either a RVMType (if the type can be resolved
 * at compile time) or a VM_TypeReference (if the type cannot be resolved
 * at compile time).
 *
 * @see Operand
 * @see RVMType
 * @see VM_TypeReference
 */
public final class TypeOperand extends Operand {

  /**
   * A type
   */
  private final RVMType type;

  /**
   * The data type.
   */
  private final VM_TypeReference typeRef;

  /**
   * Create a new type operand with the specified type.
   */
  public TypeOperand(RVMType typ) {
    type = typ;
    typeRef = type.getTypeRef();
  }

  /**
   * Create a new type operand with the specified type reference
   */
  public TypeOperand(VM_TypeReference tr) {
    type = tr.peekType();
    typeRef = tr;
  }

  private TypeOperand(RVMType t, VM_TypeReference tr) {
    type = t;
    typeRef = tr;
  }

  /**
   * Return the {@link VM_TypeReference} of the value represented by the operand.
   *
   * @return VM_TypeReference.VM_Type
   */
  public VM_TypeReference getType() {
    return VM_TypeReference.VM_Type;
  }

  /**
   * @return the VM_TypeReference for this type operand
   */
  public VM_TypeReference getTypeRef() {
    return typeRef;
  }

  /**
   * @return the RVMType for this type operand -- may be null
   */
  public RVMType getVMType() {
    return type;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  public Operand copy() {
    return new TypeOperand(type, typeRef);
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  public boolean similar(Operand op) {
    if (op instanceof TypeOperand) {
      TypeOperand that = (TypeOperand) op;
      return type == that.type && typeRef == that.typeRef;
    } else {
      return false;
    }
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  public String toString() {
    if (type != null) {
      return type.toString();
    } else {
      return typeRef.getName().toString();
    }
  }
}
