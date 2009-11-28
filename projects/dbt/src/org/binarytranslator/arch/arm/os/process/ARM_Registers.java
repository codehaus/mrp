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
package org.binarytranslator.arch.arm.os.process;

import org.binarytranslator.DBT;
import org.jikesrvm.VM;

public final class ARM_Registers {

  /** Symbolic constants for the registers in ARM that have a special function. 
   * Note that (except for the PC), those registers can also be used as general purpose registers*/
  public final static byte FP = 11; //frame pointer
  public final static byte SP = 13; //stack pointer
  public final static byte LR = 14; //link register
  public final static byte PC = 15; //program counter

  /**
   * The currently visible ARM general purpose registers. Register 15 also
   * serves as the PC.
   */
  private int regs[] = new int[16];

  /**
   * The ARM features a number of shadow registers, that are mapped into the
   * register map depending on the operating mode. It contains 3 registers for SWI handlers, 
   * 3 registers for abort handlers, 3 registers for irq handlers, 3 registers for undefined instruction
   * handlers, 8 registers for fast IRQ handlers and 7 registers to store the user/system mode registers r8-r14.
   * 
   * The registers are contained in the said order. All modes (except for the user mode) have their SPSR
   * stored as the third element (from the beginning of their registers) in this table.
   */
  @SuppressWarnings("unused")
  private int shadowRegisters[] = new int[27];
  
  /** As mentioned above, each mode has its SPSR stored as the third element within the {@link #shadowRegisters}
   *  table. This constant is a "human-redable" representation of this offset. */
  private final static int SPSR_OFFSET = 2;

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
  private boolean flagIRQsDisabled = true;

  /**
   * This flag from the CPSR denotes whether FIQs are currently accepted by the
   * processor.
   */
  private boolean flagFIQsDisabled = true;

  /**
   * The operating mode from the CPSR register. Note that only the bottom five
   * bits of this register may ever be set.
   */
  private OperatingMode operatingMode = OperatingMode.SVC;

  /**
   * Definition of symbolic constants for all valid operating modes. */
  public enum OperatingMode {
    USR ((byte) 0x10, (byte) 20),
    SYS ((byte) 0x1F, (byte) 25), //System mode is being treated as a mode with two special registers (r13, r14), that it shares with user mode 
    FIQ ((byte) 0x11, (byte) 12),
    IRQ ((byte) 0x12, (byte) 6),
    SVC ((byte) 0x13, (byte) 0),
    ABT ((byte) 0x17, (byte) 3),
    UND ((byte) 0x1A, (byte) 9);
   
    /** 
     * Each operating system is identified by 5 bits within the PSR. This values stores the byte
     * which identifies this mode within the Program Status Registers (PSR). */
    public final byte PSR_IDENTIFIER;
    
    /**
     * Most operating modes have banked registers, that are stored in the <code>shadowRegisters</code> array.
     * This value determines the offset at which the shadowed registers for the said mode are stored.
     * */
    private final byte SHADOW_OFFSET;
    
    private OperatingMode(byte psrIdentifier, byte shadowOffset) {
      this.PSR_IDENTIFIER = psrIdentifier;
      this.SHADOW_OFFSET = shadowOffset;
    }
  }
  
  /** Is the processor currently in thumb mode? */
  private boolean thumbMode = false;

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
   * Switches the current processor operating mode to <code>newMode</code>, by loading the appropriate
   * register layout for the mode and changing the processor's OperatingMode flag.
   * During this process, the SPSR of the newMode is replaced with the CPSR of the current mode.
   * 
   * @param newMode
   *  The operating mode that shall switch to.
   */
  public void switchOperatingModeAndStoreSPSR(OperatingMode newMode) {
    
    //get the previous SPSR
    int previous_cpsr = getCPSR();
    
    //change the current operating mode
    switchOperatingMode(newMode);
    
    setSPSR(previous_cpsr);
  }

  /** 
   * Sets the current operating mode and loads the register layout for that mode.
   * However, the CPSR is not pushed into the new mode's SPSR.
   * 
   * @param newMode
   *  The operating mode to switch to.
   */
  public void switchOperatingMode(OperatingMode newMode) {
    //perform the actual mode switch
    setRegisterLayout(newMode);
    operatingMode = newMode;
  }
  
  /**
   * Sets the processor's operating mode without changing the register layout.
   * This is useful in certain situations, where a command just "fakes" to be in another mode
   * so that the memory is fooled.
   * 
   * @param newMode
   */
  public void setOperatingModeWithoutRegisterLayout(OperatingMode newMode) {
    operatingMode = newMode;
  }
  
  /**
   * Sets the layout of the ARM registers to the layout expected in <code>newMode</code>.
   * 
   * @param newMode
   *  The mode for which the register layout shall be constructed.
   */
  private void setRegisterLayout(OperatingMode newMode) {
    
    //if we're either not switching to a new mode or if we're switching between SYS and USR mode, then
    //take a special fast-path
    if (newMode == operatingMode || 
        ((operatingMode == OperatingMode.USR || newMode == OperatingMode.SYS) &&
         (operatingMode == OperatingMode.SYS || newMode == OperatingMode.USR))) {
      //we don't need to do anything in this case
      return;
    }
    
    //in the first step, we're saving all registers of the current operating mode.
    //Furthermore, we are restoring all user registers except for r13 and r14. This way of approaching
    //the problem has the benefit, that we're only exchanging the registers that we really need to exchange.
    //However, we need to remember that user mode r8-r12 have not been saved after performing the first step.
    int shadowOffset = operatingMode.SHADOW_OFFSET;
    
    if (operatingMode == OperatingMode.FIQ) {
      //store the extra fiq registers
      shadowRegisters[shadowOffset++] = regs[8];
      shadowRegisters[shadowOffset++] = regs[9];
      
      //skip the FIQ mode SPSR
      if (DBT.VerifyAssertions) DBT._assert(OperatingMode.FIQ.SHADOW_OFFSET - shadowOffset == SPSR_OFFSET);
      shadowOffset++;
      
      shadowRegisters[shadowOffset++] = regs[10];
      shadowRegisters[shadowOffset++] = regs[11];
      shadowRegisters[shadowOffset++] = regs[12];
      
      //and restore their corresponding user mode registers
      regs[8] =  shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 0];
      regs[9] =  shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 1];
      regs[10] = shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 2];
      regs[11] = shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 3];
      regs[12] = shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 4];
    }
    else if (operatingMode == OperatingMode.USR) {
      //skip user mode r8-r12
      shadowOffset += 5;
    }
    
    //store the current mode's r13 and r14
    shadowRegisters[shadowOffset++] = regs[13];
    shadowRegisters[shadowOffset] = regs[14];

    //Up to there, we have saved the current mode's registers and restored user mode r8-r12.
    //Now, load the new mode's registers. However, remember that though r8-r12 are currently in the register map,
    //they have not been saved to the shadowMap. So, if the new mode needs r8-r12, then it
    //has to save them first.
    shadowOffset = newMode.SHADOW_OFFSET;
    
    if (newMode == OperatingMode.FIQ) {
      //if we're switching to FIQ mode, then remember that we also have to save the (previously unsaved)
      //r8-r12 registers
      shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 0] = regs[8];
      shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 1] = regs[9];
      shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 2] = regs[10];
      shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 3] = regs[11];
      shadowRegisters[OperatingMode.USR.SHADOW_OFFSET + 4] = regs[12];
      
      //then load in the FIQ mode's r8-r12
      regs[8] = shadowRegisters[shadowOffset++];
      regs[9] = shadowRegisters[shadowOffset++];
      
      //skip the shadow mode SPSR
      if (DBT.VerifyAssertions) DBT._assert(OperatingMode.FIQ.SHADOW_OFFSET - shadowOffset == SPSR_OFFSET);
      shadowOffset++;
      
      regs[10] = shadowRegisters[shadowOffset++];
      regs[11] = shadowRegisters[shadowOffset++];
      regs[12] = shadowRegisters[shadowOffset++];
    }
    else if (newMode == OperatingMode.USR) {
      //skip these shadow registers for now
      shadowOffset += 5;
    }
    
    //now load the remaining r13 and r14 registers
    regs[13] = shadowRegisters[shadowOffset++];
    regs[14] = shadowRegisters[shadowOffset];
  }
  
  /**
   * Overwrites the SPSR of the current mode with the supplied value. 
   * 
   * @param newSPSR
   *  The new value, which will replace the current SPSR.
   */
  public void setSPSR(int newSPSR) {
    //save the previous CPSR as the current SPSR
    if (operatingMode == OperatingMode.USR || operatingMode == OperatingMode.SYS) {
      //these modes don't have a SPSR, so ignore them
      return;
    }
    
    shadowRegisters[operatingMode.SHADOW_OFFSET + SPSR_OFFSET] = newSPSR;
  }
  
  /**
   * Returns the current operating mode.
   */
  public OperatingMode getOperatingMode() {
    return operatingMode;
  }

  /**
   * Restores the saved program status register of the current operating mode to the CPSR, 
   * thereby effectively switching to a different operating mode.
   */
  public void restoreSPSR2CPSR() {
    if (VM.VerifyAssertions) VM._assert(operatingMode != OperatingMode.USR);
    
    setCPSR(getSPSR());
  }
  
  /**
   * Reads the program counter as a ARM/thumb instruction would read it. This implies adding an offset of 8 (ARM)
   * or 4 (Thumb) to the program counter itself.
   */
  public int readPC() {
    int pc = get(ARM_Registers.PC);
    
    if ((pc & 0x1) == 1) {
      return (pc & 0xFFFFFFFE) + 4;
    }
    else {
      return pc + 8;
    }
  }
  
  /**
   * Used to control the processor flag which says if the the processor is accepting interrupt requests.
   *  
   * @param enabled
   *  Set to true to enable interrupts or false otherwise.
   */
  public void setInterruptsEnabled(boolean enabled) {
    flagIRQsDisabled = !enabled;
  }

  /**
   * Returns the content of ARM's Current Program Status Register.
   * 
   * @return
   * A integer, containing a bit representation that is equal to what the current processor state
   * would look like on the ARM processor. 
   */
  public int getCPSR() {
    return (flagNegative ? 1 << 31 : 0) | (flagZero ? 1 << 30 : 0)
        | (flagCarry ? 1 << 29 : 0) | (flagOverflow ? 1 << 28 : 0)
        | (flagIRQsDisabled ? 1 << 7 : 0) | (flagFIQsDisabled ? 1 << 6 : 0)
        | (thumbMode ? 1 << 5 : 0)  | operatingMode.PSR_IDENTIFIER;
  }
  
  /** Returns the content of the current mode's Saved Program Status register.*/
  public int getSPSR() {
    if (operatingMode == OperatingMode.USR || operatingMode == OperatingMode.SYS) {
      //these modes don't have a SPSR, so throw an exception
      throw new RuntimeException("Cannot read a SPSR in operating mode: " + operatingMode);
    }
    
    return shadowRegisters[operatingMode.SHADOW_OFFSET + SPSR_OFFSET];
  }

  /**
   * Restores the processor state to the state saved within the given CPSR.
   * 
   * @param cpsr
   *          ARM CPSR register content
   */
  public void setCPSR(int cpsr) {
    
    //extract the different flags from the PSR
    flagNegative = (cpsr & 0x80000000) != 0; //bit 31
    flagZero = (cpsr & 0x40000000) != 0; //bit 30
    flagCarry = (cpsr & 0x20000000) != 0; //bit 29
    flagOverflow = (cpsr & 0x10000000) != 0; //bit 28
    flagIRQsDisabled = (cpsr & 0x80) != 0; //bit 7
    flagFIQsDisabled = (cpsr & 0x40) != 0; //bit 6
    thumbMode = (cpsr & 0x20) != 0; //bit 5
    
    //extract the new operating mode
    byte mode = (byte)(cpsr & 0x1F);
    
    //then perform a regular mode switch to update the register map
    for (OperatingMode opMode : OperatingMode.values())
      if (opMode.PSR_IDENTIFIER == mode) {
        switchOperatingMode(opMode);
        break;
      }
    
    if (DBT.VerifyAssertions) DBT._assert(operatingMode.PSR_IDENTIFIER == mode);
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
   * Returns true if the processor is currently in Thumb mode, false otherwise.
   * @return
   *  Processor in thumb mode?
   */
  public boolean getThumbMode() {
    return thumbMode || ((get(ARM_Registers.PC) & 1) != 0);
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
  
  /** Returns true if the zero flag is set, false otherwise. */
  public boolean isZeroSet() {
    return flagZero;
  }
  
  /** Returns true if the overflow flag is set, false otherwise. */
  public boolean isOverflowSet() {
    return flagOverflow;
  }
  
  /** Returns true if the negative flag is set, false otherwise. */
  public boolean isNegativeSet() {
    return flagNegative;
  }
  
  /** Returns a string representation of the values in the ARM registers. */
  @Override
  public String toString() {

    StringBuilder result = new StringBuilder();
    for (int i = 0; i < regs.length; i++) {
      result.append('r');
      result.append(i);
      result.append(": x");
      result.append(Integer.toHexString(regs[i]));
      result.append(", ");
    }

    result.append("C:");
    result.append(flagCarry ? '1' : '0');
    result.append(", Z:");
    result.append(flagZero ? '1' : '0');
    result.append(", N:");
    result.append(flagNegative ? '1' : '0');
    result.append(", O:");
    result.append(flagOverflow ? '1' : '0');

    return result.toString();
  }
}
