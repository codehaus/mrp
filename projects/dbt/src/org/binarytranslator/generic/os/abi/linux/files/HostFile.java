package org.binarytranslator.generic.os.abi.linux.files;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A linux file that represents an open file on the host file system.
 */
public class HostFile implements OpenFile {
  
  protected final RandomAccessFile file;
  
  public static HostFile forReading(RandomAccessFile file) {
    return new ReadableHostFile(file);
  }
  
  public static HostFile forWriting(RandomAccessFile file) {
    return new WriteableHostFile(file);
  }
  
  protected HostFile(RandomAccessFile file) {
    this.file = file;
  }

  public void close() throws IOException {
    file.close();
  }
  
  private static class ReadableHostFile extends HostFile implements ReadableFile  {

    protected ReadableHostFile(RandomAccessFile file) {
      super(file);
    }

    public int read(byte[] buffer) throws IOException {
      return file.read(buffer);
    }
  }
  
  private static class WriteableHostFile extends ReadableHostFile implements WriteableFile {

    protected WriteableHostFile(RandomAccessFile file) {
      super(file);
    }

    public void write(byte[] bytes) throws IOException {
      file.write(bytes);
    }
  }

  public Info getFileInfo() throws IOException {
    
    return new Info((int)file.length());
  }
}
