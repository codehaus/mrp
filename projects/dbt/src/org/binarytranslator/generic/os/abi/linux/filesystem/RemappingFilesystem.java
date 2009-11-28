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

import java.security.InvalidParameterException;

import org.binarytranslator.generic.os.abi.linux.files.OpenFile;

/**
 * This file system component maps a path on the guest system to another path.
 *
 */
public class RemappingFilesystem extends ChainedFileProvider {
  private final FileProvider filesystem;
  private final String guestPath;
  private final String remappedPath;
  
  public RemappingFilesystem(String guestPath, String remappedPath, FileProvider filesystem) {
    this(guestPath, remappedPath, filesystem, null);
  }
  
  public RemappingFilesystem(String guestPath, String remappedPath, FileProvider filesystem, FileProvider nextFilesystem) {
    super(nextFilesystem);
    this.filesystem = filesystem;
    this.guestPath = guestPath;
    this.remappedPath = remappedPath;
    
    if (!guestPath.endsWith("/") && !guestPath.endsWith("\\")) {
      throw new InvalidParameterException("guestPath must clearly identify a path.");
    }
      
    if (!remappedPath.endsWith("/") && !remappedPath.endsWith("\\")) {
      throw new InvalidParameterException("remappedPath must clearly identify a path.");
    }
  }
  
  /**
   * Remap a file path from the guest system to another path. 
   * @param file
   *  The path and name of the file whose path is to be remapped.
   * @return
   *  The remapped version of the file path.
   */
  private String remapPath(String file) {
    if (!file.startsWith(guestPath))
      return file;
    
    file.substring(guestPath.length());
    return remappedPath + file;
  }

  @Override
  protected OpenFile tryOpen(String file, FileMode mode) {
    file = remapPath(file);
    return filesystem.openFile(file, mode);
  }
}
