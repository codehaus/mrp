/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator;

import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.os.loader.Loader;

/**
 * The runtime system for the emulator.
 * 
 * @author Ian Rogers, Richard Matley, Jon Burcham
 * 
 */
public class Main {
  /*
   * Variables required for an instance of the emulator
   */

  /**
   * A process space encapsulating the execution of a process
   */
  ProcessSpace ps;

  /*
   * Utility functions
   */

  /**
   * Debug information
   * 
   * @param s
   *          string of debug information
   */
  private static void report(String s) {
    if (DBT_Options.debugRuntime) {
      System.out.print("Main:");
      System.out.println(s);
    }
  }

  /**
   * Usage
   */
  public static void usage() {
    System.out
        .println("org.binarytranslator.Main [-X:dbt:...] <program> <args...>");
  }

  /**
   * Constructor - should only be run from main
   * 
   * @param args
   *          command line arguments. args[0] is the program to load.
   */
  private Main(String[] args) {
    // Check we have a file to load
    if (args.length < 1) {
      usage();
    } else {
      // Set up and load the process space
      try {
        report("Loading " + args[0]);
        Loader loader = Loader.getLoader(args[0]);
        ps = loader.readBinary(args);
      } catch (java.io.IOException e) {
        usage();
        throw new Error("Error accessing file: " + args[0], e);
      }
      report("Sucessfully created process");
    }
  }

  /**
   * Main method
   * 
   * @param args
   *          command line arguments (see usage())
   */
  public static void main(String[] args) {
    // Process any arguments for the emulator
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("-X:dbt:")) {
        DBT_Options.processArgument(args[i]);
      } else {
        if (i != 0) {
          String new_args[] = new String[args.length - i];
          for (int j = 0; j < (args.length - i); j++) {
            new_args[j] = args[i + j];
          }
          args = new_args;
        }
        break;
      }
    }
    Main runtime = new Main(args);
    for (int i = 0; i < args.length; i++) {
      report("Argument " + i + ": " + args[i]);
    }
    runtime.ps.run();
  }
}
