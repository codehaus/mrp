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
 * IntAddressedByteSwapMemory:
 *
 * Memory is arrays of ints, endian conversion is performed on multibyte loads and stores.
 *
 * The string helloworld following by the int
 * of 0xcafebabe appear as:
 *
 * <pre>
 *               Byte Address
 * Int Address | 0 | 1 | 2 | 3 |
 *------------------------------
 * .........0c | ca| fe| ba| be|
 * .........08 |'l'|'d'| \n| \0|
 * .........04 |'o'|'W'|'o'|'r'|
 * .........00 |'H'|'e'|'l'|'l'|
 * </pre>
 */
final public class IntAddressedByteSwapMemory extends IntAddressedMemory {
  /**
	* Constructor - used when this is the instatiated class
	*/
  public IntAddressedByteSwapMemory() {
	 super("org/binarytranslator/IntAddressedByteSwapMemory");
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
