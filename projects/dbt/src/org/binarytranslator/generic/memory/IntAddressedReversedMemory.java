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
package org.binarytranslator.generic.memory;

import org.binarytranslator.generic.memory.IntAddressedPreSwappedMemory;

/**
 * IntAddressedPreSwappedMemory:
 * 
 * Memory is arrays of ints, with bytes backwards within the ints affecting a
 * byteswap. Consecutive words are also backwards so that string operations
 * occur backwards.
 * 
 * The string helloworld following by the int of 0xcafebabe appear as:
 * 
 * <pre>
 *               Byte Address
 * Int Address | 0 | 1 | 2 | 3 |
 * -----------------------------
 * .........fc | be| ba| fe| ca|
 * .........f8 | \0| \n|'d'|'l'|
 * .........f4 |'r'|'o'|'W'|'o'|
 * .........f0 |'l'|'l'|'e'|'H'|
 * </pre>
 */
final public class IntAddressedReversedMemory extends
    IntAddressedPreSwappedMemory {
  /**
   * Constructor
   */
  public IntAddressedReversedMemory() {
    super(IntAddressedReversedMemory.class);
  }

  /**
   * Return the offset part of the address
   */
  protected int getOffset(int address) {
    return ((PAGE_SIZE - 1) - (address & (PAGE_SIZE - 1))) >>> 2;
  }
}
