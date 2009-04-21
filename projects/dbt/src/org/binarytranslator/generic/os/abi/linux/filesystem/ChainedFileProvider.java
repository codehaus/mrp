package org.binarytranslator.generic.os.abi.linux.filesystem;

import org.binarytranslator.generic.os.abi.linux.files.OpenFile;

/**
 * This is a base class for file system that follow the chain of responsibility pattern.
 * In this pattern, all file systems are chained and if one file system cannot handle the request to
 * open a specific file, the request is forwarded to the next file system until either one system
 * can provide the file or the end of the chain is reached. 
 *
 */
public abstract class ChainedFileProvider implements FileProvider {
  
  protected final FileProvider nextProvider;
  
  public ChainedFileProvider(FileProvider nextProvider) {
    
    if (nextProvider == null)
      nextProvider = new NullFileSystem();
    
    this.nextProvider = nextProvider;
  }

  public OpenFile openFile(String file, FileMode mode) {
    OpenFile result = tryOpen(file, mode);
    
    if (result == null)
      result = nextProvider.openFile(file, mode);
    
    return result;
  }
  
  protected abstract OpenFile tryOpen(String file, FileMode mode);
}
