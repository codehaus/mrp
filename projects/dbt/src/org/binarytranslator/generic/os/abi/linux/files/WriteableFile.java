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
 * An interface that identifies a file, which has been opened for writing.
 */
public interface WriteableFile extends OpenFile {
  
  /**
   * Writes the bytes contained in the buffer into the file.
   * 
   * @param bytes
   *  The bytes that are to be written into the file.
   * @throws IOException
   */
  void write(byte[] bytes) throws IOException;
}
