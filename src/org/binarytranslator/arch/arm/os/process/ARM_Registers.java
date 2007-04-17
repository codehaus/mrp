package org.binarytranslator.arch.arm.os.process;

import org.jikesrvm.VM;

public final class ARM_Registers {

  public final static int SP = 13;
  public final static int LR = 14;
  public final static int PC = 15;

  /**
   * The currently visible ARM general purpose registers. Register 15 also
   * serves as the PC.
   */
  private int regs[] = new int[16];

  /**
   * The ARM features a number of shadow registers, that are mapped into the
   * register map depending on the operating mode. It contains 8 registers for
   * fast IRQ handlers 3 registers for SWI handlers 3 registers for abort
   * handlers 3 registers for irq handlers 3 registers for undefined instruction
   * handlers 8 registers for temporarely storing the user mode registers during
   * non-user modes
   */
  @SuppressWarnings("unused")
  private int shadowRegisters[] = new int[28];

  /**
   * The negative flag from the CPSR.
   */
  private boolean flagNegative = false;

  /**
   * The zero flag from the CPSR.
   */
  private boolean flagZero = false;

  /**
   * The carry flag from the CPSR.
   */
  private boolean flagCarry = false;

  /**
   * The overflow flag from the CPSR.
   */
  private boolean flagOverflow = false;

  /**
   * This flag from the CPSR denotes whether IRQs are currently accepted by the
   * processor.
   */
  private boolean flagIRQsDisabled = false;

  /**
   * This flag from the CPSR denotes whether FIQs are currently accepted by the
   * processor.
   */
  private boolean flagFIQsDisabled = false;

  /**
   * The operating mode from the CPSR register. Note that only the bottom five
   * bits of this register may ever be set.
   */
  private byte operatingMode = OPERATING_MODE_SVC;

  /**
   * Definition of symbolic constants for all valid operating modes
   */
  public final static byte OPERATING_MODE_USR = 0x10;

  public final static byte OPERATING_MODE_FIQ = 0x11;

  public final static byte OPERATING_MODE_IRQ = 0x12;

  public final static byte OPERATING_MODE_SVC = 0x13;

  public final static byte OPERATING_MODE_ABT = 0x17;

  public final static byte OPERATING_MODE_UND = 0x1A;
  
  /** Is the processor currently in thumb mode? */
  private boolean thumbMode = false;

  public ARM_Registers() {
  }

  /** Returns the value of the specified register. */
  public int get(int reg) {
    if (VM.VerifyAssertions)
      VM._assert(reg < 16);

    return regs[reg];
  }

  /** Sets the value of the specified register (<code>reg</code>) to <code>value</code>. */
  public void set(int reg, int value) {
    if (VM.VerifyAssertions)
      VM._assert(reg < 16);

    regs[reg] = value;
  }
  
  /**
   * Restores the saved program status register of the current operating mode to the CPSR, 
   * thereby effectively switching to a different operating mode.
   */
  public void restoreSPSR2CPSR() {
    if (VM.VerifyAssertions) VM._assert(operatingMode != OPERATING_MODE_USR && operatingMode != OPERATING_MODE_SVC);
    
    setFlagsFromCPSR(getSPSR());
  }

  /**
   * Returns the content of ARM's Current Program Status Register.
   * 
   * @return
   */
  public int getCPSR() {
    return (flagNegative ? 1 << 31 : 0) | (flagZero ? 1 << 30 : 0)
        | (flagCarry ? 1 << 29 : 0) | (flagOverflow ? 1 << 28 : 0)
        | (flagIRQsDisabled ? 1 << 7 : 0) | (flagFIQsDisabled ? 1 << 6 : 0)
        | (thumbMode ? 1 << 5 : 0)  | operatingMode;
  }
  
  /** Returns the content of the current mode's Saved Program Status register.*/
  public int getSPSR() {
    throw new RuntimeException("Accessing the SPSR is not yet implemented");
  }

  /**
   * Restores the processor state to the state saved within the given CPSR.
   * 
   * @param cpsr
   *          ARM CPSR register content
   */
  public void setFlagsFromCPSR(int cpsr) {
    throw new RuntimeException("Interpreting a previous SPSR is not yet implemented.");
  }
  
  /**
   * This function switches the processor to either ARM or Thumb mode.
   * 
   * @param enable
   *  Set to true to enable execution of thumb code, false otherwise.
   */
  public void setThumbMode(boolean enable) {
    thumbMode = enable;
  }
  
  /**
   * Sets all flags at once. This operation is very common in the ARm architecture and therefore
   * implemented explicitely.
   * @param negative
   *  The value of the negative flag.
   * @param zero
   *  The value of the zero flag.
   * @param carry
   *  The value of the carry flag.
   * @param overflow
   *  The value of the overflow flag.
   */
  public void setFlags(boolean negative, boolean zero, boolean carry, boolean overflow) {
    flagNegative = negative;
    flagZero = zero;
    flagCarry = carry;
    flagOverflow = overflow;
  }
  
  /**
   * Sets all flags, except for the overflow flag, at once. This operation is very common in the ARm architecture and therefore
   * implemented explicitely.
   * @param negative
   *  The value of the negative flag.
   * @param zero
   *  The value of the zero flag.
   * @param carry
   *  The value of the carry flag.
   */
  public void setFlags(boolean negative, boolean zero, boolean carry) {
    flagNegative = negative;
    flagZero = zero;
    flagCarry = carry;
  }
  
  /**
   * Sets the negative and zero flags at once.
   * @param negative
   *  The value of the negative flag.
   * @param zero
   *  The value of the zero flag.
   */
  public void setFlags(boolean negative, boolean zero) {
    flagNegative = negative;
    flagZero = zero;
  }
  
  /** Returns true if the carry flag is set, false otherwise. */
  public boolean isCarrySet() {
    return flagCarry;
  }

}
