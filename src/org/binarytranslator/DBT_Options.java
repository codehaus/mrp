/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.binarytranslator.generic.execution.ExecutionController;

/**
 * Options for controlling the emulator
 */
public class DBT_Options {
  
  /** Remove features that will only work on jikes? */
  public final static boolean buildForSunVM = false;
  
  /** Enable the profiling of application during interpretation? */
  public final static boolean profileDuringInterpretation = true;
  
  /** Debug binary loading */
  public final static boolean debugLoader = true;

  /** Are unimplemented system calls fatal? */
  public final static boolean unimplementedSystemCallsFatal = false;

  /** The file that is currently being executed. */
  public static String executableFile;
  
  /** Arguments given to the executable.*/
  public static String[] executableArguments = null;

  /**  Instructions to translate for an optimisation level 0 trace */
  public static int instrOpt0 = 684;

  /** Instructions to translate for an optimisation level 1 trace */
  public static int instrOpt1 = 1500;

  /** Instructions to translate for an optimisation level 2 trace */
  public static int instrOpt2 = 1500;
  
  /** Determines how the program will be run (i.e. interpreted, translated etc.) */
  public static ExecutionController.Type executionController = ExecutionController.Type.Translator;

  /**
   * Favour backward branch optimization. Translate backward branch addresses
   * before the next instructions (this is the manner of the 601's branch
   * predictor).
   */
  public final static boolean optimizeBackwardBranches = true;

  /** Set this to true to record uncaught branch instructions */
  public static boolean plantUncaughtBranchWatcher = false;

  /** Should direct branches be resolved before dynamic branches? */
  public static boolean resolveDirectBranchesFirst = true;

  /** Set this to true to translate only one instruction at a time. */
  public static boolean singleInstrTranslation = false;

  /** Eliminate unneeded filling of register */
  public final static boolean eliminateRegisterFills = true;

  /** Print dissassembly of translated instructions. */
  public static boolean debugInstr = true;

  /** Print information about the lazy resolution of branch addresses...*/
  public static boolean debugBranchResolution = true;

  /** During code translation, print information about the creation of basic blocks. */
  public final static boolean debugCFG = false;

  /** Debug using GDB? */
  public static boolean gdbStub = false;

  /** GDB stub port */
  public static int gdbStubPort = 1234;
  
  /** Print debug information during the translation of instructions. */
  public static boolean debugTranslation = true;

  /** In ProcessSpace, print syscall numbers. */
  public static boolean debugSyscall = true;

  /** In ProcessSpace, print syscall numbers. */
  public static boolean debugSyscallMore = false;

  /** Print out various messages about the emulator starting. */
  public static boolean debugRuntime = true;

  /** Print out messages from the memory system */
  public static boolean debugMemory = false;

  /** The user ID for the user running the command */
  public final static int UID = 1000;

  /** The group ID for the user running the command */
  public final static int GID = 100;
  
  /** Stores the arguments given to the DBT by the user. These are NOT the arguments given to the executable. */
  private static HashMap<String, String> dbtArguments = null;
  
  /** Read and parse the command line arguments.  */
  public static void parseArguments(String[] args) {
    
    dbtArguments = new HashMap<String, String>();

    try {
      ArrayList<String> remainingArguments = new ArrayList<String>();
      ArgumentParser.parse(args, dbtArguments, remainingArguments);
      
      //did the user give an executable to execute?
      if (remainingArguments.size() > 0) {
        executableFile = remainingArguments.get(0);
        remainingArguments.remove(0);
        
        executableArguments = new String[remainingArguments.size()];
        remainingArguments.toArray(executableArguments);
      }
    }
    catch (ArgumentParser.ParseException e) {
      throw new Error(e.getMessage());
    }
    
    for (Entry<String, String> argument : dbtArguments.entrySet()) {
      String arg = argument.getKey();
      String value = argument.getValue();
      
      try {
        parseSingleArgument(arg, value);
      }
      catch (NumberFormatException e) {
        throw new Error("Argument " + arg + " is not a valid integer.");
      }
      catch (Exception e) {
        throw new Error("Error while parsing argument '" + arg + "'.", e);
      }
    }
  }
  
  /** Parses a single argument into the options class. */
  private static void parseSingleArgument(String arg, String value) {
    
    if (!arg.startsWith("-X:dbt:")) {
      throw new Error("Invalid argument. Argument prefix '-X:dbt:' expected.");
    }
    
    arg = arg.substring(7);

    if (arg.equalsIgnoreCase("debugInstr")) {
      debugInstr = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("debugRuntime")) {
      debugRuntime = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("debugBranchResolution")) {
      debugBranchResolution = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("debugMemory")) {
      debugMemory = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("debugSyscall")) {
      debugSyscall = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("debugSyscallMore")) {
      debugSyscallMore = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("instrOpt0")) {
      instrOpt0 = Integer.parseInt(value);
    } else if (arg.equalsIgnoreCase("instrOpt1")) {
      instrOpt1 = Integer.parseInt(value);
    } else if (arg.equalsIgnoreCase("instrOpt2")) {
      instrOpt2 = Integer.parseInt(value);
    } else if (arg.equalsIgnoreCase("singleInstrTranslation")) {
      singleInstrTranslation = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("resolveDirectBranchesFirst")) {
      resolveDirectBranchesFirst = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("gdbStub")) {
      gdbStub = Boolean.parseBoolean(value);
    } else if (arg.equalsIgnoreCase("gdbStubPort")) {
      gdbStubPort = Integer.parseInt(value);
    } else if (arg.equalsIgnoreCase("controller")) {
      executionController = ExecutionController.Type.valueOf(value);
    } else {
      throw new Error("Unknown DBT option: " + arg);
    }
  }
  
  /** A helper class that uses a state pattern to parse arguments, as given by a Java Main() function.
   * The class distinguishes two argument types: Key-Value-Pairs and Remaining arguments.
   * Key Value pairs take the form KEY = VALUE and must appear as the first arguments. Remaining arguments
   * take the form ARGUMENT1 ARGUMENT2 ARGUMENT3.*/
  private static class ArgumentParser {
    
    /** The current parsing state. The class is using a state pattern. */
    protected State state;
    
    /** A Key-Value mapping of key to arguments. */
    protected final Map<String, String> arguments;
    
    /** A list of remaining arguments. See the class documentation to learn about the difference
     * between Key-Value and remaining arguments. */
    protected final ArrayList<String> remainingArguments;
    
    /**
     * Parses command line arguments.
     * @param args
     *  An array of arguments to parse. This array is usually supplied by a Java Main() method.
     * @param keyValueArguments
     *  Parsed Key-Value argument pairs are stored within this map. 
     * @param remainingArguments
     *  Remaining arguments are being stored into this array list.
     * @throws ParseException
     *  A ArgumentParser.ParseException is thrown in case arguments are not properly formatted and could not be parsed. 
     */
    public static void parse(String[] args, Map<String, String> keyValueArguments, ArrayList<String> remainingArguments) 
      throws ParseException {
      
      ArgumentParser parser = new ArgumentParser(keyValueArguments, remainingArguments);
      parser.parseArguments(args);
    }
    
    /**
     * Creates a new ArgumentParser instance
     * @param arguments
     *  Parsed Key-Value argument pairs are stored within this map.
     * @param remainingArguments
     *  Remaining arguments are being stored into this array list.
     */
    private ArgumentParser(Map<String, String> arguments, ArrayList<String> remainingArguments) {
      this.arguments = arguments;
      this.remainingArguments = remainingArguments;
    }
    
    /**
     * Parses the given arguments into {@link #arguments} and {@link #remainingArguments}.
     * @param args
     *  The arguments that are to be parsed. This array is usually supplied by a Java Main() method.
     * @throws ParseException
     *  A ArgumentParser.ParseException is thrown in case arguments are not properly formatted and could not be parsed.
     */
    private void parseArguments(String[] args) 
      throws ParseException {  
      switchState(new AwaitingKeyState());
      
      int next = 0;
      
      while (next < args.length) {
        String input = args[next++].trim();
        
        int pos = input.indexOf("=");
        
        if (pos == 0) {
          //this token has the form "=TEXT"
          do {
            state.onAssignment();
            input = input.substring(1);
          }
          while (input.startsWith("="));
        }
        else if (pos > 0) {
          //the token has the form "TEXT="
          state.onText(input.substring(0, pos));
          state.onAssignment();
          
          //handle remaining text (form TEXT=TEXT)
          input = input.substring(pos + 1);
        }
        
        if (input.length() > 1) {
          state.onText(input);
        }
      }
      
      state.onEnd();
    }
    
    /** Switches the parser state to a new state. */
    protected void switchState(State s) {
      state = s;
    }
    
    /** An exception that is being thrown if parsing fails. */
    public static class ParseException extends Exception {
      
      protected ParseException(String msg) {
        super(msg);
      }
    }
    
    /** Every parser state must implement this interface. Its methods denote the possible inputs
     * that might occur during argument parsing. */
    private interface State {
      /** A text input token has been recognized by the parser. */
      void onText(String text) throws ParseException;
      
      /** An assignment input token (usually the equality symbol) has been recognized by the parser. */
      void onAssignment() throws ParseException;
      
      /** No more input tokens are available. */
      void onEnd() throws ParseException;
    }
    
    /** In this state, key-value arguments are being parsed and the parser awaits a new key. */
    private final class AwaitingKeyState implements State {

      public void onAssignment() throws ParseException {
        throw new ParseException("Unexpected token '=' while parsing arguments.");
      }

      public void onEnd() throws ParseException {
        //no further arguments, stop parsing
      }

      public void onText(String text) throws ParseException {
        switchState(new AwaitingAssignmentState(text));
      }
    }
    
    /** In this state, key-value arguments are being parsed and the parser awaits an assignment operator. */
    private final class AwaitingAssignmentState implements State {
      
      /** The previously input key. */
      private final String previousInput;
      
      public AwaitingAssignmentState(String previousInput) {
        this.previousInput = previousInput;
      }

      public void onAssignment() throws ParseException {
        switchState(new ParseValueArgumentState(previousInput));
      }

      public void onEnd() throws ParseException {
        //the key has obviously been a single remaining argument
        remainingArguments.add(previousInput);
      }

      public void onText(String text) throws ParseException {
        //the key has obviously been a single remaining argument and now we received the next one
        remainingArguments.add(previousInput);
        remainingArguments.add(text);
        
        switchState(new ParseRemainingArgumentsState());
      }
    }
    
    /** In this state, key-value arguments are being parsed and the parser awaits a value for a key-value pair. */
    private final class ParseValueArgumentState implements State {
      
      /** The previously input key. */
      private final String previousInput;
      
      public ParseValueArgumentState(String previousInput) {
        this.previousInput = previousInput;
      }

      public void onAssignment() throws ParseException {
        throw new ParseException("Invalid value for argument '" + previousInput + "'.");
      }

      public void onEnd() throws ParseException {
        throw new ParseException("Missing value for argument '" + previousInput + "'.");
      }

      public void onText(String text) throws ParseException {
        if (arguments.containsKey(text)) {
          throw new ParseException("Duplicate argument '" + previousInput + "' while parsing arguments.");
        }
        
        arguments.put(previousInput, text);
        switchState(new AwaitingKeyState());
      }
    }
    
    /** In this state, no more key-value arguments are parsed. All further text nodes are treated as remaining arguments.*/
    private final class ParseRemainingArgumentsState implements State {

      public void onAssignment() throws ParseException {
        remainingArguments.add("=");
      }

      public void onEnd() throws ParseException {
        //no-op
      }

      public void onText(String text) throws ParseException {
        remainingArguments.add(text);
      }
    }
  }
}
