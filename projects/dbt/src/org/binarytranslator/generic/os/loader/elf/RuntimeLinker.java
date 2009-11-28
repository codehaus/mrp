/*
 *  This file is part of MRP (http://mrp.codehaus.org/).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.binarytranslator.generic.os.loader.elf;

import java.io.IOException;

import org.binarytranslator.arch.arm.os.process.loader.ARM_RuntimeLinker;
import org.binarytranslator.generic.os.process.ProcessSpace;

public abstract class RuntimeLinker {
  
  /**
   * This function is the main entry point into the dynamic linker and will steer the whole
   * linking process.
   */
  public abstract void link() throws IOException;
  
  /**
   * Creates a new ELF runtime linker for dynamically linking the file loaded by the
   * <code>loader</code>.
   * @param ps
   *  The process space that we're loading the file to.
   * @param loader
   *  The loader that is in need of a runtime linker. 
   * @return
   *  A runtime linker instance.
   */
  public static RuntimeLinker create(ProcessSpace ps, ELF_Loader loader) throws Error {
    switch (loader.getISA()) {
      case ARM:
        return new ARM_RuntimeLinker(ps, loader);
        
      default:
        throw new Error("Unable to create a runtime linker for the platform " + loader.getISA());
    }
  }
}
