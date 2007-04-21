package org.binarytranslator.arch.arm.os.process.linux;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.os.abi.linux.ARM_LinuxSystemCalls;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.arch.arm.os.process.linux.abi.Legacy;
import org.binarytranslator.generic.execution.GdbController.GdbTarget;
import org.binarytranslator.generic.os.abi.linux.LinuxStackInitializer;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCalls;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.loader.elf.ELF_Loader;

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
   * The top of the bss segment
   */
  private int brk;

  public ARM_LinuxProcessSpace() {
    sysCallGenerator = new Legacy(this);
    sysCalls = new ARM_LinuxSystemCalls(sysCallGenerator);
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
    this.brk = brk;

    // initialize the stack
    int[] auxVector = {//LinuxStackInitializer.AuxiliaryVectorType.AT_SYSINFO, 0xffffe400,
        //LinuxStackInitializer.AuxiliaryVectorType.AT_SYSINFO_EHDR, 0xffffe000,
        LinuxStackInitializer.AuxiliaryVectorType.AT_HWCAP, 0x78bfbff,
        LinuxStackInitializer.AuxiliaryVectorType.AT_PAGESZ, 0x1000,
        LinuxStackInitializer.AuxiliaryVectorType.AT_CLKTCK, 0x64,
        LinuxStackInitializer.AuxiliaryVectorType.AT_PHDR, ((ELF_Loader)loader).getProgramHeaderAddress(),
        LinuxStackInitializer.AuxiliaryVectorType.AT_PHNUM, ((ELF_Loader)loader).elfHeader.getNumberOfProgramSegmentHeaders(),
        LinuxStackInitializer.AuxiliaryVectorType.AT_BASE, 0x0,
        LinuxStackInitializer.AuxiliaryVectorType.AT_FLAGS, 0x0,
        LinuxStackInitializer.AuxiliaryVectorType.AT_ENTRY, pc,

        LinuxStackInitializer.AuxiliaryVectorType.AT_UID, DBT_Options.UID,
        LinuxStackInitializer.AuxiliaryVectorType.AT_EUID, DBT_Options.UID,
        LinuxStackInitializer.AuxiliaryVectorType.AT_GID, DBT_Options.GID,
        LinuxStackInitializer.AuxiliaryVectorType.AT_EGID, DBT_Options.GID,

        LinuxStackInitializer.AuxiliaryVectorType.AT_SECURE, 0,
        //LinuxStackInitializer.AuxiliaryVectorType.AT_PLATFORM, LinuxStackInitializer.AuxiliaryVectorType.STACK_TOP - getPlatformString().length,
        LinuxStackInitializer.AuxiliaryVectorType.AT_NULL, 0x0};

    registers.set(ARM_Registers.SP, LinuxStackInitializer.stackInit(memory, STACK_TOP, getEnvironmentVariables(), auxVector));
  }

  @Override
  public GdbTarget getGdbTarget() {
    return null;
  }

}
