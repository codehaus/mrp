package org.binarytranslator.generic.os.abi.linux.filesystem;

import org.binarytranslator.generic.os.abi.linux.files.OpenFile;

/** 
 * The FileProvider interface defines the methods that any class shall implement, that serves as (part)
 * of the virtual file system that is simulated for the linux guest OS. */
public interface FileProvider {
  
  public enum FileMode {
    Read,
    Write,
    WriteCreate
  }
  
  /**
   * Opens the given file on the host.
   * @param file
   *  The path of the file that is to be opened.
   * @param mode
   *  The mode with which the file shall be opened.
   * @return
   *  A file object representing the file on success or null otherwise.
   */
  public OpenFile openFile(String file, FileMode mode);
}
