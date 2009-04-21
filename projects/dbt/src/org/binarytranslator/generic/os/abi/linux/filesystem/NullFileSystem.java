package org.binarytranslator.generic.os.abi.linux.filesystem;

import org.binarytranslator.generic.os.abi.linux.files.OpenFile;

/**
 * A NULL filesystem object.
 *
 */
public class NullFileSystem implements FileProvider {

  public OpenFile openFile(String file, FileMode mode) {
    //no-nop
    return null;
  }
}
