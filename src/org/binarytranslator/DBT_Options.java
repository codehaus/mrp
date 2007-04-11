/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator;

import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Options for controlling the emulator
 */
public class DBT_Options {
  // -oO Runtime settings Oo-
  
  /**
   * Debug binary loading
   */
  public final static boolean debugLoader = true;

  /**
   * Are unimplemented system calls are fatal?
   */
  public final static boolean unimplementedSystemCallsFatal = true;

  // -oO Translation settings Oo-
  
  /** The file that is currently being executed. */
  public static String executableFile;
  
  /** Arguments given to the executable.*/
  public static String[] executableArguments = null;

  /**
   * The initial optimisation level
   */
  public static int initialOptLevel = 0;

  /**
   * Instructions to translate for an optimisation level 0 trace
   */
  public static int instrOpt0 = 684;

  /**
   * Instructions to translate for an optimisation level 1 trace
   */
  public static int instrOpt1 = 1500;

  /**
   * Instructions to translate for an optimisation level 2 trace
   */
  public static int instrOpt2 = 1500;

  /**
   * Favour backward branch optimization. Translate backward branch addresses
   * before the next instructions (this is the manner of the 601's branch
   * predictor).
   */
  public final static boolean optimizeBackwardBranches = true;

  /**
   * Set this to true to record uncaught branch instructions
   */
  public static boolean plantUncaughtBranchWatcher = false;

  /**
   * Should all branches (excluding to lr and ctr) be resolved in one big go or
   * one at at a time
   */
  public static boolean resolveBranchesAtOnce = true;

  /**
   * Should procedures (branches to ctr and lr) be given precedent over more
   * local branches
   */
  public static boolean resolveProceduresBeforeBranches = true;

  /**
   * Use global branch information rather than local (within the trace)
   * information when optimisation level is greater than or equal to this value
   */
  public static int globalBranchLevel = 3;

  /**
   * Set this to true to translate only one instruction at a time.
   */
  public static boolean singleInstrTranslation = true;

  /**
   * Eliminate unneeded filling of register
   */
  public final static boolean eliminateRegisterFills = true;

  // -oO Translation debugging options Oo-

  /**
   * Print dissassembly of translated instructions.
   */
  public static boolean debugInstr = true;

  /**
   * In PPC2IR, print information about lazy resolution...
   */
  public final static boolean debugLazy = false;

  /**
   * In PPC2IR, print cfg.
   */
  public final static boolean debugCFG = false;

  // -oO Runtime debugging options Oo-

  /**
   * Debug using GDB?
   */
  public static boolean gdbStub = false;

  /**
   * GDB stub port
   */
  public static int gdbStubPort = 1234;

  /**
   * In ProcessSpace, print syscall numbers.
   */
  public static boolean debugSyscall = false;

  /**
   * In ProcessSpace, print syscall numbers.
   */
  public static boolean debugSyscallMore = false;

  /**
   * Print out various messages about the emulator starting.
   */
  public static boolean debugRuntime = true;

  /**
   * Print out messages from the memory system
   */
  public static boolean debugMemory = false;

  /**
   * Print out process space between instructions
   */
  public final static boolean debugPS = false;

  /**
   * When printing process space, omit floating point registers.
   */
  public final static boolean debugPS_OmitFP = false;

  /**
   * The user ID for the user running the command
   */
  public final static int UID = 1000;

  /**
   * The group ID for the user running the command
   */
  public final static int GID = 100;
  
  /** Stores the arguments given to the DBT by the user. These are NOT the arguments given to the executable. */
  private final static HashMap<String, String> dbtArguments = new HashMap<String, String>();
  
  /**
   * Read and parse the command line arguments. 
   */
  public static void parseArguments(String[] args) {
    parseArgumentsToHashmap(args);
    
    for (Entry<String, String> argument : dbtArguments.entrySet()) {
      String arg = argument.getKey();
      String value = argument.getValue();
      
      try {
        parseSingleArgument(arg, value);
      }
      catch (NumberFormatException e) {
        throw new Error("Argument " + arg + " is not a valid integer.");
      }
    }
  }
  
  /**
   * Parses a single argument into the options class.
   */
  private static void parseSingleArgument(String arg, String value) {

    if (arg.equalsIgnoreCase("-X:dbt:debugInstr")) {
      debugInstr = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:debugRuntime")) {
      debugRuntime = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:debugSyscall")) {
      debugSyscall = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:debugSyscallMore")) {
      debugSyscallMore = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:globalBranchLevel")) {
      globalBranchLevel = Integer.parseInt(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:initialOptLevel")) {
      initialOptLevel = Integer.parseInt(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:instrOpt0")) {
      instrOpt0 = Integer.parseInt(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:instrOpt1")) {
      instrOpt1 = Integer.parseInt(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:instrOpt2")) {
      instrOpt2 = Integer.parseInt(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:singleInstrTranslation")) {
      singleInstrTranslation = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:resolveBranchesAtOnce")) {
      resolveBranchesAtOnce = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:resolveBranchesAtOnce")) {
      resolveBranchesAtOnce = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:resolveProceduresBeforeBranches")) {
      resolveProceduresBeforeBranches = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:resolveProceduresBeforeBranches")) {
      resolveProceduresBeforeBranches = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:gdbStub")) {
      gdbStub = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("-X:dbt:gdbStubPort")) {
      gdbStubPort = Integer.parseInt(value);
    } else {
      throw new Error("DBT Options: Unknown emulator option " + arg);
    }
  }
  
  /**
   * Takes an array of arguments and parses them as key=value pairs into the hashmap arguments.
   */
  private static void parseArgumentsToHashmap(String[] args) {
    
    String key = null;
    String value;
    int next = 0;
    
    try {
      //are there further arguments?
      if (next == args.length) {
        return;
      }
      
      key = args[next++].trim();
      
      if (!key.startsWith("-")) {
        //this is not an argument to the DBT, so it must the file we're trying to execute.
        executableFile = key;
        
        //the remaining arguments may be passed to the executable
        executableArguments = new String[args.length - next];
        for (int i = next; i < args.length; i++)
          executableArguments[i] = args[next + i];
        
        return;
      }

      //did the user give an argument without spaces in it?
      int pos = key.indexOf('=');
      if (pos != -1) {
        value = key.substring(pos + 1);
        key = key.substring(0, pos);
      }
      else {
        
        //extract the argument's value
        do {
          value = args[next++].trim();
          
          if (value.startsWith("="))
          {
            if (value.length() > 1)
              value = value.substring(1);
            else
              value = "";
          }
        }
        while ( value.length() == 0 );
      }
      
      //store the argument's key and value
      if (dbtArguments.containsKey(key)) {
        throw new Error(String.format("Parameter %s already defined", key));
      }
      
      dbtArguments.put(key, value); 
    }
    catch (Exception e) {
      throw new Error("Invalid argument format for argument " + key);
    }
  }
}
