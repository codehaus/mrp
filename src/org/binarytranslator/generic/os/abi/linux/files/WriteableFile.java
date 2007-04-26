package org.binarytranslator.generic.os.abi.linux.files;

import java.io.IOException;

/**
 * An interface that identifies a file, which has been opened for writing.
 */
public interface WriteableFile extends OpenFile {
  
  /**
   * Writes the bytes contained in the buffer into the file.
   * 
   * @param bytes
   *  The bytes that are to be written into the file.
   * @throws IOException
   */
  void write(byte[] bytes) throws IOException;
}
