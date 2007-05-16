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
