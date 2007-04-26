package org.binarytranslator.generic.os.abi.linux.files;

import java.io.IOException;

/**
 * A base interface for all open files within linux.
 */
public interface OpenFile {
  void close() throws IOException;
}
