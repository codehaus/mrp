/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.memory;

import com.ibm.jikesrvm.classloader.VM_Atom;
import com.ibm.jikesrvm.classloader.VM_FieldReference;
import com.ibm.jikesrvm.classloader.VM_MemberReference;
import com.ibm.jikesrvm.classloader.VM_TypeReference;
import com.ibm.jikesrvm.opt.ir.ALoad;
import com.ibm.jikesrvm.opt.ir.Binary;
import com.ibm.jikesrvm.opt.ir.GetField;
import com.ibm.jikesrvm.opt.ir.Goto;
import com.ibm.jikesrvm.opt.ir.IfCmp;
import com.ibm.jikesrvm.opt.ir.OPT_AddressConstantOperand;
import com.ibm.jikesrvm.opt.ir.OPT_BasicBlock;
import com.ibm.jikesrvm.opt.ir.OPT_ConditionOperand;
import com.ibm.jikesrvm.opt.ir.OPT_IntConstantOperand;
import com.ibm.jikesrvm.opt.ir.OPT_LocationOperand;
import com.ibm.jikesrvm.opt.ir.OPT_Register;
import com.ibm.jikesrvm.opt.ir.OPT_RegisterOperand;
import com.ibm.jikesrvm.opt.ir.OPT_TrueGuardOperand;
import com.ibm.jikesrvm.opt.ir.Unary;
import java.io.RandomAccessFile;
import org.binarytranslator.generic.memory.IntAddressedMemory;
import org.binarytranslator.vmInterface.TranslationHelper;

/**
 * IntAddressedPreSwappedMemory:
 *
 * Memory is arrays of ints, with bytes backwards within the ints
 * affecting a byteswap. 
 *
 * The string helloworld following by the int of 0xcafebabe appear as:
 *
 * <pre>
 *               Byte Address
 * Int Address | 0 | 1 | 2 | 3 |
 *------------------------------
 * .........0c | be| ba| fe| ca|
 * .........08 | \0| \n|'d'|'l'|
 * .........04 |'r'|'o'|'W'|'o'|
 * .........00 |'l'|'l'|'e'|'H'|
 * </pre>
 */
public class IntAddressedPreSwappedMemory extends IntAddressedMemory {
  /**
	* Constructor
	*/
  public IntAddressedPreSwappedMemory() {
	 super("org/binarytranslator/IntAddressedPreSwappedMemory");
  }
  /**
	* Constructor
	*/
  protected IntAddressedPreSwappedMemory(String className) {
	 super(className);
  }
  /**
	* Read an int from RandomAccessFile ensuring that a byte swap is performed
	* @param file file to read from
	* @return native endian read int
	*/
  protected int readInt(RandomAccessFile file) throws java.io.IOException {
	 //-#if RVM_FOR_POWERPC
	 return file.readUnsignedByte() | (file.readUnsignedByte() << 8) | (file.readUnsignedByte() << 16)| (file.readByte() << 24);
	 //-#else
	 return file.readInt(); // NB this will always read in big-endian format
	 //-#endif
  }
  /**
	* Perform a byte load where the sign extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the sign extended result
	*/
  public int loadSigned8(int addr) {
	 return (loadWordAligned32(addr) << ((addr & 0x3) << 3)) >> 24;
// 	 switch(addr & 3) {
// 	 default: // lowest byte is at highest address
// 		return loadWordAligned32(addr) >> 24;
// 	 case 1:
// 		return (loadWordAligned32(addr) << 8) >> 24;
// 	 case 2:
// 		return (loadWordAligned32(addr) << 16) >> 24;
// 	 case 3:
// 		return (loadWordAligned32(addr) << 24) >> 24;
// 	 }
  }
  /**
	* Perform a byte load where the zero extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the zero extended result
	*/
  public int loadUnsigned8(int addr) {
	 return (loadWordAligned32(addr) >> ((3-(addr & 3)) << 3)) & 0xFF;
// 	 switch(addr & 3)	{
// 	 default:
// 		return loadWordAligned32(addr) >>> 24;
// 	 case 1:
// 		return (loadWordAligned32(addr) >> 16) & 0xFF;
// 	 case 2:
// 		return (loadWordAligned32(addr) >> 8) & 0xFF;
// 	 case 3:
// 		return loadWordAligned32(addr) & 0xFF;
// 	 }
  }
  /**
	* Perform a 16bit load where the sign extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the sign extended result
	*/
  public int loadSigned16(int addr) {
	 switch(addr & 3) {
	 default:
		return loadWordAligned32(addr) >> 16;
	 case 1:
		return (loadWordAligned32(addr) << 8) >> 16;
	 case 2:
		return (loadWordAligned32(addr) << 16) >> 16;
	 case 3: // 2 loads to deal with spanning int problem
		return  (loadWordAligned32(addr) & 0xFF) | ((loadWordAligned32(addr+1) & 0xFF000000) >> 16);
	 }
  }
  /**
	* Perform a 16bit load where the zero extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the zero extended result
	*/
  public int loadUnsigned16(int addr) {
	 switch(addr & 3) {
	 default:
		return loadWordAligned32(addr) >>> 16;
	 case 1:
		return (loadWordAligned32(addr) >> 8) & 0xFFFF;
	 case 2:
		return loadWordAligned32(addr) & 0xFFFF;
	 case 3: // 2 loads to deal with spanning int problem
		return  (loadWordAligned32(addr) & 0xFF) | ((loadWordAligned32(addr+1) & 0xFF000000) >>> 16);
	 }
  }
  /**
	* Perform a 32bit load
	* @param addr the address of the value to load
	* @return the result
	*/
  public int load32(int addr) {
	 switch(addr & 3) {
	 default:
		return loadWordAligned32(addr);
	 case 1: // 2 loads to deal with spanning int problem
		return  (loadWordAligned32(addr+3) >>> 24) | (loadWordAligned32(addr) << 8);
	 case 2: // 2 loads to deal with spanning int problem
		return  (loadWordAligned32(addr+2) >>> 16) | (loadWordAligned32(addr) << 16);
	 case 3: // 2 loads to deal with spanning int problem
		return  (loadWordAligned32(addr+1) >>> 8) | (loadWordAligned32(addr) << 24);
	 }
  }
  /**
	* Perform a 8bit load from memory that must be executable
	* @param addr the address of the value to load
	* @return the result
	*/
  public int loadInstruction8(int addr) {
	 switch(addr & 3) {
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
	* @param addr the address of the value to load
	* @return the result
	*/
  public int loadInstruction32(int addr) {
	 switch(addr & 3) {
	 default:
		return loadWordAlignedInstruction32(addr);
	 case 1: // 2 loads to deal with spanning int problem
		return  (loadWordAlignedInstruction32(addr+3) >>> 24) | (loadWordAlignedInstruction32(addr) << 8);
	 case 2: // 2 loads to deal with spanning int problem
		return  (loadWordAlignedInstruction32(addr+2) >>> 16) | (loadWordAlignedInstruction32(addr) << 16);
	 case 3: // 2 loads to deal with spanning int problem
		return  (loadWordAlignedInstruction32(addr+1) >>> 8) | (loadWordAlignedInstruction32(addr) << 24);
	 }
  }
  /**
	* Perform a byte store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store8(int addr, int value) {
	 int shift = ((3 - (addr & 3)) << 3);
	 value = (value & 0xff) << shift;
	 int mask = ~(0xFF << shift);
	 storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & mask) | value);
// 	 switch(addr & 3) {
// 	 default:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x00FFFFFF) | (value << 24));
// 		break;
// 	 case 1:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFF00FFFF) | ((value & 0xFF) << 16));
// 		break;
// 	 case 2:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFF00FF) | ((value & 0xFF) << 8));
// 		break;
// 	 case 3:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFFFF00) | (value & 0xFF));
// 		break;
// 	 }
  }
  /**
	* Perform a 16bit store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store16(int addr, int value) {
	 int byteAddr = addr & 3;
	 if (byteAddr < 3) {
		int shift = ((2 - byteAddr) << 3);
		value = (value & 0xFFFF) << shift;
		int mask = ~(0xFFFF << shift);
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & mask) | value);
	 }
	 else {
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFFFF00) | (value & 0xFF));
		storeWordAligned32(addr+1,(loadWordAligned32forWrite(addr+1) & 0x00FFFFFF) | (value << 24));
	 }
// 	 switch(addr & 3) {
// 	 default:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x0000FFFF) | (value << 16));
// 		break;
// 	 case 1:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFF0000FF) | ((value & 0xFFFF) << 8));
// 		break;
// 	 case 2:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFF0000) | (value & 0xFFFF));
// 		break;
// 	 case 3:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFFFF00) | (value & 0xFF));
// 		storeWordAligned32(addr+1,(loadWordAligned32forWrite(addr+1) & 0x00FFFFFF) | (value << 24));
// 		break;
// 	 }
  }
  /**
	* Perform a 32bit store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store32(int addr, int value) {
	 int byteAddr = addr & 3;
	 if (byteAddr == 0) {
		storeWordAligned32(addr,value);
	 }
	 else {
		int shift1 = byteAddr << 3;
		int shift2 = (4-byteAddr) << 8;
		int lowMask  = 0xFFFFFFFF << shift2;
		int highMask = 0xFFFFFFFF >>> shift1;
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & lowMask) | (value >>> shift1));
		storeWordAligned32(addr+3,(loadWordAligned32forWrite(addr+3) & highMask) | (value << shift2));
	 }
// 	 switch(addr & 3) {
// 	 case 0:
// 		storeWordAligned32(addr,value);
// 		break;
// 	 case 1:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFF000000) | (value >>> 8));
// 		storeWordAligned32(addr+3,(loadWordAligned32forWrite(addr+3) & 0x00FFFFFF) | (value << 24));
// 		break;
// 	 case 2:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFF0000) | (value >>> 16));
// 		storeWordAligned32(addr+2,(loadWordAligned32forWrite(addr+2) & 0x0000FFFF) | (value << 16));
// 		break;
// 	 case 3:
// 		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFFFF00) | (value >>> 24));
// 		storeWordAligned32(addr+1,(loadWordAligned32forWrite(addr+1) & 0x000000FF) | (value << 8));
// 		break;
// 	 }
  }

  /**
   * Register that references the read memory pages
   */
  OPT_Register readableMemoryReg;
  /**
   * Register that references the write memory pages
   */
  OPT_Register writableMemoryReg;
  /**
   * Register that references the page currently being accessed
   */
  OPT_Register pageReg;
  /**
	* Register holding PTE
	*/
  OPT_Register vpnReg;
  /**
	* Register holding Offset
	*/
  OPT_Register offsetReg;
  /**
	* Registers for holding temporaries
	*/
  OPT_Register tempReg, tempReg2;
  /**
	* Generate memory prologue,... for the beignning of a
	* trace. e.g. Loading the page table into a register
	*/
  public void initTranslate(TranslationHelper helper) {
	 super.initTranslate(helper);
	 vpnReg    = helper.makeTemp(VM_TypeReference.Int).register;
	 offsetReg = helper.makeTemp(VM_TypeReference.Int).register;
	 pageReg   = helper.makeTemp(VM_TypeReference.IntArray).register;
	 tempReg   = helper.makeTemp(VM_TypeReference.Int).register;
	 tempReg2  = helper.makeTemp(VM_TypeReference.Int).register;
	 VM_FieldReference memoryArrayRef = VM_MemberReference.findOrCreate(memoryType, VM_Atom.findOrCreateAsciiAtom("readableMemory"),
																							  VM_Atom.findOrCreateAsciiAtom("[[I")
																							  ).asFieldReference();
	 OPT_RegisterOperand memoryArrayOp = helper.makeTemp(VM_TypeReference.ObjectReferenceArray);
	 helper.appendInstructionToCurrentBlock(GetField.create(GETFIELD, memoryArrayOp,
																			  new OPT_RegisterOperand(memory, memoryType),
																			  new OPT_AddressConstantOperand(memoryArrayRef.peekResolvedField().getOffset()),
																			  new OPT_LocationOperand(memoryArrayRef),
																			  new OPT_TrueGuardOperand()));
	 readableMemoryReg = memoryArrayOp.register;
	 memoryArrayOp = helper.makeTemp(VM_TypeReference.ObjectReferenceArray);
	 memoryArrayRef = VM_MemberReference.findOrCreate(memoryType, VM_Atom.findOrCreateAsciiAtom("writableMemory"),
																	  VM_Atom.findOrCreateAsciiAtom("[[I")
																	  ).asFieldReference();
	 helper.appendInstructionToCurrentBlock(GetField.create(GETFIELD, memoryArrayOp.copyRO(),
																			  new OPT_RegisterOperand(memory, memoryType),
																			  new OPT_AddressConstantOperand(memoryArrayRef.peekResolvedField().getOffset()),
																			  new OPT_LocationOperand(memoryArrayRef),
																			  new OPT_TrueGuardOperand()));
	 writableMemoryReg = memoryArrayOp.register;
  }
  /**
	* Generate the IR code for an aligned 32bit load - all other
	* translate methods rely on this
	* @param dest the register to hold the result
	* @param addr the address of the value to load
	*/
  private void translateAlignedLoad32(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
	 // Extract the memory page number from addr.
	 OPT_RegisterOperand vpnRegOp = new OPT_RegisterOperand(vpnReg, VM_TypeReference.Int);
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_USHR, vpnRegOp,
																			addr,
																			new OPT_IntConstantOperand(OFFSET_BITS)));
 
	 // Extract the location of the address within the page.
	 OPT_RegisterOperand offsetRegOp = new OPT_RegisterOperand(offsetReg, VM_TypeReference.Int);
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, offsetRegOp,
																			addr.copyRO(),
																			new OPT_IntConstantOperand(PAGE_SIZE - 1)));

	 helper.appendInstructionToCurrentBlock(Binary.create(INT_USHR, offsetRegOp.copyRO(),
																			offsetRegOp.copyRO(),
																			new OPT_IntConstantOperand(2)));
      
	 // Retrieve the int[] for the correct page into pageReg.
	 OPT_RegisterOperand pageRegOp = new OPT_RegisterOperand(pageReg, VM_TypeReference.IntArray);
	 helper.appendInstructionToCurrentBlock(ALoad.create(REF_ALOAD, pageRegOp,
																		  new OPT_RegisterOperand(readableMemoryReg, VM_TypeReference.ObjectReferenceArray),
																		  vpnRegOp.copyRO(),
																		  new OPT_LocationOperand(VM_TypeReference.IntArray),
																		  new OPT_TrueGuardOperand()));
               
	 // Copy to reg from the correct array element.
	 helper.appendInstructionToCurrentBlock(ALoad.create(INT_ALOAD, dest,
																		  pageRegOp.copyRO(), 
																		  offsetRegOp.copyRO(),
																		  new OPT_LocationOperand(VM_TypeReference.Int),
																		  new OPT_TrueGuardOperand()));
  }
  /**
	* Generate the IR code for a byte load where the sign extended
	* result fills the register
	* @param dest the register to hold the result
	* @param addr the address of the value to load
	*/
  public void translateLoadSigned8(OPT_RegisterOperand addr, OPT_RegisterOperand dest){
	 // Load as 32-bit then mask out what we need
	 translateAlignedLoad32(addr,dest);
	 // addr = (addr & 0x3) * 8
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, addr.copyRO(),
 																			addr.copyRO(),
 																			new OPT_IntConstantOperand(3)));
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, addr.copyRO(),
 																			addr.copyRO(),
 																			new OPT_IntConstantOperand(3)));
	 // rD <<= addr
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, dest.copyRO(),
 																			dest.copyRO(),
 																			addr.copyRO()));
	 // rD >>>= 24
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_USHR, dest.copyRO(),
 																			dest.copyRO(),
 																			new OPT_IntConstantOperand(24)));
  }

  /**
	* Generate the IR code for a byte load where the zero extended
	* result fills the register
	* @param dest the register to hold the result
	* @param addr the address of the value to load
	*/
  public void translateLoadUnsigned8(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
	 // Load as 32-bit then mask out what we need
	 translateAlignedLoad32(addr,dest);
	 // addr = (3 - (addr & 0x3)) * 8
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, addr.copyRO(),
 																			addr.copyRO(),
 																			new OPT_IntConstantOperand(3)));
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SUB, addr.copyRO(),
 																			new OPT_IntConstantOperand(3),
 																			addr.copyRO()));
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, addr.copyRO(),
 																			addr.copyRO(),
 																			new OPT_IntConstantOperand(3)));
	 // rD >>>= addr
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_USHR, dest.copyRO(),
 																			dest.copyRO(),
 																			addr.copyRO()));
	 // rD &= 0xff
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, dest.copyRO(),
 																			dest.copyRO(),
 																			new OPT_IntConstantOperand(0xff)));
  }
  /**
	* Generate the IR code for a 16bit load where the sign extended
	* result fills the register
	* @param dest the register to hold the result
	* @param addr the address of the value to load
	*/
  public void translateLoadSigned16(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
	 // The block after this load - NB could still need to plant an update for this instruction in here
	 OPT_BasicBlock nextBlock = helper.createBlockAfterCurrent();
	 // Put call based version for (addr & 3 == 3) in aligned3
    OPT_BasicBlock aligned3 = helper.createBlockAfterCurrent();
	 // Put all other cases in aligned
    OPT_BasicBlock aligned = helper.createBlockAfterCurrent();
	 // Compute tempReg = addr & 3
	 OPT_RegisterOperand tempRegOp = new OPT_RegisterOperand(tempReg, VM_TypeReference.Int);
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp,
																			addr.copyRO(),
																			new OPT_IntConstantOperand(0x3)));
	 // Create if (addr & 3) == 3 goto aligned3
    helper.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, null,
																		  tempRegOp.copyRO(), new OPT_IntConstantOperand(0x3), OPT_ConditionOperand.EQUAL(),
																		  aligned3.makeJumpTarget(),
																		  helper.getConditionalBranchProfileOperand(false))
														 );
	 helper.getCurrentBlock().insertOut(aligned3);
	 // Create aligned code
    helper.setCurrentBlock(aligned);
	 translateAlignedLoad32(addr,dest);
	 // tempReg  = (addr & 0x3) * 8
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempRegOp.copyRO(),
 																			tempRegOp.copyRO(),
 																			new OPT_IntConstantOperand(3)));
	 // rD <<= tempReg
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, dest.copyRO(),
 																			dest.copyRO(),
 																			tempRegOp.copyRO()));
	 // rD >>= 16
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHR, dest.copyRO(),
 																			dest.copyRO(),
 																			new OPT_IntConstantOperand(16)));
    helper.appendInstructionToCurrentBlock(Goto.create(GOTO, nextBlock.makeJumpTarget()));
	 aligned.deleteNormalOut();
	 aligned.insertOut(nextBlock);
	 // Create aligned3 code
    helper.setCurrentBlock(aligned3);
	 translateCallBasedLoadSigned16(addr.copyRO(),dest.copyRO());
	 // Move to empty block for rest of load instruction
    helper.setCurrentBlock(nextBlock);
  }
  /**
	* Generate the IR code for a 16bit load where the zero extended
	* result fills the register
	* @param dest the register to hold the result
	* @param addr the address of the value to load
	*/
  public void translateLoadUnsigned16(OPT_RegisterOperand addr, OPT_RegisterOperand dest){
	 // The block after this load - NB could still need to plant an update for this instruction in here
	 OPT_BasicBlock nextBlock = helper.createBlockAfterCurrent();
	 // Put call based version for (addr & 3 == 3) in aligned3
    OPT_BasicBlock aligned3 = helper.createBlockAfterCurrent();
	 // Put all other cases in aligned
    OPT_BasicBlock aligned = helper.createBlockAfterCurrent();
	 // Compute tempReg = addr & 3
	 OPT_RegisterOperand tempRegOp = new OPT_RegisterOperand(tempReg, VM_TypeReference.Int);
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp,
																			addr.copyRO(),
																			new OPT_IntConstantOperand(0x3)));
	 // Create if (addr & 3) == 3 goto aligned3
    helper.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, null,
																		  tempRegOp.copyRO(), new OPT_IntConstantOperand(0x3), OPT_ConditionOperand.EQUAL(),
																		  aligned3.makeJumpTarget(),
																		  helper.getConditionalBranchProfileOperand(false))
														 );
	 helper.getCurrentBlock().insertOut(aligned3);
	 // Create aligned code
    helper.setCurrentBlock(aligned);
	 translateAlignedLoad32(addr,dest);
	 // tempReg  = (2 - (addr & 0x3)) * 8
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SUB, tempRegOp.copyRO(),
 																			new OPT_IntConstantOperand(2),
 																			tempRegOp.copyRO()));
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempRegOp.copyRO(),
 																			tempRegOp.copyRO(),
 																			new OPT_IntConstantOperand(3)));
	 // rD >>>= tempReg
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_USHR, dest.copyRO(),
 																			dest.copyRO(),
 																			tempRegOp.copyRO()));
	 // rD &= 0xffff
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, dest.copyRO(),
 																			dest.copyRO(),
 																			new OPT_IntConstantOperand(0xffff)));
    helper.appendInstructionToCurrentBlock(Goto.create(GOTO, nextBlock.makeJumpTarget()));
	 aligned.deleteNormalOut();
	 aligned.insertOut(nextBlock);
	 // Create aligned3 code
    helper.setCurrentBlock(aligned3);
	 translateCallBasedLoadUnsigned16(addr.copyRO(),dest.copyRO());
	 // Move to empty block for rest of load instruction
    helper.setCurrentBlock(nextBlock);
  }
  /**
	* Generate the IR code for a 32bit load
	* @param dest the register to hold the result
	* @param addr the address of the value to load
	*/
  public void translateLoad32(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
	 // The block after this load - NB could still need to plant an update for this instruction in here
	 OPT_BasicBlock nextBlock = helper.createBlockAfterCurrent();
	 // Put call based version for (addr & 3 != 0) in aligned123
    OPT_BasicBlock aligned123 = helper.createBlockAfterCurrent();
	 // Put case (addr & 3 == 0) in aligned
    OPT_BasicBlock aligned = helper.createBlockAfterCurrent();
	 // Compute tempReg = addr & 3
	 OPT_RegisterOperand tempRegOp = new OPT_RegisterOperand(tempReg, VM_TypeReference.Int);
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp,
																			addr.copyRO(),
																			new OPT_IntConstantOperand(0x3)));
	 // Create if (addr & 3) == 3 goto aligned3
    helper.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, null,
																		  tempRegOp.copyRO(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.NOT_EQUAL(),
																		  aligned123.makeJumpTarget(),
																		  helper.getConditionalBranchProfileOperand(false))
														 );
	 helper.getCurrentBlock().insertOut(aligned123);
	 // Create aligned code
    helper.setCurrentBlock(aligned);
	 translateAlignedLoad32(addr,dest);
    helper.appendInstructionToCurrentBlock(Goto.create(GOTO, nextBlock.makeJumpTarget()));
	 aligned.deleteNormalOut();
	 aligned.insertOut(nextBlock);
	 // Create aligned3 code
    helper.setCurrentBlock(aligned123);
	 translateCallBasedLoad32(addr.copyRO(),dest.copyRO());
	 // Move to empty block for rest of load instruction
    helper.setCurrentBlock(nextBlock);
  }
  /**
	* Generate the IR code for an aligned 32bit load from writable
	* memory - all other translate methods rely on this
	* @param dest the register to hold the result
	* @param addr the address of the value to load
	*/
  private void translateAlignedLoad32forWrite(OPT_RegisterOperand addr, OPT_RegisterOperand dest) {
	 // Extract the memory page number from addr.
	 OPT_RegisterOperand vpnRegOp = new OPT_RegisterOperand(vpnReg, VM_TypeReference.Int);
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_USHR, vpnRegOp,
																			addr,
																			new OPT_IntConstantOperand(OFFSET_BITS)));
 
	 // Extract the location of the address within the page.
	 OPT_RegisterOperand offsetRegOp = new OPT_RegisterOperand(offsetReg, VM_TypeReference.Int);
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, offsetRegOp,
																			addr.copyRO(),
																			new OPT_IntConstantOperand(PAGE_SIZE - 1)));

	 helper.appendInstructionToCurrentBlock(Binary.create(INT_USHR, offsetRegOp.copyRO(),
																			offsetRegOp.copyRO(),
																			new OPT_IntConstantOperand(2)));
      
	 // Retrieve the int[] for the correct page into pageReg.
	 OPT_RegisterOperand pageRegOp = new OPT_RegisterOperand(pageReg, VM_TypeReference.IntArray);
	 helper.appendInstructionToCurrentBlock(ALoad.create(REF_ALOAD, pageRegOp,
																		  new OPT_RegisterOperand(writableMemoryReg, VM_TypeReference.ObjectReferenceArray),
																		  vpnRegOp.copyRO(),
																		  new OPT_LocationOperand(VM_TypeReference.IntArray),
																		  new OPT_TrueGuardOperand()));
               
	 // Copy to reg from the correct array element.
	 helper.appendInstructionToCurrentBlock(ALoad.create(INT_ALOAD, dest,
																		  pageRegOp.copyRO(), 
																		  offsetRegOp.copyRO(),
																		  new OPT_LocationOperand(VM_TypeReference.Int),
																		  new OPT_TrueGuardOperand()));
  }
  /**
	* Generate the IR code for an aligned 32bit store - all other
	* translate methods rely on this
	* @param dest the register to hold the result
	* @param addr the address of the value to load
	*/
  private void translateAlignedStore32(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
	 // Extract the memory page number from addr.
	 OPT_RegisterOperand vpnRegOp = new OPT_RegisterOperand(vpnReg, VM_TypeReference.Int);
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_USHR, vpnRegOp,
																			addr,
																			new OPT_IntConstantOperand(OFFSET_BITS)));
 
	 // Extract the location of the address within the page.
	 OPT_RegisterOperand offsetRegOp = new OPT_RegisterOperand(offsetReg, VM_TypeReference.Int);
	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, offsetRegOp,
																			addr.copyRO(),
																			new OPT_IntConstantOperand(PAGE_SIZE - 1)));

	 helper.appendInstructionToCurrentBlock(Binary.create(INT_USHR, offsetRegOp.copyRO(),
																			offsetRegOp.copyRO(),
																			new OPT_IntConstantOperand(2)));
      
	 // Retrieve the int[] for the correct page into pageReg.
	 OPT_RegisterOperand pageRegOp = new OPT_RegisterOperand(pageReg, VM_TypeReference.IntArray);
	 helper.appendInstructionToCurrentBlock(ALoad.create(REF_ALOAD, pageRegOp,
																		  new OPT_RegisterOperand(writableMemoryReg, VM_TypeReference.ObjectReferenceArray),
																		  vpnRegOp.copyRO(),
																		  new OPT_LocationOperand(VM_TypeReference.IntArray),
																		  new OPT_TrueGuardOperand()));
               
	 // Copy to reg from the correct array element.
	 helper.appendInstructionToCurrentBlock(ALoad.create(INT_ASTORE, src,
																		  pageRegOp.copyRO(), 
																		  offsetRegOp.copyRO(),
																		  new OPT_LocationOperand(VM_TypeReference.Int),
																		  new OPT_TrueGuardOperand()));
  }
//   /**
// 	* Generate the IR code for a byte store
// 	* @param src the register that holds the value to store
// 	* @param addr the address of the value to store
// 	*/
//   public  void translateStore8(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
// 	 // Load 32 bit value
// 	 OPT_RegisterOperand tempReg2Op = new OPT_RegisterOperand(tempReg2, VM_TypeReference.Int);
// 	 translateAlignedLoad32forWrite(addr,tempReg2Op);

// 	 // Compute tempReg = addr & 3
// 	 OPT_RegisterOperand tempRegOp = new OPT_RegisterOperand(tempReg, VM_TypeReference.Int);
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp,
// 																			addr.copyRO(),
// 																			new OPT_IntConstantOperand(0x3)));
// 	 // tempReg  = 3 - (addr & 0x3)
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SUB, tempRegOp.copyRO(),
// 																			new OPT_IntConstantOperand(3),
//  																			tempRegOp.copyRO()));
// 	 // tempReg  = (3 - (addr & 0x3)) * 8
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempRegOp.copyRO(),
//  																			tempRegOp.copyRO(),
//  																			new OPT_IntConstantOperand(3)));

// 	 // src = src & 0xFF
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, src.copyRO(),
//  																			src.copyRO(),
//  																			new OPT_IntConstantOperand(0xFF)));
// 	 // src = (src & 0xFF) << tempReg
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, src.copyRO(),
//  																			src.copyRO(),
//  																			tempRegOp.copyRO()));	 
// 	 // tempReg = 0xFF << tempReg
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempRegOp.copyRO(),
//  																			new OPT_IntConstantOperand(0xFF),
//  																			tempRegOp.copyRO()));
// 	 // tempReg = ~tempReg
// 	 helper.appendInstructionToCurrentBlock(Unary.create(INT_NOT, tempRegOp.copyRO(),
// 																		  tempRegOp.copyRO()));
// 	 // tempReg = tempReg2  & tempReg
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp.copyRO(),
//  																			tempReg2Op.copyRO(),
//  																			tempRegOp.copyRO()));
// 	 // tempReg = tempReg | src
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_OR, tempRegOp.copyRO(),
//  																			tempRegOp.copyRO(),
//  																			src.copyRO()));
// 	 // Store - NB. pageReg and offsetReg should still be valid from aligned load
// 	 OPT_RegisterOperand pageRegOp = new OPT_RegisterOperand(pageReg, VM_TypeReference.IntArray);
// 	 OPT_RegisterOperand offsetRegOp = new OPT_RegisterOperand(offsetReg, VM_TypeReference.Int);
// 	 helper.appendInstructionToCurrentBlock(ALoad.create(INT_ASTORE, src,
// 																		  pageRegOp, 
// 																		  offsetRegOp,
// 																		  new OPT_LocationOperand(VM_TypeReference.Int),
// 																		  new OPT_TrueGuardOperand()));
//   }
//   /**
// 	* Generate the IR code for a 16bit store
// 	* @param src the register that holds the value to store
// 	* @param addr the address of the value to store
// 	*/
//   public void translateStore16(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
// 	 // The block after this load - NB could still need to plant an update for this instruction in here
// 	 OPT_BasicBlock nextBlock = helper.createBlockAfterCurrent();
// 	 // Put call based version for (addr & 3 == 3) in aligned3
//     OPT_BasicBlock aligned3 = helper.createBlockAfterCurrent();
// 	 // Put all other cases in aligned
//     OPT_BasicBlock aligned = helper.createBlockAfterCurrent();
// 	 // Compute tempReg = addr & 3
// 	 OPT_RegisterOperand tempRegOp = new OPT_RegisterOperand(tempReg, VM_TypeReference.Int);
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp,
// 																			addr.copyRO(),
// 																			new OPT_IntConstantOperand(0x3)));
// 	 // Create if (addr & 3) == 3 goto aligned3
//     helper.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, null,
// 																		  tempRegOp.copyRO(), new OPT_IntConstantOperand(0x3), OPT_ConditionOperand.EQUAL(),
// 																		  aligned3.makeJumpTarget(),
// 																		  helper.getConditionalBranchProfileOperand(false))
// 														 );
// 	 helper.getCurrentBlock().insertOut(aligned3);
// 	 // Create aligned code
//     helper.setCurrentBlock(aligned);
// 	 OPT_RegisterOperand tempReg2Op = new OPT_RegisterOperand(tempReg2, VM_TypeReference.Int);
// 	 translateAlignedLoad32forWrite(addr,tempReg2Op);
// 	 // tempReg  = 2 - (addr & 0x3)
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SUB, tempRegOp.copyRO(),
// 																			new OPT_IntConstantOperand(2),
//  																			tempRegOp.copyRO()));
// 	 // tempReg  = (2 - (addr & 0x3)) * 8
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempRegOp.copyRO(),
//  																			tempRegOp.copyRO(),
//  																			new OPT_IntConstantOperand(3)));

// 	 // src = src & 0xFFFF
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, src.copyRO(),
//  																			src.copyRO(),
//  																			new OPT_IntConstantOperand(0xFFFF)));
// 	 // src = (src & 0xFFFF) << tempReg
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, src.copyRO(),
//  																			src.copyRO(),
//  																			tempRegOp.copyRO()));	 
// 	 // tempReg = 0xFFFF << tempReg
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempRegOp.copyRO(),
//  																			new OPT_IntConstantOperand(0xFFFF),
//  																			tempRegOp.copyRO()));
// 	 // tempReg = ~tempReg
// 	 helper.appendInstructionToCurrentBlock(Unary.create(INT_NOT, tempRegOp.copyRO(),
// 																		  tempRegOp.copyRO()));
// 	 // tempReg = tempReg2  & tempReg
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp.copyRO(),
//  																			tempReg2Op.copyRO(),
//  																			tempRegOp.copyRO()));
// 	 // tempReg = tempReg | src
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_OR, tempRegOp.copyRO(),
//  																			tempRegOp.copyRO(),
//  																			src.copyRO()));
// 	 // Store - NB. pageReg and offsetReg should still be valid from aligned load
// 	 OPT_RegisterOperand pageRegOp = new OPT_RegisterOperand(pageReg, VM_TypeReference.IntArray);
// 	 OPT_RegisterOperand offsetRegOp = new OPT_RegisterOperand(offsetReg, VM_TypeReference.Int);
// 	 helper.appendInstructionToCurrentBlock(ALoad.create(INT_ASTORE, src,
// 																		  pageRegOp, 
// 																		  offsetRegOp,
// 																		  new OPT_LocationOperand(VM_TypeReference.Int),
// 																		  new OPT_TrueGuardOperand()));
//     helper.appendInstructionToCurrentBlock(Goto.create(GOTO, nextBlock.makeJumpTarget()));
// 	 aligned.deleteNormalOut();
// 	 aligned.insertOut(nextBlock);
// 	 // Create aligned3 code
//     helper.setCurrentBlock(aligned3);
// 	 translateCallBasedStore16(addr.copyRO(),src.copyRO());
// 	 // Move to empty block for rest of load instruction
//     helper.setCurrentBlock(nextBlock);
//   }
//   /**
// 	* Generate the IR code for a 32bit store
// 	* @param src the register that holds the value to store
// 	* @param addr the address of the value to store
// 	*/
//   public void translateStore32(OPT_RegisterOperand addr, OPT_RegisterOperand src) {
// 	 // The block after this load - NB could still need to plant an update for this instruction in here
// 	 OPT_BasicBlock nextBlock = helper.createBlockAfterCurrent();
// 	 // Put call based version for (addr & 3 != 0) in aligned123
//     OPT_BasicBlock aligned123 = helper.createBlockAfterCurrent();
// 	 // Put case (addr & 3 == 0) in aligned
//     OPT_BasicBlock aligned = helper.createBlockAfterCurrent();
// 	 // Compute tempReg = addr & 3
// 	 OPT_RegisterOperand tempRegOp = new OPT_RegisterOperand(tempReg, VM_TypeReference.Int);
// 	 helper.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempRegOp,
// 																			addr.copyRO(),
// 																			new OPT_IntConstantOperand(0x3)));
// 	 // Create if (addr & 3) == 3 goto aligned3
//     helper.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, null,
// 																		  tempRegOp.copyRO(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.NOT_EQUAL(),
// 																		  aligned123.makeJumpTarget(),
// 																		  helper.getConditionalBranchProfileOperand(false))
// 														 );
// 	 helper.getCurrentBlock().insertOut(aligned123);
// 	 // Create aligned code
//     helper.setCurrentBlock(aligned);
// 	 translateAlignedStore32(addr,src);
//     helper.appendInstructionToCurrentBlock(Goto.create(GOTO, nextBlock.makeJumpTarget()));
// 	 aligned.deleteNormalOut();
// 	 aligned.insertOut(nextBlock);
// 	 // Create aligned3 code
//     helper.setCurrentBlock(aligned123);
// 	 translateCallBasedStore32(addr.copyRO(),src.copyRO());
// 	 // Move to empty block for rest of load instruction
//     helper.setCurrentBlock(nextBlock);
//   }
}
