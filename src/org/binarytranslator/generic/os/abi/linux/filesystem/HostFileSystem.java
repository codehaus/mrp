package org.binarytranslator.generic.os.abi.linux.filesystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

import org.binarytranslator.generic.os.abi.linux.files.HostFile;
import org.binarytranslator.generic.os.abi.linux.files.OpenFile;

public class HostFileSystem extends ChainedFileProvider {

  
  public HostFileSystem(FileProvider nextProvider) {
    super(nextProvider);
  }

  @Override
  protected OpenFile tryOpen(String file, FileMode mode) {
    
    try {
      File f = new File(file);
      
      if (!f.isFile())
        return null;
      
      switch (mode) {
      case Read:
        if (!f.exists())
          return null;
        
        return HostFile.forReading(new RandomAccessFile(f, "r"));
        
      case Write:
        if (!f.exists())
          return null;
        
        return HostFile.forWriting(new RandomAccessFile(f, "rw"));
        
      case WriteCreate:
        return HostFile.forWriting(new RandomAccessFile(f, "rw"));
        
      default:
        throw new RuntimeException("Invalid file open mode: " + mode);
      }
      
    } catch (FileNotFoundException e) {
      return null;
    }
  }
}
