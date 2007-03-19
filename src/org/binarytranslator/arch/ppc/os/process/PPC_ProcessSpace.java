/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.ppc.os.process;

import org.jikesrvm.opt.ir.OPT_GenerationContext;
import org.jikesrvm.opt.ir.OPT_HIRGenerator;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.ppc.os.process.linux.PPC_LinuxProcessSpace;
import org.binarytranslator.arch.ppc.decoder.PPC2IR;
import org.binarytranslator.arch.ppc.decoder.PPC_InstructionDecoder;
import org.binarytranslator.generic.memory.ByteAddressedByteSwapMemory;
import org.binarytranslator.generic.memory.MemoryMapException;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.vmInterface.*;
import java.util.*;
import java.io.*;

/** 
 * Capture the running of a PowerPC process
 */
public abstract class PPC_ProcessSpace extends ProcessSpace
{
  /* Here's what would be in the PowerPC's registers if the binary
     were running on a real PowerPC.  For speed I am using individual
     variables, not arrays for GPRs and FPRs. */

  /**
   * The contents of the general purpose (int) registers.
   */
  public int r0, r1, r2, r3, r4, r5, r6, r7, r8, r9, 
    r10, r11, r12, r13, r14, r15, r16, r17, r18, r19,
    r20, r21, r22, r23, r24, r25, r26, r27, r28, r29,
    r30, r31;

  /**
   * The contents of the floating point registers.
   */
  public double f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, 
    f10, f11, f12, f13, f14, f15, f16, f17, f18, f19,
    f20, f21, f22, f23, f24, f25, f26, f27, f28, f29,
    f30, f31;

  /**
   * The contents of the condition register.
   */
  public boolean crf_lt[], crf_gt[], crf_eq[], crf_so[];

  /**
   * The contents of the floating-point status and control register.
   */
  public int fpscr;

  /**
   * The contents of the machine status register.
   */
  public int msr;

  /**
   * The contents of the XER register.
   */
    public boolean xer_so, xer_ov, xer_ca;
    public byte xer_byteCount;

  /**
   * The contents of the link register.
   */
  public int lr;

  /** 
   * The contents of the counter register.
   */
  public int ctr;

  /**
   * The contents of the time base registers.
   */
  int tbl, tbu;

  /**
   * Pointer to where we are up to in the program (the PPC doesn't
   * actually have a program counter register, but we sometimes need
   * to keep track of our place in the binary).
   */
  public int pc;

  /**
   * When interpreting this holds the current instruction being
   * interpretted
   */
  public int currentInstruction;

  /*
   * Utility functions
   */

  /**
   * Debug information
   * @param s string of debug information
   */
  private static void report(String s){
    if (DBT_Options.debugLoader) {
      System.out.print("PPC ProcessSpace:");
      System.out.println(s);
    }
  }

  /*
   * Methods
   */

  /**
   * Constructor
   */
  protected PPC_ProcessSpace() {
    memory = new ByteAddressedByteSwapMemory();
    crf_lt = new boolean[8];
    crf_gt = new boolean[8];
    crf_eq = new boolean[8];
    crf_so = new boolean[8];
  }

  /**
   * Create an optimizing compiler HIR code generator suitable for
   * this architecture
   * @param context the generation context for the HIR generation
   * @return a HIR generator
   */
  public OPT_HIRGenerator createHIRGenerator(OPT_GenerationContext context) {
    return new PPC2IR(context);
  }

  /**
   * Given an ELF binary loader, create the appropriate process space
   * @param elf the elf binary loader
   * @return the appropriate process space
   */
  public static ProcessSpace createProcessSpaceFromBinary (Loader loader) throws IOException {
    if (loader.isLinuxABI() || loader.isSysV_ABI()) {
      report("Linux/SysV ABI");
      return new PPC_LinuxProcessSpace(loader);
    }
    else {
      throw new IOException("Binary of " + loader.getABIString() +
                            " ABI is unsupported for the PowerPC architecture");
    }
  }

  /**
   * Return as an integer the current instruction's address
   */
  public int getCurrentInstructionAddress() {
    return pc;
  }
  /**
   * Sets the current instruction's address
   */
  public void setCurrentInstructionAddress(int pc) {
    this.pc = pc;
  }
  /**
   * Return as an integer the current instruction's address
   */
  public int getCurrentStackAddress() {
    return r1;
  }

  /**
   * Set register to value
   * @param register to set
   * @param value
   */
  public void setRegister(int register, int value) {
    switch(register) {
    case 0: r0 = value; break;
    case 1: r1 = value; break;
    case 2: r2 = value; break;
    case 3: r3 = value; break;
    case 4: r4 = value; break;
    case 5: r5 = value; break;
    case 6: r6 = value; break;
    case 7: r7 = value; break;
    case 8: r8 = value; break;
    case 9: r9 = value; break;
    case 10: r10 = value; break;
    case 11: r11 = value; break;
    case 12: r12 = value; break;
    case 13: r13 = value; break;
    case 14: r14 = value; break;
    case 15: r15 = value; break;
    case 16: r16 = value; break;
    case 17: r17 = value; break;
    case 18: r18 = value; break;
    case 19: r19 = value; break;
    case 20: r20 = value; break;
    case 21: r21 = value; break;
    case 22: r22 = value; break;
    case 23: r23 = value; break;
    case 24: r24 = value; break;
    case 25: r25 = value; break;
    case 26: r26 = value; break;
    case 27: r27 = value; break;
    case 28: r28 = value; break;
    case 29: r29 = value; break;
    case 30: r30 = value; break;
    case 31: r31 = value; break;
    default: throw new Error("Unknown register: "+register);
    }
  }
  /**
   * Get register value
   * @param register to set
   * @return value
   */
  public int getRegister(int register) {
    switch(register) {
    case 0: return r0;
    case 1: return r1;
    case 2: return r2;
    case 3: return r3;
    case 4: return r4;
    case 5: return r5;
    case 6: return r6;
    case 7: return r7;
    case 8: return r8;
    case 9: return r9;
    case 10: return r10;
    case 11: return r11;
    case 12: return r12;
    case 13: return r13;
    case 14: return r14;
    case 15: return r15;
    case 16: return r16;
    case 17: return r17;
    case 18: return r18;
    case 19: return r19;
    case 20: return r20;
    case 21: return r21;
    case 22: return r22;
    case 23: return r23;
    case 24: return r24;
    case 25: return r25;
    case 26: return r26;
    case 27: return r27;
    case 28: return r28;
    case 29: return r29;
    case 30: return r30;
    case 31: return r31;
    default: throw new Error("Unknown register: "+register);
    }
  }
  /**
   * Set register to value
   * @param register to set
   * @param value
   */
  public void setFPregister(int register, double value) {
    switch(register) {
    case 0: f0 = value; break;
    case 1: f1 = value; break;
    case 2: f2 = value; break;
    case 3: f3 = value; break;
    case 4: f4 = value; break;
    case 5: f5 = value; break;
    case 6: f6 = value; break;
    case 7: f7 = value; break;
    case 8: f8 = value; break;
    case 9: f9 = value; break;
    case 10: f10 = value; break;
    case 11: f11 = value; break;
    case 12: f12 = value; break;
    case 13: f13 = value; break;
    case 14: f14 = value; break;
    case 15: f15 = value; break;
    case 16: f16 = value; break;
    case 17: f17 = value; break;
    case 18: f18 = value; break;
    case 19: f19 = value; break;
    case 20: f20 = value; break;
    case 21: f21 = value; break;
    case 22: f22 = value; break;
    case 23: f23 = value; break;
    case 24: f24 = value; break;
    case 25: f25 = value; break;
    case 26: f26 = value; break;
    case 27: f27 = value; break;
    case 28: f28 = value; break;
    case 29: f29 = value; break;
    case 30: f30 = value; break;
    case 31: f31 = value; break;
    default: throw new Error("Unknown register: "+register);
    }
  }
  /**
   * Get register value
   * @param register to set
   * @return value
   */
  public double getFPregister(int register) {
    switch(register) {
    case 0: return f0;
    case 1: return f1;
    case 2: return f2;
    case 3: return f3;
    case 4: return f4;
    case 5: return f5;
    case 6: return f6;
    case 7: return f7;
    case 8: return f8;
    case 9: return f9;
    case 10: return f10;
    case 11: return f11;
    case 12: return f12;
    case 13: return f13;
    case 14: return f14;
    case 15: return f15;
    case 16: return f16;
    case 17: return f17;
    case 18: return f18;
    case 19: return f19;
    case 20: return f20;
    case 21: return f21;
    case 22: return f22;
    case 23: return f23;
    case 24: return f24;
    case 25: return f25;
    case 26: return f26;
    case 27: return f27;
    case 28: return f28;
    case 29: return f29;
    case 30: return f30;
    case 31: return f31;
    default: throw new Error("Unknown register: "+register);
    }
  }

  /**
   * Set a condition register field of the condition register
   */
    public void setCRfield_signedCompare(int field, int value1, int value2) {
    System.out.println("Signed compare: field=" +field + " val1=" + value1 + " val2=" + value2);
    crf_lt[field] = value1 < value2;
    crf_gt[field] = value1 > value2;
    crf_eq[field] = value1 == value2;
    crf_so[field] = xer_so;
  }

  /**
   * Set a condition register field of the condition register
   */
    public void setCRfield_unsignedCompare(int field, int val1, int val2) {
    long value1 = val1 & 0xFFFFFFFF;
    long value2 = val2 & 0xFFFFFFFF;
    System.out.println("Unsigned compare: field=" +field + " val1=" + value1 + " val2=" + value2);
    crf_lt[field] = value1 < value2;
    crf_gt[field] = value1 > value2;
    crf_eq[field] = val1 == val2;
    crf_so[field] = xer_so;
  }

    /**
     * Combine CR bits into 32bit CR register
     */
    public int getCR() {
        int cr = 0;
        for(int crf=0; crf < 8; crf++) {
            cr |= crf_lt[crf] ? 1 : 0;
            cr |= crf_gt[crf] ? 2 : 0;
            cr |= crf_eq[crf] ? 4 : 0;
            cr |= crf_so[crf] ? 8 : 0;
            cr <<= 4;
        }
        return cr;
    }
    /**
     * Read a bit of the condition register
     * @param crb condition register bit
     */
    public boolean getCR_bit(int crb) {
        int crf = crb >> 2;
        return getCR_bit(crf, crb & 0x3);
    }
    /**
     * Read a bit of a condition register field
     * @param crf condition register field
     * @param crb condition register bit within field
     */
    public boolean getCR_bit(int crf, int crb) {
        switch(crb) {
        case 0:
            return crf_lt[crf];
        case 1:
            return crf_gt[crf];
        case 2:
            return crf_eq[crf];
        case 3:
            return crf_so[crf];
        }
        DBT_OptimizingCompilerException.UNREACHABLE();
	return false; // keep the compiler happy
    }
    /**
     * Set CR to given value
     * @param val value to set CR to
     */
    public void setCR(int val) {
        for(int crf=7; crf >= 0; crf--) {
            crf_lt[crf] = (val & 1) != 0;
            crf_gt[crf] = (val & 2) != 0;
            crf_eq[crf] = (val & 4) != 0;
            crf_so[crf] = (val & 8) != 0;
            val >>= 4;
        }
    }
    /**
     * Set a bit of the condition register
     * @param crb condition register bit
     * @param val value to set
     */
    public void setCR_bit(int crb, boolean val) {
        int crf = crb >> 2;
        switch(crb & 0x3) {
        case 0:
            crf_lt[crf] = val;
            break;
        case 1:
            crf_gt[crf] = val;
            break;
        case 2:
            crf_eq[crf] = val;
            break;
        case 3:
            crf_so[crf] = val;
            break;
        }
    }

    /**
     * Combine and return the 32bit XER register
     */
    public int getXER() {
	int result=0;
	if (xer_so) result |= 1 << 31;
	if (xer_ov) result |= 1 << 30;
	if (xer_ca) result |= 1 << 29;
	result |= xer_byteCount & 0x3F;
	return result;
    }
  /**
   * Turn the process space into a string (for debug)
   */
  public String toString() {
    return
      "r0=0x" + Integer.toHexString(r0) +
      " r1=0x" + Integer.toHexString(r1) +
      " r2=0x" + Integer.toHexString(r2) +
      " r3=0x" + Integer.toHexString(r3) +
      "\nr4=0x" + Integer.toHexString(r4) +
      " r5=0x" + Integer.toHexString(r5) +
      " r6=0x" + Integer.toHexString(r6) +
      " r7=0x" + Integer.toHexString(r7) +
      "\nr8=0x" + Integer.toHexString(r8) +
      " r9=0x" + Integer.toHexString(r9) +
      " r10=0x" + Integer.toHexString(r10) +
      " r11=0x" + Integer.toHexString(r11) +
      "\nr12=0x" + Integer.toHexString(r12) +
      " r13=0x" + Integer.toHexString(r13) +
      " r14=0x" + Integer.toHexString(r14) +
      " r15=0x" + Integer.toHexString(r15) +
      "\nr16=0x" + Integer.toHexString(r16) +
      " r17=0x" + Integer.toHexString(r17) +
      " r18=0x" + Integer.toHexString(r18) +
      " r19=0x" + Integer.toHexString(r19) +
      "\nr20=0x" + Integer.toHexString(r20) +
      " r21=0x" + Integer.toHexString(r21) +
      " r22=0x" + Integer.toHexString(r22) +
      " r23=0x" + Integer.toHexString(r23) +
      "\nr24=0x" + Integer.toHexString(r24) +
      " r25=0x" + Integer.toHexString(r25) +
      " r26=0x" + Integer.toHexString(r26) +
      " r27=0x" + Integer.toHexString(r27) +
      "\nr28=0x" + Integer.toHexString(r28) +
      " r29=0x" + Integer.toHexString(r29) +
      " r30=0x" + Integer.toHexString(r30) +
      " r31=0x" + Integer.toHexString(r31);
  }

  /* GDB Interface */

  /**
   * Read a register and turn into a byte array conforming to the
   * endianness of the architecture
   */
  public byte[] readRegisterGDB(int regNum) {
    int value=0;
    double floatValue=0.0;
    switch(regNum) {
    case 0: value = r0; break;
    case 1: value = r1; break;
    case 2: value = r2; break;
    case 3: value = r3; break;
    case 4: value = r4; break;
    case 5: value = r5; break;
    case 6: value = r6; break;
    case 7: value = r7; break;
    case 8: value = r8; break;
    case 9: value = r9; break;
    case 10: value = r10; break;
    case 11: value = r11; break;
    case 12: value = r12; break;
    case 13: value = r13; break;
    case 14: value = r14; break;
    case 15: value = r15; break;
    case 16: value = r16; break;
    case 17: value = r17; break;
    case 18: value = r18; break;
    case 19: value = r19; break;
    case 20: value = r20; break;
    case 21: value = r21; break;
    case 22: value = r22; break;
    case 23: value = r23; break;
    case 24: value = r24; break;
    case 25: value = r25; break;
    case 26: value = r26; break;
    case 27: value = r27; break;
    case 28: value = r28; break;
    case 29: value = r29; break;
    case 30: value = r30; break;
    case 31: value = r31; break;
    case 32: floatValue = f0; break;
    case 33: floatValue = f1; break;
    case 34: floatValue = f2; break;
    case 35: floatValue = f3; break;
    case 36: floatValue = f4; break;
    case 37: floatValue = f5; break;
    case 38: floatValue = f6; break;
    case 39: floatValue = f7; break;
    case 40: floatValue = f8; break;
    case 41: floatValue = f9; break;
    case 42: floatValue = f10; break;
    case 43: floatValue = f11; break;
    case 44: floatValue = f12; break;
    case 45: floatValue = f13; break;
    case 46: floatValue = f14; break;
    case 47: floatValue = f15; break;
    case 48: floatValue = f16; break;
    case 49: floatValue = f17; break;
    case 50: floatValue = f18; break;
    case 51: floatValue = f19; break;
    case 52: floatValue = f20; break;
    case 53: floatValue = f21; break;
    case 54: floatValue = f22; break;
    case 55: floatValue = f23; break;
    case 56: floatValue = f24; break;
    case 57: floatValue = f25; break;
    case 58: floatValue = f26; break;
    case 59: floatValue = f27; break;
    case 60: floatValue = f28; break;
    case 61: floatValue = f29; break;
    case 62: floatValue = f30; break;
    case 63: floatValue = f31; break;
    case 64: value = pc; break;
    case 65: value = msr; break; // aka ps
    case 66: value = getCR(); break;
    case 67: value = lr; break;
    case 68: value = ctr; break;
    case 69: value = getXER(); break;
    case 70: value = fpscr; break;
    case 103: value = 0; break; // vscr
    case 104: value = 0; break; // vrsave
    default: System.err.println("Unknown GDB register " + regNum); value = 0; break;
    }
    byte[] result;
    if ((regNum < 32) || (regNum > 63)) {
      result = new byte[4];
      result[3] = (byte)value;
      result[2] = (byte)(value>>8);
      result[1] = (byte)(value>>16);
      result[0] = (byte)(value>>24);
    } else {
      result = new byte[8];
      long longValue = Double.doubleToLongBits(floatValue);
      result[7] = (byte)longValue;
      result[6] = (byte)(longValue>>8);
      result[5] = (byte)(longValue>>16);
      result[4] = (byte)(longValue>>24);
      result[3] = (byte)(longValue>>32);
      result[2] = (byte)(longValue>>40);
      result[1] = (byte)(longValue>>48);
      result[0] = (byte)(longValue>>56);
    }
    return result;
  }

  /**
   * Get the value of the frame base register
   */
  public int getGDBStackPointerRegister() {
    return 1;
  }
  /**
   * Get the value of the frame base register
   */
  public int getGDBProgramCountRegister() {
    return 64;
  }

  /**
   * Run a single instruction
   */
  public void runOneInstruction() throws BadInstructionException {
    currentInstruction = memoryLoad32(pc);
    try {
      PPC_InstructionDecoder.findDecoder(currentInstruction).interpretInstruction(this);
    }
    catch(NullPointerException e) {
      throw new BadInstructionException(pc, this);
    }
  }
}
