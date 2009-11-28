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
 * An open linux file that represents the console input.
 */
public class ConsoleIn implements ReadableFile {

  public int read(byte[] bytes) throws IOException {
    return System.in.read(bytes);
  }

  public void close() throws IOException {
    //no-op
  }

  public Info getFileInfo() {
    return null;
  }

}
