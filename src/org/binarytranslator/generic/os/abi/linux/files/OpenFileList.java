package org.binarytranslator.generic.os.abi.linux.files;

import java.util.HashMap;

/**
 * Represents a list of open files that can be addressed by using a file descriptor.
 */
public class OpenFileList {
  
  private HashMap<Integer, OpenFile> files;
  private int nextFileHandle = 0;
  
  /** An exception that is thrown when a file descriptor is invalid. */
  public final static class InvalidFileDescriptor extends Exception {}
  
  /** An exception that is thrown when a file descriptor is used for read or writing, but the file does not support that. */
  public final static class InvalidFileMode extends Exception {}
  
  public OpenFileList() {
    files = new HashMap<Integer, OpenFile>(5);
  }
  
  /**
   * Add a file to the list of open files and return its file descriptor.
   * @param file
   *  The file to add to the list of open files.
   * @return
   *  The file descriptor that identifies this file.
   */
  public int add(OpenFile file) {
    int handle = nextFileHandle++;
    files.put(nextFileHandle, file);
    
    return handle;
  }
  
  /**
   * Adds a file to the list of open files and returns its file descriptor.
   * This version tries to distribute the file descriptor <code>preferredFd</code> to the file.
   * However, the caller has to compare the returned file descriptor with fd to check that the preferred
   * file descriptor was actually available.
   * 
   * @param file
   *  The file to add to the list of open files.
   * @param preferredFd
   *  The file descriptor that should be given to the file, if possible.
   * @return
   *  The file descriptor that identifies this file.
   */
  public int add(OpenFile file, int preferredFd) {
    if (files.get(preferredFd) != null) {
      preferredFd = nextFileHandle++;
    }
    
    files.put(preferredFd, file);
    return preferredFd;
  }
  
  /**
   * Look for a previously opened file (identified by its filedescriptor) and return it.
   * 
   * @param fd
   *  A file object that represents the open file.
   */
  public OpenFile get(int fd) throws InvalidFileDescriptor {
    OpenFile f = files.get(fd);
    
    if (f == null)
      throw new InvalidFileDescriptor();
    
    return f;
  }
  
  /**
   * Converts a file descriptor into a file for reading.
   * 
   * @param fd
   *  The file descriptor that should be retrieved.
   * @return
   *  A file object that represents the open file.
   */
  public ReadableFile getRead(int fd) 
    throws InvalidFileDescriptor, InvalidFileMode {
    OpenFile generalFile = files.get(fd);
    
    if (generalFile == null)
      throw new InvalidFileDescriptor();
    
    try {
      ReadableFile file = (ReadableFile)generalFile;
      return file;
    }
    catch (ClassCastException e) {
      throw new InvalidFileMode();
    }
  }
  
  /**
   * Converts a file descriptor into a file for writing.
   * 
   * @param fd
   *  The file descriptor that should be retrieved.
   * @return
   * A file object that represents the open file.
   */
  public WriteableFile getWrite(int fd) 
    throws InvalidFileDescriptor, InvalidFileMode {
    OpenFile generalFile = files.get(fd);
    
    if (generalFile == null)
      throw new InvalidFileDescriptor();
    
    try {
      WriteableFile file = (WriteableFile)generalFile;
      return file;
    }
    catch (ClassCastException e) {
      throw new InvalidFileMode();
    }
  }
  
  /**
   * Removes the file identified by the given file descriptor from the file list.
   * @param fd
   *  The file that is to be removed from the list.
   */
  public void remove(int fd) {
    files.remove(fd);
  }
}
