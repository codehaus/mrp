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
package org.binarytranslator.generic.os.abi.linux.files;

import java.io.IOException;

/**
 * An interface that identifies a file, which has been opened for reading.
 */
public interface ReadableFile extends OpenFile {
  
  /**
   * Reads up to <code>bytes.length</code> bytes from the file into the supplied array.
   * @param bytes
   *  A buffer that shall be filled from the file.
   * @return
   *  The number of bytes that were actually read.
   *  -1 If the end of the file has been reached.
   * @throws IOException
   */
  int read(byte[] buffer) throws IOException;
}
