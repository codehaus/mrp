/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.loader;

import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.os.loader.elf.ELF_Loader;
import org.binarytranslator.generic.os.loader.image.ImageLoader;
import org.binarytranslator.DBT_Options;
import java.io.*;

/**
 * The Loader creates a process for a binary and then loads the binary
 * into it. This class is a superclass for different executable file
 * formats. The appropriate sub-class loader is chosen to actually
 * load the binary.
 */
public abstract class Loader {
  /*
   * Utility functions
   */

  /**
   * Debug information
   * @param s string of debug information
   */
  private static void report(String s){
    if (DBT_Options.debugLoader) {
      System.out.print("Loader:");
      System.out.println(s);
    }
  }

  /*
   * Abstract methods defined by the relavent binary loader
   */

  /**
   * Create a process space, load the binary into it and initialise
   * the stack, etc.
   * @param args command line arguments
   * @return the process space to start executing
   */
  abstract public ProcessSpace readBinary(String[] args) throws IOException;

  /**
   * Return the application binary interface (ABI) supported by this
   * file
   */
  abstract public String getABIString();
  /**
   * Return the architecture (ISA) supported by this file
   */
  abstract public String getArchitectureString();
  /**
   * Is the binary for the X86 ISA?
   */
  abstract public boolean isX86_ISA();

  /**
   * Is the binary for the Power PC ISA?
   */
  abstract public boolean isPPC_ISA();
  
  /**
   * Is the binary for the ARM ISA?
   */
  abstract public boolean isARM_ISA();

  /**
   * Does this file support the SysV ABI?
   */
  abstract public boolean isSysV_ABI();

  /**
   * Does this file support the Linux ABI?
   */
  abstract public boolean isLinuxABI();
  
  /**
   * Does this file support the ARM ABI?
   */
  abstract public boolean isARM_ABI();

  /*
   * Static methods
   */

  /**
   * Open and read the start of the file to determine the appropriate
   * file loader to use
   * @param filename Name of file to load
   * @return a binaryloader to create a process
   */
  public static Loader getLoader(String filename) throws IOException
  {    
    if (ELF_Loader.conforms(filename)) {
      report("ELF object file found");
      return new ELF_Loader();
    } 
    else if (ImageLoader.conforms(filename)) {
      report("Image Loader file found.");
      return new ImageLoader();
    }
    else {
      throw new Error("File " + filename + " has an unrecognized binary format");
    }
  }
    

}
