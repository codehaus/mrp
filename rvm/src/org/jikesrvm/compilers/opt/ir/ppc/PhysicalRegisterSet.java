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
package org.jikesrvm.compilers.opt.ir.ppc;

import java.util.Enumeration;
import org.jikesrvm.architecture.MachineRegister;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants;
import org.jikesrvm.compilers.opt.util.BitSet;
import org.jikesrvm.compilers.opt.util.CompoundEnumerator;
import org.jikesrvm.compilers.opt.util.EmptyEnumerator;
import org.jikesrvm.compilers.opt.util.ReverseEnumerator;
import org.jikesrvm.ppc.RegisterConstants;

/**
 * This class represents a set of Registers corresponding to the
 * PowerPC register set.
 *
 * <P> Implementation Notes:
 * <P> Due to some historical ugliness not yet cleaned up, the register
 * allocator depend on properties cached in the
 * <code>next</code> field of Register.  The constructor sets the
 * following properties:
 * <ul>
 * <li> The volatile GPRs form a linked list, starting with
 * FIRST_VOLATILE_GPR and ending with LAST_SCRATCH_GPR
 * <li> The volatile FPRs form a linked list, starting with
 * FIRST_VOLATILE_FPR and ending with LAST_SCRATCH_FPR
 * <li> The non-volatile GPRs form a linked list, starting with
 * FIRST_NONVOLATILE_GPR and ending with LAST_NONVOLATILE_GPR
 * <li> The non-volatile FPRs form a linked list, starting with
 * FIRST_NONVOLATILE_FPR and ending with LAST_NONVOLATILE_FPR
 * <li> The condition registers from a linked list, starting with
 * FIRST_CONDITION and ending with LAST_CONDITION-1, which opt reserves for yieldpoints.
 * </ul>
 * <P> The register allocator allocates registers according to the order
 * in these lists.  For volatile registers, it traverses the lists in
 * order, starting with getFirstVolatile() and traversing with getNext().
 * For non-volatiles, it traverses the lists
 * <STRONG> Backwards </STRONG>, starting with getLastNonvolatile() and
 * using getPrev().
 * <P> TODO; clean up all this and provide appropriate enumerators
 */
public final class PhysicalRegisterSet extends GenericPhysicalRegisterSet
    implements RegisterConstants, PhysicalRegisterConstants {

  /**
   * This array holds a pool of objects representing physical registers
   */
  private Register[] reg = new Register[getSize()];

  /**
   * The set of volatile registers; cached for efficiency
   */
  private BitSet volatileSet;

  /**
   * The condition registers that we allocate
   * To avoid expensive save/restores when
   * making a JNI transition Jikes RVM only uses the
   * CR that AIX defines to be volatile
   *
   * We reserve one of the volatiles, CR7 for use only in yieldpoints.
   * This ensures that a yieldpoint won't bash an allocated CR.
   */
  private static final int[] CR_NUMS = new int[]{0, 1, 5, 6};
  private static final int TSR_REG = 7;

  /**
   * Return the total number of physical registers.
   */
  public static int getSize() {
    return NUM_GPRS + NUM_FPRS + NUM_CRS + NUM_SPECIALS;
  }

  /**
   * Return the total number of physical registers.
   */
  public int getNumberOfPhysicalRegisters() {
    return getSize();
  }

  /**
   * Return the total number of nonvolatile GPRs.
   */
  public static int getNumberOfNonvolatileGPRs() {
    return NUM_NONVOLATILE_GPRS;
  }

  /**
   * Return the total number of nonvolatile FPRs.
   */
  public static int getNumberOfNonvolatileFPRs() {
    return NUM_NONVOLATILE_FPRS;
  }

  /**
   * Constructor: set up a pool of physical registers.
   */
  public PhysicalRegisterSet() {

    // 1. Create all the physical registers in the pool.
    for (int i = 0; i < reg.length; i++) {
      Register r = new Register(i);
      r.setPhysical();
      reg[i] = r;
    }

    // 2. Set the 'integer' attribute on each GPR
    for (int i = FIRST_INT; i < FIRST_DOUBLE; i++) {
      if (VM.BuildFor32Addr) {
        reg[i].setInteger();
      } else {
        reg[i].setLong();
      }
    }

    // 3. Set the 'double' attribute on each FPR
    for (int i = FIRST_DOUBLE; i < FIRST_CONDITION; i++) {
      reg[i].setDouble();
    }

    // 4. Set the 'condition' attribute on each CR
    for (int i = FIRST_CONDITION; i < FIRST_SPECIAL; i++) {
      reg[i].setCondition();
    }

    // 5. set up the volatile GPRs
    for (int i = FIRST_VOLATILE_GPR.value(); i < LAST_VOLATILE_GPR.value(); i++) {
      Register r = reg[i];
      r.setVolatile();
      r.linkWithNext(reg[i + 1]);
    }
    reg[LAST_VOLATILE_GPR.value()].setVolatile();
    reg[LAST_VOLATILE_GPR.value()].linkWithNext(reg[FIRST_SCRATCH_GPR.value()]);
    for (int i = FIRST_SCRATCH_GPR.value(); i < LAST_SCRATCH_GPR.value(); i++) {
      Register r = reg[i];
      r.setVolatile();
      r.linkWithNext(reg[i + 1]);
    }
    reg[LAST_SCRATCH_GPR.value()].setVolatile();

    // 6. set up the non-volatile GPRs
    for (int i = FIRST_NONVOLATILE_GPR.value(); i < LAST_NONVOLATILE_GPR.value(); i++) {
      Register r = reg[i];
      r.setNonVolatile();
      r.linkWithNext(reg[i + 1]);
    }

    // 7. set properties on some special registers
    reg[THREAD_REGISTER.value()].setSpansBasicBlock();
    reg[FRAME_POINTER.value()].setSpansBasicBlock();
    reg[JTOC_POINTER.value()].setSpansBasicBlock();

    // 8. set up the volatile FPRs
    for (int i = FIRST_DOUBLE + FIRST_VOLATILE_FPR.value();
         i < FIRST_DOUBLE + LAST_VOLATILE_FPR.value(); i++) {
      Register r = reg[i];
      r.setVolatile();
      r.linkWithNext(reg[i + 1]);
    }
    reg[FIRST_DOUBLE + LAST_VOLATILE_FPR.value()].linkWithNext(reg[FIRST_DOUBLE + FIRST_SCRATCH_FPR.value()]);
    reg[FIRST_DOUBLE + LAST_VOLATILE_FPR.value()].setVolatile();
    if (FIRST_SCRATCH_FPR != LAST_SCRATCH_FPR) {
      for (int i = FIRST_DOUBLE + FIRST_SCRATCH_FPR.value();
           i < FIRST_DOUBLE + LAST_SCRATCH_FPR.value(); i++) {
        Register r = reg[i];
        r.setVolatile();
        r.linkWithNext(reg[i + 1]);
      }
    }
    reg[FIRST_DOUBLE + LAST_SCRATCH_FPR.value()].setVolatile();

    // 9. set up the non-volatile FPRs
    for (int i = FIRST_DOUBLE + FIRST_NONVOLATILE_FPR.value();
         i < FIRST_DOUBLE + LAST_NONVOLATILE_FPR.value(); i++) {
      Register r = reg[i];
      r.setNonVolatile();
      r.linkWithNext(reg[i + 1]);
    }

    // 10. set up the condition registers
    int firstCR = -1;
    int prevCR = -1;
    for (int i : CR_NUMS) {
      reg[FIRST_CONDITION + i].setVolatile();
      if (prevCR != -1) {
        reg[FIRST_CONDITION + prevCR].linkWithNext(reg[FIRST_CONDITION + i]);
      }
      prevCR = i;
      if (firstCR == -1) {
        firstCR = i;
      }
    }

    // 11. cache the volatiles for efficiency
    volatileSet = new BitSet(this);
    for (Enumeration<Register> e = enumerateVolatiles(); e.hasMoreElements();) {
      Register r = e.nextElement();
      volatileSet.add(r);
    }

    // 12. Show which registers should be excluded from live analysis
    reg[CTR].setExcludedLiveA();
    reg[CR].setExcludedLiveA();
    reg[TU].setExcludedLiveA();
    reg[TL].setExcludedLiveA();
    reg[XER].setExcludedLiveA();
    reg[FRAME_POINTER.value()].setExcludedLiveA();
    reg[JTOC_POINTER.value()].setExcludedLiveA();
    reg[LR].setExcludedLiveA();
  }

  /**
   * Is a certain physical register allocatable?
   */
  public boolean isAllocatable(Register r) {
    if (r.number == THREAD_REGISTER.value() ||
        r.number == FRAME_POINTER.value() ||
        r.number == JTOC_POINTER.value()) {
      return false;
    } else {
      return (r.number < FIRST_SPECIAL);
    }
  }

  /**
   * @return the XER register.
   */
  public Register getXER() {
    return reg[XER];
  }

  /**
   * @return the LR register;.
   */
  public Register getLR() {
    return reg[LR];
  }

  /**
   * @return the CTR register
   */
  public Register getCTR() {
    return reg[CTR];
  }

  /**
   * @return the TU register
   */
  public Register getTU() {
    return reg[TU];
  }

  /**
   * @return the TL register
   */
  public Register getTL() {
    return reg[TL];
  }

  /**
   * @return the CR register
   */
  public Register getCR() {
    return reg[CR];
  }

  /**
   * @return the JTOC register
   */
  public Register getJTOC() {
    return reg[JTOC_POINTER.value()];
  }

  /**
   * @return the FP registers
   */
  public Register getFP() {
    return reg[FRAME_POINTER.value()];
  }

  /**
   * @return the thread register
   */
  public Register getTR() {
    return reg[THREAD_REGISTER.value()];
  }

  /**
   * @return the thread-switch register
   */
  public Register getTSR() {
    return reg[FIRST_CONDITION + TSR_REG];
  }

  /**
   * @return the nth physical GPR
   */
  @Override
  public Register getGPR(int n) {
    return reg[FIRST_INT + n];
  }

  /**
   * @return the nth physical GPR
   */
  @Override
  public Register getGPR(MachineRegister n) {
    return reg[FIRST_INT + n.value()];
  }

  /**
   * @return the first scratch GPR
   */
  public Register getFirstScratchGPR() {
    return reg[FIRST_SCRATCH_GPR.value()];
  }

  /**
   * @return the last scratch GPR
   */
  public Register getLastScratchGPR() {
    return reg[LAST_SCRATCH_GPR.value()];
  }

  /**
   * @return the first volatile GPR
   */
  public Register getFirstVolatileGPR() {
    return reg[FIRST_INT + FIRST_VOLATILE_GPR.value()];
  }

  /**
   * @return the first nonvolatile GPR
   */
  public Register getFirstNonvolatileGPR() {
    return reg[FIRST_INT + FIRST_NONVOLATILE_GPR.value()];
  }

  /**
   * @return the last nonvolatile GPR
   */
  public Register getLastNonvolatileGPR() {
    return reg[FIRST_INT + LAST_NONVOLATILE_GPR.value()];
  }

  /**
   * @return the first GPR return
   */
  public Register getFirstReturnGPR() {
    return reg[FIRST_INT_RETURN.value()];
  }

  /**
   * @return the nth physical FPR
   */
  public Register getFPR(int n) {
    return reg[FIRST_DOUBLE + n];
  }

  /**
   * @return the first scratch FPR
   */
  public Register getFirstScratchFPR() {
    return reg[FIRST_DOUBLE + FIRST_SCRATCH_FPR.value()];
  }

  /**
   * @return the first volatile FPR
   */
  public Register getFirstVolatileFPR() {
    return reg[FIRST_DOUBLE + FIRST_VOLATILE_FPR.value()];
  }

  /**
   * @return the last scratch FPR
   */
  public Register getLastScratchFPR() {
    return reg[FIRST_DOUBLE + LAST_SCRATCH_FPR.value()];
  }

  /**
   * @return the first nonvolatile FPR
   */
  public Register getFirstNonvolatileFPR() {
    return reg[FIRST_DOUBLE + FIRST_NONVOLATILE_FPR.value()];
  }

  /**
   * @return the last nonvolatile FPR
   */
  public Register getLastNonvolatileFPR() {
    return reg[FIRST_DOUBLE + LAST_NONVOLATILE_FPR.value()];
  }

  /**
   * @return the nth physical condition register
   */
  public Register getConditionRegister(int n) {
    return reg[FIRST_CONDITION + n];
  }

  /**
   * @return the first condition
   */
  public Register getFirstConditionRegister() {
    return reg[FIRST_CONDITION];
  }

  /**
   * @return the first volatile CR
   */
  public Register getFirstVolatileConditionRegister() {
    if (VM.VerifyAssertions) {
      VM._assert(getFirstConditionRegister() != getTSR());
    }
    return getFirstConditionRegister();
  }

  /**
   * @return the nth physical register in the pool.
   */
  public Register get(int n) {
    return reg[n];
  }

  /**
   * @return the first volatile physical register of a given class
   * @param regClass one of INT_REG, DOUBLE_REG, CONDITION_REG, or
   * SPECIAL_REG
   */
  public Register getFirstVolatile(int regClass) {
    switch (regClass) {
      case INT_REG:
        return getFirstVolatileGPR();
      case DOUBLE_REG:
        return getFirstVolatileFPR();
      case CONDITION_REG:
        return getFirstVolatileConditionRegister();
      case SPECIAL_REG:
        return null;
      default:
        throw new OptimizingCompilerException("Unknown register class");
    }
  }

  /**
   * @return the first nonvolatile physical register of a given class
   * @param regClass one of INT_REG, DOUBLE_REG, CONDITION_REG, or
   * SPECIAL_REG
   */
  public Register getLastNonvolatile(int regClass) {
    switch (regClass) {
      case INT_REG:
        return getLastNonvolatileGPR();
      case DOUBLE_REG:
        return getLastNonvolatileFPR();
      case CONDITION_REG:
        return null;
      case SPECIAL_REG:
        return null;
      default:
        throw new OptimizingCompilerException("Unknown register class");
    }
  }

  /**
   * Given a symbolic register, return a cdoe that gives the physical
   * register type to hold the value of the symbolic register.
   * @param r a symbolic register
   * @return one of INT_REG, DOUBLE_REG, or CONDITION_REG
   */
  public static int getPhysicalRegisterType(Register r) {
    if (r.isInteger() || r.isLong() || r.isAddress()) {
      return INT_REG;
    } else if (r.isFloatingPoint()) {
      return DOUBLE_REG;
    } else if (r.isCondition()) {
      return CONDITION_REG;
    } else {
      throw new OptimizingCompilerException("getPhysicalRegisterType " + " unexpected " + r);
    }
  }

  /**
   * Given a physical register (XER, LR, or CTR), return the integer that
   * denotes the PowerPC Special Purpose Register (SPR) in the PPC
   * instruction set.  See p.129 of PPC ISA book
   */
  public byte getSPR(Register r) {
    if (VM.VerifyAssertions) {
      VM._assert((r == getXER()) || (r == getLR()) || (r == getCTR()));
    }
    if (r == getXER()) {
      return 1;
    } else if (r == getLR()) {
      return 8;
    } else if (r == getCTR()) {
      return 9;
    } else {
      throw new OptimizingCompilerException("Invalid SPR");
    }
  }

  /**
   * register names for each class. used in printing the IR
   * The indices for "FP" and "JTOC" should always match the
   * final static values of int FP and int JTOC defined below.
   */
  private static final String[] registerName = new String[getSize()];

  static {
    String[] regName = registerName;
    for (int i = 0; i < NUM_GPRS; i++) {
      regName[i + FIRST_INT] = "R" + i;
    }
    for (int i = 0; i < NUM_FPRS; i++) {
      regName[i + FIRST_DOUBLE] = "F" + i;
    }
    for (int i = 0; i < NUM_CRS; i++) {
      regName[i + FIRST_CONDITION] = "C" + i;
    }
    regName[JTOC_POINTER.value()] = "JTOC";
    regName[FRAME_POINTER.value()] = "FP";
    regName[THREAD_REGISTER.value()] = "TR";
    regName[XER] = "XER";
    regName[LR] = "LR";
    regName[CTR] = "CTR";
    regName[TU] = "TU";
    regName[TL] = "TL";
    regName[CR] = "CR";
  }

  static final int TEMP = FIRST_INT;   // temporary register (currently r0)

  /**
   * @return R0, a temporary register
   */
  public Register getTemp() {
    return reg[TEMP];
  }

  /**
   * Get the register name for a register with a particular number in the
   * pool
   */
  public static String getName(int number) {
    return registerName[number];
  }

  /**
   * Get the spill size for a register with a particular type
   * @param type one of INT_REG, DOUBLE_REG, CONDITION_REG, SPECIAL_REG
   */
  public static int getSpillSize(int type) {
    if (VM.VerifyAssertions) {
      VM._assert((type == INT_REG) || (type == DOUBLE_REG) || (type == CONDITION_REG) || (type == SPECIAL_REG));
    }
    if (type == DOUBLE_REG) {
      return BYTES_IN_DOUBLE;
    } else {
      return BYTES_IN_ADDRESS;
    }
  }

  /**
   * Get the required spill alignment for a register with a particular type
   * @param type one of INT_REG, DOUBLE_REG, CONDITION_REG, SPECIAL_REG
   */
  public static int getSpillAlignment(int type) {
    if (VM.VerifyAssertions) {
      VM._assert((type == INT_REG) || (type == DOUBLE_REG) || (type == CONDITION_REG) || (type == SPECIAL_REG));
    }
    if (type == DOUBLE_REG) {
      return BYTES_IN_DOUBLE;
    } else {
      return BYTES_IN_ADDRESS;
    }
  }

  /**
   * Enumerate all the physical registers in this set.
   */
  public Enumeration<Register> enumerateAll() {
    return new PhysicalRegisterEnumeration(0, getSize() - 1);
  }

  /**
   * Enumerate all the GPRs in this set.
   */
  public Enumeration<Register> enumerateGPRs() {
    return new PhysicalRegisterEnumeration(FIRST_INT, FIRST_DOUBLE - 1);
  }

  /**
   * Enumerate all the volatile GPRs in this set.
   * NOTE: This assumes the scratch GPRs are numbered immediately
   * <em> after </em> the volatile GPRs
   */
  public Enumeration<Register> enumerateVolatileGPRs() {
    return new PhysicalRegisterEnumeration(FIRST_INT + FIRST_VOLATILE_GPR.value(), FIRST_INT + LAST_SCRATCH_GPR.value());
  }

  static {
    // enumerateVolatileGPRs relies on volatiles & scratches being
    // contiguous; so let's make sure that is the case!
    if (VM.VerifyAssertions) {
      VM._assert(LAST_VOLATILE_GPR.value() + 1 == FIRST_SCRATCH_GPR.value());
    }
  }

  /**
   * Enumerate the first n GPR parameters.
   */
  public Enumeration<Register> enumerateGPRParameters(int n) {
    if (VM.VerifyAssertions) {
      VM._assert(n <= NUMBER_INT_PARAM);
    }
    return new PhysicalRegisterEnumeration(FIRST_INT_PARAM.value(), FIRST_INT_PARAM.value() + n - 1);
  }

  /**
   * Enumerate all the nonvolatile GPRs in this set.
   */
  public Enumeration<Register> enumerateNonvolatileGPRs() {
    return new PhysicalRegisterEnumeration(FIRST_INT + FIRST_NONVOLATILE_GPR.value(), FIRST_INT + LAST_NONVOLATILE_GPR.value());
  }

  /**
   * Enumerate the nonvolatile GPRs backwards.
   */
  public Enumeration<Register> enumerateNonvolatileGPRsBackwards() {
    return new ReverseEnumerator<Register>(enumerateNonvolatileGPRs());
  }

  /**
   * Enumerate all the volatile FPRs in this set.
   * NOTE: This assumes the scratch FPRs are numbered immediately
   * <em> before</em> the volatile FPRs
   */
  public Enumeration<Register> enumerateVolatileFPRs() {
    return new PhysicalRegisterEnumeration(FIRST_DOUBLE + FIRST_SCRATCH_FPR.value(), FIRST_DOUBLE + LAST_VOLATILE_FPR.value());
  }

  /**
   * Enumerate the first n FPR parameters.
   */
  public Enumeration<Register> enumerateFPRParameters(int n) {
    if (VM.VerifyAssertions) {
      VM._assert(n <= NUMBER_DOUBLE_PARAM);
    }
    return new PhysicalRegisterEnumeration(FIRST_DOUBLE_PARAM.value(), FIRST_DOUBLE_PARAM.value() + n - 1);
  }

  /**
   * Enumerate all the nonvolatile FPRs in this set.
   */
  public Enumeration<Register> enumerateNonvolatileFPRs() {
    return new PhysicalRegisterEnumeration(FIRST_DOUBLE + FIRST_NONVOLATILE_FPR.value(), FIRST_DOUBLE + LAST_NONVOLATILE_FPR.value());
  }

  /**
   * Enumerate the volatile physical condition registers.
   * Note that the TSR is non-volatile.
   */
  public Enumeration<Register> enumerateVolatileConditionRegisters() {
    return new Enumeration<Register>() {
      private int idx = 0;

      public Register nextElement() {
        return reg[FIRST_CONDITION + CR_NUMS[idx++]];
      }

      public boolean hasMoreElements() {
        return idx < CR_NUMS.length;
      }
    };
  }

  /**
   * Enumerate the non-volatile physical condition registers.
   * Note that only the TSR is non-volatile.
   */
  public Enumeration<Register> enumerateNonvolatileConditionRegisters() {
    return new PhysicalRegisterEnumeration(0, -1);
  }

  /**
   * Enumerate the volatile physical registers of a given class.
   * @param regClass one of INT_REG, DOUBLE_REG, CONDITION_REG
   */
  public Enumeration<Register> enumerateVolatiles(int regClass) {
    switch (regClass) {
      case INT_REG:
        return enumerateVolatileGPRs();
      case DOUBLE_REG:
        return enumerateVolatileFPRs();
      case CONDITION_REG:
        return enumerateVolatileConditionRegisters();
      case SPECIAL_REG:
        return EmptyEnumerator.emptyEnumeration();
      default:
        throw new OptimizingCompilerException("Unsupported volatile type");
    }
  }

  /**
   * Enumerate all the volatile physical registers
   */
  public Enumeration<Register> enumerateVolatiles() {
    Enumeration<Register> e1 = enumerateVolatileGPRs();
    Enumeration<Register> e2 = enumerateVolatileFPRs();
    Enumeration<Register> e3 = enumerateVolatileConditionRegisters();
    return new CompoundEnumerator<Register>(e1, new CompoundEnumerator<Register>(e2, e3));
  }

  /**
   * Return a set of all the volatile registers.
   */
  public BitSet getVolatiles() {
    return volatileSet;
  }

  /**
   * Enumerate the nonvolatile physical registers of a given class.
   * @param regClass one of INT_REG, DOUBLE_REG, CONDITION_REG
   */
  public Enumeration<Register> enumerateNonvolatiles(int regClass) {
    switch (regClass) {
      case INT_REG:
        return enumerateNonvolatileGPRs();
      case DOUBLE_REG:
        return enumerateNonvolatileFPRs();
      case CONDITION_REG:
        return enumerateNonvolatileConditionRegisters();
      case SPECIAL_REG:
        return EmptyEnumerator.emptyEnumeration();
      default:
        throw new OptimizingCompilerException("Unsupported non-volatile type");
    }
  }

  /**
   * Enumerate the nonvolatile physical registers of a given class,
   * backwards.
   */
  public Enumeration<Register> enumerateNonvolatilesBackwards(int regClass) {
    return new ReverseEnumerator<Register>(enumerateNonvolatiles(regClass));
  }

  /**
   * If the passed in physical register r is used as a GPR parameter register,
   * return the index into the GPR parameters for r.  Otherwise, return -1;
   */
  public int getGPRParamIndex(Register r) {
    if ((r.number < FIRST_INT_PARAM.value()) || (r.number > LAST_VOLATILE_GPR.value())) {
      return -1;
    } else {
      return r.number - FIRST_INT_PARAM.value();
    }
  }

  /**
   * If the passed in physical register r is used as an FPR parameter register,
   * return the index into the FPR parameters for r.  Otherwise, return -1;
   */
  public int getFPRParamIndex(Register r) {
    if ((r.number < FIRST_DOUBLE_PARAM.value()) || (r.number > LAST_VOLATILE_FPR.value())) {
      return -1;
    } else {
      return r.number - FIRST_DOUBLE_PARAM.value();
    }
  }

  /**
   * If r is used as the first half of a (long) register pair, return
   * the second half of the pair.
   */
  public Register getSecondHalf(Register r) {
    int n = r.number;
    return get(n + 1);
  }

  /**
   * An enumerator for use by the physical register utilities.
   */
  final class PhysicalRegisterEnumeration implements Enumeration<Register> {
    private int end;
    private int index;

    PhysicalRegisterEnumeration(int start, int end) {
      this.end = end;
      this.index = start;
    }

    public Register nextElement() {
      return reg[index++];
    }

    public boolean hasMoreElements() {
      return (index <= end);
    }
  }
}
