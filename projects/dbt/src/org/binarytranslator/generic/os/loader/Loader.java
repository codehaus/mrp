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
 * The Loader creates a process for a binary and then loads the binary into it.
 * This class is a superclass for different executable file formats. The
 * appropriate sub-class loader is chosen to actually load the binary.
 */
public abstract class Loader {

  /**
   * Debug information
   * 
   * @param s
   *          string of debug information
   */
  private static void report(String s) {
    if (DBT_Options.debugLoader) {
      System.out.print("Loader:");
      System.out.println(s);
    }
  }
  /**
   * Create a process space, load the binary into it and initialise the stack,
   * etc.
   * 
   * @param filename
   *          The file, which is to be loaded and executed.
   * @return the process space to start executing
   */
  abstract public ProcessSpace readBinary(String filename) throws IOException;

  /** A list of supported instruction set architectures. */
  public enum ISA {
    Undefined,
    X86,
    PPC,
    ARM
  }
  
  /** A list of supported Application binary interface. */
  public enum ABI {
    Undefined,
    SystemV,
    Linux,
    ARM
  }
  
  /** Returns the instruction set architecture required by this executable. */
  public abstract ISA getISA();
  
  /** Shall return the Application Binary Interface that is required to load this executable. */
  public abstract ABI getABI();
  
  /** Shall return the top of the stack. */
  public abstract int getBrk();
  
  /** Shall return the address at which execution of the program starts. */
  public abstract int getEntryPoint();

  /**
   * Open and read the start of the file to determine the appropriate file
   * loader to use
   * 
   * @param filename
   *          Name of file to load
   * @return a binaryloader to create a process
   */
  public static Loader getLoader(String filename) throws IOException {
    if (ELF_Loader.conforms(filename)) {
      report("ELF object file found");
      return new ELF_Loader();
    } else if (ImageLoader.conforms(filename)) {
      report("Image Loader file found.");
      return new ImageLoader();
    } else {
      throw new Error("File " + filename + " has an unrecognized binary format");
    }
  }

}
