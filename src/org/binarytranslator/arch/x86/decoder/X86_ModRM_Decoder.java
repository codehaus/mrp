/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.x86.decoder;

import org.binarytranslator.arch.x86.os.process.X86_Registers;

import org.binarytranslator.generic.decoder.DecoderUtils;
import org.binarytranslator.generic.decoder.Laziness;

/**
 * The decoder for the ModRM byte of the instruction
 */
class X86_ModRM_Decoder extends X86_InstructionDecoder {
  /**
   * Look up table of ModRM values to the appropriate decoder
   */
  private static final X86_ModRM_Decoder modrmLookup[] = {
    /* ModRM   Decoder         Mod,Reg or Opcode,R/M */
    /* 0x00 */ new X86_ModRM_Decoder(0,0,0),
    /* 0x01 */ new X86_ModRM_Decoder(0,0,1),
    /* 0x02 */ new X86_ModRM_Decoder(0,0,2),
    /* 0x03 */ new X86_ModRM_Decoder(0,0,3),
    /* 0x04 */ new X86_ModRM_Decoder(0,0,4),
    /* 0x05 */ new X86_ModRM_Decoder(0,0,5),
    /* 0x06 */ new X86_ModRM_Decoder(0,0,6),
    /* 0x07 */ new X86_ModRM_Decoder(0,0,7),

    /* 0x08 */ new X86_ModRM_Decoder(0,1,0),
    /* 0x09 */ new X86_ModRM_Decoder(0,1,1),
    /* 0x0A */ new X86_ModRM_Decoder(0,1,2),
    /* 0x0B */ new X86_ModRM_Decoder(0,1,3),
    /* 0x0C */ new X86_ModRM_Decoder(0,1,4),
    /* 0x0D */ new X86_ModRM_Decoder(0,1,5),
    /* 0x0E */ new X86_ModRM_Decoder(0,1,6),
    /* 0x0F */ new X86_ModRM_Decoder(0,1,7),

    /* 0x10 */ new X86_ModRM_Decoder(0,2,0),
    /* 0x11 */ new X86_ModRM_Decoder(0,2,1),
    /* 0x12 */ new X86_ModRM_Decoder(0,2,2),
    /* 0x13 */ new X86_ModRM_Decoder(0,2,3),
    /* 0x14 */ new X86_ModRM_Decoder(0,2,4),
    /* 0x15 */ new X86_ModRM_Decoder(0,2,5),
    /* 0x16 */ new X86_ModRM_Decoder(0,2,6),
    /* 0x17 */ new X86_ModRM_Decoder(0,2,7),

    /* 0x18 */ new X86_ModRM_Decoder(0,3,0),
    /* 0x19 */ new X86_ModRM_Decoder(0,3,1),
    /* 0x1A */ new X86_ModRM_Decoder(0,3,2),
    /* 0x1B */ new X86_ModRM_Decoder(0,3,3),
    /* 0x1C */ new X86_ModRM_Decoder(0,3,4),
    /* 0x1D */ new X86_ModRM_Decoder(0,3,5),
    /* 0x1E */ new X86_ModRM_Decoder(0,3,6),
    /* 0x1F */ new X86_ModRM_Decoder(0,3,7),

    /* 0x20 */ new X86_ModRM_Decoder(0,4,0),
    /* 0x21 */ new X86_ModRM_Decoder(0,4,1),
    /* 0x22 */ new X86_ModRM_Decoder(0,4,2),
    /* 0x23 */ new X86_ModRM_Decoder(0,4,3),
    /* 0x24 */ new X86_ModRM_Decoder(0,4,4),
    /* 0x25 */ new X86_ModRM_Decoder(0,4,5),
    /* 0x26 */ new X86_ModRM_Decoder(0,4,6),
    /* 0x27 */ new X86_ModRM_Decoder(0,4,7),

    /* 0x28 */ new X86_ModRM_Decoder(0,5,0),
    /* 0x29 */ new X86_ModRM_Decoder(0,5,1),
    /* 0x2A */ new X86_ModRM_Decoder(0,5,2),
    /* 0x2B */ new X86_ModRM_Decoder(0,5,3),
    /* 0x2C */ new X86_ModRM_Decoder(0,5,4),
    /* 0x2D */ new X86_ModRM_Decoder(0,5,5),
    /* 0x2E */ new X86_ModRM_Decoder(0,5,6),
    /* 0x2F */ new X86_ModRM_Decoder(0,5,7),

    /* 0x30 */ new X86_ModRM_Decoder(0,6,0),
    /* 0x31 */ new X86_ModRM_Decoder(0,6,1),
    /* 0x32 */ new X86_ModRM_Decoder(0,6,2),
    /* 0x33 */ new X86_ModRM_Decoder(0,6,3),
    /* 0x34 */ new X86_ModRM_Decoder(0,6,4),
    /* 0x35 */ new X86_ModRM_Decoder(0,6,5),
    /* 0x36 */ new X86_ModRM_Decoder(0,6,6),
    /* 0x37 */ new X86_ModRM_Decoder(0,6,7),

    /* 0x38 */ new X86_ModRM_Decoder(0,7,0),
    /* 0x39 */ new X86_ModRM_Decoder(0,7,1),
    /* 0x3A */ new X86_ModRM_Decoder(0,7,2),
    /* 0x3B */ new X86_ModRM_Decoder(0,7,3),
    /* 0x3C */ new X86_ModRM_Decoder(0,7,4),
    /* 0x3D */ new X86_ModRM_Decoder(0,7,5),
    /* 0x3E */ new X86_ModRM_Decoder(0,7,6),
    /* 0x3F */ new X86_ModRM_Decoder(0,7,7),

    /* 0x40 */ new X86_ModRM_Decoder(1,0,0),
    /* 0x41 */ new X86_ModRM_Decoder(1,0,1),
    /* 0x42 */ new X86_ModRM_Decoder(1,0,2),
    /* 0x43 */ new X86_ModRM_Decoder(1,0,3),
    /* 0x44 */ new X86_ModRM_Decoder(1,0,4),
    /* 0x45 */ new X86_ModRM_Decoder(1,0,5),
    /* 0x46 */ new X86_ModRM_Decoder(1,0,6),
    /* 0x47 */ new X86_ModRM_Decoder(1,0,7),

    /* 0x48 */ new X86_ModRM_Decoder(1,1,0),
    /* 0x49 */ new X86_ModRM_Decoder(1,1,1),
    /* 0x4A */ new X86_ModRM_Decoder(1,1,2),
    /* 0x4B */ new X86_ModRM_Decoder(1,1,3),
    /* 0x4C */ new X86_ModRM_Decoder(1,1,4),
    /* 0x4D */ new X86_ModRM_Decoder(1,1,5),
    /* 0x4E */ new X86_ModRM_Decoder(1,1,6),
    /* 0x4F */ new X86_ModRM_Decoder(1,1,7),

    /* 0x50 */ new X86_ModRM_Decoder(1,2,0),
    /* 0x51 */ new X86_ModRM_Decoder(1,2,1),
    /* 0x52 */ new X86_ModRM_Decoder(1,2,2),
    /* 0x53 */ new X86_ModRM_Decoder(1,2,3),
    /* 0x54 */ new X86_ModRM_Decoder(1,2,4),
    /* 0x55 */ new X86_ModRM_Decoder(1,2,5),
    /* 0x56 */ new X86_ModRM_Decoder(1,2,6),
    /* 0x57 */ new X86_ModRM_Decoder(1,2,7),

    /* 0x58 */ new X86_ModRM_Decoder(1,3,0),
    /* 0x59 */ new X86_ModRM_Decoder(1,3,1),
    /* 0x5A */ new X86_ModRM_Decoder(1,3,2),
    /* 0x5B */ new X86_ModRM_Decoder(1,3,3),
    /* 0x5C */ new X86_ModRM_Decoder(1,3,4),
    /* 0x5D */ new X86_ModRM_Decoder(1,3,5),
    /* 0x5E */ new X86_ModRM_Decoder(1,3,6),
    /* 0x5F */ new X86_ModRM_Decoder(1,3,7),

    /* 0x60 */ new X86_ModRM_Decoder(1,4,0),
    /* 0x61 */ new X86_ModRM_Decoder(1,4,1),
    /* 0x62 */ new X86_ModRM_Decoder(1,4,2),
    /* 0x63 */ new X86_ModRM_Decoder(1,4,3),
    /* 0x64 */ new X86_ModRM_Decoder(1,4,4),
    /* 0x65 */ new X86_ModRM_Decoder(1,4,5),
    /* 0x66 */ new X86_ModRM_Decoder(1,4,6),
    /* 0x67 */ new X86_ModRM_Decoder(1,4,7),

    /* 0x68 */ new X86_ModRM_Decoder(1,5,0),
    /* 0x69 */ new X86_ModRM_Decoder(1,5,1),
    /* 0x6A */ new X86_ModRM_Decoder(1,5,2),
    /* 0x6B */ new X86_ModRM_Decoder(1,5,3),
    /* 0x6C */ new X86_ModRM_Decoder(1,5,4),
    /* 0x6D */ new X86_ModRM_Decoder(1,5,5),
    /* 0x6E */ new X86_ModRM_Decoder(1,5,6),
    /* 0x6F */ new X86_ModRM_Decoder(1,5,7),

    /* 0x70 */ new X86_ModRM_Decoder(1,6,0),
    /* 0x71 */ new X86_ModRM_Decoder(1,6,1),
    /* 0x72 */ new X86_ModRM_Decoder(1,6,2),
    /* 0x73 */ new X86_ModRM_Decoder(1,6,3),
    /* 0x74 */ new X86_ModRM_Decoder(1,6,4),
    /* 0x75 */ new X86_ModRM_Decoder(1,6,5),
    /* 0x76 */ new X86_ModRM_Decoder(1,6,6),
    /* 0x77 */ new X86_ModRM_Decoder(1,6,7),

    /* 0x78 */ new X86_ModRM_Decoder(1,7,0),
    /* 0x79 */ new X86_ModRM_Decoder(1,7,1),
    /* 0x7A */ new X86_ModRM_Decoder(1,7,2),
    /* 0x7B */ new X86_ModRM_Decoder(1,7,3),
    /* 0x7C */ new X86_ModRM_Decoder(1,7,4),
    /* 0x7D */ new X86_ModRM_Decoder(1,7,5),
    /* 0x7E */ new X86_ModRM_Decoder(1,7,6),
    /* 0x7F */ new X86_ModRM_Decoder(1,7,7),

    /* 0x80 */ new X86_ModRM_Decoder(2,0,0),
    /* 0x81 */ new X86_ModRM_Decoder(2,0,1),
    /* 0x82 */ new X86_ModRM_Decoder(2,0,2),
    /* 0x83 */ new X86_ModRM_Decoder(2,0,3),
    /* 0x84 */ new X86_ModRM_Decoder(2,0,4),
    /* 0x85 */ new X86_ModRM_Decoder(2,0,5),
    /* 0x86 */ new X86_ModRM_Decoder(2,0,6),
    /* 0x87 */ new X86_ModRM_Decoder(2,0,7),

    /* 0x88 */ new X86_ModRM_Decoder(2,1,0),
    /* 0x89 */ new X86_ModRM_Decoder(2,1,1),
    /* 0x8A */ new X86_ModRM_Decoder(2,1,2),
    /* 0x8B */ new X86_ModRM_Decoder(2,1,3),
    /* 0x8C */ new X86_ModRM_Decoder(2,1,4),
    /* 0x8D */ new X86_ModRM_Decoder(2,1,5),
    /* 0x8E */ new X86_ModRM_Decoder(2,1,6),
    /* 0x8F */ new X86_ModRM_Decoder(2,1,7),

    /* 0x90 */ new X86_ModRM_Decoder(2,2,0),
    /* 0x91 */ new X86_ModRM_Decoder(2,2,1),
    /* 0x92 */ new X86_ModRM_Decoder(2,2,2),
    /* 0x93 */ new X86_ModRM_Decoder(2,2,3),
    /* 0x94 */ new X86_ModRM_Decoder(2,2,4),
    /* 0x95 */ new X86_ModRM_Decoder(2,2,5),
    /* 0x96 */ new X86_ModRM_Decoder(2,2,6),
    /* 0x97 */ new X86_ModRM_Decoder(2,2,7),

    /* 0x98 */ new X86_ModRM_Decoder(2,3,0),
    /* 0x99 */ new X86_ModRM_Decoder(2,3,1),
    /* 0x9A */ new X86_ModRM_Decoder(2,3,2),
    /* 0x9B */ new X86_ModRM_Decoder(2,3,3),
    /* 0x9C */ new X86_ModRM_Decoder(2,3,4),
    /* 0x9D */ new X86_ModRM_Decoder(2,3,5),
    /* 0x9E */ new X86_ModRM_Decoder(2,3,6),
    /* 0x9F */ new X86_ModRM_Decoder(2,3,7),

    /* 0xA0 */ new X86_ModRM_Decoder(2,4,0),
    /* 0xA1 */ new X86_ModRM_Decoder(2,4,1),
    /* 0xA2 */ new X86_ModRM_Decoder(2,4,2),
    /* 0xA3 */ new X86_ModRM_Decoder(2,4,3),
    /* 0xA4 */ new X86_ModRM_Decoder(2,4,4),
    /* 0xA5 */ new X86_ModRM_Decoder(2,4,5),
    /* 0xA6 */ new X86_ModRM_Decoder(2,4,6),
    /* 0xA7 */ new X86_ModRM_Decoder(2,4,7),

    /* 0xA8 */ new X86_ModRM_Decoder(2,5,0),
    /* 0xA9 */ new X86_ModRM_Decoder(2,5,1),
    /* 0xAA */ new X86_ModRM_Decoder(2,5,2),
    /* 0xAB */ new X86_ModRM_Decoder(2,5,3),
    /* 0xAC */ new X86_ModRM_Decoder(2,5,4),
    /* 0xAD */ new X86_ModRM_Decoder(2,5,5),
    /* 0xAE */ new X86_ModRM_Decoder(2,5,6),
    /* 0xAF */ new X86_ModRM_Decoder(2,5,7),

    /* 0xB0 */ new X86_ModRM_Decoder(2,6,0),
    /* 0xB1 */ new X86_ModRM_Decoder(2,6,1),
    /* 0xB2 */ new X86_ModRM_Decoder(2,6,2),
    /* 0xB3 */ new X86_ModRM_Decoder(2,6,3),
    /* 0xB4 */ new X86_ModRM_Decoder(2,6,4),
    /* 0xB5 */ new X86_ModRM_Decoder(2,6,5),
    /* 0xB6 */ new X86_ModRM_Decoder(2,6,6),
    /* 0xB7 */ new X86_ModRM_Decoder(2,6,7),

    /* 0xB8 */ new X86_ModRM_Decoder(2,7,0),
    /* 0xB9 */ new X86_ModRM_Decoder(2,7,1),
    /* 0xBA */ new X86_ModRM_Decoder(2,7,2),
    /* 0xBB */ new X86_ModRM_Decoder(2,7,3),
    /* 0xBC */ new X86_ModRM_Decoder(2,7,4),
    /* 0xBD */ new X86_ModRM_Decoder(2,7,5),
    /* 0xBE */ new X86_ModRM_Decoder(2,7,6),
    /* 0xBF */ new X86_ModRM_Decoder(2,7,7),

    /* 0xC0 */ new X86_ModRM_Decoder(3,0,0),
    /* 0xC1 */ new X86_ModRM_Decoder(3,0,1),
    /* 0xC2 */ new X86_ModRM_Decoder(3,0,2),
    /* 0xC3 */ new X86_ModRM_Decoder(3,0,3),
    /* 0xC4 */ new X86_ModRM_Decoder(3,0,4),
    /* 0xC5 */ new X86_ModRM_Decoder(3,0,5),
    /* 0xC6 */ new X86_ModRM_Decoder(3,0,6),
    /* 0xC7 */ new X86_ModRM_Decoder(3,0,7),

    /* 0xC8 */ new X86_ModRM_Decoder(3,1,0),
    /* 0xC9 */ new X86_ModRM_Decoder(3,1,1),
    /* 0xCA */ new X86_ModRM_Decoder(3,1,2),
    /* 0xCB */ new X86_ModRM_Decoder(3,1,3),
    /* 0xCC */ new X86_ModRM_Decoder(3,1,4),
    /* 0xCD */ new X86_ModRM_Decoder(3,1,5),
    /* 0xCE */ new X86_ModRM_Decoder(3,1,6),
    /* 0xCF */ new X86_ModRM_Decoder(3,1,7),

    /* 0xD0 */ new X86_ModRM_Decoder(3,2,0),
    /* 0xD1 */ new X86_ModRM_Decoder(3,2,1),
    /* 0xD2 */ new X86_ModRM_Decoder(3,2,2),
    /* 0xD3 */ new X86_ModRM_Decoder(3,2,3),
    /* 0xD4 */ new X86_ModRM_Decoder(3,2,4),
    /* 0xD5 */ new X86_ModRM_Decoder(3,2,5),
    /* 0xD6 */ new X86_ModRM_Decoder(3,2,6),
    /* 0xD7 */ new X86_ModRM_Decoder(3,2,7),

    /* 0xD8 */ new X86_ModRM_Decoder(3,3,0),
    /* 0xD9 */ new X86_ModRM_Decoder(3,3,1),
    /* 0xDA */ new X86_ModRM_Decoder(3,3,2),
    /* 0xDB */ new X86_ModRM_Decoder(3,3,3),
    /* 0xDC */ new X86_ModRM_Decoder(3,3,4),
    /* 0xDD */ new X86_ModRM_Decoder(3,3,5),
    /* 0xDE */ new X86_ModRM_Decoder(3,3,6),
    /* 0xDF */ new X86_ModRM_Decoder(3,3,7),

    /* 0xE0 */ new X86_ModRM_Decoder(3,4,0),
    /* 0xE1 */ new X86_ModRM_Decoder(3,4,1),
    /* 0xE2 */ new X86_ModRM_Decoder(3,4,2),
    /* 0xE3 */ new X86_ModRM_Decoder(3,4,3),
    /* 0xE4 */ new X86_ModRM_Decoder(3,4,4),
    /* 0xE5 */ new X86_ModRM_Decoder(3,4,5),
    /* 0xE6 */ new X86_ModRM_Decoder(3,4,6),
    /* 0xE7 */ new X86_ModRM_Decoder(3,4,7),

    /* 0xE8 */ new X86_ModRM_Decoder(3,5,0),
    /* 0xE9 */ new X86_ModRM_Decoder(3,5,1),
    /* 0xEA */ new X86_ModRM_Decoder(3,5,2),
    /* 0xEB */ new X86_ModRM_Decoder(3,5,3),
    /* 0xEC */ new X86_ModRM_Decoder(3,5,4),
    /* 0xED */ new X86_ModRM_Decoder(3,5,5),
    /* 0xEE */ new X86_ModRM_Decoder(3,5,6),
    /* 0xEF */ new X86_ModRM_Decoder(3,5,7),

    /* 0xF0 */ new X86_ModRM_Decoder(3,6,0),
    /* 0xF1 */ new X86_ModRM_Decoder(3,6,1),
    /* 0xF2 */ new X86_ModRM_Decoder(3,6,2),
    /* 0xF3 */ new X86_ModRM_Decoder(3,6,3),
    /* 0xF4 */ new X86_ModRM_Decoder(3,6,4),
    /* 0xF5 */ new X86_ModRM_Decoder(3,6,5),
    /* 0xF6 */ new X86_ModRM_Decoder(3,6,6),
    /* 0xF7 */ new X86_ModRM_Decoder(3,6,7),

    /* 0xF8 */ new X86_ModRM_Decoder(3,7,0),
    /* 0xF9 */ new X86_ModRM_Decoder(3,7,1),
    /* 0xFA */ new X86_ModRM_Decoder(3,7,2),
    /* 0xFB */ new X86_ModRM_Decoder(3,7,3),
    /* 0xFC */ new X86_ModRM_Decoder(3,7,4),
    /* 0xFD */ new X86_ModRM_Decoder(3,7,5),
    /* 0xFE */ new X86_ModRM_Decoder(3,7,6),
    /* 0xFF */ new X86_ModRM_Decoder(3,7,7)
  };    
  /**
   * Return an appropriate decoder from the modrm value
   */
  static X86_ModRM_Decoder getModRM_Decoder(int modrmVal) {
    return modrmLookup[modrmVal];
  }
  /**
   * The Mod field for this byte
   */
  private final int mod;
  /**
   * The REG field for this byte
   */
  private final int reg;
  /**
   * The RM field for this byte
   */
  private final int rm;
  /**
   * Constructor
   */
  X86_ModRM_Decoder(int mod, int reg, int rm) {
    this.mod = mod;
    this.reg = reg;
    this.rm = rm;
  }
  /**
   * Does a SIB byte follow?
   * @return true means an SIB byte follows the ModRM byte
   */
  boolean hasSIB() {
    if (_16BIT) {
      return false;
    }
    else {
      return (mod != 3) && (rm == 4);
    }
  }
  /**
   * What is the size of the following displacement?
   * @param override has the address size been overridden?
   * @return size in bits of displacement
   */
  int displacementSize(boolean override, X86_SIB_Decoder sib) {
    switch(mod) {
    case 0:
      if (_16BIT || override) {
        return (rm == 6) ? 16 : 0;
      }
      else { // 32bit
        if ((rm == 5) ||
            ((rm == 4) && (sib.getBase(mod) == -1))) {            
          return 32;
        }
        else {
          return 0;
        }
      }
    case 1:
      return 8;
    case 2:
      return (_16BIT || override) ? 16 : 32;
    default:
      return 0;
    }
  }
  /**
   * Get the RM field as a decoded operand, the RM field encodes
   * either a register or memory location
   */
  X86_DecodedOperand getRM(DecoderUtils translationHelper, Laziness lazy, X86_SIB_Decoder sib, int displacement, int operandSize, int addressSize, int segment) {
    if(addressSize == 32) {
      switch(mod) {
      case 0:
        switch(rm) {
        case 0:  return X86_DecodedOperand.getMemory(segment, X86_Registers.EAX, 0, -1, 0, addressSize, operandSize);
        case 1:  return X86_DecodedOperand.getMemory(segment, X86_Registers.ECX, 0, -1, 0, addressSize, operandSize);
        case 2:  return X86_DecodedOperand.getMemory(segment, X86_Registers.EDX, 0, -1, 0, addressSize, operandSize);
        case 3:  return X86_DecodedOperand.getMemory(segment, X86_Registers.EBX, 0, -1, 0, addressSize, operandSize);
        case 4:  return X86_DecodedOperand.getMemory(segment, sib.getBase(mod), sib.getScale(), sib.getIndex(), displacement, addressSize, operandSize);
        case 5:  return X86_DecodedOperand.getMemory(segment, -1, 0, 0, displacement, addressSize, operandSize);
        case 6:  return X86_DecodedOperand.getMemory(segment, X86_Registers.ESI, 0, -1, 0, addressSize, operandSize);
        default: return X86_DecodedOperand.getMemory(segment, X86_Registers.EDI, 0, -1, 0, addressSize, operandSize);
        }
      case 1:
      case 2:
        switch(rm) {
        case 0:  return X86_DecodedOperand.getMemory(segment, X86_Registers.EAX, 0, -1, displacement, addressSize, operandSize);
        case 1:  return X86_DecodedOperand.getMemory(segment, X86_Registers.ECX, 0, -1, displacement, addressSize, operandSize);
        case 2:  return X86_DecodedOperand.getMemory(segment, X86_Registers.EDX, 0, -1, displacement, addressSize, operandSize);
        case 3:  return X86_DecodedOperand.getMemory(segment, X86_Registers.EBX, 0, -1, displacement, addressSize, operandSize);
        case 4:  return X86_DecodedOperand.getMemory(segment, sib.getBase(mod), sib.getScale(), sib.getIndex(), displacement, addressSize, operandSize);
        case 5:  return X86_DecodedOperand.getMemory(segment, X86_Registers.EBP, 0, -1, displacement, addressSize, operandSize);
        case 6:  return X86_DecodedOperand.getMemory(segment, X86_Registers.ESI, 0, -1, displacement, addressSize, operandSize);
        default: return X86_DecodedOperand.getMemory(segment, X86_Registers.EDI, 0, -1, displacement, addressSize, operandSize);
        }
      default: // Case 3, the RM field just encodes a direct register access
        return X86_DecodedOperand.getRegister(rm, operandSize);
      }
    }
    else {
      TODO();
      return null;
    }
  }
  /**
   * Get the reg field as a decoded operand, the reg field encodes
   * either a register or an opcode
   */
  X86_DecodedOperand getReg(int size) {
    return X86_DecodedOperand.getRegister(reg, size);
  }
  /**
   * Get the reg/opcode field as an integer
   */
  int getOpcode() {
    return reg;
  }
  /**
   * Disassemble the RM field as a decoded operand, the RM field
   * encodes either a register or memory location
   */
  String disassembleRM(X86_SIB_Decoder sib, int displacement, int operandSize, int addressSize, int seg) {
    String segment = X86_Registers.disassembleSegment(seg)+":";
    if (addressSize == 32) {
      switch(mod) {
      case 0: // no displacement, rm==4 or rm==5 are special
        switch(rm) {
        case 0:  return segment+"[EAX]";
        case 1:  return segment+"[ECX]";
        case 2:  return segment+"[EDX]";
        case 3:  return segment+"[EBX]";
        case 4:  return segment+"["+sib.disassemble(mod)+"]";
        case 5:  return segment+"[0x" + Integer.toHexString(displacement) +"]";
        case 6:  return segment+"[ESI]";
        default: return segment+"[EDI]";
        }
      case 1: // 8bit displacement
      case 2: // 32bit displacement
        switch(rm) {
        case 0:  return segment+"[EAX+0x" + Integer.toHexString(displacement) + "]";
        case 1:  return segment+"[ECX+0x" + Integer.toHexString(displacement) + "]";
        case 2:  return segment+"[EDX+0x" + Integer.toHexString(displacement) + "]";
        case 3:  return segment+"[EBX+0x" + Integer.toHexString(displacement) + "]";
        case 4:  return segment+"["+sib.disassemble(mod)  + "+0x" +Integer.toHexString(displacement)+"]";
        case 5:  return segment+"[EBP+0x" + Integer.toHexString(displacement) + "]";
        case 6:  return segment+"[ESI+0x" + Integer.toHexString(displacement) + "]";
        default: return segment+"[EDI+0x" + Integer.toHexString(displacement) + "]";
        }
      default: // Case 3, the RM field just encodes a direct register access
        return X86_Registers.disassemble(rm, operandSize);
      }
    }
    else if (addressSize == 16) {
      switch(mod) {
      case 0: // no displacement and rm==6 is special
        switch(rm) {
        case 0:  return segment+"[BX+SI]";
        case 1:  return segment+"[BX+DI]";
        case 2:  return segment+"[BP+SI]";
        case 3:  return segment+"[BP+DI]";
        case 4:  return segment+"[SI]";
        case 5:  return segment+"[DI]";
        case 6:  return segment+"[0x"+Integer.toHexString(displacement)+"]";
        default: return segment+"[BX]";
        }
      case 1: // 8bit displacement
      case 2: // 16bit displacement
        switch(rm) {
        case 0:  return segment+"[BX+SI+0x" + Integer.toHexString(displacement) + "]";
        case 1:  return segment+"[BX+DI+0x" + Integer.toHexString(displacement) + "]";
        case 2:  return segment+"[BP+SI+0x" + Integer.toHexString(displacement) + "]";
        case 3:  return segment+"[BP+DI+0x" + Integer.toHexString(displacement) + "]";
        case 4:  return segment+"[SI+0x" + Integer.toHexString(displacement) + "]";
        case 5:  return segment+"[DI+0x" + Integer.toHexString(displacement) + "]";
        case 6:  return segment+"[BP+0x" + Integer.toHexString(displacement) + "]";
        default: return segment+"[BX+0x" + Integer.toHexString(displacement) + "]";
        }
      default: // Case 3, the RM field just encodes a direct register access
        return X86_Registers.disassemble(rm, operandSize);
      }
    }
    else {
      TODO();
      return "TODO";
    }
  }
  /**
   * Disassemble the reg field as a decoded operand, the reg field
   * encodes either a register or an opcode
   */
  String disassembleReg(int size) {
    return X86_Registers.disassemble(reg, size);
  }
}
