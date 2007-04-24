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
import org.jikesrvm.compilers.opt.ir.*;

/**
 * Wrapper for X86 decoded operands that are either in memory, registers or
 * immediates
 */
abstract class X86_DecodedOperand implements OPT_Operators {
  /**
   * Read the value into a register
   */
  abstract void readToRegister(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op);

  /**
   * Write the given operand to this
   */
  abstract void writeValue(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op);

  /**
   * Read the value as giving an address
   */
  abstract void readEffectiveAddress(X862IR translationHelper,
      X86_Laziness lazy, OPT_RegisterOperand op);

  /**
   * Get a decoded operand for an immediate
   */
  static X86_DecodedOperand getImmediate(int immediate) {
    return new X86_IntDecodedOperand(immediate);
  }

  /**
   * Get a decoded operand for a register
   */
  static X86_DecodedOperand getRegister(int reg, int size) {
    return new X86_RegDecodedOperand(reg, size);
  }

  /**
   * Get a decoded operand for a segment register
   */
  static X86_DecodedOperand getSegmentRegister(int reg) {
    return new X86_SegRegDecodedOperand(reg);
  }

  /**
   * Get a memory reference to the stack
   */
  static X86_DecodedOperand getStack(int addressSize, int operandSize) {
    return new X86_MemDecodedOperand(X86_Registers.SS, X86_Registers.ESP,
        addressSize, operandSize);
  }

  /**
   * Get a memory reference
   */
  static X86_DecodedOperand getMemory(int segment, int base, int scale,
      int index, int displacement, int addressSize, int operandSize) {
    return new X86_MemDecodedOperand(segment, base, scale, index, displacement,
        addressSize, operandSize);
  }
}

/**
 * Immediate constants
 */
final class X86_IntDecodedOperand extends X86_DecodedOperand {
  /**
   * The immediate value in question
   */
  final int immediate;

  /**
   * Constructor
   */
  X86_IntDecodedOperand(int immediate) {
    this.immediate = immediate;
  }

  /**
   * Read the value into a register
   */
  void readToRegister(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    translationHelper.appendInstructionToCurrentBlock(Move.create(INT_MOVE, op,
        new OPT_IntConstantOperand(immediate)));
  }

  /**
   * Write the given operand to this
   */
  void writeValue(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    throw new Error("Trying to write a value to an immediate!");
  }

  /**
   * Read the value as giving an address
   */
  void readEffectiveAddress(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    throw new Error("Trying to read the address of an immediate!");
  }
}

/**
 * Registers
 */
final class X86_RegDecodedOperand extends X86_DecodedOperand {
  /**
   * The register in question
   */
  final int reg;

  /**
   * The size of the register
   */
  final int size;

  /**
   * Constructor
   */
  X86_RegDecodedOperand(int reg, int size) {
    this.reg = reg;
    this.size = size;
  }

  /**
   * Read the value into a register
   */
  void readToRegister(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    translationHelper.appendInstructionToCurrentBlock(Move.create(INT_MOVE, op,
        translationHelper.getGPRegister(lazy, reg, size)));
  }

  /**
   * Write the given operand to this
   */
  void writeValue(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    OPT_RegisterOperand result = translationHelper.getGPRegister(lazy, reg,
        size);
    translationHelper.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
        result, op));
  }

  /**
   * Read the value as giving an address
   */
  void readEffectiveAddress(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    throw new Error("Trying to read the address of a register!");
  }
}

/**
 * Segment Registers
 */
final class X86_SegRegDecodedOperand extends X86_DecodedOperand {
  /**
   * The register in question
   */
  final int reg;

  /**
   * Constructor
   */
  X86_SegRegDecodedOperand(int reg) {
    this.reg = reg;
  }

  /**
   * Read the value into a register
   */
  void readToRegister(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    translationHelper.appendInstructionToCurrentBlock(Move.create(INT_MOVE, op,
        translationHelper.getSegRegister(lazy, reg)));
  }

  /**
   * Write the given operand to this
   */
  void writeValue(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    OPT_RegisterOperand result = translationHelper.getSegRegister(lazy, reg);
    translationHelper.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
        result, op));
  }

  /**
   * Read the value as giving an address
   */
  void readEffectiveAddress(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    throw new Error("Trying to read the address of a register!");
  }
}

/**
 * Memory
 */
final class X86_MemDecodedOperand extends X86_DecodedOperand {
  /**
   * The segment in question
   */
  final int segment;

  /**
   * The base register
   */
  final int base;

  /**
   * Scale used to scale the index register - 0 means there is no index
   */
  final int scale;

  /**
   * Index register that is scaled and added to the base
   */
  final int index;

  /**
   * Any additional displacement
   */
  final int displacement;

  /**
   * The size of address registers
   */
  final int addressSize;

  /**
   * The size of the operand
   */
  final int operandSize;
  
  /**
   * Constructor
   */
  X86_MemDecodedOperand(int segment, int base, int addressSize, int operandSize) {
    this.segment = segment;
    this.base = base;
    this.scale = 0;
    this.index = 0;
    this.displacement = 0;
    this.addressSize = addressSize;
    this.operandSize = operandSize;
  }

  /**
   * Constructor
   */
  X86_MemDecodedOperand(int segment, int base, int scale, int index,
      int displacement, int addressSize, int operandSize) {
    this.segment = segment;
    this.base = base;
    this.scale = scale;
    this.index = index;
    this.displacement = displacement;
    this.addressSize = addressSize;
    this.operandSize = operandSize;
  }

  /**
   * Read the value into a register
   */
  void readToRegister(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    OPT_RegisterOperand address = translationHelper.getTempInt(8);
    readEffectiveAddress(translationHelper, lazy, address);
    // Perform the load
    switch (operandSize) {
    case 32:
      translationHelper.ps.memory.translateLoad32(address.copyRO(), op);
      break;
    case 16:
      translationHelper.ps.memory.translateLoadSigned16(address.copyRO(), op);
      break;
    case 8:
      translationHelper.ps.memory.translateLoadSigned8(address.copyRO(), op);
      break;
    default:
      throw new Error("Unrecognize operand size " + operandSize);
    }
  }

  /**
   * Write the given operand to this
   */
  void writeValue(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand op) {
    OPT_RegisterOperand address = translationHelper.getTempInt(8);
    readEffectiveAddress(translationHelper, lazy, address);
    // Perform the store
    switch (operandSize) {
    case 32:
      translationHelper.ps.memory.translateStore32(address.copyRO(), op);
      break;
    case 16:
      translationHelper.ps.memory.translateStore16(address.copyRO(), op);
      break;
    case 8:
      translationHelper.ps.memory.translateStore8(address.copyRO(), op);
      break;
    default:
      throw new Error("Unrecognize operand size " + operandSize);
    }
  }

  /**
   * Read the value as giving an address
   */
  void readEffectiveAddress(X862IR translationHelper, X86_Laziness lazy,
      OPT_RegisterOperand address) {
    // Get the index and scale it
    if ((scale > 0) && (index != -1)) {
      translationHelper.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
          address, translationHelper.getGPRegister(lazy, index, 32)));
      if (scale > 1) {
        translationHelper.appendInstructionToCurrentBlock(Binary.create(
            INT_MUL, address.copyRO(), address.copyRO(),
            new OPT_IntConstantOperand(scale)));
      }
    } else {
      translationHelper.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
          address, new OPT_IntConstantOperand(0)));
    }
    // Add on the base
    if (base != -1) {
      translationHelper.appendInstructionToCurrentBlock(Binary.create(INT_ADD,
          address.copyRO(), address.copyRO(), translationHelper.getGPRegister(
              lazy, base, addressSize)));
    }
    // Add on the displacement
    if (displacement != 0) {
      translationHelper.appendInstructionToCurrentBlock(Binary.create(INT_ADD,
          address.copyRO(), address.copyRO(), new OPT_IntConstantOperand(
              displacement)));
    }
    // Add on any base address from a segment override
    translationHelper.addSegmentBaseAddress(segment, address);
  }
}
