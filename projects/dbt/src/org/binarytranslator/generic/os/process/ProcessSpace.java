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
package org.binarytranslator.generic.os.process;

import java.io.IOException;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.ppc.os.process.PPC_ProcessSpace;
import org.binarytranslator.arch.x86.os.process.X86_ProcessSpace;
import org.binarytranslator.generic.branchprofile.BranchProfile;
import org.binarytranslator.generic.branchprofile.BranchProfile.BranchType;
import org.binarytranslator.generic.decoder.CodeTranslator;
import org.binarytranslator.generic.decoder.CodeCache;
import org.binarytranslator.generic.decoder.Disassembler;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.execution.GdbController.GdbTarget;
import org.binarytranslator.generic.memory.Memory;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;

/**
 * A process space encapsulates a running process. This superclass contains non
 * operating and architecture specific details of the process.
 */
public abstract class ProcessSpace {

  /** A record of branches to guide translation */
  public final BranchProfile branchInfo;

  /** Has a system call been called to terminate the process? */
  public boolean finished = false;
  
  /** A cache of code traces within this process space. */
  public final CodeCache codeCache;

  /**
   * The memory for the process. As this is user mode code, it is a virtual
   * address space
   */
  public Memory memory;

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

  /**
   * Create an optimizing compiler HIR code generator suitable for a particular
   * architecture
   * 
   * @param context
   *          the generation context for the HIR generation
   * @param trace 
   * @return a HIR generator
   */
  public abstract CodeTranslator createTranslator(GenerationContext context, DBT_Trace trace) throws UnsupportedOperationException ;
  
  /** 
   * Returns an instance of {@link Interpreter} that can be used to interpret instructions
   * for this process space. 
   * */
  public Interpreter createInterpreter() throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }
  
  public Disassembler createDisassembler() throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }
  
  /** Return a string disassembly of the instuction at the given address*/
  public String disassembleInstruction(int pc) throws UnsupportedOperationException {
    return createDisassembler().disassemble(pc).asString();
  }

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
   
    Loader.ISA isa = loader.getISA();
    report("Found " + isa.toString() + " ISA.");
    
    switch (isa) {
      case X86:
        result = X86_ProcessSpace.createProcessSpaceFromBinary(loader);
        break;
        
      case PPC:
        result = PPC_ProcessSpace.createProcessSpaceFromBinary(loader);
        break;
        
      case ARM:
        result = ARM_ProcessSpace.createProcessSpaceFromBinary(loader);
        break;
        
      default:
        throw new UnsupportedOperationException("Binary of "+ isa + " architecture is unsupported");
    }
    
    return result;
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
   */
  public abstract void initialise(Loader loader);

  /**
   * Constructor
   */
  protected ProcessSpace() {
    branchInfo = new BranchProfile();
    codeCache = new CodeCache();
  }

  /** Record a branch instruction */
  public void recordUncaughtBranch(int location, int destination, int code, int returnAddress) {
    
    if (code < 0 || code > BranchType.values().length)
      throw new Error("Invalid uncaught branch type: " + code);

    BranchType type = BranchType.values()[code];
    
    switch (type) {
    case CALL:
      branchInfo.registerCallSite(location, destination, returnAddress);
      return;
      
    case RETURN:
      branchInfo.registerReturnSite(location, destination);
      return;
      
    default:
      branchInfo.registerDynamicBranchSite(location, destination);
    }
  }

  /** Return as an integer the current instruction's address */
  public abstract int getCurrentInstructionAddress();

  /** Sets the current instruction's address */
  public abstract void setCurrentInstructionAddress(int pc);

  /** Return as an integer the current instruction's address*/
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
  }

  /**
   * Return an interface that allows GDB to read from this process
   */
  public abstract GdbTarget getGdbTarget();
}
