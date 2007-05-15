package org.binarytranslator.generic.os.abi.linux.filesystem;

import org.binarytranslator.generic.os.abi.linux.files.OpenFile;

/**
 * A file system decorator, that prevents all write-accesses to this file system.
 */
public final class ReadonlyFilesystem implements FileProvider {
  
  private final FileProvider filesystem;
  
  public ReadonlyFilesystem(FileProvider filesystem) {
    this.filesystem = filesystem;
  }

  public OpenFile openFile(String file, FileMode mode) {
    if (mode != FileMode.Read)
      return null;
    
    return filesystem.openFile(file, mode);
  }
}
