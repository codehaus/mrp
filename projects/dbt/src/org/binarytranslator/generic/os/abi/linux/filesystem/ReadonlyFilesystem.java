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

/**
 * A file system decorator, that prevents all write-accesses to this file system.
 */
public final class ReadonlyFilesystem extends ChainedFileProvider {
  
  private final FileProvider filesystem;
  
  public ReadonlyFilesystem(FileProvider filesystem) {
    this(filesystem, null);
  }
  
  public ReadonlyFilesystem(FileProvider filesystem, FileProvider nextFilesystem) {
    super(nextFilesystem);
    this.filesystem = filesystem;
  }

  @Override
  protected OpenFile tryOpen(String file, FileMode mode) {
    if (mode != FileMode.Read)
      return null;
    
    return filesystem.openFile(file, mode);
  }
}
