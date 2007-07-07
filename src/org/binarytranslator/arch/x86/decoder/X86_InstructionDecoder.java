/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2007
 */
package org.binarytranslator.arch.x86.decoder;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.x86.os.process.X86_ProcessSpace;
import org.binarytranslator.arch.x86.os.process.X86_Registers;
import org.binarytranslator.generic.branchprofile.BranchProfile.BranchType;
import org.binarytranslator.generic.decoder.InstructionDecoder;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DBT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.*;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.*;

/**
 * Decoder for X86 instructions
 */
public class X86_InstructionDecoder extends InstructionDecoder {

  /*
   * Process defaults
   */

  /**
   * Decode 64bit extensions
   */
  protected static final boolean X86_64 = X86_ProcessSpace.X86_64;

  /**
   * Decode as 16bit or 32bit registers/addresses - overridden by address and
   * operand prefixs
   */
  protected static final boolean _16BIT = X86_ProcessSpace._16BIT;

  /*
   * Encodings for branch types
   */
  /* signed integer arithmetic */
  public static final int EQUAL = 0;

  public static final int NOT_EQUAL = 1;

  public static final int LESS = 2;

  public static final int GREATER_EQUAL = 3;

  public static final int GREATER = 4;

  public static final int LESS_EQUAL = 5;

  /* unsigned integer arithmetic */
  public static final int HIGHER = 6;

  public static final int LOWER = 7;

  public static final int HIGHER_EQUAL = 8;

  public static final int LOWER_EQUAL = 9;

  /* other flag operations */
  public static final int OVERFLOW = 10;

  public static final int NOT_OVERFLOW = 11;

  public static final int SIGNED = 12;

  public static final int NOT_SIGNED = 13;

  public static final int PARITY_EVEN = 14;

  public static final int PARITY_ODD = 15;

  /**
   * Encoding of flags for decoders 
   */
  protected enum FLAG {
    CARRY, DIRECTION, INTERRUPT 
  }
  
  /**
   * Look up table to find instruction translator, performed using the first
   * byte of the instruction
   */
  private static final X86_InstructionDecoder[] primaryOpcodes = {
      /* OPCD Decoder */
      /* 0x00 */new X86_Add_OpcodeDecoder(8, true, 0, true),
      //                                  8bit, has ModRM, no imm, rm is dest
      /* 0x01 */new X86_Add_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true),
      //                                  16/32bit, has ModRM, no imm, rm is dest
      /* 0x02 */new X86_Add_OpcodeDecoder(8, true, 0, false),
      //                                  8bit, has ModRM, no imm, rm is src
      /* 0x03 */new X86_Add_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, false),
      //                                  16/32bit, has ModRM, no imm, rm is src
      /* 0x04 */new X86_Add_OpcodeDecoder(8, false, 8, false),
      //                                  8bit, no ModRM, 8bit imm
      /* 0x05 */new X86_Add_OpcodeDecoder(_16BIT ? 16 : 32, false, _16BIT?16:32, false),
      //                                  16/32bit, no ModRM, 16/32bit imm
      /* 0x06 */null,
      /* 0x07 */null,
      /* 0x08 */new X86_Or_OpcodeDecoder(8, true, 0, true),
      //                                 8bit, has ModRM, no imm, rm is dest
      /* 0x09 */new X86_Or_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true),
      //                                 16/32bit,has ModRM, no imm, rm is dest
      /* 0x0A */new X86_Or_OpcodeDecoder(8, true, 0, false),
      //                                 8bit, has ModRM, no imm, rm is src
      /* 0x0B */new X86_Or_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, false),
      //                                 16/32bit, has ModRM, no imm, rm is src
      /* 0x0C */new X86_Or_OpcodeDecoder(8, false, 8, false),
      //                                 8bit, no ModRM, 8bit imm
      /* 0x0D */new X86_Or_OpcodeDecoder(_16BIT ? 16 : 32, false, _16BIT ? 16
          : 32, false),// 16/32bit, no ModRM, 16/32bit im
      /* 0x0E */null,
      /* 0x0F */new X86_Escape_OpcodeDecoder(),

      /* 0x10 */null,
      /* 0x11 */null,
      /* 0x12 */null,
      /* 0x13 */null,
      /* 0x14 */null,
      /* 0x15 */null,
      /* 0x16 */null,
      /* 0x17 */null,
      /* 0x18 */new X86_Sbb_OpcodeDecoder(8, true, 0, true),
      // 8bit, has ModRM, no imm, rm is dest
      /* 0x19 */new X86_Sbb_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true),
      // 16/32bit, has ModRM, no imm, rm is dest
      /* 0x1A */null,
      /* 0x1B */null,
      /* 0x1C */null,
      /* 0x1D */null,
      /* 0x1E */null,
      /* 0x1F */null,

      /* 0x20 */new X86_And_OpcodeDecoder(8, true, 0, true),
      // 8bit, has ModRM, no imm, rm is dest
      /* 0x21 */new X86_And_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true),
      // 16/32bit, has ModRM, no imm, rm is dest
      /* 0x22 */new X86_And_OpcodeDecoder(8, true, 0, false),
      // 8bit, has ModRM, no imm, rm is src
      /* 0x23 */new X86_And_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, false),
      // 16/32bit, has ModRM, no imm, rm is src
      /* 0x24 */new X86_And_OpcodeDecoder(8, false, 8, false),
      // 8bit, no ModRM, 8bit imm
      /* 0x25 */new X86_And_OpcodeDecoder(_16BIT ? 16 : 32, false, _16BIT ? 16
          : 32, false),// 16/32bit, no ModRM, 16/32bit imm
      /* 0x26 */new X86_ES_SegmentOverride_PrefixDecoder(),
      /* 0x27 */null,
      /* 0x28 */new X86_Sub_OpcodeDecoder(8, true, 0, true), // 8bit, has
      // ModRM, no imm,
      // rm is dest
      /* 0x29 */new X86_Sub_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true), // 16/32bit,has
      // ModRM,
      // no
      // imm,
      // rm
      // is
      // dest
      /* 0x2A */new X86_Sub_OpcodeDecoder(8, true, 0, false),// 8bit, has
      // ModRM, no imm,
      // rm is src
      /* 0x2B */new X86_Sub_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, false),// 16/32bit,has
      // ModRM,
      // no
      // imm,
      // rm
      // is
      // src
      /* 0x2C */new X86_Sub_OpcodeDecoder(8, false, 8, false),// 8bit, no ModRM,
      // 8bit imm
      /* 0x2D */new X86_Sub_OpcodeDecoder(_16BIT ? 16 : 32, false, _16BIT ? 16
          : 32, false),// 16/32bit, no ModRM, 16/32bit imm
      /* 0x2E */new X86_CS_SegmentOverride_PrefixDecoder(),
      /* 0x2F */null,

      /* 0x30 */new X86_Xor_OpcodeDecoder(8, true, 0, true), // 8bit, has
      // ModRM, no imm,
      // rm is dest
      /* 0x31 */new X86_Xor_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true), // 16/32bit,has
      // ModRM,
      // no
      // imm,
      // rm
      // is
      // dest
      /* 0x32 */new X86_Xor_OpcodeDecoder(8, true, 0, false),// 8bit, has
      // ModRM, no imm,
      // rm is src
      /* 0x33 */new X86_Xor_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, false),// 16/32bit,has
      // ModRM,
      // no
      // imm,
      // rm
      // is
      // src
      /* 0x34 */new X86_Xor_OpcodeDecoder(8, false, 8, false),// 8bit, no ModRM,
      // 8bit imm
      /* 0x35 */new X86_Xor_OpcodeDecoder(_16BIT ? 16 : 32, false, _16BIT ? 16
          : 32, false),// 16/32bit, no ModRM, 16/32bit imm
      /* 0x36 */new X86_SS_SegmentOverride_PrefixDecoder(),
      /* 0x37 */null,
      /* 0x38 */new X86_Cmp_OpcodeDecoder(8, true, 0, true), // 8bit, has
      // ModRM, no imm,
      // rm is dest
      /* 0x39 */new X86_Cmp_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true), // 16/32bit,has
      // ModRM,
      // no
      // imm,
      // rm
      // is
      // dest
      /* 0x3A */new X86_Cmp_OpcodeDecoder(8, true, 0, false),// 8bit, has
      // ModRM, no imm,
      // rm is src
      /* 0x3B */new X86_Cmp_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, false),// 16/32bit,has
      // ModRM,
      // no
      // imm,
      // rm
      // is
      // src
      /* 0x3C */new X86_Cmp_OpcodeDecoder(8, false, 8, false),// 8bit, no ModRM,
      // 8bit imm
      /* 0x3D */new X86_Cmp_OpcodeDecoder(_16BIT ? 16 : 32, false, _16BIT ? 16
          : 32, false),// 16/32bit, no ModRM, 16/32bit imm
      /* 0x3E */new X86_DS_SegmentOverride_PrefixDecoder(),
      /* 0x3F */null,

      // X86_64 REX prefix byte or inc/dec instructions
      /* 0x40 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x0) : new X86_Inc_OpcodeDecoder(0),
      /* 0x41 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x1) : new X86_Inc_OpcodeDecoder(1),
      /* 0x42 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x2) : new X86_Inc_OpcodeDecoder(2),
      /* 0x43 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x3) : new X86_Inc_OpcodeDecoder(3),
      /* 0x44 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x4) : new X86_Inc_OpcodeDecoder(4),
      /* 0x45 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x5) : new X86_Inc_OpcodeDecoder(5),
      /* 0x46 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x6) : new X86_Inc_OpcodeDecoder(6),
      /* 0x47 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x7) : new X86_Inc_OpcodeDecoder(7),
      /* 0x48 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x8) : new X86_Dec_OpcodeDecoder(0),
      /* 0x49 */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0x9) : new X86_Dec_OpcodeDecoder(1),
      /* 0x4A */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0xA) : new X86_Dec_OpcodeDecoder(2),
      /* 0x4B */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0xB) : new X86_Dec_OpcodeDecoder(3),
      /* 0x4C */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0xC) : new X86_Dec_OpcodeDecoder(4),
      /* 0x4D */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0xD) : new X86_Dec_OpcodeDecoder(5),
      /* 0x4E */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0xE) : new X86_Dec_OpcodeDecoder(6),
      /* 0x4F */X86_64 ? (X86_InstructionDecoder) new X86_REX_PrefixDecoder(
          0xF) : new X86_Dec_OpcodeDecoder(7),

      /* 0x50 */new X86_Push_OpcodeDecoder(0),
      /* 0x51 */new X86_Push_OpcodeDecoder(1),
      /* 0x52 */new X86_Push_OpcodeDecoder(2),
      /* 0x53 */new X86_Push_OpcodeDecoder(3),
      /* 0x54 */new X86_Push_OpcodeDecoder(4),
      /* 0x55 */new X86_Push_OpcodeDecoder(5),
      /* 0x56 */new X86_Push_OpcodeDecoder(6),
      /* 0x57 */new X86_Push_OpcodeDecoder(7),
      /* 0x58 */new X86_Pop_OpcodeDecoder(0),
      /* 0x59 */new X86_Pop_OpcodeDecoder(1),
      /* 0x5A */new X86_Pop_OpcodeDecoder(2),
      /* 0x5B */new X86_Pop_OpcodeDecoder(3),
      /* 0x5C */new X86_Pop_OpcodeDecoder(4),
      /* 0x5D */new X86_Pop_OpcodeDecoder(5),
      /* 0x5E */new X86_Pop_OpcodeDecoder(6),
      /* 0x5F */new X86_Pop_OpcodeDecoder(7),

      /* 0x60 */new X86_PushA_OpcodeDecoder(),
      /* 0x61 */new X86_PopA_OpcodeDecoder(),
      /* 0x62 */null,
      /* 0x63 */null,
      /* 0x64 */new X86_FS_SegmentOverride_PrefixDecoder(),
      /* 0x65 */new X86_GS_SegmentOverride_PrefixDecoder(),
      /* 0x66 */new X86_OperandSizeOverride_PrefixDecoder(),
      /* 0x67 */new X86_AddressSizeOverride_PrefixDecoder(),
      /* 0x68 */new X86_Push_OpcodeDecoder(_16BIT ? -16 : -32), // Push 16/32bit
      // immediate
      /* 0x69 */new X86_Imul_OpcodeDecoder(_16BIT ? 16 : 32, _16BIT ? 16 : 32, false),
      /* 0x6A */new X86_Push_OpcodeDecoder(-8), // Push 8bit immediate
      /* 0x6B */new X86_Imul_OpcodeDecoder(_16BIT ? 16 : 32, 8, false),
      /* 0x6C */null,
      /* 0x6D */null,
      /* 0x6E */null,
      /* 0x6F */null,

      /* 0x70 */new X86_Jcc_OpcodeDecoder(OVERFLOW, 8),
      /* 0x71 */new X86_Jcc_OpcodeDecoder(NOT_OVERFLOW, 8),
      /* 0x72 */new X86_Jcc_OpcodeDecoder(LOWER, 8),
      /* 0x73 */new X86_Jcc_OpcodeDecoder(HIGHER_EQUAL, 8),
      /* 0x74 */new X86_Jcc_OpcodeDecoder(EQUAL, 8),
      /* 0x75 */new X86_Jcc_OpcodeDecoder(NOT_EQUAL, 8),
      /* 0x76 */new X86_Jcc_OpcodeDecoder(LOWER_EQUAL, 8),
      /* 0x77 */new X86_Jcc_OpcodeDecoder(HIGHER, 8),
      /* 0x78 */new X86_Jcc_OpcodeDecoder(SIGNED, 8),
      /* 0x79 */new X86_Jcc_OpcodeDecoder(NOT_SIGNED, 8),
      /* 0x7A */new X86_Jcc_OpcodeDecoder(PARITY_EVEN, 8),
      /* 0x7B */new X86_Jcc_OpcodeDecoder(PARITY_ODD, 8),
      /* 0x7C */new X86_Jcc_OpcodeDecoder(LESS, 8),
      /* 0x7D */new X86_Jcc_OpcodeDecoder(GREATER_EQUAL, 8),
      /* 0x7E */new X86_Jcc_OpcodeDecoder(LESS_EQUAL, 8),
      /* 0x7F */new X86_Jcc_OpcodeDecoder(GREATER, 8),

      /* 0x80 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          // 8bit, ModRM, 8bit imm, rm is dest
          new X86_Add_OpcodeDecoder(8, true, 8, true),// 0
          new X86_Or_OpcodeDecoder(8, true, 8, true),// 1
          new X86_Adc_OpcodeDecoder(8, true, 8, true),// 2
          new X86_Sbb_OpcodeDecoder(8, true, 8, true),// 3
          new X86_And_OpcodeDecoder(8, true, 8, true),// 4
          new X86_Sub_OpcodeDecoder(8, true, 8, true),// 5
          new X86_Xor_OpcodeDecoder(8, true, 8, true),// 6
          new X86_Cmp_OpcodeDecoder(8, true, 8, true) // 7
      }),
      /* 0x81 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          // 16/32bit, ModRM, 16/32bit imm, rm is dest
          new X86_Add_OpcodeDecoder(_16BIT?16:32, true, _16BIT?16:32, true),// 0
          new X86_Or_OpcodeDecoder(_16BIT?16:32, true, _16BIT?16:32, true),// 1
          new X86_Adc_OpcodeDecoder(_16BIT?16:32, true, _16BIT?16:32, true),// 2
          new X86_Sbb_OpcodeDecoder(_16BIT?16:32, true, _16BIT?16:32, true),// 3
          new X86_And_OpcodeDecoder(_16BIT?16:32, true, _16BIT?16:32, true),// 4
          new X86_Sub_OpcodeDecoder(_16BIT?16:32, true, _16BIT?16:32, true),// 5
          new X86_Xor_OpcodeDecoder(_16BIT?16:32, true, _16BIT?16:32, true),// 6
          new X86_Cmp_OpcodeDecoder(_16BIT?16:32, true, _16BIT?16:32, true) // 7
          }),
      /* 0x82 */null,
      /* 0x83 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          // 16/32bit, ModRM, 8bit imm, rm is dest
          new X86_Add_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true),// 0
          new X86_Or_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true),// 1
          new X86_Adc_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true),// 2
          new X86_Sbb_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true),// 3
          new X86_And_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true),// 4
          new X86_Sub_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true),// 5
          new X86_Xor_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true),// 6
          new X86_Cmp_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true) // 7
      }),
      /* 0x84 */new X86_Test_OpcodeDecoder(8, true, 0),
      // 8bit, has ModRM, no imm
      /* 0x85 */new X86_Test_OpcodeDecoder(_16BIT ? 16 : 32, true, 0),
      // 16/32bit,has ModRM, no imm
      /* 0x86 */null,
      /* 0x87 */null,
      /* 0x88 */new X86_Mov_OpcodeDecoder(8, true, 0, true),
      // 8bit, has ModRM, no imm, rm is dest
      /* 0x89 */new X86_Mov_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true),
      // 16/32bit,has ModRM, no imm, rm is dest
      /* 0x8A */new X86_Mov_OpcodeDecoder(8, true, 0, false),
      // 8bit, has ModRM, no imm, rm is src
      /* 0x8B */new X86_Mov_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, false),
      // 16/32bit,has ModRM, no imm, rm is src
      /* 0x8C */new X86_MovSeg_OpcodeDecoder(true),
      // move Sreg to r/m
      /* 0x8D */new X86_Lea_OpcodeDecoder(),
      /* 0x8E */new X86_MovSeg_OpcodeDecoder(false),
      // move r/m to Sreg
      /* 0x8F */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          new X86_Pop_OpcodeDecoder(-1), // 0 - Pop of memory operand
          null, // 1
          null, // 2
          null, // 3
          null, // 4
          null, // 5
          null, // 6
          null // 7
          }),

      /* 0x90 */new X86_Nop_OpcodeDecoder(),
      /* 0x91 */null,
      /* 0x92 */null,
      /* 0x93 */null,
      /* 0x94 */null,
      /* 0x95 */null,
      /* 0x96 */null,
      /* 0x97 */null,
      /* 0x98 */null,
      /* 0x99 */null,
      /* 0x9A */null,
      /* 0x9B */null,
      /* 0x9C */null,
      /* 0x9D */null,
      /* 0x9E */null,
      /* 0x9F */null,

      /* 0xA0 */new X86_Mov_OpcodeDecoder(8, true), // mov al, [disp8]
      /* 0xA1 */new X86_Mov_OpcodeDecoder(_16BIT ? 16 : 32, false),// mov
      // [e]ax,
      // [disp(16|32)]
      /* 0xA2 */new X86_Mov_OpcodeDecoder(8, false), // mov [disp8], al
      /* 0xA3 */new X86_Mov_OpcodeDecoder(_16BIT ? 16 : 32, true), // mov
      // [disp(16|32)],
      // eax
      /* 0xA4 */new X86_Movs_OpcodeDecoder(8),
      /* 0xA5 */new X86_Movs_OpcodeDecoder(_16BIT ? 16 : 32),
      /* 0xA6 */null,
      /* 0xA7 */null,
      /* 0xA8 */null,
      /* 0xA9 */null,
      /* 0xAA */null,
      /* 0xAB */null,
      /* 0xAC */null,
      /* 0xAD */null,
      /* 0xAE */null,
      /* 0xAF */null,

      // reg, 8bit immediate
      /* 0xB0 */new X86_Mov_OpcodeDecoder(0, 8),
      /* 0xB1 */new X86_Mov_OpcodeDecoder(1, 8),
      /* 0xB2 */new X86_Mov_OpcodeDecoder(2, 8),
      /* 0xB3 */new X86_Mov_OpcodeDecoder(3, 8),
      /* 0xB4 */new X86_Mov_OpcodeDecoder(4, 8),
      /* 0xB5 */new X86_Mov_OpcodeDecoder(5, 8),
      /* 0xB6 */new X86_Mov_OpcodeDecoder(6, 8),
      /* 0xB7 */new X86_Mov_OpcodeDecoder(7, 8),
      // reg, 16/32bit immediate
      /* 0xB8 */new X86_Mov_OpcodeDecoder(0, _16BIT ? 16 : 32),
      /* 0xB9 */new X86_Mov_OpcodeDecoder(1, _16BIT ? 16 : 32),
      /* 0xBA */new X86_Mov_OpcodeDecoder(2, _16BIT ? 16 : 32),
      /* 0xBB */new X86_Mov_OpcodeDecoder(3, _16BIT ? 16 : 32),
      /* 0xBC */new X86_Mov_OpcodeDecoder(4, _16BIT ? 16 : 32),
      /* 0xBD */new X86_Mov_OpcodeDecoder(5, _16BIT ? 16 : 32),
      /* 0xBE */new X86_Mov_OpcodeDecoder(6, _16BIT ? 16 : 32),
      /* 0xBF */new X86_Mov_OpcodeDecoder(7, _16BIT ? 16 : 32),

      /* 0xC0 */null,
      /* 0xC1 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          null, // 0
          null, // 1
          null, // 2
          null, // 3
          new X86_Shl_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true),
          // 4 - 16/32bit, has ModRM, 8bit imm, rm is dest
          new X86_Ushr_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true),
          // 5 - 16/32bit, has ModRM, 8bit imm, rm is dest
          null, // 6
          new X86_Shr_OpcodeDecoder(_16BIT ? 16 : 32, true, 8, true)
          // 7 - 16/32bit, has ModRM, 8bit imm, rm is dest
          }),
      /* 0xC2 */new X86_Ret_OpcodeDecoder(false, 16),
      // near return, 16bit immediate
      /* 0xC3 */new X86_Ret_OpcodeDecoder(false, 0),
      // near return, no immediate
      /* 0xC4 */null,
      /* 0xC5 */null,
      /* 0xC6 */new X86_Mov_OpcodeDecoder(8, true, 8, true),
      // 8bit, has ModRM, 8bit imm, rm is dest
      /* 0xC7 */new X86_Mov_OpcodeDecoder(_16BIT ? 16 : 32, true, _16BIT ? 16
          : 32, true), // 16/32bit, has ModRM, 16/32bit imm, rm is dest
      /* 0xC8 */null,
      /* 0xC9 */new X86_Leave_OpcodeDecoder(),
      /* 0xCA */new X86_Ret_OpcodeDecoder(true, 16),
      // far return, 16bit immediate
      /* 0xCB */new X86_Ret_OpcodeDecoder(true, 0),
      // far return, no immediate
      /* 0xCC */null,
      /* 0xCD */new X86_Int_OpcodeDecoder(),
      /* 0xCE */null,
      /* 0xCF */null,

      /* 0xD0 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          // 8bit shift/rotates by 1
          new X86_Rol_OpcodeDecoder(8),// 0
          new X86_Ror_OpcodeDecoder(8),// 1
          new X86_Rcl_OpcodeDecoder(8),// 2
          new X86_Rcr_OpcodeDecoder(8),// 3
          new X86_Shl_OpcodeDecoder(8),// 4
          new X86_Ushr_OpcodeDecoder(8),// 5
          null, // 6
          new X86_Shr_OpcodeDecoder(8) // 7
          }),
      /* 0xD1 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          // 16/32bit shift/rotates by 1
          new X86_Rol_OpcodeDecoder(_16BIT?16:32),// 0
          new X86_Ror_OpcodeDecoder(_16BIT?16:32),// 1
          new X86_Rcl_OpcodeDecoder(_16BIT?16:32),// 2
          new X86_Rcr_OpcodeDecoder(_16BIT?16:32),// 3
          new X86_Shl_OpcodeDecoder(_16BIT?16:32),// 4
          new X86_Ushr_OpcodeDecoder(_16BIT?16:32),// 5
          null, // 6
          new X86_Shr_OpcodeDecoder(_16BIT?16:32) // 7
          }),
      /* 0xD2 */null,
      /* 0xD3 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          null, // 0
          null, // 1
          null, // 2
          null, // 3
          new X86_Shl_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true), // 4 -
          // 16/32bit,
          // has
          // ModRM,
          // no imm,
          // rm is
          // dest
          new X86_Ushr_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true),// 5 -
          // 16/32bit,
          // has
          // ModRM,
          // no imm,
          // rm is
          // dest
          null, // 6
          new X86_Shr_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, true) // 7 -
          // 16/32bit,
          // has
          // ModRM,
          // no imm,
          // rm is
          // dest
          }),
      /* 0xD4 */null,
      /* 0xD5 */null,
      /* 0xD6 */null,
      /* 0xD7 */null,
      /* 0xD8 */null, // -- CoProcessor opcodes
      /* 0xD9 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          null, // 0
          null, // 1
          null, // 2
          null, // 3
          null, // 4
          new X86_Fldcw_OpcodeDecoder(), // 5
          null, // 6
          new X86_Fstcw_OpcodeDecoder() // 7
          }),
      /* 0xDA */null,
      /* 0xDB */null,
      /* 0xDC */null,
      /* 0xDD */null,
      /* 0xDE */null,
      /* 0xDF */null,

      /* 0xE0 */null,
      /* 0xE1 */null,
      /* 0xE2 */null,
      /* 0xE3 */null,
      /* 0xE4 */null,
      /* 0xE5 */null,
      /* 0xE6 */null,
      /* 0xE7 */null,
      /* 0xE8 */new X86_Call_OpcodeDecoder(_16BIT ? 16 : 32, false,
          _16BIT ? 16 : 32, false), // 16/32bit, no ModRM, 16/32bit imm
      /* 0xE9 */new X86_Jmp_OpcodeDecoder(false, _16BIT ? 16 : 32), // relative
      // jump +
      // 16/32bit
      // immediate
      /* 0xEA */null,
      /* 0xEB */new X86_Jmp_OpcodeDecoder(false, 8), // relative jump + 8bit
      // immediate
      /* 0xEC */null,
      /* 0xED */null,
      /* 0xEE */null,
      /* 0xEF */null,

      /* 0xF0 */new X86_Lock_PrefixDecoder(),
      /* 0xF1 */null,
      /* 0xF2 */new X86_RepNE_PrefixDecoder(),
      /* 0xF3 */new X86_Rep_PrefixDecoder(),
      /* 0xF4 */null,
      /* 0xF5 */null,//complement carry
      /* 0xF6 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {// 8bit,
          // ModRM, 8bit imm, rm is dest
              new X86_Test_OpcodeDecoder(8, true, 8), // 0
              null, // 1
              new X86_Not_OpcodeDecoder(8), // 2 - 8bit
              new X86_Neg_OpcodeDecoder(8), // 3 - 8bit
              new X86_Mul_OpcodeDecoder(8), // 4 - 8bit
              null, // 5
              new X86_Div_OpcodeDecoder(8), // 6 - 8bit
              null // 7
          }),
      /* 0xF7 */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {// 16/32bit,
          // ModRM,
              // 16/32bit
              // imm,
              // rm
              // is
              // dest
              new X86_Test_OpcodeDecoder(_16BIT ? 16 : 32, true, _16BIT ? 16
                  : 32),// 0
              null, // 1
              new X86_Not_OpcodeDecoder(_16BIT ? 16 : 32), // 2 - 16/32bit
              new X86_Neg_OpcodeDecoder(_16BIT ? 16 : 32), // 3 - 16/32bit
              new X86_Mul_OpcodeDecoder(_16BIT ? 16 : 32), // 4 - 16/32bit
              null, // 5
              new X86_Div_OpcodeDecoder(_16BIT ? 16 : 32), // 6 - 16/32bit
              null // 7
          }),
      /* 0xF8 */new X86_MoveToFlag_OpcodeDecoder(FLAG.CARRY,false), // clc
      /* 0xF9 */new X86_MoveToFlag_OpcodeDecoder(FLAG.CARRY,true),  // stc
      /* 0xFA */new X86_MoveToFlag_OpcodeDecoder(FLAG.INTERRUPT,false), // cli
      /* 0xFB */new X86_MoveToFlag_OpcodeDecoder(FLAG.INTERRUPT,true),  // sti
      /* 0xFC */new X86_MoveToFlag_OpcodeDecoder(FLAG.DIRECTION,false), // cld
      /* 0xFD */new X86_MoveToFlag_OpcodeDecoder(FLAG.DIRECTION,true),  // std
      /* 0xFE */null,
      /* 0xFF */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
          new X86_Inc_OpcodeDecoder(-1), // 0 - Inc of memory operand
          null, // 1
          new X86_Call_OpcodeDecoder(_16BIT ? 16 : 32, true, 0, false), // 2 -
          // 16/32bit,
          // ModRM,
          // no
          // imm
          null, // 3
          new X86_Jmp_OpcodeDecoder(true, 0), // 4 - near absolute jump to ModRM
          null, // 5
          new X86_Push_OpcodeDecoder(-1), // 6 - Push of memory operand
          null // 7
          }) };

  /**
   * Utility to get a decoder for a particular opcode
   */
  protected static X86_InstructionDecoder primaryOpcodeLookup(int opcode) {
    if (primaryOpcodes[opcode] == null) {
      throw new Error("Opcode 0x" + Integer.toHexString(opcode) + " not found");
    } else {
      return primaryOpcodes[opcode];
    }
  }

  /**
   * Decoder that represents a bad instruction
   */
  protected static X86_BadInstructionDecoder badInstructionDecoder = new X86_BadInstructionDecoder();

  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int opcode = ps.memory.loadInstruction8(pc + offset);
    offset++;
    return primaryOpcodeLookup(opcode).getDecoder(ps, pc, offset, prefix1,
        prefix2, prefix3, prefix4, prefix5);
  }

  /**
   * Get the decoder
   */
  public static X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc) {
    int opcode = ps.memory.loadInstruction8(pc);
    return primaryOpcodeLookup(opcode).getDecoder(ps, pc, 1, null, null, null,
        null, null);
  }

  /**
   * Disassemble a single instruction
   * @param ps the process space of the translation
   * @param pc the address of the instruction to translate
   * @return the string for this instruction
   */
  public String disassemble(ProcessSpace ps, int pc) {
    return "TODO";
  }

  /**
   * Translate a single instruction
   * @param translationHelper the object containing the translation sequence
   * @param ps the process space of the translation
   * @param pc the address of the instruction to translate
   * @return the address of the next instruction or -1 if this instruction has
   *         branched to the end of the trace
   */
  public int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc) {
    TODO();
    return 0xEBADC0DE;
  }

  /**
   * Translate a single instruction which doesn't already have a decoder
   * @param translationHelper the object containing the translation sequence
   * @param ps the process space of the translation
   * @param pc the address of the instruction to translate
   * @return the address of the next instruction or -1 if this instruction has
   *         branched to the end of the trace
   */
  public static int translateInstruction(X862IR translationHelper,
      ProcessSpace ps, X86_Laziness lazy, int pc) {
    X86_InstructionDecoder decoder = getDecoder(ps, pc);
    if (DBT_Options.debugInstr) {
      System.err.println(decoder.disassemble(ps, pc));
    }
    return decoder.translate(translationHelper, ps, lazy, pc);
  }

  /**
   * Interpret a single instruction
   * @param ps the process space of the interpretation, contains the fetched
   *          instruction and instruction address
   * @return the next instruction interpreter
   */
  public InstructionDecoder interpret(ProcessSpace ps, int pc)
      throws BadInstructionException {
    X86_InstructionDecoder decoder = getDecoder(ps, pc);
    System.err.println("Attempt to interpret " + decoder.disassemble(ps, pc));
    TODO();
    return null;
  }

  /**
   * Die with an error
   */
  protected void TODO() {
    DBT_OptimizingCompilerException.TODO();
  }
}

// -oO Virtual decoders Oo-

/**
 * A decoder that faults with a bad instruction exception
 */
class X86_BadInstructionDecoder extends X86_InstructionDecoder {
  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    return this;
  }
  /**
   * Translate a single instruction
   * @param translationHelper the object containing the translation sequence
   * @param ps the process space of the translation
   * @param pc the address of the instruction to translate
   * @return the address of the next instruction or -1 if this instruction has
   *         branched to the end of the trace
   */
  public int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc) {
    translationHelper.appendThrowBadInstruction(lazy, pc);
    return -1;
  }
}

/**
 * A decoder that wraps a number of prefixs, opcode and operand decoders
 * together
 */
class X86_GenericDecoder extends X86_InstructionDecoder {
  /**
   * 1st instruction prefix decoder
   */
  private final X86_Group1PrefixDecoder prefix1;

  /**
   * 2nd instruction prefix decoder
   */
  private final X86_Group2PrefixDecoder prefix2;

  /**
   * 3rd instruction prefix decoder
   */
  private final X86_Group3PrefixDecoder prefix3;

  /**
   * 4th instruction prefix decoder
   */
  private final X86_Group4PrefixDecoder prefix4;

  /**
   * 5th instruction prefix decoder
   */
  private final X86_Group5PrefixDecoder prefix5;

  /**
   * Instruction opcode decoder
   */
  private final X86_OpcodeDecoder opcode;

  /**
   * Instruction ModRM decoder
   */
  private final X86_ModRM_Decoder modrm;

  /**
   * Instruction SIB decoder
   */
  private final X86_SIB_Decoder sib;

  /**
   * Instruction displacement
   */
  private final int displacement;

  /**
   * What's the size of the instruction's immediate operand
   */
  private final int immediateSize;

  /**
   * Instruction immediate if there's one
   */
  private final int immediate;

  /**
   * Length of the instruction
   */
  private final int length;

  /**
   * Constructor
   */
  X86_GenericDecoder(X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5,
      X86_OpcodeDecoder opcode, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length) {
    this.prefix1 = prefix1;
    this.prefix2 = prefix2;
    this.prefix3 = prefix3;
    this.prefix4 = prefix4;
    this.prefix5 = prefix5;
    this.opcode = opcode;
    this.modrm = modrm;
    this.sib = sib;
    this.displacement = displacement;
    this.immediateSize = immediateSize;
    this.immediate = immediate;
    this.length = length;
  }

  /**
   * Translate a single instruction
   * @param translationHelper the object containing the translation sequence
   * @param ps the process space of the translation
   * @param pc the address of the instruction to translate
   * @return the address of the next instruction or -1 if this instruction has
   *         branched to the end of the trace
   */
  public int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc) {
    if (prefix1 != null)
      prefix1.startPrefix(translationHelper, lazy);

    int nextPC = opcode.translate(translationHelper, ps, lazy, pc, modrm, sib,
        displacement, immediateSize, immediate, length, prefix2, prefix3,
        prefix4, prefix5);

    if (prefix1 != null)
      prefix1.endPrefix(translationHelper, lazy);

    return nextPC;
  }

  /**
   * Disassemble a single instruction
   * @param ps the process space of the translation
   * @param pc the address of the instruction to translate
   * @return the string for this instruction
   */
  public String disassemble(ProcessSpace ps, int pc) {
    String result = "";

    if (prefix1 != null)
      result += prefix1.disassemble(ps, pc);

    result += opcode.disassemble(ps, pc, modrm, sib, displacement,
        immediateSize, immediate, length, prefix2, prefix3, prefix4, prefix5);
    return result;
  }
}

// -oO Prefix Decoders Oo-

/**
 * Decoder for X86 prefix opcodes
 */
abstract class X86_PrefixDecoder extends X86_InstructionDecoder {
}

/**
 * Decoder for X86 group 1 prefix opcodes
 */
abstract class X86_Group1PrefixDecoder extends X86_PrefixDecoder {
  /**
   * Start an instruction prefix
   */
  abstract void startPrefix(X862IR translationHelper, X86_Laziness lazy);

  /**
   * End an instruction prefix
   */
  abstract void endPrefix(X862IR translationHelper, X86_Laziness lazy);

  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    if (prefix1 == null)
      return super.getDecoder(ps, pc, offset, this, prefix2, prefix3, prefix4,
          prefix5);
    else
      return badInstructionDecoder;
  }
}

/**
 * Decoder for X86 group 2 prefix opcodes
 */
abstract class X86_Group2PrefixDecoder extends X86_PrefixDecoder {
  /**
   * Return the segment for this segment override
   */
  abstract int getSegment();

  /**
   * Return the likely hint this prefix encodes
   */
  abstract boolean getLikely();

  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    if (prefix2 == null)
      return super.getDecoder(ps, pc, offset, prefix1, this, prefix3, prefix4,
          prefix5);
    else
      return badInstructionDecoder;
  }
}

/**
 * Decoder for X86 group 3 prefix opcodes
 */
abstract class X86_Group3PrefixDecoder extends X86_PrefixDecoder {
  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    if (prefix3 == null)
      return super.getDecoder(ps, pc, offset, prefix1, prefix2, this, prefix4,
          prefix5);
    else
      return badInstructionDecoder;
  }
}

/**
 * Decoder for X86 group 4 prefix opcodes
 */
abstract class X86_Group4PrefixDecoder extends X86_PrefixDecoder {
  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    if (prefix4 == null)
      return super.getDecoder(ps, pc, offset, prefix1, prefix2, prefix3, this,
          prefix5);
    else
      return badInstructionDecoder;
  }
}

/**
 * Decoder for X86 group 5 prefix opcodes
 */
abstract class X86_Group5PrefixDecoder extends X86_PrefixDecoder {
  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    if (X86_64 && (prefix5 == null))
      return super.getDecoder(ps, pc, offset, prefix1, prefix2, prefix3,
          prefix4, this);
    else
      return badInstructionDecoder;
  }
}

// -oO Group 1 prefixs Oo-

/**
 * Decoder for X86 lock prefix
 */
class X86_Lock_PrefixDecoder extends X86_Group1PrefixDecoder {
  /**
   * Start an instruction prefix
   */
  void startPrefix(X862IR translationHelper, X86_Laziness lazy) {
    TODO();
  }

  /**
   * End an instruction prefix
   */
  void endPrefix(X862IR translationHelper, X86_Laziness lazy) {
    TODO();
  }

  /**
   * Disassemble a single prefix
   * @param ps the process space of the translation
   * @param pc the address of the instruction to translate
   * @return the string for this prefic
   */
  public String disassemble(ProcessSpace ps, int pc) {
    return "lock ";
  }
}

/**
 * Decoder for X86 repne/repnz prefix
 */
class X86_RepNE_PrefixDecoder extends X86_Group1PrefixDecoder {
  /**
   * Start an instruction prefix
   */
  void startPrefix(X862IR translationHelper, X86_Laziness lazy) {
    TODO();
  }

  /**
   * End an instruction prefix
   */
  void endPrefix(X862IR translationHelper, X86_Laziness lazy) {
    TODO();
  }
  /**
   * Disassemble a single prefix
   * @param ps the process space of the translation
   * @param pc the address of the instruction to translate
   * @return the string for this prefic
   */
  public String disassemble(ProcessSpace ps, int pc) {
    return "repne ";
  }
}

/**
 * Decoder for X86 rep or repe/repz prefix
 */
class X86_Rep_PrefixDecoder extends X86_Group1PrefixDecoder {
  /**
   * Block containing instruction to repeat
   */
  private OPT_BasicBlock instructionBlock;
  /**
   * Start an instruction prefix
   */
  void startPrefix(X862IR translationHelper, X86_Laziness lazy) {
    instructionBlock = translationHelper.createBlockAfterCurrent();
    translationHelper.setCurrentBlock(instructionBlock);
  }

  /**
   * End an instruction prefix
   */
  void endPrefix(X862IR translationHelper, X86_Laziness lazy) {
    // TODO: handle 16bit addresses
    OPT_RegisterOperand ecx = translationHelper.getGPRegister(lazy,
        X86_Registers.ECX, 32);

    translationHelper.appendInstruction(
        Binary.create(INT_ADD, ecx, ecx.copyRO(), new OPT_IntConstantOperand(-1)));

    OPT_RegisterOperand guardResult = translationHelper.getTempValidation(0);
    translationHelper.appendInstruction(
        IfCmp.create(INT_IFCMP, guardResult, ecx.copyRO(), new OPT_IntConstantOperand(0),
            OPT_ConditionOperand.NOT_EQUAL(), instructionBlock.makeJumpTarget(),
            OPT_BranchProfileOperand.likely()));

    instructionBlock.insertOut(instructionBlock);
    OPT_BasicBlock nextBlock = translationHelper.createBlockAfterCurrent();
    translationHelper.setCurrentBlock(nextBlock);
  }
  /**
   * Disassemble a single prefix
   * @param ps the process space of the translation
   * @param pc the address of the instruction to translate
   * @return the string for this prefic
   */
  public String disassemble(ProcessSpace ps, int pc) {
    return "rep ";
  }
}

// -oO Group 2 prefixs Oo-

/**
 * Decoder for X86 CS segment override or branch not likely prefix
 */
class X86_CS_SegmentOverride_PrefixDecoder extends X86_Group2PrefixDecoder {
  /**
   * Return the segment for this segment override
   */
  int getSegment() {
    return X86_Registers.CS;
  }

  /**
   * Return the likely hint this prefix encodes
   */
  boolean getLikely() {
    return true;
  }
}

/**
 * Decoder for X86 SS segment override
 */
class X86_SS_SegmentOverride_PrefixDecoder extends X86_Group2PrefixDecoder {
  /**
   * Return the segment for this segment override
   */
  int getSegment() {
    return X86_Registers.SS;
  }

  /**
   * Return the likely hint this prefix encodes
   */
  boolean getLikely() {
    throw new Error("Not a valid branch prefix");
  }
}

/**
 * Decoder for X86 DS segment override or branch likely prefix
 */
class X86_DS_SegmentOverride_PrefixDecoder extends X86_Group2PrefixDecoder {
  /**
   * Return the segment for this segment override
   */
  int getSegment() {
    return X86_Registers.DS;
  }

  /**
   * Return the likely hint this prefix encodes
   */
  boolean getLikely() {
    return false;
  }
}

/**
 * Decoder for X86 ES segment override
 */
class X86_ES_SegmentOverride_PrefixDecoder extends X86_Group2PrefixDecoder {
  /**
   * Return the segment for this segment override
   */
  int getSegment() {
    return X86_Registers.ES;
  }

  /**
   * Return the likely hint this prefix encodes
   */
  boolean getLikely() {
    throw new Error("Not a valid branch prefix");
  }
}

/**
 * Decoder for X86 FS segment override
 */
class X86_FS_SegmentOverride_PrefixDecoder extends X86_Group2PrefixDecoder {
  /**
   * Return the segment for this segment override
   */
  int getSegment() {
    return X86_Registers.FS;
  }

  /**
   * Return the likely hint this prefix encodes
   */
  boolean getLikely() {
    throw new Error("Not a valid branch prefix");
  }
}

/**
 * Decoder for X86 GS segment override
 */
class X86_GS_SegmentOverride_PrefixDecoder extends X86_Group2PrefixDecoder {
  /**
   * Return the segment for this segment override
   */
  int getSegment() {
    return X86_Registers.GS;
  }

  /**
   * Return the likely hint this prefix encodes
   */
  boolean getLikely() {
    throw new Error("Not a valid branch prefix");
  }
}

// -oO Group 3 prefixs Oo-

/**
 * Decoder for X86 operand size override
 */
class X86_OperandSizeOverride_PrefixDecoder extends X86_Group3PrefixDecoder {
}

// -oO Group 4 prefixs Oo-

/**
 * Decoder for X86 address size override
 */
class X86_AddressSizeOverride_PrefixDecoder extends X86_Group4PrefixDecoder {
}

// -oO Group 5 prefixs Oo-

/**
 * Decoder for X86_64 REX prefix byte
 */
class X86_REX_PrefixDecoder extends X86_Group5PrefixDecoder {
  /**
   * 0 = operand size determined by CS.D 1 = 64 bit operand size
   */
  boolean W;

  /**
   * Extension of the ModR/M reg field
   */
  boolean R;

  /**
   * Extension of the SIB index field
   */
  boolean X;

  /**
   * Extension of the ModR/M r/m field, SIB base field, or Opcode reg field
   */
  boolean B;

  /**
   * Constructor
   */
  X86_REX_PrefixDecoder(int prefix) {
    W = (prefix & 8) != 0;
    R = (prefix & 4) != 0;
    X = (prefix & 2) != 0;
    B = (prefix & 1) != 0;
  }
}

// -oO Secondary opcode decoders Oo-

class X86_OpcodeInModRMReg_Decoder extends X86_InstructionDecoder {
  /**
   * Array of secondary decoders
   */
  private final X86_OpcodeDecoder secondaryDecoders[];

  /**
   * Constructor
   */
  X86_OpcodeInModRMReg_Decoder(X86_OpcodeDecoder secondaryDecoders[]) {
    this.secondaryDecoders = secondaryDecoders;
  }

  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    X86_ModRM_Decoder modrm = X86_ModRM_Decoder.getModRM_Decoder(ps.memory
        .loadInstruction8(pc + offset));
    X86_OpcodeDecoder decoder = secondaryDecoders[modrm.getOpcode()];
    if (decoder != null)
      return decoder.getDecoder(ps, pc, offset + 1, prefix1, prefix2, prefix3,
          prefix4, prefix5, modrm);
    else
      throw new Error("Opcode in ModRM 0x"
          + Integer.toHexString(modrm.getOpcode()) + " not found");
  }
}

// -oO Opcode decoders Oo-

/**
 * The decoder for the opcode of the instruction
 */
abstract class X86_OpcodeDecoder extends X86_InstructionDecoder implements
    OPT_Operators {
  /**
   * Size of register and/or memory values
   */
  final int operandSize;

  /**
   * Does a ModRM byte follow the opcode possibly giving more information on the
   * opcode as well as defining register and memory operands
   */
  final boolean hasModRM;

  /**
   * Does the ModRM byte contain an opcode
   */
  final boolean modRMhasOpcode;

  /**
   * immediateSize the size in bits of any immediate or 0 if no immediate value
   */
  final int immediateSize;

  /**
   * If we have a ModRM byte then is the memory operand the destination or
   * source for this instruction
   */
  final boolean isMemoryOperandDestination;

  /**
   * An implicit register if one is specified (default to EAX)
   */
  final int register;

  /**
   * Discard the result (ie. perform the operation with the destination and
   * source, but then don't write the result back to the destination). Useful
   * for cmp and test.
   */
  final boolean discardResult;

  /**
   * This opcode always has a displacement regardless of the modrm byte
   */
  final int displacementSize;

  /**
   * Constructor
   * @param operandSize size of register/mem/immediate operands
   * @param hasModRM does a ModRM byte follow the opcode possibly giving more
   *          information on the opcode as well as defining register and memory
   *          operands?
   * @param immediateSize the size in bits of any immediate or 0 if no immediate
   *          value
   * @param isMemoryOperandDestination is the destination/result of this
   *          instruction a memory or register in the case that there's a ModRM
   *          byte
   */
  X86_OpcodeDecoder(int operandSize, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    this.operandSize = operandSize;
    this.hasModRM = hasModRM;
    this.modRMhasOpcode = false;
    this.immediateSize = immediateSize;
    this.isMemoryOperandDestination = isMemoryOperandDestination;
    this.register = X86_Registers.EAX;
    this.discardResult = false;
    this.displacementSize = 0;
  }

  /**
   * Constructor
   * @param operandSize size of register/mem/immediate operands
   * @param hasModRM does a ModRM byte follow the opcode possibly giving more
   *          information on the opcode as well as defining register and memory
   *          operands?
   * @param immediateSize the size in bits of any immediate or 0 if no immediate
   *          value
   * @param isMemoryOperandDestination is the destination/result of this
   *          instruction a memory or register in the case that there's a ModRM
   *          byte
   * @param register override EAX as the implicit register for an instruction
   *          not specifying modrm
   */
  X86_OpcodeDecoder(int operandSize, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination, int register) {
    this.operandSize = operandSize;
    this.hasModRM = hasModRM;
    this.modRMhasOpcode = true; // override the register to show that the reg of
    // the modrm is invalid
    this.immediateSize = immediateSize;
    this.isMemoryOperandDestination = isMemoryOperandDestination;
    this.register = register;
    this.discardResult = false;
    this.displacementSize = 0;
  }

  /**
   * Constructor
   * @param operandSize size of register/mem/immediate operands
   * @param hasModRM does a ModRM byte follow the opcode possibly giving more
   *          information on the opcode as well as defining register and memory
   *          operands?
   * @param immediateSize the size in bits of any immediate or 0 if no immediate
   *          value
   * @param isMemoryOperandDestination is the destination/result of this
   *          instruction a memory or register in the case that there's a ModRM
   *          byte
   * @param discardResult should the result of the operation be written to the
   *          destination or just the flags modified?
   */
  X86_OpcodeDecoder(int operandSize, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination, boolean discardResult) {
    this.operandSize = operandSize;
    this.hasModRM = hasModRM;
    this.modRMhasOpcode = false;
    this.immediateSize = immediateSize;
    this.isMemoryOperandDestination = isMemoryOperandDestination;
    this.register = X86_Registers.EAX;
    this.discardResult = discardResult;
    this.displacementSize = 0;
  }

  /**
   * Constructor
   * @param operandSize size of register/mem/immediate operands
   * @param isMemoryOperandDestination is the destination/result of this
   *          instruction a memory or register in the case that there's a ModRM
   *          byte
   * @param displacementSize a size for a displacement always present regardless
   *          of modrm
   */
  X86_OpcodeDecoder(int operandSize, boolean isMemoryOperandDestination,
      int displacementSize) {
    this.operandSize = operandSize;
    this.hasModRM = false;
    this.modRMhasOpcode = false;
    this.immediateSize = 0;
    this.isMemoryOperandDestination = isMemoryOperandDestination;
    this.register = X86_Registers.EAX;
    this.discardResult = false;
    this.displacementSize = displacementSize;
  }

  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    // start off with all operands null
    X86_ModRM_Decoder modrm = null;
    X86_SIB_Decoder sib = null;
    int displacementSize = 0;
    int displacement = 0;
    int immediate = 0;
    // Decode ModRM
    if (hasModRM) {
      modrm = X86_ModRM_Decoder.getModRM_Decoder(ps.memory.loadInstruction8(pc
          + offset));
      offset++;
      // Possibly decode an extra SIB byte
      if (modrm.hasSIB()) {
        sib = X86_SIB_Decoder.getSIB_Decoder(ps.memory.loadInstruction8(pc
            + offset));
        offset++;
      }
      displacementSize = modrm.displacementSize(prefix4 != null, sib);
    } else {
      if (prefix4 == null) {
        displacementSize = this.displacementSize;
      } else {
        switch (this.displacementSize) {
        case 32:
          displacementSize = 16;
          break;
        case 16:
          displacementSize = 32;
          break;
        default:
          throw new Error("Unexpected address size override");
        }
      }
    }
    switch (displacementSize) {
    case 32:
      displacement = ps.memory.load32(pc + offset);
      offset += 4;
      break;
    case 16:
      displacement = ps.memory.loadSigned16(pc + offset);
      offset += 2;
      break;
    case 8:
      displacement = ps.memory.loadSigned8(pc + offset);
      offset++;
      break;
    default: // no displacement
      break;
    }
    // Decode Immediate
    switch (immediateSize) {
    case 32:
      if (prefix3 == null) {
        immediate = ps.memory.load32(pc + offset);
        offset += 4;
      } else {
        immediate = ps.memory.loadSigned16(pc + offset);
        offset += 2;
      }
      break;
    case 16:
      if (prefix3 != null) {
        immediate = ps.memory.load32(pc + offset);
        offset += 4;
      } else {
        immediate = ps.memory.loadSigned16(pc + offset);
        offset += 2;
      }
      break;
    case 8:
      immediate = ps.memory.loadSigned8(pc + offset);
      offset++;
      break;
    case 1: // Special case for shift/rotates by 1
      immediate = 1;
      break;
    default: // no immediate
      break;
    }
    // Wrap this in a generic decoder
    return new X86_GenericDecoder(prefix1, prefix2, prefix3, prefix4, prefix5,
        this, modrm, sib, displacement, immediateSize, immediate, offset);
  }

  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5,
      X86_ModRM_Decoder modrm) {
    // start off with all operands null
    X86_SIB_Decoder sib = null;
    int displacement = 0;
    int immediate = 0;
    // Possibly decode an extra SIB byte
    if (modrm.hasSIB()) {
      sib = X86_SIB_Decoder.getSIB_Decoder(ps.memory.loadInstruction8(pc
          + offset));
      offset++;
    }
    switch (modrm.displacementSize(prefix4 != null, sib)) {
    case 32:
      displacement = ps.memory.load32(pc + offset);
      offset += 4;
      break;
    case 16:
      displacement = ps.memory.loadSigned16(pc + offset);
      offset += 2;
      break;
    case 8:
      displacement = ps.memory.loadSigned8(pc + offset);
      offset++;
      break;
    default: // no displacement
      break;
    }
    // Decode Immediate
    switch (immediateSize) {
    case 32:
      if (prefix3 == null) {
        immediate = ps.memory.load32(pc + offset);
        offset += 4;
      } else {
        immediate = ps.memory.loadSigned16(pc + offset);
        offset += 2;
      }
      break;
    case 16:
      if (prefix3 != null) {
        immediate = ps.memory.load32(pc + offset);
        offset += 4;
      } else {
        immediate = ps.memory.loadSigned16(pc + offset);
        offset += 2;
      }
      break;
    case 8:
      immediate = ps.memory.loadSigned8(pc + offset);
      offset++;
      break;
    case 1: // Special case for shift/rotates by 1
      immediate = 1;
      break;
    default: // no immediate
      break;
    }
    // Wrap this in a generic decoder
    return new X86_GenericDecoder(prefix1, prefix2, prefix3, prefix4, prefix5,
        this, modrm, sib, displacement, immediateSize, immediate, offset);
  }

  /**
   * Get the decoder with no known prefixs, opcode or operands yet
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset) {
    // call the decoder above
    return getDecoder(ps, pc, offset, null, null, null, null, null);
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else {
      switch (this.operandSize) {
      case 32:
        operandSize = 16;
        break;
      case 16:
        operandSize = 32;
        break;
      default:
        operandSize = -1;
        DBT_OptimizingCompilerException.UNREACHABLE();
      }
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination;
    X86_DecodedOperand source = null;
    if (modrm != null) {
      if (isMemoryOperandDestination) {
        destination = modrm.getRM(translationHelper, lazy, sib, displacement,
            operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
                : X86_Registers.DS);
        if (immediateSize == 0) {
          if (modRMhasOpcode) {
            source = X86_DecodedOperand.getRegister(register, operandSize);
          } else {
            source = modrm.getReg(operandSize);
          }
        } else {
          source = X86_DecodedOperand.getImmediate(immediate);
        }
      } else {
        if (modRMhasOpcode) {
          destination = X86_DecodedOperand.getRegister(register, operandSize);
        } else {
          destination = modrm.getReg(operandSize);
        }
        source = modrm.getRM(translationHelper, lazy, sib, displacement,
            operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
                : X86_Registers.DS);
      }
    } else {
      if (immediateSize > 0) {
        destination = X86_DecodedOperand.getRegister(register, operandSize);
        source = X86_DecodedOperand.getImmediate(immediate);
      } else {
        if (isMemoryOperandDestination) {
          destination = X86_DecodedOperand.getMemory(
              (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS, -1,
              0, -1, displacement, addressSize, operandSize);
          source = X86_DecodedOperand.getRegister(register, operandSize);
        } else {
          source = X86_DecodedOperand.getMemory((prefix2 != null) ? prefix2
              .getSegment() : X86_Registers.DS, -1, 0, -1, displacement,
              addressSize, operandSize);
          destination = X86_DecodedOperand.getRegister(register, operandSize);
        }
      }
    }
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    OPT_RegisterOperand sourceOp1 = translationHelper.getTempInt(1);
    source.readToRegister(translationHelper, lazy, sourceOp1);
    OPT_Operator operator = getOperator();
    if (operator != INT_MOVE) {
      OPT_RegisterOperand sourceOp2 = translationHelper.getTempInt(2);
      destination.readToRegister(translationHelper, lazy, sourceOp2);
      translationHelper.appendInstruction(Binary.create(operator,
          temp, sourceOp2.copyRO(), sourceOp1.copyRO()));
      setCarryFlag(translationHelper, temp.copyRO(), sourceOp2.copyRO(),
          sourceOp1.copyRO());
      setSignFlag(translationHelper, temp.copyRO());
      setZeroFlag(translationHelper, temp.copyRO());
      setOverflowFlag(translationHelper, temp.copyRO(), sourceOp2.copyRO(),
          sourceOp1.copyRO());
    } else {
      // Nothing to do for a move
      translationHelper.appendInstruction(Move.create(operator,
          temp, sourceOp1.copyRO()));
    }
    if (!discardResult) {
      destination.writeValue(translationHelper, lazy, temp.copyRO());
    }
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Set the carry flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setCarryFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand carry = translationHelper.getCarryFlag();
    translationHelper.appendInstruction(Move.create(INT_MOVE,
        carry, new OPT_IntConstantOperand(0)));
  }

  /**
   * Set the sign flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setSignFlag(X862IR translationHelper,
      OPT_RegisterOperand result) {
    OPT_RegisterOperand sign = translationHelper.getSignFlag();
    // sign := result < 0
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, sign, result, new OPT_IntConstantOperand(0),
        OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
  }

  /**
   * Set the sign flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setZeroFlag(X862IR translationHelper,
      OPT_RegisterOperand result) {
    OPT_RegisterOperand sign = translationHelper.getZeroFlag();
    // zero := result == 0
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, sign, result, new OPT_IntConstantOperand(0),
        OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
  }

  /**
   * Set the overflow flag following a computation. 1 - true/set; 0 -
   * false/clear
   */
  protected void setOverflowFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand overflow = translationHelper.getOverflowFlag();
    translationHelper.appendInstruction(Move.create(INT_MOVE,
        overflow, new OPT_IntConstantOperand(0)));
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else {
      switch (this.operandSize) {
      case 32:
        operandSize = 16;
        break;
      case 16:
        operandSize = 32;
        break;
      default:
        operandSize = -1;
        DBT_OptimizingCompilerException.UNREACHABLE();
      }
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    String destination;
    String source = null;
    if (modrm != null) {
      if (isMemoryOperandDestination) {
        destination = modrm.disassembleRM(sib, displacement, operandSize,
            addressSize, (prefix2 != null) ? prefix2.getSegment()
                : X86_Registers.DS);
        if (immediateSize == 0) {
          source = modrm.disassembleReg(operandSize);
        } else {
          source = Integer.toString(immediate);
        }
      } else {
        destination = modrm.disassembleReg(operandSize);
        source = modrm.disassembleRM(sib, displacement, operandSize,
            addressSize, (prefix2 != null) ? prefix2.getSegment()
                : X86_Registers.DS);
      }
    } else {
      if (immediateSize > 0) {
        destination = X86_Registers.disassemble(register, operandSize);
        source = Integer.toString(immediate);
      } else {
        if (isMemoryOperandDestination) {
          destination = X86_Registers
              .disassembleSegment((prefix2 != null) ? prefix2.getSegment()
                  : X86_Registers.DS)
              + ":[0x" + Integer.toHexString(displacement) + "]";
          source = X86_Registers.disassemble(register, operandSize);
        } else {
          source = X86_Registers.disassembleSegment((prefix2 != null) ? prefix2
              .getSegment() : X86_Registers.DS)
              + ":[0x" + Integer.toHexString(displacement) + "]";
          destination = X86_Registers.disassemble(register, operandSize);
        }
      }
    }
    return getOperatorString() + " " + destination + ", " + source;
  }

  /**
   * Return the operator for this opcode
   */
  String getOperatorString() {
    return "Unknown";
  }
}

/**
 * The decoder for the escape opcode
 */
final class X86_Escape_OpcodeDecoder extends X86_InstructionDecoder implements
    OPT_Operators {
  /**
   * Look up table to find secondary opcode translator
   */
  private static final X86_InstructionDecoder[] secondaryOpcodes = {
  /* 0x00 */null,
  /* 0x01 */null,
  /* 0x02 */null,
  /* 0x03 */null,
  /* 0x04 */null,
  /* 0x05 */null,
  /* 0x06 */null,
  /* 0x07 */null,
  /* 0x08 */null,
  /* 0x09 */null,
  /* 0x0A */null,
  /* 0x0B */null,
  /* 0x0C */null,
  /* 0x0D */null,
  /* 0x0E */null,
  /* 0x0F */null,

  /* 0x10 */null,
  /* 0x11 */null,
  /* 0x12 */null,
  /* 0x13 */null,
  /* 0x14 */null,
  /* 0x15 */null,
  /* 0x16 */null,
  /* 0x17 */null,
  /* 0x18 */null,
  /* 0x19 */null,
  /* 0x1A */null,
  /* 0x1B */null,
  /* 0x1C */null,
  /* 0x1D */null,
  /* 0x1E */null,
  /* 0x1F */null,

  /* 0x20 */null,
  /* 0x21 */null,
  /* 0x22 */null,
  /* 0x23 */null,
  /* 0x24 */null,
  /* 0x25 */null,
  /* 0x26 */null,
  /* 0x27 */null,
  /* 0x28 */null,
  /* 0x29 */null,
  /* 0x2A */null,
  /* 0x2B */null,
  /* 0x2C */null,
  /* 0x2D */null,
  /* 0x2E */null,
  /* 0x2F */null,

  /* 0x30 */null,
  /* 0x31 */new X86_Rdtsc_OpcodeDecoder(),
  /* 0x32 */null,
  /* 0x33 */null,
  /* 0x34 */null,
  /* 0x35 */null,
  /* 0x36 */null,
  /* 0x37 */null,
  /* 0x38 */null,
  /* 0x39 */null,
  /* 0x3A */null,
  /* 0x3B */null,
  /* 0x3C */null,
  /* 0x3D */null,
  /* 0x3E */null,
  /* 0x3F */null,

  /* 0x40 */new X86_Cmov_OpcodeDecoder(OVERFLOW),
  /* 0x41 */new X86_Cmov_OpcodeDecoder(NOT_OVERFLOW),
  /* 0x42 */new X86_Cmov_OpcodeDecoder(LOWER),
  /* 0x43 */new X86_Cmov_OpcodeDecoder(HIGHER_EQUAL),
  /* 0x44 */new X86_Cmov_OpcodeDecoder(EQUAL),
  /* 0x45 */new X86_Cmov_OpcodeDecoder(NOT_EQUAL),
  /* 0x46 */new X86_Cmov_OpcodeDecoder(LOWER_EQUAL),
  /* 0x47 */new X86_Cmov_OpcodeDecoder(HIGHER),
  /* 0x48 */new X86_Cmov_OpcodeDecoder(SIGNED),
  /* 0x49 */new X86_Cmov_OpcodeDecoder(NOT_SIGNED),
  /* 0x4A */new X86_Cmov_OpcodeDecoder(PARITY_EVEN),
  /* 0x4B */new X86_Cmov_OpcodeDecoder(PARITY_ODD),
  /* 0x4C */new X86_Cmov_OpcodeDecoder(LESS),
  /* 0x4D */new X86_Cmov_OpcodeDecoder(GREATER_EQUAL),
  /* 0x4E */new X86_Cmov_OpcodeDecoder(LESS_EQUAL),
  /* 0x4F */new X86_Cmov_OpcodeDecoder(GREATER),

  /* 0x50 */null,
  /* 0x51 */null,
  /* 0x52 */null,
  /* 0x53 */null,
  /* 0x54 */null,
  /* 0x55 */null,
  /* 0x56 */null,
  /* 0x57 */null,
  /* 0x58 */null,
  /* 0x59 */null,
  /* 0x5A */null,
  /* 0x5B */null,
  /* 0x5C */null,
  /* 0x5D */null,
  /* 0x5E */null,
  /* 0x5F */null,

  /* 0x60 */null,
  /* 0x61 */null,
  /* 0x62 */null,
  /* 0x63 */null,
  /* 0x64 */null,
  /* 0x65 */null,
  /* 0x66 */null,
  /* 0x67 */null,
  /* 0x68 */null,
  /* 0x69 */null,
  /* 0x6A */null,
  /* 0x6B */null,
  /* 0x6C */null,
  /* 0x6D */null,
  /* 0x6E */null,
  /* 0x6F */null,

  /* 0x70 */null,
  /* 0x71 */null,
  /* 0x72 */null,
  /* 0x73 */null,
  /* 0x74 */null,
  /* 0x75 */null,
  /* 0x76 */null,
  /* 0x77 */null,
  /* 0x78 */null,
  /* 0x79 */null,
  /* 0x7A */null,
  /* 0x7B */null,
  /* 0x7C */null,
  /* 0x7D */null,
  /* 0x7E */null,
  /* 0x7F */null,

  /* 0x80 */new X86_Jcc_OpcodeDecoder(OVERFLOW, _16BIT ? 16 : 32),
  /* 0x81 */new X86_Jcc_OpcodeDecoder(NOT_OVERFLOW, _16BIT ? 16 : 32),
  /* 0x82 */new X86_Jcc_OpcodeDecoder(LOWER, _16BIT ? 16 : 32),
  /* 0x83 */new X86_Jcc_OpcodeDecoder(HIGHER_EQUAL, _16BIT ? 16 : 32),
  /* 0x84 */new X86_Jcc_OpcodeDecoder(EQUAL, _16BIT ? 16 : 32),
  /* 0x85 */new X86_Jcc_OpcodeDecoder(NOT_EQUAL, _16BIT ? 16 : 32),
  /* 0x86 */new X86_Jcc_OpcodeDecoder(LOWER_EQUAL, _16BIT ? 16 : 32),
  /* 0x87 */new X86_Jcc_OpcodeDecoder(HIGHER, _16BIT ? 16 : 32),
  /* 0x88 */new X86_Jcc_OpcodeDecoder(SIGNED, _16BIT ? 16 : 32),
  /* 0x89 */new X86_Jcc_OpcodeDecoder(NOT_SIGNED, _16BIT ? 16 : 32),
  /* 0x8A */new X86_Jcc_OpcodeDecoder(PARITY_EVEN, _16BIT ? 16 : 32),
  /* 0x8B */new X86_Jcc_OpcodeDecoder(PARITY_ODD, _16BIT ? 16 : 32),
  /* 0x8C */new X86_Jcc_OpcodeDecoder(LESS, _16BIT ? 16 : 32),
  /* 0x8D */new X86_Jcc_OpcodeDecoder(GREATER_EQUAL, _16BIT ? 16 : 32),
  /* 0x8E */new X86_Jcc_OpcodeDecoder(LESS_EQUAL, _16BIT ? 16 : 32),
  /* 0x8F */new X86_Jcc_OpcodeDecoder(GREATER, _16BIT ? 16 : 32),

  /* 0x90 */new X86_Set_OpcodeDecoder(OVERFLOW),
  /* 0x91 */new X86_Set_OpcodeDecoder(NOT_OVERFLOW),
  /* 0x92 */new X86_Set_OpcodeDecoder(LOWER),
  /* 0x93 */new X86_Set_OpcodeDecoder(HIGHER_EQUAL),
  /* 0x94 */new X86_Set_OpcodeDecoder(EQUAL),
  /* 0x95 */new X86_Set_OpcodeDecoder(NOT_EQUAL),
  /* 0x96 */new X86_Set_OpcodeDecoder(LOWER_EQUAL),
  /* 0x97 */new X86_Set_OpcodeDecoder(HIGHER),
  /* 0x98 */new X86_Set_OpcodeDecoder(SIGNED),
  /* 0x99 */new X86_Set_OpcodeDecoder(NOT_SIGNED),
  /* 0x9A */new X86_Set_OpcodeDecoder(PARITY_EVEN),
  /* 0x9B */new X86_Set_OpcodeDecoder(PARITY_ODD),
  /* 0x9C */new X86_Set_OpcodeDecoder(LESS),
  /* 0x9D */new X86_Set_OpcodeDecoder(GREATER_EQUAL),
  /* 0x9E */new X86_Set_OpcodeDecoder(LESS_EQUAL),
  /* 0x9F */new X86_Set_OpcodeDecoder(GREATER),

  /* 0xA0 */null,
  /* 0xA1 */null,
  /* 0xA2 */null,
  /* 0xA3 */null,
  /* 0xA4 */null,
  /* 0xA5 */null,
  /* 0xA6 */null,
  /* 0xA7 */null,
  /* 0xA8 */null,
  /* 0xA9 */null,
  /* 0xAA */null,
  /* 0xAB */null,
  /* 0xAC */null,
  /* 0xAD */null,
  /* 0xAE */new X86_OpcodeInModRMReg_Decoder(new X86_OpcodeDecoder[] {
      null,// 0
      null,// 1
      new X86_LdMXCSR_OpcodeDecoder(),// 2
      new X86_StMXCSR_OpcodeDecoder(),// 3
      null,// 4
      null,// 5
      null,// 6
      null // 7
  }),
  /* 0xAF */new X86_Imul_OpcodeDecoder(_16BIT ? 16 : 32, 0, false),
  //                                  16/32bit, no imm, not edx:eax

  /* 0xB0 */new X86_CmpXChg_OpcodeDecoder(8),
  /* 0xB1 */new X86_CmpXChg_OpcodeDecoder(_16BIT ? 16 : 32),
  /* 0xB2 */null,
  /* 0xB3 */null,
  /* 0xB4 */null,
  /* 0xB5 */null,
  /* 0xB6 */new X86_MovZX_OpcodeDecoder(_16BIT ? 16 : 32, 8), // dest 16/32bit, src 8bit
  /* 0xB7 */new X86_MovZX_OpcodeDecoder(32, 16), // dest 32bit, src 16bit
  /* 0xB8 */null,
  /* 0xB9 */null,
  /* 0xBA */null,
  /* 0xBB */null,
  /* 0xBC */null,
  /* 0xBD */null,
  /* 0xBE */new X86_MovSX_OpcodeDecoder(_16BIT ? 16 : 32, 8), // dest 16/32bit, src 8bit
  /* 0xBF */new X86_MovSX_OpcodeDecoder(32, 16), // dest 32bit, src 16bit

  /* 0xC0 */null,
  /* 0xC1 */null,
  /* 0xC2 */null,
  /* 0xC3 */null,
  /* 0xC4 */null,
  /* 0xC5 */null,
  /* 0xC6 */null,
  /* 0xC7 */null,
  /* 0xC8 */null,
  /* 0xC9 */null,
  /* 0xCA */null,
  /* 0xCB */null,
  /* 0xCC */null,
  /* 0xCD */null,
  /* 0xCE */null,
  /* 0xCF */null,

  /* 0xD0 */null,
  /* 0xD1 */null,
  /* 0xD2 */null,
  /* 0xD3 */null,
  /* 0xD4 */null,
  /* 0xD5 */null,
  /* 0xD6 */null,
  /* 0xD7 */null,
  /* 0xD8 */null,
  /* 0xD9 */null,
  /* 0xDA */null,
  /* 0xDB */null,
  /* 0xDC */null,
  /* 0xDD */null,
  /* 0xDE */null,
  /* 0xDF */null,

  /* 0xE0 */null,
  /* 0xE1 */null,
  /* 0xE2 */null,
  /* 0xE3 */null,
  /* 0xE4 */null,
  /* 0xE5 */null,
  /* 0xE6 */null,
  /* 0xE7 */null,
  /* 0xE8 */null,
  /* 0xE9 */null,
  /* 0xEA */null,
  /* 0xEB */null,
  /* 0xEC */null,
  /* 0xED */null,
  /* 0xEE */null,
  /* 0xEF */null,

  /* 0xF0 */null,
  /* 0xF1 */null,
  /* 0xF2 */null,
  /* 0xF3 */null,
  /* 0xF4 */null,
  /* 0xF5 */null,
  /* 0xF6 */null,
  /* 0xF7 */null,
  /* 0xF8 */null,
  /* 0xF9 */null,
  /* 0xFA */null,
  /* 0xFB */null,
  /* 0xFC */null,
  /* 0xFD */null,
  /* 0xFE */null,
  /* 0xFF */null };

  /**
   * Utility to get a decoder for a particular opcode
   */
  protected static X86_InstructionDecoder secondaryOpcodeLookup(int opcode) {
    if (secondaryOpcodes[opcode] == null) {
      System.out.println("Secondary Opcode 0x" + Integer.toHexString(opcode)
          + " not found");
      return badInstructionDecoder;
    } else {
      return secondaryOpcodes[opcode];
    }
  }

  /**
   * Get the decoder with upto four or five(X86_64) prefix decoders but
   * currently no opcode or operands
   */
  protected X86_InstructionDecoder getDecoder(ProcessSpace ps, int pc,
      int offset, X86_Group1PrefixDecoder prefix1,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int secondaryOpcode = ps.memory.loadInstruction8(pc + offset);
    offset++;
    return secondaryOpcodeLookup(secondaryOpcode).getDecoder(ps, pc, offset,
        prefix1, prefix2, prefix3, prefix4, prefix5);
  }
}

/**
 * The decoder for the Adc opcode
 */
class X86_Adc_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Adc_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination);
  }

  /**
   * Return the ADC operator
   */
  OPT_Operator getOperator() {
    TODO();
    return INT_ADD;
  }

  /**
   * Set the carry flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setCarryFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand carry = translationHelper.getCarryFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, carry, source1, source2, OPT_ConditionOperand
            .CARRY_FROM_ADD(), new OPT_BranchProfileOperand()));
  }

  /**
   * Set the overflow flag following a computation. 1 - true/set; 0 -
   * false/clear
   */
  protected void setOverflowFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand overflow = translationHelper.getOverflowFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, overflow, source1, source2, OPT_ConditionOperand
            .OVERFLOW_FROM_ADD(), new OPT_BranchProfileOperand()));
  }

  /**
   * Return "adc"
   */
  String getOperatorString() {
    return "adc";
  }
}

/**
 * The decoder for the Add opcode
 */
class X86_Add_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Add_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination);
  }

  /**
   * Return the ADD operator
   */
  OPT_Operator getOperator() {
    return INT_ADD;
  }

  /**
   * Set the carry flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setCarryFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand carry = translationHelper.getCarryFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, carry, source1, source2, OPT_ConditionOperand
            .CARRY_FROM_ADD(), new OPT_BranchProfileOperand()));
  }

  /**
   * Set the overflow flag following a computation. 1 - true/set; 0 -
   * false/clear
   */
  protected void setOverflowFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand overflow = translationHelper.getOverflowFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, overflow, source1, source2, OPT_ConditionOperand
            .OVERFLOW_FROM_ADD(), new OPT_BranchProfileOperand()));
  }

  /**
   * Return "add"
   */
  String getOperatorString() {
    return "add";
  }
}

/**
 * The decoder for the And opcode
 */
class X86_And_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_And_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination);
  }

  /**
   * Return the AND operator
   */
  OPT_Operator getOperator() {
    return INT_AND;
  }

  /**
   * Return "and"
   */
  String getOperatorString() {
    return "and";
  }
}

/**
 * The decoder for the Cmp opcode
 */
class X86_Cmp_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Cmp_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination, true);
  }

  /**
   * Return the CMP operator
   */
  OPT_Operator getOperator() {
    return INT_SUB;
  }

  /**
   * Set the carry flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setCarryFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand carry = translationHelper.getCarryFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, carry, source1, source2, OPT_ConditionOperand
            .BORROW_FROM_SUB(), new OPT_BranchProfileOperand()));
  }

  /**
   * Set the overflow flag following a computation. 1 - true/set; 0 -
   * false/clear
   */
  protected void setOverflowFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand overflow = translationHelper.getOverflowFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, overflow, source1, source2, OPT_ConditionOperand
            .OVERFLOW_FROM_SUB(), new OPT_BranchProfileOperand()));
  }

  /**
   * Return "cmp"
   */
  String getOperatorString() {
    return "cmp";
  }
}

/**
 * The decoder for the Cmp opcode
 */
class X86_CmpXChg_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_CmpXChg_OpcodeDecoder(int size) {
    super(size, true, 0, true);
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else {
      switch (this.operandSize) {
      case 32:
        operandSize = 16;
        break;
      case 16:
        operandSize = 32;
        break;
      default:
        operandSize = -1;
        DBT_OptimizingCompilerException.UNREACHABLE();
      }
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination = modrm.getRM(translationHelper, lazy, sib, displacement,
        operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);
    X86_DecodedOperand source = modrm.getReg(operandSize);
    
    
    OPT_RegisterOperand newValue = translationHelper.getTempInt(0);
    OPT_RegisterOperand oldValue = translationHelper.getTempInt(1);

    source.readToRegister(translationHelper, lazy, newValue);
    destination.readToRegister(translationHelper, lazy, oldValue);
    OPT_RegisterOperand expected = translationHelper.getGPRegister(lazy, X86_Registers.EAX, operandSize);
    
    translationHelper.appendInstruction(CondMove.create(INT_COND_MOVE,
        newValue.copyRO(),
        expected, oldValue.copyRO(), OPT_ConditionOperand.EQUAL(),
        newValue.copyRO(), oldValue.copyRO()
        ));
    
    destination.writeValue(translationHelper, lazy, newValue.copyRO());
    return pc + length;
  }

  /**
   * Return "cmpxchg"
   */
  String getOperatorString() {
    return "cmpxchg";
  }
}

/**
 * The decoder for the Mov opcode
 */
class X86_Mov_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Mov_OpcodeDecoder(int size, boolean isMemoryOperandDestination) {
    super(size, isMemoryOperandDestination, size);
  }

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Mov_OpcodeDecoder(int register, int size) {
    super(size, false, size, false, register);
  }

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Mov_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination);
  }

  /**
   * Return the MOVE operator
   */
  OPT_Operator getOperator() {
    return INT_MOVE;
  }

  /**
   * Return "mov"
   */
  String getOperatorString() {
    return "mov";
  }
}
/**
 * The decoder for the Mov opcode
 */
class X86_MovSeg_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_MovSeg_OpcodeDecoder(boolean isMemoryOperandDestination) {
    super(X86_64 ? 64 : 16, true, 0, isMemoryOperandDestination);
  }
  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize = this.operandSize;
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination;
    X86_DecodedOperand source = null;
    if (isMemoryOperandDestination) {
      destination = modrm.getRM(translationHelper, lazy, sib, displacement,
          operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
              : X86_Registers.DS);
      source = modrm.getSReg();
    } else {
      destination = modrm.getSReg();
      source = modrm.getRM(translationHelper, lazy, sib, displacement,
          operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
              : X86_Registers.DS);
    }

    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    OPT_RegisterOperand sourceOp1 = translationHelper.getTempInt(1);
    source.readToRegister(translationHelper, lazy, sourceOp1);
    translationHelper.appendInstruction(Move.create(INT_MOVE,
        temp, sourceOp1.copyRO()));
    destination.writeValue(translationHelper, lazy, temp.copyRO());
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    char addressPrefix;
    if (prefix4 == null) {
      addressPrefix = _16BIT ? ' ' : 'e';
    } else {
      addressPrefix = _16BIT ? 'e' : ' ';
    }
    // TODO: apply segment override
    switch (operandSize) {
    case 8:
      return "movsb es:" + addressPrefix + "di, ds:" + addressPrefix + "si";
    case 16:
      return "movsw es:" + addressPrefix + "di, ds:" + addressPrefix + "si";
    case 32:
      return "movsd es:" + addressPrefix + "di, ds:" + addressPrefix + "si";
    default:
      DBT_OptimizingCompilerException.UNREACHABLE();
    return "error";
    }
  }
}

/**
 * The decoder for the Movs opcode
 */
class X86_Movs_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Movs_OpcodeDecoder(int size) {
    super(size, false, 0, false);
  }
  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    // Create memory references
    X86_DecodedOperand source = X86_DecodedOperand.getMemory(
        (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS,
            X86_Registers.ESI, 0, -1, 0, addressSize, operandSize);
    
    X86_DecodedOperand destination = X86_DecodedOperand.getMemory(
        X86_Registers.ES, X86_Registers.EDI, 0, -1, 0, addressSize, operandSize);

    // Perform copy
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, temp);
    destination.writeValue(translationHelper, lazy, temp.copyRO());
    
    // Do update
    OPT_RegisterOperand esi = translationHelper.getGPRegister(lazy,
        X86_Registers.ESI, addressSize);
    OPT_RegisterOperand edi = translationHelper.getGPRegister(lazy,
        X86_Registers.EDI, addressSize);

    OPT_IntConstantOperand upIncrement = null, downIncrement = null;
    switch(operandSize) {
    case 32:
      upIncrement = new OPT_IntConstantOperand(4);
      downIncrement = new OPT_IntConstantOperand(-4);
      break;
    case 16:
      upIncrement = new OPT_IntConstantOperand(2);
      downIncrement = new OPT_IntConstantOperand(-2);
      break;
    case 8:
      upIncrement = new OPT_IntConstantOperand(1);
      downIncrement = new OPT_IntConstantOperand(-1);
      break;
    default:
      DBT_OptimizingCompilerException.UNREACHABLE();
    }
    translationHelper.appendInstruction(CondMove.create(INT_COND_MOVE,
        temp.copyRO(), translationHelper.getDirectionFlag(), new OPT_IntConstantOperand(0),
        OPT_ConditionOperand.EQUAL(), upIncrement, downIncrement));
    
    translationHelper.appendInstruction(Binary.create(INT_ADD,
        esi.copyRO(), esi.copyRO(), temp.copyRO()));

    translationHelper.appendInstruction(Binary.create(INT_ADD,
        edi.copyRO(), edi.copyRO(), temp.copyRO()));

    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    char addressPrefix;
    if (prefix4 == null) {
      addressPrefix = _16BIT ? ' ' : 'e';
    } else {
      addressPrefix = _16BIT ? 'e' : ' ';
    }
    // TODO: apply segment override
    switch (operandSize) {
    case 8:
      return "movsb es:" + addressPrefix + "di, ds:" + addressPrefix + "si";
    case 16:
      return "movsw es:" + addressPrefix + "di, ds:" + addressPrefix + "si";
    case 32:
      return "movsd es:" + addressPrefix + "di, ds:" + addressPrefix + "si";
    default:
      DBT_OptimizingCompilerException.UNREACHABLE();
    return "error";
    }
  }
}

/**
 * The decoder for the Or opcode
 */
class X86_Or_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Or_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination);
  }

  /**
   * Return the OR operator
   */
  OPT_Operator getOperator() {
    return INT_OR;
  }

  /**
   * Return "or"
   */
  String getOperatorString() {
    return "or";
  }
}
/**
 * The decoder for the Rcl opcode
 */
class X86_Rcl_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Rotate rm right by 1 through carry 
   */
  X86_Rcl_OpcodeDecoder(int size) {
    super(size, true, 1, true);
  }
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Rcl_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination,
        X86_Registers.ECX);
  }
  /**
   * Return the RCL operator
   */
  OPT_Operator getOperator() {
    TODO();
    return null;
  }
}
/**
 * The decoder for the Rcr opcode
 */
class X86_Rcr_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Rotate rm right by 1 through carry 
   */
  X86_Rcr_OpcodeDecoder(int size) {
    super(size, true, 1, true);
  }
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Rcr_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination,
        X86_Registers.ECX);
  }
  /**
   * Return the RCL operator
   */
  OPT_Operator getOperator() {
    TODO();
    return null;
  }
}
/**
 * The decoder for the Rol opcode
 */
class X86_Rol_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Rotate rm left by 1 
   */
  X86_Rol_OpcodeDecoder(int size) {
    super(size, true, 1, true);
  }
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Rol_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination,
        X86_Registers.ECX);
  }
  /**
   * Return the ROL operator
   */
  OPT_Operator getOperator() {
    TODO();
    return null;
  }
}
/**
 * The decoder for the Ror opcode
 */
class X86_Ror_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Rotate rm right by 1 
   */
  X86_Ror_OpcodeDecoder(int size) {
    super(size, true, 1, true);
  }

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Ror_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination,
        X86_Registers.ECX);
  }
  /**
   * Return the ROL operator
   */
  OPT_Operator getOperator() {
    TODO();
    return null;
  }
}

/**
 * The decoder for the Shl opcode
 */
class X86_Shl_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Shift rm left by 1 
   */
  X86_Shl_OpcodeDecoder(int size) {
    super(size, true, 1, true);
  }

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Shl_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination,
        X86_Registers.ECX);
  }

  /**
   * Return the SHL operator
   */
  OPT_Operator getOperator() {
    return INT_SHL;
  }

  /**
   * Set the carry flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setCarryFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand carry = translationHelper.getCarryFlag();
    OPT_RegisterOperand temp = translationHelper.getTempInt(9);
    translationHelper.appendInstruction(Binary.create(INT_ADD,
        temp, source2, new OPT_IntConstantOperand(1)));
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, carry, source1, temp.copyRO(), OPT_ConditionOperand
            .BIT_TEST(), new OPT_BranchProfileOperand()));
  }

  /**
   * Return "shl"
   */
  String getOperatorString() {
    return "shl";
  }
}

/**
 * The decoder for the Shr opcode
 */
class X86_Shr_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Shift rm right by 1 
   */
  X86_Shr_OpcodeDecoder(int size) {
    super(size, true, 1, true);
  }
  
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Shr_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination,
        X86_Registers.ECX);
  }

  /**
   * Return the SHR operator
   */
  OPT_Operator getOperator() {
    return INT_SHR;
  }

  /**
   * Set the carry flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setCarryFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand carry = translationHelper.getCarryFlag();
    OPT_RegisterOperand temp = translationHelper.getTempInt(9);
    translationHelper.appendInstruction(Binary.create(INT_ADD,
        temp, source2, new OPT_IntConstantOperand(1)));
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, carry, source1, temp.copyRO(), OPT_ConditionOperand
            .BIT_TEST(), new OPT_BranchProfileOperand()));
  }

  /**
   * Return "shr"
   */
  String getOperatorString() {
    return "shr";
  }
}

/**
 * The decoder for the Sbb opcode
 */
class X86_Sbb_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Sbb_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination);
  }

  /**
   * Return the SBB operator
   */
  OPT_Operator getOperator() {
    return INT_SUB;
  }

  /**
   * Set the carry flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setCarryFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand carry = translationHelper.getCarryFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, carry, source1, source2, OPT_ConditionOperand
            .BORROW_FROM_SUB(), new OPT_BranchProfileOperand()));
  }

  /**
   * Set the overflow flag following a computation. 1 - true/set; 0 -
   * false/clear
   */
  protected void setOverflowFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand overflow = translationHelper.getOverflowFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, overflow, source1, source2, OPT_ConditionOperand
            .OVERFLOW_FROM_SUB(), new OPT_BranchProfileOperand()));
  }

  /**
   * Return "sbb"
   */
  String getOperatorString() {
    return "sbb";
  }
}

/**
 * The decoder for the Sub opcode
 */
class X86_Sub_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Sub_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination);
  }

  /**
   * Return the SUB operator
   */
  OPT_Operator getOperator() {
    return INT_SUB;
  }

  /**
   * Set the carry flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setCarryFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand carry = translationHelper.getCarryFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, carry, source1, source2, OPT_ConditionOperand
            .BORROW_FROM_SUB(), new OPT_BranchProfileOperand()));
  }

  /**
   * Set the overflow flag following a computation. 1 - true/set; 0 -
   * false/clear
   */
  protected void setOverflowFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand overflow = translationHelper.getOverflowFlag();
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, overflow, source1, source2, OPT_ConditionOperand
            .OVERFLOW_FROM_SUB(), new OPT_BranchProfileOperand()));
  }

  /**
   * Return "sub"
   */
  String getOperatorString() {
    return "sub";
  }
}

/**
 * The decoder for the Test opcode
 */
class X86_Test_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Test_OpcodeDecoder(int size, boolean hasModRM, int immediateSize) {
    super(size, hasModRM, immediateSize, false, true);
  }

  /**
   * Return the TEST operator
   */
  OPT_Operator getOperator() {
    return INT_AND;
  }

  /**
   * Return "test"
   */
  String getOperatorString() {
    return "test";
  }
}

/**
 * The decoder for the Ushr opcode
 */
class X86_Ushr_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Unsigned shift rm right by 1 
   */
  X86_Ushr_OpcodeDecoder(int size) {
    super(size, true, 1, true);
  }
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Ushr_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination,
        X86_Registers.ECX);
  }

  /**
   * Return the USHR operator
   */
  OPT_Operator getOperator() {
    return INT_USHR;
  }

  /**
   * Set the carry flag following a computation. 1 - true/set; 0 - false/clear
   */
  protected void setCarryFlag(X862IR translationHelper,
      OPT_RegisterOperand result, OPT_RegisterOperand source1,
      OPT_RegisterOperand source2) {
    OPT_RegisterOperand carry = translationHelper.getCarryFlag();
    OPT_RegisterOperand temp = translationHelper.getTempInt(9);
    translationHelper.appendInstruction(Binary.create(INT_SUB,
        temp, source2, new OPT_IntConstantOperand(1)));
    translationHelper.appendInstruction(BooleanCmp.create(
        BOOLEAN_CMP_INT, carry, source1, temp.copyRO(), OPT_ConditionOperand
            .BIT_TEST(), new OPT_BranchProfileOperand()));
  }

  /**
   * Return "ushr"
   */
  String getOperatorString() {
    return "ushr";
  }
}

/**
 * The decoder for the Xor opcode
 */
class X86_Xor_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Xor_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination);
  }

  /**
   * Return the XOR operator
   */
  OPT_Operator getOperator() {
    return INT_XOR;
  }

  /**
   * Return "xor"
   */
  String getOperatorString() {
    return "xor";
  }
}

/**
 * The decoder for the Pop opcode
 */
class X86_Pop_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   * @param reg the register to pop into or -1 to show that the destination is a
   *          memory operand
   */
  X86_Pop_OpcodeDecoder(int reg) {
    super(_16BIT ? 16 : 32, // operandSize
        (reg == -1), // hasModRM,
        0, // immediateSize
        (reg == -1), // isMemoryOperandDestination
        reg // register
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination;
    if ((modrm != null) && isMemoryOperandDestination) {
      destination = modrm.getRM(translationHelper, lazy, sib, displacement,
          operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
              : X86_Registers.DS);
    } else {
      destination = X86_DecodedOperand.getRegister(register, operandSize);
    }
    // Get operands
    X86_DecodedOperand source = X86_DecodedOperand.getStack(addressSize,
        operandSize);
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    OPT_RegisterOperand sourceOp1 = translationHelper.getTempInt(1);
    source.readToRegister(translationHelper, lazy, sourceOp1);
    // Make copy
    destination.writeValue(translationHelper, lazy, sourceOp1.copyRO());
    // Increment stack pointer
    OPT_RegisterOperand esp = translationHelper.getGPRegister(lazy,
        X86_Registers.ESP, addressSize);
    translationHelper.appendInstruction(Binary.create(INT_ADD,
        esp, esp.copyRO(), new OPT_IntConstantOperand(operandSize >>> 3)));
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination;
    if ((modrm != null) && isMemoryOperandDestination) {
      return "pop modrm";
    } else {
      return "pop " + X86_Registers.disassemble(register, operandSize);
    }
  }
}

/**
 * The decoder for the Push opcode
 */
class X86_Push_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   * @param reg the register to push into, or -1 to show that the destination is
   *          a memory operand, or -8/-16/-32 to show that this is an immediate
   *          push of the appropriate size
   */
  X86_Push_OpcodeDecoder(int reg) {
    super(_16BIT ? 16 : 32, // operandSize
        (reg == -1), // hasModRM,
        (reg >= -1) ? 0 : -reg, // immediateSize
        true, // isMemoryOperandDestination
        reg // register
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand source;
    if (modrm != null) {
      source = modrm.getRM(translationHelper, lazy, sib, displacement,
          operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
              : X86_Registers.DS);
    } else {
      if (immediateSize > 0) {
        source = X86_DecodedOperand.getImmediate(immediate);
      } else {
        source = X86_DecodedOperand.getRegister(register, operandSize);
      }
    }
    // Get operands
    X86_DecodedOperand destination = X86_DecodedOperand.getStack(addressSize,
        operandSize);
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    OPT_RegisterOperand sourceOp1 = translationHelper.getTempInt(1);
    source.readToRegister(translationHelper, lazy, sourceOp1);
    // Decrement stack pointer
    OPT_RegisterOperand esp = translationHelper.getGPRegister(lazy,
        X86_Registers.ESP, addressSize);
    translationHelper.appendInstruction(Binary.create(INT_SUB,
        esp, esp.copyRO(), new OPT_IntConstantOperand(addressSize >>> 3)));
    // Write value
    destination.writeValue(translationHelper, lazy, sourceOp1.copyRO());
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination;
    if (modrm != null) {
      return "push "
          + modrm.disassembleRM(sib, displacement, operandSize, addressSize,
              (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    } else {
      if (immediateSize > 0) {
        return "push 0x" + Integer.toHexString(immediate);
      } else {
        return "push " + X86_Registers.disassemble(register, operandSize);
      }
    }
  }
}

/**
 * The decoder for the Leave opcode
 */
class X86_Leave_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Leave_OpcodeDecoder() {
    super(_16BIT ? 16 : 32, // operandSize
        false, // hasModRM,
        0, // immediateSize
        false, // isMemoryOperandDestination
        X86_Registers.EBP // register
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }

    // Get registers
    OPT_RegisterOperand ebp = translationHelper.getGPRegister(lazy,
        X86_Registers.EBP, operandSize);
    OPT_RegisterOperand esp = translationHelper.getGPRegister(lazy,
        X86_Registers.ESP, operandSize);
    // Copy ebp to esp
    translationHelper.appendInstruction(Move.create(INT_MOVE,
        esp, ebp));

    // Read ebp from stack
    X86_DecodedOperand source = X86_DecodedOperand.getStack(operandSize,
        operandSize);
    source.readToRegister(translationHelper, lazy, ebp.copyRO());
    // Increment stack pointer
    translationHelper.appendInstruction(Binary.create(INT_ADD,
        esp.copyRO(), esp.copyRO(), new OPT_IntConstantOperand(
            operandSize >>> 3)));
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    return "leave";
  }
}

/**
 * The decoder for the Call opcode
 */
class X86_Call_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Call_OpcodeDecoder(int size, boolean hasModRM, int immediateSize,
      boolean isMemoryOperandDestination) {
    super(size, hasModRM, immediateSize, isMemoryOperandDestination);
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination;
    if (modrm != null) {
      destination = modrm.getRM(translationHelper, lazy, sib, displacement,
          operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
              : X86_Registers.DS);
    } else {
      destination = X86_DecodedOperand.getImmediate(pc + length + immediate);
    }
    // Get operands
    OPT_RegisterOperand destOp = translationHelper.getTempInt(0);
    destination.readToRegister(translationHelper, lazy, destOp);
    X86_DecodedOperand stack = X86_DecodedOperand.getStack(addressSize,
        operandSize);
    OPT_RegisterOperand temp = translationHelper.getTempInt(1);
    // Decrement stack pointer
    OPT_RegisterOperand esp = translationHelper.getGPRegister(lazy,
        X86_Registers.ESP, addressSize);
    translationHelper.appendInstruction(Binary.create(INT_SUB,
        esp, esp.copyRO(), new OPT_IntConstantOperand(operandSize >>> 3)));
    ;
    // Store return address
    translationHelper.appendInstruction(Move.create(INT_MOVE,
        temp, new OPT_IntConstantOperand(pc + length)));
    stack.writeValue(translationHelper, lazy, temp.copyRO());
    
    // Branch
    translationHelper.appendBranch(destOp.copyRO(), lazy, BranchType.CALL);
    return -1;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else {
      switch (this.operandSize) {
      case 32:
        operandSize = 16;
        break;
      case 16:
        operandSize = 32;
        break;
      default:
        operandSize = -1;
        DBT_OptimizingCompilerException.UNREACHABLE();
      }
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    String destination;
    String source = null;
    if (modrm != null) {
      destination = modrm.disassembleRM(sib, displacement, operandSize,
          addressSize, (prefix2 != null) ? prefix2.getSegment()
              : X86_Registers.DS);
    } else {
      destination = "0x" + Integer.toHexString(pc + length + immediate);
    }
    return "call " + destination;
  }
}

/**
 * The decoder for the Ret opcode
 */
class X86_Ret_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Near or far return?
   */
  private final boolean far;

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Ret_OpcodeDecoder(boolean far, int immediateSize) {
    super(0, false, immediateSize, false);
    this.far = far;
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    if (far) {
      TODO();
    }

    // Get return address
    X86_DecodedOperand source = X86_DecodedOperand.getStack(_16BIT ? 16 : 32,
        _16BIT ? 16 : 32);
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, temp);

    // Increment stack pointer
    OPT_RegisterOperand esp = translationHelper.getGPRegister(lazy,
        X86_Registers.ESP, _16BIT ? 16 : 32);
    translationHelper.appendInstruction(Binary.create(INT_ADD,
        esp, esp.copyRO(), new OPT_IntConstantOperand(4 + immediate)));
    // Branch
    translationHelper.appendTraceExit(
        (X86_Laziness) lazy.clone(), temp.copyRO());
    return -1;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    if (immediateSize > 0)
      return "ret " + immediate;
    else
      return "ret";
  }
}

/**
 * The decoder for the Int opcode
 */
class X86_Int_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Int_OpcodeDecoder() {
    super(_16BIT ? 16 : 32, false, 8, false);
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    translationHelper.appendSystemCall(lazy);
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    return "int 0x" + Integer.toHexString(immediate & 0xFF);
  }
}

/**
 * The decoder for the Jcc opcode
 */
class X86_Jcc_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * The condition for this jcc
   */
  private final int condition;

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Jcc_OpcodeDecoder(int condition, int immediateSize) {
    super(immediateSize, false, immediateSize, false);
    this.condition = condition;
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    // The destination for the branch
    int target_address = pc + length + immediate;

    OPT_BasicBlock executeBranch = translationHelper.createBlockAfterCurrent();
    OPT_BasicBlock fallThrough = translationHelper.createBlockAfterCurrent();
    boolean branchLikely;
    if (prefix2 != null) {
      branchLikely = prefix2.getLikely();
    } else {
      branchLikely = target_address < pc;
    }
    OPT_BranchProfileOperand likelyOp = translationHelper
        .getConditionalBranchProfileOperand(branchLikely);

    // decode condition
    OPT_Operator operator = null;
    OPT_Operand lhsOp1 = null, rhsOp1 = null, lhsOp2 = null, rhsOp2 = null;
    OPT_ConditionOperand condOp1 = null, condOp2 = null;
    switch (condition) {
    case EQUAL:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case NOT_EQUAL:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      break;
    case LOWER_EQUAL:
      operator = BOOLEAN_CMP2_INT_OR;
      lhsOp1 = translationHelper.getCarryFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      lhsOp2 = translationHelper.getZeroFlag();
      rhsOp2 = new OPT_IntConstantOperand(0);
      condOp2 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case LESS_EQUAL:
      operator = BOOLEAN_CMP2_INT_AND;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      lhsOp2 = translationHelper.getSignFlag();
      rhsOp2 = translationHelper.getOverflowFlag();
      condOp2 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case HIGHER_EQUAL:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getCarryFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      break;
    case HIGHER:
      operator = BOOLEAN_CMP2_INT_AND;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      lhsOp2 = translationHelper.getCarryFlag();
      rhsOp2 = new OPT_IntConstantOperand(0);
      condOp2 = OPT_ConditionOperand.EQUAL();
      break;
    case SIGNED:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getSignFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case NOT_SIGNED:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getSignFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      break;
    case LOWER:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getCarryFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case GREATER_EQUAL:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getSignFlag();
      rhsOp1 = translationHelper.getOverflowFlag();
      condOp1 = OPT_ConditionOperand.EQUAL();
      break;
    case GREATER:
      operator = BOOLEAN_CMP2_INT_AND;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      lhsOp2 = translationHelper.getSignFlag();
      rhsOp2 = translationHelper.getOverflowFlag();
      condOp2 = OPT_ConditionOperand.EQUAL();
      break;      
    default:
      TODO();
    }
    // Compute result
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    OPT_Instruction boolcmp;
    if (operator == BOOLEAN_CMP_INT) {
      boolcmp = BooleanCmp.create(BOOLEAN_CMP_INT, temp, lhsOp1, rhsOp1,
          condOp1, translationHelper.getConditionalBranchProfileOperand(false));
    } else {
      boolcmp = BooleanCmp2.create(operator, temp, lhsOp1, rhsOp1, condOp1,
          translationHelper.getConditionalBranchProfileOperand(false), lhsOp2,
          rhsOp2, condOp2, translationHelper
              .getConditionalBranchProfileOperand(false));
    }
    translationHelper.appendInstruction(boolcmp);
    translationHelper.appendInstruction(IfCmp.create(INT_IFCMP, translationHelper.getTempValidation(0), temp.copyRO(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.NOT_EQUAL(), executeBranch.makeJumpTarget(), likelyOp));
    
    OPT_Instruction gotoInstr = Goto.create(GOTO, fallThrough.makeJumpTarget());
    translationHelper.appendInstruction(gotoInstr);
    
    translationHelper.setCurrentBlock(executeBranch);
    translationHelper.appendBranch(target_address, lazy);
    
    translationHelper.setCurrentBlock(fallThrough);
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    switch (condition) {
    case EQUAL:
      return "je +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case NOT_EQUAL:
      return "jne +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case LESS:
      return "jl +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case GREATER_EQUAL:
      return "jge +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case GREATER:
      return "jg +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case LESS_EQUAL:
      return "jle +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case HIGHER:
      return "ja +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case LOWER:
      return "jb +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case HIGHER_EQUAL:
      return "jae +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case LOWER_EQUAL:
      return "jbe +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case OVERFLOW:
      return "jo +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case NOT_OVERFLOW:
      return "jno +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case SIGNED:
      return "js +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case NOT_SIGNED:
      return "jns +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case PARITY_EVEN:
      return "jpe +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    case PARITY_ODD:
      return "jpo +" + immediate + " (0x"
          + Integer.toHexString(pc + length + immediate) + ")";
    default:
      return "jcc " + condition + " 0x"
          + Integer.toHexString(pc + length + immediate);
    }
  }
}

/**
 * The decoder for the Jmp opcode
 */
class X86_Jmp_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Is this an absolute jmp?
   */
  private final boolean absolute;

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Jmp_OpcodeDecoder(boolean absolute, int immediateSize) {
    super((immediateSize == 0) ? (_16BIT ? 16 : 32) : immediateSize,
        (immediateSize == 0), immediateSize, false);
    this.absolute = absolute;
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    // The destination for the branch
    int target_address;
    if (modrm == null) {
      target_address = absolute ? immediate : pc + length + immediate;
      translationHelper.getCurrentBlock().deleteNormalOut();
      translationHelper.appendBranch(target_address, lazy);
    } else {
      int operandSize;
      if (prefix3 == null) {
        operandSize = this.operandSize;
      } else {
        switch (this.operandSize) {
        case 32:
          operandSize = 16;
          break;
        case 16:
          operandSize = 32;
          break;
        default:
          operandSize = -1;
          DBT_OptimizingCompilerException.UNREACHABLE();
        }
      }
      int addressSize;
      if (prefix4 == null) {
        addressSize = _16BIT ? 16 : 32;
      } else {
        addressSize = _16BIT ? 32 : 16;
      }
      X86_DecodedOperand destination = modrm.getRM(translationHelper, lazy,
          sib, displacement, operandSize, addressSize,
          (prefix2 != null) ? prefix2.getSegment() : X86_Registers.CS);

      OPT_RegisterOperand branchAddress = translationHelper.getTempInt(0);
      destination.readToRegister(translationHelper, lazy, branchAddress);
      /*
       * TODO!!! OPT_BasicBlock fallThrough = ppc2ir.createBlockAfterCurrent();
       * OPT_Instruction lookupswitch_instr; lookupswitch_instr =
       * LookupSwitch.create(LOOKUPSWITCH, branchAddress.copyRO(), null, null,
       * fallThrough.makeJumpTarget(), null, 0);
       * ppc2ir.appendInstructionToCurrentBlock(lookupswitch_instr);
       * ppc2ir.registerLookupSwitchForSwitchUnresolved(lookupswitch_instr, pc,
       * (PPC_Laziness)lazy.clone(), lk != 0);
       * ppc2ir.setCurrentBlock(fallThrough);
       * ppc2ir.plantRecordUncaughtBcctr(pc, branchAddress.copyRO());
       */
      translationHelper.appendTraceExit(
          (X86_Laziness) lazy.clone(), branchAddress.copyRO());
    }
    return -1;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    if (modrm == null) {
      if (!absolute)
        return "jmp 0x" + Integer.toHexString(pc + length + immediate);
      else
        return "jmp 0x" + Integer.toHexString(immediate);
    } else {
      int operandSize;
      if (prefix3 == null) {
        operandSize = this.operandSize;
      } else {
        switch (this.operandSize) {
        case 32:
          operandSize = 16;
          break;
        case 16:
          operandSize = 32;
          break;
        default:
          operandSize = -1;
          DBT_OptimizingCompilerException.UNREACHABLE();
        }
      }
      int addressSize;
      if (prefix4 == null) {
        addressSize = _16BIT ? 16 : 32;
      } else {
        addressSize = _16BIT ? 32 : 16;
      }
      return "jmp "
          + modrm.disassembleRM(sib, displacement, operandSize, addressSize,
              (prefix2 != null) ? prefix2.getSegment() : X86_Registers.CS);
    }
  }
}
/**
 * The decoder for the STMXCSR opcode
 */
class X86_StMXCSR_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_StMXCSR_OpcodeDecoder() {
    super(32, // operandSize
        true, // hasModRM,
        0, // immediateSize
        true // isMemoryOperandDestination
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination = modrm.getRM(translationHelper, lazy, sib, displacement,
        32, addressSize, (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);

    OPT_RegisterOperand mxcsr = translationHelper.getMXCSR();
    destination.writeValue(translationHelper, lazy, mxcsr);
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    return "stmxcsr " + modrm.disassembleRM(sib, displacement, 32, addressSize, (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
  }
}

/**
 * The decoder for the Set opcode
 */
class X86_Set_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * The condition for this set
   */
  private final int condition;

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Set_OpcodeDecoder(int condition) {
    super(8, true, 0, true);
    this.condition = condition;
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination = modrm.getRM(translationHelper, lazy, sib,
        displacement, operandSize, addressSize, (prefix2 != null) ? prefix2
            .getSegment() : X86_Registers.DS);

    // decode condition
    OPT_Operator operator = null;
    OPT_Operand lhsOp1 = null, rhsOp1 = null, lhsOp2 = null, rhsOp2 = null;
    OPT_ConditionOperand condOp1 = null, condOp2 = null;
    switch (condition) {
    case EQUAL:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case NOT_EQUAL:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      break;
    case LOWER_EQUAL:
      operator = BOOLEAN_CMP2_INT_OR;
      lhsOp1 = translationHelper.getCarryFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      lhsOp2 = translationHelper.getZeroFlag();
      rhsOp2 = new OPT_IntConstantOperand(0);
      condOp2 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case LESS_EQUAL:
      operator = BOOLEAN_CMP2_INT_AND;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      lhsOp2 = translationHelper.getSignFlag();
      rhsOp2 = translationHelper.getOverflowFlag();
      condOp2 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case LOWER:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getCarryFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    default:
      TODO();
    }
    // Compute result
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    OPT_Instruction boolcmp;
    if (operator == BOOLEAN_CMP_INT) {
      boolcmp = BooleanCmp.create(BOOLEAN_CMP_INT, temp, lhsOp1, rhsOp1,
          condOp1, translationHelper.getConditionalBranchProfileOperand(false));
    } else {
      boolcmp = BooleanCmp2.create(operator, temp, lhsOp1, rhsOp1, condOp1,
          translationHelper.getConditionalBranchProfileOperand(false), lhsOp2,
          rhsOp2, condOp2, translationHelper
              .getConditionalBranchProfileOperand(false));
    }
    translationHelper.appendInstruction(boolcmp);
    destination.writeValue(translationHelper, lazy, temp.copyRO());
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    String destination = modrm.disassembleRM(sib, displacement, 8, addressSize,
        (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    switch (condition) {
    case EQUAL:
      return "sete " + destination;
    case NOT_EQUAL:
      return "setne " + destination;
    default:
      return "set " + condition + " " + destination;
    }
  }
}

/**
 * The decoder for the Cmov opcode
 */
class X86_Cmov_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * The condition for this cmov
   */
  private final int condition;

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Cmov_OpcodeDecoder(int condition) {
    super(_16BIT ? 16 : 32, true, 0, false);
    this.condition = condition;
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand source = modrm.getRM(translationHelper, lazy, sib,
        displacement, operandSize, addressSize, (prefix2 != null) ? prefix2
            .getSegment() : X86_Registers.DS);
    X86_DecodedOperand destination = modrm.getReg(operandSize);

    // decode condition
    OPT_Operator operator = null;
    OPT_Operand lhsOp1 = null, rhsOp1 = null, lhsOp2 = null, rhsOp2 = null;
    OPT_ConditionOperand condOp1 = null, condOp2 = null;
    switch (condition) {
    case EQUAL:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case NOT_EQUAL:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      break;
    case LOWER_EQUAL:
      operator = BOOLEAN_CMP2_INT_OR;
      lhsOp1 = translationHelper.getCarryFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      lhsOp2 = translationHelper.getZeroFlag();
      rhsOp2 = new OPT_IntConstantOperand(0);
      condOp2 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case LESS_EQUAL:
      operator = BOOLEAN_CMP2_INT_AND;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.NOT_EQUAL();
      lhsOp2 = translationHelper.getSignFlag();
      rhsOp2 = translationHelper.getOverflowFlag();
      condOp2 = OPT_ConditionOperand.NOT_EQUAL();
      break;
    case GREATER:
      operator = BOOLEAN_CMP2_INT_AND;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      lhsOp2 = translationHelper.getSignFlag();
      rhsOp2 = translationHelper.getOverflowFlag();
      condOp2 = OPT_ConditionOperand.EQUAL();
      break;
    case HIGHER:
      operator = BOOLEAN_CMP2_INT_AND;
      lhsOp1 = translationHelper.getZeroFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      lhsOp2 = translationHelper.getCarryFlag();
      rhsOp2 = new OPT_IntConstantOperand(0);
      condOp2 = OPT_ConditionOperand.EQUAL();
      break;
    case HIGHER_EQUAL:
      operator = BOOLEAN_CMP_INT;
      lhsOp1 = translationHelper.getCarryFlag();
      rhsOp1 = new OPT_IntConstantOperand(0);
      condOp1 = OPT_ConditionOperand.EQUAL();
      break;
    default:
      TODO();
    }
    // Compute result
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    OPT_Instruction boolcmp;
    if (operator == BOOLEAN_CMP_INT) {
      boolcmp = BooleanCmp.create(BOOLEAN_CMP_INT, temp, lhsOp1, rhsOp1,
          condOp1, translationHelper.getConditionalBranchProfileOperand(false));
    } else {
      boolcmp = BooleanCmp2.create(operator, temp, lhsOp1, rhsOp1, condOp1,
          translationHelper.getConditionalBranchProfileOperand(false), lhsOp2,
          rhsOp2, condOp2, translationHelper
              .getConditionalBranchProfileOperand(false));
    }
    translationHelper.appendInstruction(boolcmp);
    // do cmov
    OPT_RegisterOperand temp2 = translationHelper.getTempInt(1);
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(2);
    OPT_RegisterOperand destinationOp = translationHelper.getTempInt(3);
    source.readToRegister(translationHelper, lazy, sourceOp);
    destination.readToRegister(translationHelper, lazy, destinationOp);
    OPT_Instruction cmov = CondMove.create(INT_COND_MOVE, temp2, temp.copyRO(),
        new OPT_IntConstantOperand(0), OPT_ConditionOperand.NOT_EQUAL(),
        sourceOp.copyRO(), destinationOp.copyRO());
    translationHelper.appendInstruction(cmov);
    destination.writeValue(translationHelper, lazy, temp2.copyRO());
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    String source = modrm.disassembleRM(sib, displacement, operandSize,
        addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);
    String destination = modrm.disassembleReg(operandSize);

    switch (condition) {
    case EQUAL:
      return "cmove " + destination + ", " + source;
    case NOT_EQUAL:
      return "cmovne " + destination + ", " + source;
    default:
      return "cmov " + condition + " " + destination + ", " + source;
    }
  }
}

/**
 * The decoder for the LDMXCSR opcode
 */
class X86_LdMXCSR_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_LdMXCSR_OpcodeDecoder() {
    super(32, // operandSize
        true, // hasModRM,
        0, // immediateSize
        false // isMemoryOperandDestination
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand source = modrm.getRM(translationHelper, lazy, sib, displacement,
        32, addressSize, (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);

    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, temp);
    
    OPT_RegisterOperand mxcsr = translationHelper.getMXCSR();
    translationHelper.appendInstruction(Move.create(INT_MOVE, mxcsr, temp.copyRO()));
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    return "ldmxcsr " + modrm.disassembleRM(sib, displacement, 32, addressSize, (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
  }
}

/**
 * The decoder for the Lea opcode
 */
class X86_Lea_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Lea_OpcodeDecoder() {
    super(_16BIT ? 16 : 32, true, 0, false);
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination;
    X86_DecodedOperand source;
    source = modrm.getRM(translationHelper, lazy, sib, displacement,
        operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);
    destination = modrm.getReg(operandSize);

    // Compute and write effective address
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(0);
    source.readEffectiveAddress(translationHelper, lazy, sourceOp);
    destination.writeValue(translationHelper, lazy, sourceOp.copyRO());

    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else {
      switch (this.operandSize) {
      case 32:
        operandSize = 16;
        break;
      case 16:
        operandSize = 32;
        break;
      default:
        operandSize = -1;
        DBT_OptimizingCompilerException.UNREACHABLE();
      }
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    String destination;
    String source = null;

    source = modrm.disassembleRM(sib, displacement, operandSize, addressSize,
        (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    destination = modrm.disassembleReg(operandSize);

    return "lea " + destination + ", " + source;
  }
}

/**
 * The decoder for the MovSX opcode
 */
class X86_MovSX_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * The size of the source register
   */
  private final int srcSize;

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_MovSX_OpcodeDecoder(int destSize, int srcSize) {
    super(destSize, true, 0, true);
    this.srcSize = srcSize;
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination = modrm.getReg(operandSize);
    X86_DecodedOperand source = modrm.getRM(translationHelper, lazy, sib,
        displacement, srcSize, addressSize, (prefix2 != null) ? prefix2
            .getSegment() : X86_Registers.DS);

    // Mask appropriate bits
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, temp);
    int shift = (srcSize == 8) ? 24 : 16;
    translationHelper.appendInstruction(Binary.create(INT_SHL,
        temp.copyRO(), temp.copyRO(), new OPT_IntConstantOperand(shift)));
    translationHelper.appendInstruction(Binary.create(INT_SHR,
        temp.copyRO(), temp.copyRO(), new OPT_IntConstantOperand(shift)));
    destination.writeValue(translationHelper, lazy, temp.copyRO());
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else {
      switch (this.operandSize) {
      case 32:
        operandSize = 16;
        break;
      case 16:
        operandSize = 32;
        break;
      default:
        operandSize = -1;
        DBT_OptimizingCompilerException.UNREACHABLE();
      }
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    String source = modrm.disassembleRM(sib, displacement, srcSize,
        addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);
    String destination = modrm.disassembleReg(operandSize);

    return "movsx " + destination + ", " + source;
  }
}

/**
 * The decoder for the MovZX opcode
 */
class X86_MovZX_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * The size of the source register
   */
  private final int srcSize;

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_MovZX_OpcodeDecoder(int destSize, int srcSize) {
    super(destSize, true, 0, true);
    this.srcSize = srcSize;
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination = modrm.getReg(operandSize);
    X86_DecodedOperand source = modrm.getRM(translationHelper, lazy, sib,
        displacement, srcSize, addressSize, (prefix2 != null) ? prefix2
            .getSegment() : X86_Registers.DS);

    // Mask appropriate bits
    OPT_RegisterOperand temp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, temp);
    int mask = (srcSize == 8) ? 0xFF : 0xFFFF;
    translationHelper.appendInstruction(Binary.create(INT_AND,
        temp.copyRO(), temp.copyRO(), new OPT_IntConstantOperand(mask)));
    destination.writeValue(translationHelper, lazy, temp.copyRO());
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else {
      switch (this.operandSize) {
      case 32:
        operandSize = 16;
        break;
      case 16:
        operandSize = 32;
        break;
      default:
        operandSize = -1;
        DBT_OptimizingCompilerException.UNREACHABLE();
      }
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    String source = modrm.disassembleRM(sib, displacement, srcSize,
        addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);
    String destination = modrm.disassembleReg(operandSize);

    return "movzx " + destination + ", " + source;
  }
}

/**
 * The decoder to set or clear flags
 */
class X86_MoveToFlag_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * The value to set the flag to
   */
  private final boolean value;
  /**
   * The flag to set
   */
  private final FLAG flag;

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_MoveToFlag_OpcodeDecoder(FLAG flag, boolean value) {
    super(0, false, 0, false);
    this.flag = flag;
    this.value = value;
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    OPT_RegisterOperand flagOp;
    switch (flag) {
    case CARRY:
      flagOp = translationHelper.getCarryFlag();
      break;
    case DIRECTION:
      flagOp = translationHelper.getDirectionFlag();
      break;
    default:
      TODO();
      flagOp = null;
      break;      
    }
    translationHelper.appendInstruction(
        Move.create(INT_MOVE, flagOp,
            value ? new OPT_IntConstantOperand(1) :
              new OPT_IntConstantOperand(0)));
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    String prefix = value ? "st" : "cl";
    switch (flag) {
    case CARRY:
      return prefix + "c";
    case DIRECTION:
      return prefix + "d";
    case INTERRUPT:
      return prefix + "i";
    default:
      return "Unrecognized flag";  
    }    
  }
}
/**
 * The decoder for the Nop opcode
 */
class X86_Nop_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Nop_OpcodeDecoder() {
    super(_16BIT ? 16 : 32, false, 0, false);
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    return "nop";
  }
}

/**
 * The decoder for the Rdtsc opcode
 */
class X86_Rdtsc_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Rdtsc_OpcodeDecoder() {
    super(32, false, 0, false);
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    OPT_RegisterOperand edx = translationHelper.getGPRegister(lazy,
        X86_Registers.EDX, 32);
    OPT_RegisterOperand eax = translationHelper.getGPRegister(lazy,
        X86_Registers.EAX, 32);
    long time = System.currentTimeMillis(); // TODO - make this dynamic
    translationHelper.appendInstruction(Move.create(INT_MOVE,
        edx, new OPT_IntConstantOperand((int) (time >>> 32))));
    translationHelper.appendInstruction(Move.create(INT_MOVE,
        eax, new OPT_IntConstantOperand((int) time)));
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    return "rdtsc";
  }
}

/**
 * Decoder for store FPU control world
 */
final class X86_Fstcw_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Fstcw_OpcodeDecoder() {
    super(16, true, 0, true);
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination = modrm.getRM(translationHelper, lazy, sib,
        displacement, 16, addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);

    destination.writeValue(translationHelper, lazy, translationHelper
        .getFPU_CW());
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    String destination = modrm.disassembleRM(sib, displacement, 16,
        addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);

    return "fstcw " + destination;
  }
}

/**
 * Decoder for load FPU control world
 */
final class X86_Fldcw_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Fldcw_OpcodeDecoder() {
    super(16, true, 0, true);
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand source = modrm.getRM(translationHelper, lazy, sib,
        displacement, 16, addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);

    source.readToRegister(translationHelper, lazy, translationHelper
        .getFPU_CW());
    return pc + length;
  }

  /**
   * Return the operator for this opcode
   */
  OPT_Operator getOperator() {
    throw new Error("This opcode requires more complex operator decoding");
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    String source = modrm.disassembleRM(sib, displacement, 16, addressSize,
        (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);

    return "fldcw " + source;
  }
}

/**
 * The decoder for the Inc opcode
 */
class X86_Inc_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   * @param reg the register to inc into, or -1 to show that the destination is
   *          a memory operand, or -8/-16/-32 to show that this is an immediate
   *          inc of the appropriate size
   */
  X86_Inc_OpcodeDecoder(int reg) {
    super(_16BIT ? 16 : 32, // operandSize
        (reg == -1), // hasModRM,
        0, // immediateSize
        true, // isMemoryOperandDestination
        reg // register
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    // Get operand
    X86_DecodedOperand source;
    if (modrm != null) {
      source = modrm.getRM(translationHelper, lazy, sib, displacement,
          operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
              : X86_Registers.DS);
    } else {
      source = X86_DecodedOperand.getRegister(register, operandSize);
    }
    // Perform inc
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, sourceOp);
    translationHelper.appendInstruction(Binary.create(INT_ADD,
        sourceOp.copyRO(), sourceOp.copyRO(), new OPT_IntConstantOperand(1)));
    // Write value
    source.writeValue(translationHelper, lazy, sourceOp.copyRO());
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    String source;
    if (modrm != null) {
      source = modrm.disassembleRM(sib, displacement, operandSize, addressSize,
          (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    } else {
      source = X86_Registers.disassemble(register, operandSize);
    }
    return "inc " + source;
  }
}

/**
 * The decoder for the Dec opcode
 */
class X86_Dec_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   * @param reg the register to dec into, or -1 to show that the destination is
   *          a memory operand, or -8/-16/-32 to show that this is an immediate
   *          dec of the appropriate size
   */
  X86_Dec_OpcodeDecoder(int reg) {
    super(_16BIT ? 16 : 32, // operandSize
        (reg == -1), // hasModRM,
        0, // immediateSize
        true, // isMemoryOperandDestination
        reg // register
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    // Get operand
    X86_DecodedOperand source;
    if (modrm != null) {
      source = modrm.getRM(translationHelper, lazy, sib, displacement,
          operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
              : X86_Registers.DS);
    } else {
      source = X86_DecodedOperand.getRegister(register, operandSize);
    }
    // Perform dec
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, sourceOp);
    translationHelper.appendInstruction(Binary.create(INT_SUB,
        sourceOp.copyRO(), sourceOp.copyRO(), new OPT_IntConstantOperand(1)));
    // Write value
    source.writeValue(translationHelper, lazy, sourceOp.copyRO());
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    String source;
    if (modrm != null) {
      source = modrm.disassembleRM(sib, displacement, operandSize, addressSize,
          (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    } else {
      source = X86_Registers.disassemble(register, operandSize);
    }
    return "dec " + source;
  }
}

/**
 * The decoder for the Not opcode
 */
class X86_Not_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   * @param operandSize
   */
  X86_Not_OpcodeDecoder(int operandSize) {
    super(operandSize, // operandSize
        true, // hasModRM,
        0, // immediateSize
        true // isMemoryOperandDestination
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    // Get operand
    X86_DecodedOperand source;
    source = modrm.getRM(translationHelper, lazy, sib, displacement,
        operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);
    // Perform not
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, sourceOp);
    translationHelper.appendInstruction(Unary.create(INT_NOT,
        sourceOp.copyRO(), sourceOp.copyRO()));
    // Write value
    source.writeValue(translationHelper, lazy, sourceOp.copyRO());
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    String source;
    source = modrm.disassembleRM(sib, displacement, operandSize, addressSize,
        (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    return "not " + source;
  }
}

/**
 * The decoder for the Neg opcode
 */
class X86_Neg_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   * @param operandSize
   */
  X86_Neg_OpcodeDecoder(int operandSize) {
    super(operandSize, // operandSize
        true, // hasModRM,
        0, // immediateSize
        true // isMemoryOperandDestination
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    // Get operand
    X86_DecodedOperand source;
    source = modrm.getRM(translationHelper, lazy, sib, displacement,
        operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);
    // Perform neg
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, sourceOp);
    translationHelper.appendInstruction(Unary.create(INT_NEG,
        sourceOp.copyRO(), sourceOp.copyRO()));
    // Write value
    source.writeValue(translationHelper, lazy, sourceOp.copyRO());
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    String source;
    source = modrm.disassembleRM(sib, displacement, operandSize, addressSize,
        (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    return "neg " + source;
  }
}

/**
 * The decoder for the Mul opcode
 */
class X86_Mul_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   * @param operandSize
   */
  X86_Mul_OpcodeDecoder(int operandSize) {
    super(operandSize, // operandSize
        true, // hasModRM,
        0, // immediateSize
        true // isMemoryOperandDestination
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    if (operandSize != 32) {
      TODO();
    }
    // longs to perform the mul in
    OPT_RegisterOperand tempLong1 = translationHelper.getTempLong(0);
    OPT_RegisterOperand tempLong2 = translationHelper.getTempLong(1);
    // Get operands
    X86_DecodedOperand source;
    source = modrm.getRM(translationHelper, lazy, sib, displacement,
        operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, sourceOp);
    translationHelper.appendInstruction(Unary.create(INT_2LONG,
        tempLong1, sourceOp.copyRO()));
    OPT_RegisterOperand edx = translationHelper.getGPRegister(lazy,
        X86_Registers.EDX, operandSize);
    OPT_RegisterOperand eax = translationHelper.getGPRegister(lazy,
        X86_Registers.EAX, operandSize);
    translationHelper.appendInstruction(Unary.create(INT_2LONG,
        tempLong2, eax));

    // Perform mul
    translationHelper.appendInstruction(Binary.create(LONG_MUL,
        tempLong1.copyRO(), tempLong1.copyRO(), tempLong2.copyRO()));
    // Write values
    translationHelper.appendInstruction(Unary.create(LONG_2INT,
        eax.copyRO(), tempLong1.copyRO()));
    translationHelper
        .appendInstruction(Binary.create(LONG_USHR, tempLong1
            .copyRO(), tempLong1.copyRO(), new OPT_IntConstantOperand(32)));
    translationHelper.appendInstruction(Unary.create(LONG_2INT,
        edx, tempLong1.copyRO()));
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    String source;
    source = modrm.disassembleRM(sib, displacement, operandSize, addressSize,
        (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    return "mul " + source;
  }
}

/**
 * The decoder for the Imul opcode. This opcode has 3 forms:
 * EDX:EAX is the destination,
 * reg is the destination with either reg/mem/immediate as a source,
 * or reg is the destination with either reg/mem as a source and an immediate
 * as the second source. 
 */
class X86_Imul_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Is EDX:EAX implicitly the destination of this instruction 
   */
  final boolean isAccumulatorDestination;
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_Imul_OpcodeDecoder(int size, int immediateSize, boolean isAccumulatorDestination) {
    super(size, true, immediateSize, false);
    this.isAccumulatorDestination = isAccumulatorDestination;
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else {
      switch (this.operandSize) {
      case 32:
        operandSize = 16;
        break;
      case 16:
        operandSize = 32;
        break;
      default:
        operandSize = -1;
        DBT_OptimizingCompilerException.UNREACHABLE();
      }
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination = null;
    X86_DecodedOperand source1, source2 = null;

    source1 = modrm.getRM(translationHelper, lazy, sib, displacement,
        operandSize, addressSize,
        (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    
    if (!isAccumulatorDestination) {
      destination = modrm.getReg(operandSize);
      if (immediateSize > 0) {
        // dest = source1 * imm
        source2 = X86_DecodedOperand.getImmediate(immediate);
      } else {
        // dest = dest * source1;
        source2 = destination;
      }
    }

    if (operandSize != 32) {
      TODO();
    }
    
    OPT_RegisterOperand sourceOp1 = translationHelper.getTempInt(1);
    source1.readToRegister(translationHelper, lazy, sourceOp1);
    if (isAccumulatorDestination) {
      // 64bit by 32bit multiply result to go in edx:eax
      OPT_RegisterOperand longSourceOp1 = translationHelper.getTempLong(1);
      translationHelper.appendInstruction(Unary.create(INT_2LONG,
          longSourceOp1, sourceOp1.copyRO()));
      TODO();
    } else {
      OPT_RegisterOperand sourceOp2 = translationHelper.getTempInt(2);
      source2.readToRegister(translationHelper, lazy, sourceOp2);
      OPT_RegisterOperand temp = translationHelper.getTempInt(3);
      translationHelper.appendInstruction(Binary.create(INT_MUL,
          temp, sourceOp1.copyRO(), sourceOp2.copyRO()));      
      destination.writeValue(translationHelper, lazy, temp.copyRO());
      OPT_RegisterOperand carry = translationHelper.getCarryFlag();
      translationHelper.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, carry, sourceOp1.copyRO(), sourceOp2.copyRO(), OPT_ConditionOperand
              .OVERFLOW_FROM_MUL(), new OPT_BranchProfileOperand()));
      OPT_RegisterOperand overflow = translationHelper.getOverflowFlag();
      translationHelper.appendInstruction(
          Move.create(INT_MOVE, overflow, carry.copyRO()));
    }
    return pc + length;
  }

  /**
   * Return "imul"
   */
  String getOperatorString() {
    return "imul";
  }
}

/**
 * The decoder for the Div opcode
 */
class X86_Div_OpcodeDecoder extends X86_OpcodeDecoder {
  /**
   * Constructor, {@see X86_OpcodeDecoder}
   * @param operandSize
   */
  X86_Div_OpcodeDecoder(int operandSize) {
    super(operandSize, // operandSize
        true, // hasModRM,
        0, // immediateSize
        true // isMemoryOperandDestination
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    if (operandSize != 32) {
      TODO();
    }
    // longs to perform the div in
    OPT_RegisterOperand tempLong1 = translationHelper.getTempLong(0);
    OPT_RegisterOperand tempLong2 = translationHelper.getTempLong(1);
    // Create EDX:EAX in tempLong1
    OPT_RegisterOperand edx = translationHelper.getGPRegister(lazy,
        X86_Registers.EDX, operandSize);
    OPT_RegisterOperand eax = translationHelper.getGPRegister(lazy,
        X86_Registers.EAX, operandSize);
    translationHelper.appendInstruction(Unary.create(INT_2LONG,
        tempLong1, edx));
    translationHelper.appendInstruction(Unary.create(INT_2LONG,
        tempLong2, eax));
    translationHelper.appendInstruction(Binary.create(LONG_SHL,
        tempLong1.copyRO(), tempLong1.copyRO(), new OPT_IntConstantOperand(32)));
    translationHelper.appendInstruction(Binary.create(LONG_OR,
        tempLong1.copyRO(), tempLong1.copyRO(), tempLong2.copyRO()));
    // Read unsigned source into tempLong2
    X86_DecodedOperand source;
    source = modrm.getRM(translationHelper, lazy, sib, displacement,
        operandSize, addressSize, (prefix2 != null) ? prefix2.getSegment()
            : X86_Registers.DS);
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(0);
    source.readToRegister(translationHelper, lazy, sourceOp);
    translationHelper.appendInstruction(Unary.create(INT_2LONG,
        tempLong2, sourceOp.copyRO()));
    translationHelper.appendInstruction(Binary.create(LONG_AND,
        tempLong2.copyRO(), tempLong2.copyRO(), new OPT_LongConstantOperand(0xFFFFFFFF)));
    // Check source isn't zero
    OPT_RegisterOperand guard = translationHelper.getTempValidation(0);
    translationHelper.appendInstruction(
        ZeroCheck.create(LONG_ZERO_CHECK, guard, tempLong2.copyRO()));
    
    // Perform div
    OPT_RegisterOperand quotient = translationHelper.getTempLong(2);
    OPT_RegisterOperand remainder = translationHelper.getTempLong(3);
    translationHelper.appendInstruction(GuardedBinary.create(LONG_DIV,
        quotient, tempLong1.copyRO(), tempLong2.copyRO(), guard.copyRO()));
    translationHelper.appendInstruction(GuardedBinary.create(LONG_REM,
        remainder, tempLong1.copyRO(), tempLong2.copyRO(), guard.copyRO()));
    
    // TODO: if the value in EDX:EAX divided by SRC is > 0xFFFFFFFF then a
    // divide error exception should be raised
    
    // Write values
    translationHelper.appendInstruction(Unary.create(LONG_2INT,
        eax.copyRO(), quotient.copyRO()));
    translationHelper.appendInstruction(Unary.create(LONG_2INT,
        edx.copyRO(), remainder.copyRO()));
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    String source;
    source = modrm.disassembleRM(sib, displacement, operandSize, addressSize,
        (prefix2 != null) ? prefix2.getSegment() : X86_Registers.DS);
    return "div " + source;
  }
}

/**
 * The decoder for the PushA opcode
 */
class X86_PushA_OpcodeDecoder extends X86_OpcodeDecoder {
  /** The registers to push in order */
  final static int pushARegs[] = { X86_Registers.EAX, X86_Registers.ECX,
      X86_Registers.EDX, X86_Registers.EBX, X86_Registers.ESP,
      X86_Registers.EBP, X86_Registers.ESI, X86_Registers.EDI };

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_PushA_OpcodeDecoder() {
    super(_16BIT ? 16 : 32, // operandSize
        false, // hasModRM,
        0, // immediateSize
        true // isMemoryOperandDestination
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }

    X86_DecodedOperand destination = X86_DecodedOperand.getStack(addressSize,
        operandSize);
    X86_DecodedOperand source;
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(0);
    OPT_RegisterOperand esp = translationHelper.getGPRegister(lazy,
        X86_Registers.ESP, addressSize);
    OPT_RegisterOperand original_esp = translationHelper.getTempInt(1);
    translationHelper.appendInstruction(Move.create(INT_MOVE,
        original_esp, esp));
    for (int i = 0; i < pushARegs.length; i++) {
      int reg = pushARegs[i];
      if (reg != X86_Registers.ESP) {
        source = X86_DecodedOperand.getRegister(reg, operandSize);
        source.readToRegister(translationHelper, lazy, sourceOp.copyRO());
      } else {
        translationHelper.appendInstruction(Move.create(INT_MOVE,
            sourceOp.copyRO(), original_esp.copyRO()));
      }
      // Decrement stack pointer
      translationHelper.appendInstruction(Binary.create(INT_SUB,
          esp.copyRO(), esp.copyRO(), new OPT_IntConstantOperand(
              addressSize >>> 3)));
      // Write value
      destination.writeValue(translationHelper, lazy, sourceOp.copyRO());
    }

    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    if (operandSize == 32) {
      return "pushad";
    } else {
      return "pusha";
    }
  }
}

/**
 * The decoder for the PopA opcode
 */
class X86_PopA_OpcodeDecoder extends X86_OpcodeDecoder {
  /** The registers to pop in order */
  final static int popARegs[] = { X86_Registers.EDI, X86_Registers.ESI,
      X86_Registers.EBP, X86_Registers.ESP, X86_Registers.EBX,
      X86_Registers.EDX, X86_Registers.ECX, X86_Registers.EAX };

  /**
   * Constructor, {@see X86_OpcodeDecoder}
   */
  X86_PopA_OpcodeDecoder() {
    super(_16BIT ? 16 : 32, // operandSize
        false, // hasModRM,
        0, // immediateSize
        true // isMemoryOperandDestination
    );
  }

  /**
   * Perform the actual translation
   * @param translationHelper
   * @param ps
   * @param lazy
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immediate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected int translate(X862IR translationHelper, ProcessSpace ps,
      X86_Laziness lazy, int pc, X86_ModRM_Decoder modrm, X86_SIB_Decoder sib,
      int displacement, int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    int addressSize;
    if (prefix4 == null) {
      addressSize = _16BIT ? 16 : 32;
    } else {
      addressSize = _16BIT ? 32 : 16;
    }
    X86_DecodedOperand destination;
    X86_DecodedOperand source = X86_DecodedOperand.getStack(addressSize,
        operandSize);
    OPT_RegisterOperand sourceOp = translationHelper.getTempInt(0);
    OPT_RegisterOperand esp = translationHelper.getGPRegister(lazy,
        X86_Registers.ESP, addressSize);
    for (int i = 0; i < popARegs.length; i++) {
      int reg = popARegs[i];
      if (reg != X86_Registers.ESP) {
        destination = X86_DecodedOperand.getRegister(reg, operandSize);
        source.readToRegister(translationHelper, lazy, sourceOp.copyRO());
        // Write value
        destination.writeValue(translationHelper, lazy, sourceOp.copyRO());
        // Increment stack pointer
        translationHelper.appendInstruction(Binary.create(
            INT_ADD, esp.copyRO(), esp.copyRO(), new OPT_IntConstantOperand(
                addressSize >>> 3)));
      } else { // ESP is ignored - increment stack pointer
        translationHelper.appendInstruction(Binary.create(
            INT_ADD, esp.copyRO(), esp.copyRO(), new OPT_IntConstantOperand(
                addressSize >>> 3)));
      }
    }
    return pc + length;
  }

  /**
   * Disassemble the opcode
   * @param ps
   * @param pc the address of the instruction being translated
   * @param modrm the decoder for any modrm part of the instruction
   * @param sib the sib decoder for any sib part of the instruction
   * @param displacement any displacement to be added to the modrm
   * @param immediateSize what size is the immediate value
   * @param immedate if immediateSize &gt; 0 then this is the immediate value
   * @param length the length of the instruction
   * @param prefix2 a group2 prefix decoder or null
   * @param prefix3 a group3 prefix decoder or null
   * @param prefix4 a group4 prefix decoder or null
   * @param prefix5 a group5 prefix decoder or null
   */
  protected String disassemble(ProcessSpace ps, int pc,
      X86_ModRM_Decoder modrm, X86_SIB_Decoder sib, int displacement,
      int immediateSize, int immediate, int length,
      X86_Group2PrefixDecoder prefix2, X86_Group3PrefixDecoder prefix3,
      X86_Group4PrefixDecoder prefix4, X86_Group5PrefixDecoder prefix5) {
    int operandSize;
    if (prefix3 == null) {
      operandSize = this.operandSize;
    } else if (this.operandSize == 32) {
      operandSize = 16;
    } else {
      operandSize = 32;
    }
    if (operandSize == 32) {
      return "popad";
    } else {
      return "popa";
    }
  }
}
