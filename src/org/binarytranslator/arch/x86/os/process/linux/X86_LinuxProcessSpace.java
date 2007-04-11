/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.x86.os.process.linux;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.x86.os.abi.linux.X86_LinuxSystemCalls;
import org.binarytranslator.arch.x86.os.process.X86_ProcessSpace;
import org.binarytranslator.arch.x86.os.process.X86_Registers;
import org.binarytranslator.generic.os.abi.linux.LinuxStackInitializer;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCalls;
import org.binarytranslator.generic.os.loader.Loader;

/**
 * Linux specific parts of the process
 */
public class X86_LinuxProcessSpace extends X86_ProcessSpace implements LinuxSystemCallGenerator {
  /**
   * System calls object for handling system calls generated by this process
   */
  final LinuxSystemCalls syscalls;
  
  /**
   * Allows uniform access to the arguments of a system call. We cache this object for reuse.
   */
  private final X86_LinuxSyscallArgumentIterator syscallArgs;

  /**
   * The top of the bss segment
   */
  private int brk;

  /**
   * The top of the stack
   */
  private static final int STACK_TOP = 0xC0000000;

  /**
   * Constructor
   */
  public X86_LinuxProcessSpace(Loader loader) {
    syscallArgs = new X86_LinuxSyscallArgumentIterator(this);
    syscalls = new X86_LinuxSystemCalls(this);
  }

  /**
   * Initialise the process space
   * @param loader the loader that's created the process space
   * @param pc the entry point
   * @param brk the initial value for the top of BSS
   * @param args command line arguments
   */
  public void initialise(Loader loader, int pc, int brk) {
    registers.eip = pc;
    this.brk = brk;
    registers.writeGP32(X86_Registers.ESP, initialiseStack(loader, pc));
  }

  /**
   * Initialise the stack
   */
  private int initialiseStack(Loader loader, int pc) {
    int[] auxVector = {LinuxStackInitializer.AuxiliaryVectorType.AT_SYSINFO, 0xffffe400,
                       LinuxStackInitializer.AuxiliaryVectorType.AT_SYSINFO_EHDR, 0xffffe000,
                       LinuxStackInitializer.AuxiliaryVectorType.AT_HWCAP, 0x78bfbff,
                       LinuxStackInitializer.AuxiliaryVectorType.AT_PAGESZ, 0x1000,
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
                       //LinuxStackInitializer.AuxiliaryVectorType.AT_PLATFORM, LinuxStackInitializer.AuxiliaryVectorType.STACK_TOP - getPlatformString().length,
                       LinuxStackInitializer.AuxiliaryVectorType.AT_NULL, 0x0};

    return LinuxStackInitializer.stackInit(memory, STACK_TOP, getEnvironmentVariables(), auxVector);
  }

  /**
   * Entry point for system calls
   */
  public void doSysCall() {
    syscalls.doSysCall();
  }

  /**
   * Return the system call number
   */
  public int getSysCallNumber() {
    return registers.readGP32(X86_Registers.EAX);
  }

  /**
   * Returns an interface to read the arguments of a system call.
   */
  public CallArgumentIterator getSysCallArguments() {
    syscallArgs.reset();
    return syscallArgs;
  }

  public void setSysCallReturn(int r) {
    registers.writeGP32(X86_Registers.EAX, r);
  }

  public void setSysCallError(int r) {    
    registers.writeGP32(X86_Registers.EAX, -r);
  }

  /**
   * Get the top of the BSS segment (the heap that reside below the
   * stack in memory)
   * @return top of BSS segment
   */
  public int getBrk() {
    return brk;
  }
  /**
   * Set the top of the BSS segment (the heap that reside below the
   * stack in memory)
   * @param address new top of BSS segment
   */
  public void setBrk(int address) {
    brk = address;
  }

  public void setStackPtr(int ptr) {}

  public int[] getAuxVector() { //ELF_Header header, ELF_ProgramHeaderTable programHeaders) {
    /*
    return new int[] {
      32, 0xffffe400,
      33, 0xffffe000,
      ELF_Constants.AT_HWCAP, 0x78bfbff,
      ELF_Constants.AT_PAGESZ, 0x1000,
      ELF_Constants.AT_CLKTCK, 0x64,
      ELF_Constants.AT_PHDR, header.e_phoff - programHeaders.getSegment(0).p_offset + programHeaders.getSegment(0).p_vaddr,
      ELF_Constants.AT_PHNUM, header.e_phnum,
      ELF_Constants.AT_BASE, 0x0,
      ELF_Constants.AT_FLAGS, 0x0,
      ELF_Constants.AT_ENTRY, header.e_entry,
      ELF_Constants.AT_UID, ELF_Constants.UID, 
      ELF_Constants.AT_EUID, ELF_Constants.UID,
      ELF_Constants.AT_GID, ELF_Constants.GID, 
      ELF_Constants.AT_EGID, ELF_Constants.GID,
      ELF_Constants.AT_SECURE, 0,
      ELF_Constants.AT_PLATFORM, ELF_Constants.STACK_TOP - getPlatformString().length,
      ELF_Constants.AT_NULL, 0x0,  
    };
    */
    throw new Error("TODO");
  }

  public byte[] getPlatformString() {
    return new byte[] {'\0', '6', '8', '6', 'i'};
  }
}
