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
import java.io.PrintStream;

/**
 * An open linux file that represents the console ouput streams (stdout and stderr).
 */
public class ConsoleOut implements WriteableFile {
  
  private final PrintStream stream;
  
  public ConsoleOut(PrintStream stream) {
    this.stream = stream;
  }

  public void write(byte[] bytes) throws IOException {
    stream.write(bytes);
  }

  public void close() throws IOException {
    //no-op
  }

  public Info getFileInfo() {
    return null;
  }
}
