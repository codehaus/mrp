package org.binarytranslator.generic.os.abi.linux.files;

import java.io.IOException;

/**
 * A base interface for all open files within linux.
 */
public interface OpenFile {
  Info getFileInfo() throws IOException;
  void close() throws IOException;
  
  
  public class Info {
    public int size;
    
    public Info(int size) {
      this.size = size;
    }
  }
}
