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

/**
 * ByteAddressedReversedMemory:
 * 
 * Memory is arrays of bytes, endian conversion is performed.
 * 
 * The string helo followed by the int of 0xcafebabe appear as:
 * 
 * <pre>
 * Byte Address|    
 * -----------------
 * .........ff |'H'|
 * .........fe |'e'|
 * .........fd |'l'|
 * .........fc |'o'|
 * .........fb | ca|
 * .........fa | fe|
 * .........f9 | ba|
 * .........f8 | be|
 * </pre>
 */
// public final class ByteAddressedReversedMemory extends CallBasedMemory {
// }
