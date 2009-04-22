/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.memory;

import org.jikesrvm.Configuration;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.*;
import org.jikesrvm.compilers.opt.ir.operand.*;

import java.io.RandomAccessFile;

import org.binarytranslator.generic.decoder.CodeTranslator;
import org.binarytranslator.generic.memory.IntAddressedLittleEndianMemory;

/**
 * IntAddressedPreSwappedMemory:
 * 
 * Memory is arrays of ints, with bytes backwards within the ints affecting a
 * byteswap.
 * 
 * The string helloworld following by the int of 0xcafebabe appear as:
 * 
 * <pre>
 *               Byte Address
 * Int Address | 0 | 1 | 2 | 3 |
 * -----------------------------
 * .........0c | be| ba| fe| ca|
 * .........08 | \0| \n|'d'|'l'|
 * .........04 |'r'|'o'|'W'|'o'|
 * .........00 |'l'|'l'|'e'|'H'|
 * </pre>
 */
public class IntAddressedPreSwappedMemory extends IntAddressedLittleEndianMemory {
  /**
   * Constructor
   */
  public IntAddressedPreSwappedMemory() {
    super(IntAddressedPreSwappedMemory.class);
  }

  /**
   * Constructor
   */
  protected IntAddressedPreSwappedMemory(Class classType) {
    super(classType);
  }

  /**
   * Read an int from RandomAccessFile ensuring that a byte swap is performed
   * 
   * @param file
   *          file to read from
   * @return native endian read int
   */
  protected int readInt(RandomAccessFile file) throws java.io.IOException {
    if (Configuration.BuildForPowerPC) {
      return file.readUnsignedByte() | (file.readUnsignedByte() << 8)
          | (file.readUnsignedByte() << 16) | (file.readByte() << 24);
    } else {
      return file.readInt(); // NB this will always read in big-endian format
    }
  }

  /**
   * Perform a byte load where the sign extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the sign extended result
   */
  public int loadSigned8(int addr) {
    return (loadWordAligned32(addr) << ((addr & 0x3) << 3)) >> 24;
    // switch(addr & 3) {
    // default: // lowest byte is at highest address
    // return loadWordAligned32(addr) >> 24;
    // case 1:
    // return (loadWordAligned32(addr) << 8) >> 24;
    // case 2:
    // return (loadWordAligned32(addr) << 16) >> 24;
    // case 3:
    // return (loadWordAligned32(addr) << 24) >> 24;
    // }
  }

  /**
   * Perform a byte load where the zero extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the zero extended result
   */
  public int loadUnsigned8(int addr) {
    return (loadWordAligned32(addr) >> ((3 - (addr & 3)) << 3)) & 0xFF;
    // switch(addr & 3) {
    // default:
    // return loadWordAligned32(addr) >>> 24;
    // case 1:
    // return (loadWordAligned32(addr) >> 16) & 0xFF;
    // case 2:
    // return (loadWordAligned32(addr) >> 8) & 0xFF;
    // case 3:
    // return loadWordAligned32(addr) & 0xFF;
    // }
  }

  /**
   * Perform a 16bit load where the sign extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the sign extended result
   */
  public int loadSigned16(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAligned32(addr) >> 16;
    case 1:
      return (loadWordAligned32(addr) << 8) >> 16;
    case 2:
      return (loadWordAligned32(addr) << 16) >> 16;
    case 3: // 2 loads to deal with spanning int problem
      return (loadWordAligned32(addr) & 0xFF)
          | ((loadWordAligned32(addr + 1) & 0xFF000000) >> 16);
    }
  }

  /**
   * Perform a 16bit load where the zero extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the zero extended result
   */
  public int loadUnsigned16(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAligned32(addr) >>> 16;
    case 1:
      return (loadWordAligned32(addr) >> 8) & 0xFFFF;
    case 2:
      return loadWordAligned32(addr) & 0xFFFF;
    case 3: // 2 loads to deal with spanning int problem
      return (loadWordAligned32(addr) & 0xFF)
          | ((loadWordAligned32(addr + 1) & 0xFF000000) >>> 16);
    }
  }

  /**
   * Perform a 32bit load
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  public int load32(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAligned32(addr);
    case 1: // 2 loads to deal with spanning int problem
      return (loadWordAligned32(addr + 3) >>> 24)
          | (loadWordAligned32(addr) << 8);
    case 2: // 2 loads to deal with spanning int problem
      return (loadWordAligned32(addr + 2) >>> 16)
          | (loadWordAligned32(addr) << 16);
    case 3: // 2 loads to deal with spanning int problem
      return (loadWordAligned32(addr + 1) >>> 8)
          | (loadWordAligned32(addr) << 24);
    }
  }

  /**
   * Perform a 8bit load from memory that must be executable
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  public int loadInstruction8(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAlignedInstruction32(addr) >>> 24;
    case 1:
      return (loadWordAlignedInstruction32(addr) >> 16) & 0xFF;
    case 2:
      return (loadWordAlignedInstruction32(addr) >> 8) & 0xFF;
    case 3:
      return loadWordAlignedInstruction32(addr) & 0xFF;
    }
  }

  /**
   * Perform a 32bit load from memory that must be executable
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  public int loadInstruction32(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAlignedInstruction32(addr);
    case 1: // 2 loads to deal with spanning int problem
      return (loadWordAlignedInstruction32(addr + 3) >>> 24)
          | (loadWordAlignedInstruction32(addr) << 8);
    case 2: // 2 loads to deal with spanning int problem
      return (loadWordAlignedInstruction32(addr + 2) >>> 16)
          | (loadWordAlignedInstruction32(addr) << 16);
    case 3: // 2 loads to deal with spanning int problem
      return (loadWordAlignedInstruction32(addr + 1) >>> 8)
          | (loadWordAlignedInstruction32(addr) << 24);
    }
  }

  /**
   * Perform a byte store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  public void store8(int addr, int value) {
    int shift = ((3 - (addr & 3)) << 3);
    value = (value & 0xff) << shift;
    int mask = ~(0xFF << shift);
    storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & mask) | value);
    // switch(addr & 3) {
    // default:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x00FFFFFF) |
    // (value << 24));
    // break;
    // case 1:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFF00FFFF) |
    // ((value & 0xFF) << 16));
    // break;
    // case 2:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFF00FF) |
    // ((value & 0xFF) << 8));
    // break;
    // case 3:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFFFF00) |
    // (value & 0xFF));
    // break;
    // }
  }

  /**
   * Perform a 16bit store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  public void store16(int addr, int value) {
    int byteAddr = addr & 3;
    if (byteAddr < 3) {
      int shift = ((2 - byteAddr) << 3);
      value = (value & 0xFFFF) << shift;
      int mask = ~(0xFFFF << shift);
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & mask) | value);
    } else {
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFFFFFF00)
          | (value & 0xFF));
      storeWordAligned32(addr + 1,
          (loadWordAligned32forWrite(addr + 1) & 0x00FFFFFF) | (value << 24));
    }
    // switch(addr & 3) {
    // default:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x0000FFFF) |
    // (value << 16));
    // break;
    // case 1:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFF0000FF) |
    // ((value & 0xFFFF) << 8));
    // break;
    // case 2:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFF0000) |
    // (value & 0xFFFF));
    // break;
    // case 3:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFFFF00) |
    // (value & 0xFF));
    // storeWordAligned32(addr+1,(loadWordAligned32forWrite(addr+1) &
    // 0x00FFFFFF) | (value << 24));
    // break;
    // }
  }

  /**
   * Perform a 32bit store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  public void store32(int addr, int value) {
    int byteAddr = addr & 3;
    if (byteAddr == 0) {
      storeWordAligned32(addr, value);
    } else {
      int shift1 = byteAddr << 3;
      int shift2 = (4 - byteAddr) << 8;
      int lowMask = 0xFFFFFFFF << shift2;
      int highMask = 0xFFFFFFFF >>> shift1;
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & lowMask)
          | (value >>> shift1));
      storeWordAligned32(addr + 3,
          (loadWordAligned32forWrite(addr + 3) & highMask) | (value << shift2));
    }
    // switch(addr & 3) {
    // case 0:
    // storeWordAligned32(addr,value);
    // break;
    // case 1:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFF000000) |
    // (value >>> 8));
    // storeWordAligned32(addr+3,(loadWordAligned32forWrite(addr+3) &
    // 0x00FFFFFF) | (value << 24));
    // break;
    // case 2:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFF0000) |
    // (value >>> 16));
    // storeWordAligned32(addr+2,(loadWordAligned32forWrite(addr+2) &
    // 0x0000FFFF) | (value << 16));
    // break;
    // case 3:
    // storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFFFF00) |
    // (value >>> 24));
    // storeWordAligned32(addr+1,(loadWordAligned32forWrite(addr+1) &
    // 0x000000FF) | (value << 8));
    // break;
    // }
  }

  /**
   * Register that references the read memory pages
   */
  Register readableMemoryReg;

  /**
   * Register that references the write memory pages
   */
  Register writableMemoryReg;

  /**
   * Register that references the page currently being accessed
   */
  Register pageReg;

  /**
   * Register holding PTE
   */
  Register vpnReg;

  /**
   * Register holding Offset
   */
  Register offsetReg;

  /**
   * Registers for holding temporaries
   */
  Register tempReg, tempReg2;

  /**
   * Generate memory prologue,... for the beignning of a trace. e.g. Loading the
   * page table into a register
   */
  public void initTranslate(CodeTranslator helper) {
    super.initTranslate(helper);
    vpnReg = helper.makeTemp(TypeReference.Int).register;
    offsetReg = helper.makeTemp(TypeReference.Int).register;
    pageReg = helper.makeTemp(TypeReference.IntArray).register;
    tempReg = helper.makeTemp(TypeReference.Int).register;
    tempReg2 = helper.makeTemp(TypeReference.Int).register;
    FieldReference memoryArrayRef = MemberReference.findOrCreate(
        memoryType, Atom.findOrCreateAsciiAtom("readableMemory"),
        Atom.findOrCreateAsciiAtom("[[I")).asFieldReference();
    RegisterOperand memoryArrayOp = helper
        .makeTemp(TypeReference.ObjectReferenceArray);
    helper.appendInstruction(GetField.create(GETFIELD,
        memoryArrayOp, new RegisterOperand(memory, memoryType),
        new AddressConstantOperand(memoryArrayRef.peekResolvedField()
            .getOffset()), new LocationOperand(memoryArrayRef),
        new TrueGuardOperand()));
    readableMemoryReg = memoryArrayOp.register;
    memoryArrayOp = helper.makeTemp(TypeReference.ObjectReferenceArray);
    memoryArrayRef = MemberReference.findOrCreate(memoryType,
        Atom.findOrCreateAsciiAtom("writableMemory"),
        Atom.findOrCreateAsciiAtom("[[I")).asFieldReference();
    helper.appendInstruction(GetField.create(GETFIELD,
        memoryArrayOp.copyRO(), new RegisterOperand(memory, memoryType),
        new AddressConstantOperand(memoryArrayRef.peekResolvedField()
            .getOffset()), new LocationOperand(memoryArrayRef),
        new TrueGuardOperand()));
    writableMemoryReg = memoryArrayOp.register;
  }

  /**
   * Generate the IR code for an aligned 32bit load - all other translate
   * methods rely on this
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  private void translateAlignedLoad32(RegisterOperand addr,
      RegisterOperand dest) {
    // Extract the memory page number from addr.
    RegisterOperand vpnRegOp = new RegisterOperand(vpnReg,
        TypeReference.Int);
    translator.appendInstruction(Binary.create(INT_USHR, vpnRegOp,
        addr, new IntConstantOperand(OFFSET_BITS)));

    // Extract the location of the address within the page.
    RegisterOperand offsetRegOp = new RegisterOperand(offsetReg,
        TypeReference.Int);
    translator.appendInstruction(Binary.create(INT_AND, offsetRegOp,
        addr.copyRO(), new IntConstantOperand(PAGE_SIZE - 1)));

    translator.appendInstruction(Binary.create(INT_USHR, offsetRegOp
        .copyRO(), offsetRegOp.copyRO(), new IntConstantOperand(2)));

    // Retrieve the int[] for the correct page into pageReg.
    RegisterOperand pageRegOp = new RegisterOperand(pageReg,
        TypeReference.IntArray);
    translator.appendInstruction(ALoad.create(REF_ALOAD, pageRegOp,
        new RegisterOperand(readableMemoryReg,
            TypeReference.ObjectReferenceArray), vpnRegOp.copyRO(),
        new LocationOperand(TypeReference.IntArray),
        new TrueGuardOperand()));

    // Copy to reg from the correct array element.
    translator.appendInstruction(ALoad.create(INT_ALOAD, dest,
        pageRegOp.copyRO(), offsetRegOp.copyRO(), new LocationOperand(
            TypeReference.Int), new TrueGuardOperand()));
  }

  /**
   * Generate the IR code for a byte load where the sign extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoadSigned8(RegisterOperand addr,
      RegisterOperand dest) {
    // Load as 32-bit then mask out what we need
    translateAlignedLoad32(addr, dest);
    // addr = (addr & 0x3) * 8
    translator.appendInstruction(Binary.create(INT_AND,
        addr.copyRO(), addr.copyRO(), new IntConstantOperand(3)));
    translator.appendInstruction(Binary.create(INT_SHL,
        addr.copyRO(), addr.copyRO(), new IntConstantOperand(3)));
    // rD <<= addr
    translator.appendInstruction(Binary.create(INT_SHL,
        dest.copyRO(), dest.copyRO(), addr.copyRO()));
    // rD >>>= 24
    translator.appendInstruction(Binary.create(INT_USHR, dest
        .copyRO(), dest.copyRO(), new IntConstantOperand(24)));
  }

  /**
   * Generate the IR code for a byte load where the zero extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoadUnsigned8(RegisterOperand addr,
      RegisterOperand dest) {
    // Load as 32-bit then mask out what we need
    translateAlignedLoad32(addr, dest);
    // addr = (3 - (addr & 0x3)) * 8
    translator.appendInstruction(Binary.create(INT_AND,
        addr.copyRO(), addr.copyRO(), new IntConstantOperand(3)));
    translator.appendInstruction(Binary.create(INT_SUB,
        addr.copyRO(), new IntConstantOperand(3), addr.copyRO()));
    translator.appendInstruction(Binary.create(INT_SHL,
        addr.copyRO(), addr.copyRO(), new IntConstantOperand(3)));
    // rD >>>= addr
    translator.appendInstruction(Binary.create(INT_USHR, dest
        .copyRO(), dest.copyRO(), addr.copyRO()));
    // rD &= 0xff
    translator.appendInstruction(Binary.create(INT_AND,
        dest.copyRO(), dest.copyRO(), new IntConstantOperand(0xff)));
  }

  /**
   * Generate the IR code for a 16bit load where the sign extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoadSigned16(RegisterOperand addr,
      RegisterOperand dest) {
    // The block after this load - NB could still need to plant an update for
    // this instruction in here
    BasicBlock nextBlock = translator.createBlockAfterCurrent();
    // Put call based version for (addr & 3 == 3) in aligned3
    BasicBlock aligned3 = translator.createBlockAfterCurrent();
    // Put all other cases in aligned
    BasicBlock aligned = translator.createBlockAfterCurrent();
    // Compute tempReg = addr & 3
    RegisterOperand tempRegOp = new RegisterOperand(tempReg,
        TypeReference.Int);
    translator.appendInstruction(Binary.create(INT_AND, tempRegOp,
        addr.copyRO(), new IntConstantOperand(0x3)));
    // Create if (addr & 3) == 3 goto aligned3
    translator.appendInstruction(IfCmp.create(INT_IFCMP, null,
        tempRegOp.copyRO(), new IntConstantOperand(0x3),
        ConditionOperand.EQUAL(), aligned3.makeJumpTarget(), translator
            .getConditionalBranchProfileOperand(false)));
    translator.getCurrentBlock().insertOut(aligned3);
    // Create aligned code
    translator.setCurrentBlock(aligned);
    translateAlignedLoad32(addr, dest);
    // tempReg = (addr & 0x3) * 8
    translator.appendInstruction(Binary.create(INT_SHL, tempRegOp
        .copyRO(), tempRegOp.copyRO(), new IntConstantOperand(3)));
    // rD <<= tempReg
    translator.appendInstruction(Binary.create(INT_SHL,
        dest.copyRO(), dest.copyRO(), tempRegOp.copyRO()));
    // rD >>= 16
    translator.appendInstruction(Binary.create(INT_SHR,
        dest.copyRO(), dest.copyRO(), new IntConstantOperand(16)));
    translator.appendInstruction(Goto.create(GOTO, nextBlock
        .makeJumpTarget()));
    aligned.deleteNormalOut();
    aligned.insertOut(nextBlock);
    // Create aligned3 code
    translator.setCurrentBlock(aligned3);
    translateCallBasedLoadSigned16(addr.copyRO(), dest.copyRO());
    // Move to empty block for rest of load instruction
    translator.setCurrentBlock(nextBlock);
  }

  /**
   * Generate the IR code for a 16bit load where the zero extended result fills
   * the register
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoadUnsigned16(RegisterOperand addr,
      RegisterOperand dest) {
    // The block after this load - NB could still need to plant an update for
    // this instruction in here
    BasicBlock nextBlock = translator.createBlockAfterCurrent();
    // Put call based version for (addr & 3 == 3) in aligned3
    BasicBlock aligned3 = translator.createBlockAfterCurrent();
    // Put all other cases in aligned
    BasicBlock aligned = translator.createBlockAfterCurrent();
    // Compute tempReg = addr & 3
    RegisterOperand tempRegOp = new RegisterOperand(tempReg,
        TypeReference.Int);
    translator.appendInstruction(Binary.create(INT_AND, tempRegOp,
        addr.copyRO(), new IntConstantOperand(0x3)));
    // Create if (addr & 3) == 3 goto aligned3
    translator.appendInstruction(IfCmp.create(INT_IFCMP, null,
        tempRegOp.copyRO(), new IntConstantOperand(0x3),
        ConditionOperand.EQUAL(), aligned3.makeJumpTarget(), translator
            .getConditionalBranchProfileOperand(false)));
    translator.getCurrentBlock().insertOut(aligned3);
    // Create aligned code
    translator.setCurrentBlock(aligned);
    translateAlignedLoad32(addr, dest);
    // tempReg = (2 - (addr & 0x3)) * 8
    translator.appendInstruction(Binary.create(INT_SUB, tempRegOp
        .copyRO(), new IntConstantOperand(2), tempRegOp.copyRO()));
    translator.appendInstruction(Binary.create(INT_SHL, tempRegOp
        .copyRO(), tempRegOp.copyRO(), new IntConstantOperand(3)));
    // rD >>>= tempReg
    translator.appendInstruction(Binary.create(INT_USHR, dest
        .copyRO(), dest.copyRO(), tempRegOp.copyRO()));
    // rD &= 0xffff
    translator.appendInstruction(Binary.create(INT_AND,
        dest.copyRO(), dest.copyRO(), new IntConstantOperand(0xffff)));
    translator.appendInstruction(Goto.create(GOTO, nextBlock
        .makeJumpTarget()));
    aligned.deleteNormalOut();
    aligned.insertOut(nextBlock);
    // Create aligned3 code
    translator.setCurrentBlock(aligned3);
    translateCallBasedLoadUnsigned16(addr.copyRO(), dest.copyRO());
    // Move to empty block for rest of load instruction
    translator.setCurrentBlock(nextBlock);
  }

  /**
   * Generate the IR code for a 32bit load
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  public void translateLoad32(RegisterOperand addr, RegisterOperand dest) {
    // The block after this load - NB could still need to plant an update for
    // this instruction in here
    BasicBlock nextBlock = translator.createBlockAfterCurrent();
    // Put call based version for (addr & 3 != 0) in aligned123
    BasicBlock aligned123 = translator.createBlockAfterCurrent();
    // Put case (addr & 3 == 0) in aligned
    BasicBlock aligned = translator.createBlockAfterCurrent();
    // Compute tempReg = addr & 3
    RegisterOperand tempRegOp = new RegisterOperand(tempReg,
        TypeReference.Int);
    translator.appendInstruction(Binary.create(INT_AND, tempRegOp,
        addr.copyRO(), new IntConstantOperand(0x3)));
    // Create if (addr & 3) == 3 goto aligned3
    translator.appendInstruction(IfCmp.create(INT_IFCMP, null,
        tempRegOp.copyRO(), new IntConstantOperand(0), ConditionOperand
            .NOT_EQUAL(), aligned123.makeJumpTarget(), translator
            .getConditionalBranchProfileOperand(false)));
    translator.getCurrentBlock().insertOut(aligned123);
    // Create aligned code
    translator.setCurrentBlock(aligned);
    translateAlignedLoad32(addr, dest);
    translator.appendInstruction(Goto.create(GOTO, nextBlock
        .makeJumpTarget()));
    aligned.deleteNormalOut();
    aligned.insertOut(nextBlock);
    // Create aligned3 code
    translator.setCurrentBlock(aligned123);
    translateCallBasedLoad32(addr.copyRO(), dest.copyRO());
    // Move to empty block for rest of load instruction
    translator.setCurrentBlock(nextBlock);
  }

  /**
   * Generate the IR code for an aligned 32bit load from writable memory - all
   * other translate methods rely on this
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  private void translateAlignedLoad32forWrite(RegisterOperand addr,
      RegisterOperand dest) {
    // Extract the memory page number from addr.
    RegisterOperand vpnRegOp = new RegisterOperand(vpnReg,
        TypeReference.Int);
    translator.appendInstruction(Binary.create(INT_USHR, vpnRegOp,
        addr, new IntConstantOperand(OFFSET_BITS)));

    // Extract the location of the address within the page.
    RegisterOperand offsetRegOp = new RegisterOperand(offsetReg,
        TypeReference.Int);
    translator.appendInstruction(Binary.create(INT_AND, offsetRegOp,
        addr.copyRO(), new IntConstantOperand(PAGE_SIZE - 1)));

    translator.appendInstruction(Binary.create(INT_USHR, offsetRegOp
        .copyRO(), offsetRegOp.copyRO(), new IntConstantOperand(2)));

    // Retrieve the int[] for the correct page into pageReg.
    RegisterOperand pageRegOp = new RegisterOperand(pageReg,
        TypeReference.IntArray);
    translator.appendInstruction(ALoad.create(REF_ALOAD, pageRegOp,
        new RegisterOperand(writableMemoryReg,
            TypeReference.ObjectReferenceArray), vpnRegOp.copyRO(),
        new LocationOperand(TypeReference.IntArray),
        new TrueGuardOperand()));

    // Copy to reg from the correct array element.
    translator.appendInstruction(ALoad.create(INT_ALOAD, dest,
        pageRegOp.copyRO(), offsetRegOp.copyRO(), new LocationOperand(
            TypeReference.Int), new TrueGuardOperand()));
  }

  /**
   * Generate the IR code for an aligned 32bit store - all other translate
   * methods rely on this
   * 
   * @param dest
   *          the register to hold the result
   * @param addr
   *          the address of the value to load
   */
  private void translateAlignedStore32(RegisterOperand addr,
      RegisterOperand src) {
    // Extract the memory page number from addr.
    RegisterOperand vpnRegOp = new RegisterOperand(vpnReg,
        TypeReference.Int);
    translator.appendInstruction(Binary.create(INT_USHR, vpnRegOp,
        addr, new IntConstantOperand(OFFSET_BITS)));

    // Extract the location of the address within the page.
    RegisterOperand offsetRegOp = new RegisterOperand(offsetReg,
        TypeReference.Int);
    translator.appendInstruction(Binary.create(INT_AND, offsetRegOp,
        addr.copyRO(), new IntConstantOperand(PAGE_SIZE - 1)));

    translator.appendInstruction(Binary.create(INT_USHR, offsetRegOp
        .copyRO(), offsetRegOp.copyRO(), new IntConstantOperand(2)));

    // Retrieve the int[] for the correct page into pageReg.
    RegisterOperand pageRegOp = new RegisterOperand(pageReg,
        TypeReference.IntArray);
    translator.appendInstruction(ALoad.create(REF_ALOAD, pageRegOp,
        new RegisterOperand(writableMemoryReg,
            TypeReference.ObjectReferenceArray), vpnRegOp.copyRO(),
        new LocationOperand(TypeReference.IntArray),
        new TrueGuardOperand()));

    // Copy to reg from the correct array element.
    translator.appendInstruction(ALoad.create(INT_ASTORE, src,
        pageRegOp.copyRO(), offsetRegOp.copyRO(), new LocationOperand(
            TypeReference.Int), new TrueGuardOperand()));
  }
  // /**
  // * Generate the IR code for a byte store
  // * @param src the register that holds the value to store
  // * @param addr the address of the value to store
  // */
  // public void translateStore8(RegisterOperand addr, RegisterOperand
  // src) {
  // // Load 32 bit value
  // RegisterOperand tempReg2Op = new RegisterOperand(tempReg2,
  // TypeReference.Int);
  // translateAlignedLoad32forWrite(addr,tempReg2Op);

  // // Compute tempReg = addr & 3
  // RegisterOperand tempRegOp = new RegisterOperand(tempReg,
  // TypeReference.Int);
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp,
  // addr.copyRO(),
  // new IntConstantOperand(0x3)));
  // // tempReg = 3 - (addr & 0x3)
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_SUB,
  // tempRegOp.copyRO(),
  // new IntConstantOperand(3),
  // tempRegOp.copyRO()));
  // // tempReg = (3 - (addr & 0x3)) * 8
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL,
  // tempRegOp.copyRO(),
  // tempRegOp.copyRO(),
  // new IntConstantOperand(3)));

  // // src = src & 0xFF
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, src.copyRO(),
  // src.copyRO(),
  // new IntConstantOperand(0xFF)));
  // // src = (src & 0xFF) << tempReg
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, src.copyRO(),
  // src.copyRO(),
  // tempRegOp.copyRO()));
  // // tempReg = 0xFF << tempReg
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL,
  // tempRegOp.copyRO(),
  // new IntConstantOperand(0xFF),
  // tempRegOp.copyRO()));
  // // tempReg = ~tempReg
  // helper.appendInstructionToCurrentBlock(Unary.create(INT_NOT,
  // tempRegOp.copyRO(),
  // tempRegOp.copyRO()));
  // // tempReg = tempReg2 & tempReg
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_AND,
  // tempRegOp.copyRO(),
  // tempReg2Op.copyRO(),
  // tempRegOp.copyRO()));
  // // tempReg = tempReg | src
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_OR,
  // tempRegOp.copyRO(),
  // tempRegOp.copyRO(),
  // src.copyRO()));
  // // Store - NB. pageReg and offsetReg should still be valid from aligned
  // load
  // RegisterOperand pageRegOp = new RegisterOperand(pageReg,
  // TypeReference.IntArray);
  // RegisterOperand offsetRegOp = new RegisterOperand(offsetReg,
  // TypeReference.Int);
  // helper.appendInstructionToCurrentBlock(ALoad.create(INT_ASTORE, src,
  // pageRegOp,
  // offsetRegOp,
  // new LocationOperand(TypeReference.Int),
  // new TrueGuardOperand()));
  // }
  // /**
  // * Generate the IR code for a 16bit store
  // * @param src the register that holds the value to store
  // * @param addr the address of the value to store
  // */
  // public void translateStore16(RegisterOperand addr, RegisterOperand
  // src) {
  // // The block after this load - NB could still need to plant an update for
  // this instruction in here
  // BasicBlock nextBlock = helper.createBlockAfterCurrent();
  // // Put call based version for (addr & 3 == 3) in aligned3
  // BasicBlock aligned3 = helper.createBlockAfterCurrent();
  // // Put all other cases in aligned
  // BasicBlock aligned = helper.createBlockAfterCurrent();
  // // Compute tempReg = addr & 3
  // RegisterOperand tempRegOp = new RegisterOperand(tempReg,
  // TypeReference.Int);
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp,
  // addr.copyRO(),
  // new IntConstantOperand(0x3)));
  // // Create if (addr & 3) == 3 goto aligned3
  // helper.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, null,
  // tempRegOp.copyRO(), new IntConstantOperand(0x3),
  // ConditionOperand.EQUAL(),
  // aligned3.makeJumpTarget(),
  // helper.getConditionalBranchProfileOperand(false))
  // );
  // helper.getCurrentBlock().insertOut(aligned3);
  // // Create aligned code
  // helper.setCurrentBlock(aligned);
  // RegisterOperand tempReg2Op = new RegisterOperand(tempReg2,
  // TypeReference.Int);
  // translateAlignedLoad32forWrite(addr,tempReg2Op);
  // // tempReg = 2 - (addr & 0x3)
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_SUB,
  // tempRegOp.copyRO(),
  // new IntConstantOperand(2),
  // tempRegOp.copyRO()));
  // // tempReg = (2 - (addr & 0x3)) * 8
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL,
  // tempRegOp.copyRO(),
  // tempRegOp.copyRO(),
  // new IntConstantOperand(3)));

  // // src = src & 0xFFFF
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, src.copyRO(),
  // src.copyRO(),
  // new IntConstantOperand(0xFFFF)));
  // // src = (src & 0xFFFF) << tempReg
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, src.copyRO(),
  // src.copyRO(),
  // tempRegOp.copyRO()));
  // // tempReg = 0xFFFF << tempReg
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL,
  // tempRegOp.copyRO(),
  // new IntConstantOperand(0xFFFF),
  // tempRegOp.copyRO()));
  // // tempReg = ~tempReg
  // helper.appendInstructionToCurrentBlock(Unary.create(INT_NOT,
  // tempRegOp.copyRO(),
  // tempRegOp.copyRO()));
  // // tempReg = tempReg2 & tempReg
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_AND,
  // tempRegOp.copyRO(),
  // tempReg2Op.copyRO(),
  // tempRegOp.copyRO()));
  // // tempReg = tempReg | src
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_OR,
  // tempRegOp.copyRO(),
  // tempRegOp.copyRO(),
  // src.copyRO()));
  // // Store - NB. pageReg and offsetReg should still be valid from aligned
  // load
  // RegisterOperand pageRegOp = new RegisterOperand(pageReg,
  // TypeReference.IntArray);
  // RegisterOperand offsetRegOp = new RegisterOperand(offsetReg,
  // TypeReference.Int);
  // helper.appendInstructionToCurrentBlock(ALoad.create(INT_ASTORE, src,
  // pageRegOp,
  // offsetRegOp,
  // new LocationOperand(TypeReference.Int),
  // new TrueGuardOperand()));
  // helper.appendInstructionToCurrentBlock(Goto.create(GOTO,
  // nextBlock.makeJumpTarget()));
  // aligned.deleteNormalOut();
  // aligned.insertOut(nextBlock);
  // // Create aligned3 code
  // helper.setCurrentBlock(aligned3);
  // translateCallBasedStore16(addr.copyRO(),src.copyRO());
  // // Move to empty block for rest of load instruction
  // helper.setCurrentBlock(nextBlock);
  // }
  // /**
  // * Generate the IR code for a 32bit store
  // * @param src the register that holds the value to store
  // * @param addr the address of the value to store
  // */
  // public void translateStore32(RegisterOperand addr, RegisterOperand
  // src) {
  // // The block after this load - NB could still need to plant an update for
  // this instruction in here
  // BasicBlock nextBlock = helper.createBlockAfterCurrent();
  // // Put call based version for (addr & 3 != 0) in aligned123
  // BasicBlock aligned123 = helper.createBlockAfterCurrent();
  // // Put case (addr & 3 == 0) in aligned
  // BasicBlock aligned = helper.createBlockAfterCurrent();
  // // Compute tempReg = addr & 3
  // RegisterOperand tempRegOp = new RegisterOperand(tempReg,
  // TypeReference.Int);
  // helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp,
  // addr.copyRO(),
  // new IntConstantOperand(0x3)));
  // // Create if (addr & 3) == 3 goto aligned3
  // helper.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, null,
  // tempRegOp.copyRO(), new IntConstantOperand(0),
  // ConditionOperand.NOT_EQUAL(),
  // aligned123.makeJumpTarget(),
  // helper.getConditionalBranchProfileOperand(false))
  // );
  // helper.getCurrentBlock().insertOut(aligned123);
  // // Create aligned code
  // helper.setCurrentBlock(aligned);
  // translateAlignedStore32(addr,src);
  // helper.appendInstructionToCurrentBlock(Goto.create(GOTO,
  // nextBlock.makeJumpTarget()));
  // aligned.deleteNormalOut();
  // aligned.insertOut(nextBlock);
  // // Create aligned3 code
  // helper.setCurrentBlock(aligned123);
  // translateCallBasedStore32(addr.copyRO(),src.copyRO());
  // // Move to empty block for rest of load instruction
  // helper.setCurrentBlock(nextBlock);
  // }
}
