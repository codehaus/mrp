package org.binarytranslator.generic.os.abi.linux.files;

import java.io.IOException;

/**
 * An interface that identifies a file, which has been opened for reading.
 */
public interface ReadableFile extends OpenFile {
  
  /**
   * Reads up to <code>bytes.length</code> bytes from the file into the supplied array.
   * @param bytes
   *  A buffer that shall be filled from the file.
   * @return
   *  The number of bytes that were actually read.
   *  -1 If the end of the file has been reached.
   * @throws IOException
   */
  int read(byte[] buffer) throws IOException;
}
