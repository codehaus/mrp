/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.process;

import java.util.Hashtable;
import java.io.*;

import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.compilers.opt.ir.OPT_GenerationContext;
import org.jikesrvm.compilers.opt.ir.OPT_HIRGenerator;

import org.binarytranslator.vmInterface.DBT_Trace;
import org.binarytranslator.vmInterface.DynamicCodeRunner;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.branch.BranchLogic;
import org.binarytranslator.generic.memory.Memory;
import org.binarytranslator.generic.memory.MemoryMapException;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.generic.gdbstub.GDBStub;
import org.binarytranslator.generic.gdbstub.GDBTarget;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.arch.x86.os.process.X86_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.ppc.os.process.PPC_ProcessSpace;

/**
 * A process space encapsulates a running process. This superclass contains non
 * operating and architecture specific details of the process.
 */
public abstract class ProcessSpace {
  /*
   * Runtime information
   */

  /**
   * A record of branches to guide translation
   */
  public final BranchLogic branchInfo;

  /**
   * A hashtable containing translated traces of code
   */
  protected final Hashtable<Integer, VM_CodeArray> codeHash = new Hashtable<Integer, VM_CodeArray>();

  /**
   * Has a system call been called to terminate the process
   */
  public boolean finished = false;

  /*
   * Interface to memory
   */

  /**
   * The memory for the process. As this is user mode code, it is a virtual
   * address space
   */
  public Memory memory;

  /**
   * Load a 32bit value from memory
   */
  public int memoryLoad32(int wordAddr) {
    return memory.load32(wordAddr);
  }

  /**
   * Store a 32bit value to memory
   */
  public void memoryStore32(int address, int data) {
    memory.store32(address, data);
  }

  /**
   * Load a 16bit value from memory
   */
  public int memoryLoad16(int hwAddr) {
    return memory.loadSigned16(hwAddr);
  }

  /**
   * Store a 16bit value to memory
   */
  public void memoryStore16(int hwAddr, int iValue) {
    memory.store16(hwAddr, iValue);
  }

  /**
   * Load a 8bit value from memory
   */
  public byte memoryLoad8(int address) {
    return (byte) memory.loadSigned8(address);
  }

  /**
   * Store a 8bit value to memory
   */
  public void memoryStore8(int address, byte data) {
    memory.store8(address, data);
  }

  /**
   * Read an ASCIIZ string from the process' memory into a Java String
   */
  public String memoryReadString(int address) {
    StringBuffer str = new StringBuffer();
    char c;

    while ((c = (char) memoryLoad8(address++)) != 0)
      str.append(c);

    return str.toString();
  }

  /**
   * Write a Java string (crudely) to an ASCIIZ string in the process' memory
   */
  public void memoryWriteString(int byteAddr, String value) {
    if (value != null) {
      for (int i = 0; i < value.length(); i++) {
        memoryStore8(byteAddr + i, (byte) value.charAt(i));
      }
      memoryStore8(byteAddr + value.length(), (byte) 0);
    }
  }

  /**
   * Map an anonymous page of memory
   * 
   * @param addr
   *          the address to map or NULL if don't care
   * @param len
   *          the amount of memory to map
   * @param read
   *          is the page readable
   * @param write
   *          is the page writable
   * @param exec
   *          is the page executable
   */
  public int memoryMap(int addr, int len, boolean read, boolean write,
      boolean exec) throws MemoryMapException {
    return memory.map(addr, len, read, write, exec);
  }

  /**
   * Simulate an munmap system call.
   * 
   * @param start
   *          start of memory area to unmap.
   * @param length
   *          length of area.
   */
  public int munmap(int start, int length) {
    memory.unmap(start, length);
    return 0;
  }

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
    if (DBT_Options.debugLoader) {
      System.out.print("ProcessSpace:");
      System.out.println(s);
    }
  }

  /*
   * Methods
   */

  /**
   * Create an optimizing compiler HIR code generator suitable for a particular
   * architecture
   * 
   * @param context
   *          the generation context for the HIR generation
   * @return a HIR generator
   */

  public abstract OPT_HIRGenerator createHIRGenerator(
      OPT_GenerationContext context);

  /**
   * Given an ELF binary loader, create the appropriate process space
   * 
   * @param elf
   *          the elf binary loader
   * @return the appropriate process space
   */
  public static ProcessSpace createProcessSpaceFromBinary(Loader loader)
      throws IOException {
    ProcessSpace result;
    if (loader.isX86_ISA()) {
      report("X86 Binary");
      result = X86_ProcessSpace.createProcessSpaceFromBinary(loader);
    } else if (loader.isPPC_ISA()) {
      report("PPC Binary");
      result = PPC_ProcessSpace.createProcessSpaceFromBinary(loader);
    } else if (loader.isARM_ISA()) {
      report("ARM Binary");
      result = ARM_ProcessSpace.createProcessSpaceFromBinary(loader);
    } else {
      throw new UnsupportedOperationException("Binary of "
          + loader.getArchitectureString() + " architecture is unsupported");
    }
    return result;
  }

  /**
   * Create a segment
   * 
   * @param RandomAccessFile
   *          file to read segment data from if file size != 0
   * @param offset
   *          file offset
   * @param address
   *          location of segment
   * @param filesize
   *          size of segment in file
   * @param memsize
   *          size of segment in memory
   * @param read
   *          is segment readable
   * @param write
   *          is segment writable
   * @param exec
   *          is segment executable
   */
  public void createSegment(RandomAccessFile file, long offset, int address,
      int filesize, int memsize, boolean read, boolean write, boolean exec)
      throws MemoryMapException {
    // Sanity check
    if (memsize < filesize) {
      throw new Error("Segment memory size (" + memsize
          + ")less than file size (" + filesize + ")");
    }
    // Are we mapping anything from a file?
    if (filesize == 0) {
      // No: map anonymously
      memory.map(address, memsize, read, write, exec);
    } else {
      // align offset and address
      int alignedAddress;
      long alignedOffset;
      int alignedFilesize;
      if (memory.isPageAligned(address)) {
        // memory and therefore offset should be aligned
        alignedAddress = address;
        alignedOffset = offset;
        alignedFilesize = filesize;
      } else {
        // Address not aligned
        alignedAddress = memory.truncateToPage(address);
        int delta = address - alignedAddress;
        // adjust offset and length too
        alignedOffset = offset - delta;
        alignedFilesize = filesize + delta;
      }
      memory.map(file, alignedOffset, alignedAddress, alignedFilesize, read,
          write, exec);
      // Do we need to map in some blank pages at the end of the segment?
      if (filesize < memsize) {
        alignedAddress = memory.truncateToNextPage(address + filesize);
        memory.map(alignedAddress, memsize - filesize, read, write, exec);
      }
    }
  }

  /**
   * Initialise the process space
   * 
   * @param loader
   *          the loader that's created the process space
   * @param pc
   *          the entry point
   * @param brk
   *          the initial value for the top of BSS
   * @param args
   *          command line arguments
   */
  public abstract void initialise(Loader loader, int pc, int brk, String args[]);

  /**
   * Constructor
   */
  protected ProcessSpace() {
    branchInfo = new BranchLogic();
  }

  /**
   * Constructor
   */
  public ProcessSpace(String[] args) throws IOException {
    branchInfo = new BranchLogic();
  }

  /**
   * Replace the compiled code for a trace (called by the adaptive system)
   */
  public synchronized void replaceCompiledTrace(VM_CompiledMethod cm,
      DBT_Trace trace) {
    VM_CodeArray code = cm.getEntryCodeArray();
    codeHash.put(trace.pc, code);
  }

  public synchronized VM_CodeArray getCodeForPC(int pc) {
    VM_CodeArray code = (VM_CodeArray) codeHash.get(pc);
    if (code == null) {
      code = translateCode(new DBT_Trace(this, pc));
    }
    return code;
  }

  private VM_CodeArray translateCode(DBT_Trace trace) {
    if (DBT_Options.debugRuntime) {
      report("Translating code for 0x" + Integer.toHexString(trace.pc));
    }
    trace.compile();
    VM_CompiledMethod cm = trace.getCurrentCompiledMethod();
    replaceCompiledTrace(cm, trace);
    return cm.getEntryCodeArray();
  }

  /*
   * 
   * /** Record a branch instruction
   */
  public void recordUncaughtBranch(int location, int destination, int code) {
    branchInfo.registerBranch(location, destination, code);
  }

  /**
   * Return as an integer the current instruction's address
   */
  public abstract int getCurrentInstructionAddress();

  /**
   * Sets the current instruction's address
   */
  public abstract void setCurrentInstructionAddress(int pc);

  /**
   * Return as an integer the current instruction's address
   */
  public abstract int getCurrentStackAddress();

  /**
   * Print the stack
   * 
   * @param words
   *          how many words to print from the stack
   */
  public void dumpStack(int words) {
    int stackPtr = getCurrentStackAddress();
    for (int i = 0; i < words; i++) {
      if ((i % 4) == 0) {
        if (i != 0) {
          System.out.println();
        }
        System.out.print("0x" + Integer.toHexString(stackPtr) + ":");
      }
      String hexValue = Integer.toHexString(memory.load32(stackPtr));
      System.out.print("   0x");
      for (int j = 0; j < (8 - hexValue.length()); j++) {
        System.out.print("0");
      }
      System.out.print(hexValue);
      stackPtr += 4;
    }
    System.out.println();
  }

  /**
   * Runtime loop, goes through the binary and looks in the Hashtable codeHash
   * to see if we have already translated/compiled this piece of code, if not it
   * is compiled. The compiled code is then run.
   */
  public void run() {
    if (DBT_Options.debugRuntime) {
      System.out.println("Main: run");
    }

    // The current block of compiled code.
    VM_CodeArray code;

    if (DBT_Options.debugPS) {
      System.out.println("***** INITIAL PROCESS SPACE *****\n" + this);
      System.out.println(this);
      // dumpStack(20);
    }

    if (DBT_Options.gdbStub == false) {
      try {
        // interpretFrom(); // Interpreter - experimental
        while (finished == false) {
          // Get the compiled code
          code = getCodeForPC(getCurrentInstructionAddress());
          // Run the compiled code.
          setCurrentInstructionAddress(DynamicCodeRunner.invokeCode(code, this));
        }
      } catch (BadInstructionException e) {
        System.out.println(e.toString());
      }
    } else {
      GDBStub gdbStub = new GDBStub(DBT_Options.gdbStubPort, getGDBTarget());
      gdbStub.run();
    }
  }

  /**
   * Entry point for system calls
   */
  public abstract void doSysCall();

  private static final String[] env = {
      "HOSTNAME=softwood",
      "PVM_RSH=/usr/bin/rsh",
      "HOST_JAVA_HOME=/home/amulocal/linux/appl/j2sdk1.4.2",
      "SHELL=/bin/bash",
      "TERM=xterm",
      "HISTSIZE=1000",
      "SSH_CLIENT=130.88.194.110 8380 22",
      "CVSROOT=/home/simgroup/cvsroot",
      "QTDIR=/usr/lib/qt-3.1",
      "SSH_TTY=/dev/pts/0",
      "RVM_HOST_CONFIG=/home/matleyr/cvs/rvm/config/i686-pc-linux-gnu.ManCS",
      "USER=matleyr",
      "LS_COLORS=no=00:fi=00:di=00;34:ln=00;36:pi=40;33:so=00;35:bd=40;33;01:cd=40;33;01:or=01;05;37;41:mi=01;05;37;41:ex=00;32:*.cmd=00;32:*.exe=00;32:*.com=00;32:*.btm=00;32:*.bat=00;32:*.sh=00;32:*.csh=00;32:*.tar=00;31:*.tgz=00;31:*.arj=00;31:*.taz=00;31:*.lzh=00;31:*.zip=00;31:*.z=00;31:*.Z=00;31:*.gz=00;31:*.bz2=00;31:*.bz=00;31:*.tz=00;31:*.rpm=00;31:*.cpio=00;31:*.jpg=00;35:*.gif=00;35:*.bmp=00;35:*.xbm=00;35:*.xpm=00;35:*.png=00;35:*.tif=00;35:",
      "XENVIRONMENT=/home/matleyr/.Xdefaults",
      "PVM_ROOT=/usr/share/pvm3",
      "CLASSPATH_ROOT=/home/matleyr/cvs/classpath",
      "PATH=/home/matleyr/bin:/usr/kerberos/bin:/usr/local/bin:/bin:/usr/bin:/usr/X11R6/bin:/opt/lib/j2re1.3.1/bin:/home/matleyr/cvs/rvm/bin:/home/matleyr/bin",
      "MAIL=/var/spool/mail/matleyr", "_=/bin/bash", "PWD=/home/matleyr/dhry",
      "INPUTRC=/etc/inputrc", "LANG=en_GB.iso88591",
      "LAMHELPFILE=/etc/lam/lam-helpfile",
      "SSH_ASKPASS=/usr/libexec/openssh/gnome-ssh-askpass",
      "CSHOME=matleyr@antigua.cs.man.ac.uk:/home/M03/cc/matleyr",
      "HOME=/home/matleyr", "SHLVL=1", "SIM=/home/simgroup/matleyr",
      "XPVM_ROOT=/usr/share/pvm3/xpvm", "RVM_ROOT=/home/matleyr/cvs",
      "LOGNAME=matleyr", "PRINTER=happy_duplex",
      "SSH_CONNECTION=130.88.194.110 2380 130.88.198.215 22",
      "LESSOPEN=|/usr/bin/lesspipe.sh %s", "RVM_BUILD=/tmp/RVMbuild",
      "DISPLAY=localhost:10.0",
      "RVM_TARGET_CONFIG=/home/matleyr/cvs/rvm/config/i686-pc-linux-gnu.ManCS",
      "G_BROKEN_FILENAMES=1" };

  /**
   * Method to return environment variables
   * 
   * @return an array of environment variable strings
   */
  protected String[] getEnvironmentVariables() {
    /*
     * Environment variables, exactly as on softwood. Not that the number 8380
     * in SSH_* varies.
     */
    return env;
    /*
     * if (!DBT_Options.loadEnv) { Process printenv =
     * Runtime.exec("/usr/bin/printenv"); InputStream variables = new
     * DataInputStream(printenv.getInputStream()); variables.readUTF(); }
     */
  }

  /**
   * Return an interface that allows GDB to read from this process
   */
  public abstract GDBTarget getGDBTarget();
}
