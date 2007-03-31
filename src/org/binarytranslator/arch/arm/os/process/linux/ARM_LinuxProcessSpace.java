package org.binarytranslator.arch.arm.os.process.linux;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.os.abi.linux.ARM_LinuxSystemCalls;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.generic.gdbstub.GDBTarget;
import org.binarytranslator.generic.os.abi.linux.LinuxStackInitializer;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCalls;
import org.binarytranslator.generic.os.loader.Loader;

public class ARM_LinuxProcessSpace extends ARM_ProcessSpace implements
    LinuxSystemCallGenerator {

  /**
   * A link to the current system call interface
   */
  private final LinuxSystemCalls sysCalls;

  /**
   * A re-used iterator that allows enumerating the argument of the current
   * system call
   */
  private final ARM_SyscallArgumentIterator syscallArgs;

  /**
   * The top of the stack
   */
  private static final int STACK_TOP = 0xC0000000;

  /**
   * The top of the bss segment
   */
  private int brk;

  public ARM_LinuxProcessSpace() {
    sysCalls = new ARM_LinuxSystemCalls(this);
    syscallArgs = new ARM_SyscallArgumentIterator(this);
  }

  @Override
  public void doSysCall() {
    sysCalls.doSysCall();
  }

  @Override
  public void initialise(Loader loader, int pc, int brk, String[] args) {
    registers.write(ARM_Registers.PC, pc);
    this.brk = brk;

    // initialize the stack
    int[] auxVector = { LinuxStackInitializer.AuxiliaryVectorType.AT_SYSINFO,
        0xffffe400, LinuxStackInitializer.AuxiliaryVectorType.AT_SYSINFO_EHDR,
        0xffffe000, LinuxStackInitializer.AuxiliaryVectorType.AT_HWCAP,
        0x78bfbff, LinuxStackInitializer.AuxiliaryVectorType.AT_PAGESZ, 0x1000,
        LinuxStackInitializer.AuxiliaryVectorType.AT_CLKTCK, 0x64,
        LinuxStackInitializer.AuxiliaryVectorType.AT_PHDR, 0xBADADD8E,
        LinuxStackInitializer.AuxiliaryVectorType.AT_PHNUM, 0xBAD2BAD2,
        LinuxStackInitializer.AuxiliaryVectorType.AT_BASE, 0x0,
        LinuxStackInitializer.AuxiliaryVectorType.AT_FLAGS, 0x0,
        LinuxStackInitializer.AuxiliaryVectorType.AT_ENTRY, pc,

        LinuxStackInitializer.AuxiliaryVectorType.AT_UID, DBT_Options.UID,
        LinuxStackInitializer.AuxiliaryVectorType.AT_EUID, DBT_Options.UID,
        LinuxStackInitializer.AuxiliaryVectorType.AT_GID, DBT_Options.GID,
        LinuxStackInitializer.AuxiliaryVectorType.AT_EGID, DBT_Options.GID,

        LinuxStackInitializer.AuxiliaryVectorType.AT_SECURE, 0,
        LinuxStackInitializer.AuxiliaryVectorType.AT_NULL, 0x0 };

    LinuxStackInitializer.stackInit(memory, STACK_TOP, args,
        getEnvironmentVariables(), auxVector);
  }

  public int getBrk() {
    return brk;
  }

  public int getSysCallNumber() {
    // TODO Auto-generated method stub
    return 0;
  }

  public CallArgumentIterator getSysCallArguments() {
    syscallArgs.reset();
    return syscallArgs;
  }

  public void setBrk(int address) {
    brk = address;
  }

  public void setSysCallError(int r) {
    // TODO Auto-generated method stub
  }

  public void setSysCallReturn(int r) {
    // TODO Auto-generated method stub
  }

  @Override
  public GDBTarget getGDBTarget() {
    // TODO Auto-generated method stub
    return null;
  }

}
