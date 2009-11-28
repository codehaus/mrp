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
import org.binarytranslator.generic.os.abi.linux.files.ReadableFile;

/** Represents the Unix /proc/ file system. */
public abstract class ProcFileSystem extends ChainedFileProvider {
  
  /** A string that defines the path of the virtual proc file system on the guest. */
  protected final static String PROC_DIRECTORY_PATH = "/proc/";
  
  public ProcFileSystem(FileProvider nextProvider) {
    super(nextProvider);
  }

  @Override
  protected OpenFile tryOpen(String file, FileMode mode) {
    if (!file.startsWith(PROC_DIRECTORY_PATH))
      return null;
    
    if (file.equals(PROC_DIRECTORY_PATH + "cpuinfo") && mode == FileMode.Read)
      return openCpuInfo();
    
    return null;
  }

  /**
   * Called when the application tries to open /proc/cpuinfo. 
   * Implement it by returning a {@link ReadableFile} that contains the information usually returned by /proc/cpuinfo.
   */
  protected abstract ReadableFile openCpuInfo();
}
