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
package org.binarytranslator.generic.os.abi.linux.filesystem;

import org.binarytranslator.generic.os.abi.linux.files.OpenFile;

/** This class allows write-access to the /tmp file system from the guest. */
public class TempFileSystem extends HostFileSystem {
  
  /** A string that defines the location of the tmp file system on the guest. */
  protected final static String TMP_FILE_PATH = "/tmp/";

  public TempFileSystem(FileProvider nextProvider) {
    super(nextProvider);
  }
  
  @Override
  protected OpenFile tryOpen(String file, FileMode mode) {
    if (!file.startsWith(TMP_FILE_PATH))
      return null;
    
    return super.tryOpen(file, mode);
  }
}
