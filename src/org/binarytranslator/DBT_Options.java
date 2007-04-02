/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator;

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

  /**
   * Process a command line option
   * 
   * @arg the command line argument starting "-X:dbt:"
   */
  public static void processArgument(String arg) {
    if (arg.startsWith("-X:dbt:debugInstr=true")) {
      debugInstr = true;
    } else if (arg.startsWith("-X:dbt:debugRuntime=true")) {
      debugRuntime = true;
    } else if (arg.startsWith("-X:dbt:debugSyscall=true")) {
      debugSyscall = true;
    } else if (arg.startsWith("-X:dbt:debugSyscallMore=true")) {
      debugSyscallMore = true;
    } else if (arg.startsWith("-X:dbt:globalBranchLevel=")) {
      globalBranchLevel = Integer.parseInt(arg.substring(25));
    } else if (arg.startsWith("-X:dbt:initialOptLevel=")) {
      initialOptLevel = Integer.parseInt(arg.substring(23));
    } else if (arg.startsWith("-X:dbt:instrOpt0=")) {
      instrOpt0 = Integer.parseInt(arg.substring(17));
    } else if (arg.startsWith("-X:dbt:instrOpt1=")) {
      instrOpt1 = Integer.parseInt(arg.substring(17));
    } else if (arg.startsWith("-X:dbt:instrOpt2=")) {
      instrOpt2 = Integer.parseInt(arg.substring(17));
    } else if (arg.startsWith("-X:dbt:singleInstrTranslation=true")) {
      singleInstrTranslation = true;
    } else if (arg.startsWith("-X:dbt:resolveBranchesAtOnce=true")) {
      resolveBranchesAtOnce = true;
    } else if (arg.startsWith("-X:dbt:resolveBranchesAtOnce=false")) {
      resolveBranchesAtOnce = false;
    } else if (arg.startsWith("-X:dbt:resolveProceduresBeforeBranches=true")) {
      resolveProceduresBeforeBranches = true;
    } else if (arg.startsWith("-X:dbt:resolveProceduresBeforeBranches=false")) {
      resolveProceduresBeforeBranches = false;
    } else if (arg.startsWith("-X:dbt:gdbStub=true")) {
      gdbStub = true;
    } else if (arg.startsWith("-X:dbt:gdbStubPort=")) {
      gdbStubPort = Integer.parseInt(arg.substring(19));
    } else {
      throw new Error("DBT Options: Unrecongised emulator option " + arg);
    }
  }
}
