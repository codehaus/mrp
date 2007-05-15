package org.binarytranslator.generic.os.abi.linux.files;

import java.io.IOException;

/**
 * This is a file, for which the content is given by a constant string or byte array.
 */
public class FixedContentFile implements ReadableFile {
  
  /** The data that is actually returned to a reader. */
  protected final byte[] data;
  
  /** An integer offset into the {@link #data} array, that points at the next byte that is to be read. */
  protected int readPtr;
  
  public FixedContentFile(byte[] data) {
    readPtr = 0;
    this.data = data;
  }
  
  public FixedContentFile(String asciiData) {
    readPtr = 0;
    data = new byte[asciiData.length()];
    
    for (int i = 0; i < asciiData.length(); i++)
      data[i] = (byte)asciiData.charAt(i);
  }

  public int read(byte[] buffer) throws IOException {
    
    if (readPtr == data.length) {
      return -1;
    }
   
    for (int i = 0; i < buffer.length; i++) {
      
      if (readPtr == data.length) {
        return i;
      }
      
      buffer[i] = data[readPtr++];
    }
    
    return buffer.length;
  }

  public void close() throws IOException {
    //no-op
  }

}
