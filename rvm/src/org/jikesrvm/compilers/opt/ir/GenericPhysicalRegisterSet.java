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
package org.jikesrvm.compilers.opt.ir;

import java.util.Enumeration;
import org.jikesrvm.architecture.MachineRegister;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.util.BitSetMapping;
import org.jikesrvm.compilers.opt.util.ReverseEnumerator;

/**
 * This class represents a set of Registers corresponding to the
 * physical register set. This class holds the architecture-independent
 * functionality
 *
 * <P> Implementation Note: Each register has an integer field
 * Register.number.  This class must number the physical registers so
 * that get(n) returns an Register r with r.number = n!
 */
public abstract class GenericPhysicalRegisterSet implements BitSetMapping {
  /**
   * Return the total number of physical registers.
   */
  public static int getSize() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet.getSize();
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet.getSize();
    }
  }

  /**
   * Get the register name for a register with a particular number in the
   * pool
   */
  public static String getName(int regnum) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet.getName(regnum);
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet.getName(regnum);
    }
  }

  public static int getPhysicalRegisterType(Register symbReg) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet.getPhysicalRegisterType(symbReg);
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet.getPhysicalRegisterType(symbReg);
    }
  }

  public static int getSpillSize(int type) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet.getSpillSize(type);
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet.getSpillSize(type);
    }
  }

  public org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet asIA32() {
    return (org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet)this;
  }

  public org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet asPPC() {
    return (org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet)this;
  }

  /**
   * Is a particular register subject to allocation?
   */
  public abstract boolean isAllocatable(Register p);

  /**
   * Return the total number of physical registers.
   */
  public abstract int getNumberOfPhysicalRegisters();

  /**
   * @return the FP register
   */
  public abstract Register getFP();

  /**
   * @return the thread register
   */
  public abstract Register getTR();

  /**
   * @return the nth physical GPR
   */
  public abstract Register getGPR(int n);

  /**
   * @return the physical GPR corresponding to n
   */
  public abstract Register getGPR(MachineRegister n);

  /**
   * @return the first GPR return
   */
  public abstract Register getFirstReturnGPR();

  /**
   * @return the nth physical FPR
   */
  public abstract Register getFPR(int n);

  /**
   * @return the nth physical register in the pool.
   */
  public abstract Register get(int n);

  /**
   * Enumerate all the physical registers in this set.
   */
  public abstract Enumeration<Register> enumerateAll();

  /**
   * Enumerate all the GPRs in this set.
   */
  public abstract Enumeration<Register> enumerateGPRs();

  /**
   * Enumerate all the volatile GPRs in this set.
   */
  public abstract Enumeration<Register> enumerateVolatileGPRs();

  /**
   * Enumerate all the nonvolatile GPRs in this set.
   */
  public abstract Enumeration<Register> enumerateNonvolatileGPRs();

  /**
   * Enumerate all the volatile FPRs in this set.
   */
  public abstract Enumeration<Register> enumerateVolatileFPRs();

  /**
   * Enumerate all the nonvolatile FPRs in this set.
   */
  public abstract Enumeration<Register> enumerateNonvolatileFPRs();

  /**
   * Enumerate all the volatile physical registers
   */
  public abstract Enumeration<Register> enumerateVolatiles();

  /**
   * Enumerate volatiles of the given type
   */
  public abstract Enumeration<Register> enumerateVolatiles(int type);

  /**
   * Enumerate nonvolatiles of the given type, backwards
   */
  public abstract Enumeration<Register> enumerateNonvolatilesBackwards(int type);

  /**
   * Enumerate all the nonvolatile GPRs in this set, backwards
   */
  public Enumeration<Register> enumerateNonvolatileGPRsBackwards() {
    return new ReverseEnumerator<Register>(enumerateNonvolatileGPRs());
  }

  /**
   * Enumerate all the nonvolatile FPRs in this set, backwards.
   */
  public Enumeration<Register> enumerateNonvolatileFPRsBackwards() {
    return new ReverseEnumerator<Register>(enumerateNonvolatileFPRs());
  }

  /**
   * Implementation of the BitSetMapping interface.
   */
  public final Object getMappedObject(int n) {
    return get(n);
  }

  /**
   * Implementation of the BitSetMapping interface.
   */
  public final int getMappedIndex(Object o) {
    Register r = (Register) o;
    return r.number;
  }

  /**
   * Implementation of the BitSetMapping interface.
   */
  public final int getMappingSize() {
    return getNumberOfPhysicalRegisters();
  }
}
