package org.binarytranslator.arch.arm.os.process.linux;

import org.binarytranslator.arch.arm.os.abi.linux.ARM_LinuxSystemCalls;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.arch.arm.os.process.linux.abi.Legacy;
import org.binarytranslator.generic.execution.GdbController.GdbTarget;
import org.binarytranslator.generic.os.abi.linux.LinuxStackInitializer;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCalls;
import org.binarytranslator.generic.os.loader.Loader;

public class ARM_LinuxProcessSpace extends ARM_ProcessSpace {

  /**
   * A link to the current system call interface
   */
  private final LinuxSystemCalls sysCalls;
  
  /** As ARM provides two different linux ABIs, we put the system call specifics into a separate class.*/
  private final LinuxSystemCallGenerator sysCallGenerator;

  /**
   * The top of the stack
   */
  private static final int STACK_TOP = 0xC0000000;

  /**
   * Auxiliary vector
   */
  private int[] auxVector;

  public ARM_LinuxProcessSpace() {
    sysCallGenerator = new Legacy(this);
    sysCalls = new ARM_LinuxSystemCalls(this, sysCallGenerator);
  }

  @Override
  public void doSysCall() {
    sysCalls.doSysCall();
    
    //simulate a return from the call
    //TODO: This actually assumes that we're calling from ARM32
    registers.set(ARM_Registers.PC, getCurrentInstructionAddress() + 4);
  }

  @Override
  public void initialise(Loader loader, int pc, int brk) {
    registers.set(ARM_Registers.PC, pc);
    sysCalls.initialize(brk);

    // initialize the stack
    auxVector = new int[] {
        LinuxStackInitializer.AuxiliaryVectorType.AT_HWCAP, 0x97,
        LinuxStackInitializer.AuxiliaryVectorType.AT_PAGESZ, 4096, //0x100
        LinuxStackInitializer.AuxiliaryVectorType.AT_CLKTCK, 0x17,
        LinuxStackInitializer.AuxiliaryVectorType.AT_PHDR, 0x8034,
        LinuxStackInitializer.AuxiliaryVectorType.AT_PHENT, 0x20,
        LinuxStackInitializer.AuxiliaryVectorType.AT_PHNUM, 0x6,
        LinuxStackInitializer.AuxiliaryVectorType.AT_BASE, 0x40000000,
        LinuxStackInitializer.AuxiliaryVectorType.AT_FLAGS, 0x0,
        LinuxStackInitializer.AuxiliaryVectorType.AT_ENTRY, 0x82b4,
        LinuxStackInitializer.AuxiliaryVectorType.AT_UID, 0x0, 
        LinuxStackInitializer.AuxiliaryVectorType.AT_EUID, 0x0, 
        LinuxStackInitializer.AuxiliaryVectorType.AT_GID, 0x0, 
        LinuxStackInitializer.AuxiliaryVectorType.AT_EGID, 0x0,
        LinuxStackInitializer.AuxiliaryVectorType.AT_PLATFORM, 0xbffffecd };

    registers.set(ARM_Registers.SP, LinuxStackInitializer.stackInit(memory, STACK_TOP, getEnvironmentVariables(), auxVector));
  }
  
  @Override
  protected String[] getEnvironmentVariables() {
    return new String[]   { "PWD=/root",
                            "PS1=\\h:\\w\\$", 
                            "USER=root",
                            "MAIL=/var/mail/root",
                            "LANG=C",
                            "SSH_CLIENT=130.88.199.8 54342 22",
                            "LOGNAME=root",
                            "SHLVL=1",
                            "SHELL=/bin/bash",
                            "HOME=/root",
                            "TERM=xterm",
                            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/bin/X11",
                            "SSH_TTY=/dev/pts/1",
                            "_=/usr/bin/env" };
  }
  
  public void dumpStack() {
    //grab the current frame pointer
    int fp = registers.get(ARM_Registers.FP);
    
    //print the current position
    System.out.println("PC: 0x" + Integer.toHexString(registers.get(ARM_Registers.PC)));
    
    //we might be in a leaf function which did not create a stack frame. Check that by
    //comparing the current link register with the one saved on the first stack frame
    int saved_lr = memory.load32(fp - 4);
    int processor_lr = registers.get(ARM_Registers.LR);
    
    if (saved_lr != processor_lr) {
      //we are in a leaf function that did not generate a stack frame. Print out the function address
      System.out.println("Called from 0x" + Integer.toHexString(processor_lr - 4) + " (Function did not create a stack frame).");
    }
    
    do {
      saved_lr = memory.load32(fp - 4); //load the link register, so we know where we're called from
      fp = memory.load32(fp - 12);    //load the previous frame pointer
      System.out.println("Called from 0x" + Integer.toHexString(saved_lr - 4));
    }
    while (fp != 0);
  }

  @Override
  public GdbTarget getGdbTarget() {
    return null;
  }

  public int[] getAuxVector() {
    return auxVector;
  }
}
