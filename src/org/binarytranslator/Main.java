/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator;

import java.io.File;

import org.binarytranslator.generic.execution.DynamicTranslationController;
import org.binarytranslator.generic.execution.ExecutionController;
import org.binarytranslator.generic.execution.GdbController;
import org.binarytranslator.generic.execution.InterpreterController;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * The runtime system for the emulator.
 * 
 * @author Ian Rogers, Richard Matley, Jon Burcham
 * 
 */
public class Main {
  private static ProcessSpace ps;

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
  public static void showUsage() {
    System.out
        .println("org.binarytranslator.Main [-X:dbt:...] <program> <args...>");
  }
  
  

  /**
   * Main method
   * 
   * @param args
   *          command line arguments (see usage())
   */
  public static void main(String[] args) {

    // Process any arguments for the emulator
    try {
      DBT_Options.parseArguments(args);
    } catch (Exception e) {
      System.err.println("Error while parsing command line arguments.");
      
      if (DBT_Options.debugRuntime)
        e.printStackTrace();
      
      showUsage();
      return;
    }
    
    //check if the user actually supplied an executable name
    if (DBT_Options.executableFile == null) {
      System.err.println("Missing executable file name");
      showUsage();
      return;
    }
    
    //also make sure that the said executable really exists
    if (!new File(DBT_Options.executableFile).exists()) {
      System.err.println("The specified executable '" + DBT_Options.executableFile + "' could not be found.");
      return;
    }

    try {
      report("Loading " + DBT_Options.executableFile);
      Loader loader = Loader.getLoader(DBT_Options.executableFile);
      ps = loader.readBinary(DBT_Options.executableFile);
    } 
    catch (java.io.IOException e) {
      System.err.println("Error accesing file: " + args[0] + ". " + e.getMessage());
      return;
    }
    /*catch (Error e) {
      System.err.println(e.getMessage());
      return;
    }*/

    report("Sucessfully created process.");
    
    if (DBT_Options.debugPS) {
      System.out.println("***** INITIAL PROCESS SPACE *****\n");
      System.out.println(ps);
    }

    //Create an execution controller and pass execution on to it
    ExecutionController controller;
    
    if (DBT_Options.buildForSunVM) {
      controller = new InterpreterController(ps);
    }
    else {
      if (DBT_Options.gdbStub) {
        controller = new GdbController(DBT_Options.gdbStubPort, ps);
      }
      else {
        controller = new DynamicTranslationController(ps);
      }
    }
    
    controller.run();
    System.out.println("\nExecution controller has exited.");
  }

  public static void onExit(int exitcode) {
    System.out.println("\nProgram has finished. Exitcode: " + exitcode);
    
    /*try {
      //ps.branchInfo.saveAsXML("/tmp/profile.xml");
    } catch (IOException e) {
      e.printStackTrace();
    }*/
  }
}
