/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.memory;

/**
 * ByteAddressedByteSwapMemory:
 *
 * Memory is arrays of bytes, endian conversion is performed. This is
 * based on the ByteAddressedMemory but with the multi-byte methods
 * overloaded.
 *
 * The string helo followed by the int of 0xcafebabe appear as:
 *
 * <pre>
 * Byte Address|    
 *------------------
 * .........07 | be|
 * .........06 | ba|
 * .........05 | fe|
 * .........04 | ca|
 * .........03 |'o'|
 * .........02 |'l'|
 * .........01 |'e'|
 * .........00 |'H'|
 * </pre>
 */
final public class ByteAddressedByteSwapMemory extends ByteAddressedMemory {
  /**
	* Constructor - used when this is the instatiated class
	*/
  public ByteAddressedByteSwapMemory() {
      super(ByteAddressedByteSwapMemory.class);
  }
  /**
	* Perform a 16bit load where the sign extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the sign extended result
	*/
  public int loadSigned16(int addr) {
	 return loadUnsigned8(addr+1) | (loadSigned8(addr) << 8);
  }
  /**
	* Perform a 16bit load where the zero extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the zero extended result
	*/
  public int loadUnsigned16(int addr) {
	 return loadUnsigned8(addr+1) | (loadUnsigned8(addr) << 8);
  }
  /**
	* Perform a 32bit load
	* @param addr the address of the value to load
	* @return the result
	*/
  public int load32(int addr) {
	 return (loadSigned8(addr) << 24) | (loadUnsigned8(addr+1) << 16) | (loadUnsigned8(addr+2) << 8) | loadUnsigned8(addr+3);
  }
  /**
	* Perform a 32bit load from memory that must be executable
	* @param addr the address of the value to load
	* @return the result
	*/
  public int loadInstruction32(int addr) {
	 return (loadInstruction8(addr) << 24) | (loadInstruction8(addr+1) << 16) | (loadInstruction8(addr+2) << 8) | loadInstruction8(addr+3);
  }
  /**
	* Perform a 16bit store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store16(int addr, int value) {
	 store8(addr+1, value);
	 store8(addr, value >> 8);
  }
  /**
	* Perform a 32bit store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store32(int addr, int value) {
	 store8(addr+3, value);
	 store8(addr+2, value >> 8);
	 store8(addr+1, value >> 16);
	 store8(addr, value >> 24);
  }
}
