/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.abi.linux;

import java.io.UnsupportedEncodingException;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.memory.Memory;
import org.binarytranslator.generic.memory.MemoryMapException;

/**
 * This class captures the common parts of stack initialization for a process.
 * From the System V ABI specification shows the initial process stack varies
 * across architectures, but in general it should look like:
 * 
 * <pre>
 * ----------- High address
 * Unspecified
 * -----------
 * Information block, including argument strings, environment strings,
 * auxiliary information
 * ...
 * (size varies)
 * -----------
 * Unspecified
 * -----------
 * Null auxiliary vector entry
 * -----------
 * Auxiliary vector
 * ...
 * (2-word entries)
 * -----------
 * 0 word
 * -----------
 * Environment pointers
 * ...
 * (one word each)
 * -----------
 * 0 word
 * -----------
 * Argument pointers
 * ...
 * (Argument count words)
 * -----------
 * Argument count
 * ----------- Low address
 * </pre>
 */
public class LinuxStackInitializer {

  /*
   * ABI constants
   */

  /**
   * Class capturing auxiliary vector types
   */
  public final static class AuxiliaryVectorType {
    /**
     * The auxiliary vector has no fixed length; instead an entry of this type
     * denotes the end of the vector. The corresponding value of a_un is
     * undefined.
     */
    public final static int AT_NULL = 0;

    /**
     * This type indicates the entry has no meaning. The corresponding value of
     * a_un is undefined.
     */
    public final static int AT_IGNORE = 1;

    /**
     * As Chapter 5 in the System V ABI describes, exec(BA_OS) may pass control
     * to an interpreter program. When this happens, the system places either an
     * entry of type AT_EXECFD or one of type AT_PHDR in the auxiliary vector.
     * The entry for type AT_EXECFD uses the a_val member to contain a file
     * descriptor open to read the application program’s object file.
     */
    public final static int AT_EXECFD = 2;

    /**
     * Under some conditions, the system creates the memory image of the
     * application program before passing control to an interpreter program.
     * When this happens, the a_ptr member of the AT_PHDR entry tells the
     * interpreter where to find the program header table in the memory image.
     * If the AT_PHDR entry is present, entries of types AT_PHENT, AT_PHNUM, and
     * AT_ENTRY must also be present. See the section Program Header in Chapter
     * 5 of the System V ABI and the section Program Loading in Chapter 5 of
     * this processor supplement for more information about the program header
     * table.
     */
    public final static int AT_PHDR = 3;

    /**
     * The a_val member of this entry holds the size, in bytes, of one entry in
     * the program header table to which the AT_PHDR entry points.
     */
    public final static int AT_PHENT = 4;

    /**
     * The a_val member of this entry holds the size, in bytes, of one entry in
     * the program header table to which the AT_PHDR entry points.
     */
    public final static int AT_PHNUM = 5;

    /**
     * If present, this entry’s a_val member gives the system page size in
     * bytes. The same information is also available through sysconf(BA_OS).
     */
    public final static int AT_PAGESZ = 6;

    /**
     * The a_ptr member of this entry holds the base address at which the
     * interpreter program was loaded into memory. See the section Program
     * Header in Chapter 5 of the System V ABI for more information about the
     * base address.
     */
    public final static int AT_BASE = 7;

    /**
     * If present, the a_val member of this entry holds 1-bit flags. Bits with
     * undefined semantics are set to zero.
     */
    public final static int AT_FLAGS = 8;

    /**
     * The a_ptr member of this entry holds the entry point of the application
     * program to which the interpreter program should transfer control.
     */
    public final static int AT_ENTRY = 9;

    /**
     * The a_val member of this entry is non-zero if the dynamic linker should
     * examine LD_LIBRARY_PATH when searching for shared objects of the process
     * based on the security considerations in the Shared Object Dependency
     * section in Chapter 5 of the gABI. Defined in the i386 System V ABI spec.
     */
    public final static int AT_LIBPATH = 10;

    /**
     * Program is not an ELF. This conflicting definition for type 10 occurs in
     * elf.h
     */
    public final static int AT_NOTELF = 10;

    /**
     * The a_val member of this entry will be set to: 0, if no floating point
     * support exists; 1, if floating point software emulation exists; 3, if it
     * has an 80287 chip; 4, if it has an 80387 or 80487 chip. Defined in the
     * i386 System V ABI spec.
     */
    public final static int AT_FPHW = 11;

    /**
     * Real UID. Defined in elf.h
     */
    public final static int AT_UID = 11;

    /**
     * The a_val member of this entry holds the device number of the file from
     * which the dynamic linker is loaded. Defined in the i386 System V ABI
     * spec.
     */
    public final static int AT_INTP_DEVICE = 12;

    /**
     * Effective UID. Defined in elf.h
     */
    public final static int AT_EUID = 12;

    /**
     * The a_val member of this entry holds the inode of the file from which the
     * dynamic linker is loaded. Defined in the i386 System V ABI spec.
     */
    public final static int AT_INTP_INODE = 12;

    /**
     * Real gid. Defined in elf.h
     */
    public final static int AT_GID = 13;

    /**
     * Effective gid. Defined in elf.h
     */
    public final static int AT_EGID = 14;

    /**
     * String identifying CPU for optimizations
     */
    public final static int AT_PLATFORM = 15;

    /**
     * Arch dependent hints at CPU capabilities
     */
    public final static int AT_HWCAP = 16;

    /**
     * Frequency at which times() increments
     */
    public final static int AT_CLKTCK = 17;

    /**
     * The a_val member of this entry gives the data cache block size for
     * processors on the system on which this program is running. If the
     * processors have unified caches, AT_DCACHEBSIZE is the same as
     * AT_UCACHEBSIZE
     */
    public final static int AT_DCACHEBSIZE = 19;

    /**
     * The a_val member of this entyr gives the instruction cache block size for
     * processors on the system on which this program is running. If the
     * processors have unified caches, AT_DCACHEBSIZE is the same as
     * AT_UCACHEBSIZE.
     */
    public final static int AT_ICACHEBSIZE = 20;

    /**
     * The a_val member of this entry is zero if the processors on the system on
     * which this program is running do not have a unified instruction and data
     * cache. Otherwise it gives the cache block size.
     */
    public final static int AT_UCACHEBSIZE = 21;

    /**
     * All entries of this type should be ignored.
     */
    public final static int AT_IGNOREPPC = 22;

    /**
     * Boolean, was exec setuid-like?
     */
    public final static int AT_SECURE = 23;

    /**
     * X86 Specific: The sysinfo page is a shared page in the kernel, aka
     * Virtual Dynamically Shared Object (VDSO), this is the entry point into
     * that page
     */
    public final static int AT_SYSINFO = 32;

    /**
     * X86 Specific: The sysinfo page is a shared page in the kernel, aka
     * Virtual Dynamically Shared Object (VDSO), this is the ELF header for that
     * page
     */
    public final static int AT_SYSINFO_EHDR = 33;
  }

  /**
   * Set up the process stack to:
   * 
   * <pre>
   * ----------- stackTop
   * 0 word
   * ----------- stackTop - 4
   * Information block, including argument strings, environment strings,
   * auxiliary information
   * ...
   * (size varies)
   * ----------- infoBlockStart
   * Null auxiliary vector entry
   * -----------
   * Auxiliary vector
   * ...
   * (2-word entries)
   * -----------
   * 0 word
   * -----------
   * Environment pointers
   * ...
   * (one word each)
   * -----------
   * 0 word
   * -----------
   * Argument pointers
   * ...
   * (Argument count words)
   * -----------
   * Argument count
   * ----------- Low address
   * </pre>
   * 
   * @param argv the command line arguments of the PPC binary (starting with its
   *          name, C style).
   * @param env the environment variables.
   * @param the auxiliary vector, including the terminating AT_NULL (two
   *          zeroes).
   * @return the bottom of the stack in memory
   */
  public static int stackInit(Memory memory, int stackTop, String[] env,
      int[] auxVector) {

    // grab the vector of command line options that are to be delivered to the
    // linux program
    String[] argv = new String[DBT_Options.executableArguments.length+1];
    argv[0] = DBT_Options.executableFile;
    for (int i=0; i < DBT_Options.executableArguments.length; i++) {
      argv[i+1] = DBT_Options.executableArguments[i];
    }
  
    // ---
    // First create the information block by concatenating all strings
    // together, then compute pointers to values in the information
    // block to be held lower down the initial stack
    // ---
    // The infoBlock as a byte array
    byte[] infoBlockBytes;
    // Starting address for the infoBlock
    int infoBlockStart;
    {
      StringBuffer infoBlock = new StringBuffer();

      for (int a = 0; a < argv.length; a++) {
        infoBlock.append(argv[a]);
        infoBlock.append('\0'); // NB Java Strings are not null terminated.
      }
      for (int e = 0; e < env.length; e++) {
        infoBlock.append(env[e]);
        infoBlock.append('\0');
      }
      // Add up to 3bytes of padding to word align
      switch (infoBlock.length() & 3) {
      case 1:
        infoBlock.append('\0');
      case 2:
        infoBlock.append('\0');
      case 3:
        infoBlock.append('\0');
      default:
      }
      try {
        infoBlockBytes = infoBlock.toString().getBytes("US-ASCII");
      } catch (UnsupportedEncodingException e) {
        // Failing to encode the info block into bytes is a fatal error
        throw new Error("Failed to convert infoBlock into US-ASCII bytes", e);
      }
      infoBlockStart = stackTop - 4 - infoBlock.length();
    }
    // Array to hold pointers to arguments and environment variables
    int[] argPtr = new int[argv.length];
    int[] envPtr = new int[env.length];
    // Compute pointer values into the infoBlock for arguments and
    // environment variables
    {
      int ptr = infoBlockStart;
      for (int i = 0; i < argv.length; i++) {
        argPtr[i] = ptr + 1;
        ptr += argv[i].length() + 1;
      }
      for (int i = 0; i < env.length; i++) {
        envPtr[i] = ptr + 1;
        ptr += env[i].length() + 1;
      }
    }
    // ---
    // Create the pages of memory for the stack
    // ---
    {
      // how big the initial stack will be
      int initialStackSize = 4 + // 0 word +
          infoBlockBytes.length + // information block size +
          (auxVector.length * 4) + // 1 word per auxiliary vector item +
          4 + // 0 word +
          (env.length * 4) + // 1 pointer per environment variable +
          4 + // 0 word +
          (argv.length * 4) + // 1 pointer per argument +
          4; // argc

      // Round this up to a page
      initialStackSize = memory.truncateToNextPage(initialStackSize);

      try {
        memory.map(stackTop - initialStackSize - 8192, initialStackSize + 8192,
            true, true, false); // read/write/no execute
      } catch (MemoryMapException e) {
        // Failing to create the stack is a fatal error
        throw new Error("Failed to create stack", e);
      }
    }
    // ---
    // Place the values into memory
    // ---
    int stackPtr = stackTop;
    {
      // 0 word
      stackPtr -= 4;
      memory.store32(stackPtr, 0);
      // information block
      for (int i = 0; i < infoBlockBytes.length; i++) {
        stackPtr--;
        memory.store8(stackPtr, infoBlockBytes[i]);
      }
      // auxiliary vector
      for (int i = (auxVector.length - 1); i >= 0; i--) {
        stackPtr -= 4;
        memory.store32(stackPtr, auxVector[i]);
      }
      // 0 word
      stackPtr -= 4;
      memory.store32(stackPtr, 0);
      // environment variables
      for (int i = (envPtr.length - 1); i >= 0; i--) {
        stackPtr -= 4;
        memory.store32(stackPtr, envPtr[i]);
      }
      // 0 word
      stackPtr -= 4;
      memory.store32(stackPtr, 0);
      // arguments
      for (int i = (argPtr.length - 1); i >= 0; i--) {
        stackPtr -= 4;
        memory.store32(stackPtr, argPtr[i]);
      }
      // argc
      stackPtr -= 4;
      memory.store32(stackPtr, argPtr.length);
    }
    return stackPtr;
  }
}
