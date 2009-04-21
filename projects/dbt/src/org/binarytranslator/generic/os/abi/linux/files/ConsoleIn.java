package org.binarytranslator.generic.os.abi.linux.files;

import java.io.IOException;

/**
 * An open linux file that represents the console input.
 */
public class ConsoleIn implements ReadableFile {

  public int read(byte[] bytes) throws IOException {
    return System.in.read(bytes);
  }

  public void close() throws IOException {
    //no-op
  }

  public Info getFileInfo() {
    return null;
  }

}
