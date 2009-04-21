/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.x86.os.process;

/**
 * Capture the rather complex X86 register file. These registers will
 * be used as the backing store between translations and will be
 * operated on by the interpreter.
 */
public class X86_Registers  {
  /*
   * General purpose registers
   */
    
  /**
   * Segment register numbers
   */
  public static final int ES = 0, CS = 1, SS = 2, DS = 3,   FS = 4, GS = 5;
  /**
   * Translate a segment number into its string variant
   */
  private static final String segmentName[] = {"ES", "CS", "SS", "DS", "FS", "GS"};

  /**
   * 8 bit register numbers
   */
  public static final int AL = 0, CL = 1, DL = 2, BL = 3;
  public static final int AH = 4, CH = 5, DH = 6, BH = 7;

  /**
   * 16bit register numbers
   */
  public static final int AX = 0, CX = 1, DX = 2, BX = 3;
  public static final int SP = 4, BP = 5, SI = 6, DI = 7;

  /**
   * 32bit register numbers
   */
  public static final int EAX = 0, ECX = 1, EDX = 2, EBX = 3;
  public static final int ESP = 4, EBP = 5, ESI = 6, EDI = 7;

  /**
   * 64bit register numbers
   */
  public static final int RAX  = 0,  RCX  = 1,  RDX = 2,  RBX = 3;
  public static final int RSP  = 4,  RBP  = 5,  RSI = 6,  RDI = 7;
  public static final int R8   = 8,  R9   = 9,  R10 = 10, R11 = 11;
  public static final int R12  = 12, R13  = 13, R14 = 14, R15 = 15;

  /**
   * Translate a register number into its 32bit string variant
   */
  private static final String[] name64 = {"rax", "rcx", "rdx", "rbx", "rsp", "rbp", "rsi", "rdi", "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15"};
  /**
   * Translate a register number into its 32bit string variant
   */
  private static final String[] name32 = {"eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi"};
  /**
   * Translate a register number into its 16bit string variant
   */
  private static final String[] name16 = {"ax", "cx", "dx", "bx", "sp", "bp", "si", "di"};
  /**
   * Translate a register number into its 32bit string variant
   */
  private static final String[] name8 = {"al", "cl", "dl", "bl", "ah", "ch", "dh", "bh"};

  /**
   * Array holding the 8 32bit versions of the registers
   */
  private int[] gp32 = new int[8];

  /**
   * The instruction pointer register
   */
  public int eip;

  /**
   * Array holding the 6 segment registers
   */    
  private char[] segmentRegister = new char[6];

  /**
   * Base address to be added onto gs segment overrides 
   */
  private int gsBaseAddr;
  
  /*
   * Flags
   */

  // -- status flags
  /**
   * X86 flag register constituants - bit 0 - CF or carry flag
   *
   * Set if an arithmetic operation generates a carry or a borrow out
   * of the most-significant bit of the result; cleared
   * otherwise. This flag indicates an overflow condition for
   * unsigned-integer arithmetic. It is also used in
   * multiple-precision arithmetic.
   */
  private boolean flag_CF;
  /**
   * X86 flag register constituants - bit 2 - PF or parity flag
   *
   * Set if the least-significant byte of the result contains an even
   * number of 1 bits; cleared otherwise.
   */
  private boolean flag_PF;
  /**
   * X86 flag register constituants - bit 4 - AF or auxiliary carry
   * flag or adjust flag
   *
   * Set if an arithmetic operation generates a carry or a borrow out
   * of bit 3 of the result; cleared otherwise. This flag is used in
   * binary-coded decimal (BCD) arithmetic.
   */
  private boolean flag_AF;
  /**
   * X86 flag register constituants - bit 6 - ZF or zero flag
   *
   * Set if the result is zero; cleared otherwise.
   */
  private boolean flag_ZF;
  /**
   * X86 flag register constituants - bit 7 - SF or sign flag
   *
   * Set equal to the most-significant bit of the result, which is the
   * sign bit of a signed integer. (0 indicates a positive value and 1
   * indicates a negative value.)
   */
  private boolean flag_SF;
  /**
   * X86 flag register constituants - bit 11 - OF or overflow flag
   *
   * Set if the integer result is too large a positive number or too
   * small a negative number (excluding the sign-bit) to fit in the
   * destination operand; cleared otherwise. This flag indicates an
   * overflow condition for signed-integer (two’s complement)
   * arithmetic.
   */
  private boolean flag_OF;

  // -- direction flag

  /**
   * X86 flag register constituants - bit 10 - DF or direction flag
   *
   * The direction flag (DF, located in bit 10 of the EFLAGS register)
   * controls string instructions (MOVS, CMPS, SCAS, LODS, and
   * STOS). Setting the DF flag causes the string instructions to
   * auto-decrement (to process strings from high addresses to low
   * addresses). Clearing the DF flag causes the string instructions
   * to auto-increment (process strings from low addresses to high
   * addresses).
   */
  private boolean flag_DF;
    
  // -- System Flags and IOPL Field
  // TODO

  /*
   * X86 flag register constituants - bit 8 - TF or trap flag
   *
   * Set to enable single-step mode for debugging; clear to disable
   * single-step mode.
   */

  /*
   * X86 flag register constituants - bit 9 - IF or interrupt enable flag
   *
   * Controls the response of the processor to maskable interrupt
   * requests. Set to respond to maskable interrupts; cleared to
   * inhibit maskable interrupts.
   */

  /*
   * X86 flag register constituants - bits 12 and 13 - IOPL or I/O
   * privilege level field
   * 
   * Indicates the I/O privilege level of the currently running
   * program or task. The current privilege level (CPL) of the
   * currently running program or task must be less than or equal to
   * the I/O privilege level to access the I/O address space. This
   * field can only be modified by the POPF and IRET instructions when
   * operating at a CPL of 0.
   */

  /*
   * X86 flag register constituants - bit 14 - NT or nested task flag
   *
   * Controls the chaining of interrupted and called tasks. Set when
   * the current task is linked to the previously executed task;
   * cleared when the current task is not linked to another task.
   */

  /*
   * X86 flag register constituants - bit 16 - RF or resume flag
   *
   * Controls the processor’s response to debug exceptions.
   */

  /*
   * X86 flag register constituants - bit 17 - VM or Virtual-8086 mode
   * flag
   *
   * Set to enable virtual-8086 mode; clear to return to protected
   * mode without virtual-8086 mode semantics.
   */

  /*
   * X86 flag register constituants - bit 18 - AC or alignment check
   * flag
   *
   * Set this flag and the AM bit in the CR0 register to enable
   * alignment checking of memory references; clear the AC flag and/or
   * the AM bit to disable alignment checking.
   */

  /*
   * X86 flag register constituants - bit 19 - VIF or virtual
   * interrupt flag
   *
   * Virtual image of the IF flag. Used in conjunction with the VIP
   * flag. (To use this flag and the VIP flag the virtual mode
   * extensions are enabled by setting the VME flag in control
   * register CR4.)
   */

  /*
   * X86 flag register constituants - bit 20 - VIP or virtual interrupt pending flag
   *
   * Set to indicate that an interrupt is pending; clear when no
   * interrupt is pending. (Software sets and clears this flag; the
   * processor only reads it.) Used in conjunction with the VIF flag.
   */

  /*
   * X86 flag register constituants - bit 21 - Identification flag
   *
   * The ability of a program to set or clear this flag indicates
   * support for the CPUID instruction.
   */

  /*
   * Floating point registers
   */

  /**
   * The floating point stack
   */
  private double fpStack[] = new double[8]; // these are actually 80bit precision on the chip

  /**
   * The floating point control register aka fctrl
   */
  private short fpControlRegister;

  /**
   * The floating point status register aka fstat
   */
  private short fstat;

  /**
   * The floating point tag register aka ftag
   */
  private short ftag;

  /**
   * The last floating point opcode aka fop
   */
  private short fop;

  // Registers currently missing: fiseg, fioff, foseg, fooff

  /*
   * SSE registers
   */
  /**
   * SSE registers mm0..mm7
   */
  private long mmRegister[] = new  long[8];

  /**
   * The MXCSR register that contains control and status information
   * for SSE, SSE2, and SSE3 SIMD floating-point operations.
   * 
   * 31..16 : Reserved
   * 15     : FZ - Flush to zero
   * 14..13 : RC - Rounding control
   * 12     : PM - Precision mask
   * 11     : UM - Underflow mask
   * 10     : OM - Overflow mask
   * 9      : ZM - Divide-by-zero mask
   * 8      : DM - Denormal flag
   * 7      : IM - Invalid operation flag
   * 6      : DAZ - Denormals are zeros
   * 5      : PE - Precision flag
   * 4      : UE - Underflow flag
   * 3      : OE - Overflow flag
   * 2      : ZE - Divide-by-zero flag
   * 1      : DE - Denormal flag
   * 0      : IE - Invalid operation flag
   */
  private int mxcsr;

  /*
   * Methods
   */

  /**
   * Constructor
   */
  public X86_Registers() {
    // When the x87 FPU is initialized with either an FINIT/FNINIT
    // or FSAVE/FNSAVE instruction, the x87 FPU control word is set
    // to 037FH, which masks all floating-point exceptions, sets
    // rounding to nearest, and sets the x87 FPU precision to 64
    // bits.
    fpControlRegister = 0x037f;

    mxcsr = 0x1F80;
  }

  /*
   * General purpose registers
   */

  /**
   * Disassemble the register
   */
  public static String disassemble(int register, int operandSize){
    switch (operandSize) {
    case 64: return name64[register];
    case 32: return name32[register];
    case 16: return name16[register];
    default: return name8[register];
    }
  }

  /**
   * Disassemble the register
   */
  public static String disassembleSegment(int register){
    return segmentName[register];
  }

  /**
   * Read the eflags register
   */
  public int readEFlags() {
    return (flag_CF ? 1 : 0) |
      (flag_PF ? (1 << 2) : 0) |
      (flag_AF ? (1 << 4) : 0) |
      (flag_ZF ? (1 << 6) : 0) |
      (flag_SF ? (1 << 7) : 0) |
      (flag_DF ? (1 << 10) : 0) |
      (flag_OF ? (1 << 11) : 0);
  }
  /**
   * Read 32bit version of register
   */
  public int readGP32(int reg) {
    return gp32[reg];
  }
  /**
   * Read 16it version of register
   */
  public short readGP16(int reg) {
    return (short) (gp32[reg] & 0xffff);
  }
  /**
   * Read 8 bit register
   */
  public byte readGP8(int reg) {      
    if (reg < 4) {
      return (byte) (gp32[reg] & 0xff);
    } else {
      return (byte) ((gp32[reg - 4] >> 8) & 0xff);
    }
  }
  /**
   * Write a 32bit general purpose register
   */
  public void writeGP32(int reg, int val) {
    gp32[reg] = val;
  }
  /**
   * Write a 16bit general purpose register
   */
  public void writeGP16(int reg, short val) {
    gp32[reg] = (gp32[reg] & 0xffff0000) | (((int) val) & 0x0000ffff);
  }
  /**
   * Write an 8bit general purpose register
   */
  public void writeGP8(int reg, byte val) {
    if (reg < 4) {
      gp32[reg] = (gp32[reg] & 0xffffff00) | (((int) val) & 0x000000ff);
    } else {
      gp32[reg - 4] = (gp32[reg - 4] & 0xffff00ff) | (((int)val & 0xff) << 8);
    }
  }

  /**
   * Read 16 bit segment
   */
  public char readSeg(int reg) {
    return segmentRegister[reg];
  }
  /**
   * Write a segment register value
   */
  public void writeSeg(int reg, char val) {
    segmentRegister[reg] = val;
  }
  
  /**
   * Write a segment register value
   */
  public void writeGS_BaseAddr(int baseAddr) {
    gsBaseAddr = baseAddr;
  }
  
  /**
   * Read flags
   */
  public int readFlags() {
    throw new Error("TODO");
  }
  /**
   * Write flags
   */
  public void writeFlags(int val) {
    throw new Error("TODO");
  }

  /*
   * Floating point registers
   */

  /**
   * Read floating point control register
   */
  public short readFpControlRegister() {
    return fpControlRegister;
  }

  /**
   * Write the floating point control register
   */
  public void writeFpControlRegister(short val) {      
    fpControlRegister = val;
  }

  /*
   * SSE registers
   */

  /**
   * Read mxcsr
   */
  public int readMxcsr() {
    return mxcsr;
  }

  /**
   * Write mxcsr
   */
  public void writeMxcsr(int val) {
    mxcsr = val;
  }

  /**
   * Turn the registers into a string (for debug)
   */
  public String toString() {
    return
      name32[EAX] + "=0x" + Integer.toHexString(gp32[EAX]) + " " +
      name32[EBX] + "=0x" + Integer.toHexString(gp32[EBX]) + " " +
      name32[ECX] + "=0x" + Integer.toHexString(gp32[ECX]) + " " +
      name32[EDX] + "=0x" + Integer.toHexString(gp32[EDX]) + " " +
      name32[ESP] + "=0x" + Integer.toHexString(gp32[ESP]) + " " +
      name32[EBP] + "=0x" + Integer.toHexString(gp32[EBP]) + " " +
      name32[ESI] + "=0x" + Integer.toHexString(gp32[ESI]) + " " +
      name32[EDI] + "=0x" + Integer.toHexString(gp32[EDI]) +
      " cf=" + flag_CF +
      " sf=" + flag_SF +
      " zf=" + flag_ZF +
      " of=" + flag_OF;
  }
}
