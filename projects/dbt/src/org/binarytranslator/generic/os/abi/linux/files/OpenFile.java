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
 * A base interface for all open files within linux.
 */
public interface OpenFile {
  Info getFileInfo() throws IOException;
  void close() throws IOException;
  
  
  public class Info {
    public int size;
    
    public Info(int size) {
      this.size = size;
    }
  }
}
