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
