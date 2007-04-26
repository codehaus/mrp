package org.binarytranslator.generic.os.abi.linux.files;

import java.io.IOException;
import java.io.PrintStream;

/**
 * An open linux file that represents the console ouput streams (stdout and stderr).
 */
public class ConsoleOut implements WriteableFile {
  
  private final PrintStream stream;
  
  public ConsoleOut(PrintStream stream) {
    this.stream = stream;
  }

  public void write(byte[] bytes) throws IOException {
    stream.write(bytes);
  }

  public void close() throws IOException {
    //no-op
  }
}
