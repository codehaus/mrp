/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.memory;

import java.io.RandomAccessFile;

/**
 * ByteAddressedReversedMemory:
 *
 * Memory is arrays of bytes, endian conversion is performed.
 *
 * The string helo followed by the int of 0xcafebabe appear as:
 *
 * <pre>
 * Byte Address|    
 *------------------
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
//public final class ByteAddressedReversedMemory extends CallBasedMemory {
//}
