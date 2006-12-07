/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.ppc;

/** 
 * Interface to contain some of the constants used for ELF files, PPC
 * memory space and machine code translation.
 *
 * @author Richard Matley, Ian Rogers
 */
public interface PPC_Constants {
  /** 
   * Mask to get the low-order word from a long. 
   */
  long LW_MASK=0xffffffffL;

  /* Stuff related to my PPC ProcessSpace. PROT_* and MAP_* have same numerical values as in 
     include/asm-ppc/mman.h */

  /** 
   * The total number of 4kB pages in the process space memory array, 1M.
   */
  int TOTAL_PAGES=0x100000; 

  /**
   * The bitwise AND of this mask with a memory address results in the address of the start of
   * the page containing that location.
   */
  int PAGE_START=0xfffff000; 

  /**
   * The bitwise AND of this mask with a memory address results in the offset of the address
   * from the start of the page in which it is located.
   */
  int PAGE_OFFSET=0x00000fff;

  /**
   * Unsigned right shifting of an address by this value results in the number of the page containing
   * the address.
   */
  int PAGE_SHIFT=12; 

  /**
   * The size of the space available to a user process in pages. Kludge! The value of this in
   * bytes is 0x80000000, which Java treats as negative, hence preventing comparisons such as
   * (start + offset) > TASK_SIZE.
   */
  int TASK_SIZE_P=0x80000;

  /**
   * The address immediately below which the (downward growing) stack is located. See TASK_SIZE_P
   * for a note about the problems of this being treated as unsigned.
   */
  int STACK_TOP=0x80000000; 

  /**
   * Used in the prot argument to PPC_ProcessSpace.mmap to grant read permissions to the requested
   * memory.
   */
  int PROT_READ=0x1;

  /**
   * Used in the prot argument to PPC_ProcessSpace.mmap to grant write permissions to the requested
   * memory.
   */
  int PROT_WRITE=0x2;

  /**
   * Used in the prot argument to PPC_ProcessSpace.mmap to grant execute permissions to the requested
   * memory.
   */
  int PROT_EXEC=0x4; 

  /**
   * Equivalent to none of PROT_READ, PROT_WRITE and PROT_EXEC.
   */
  int PROT_NONE=0x0; 

  /**
   * Used in the flags argument to PPC_ProcessSpace.mmap to indicate that changes to the memory
   * are shared between multiple processes accessing it (some way beyond what we currently do).
   * Either this or MAP_PRIVATE must be specified.
   */
  int MAP_SHARED=0x01; 

  /**
   * Used in the flags argument to PPC_ProcessSpace.mmap to indicate that changes to the memory
   * are note shared between multiple processes accessing it.
   * Either this or MAP_SHARED must be specified.
   */
  int MAP_PRIVATE=0x02; 

  //      int MAP_TYPE=0x0f; // Mask for type of mapping.

  /**
   * Used in the flags argument to PPC_ProcessSpace.mmap to insist that the memory be allocated
   * at the specified start location; if enough space is not free at that location, the request
   * will fail. Without this flag, the memory will be allocated at an alternative location if
   * necessary.
   */
  int MAP_FIXED=0x10;

  /**
   * Used in the flags argument to PPC_ProcessSpace.mmap when the memory requested is not 
   * for the mapping of a file.
   */
  int MAP_ANONYMOUS=0x20; 

  //      int MAP_NORESERVE=0x40; // Don't reserve swap space.
  //int MAP_LOCKED=0x80;

  /**
   * Used in the flags argument to PPC_ProcessSpace.mmap to cause the allocated region to be expanded
   * downwards in memory (currently ignored).
   */
  int MAP_GROWSDOWN=0x0100;

  //      int MAP_DENYWRITE=0x0800;
  //      int MAP_EXECUTABLE=0x1000; // Mark as executable.


  /* ELF stuff. The comment before each block of values gives the name of the field for which the
     values are valid, firstly the ELF standard name, then the name in my Java code. */

  /**
   * The value of PPCELFHeader.byteOrder corresponding to little-endian ordering.
   */
  int ELFDATA2LSB = 1;

  /**
   * The value of PPCELFHeader.byteOrder corresponding to big-endian ordering.
   */
  int ELFDATA2MSB = 2;

  /**
   * The value of PPCELFHeader.fileType for a relocatable file.
   */
  int ET_REL = 1; 

  /**
   * The value of PPCELFHeader.fileType for an executable file.
   */
  int ET_EXEC = 2; 

  /**
   * The value of PPCELFHeader.fileType for a shared object file.
   */
  int ET_DYN = 3; 

  /**
   * Bit set in ELFProgramHeader.flags to indicate an executable segment.
   */
  int PF_X = 0x1;

  /**
   * Bit set in ELFProgramHeader.flags to indicate a writable segment.
   */
  int PF_W = 0x2;

  /**
   * Bit set in ELFProgramHeader.flags to indicate a readable segment.
   */
  int PF_R = 0x4;

  /**
   * Value of PPCELFHeader.archType for PowerPC.
   */
  int EM_PPC = 20;


  /**
   * Value of ELFProgramHeader.type for a loadable code/data segment.
   */
  int PT_LOAD = 1;
      
  /**
   * Value of ELFProgramHeader.type for a note segment.
   */      
  int PT_NOTE = 4;

  /**
   * The only correct value for PPCELFHeader.fileVersion and PPCELFHeader.headerVersion.
   */
  int EV_CURRENT = 1;


  /* For the auxiliary vector in the initial process stack. See 'System V Application Binary Interface
     PowerPC Processor Supplement' and 'Linux Standard Base Specification for PPC32 Architecture 1.3. */
      
  /**
   * Used in the auxiliary vector of the PPC process stack. 
   * See 'System V Application Binary Interface PowerPC Processor Supplement' and 
   * 'Linux Standard Base Specification for PPC32 Architecture 1.3' for further explanation.
   */
  int AT_NULL=0,
    AT_IGNORE=1,
    AT_EXECFD=2,
    AT_PHDR=3,
    AT_PHENT=4,
    AT_PHNUM=5,
    AT_PAGESZ=6,
    AT_BASE=7,
    AT_FLAGS=8,
    AT_ENTRY=9,
    AT_NOTELF=10,
    AT_UID=11,
    AT_EUID=12,
    AT_GID=13,
    AT_EGID=14,
    AT_PLATFORM=15,
    AT_HWCAP=16,
    AT_CLKTCK=17,
    AT_DCACHEBSIZE=19,
    AT_ICACHEBSIZE=20,
    AT_UCACHEBSIZE=21,
    AT_IGNOREPPC=22;

  /* Masks and shifts to extract parts of the PPC instruction. */

  /**
   * Unsigned right shift of a PPC instruction by this value results in the primary opcode.
   */
  int PRIM_OP_SHIFT=26; // Bits 0-5 No mask needed

  /**
   * Bitwise AND of a PPC instruction with this value results in the bits which give the
   * secondary opcode (where appropriate and usually with the Rc bit at the end).
   */
  int SEC_OP_MASK=0x7ff; // Bits 21-31 No shift needed

  /** Bitwise AND of a PPC instruction with this value, followed by unsigned right shift by
   * FIELD1_SHIFT results in the value in the field at bit positions 6-10.
   */
  int FIELD1_MASK=0x3E00000; // Bits 6-10

  /**
   * See FIELD1_MASK.
   */
  int FIELD1_SHIFT=21;

  /** Bitwise AND of a PPC instruction with this value, followed by unsigned right shift by
   * FIELD2_SHIFT results in the value in the field at bit positions 11-15.
   */
  int FIELD2_MASK=0x1F0000; // Bits 11-15

  /**
   * See FIELD2_MASK.
   */
  int FIELD2_SHIFT=16;


  /** Bitwise AND of a PPC instruction with this value, followed by unsigned right shift by
   * FIELD3_SHIFT results in the value in the field at bit positions 16-20.
   */
  int FIELD3_MASK=0xf800; // Bits 16-20

  /**
   * See FIELD3_MASK.
   */
  int FIELD3_SHIFT=11;

  /** Bitwise AND of a PPC instruction with this value, followed by unsigned right shift by
   * FIELD4_SHIFT results in the value in the field at bit positions 21-25.
   */
  int FIELD4_MASK=0x7c0; // Bits 21-25

  /**
   * See FIELD4_MASK.
   */
  int FIELD4_SHIFT=6;

  /** Bitwise AND of a PPC instruction with this value, followed by unsigned right shift by
   * FIELD5_SHIFT results in the value in the field at bit positions 26-30.
   */
  int FIELD5_MASK=0x3e; // Bits 26-30

  /**
   * See FIELD5_MASK.
   */
  int FIELD5_SHIFT=1;

  /* CR stuff */
  int CR0_LT=0x80000000; // Bit 0, negative result
  int CR0_GT=0x40000000; // Bit 1, positive result
  int CR0_EQ=0x20000000; // Bit 2, zero result
  int CR0_SO=0x10000000; // Bit 3, summary overflow

  /* CR single field */
  int CR_LT=0x8;
  int CR_GT=0x4;
  int CR_EQ=0x2;
  int CR_SO=0x1;

  /* 'fake' UID and GID */
  int UID=500;
  int GID=500;

  /* Values for use of integer registers to hold boolean values. */
  int TRUE=1;
  int FALSE=0;

  /* Error codes for syscalls. From /usr/include/asm-ppc/errno.h. */
  int ENOENT=2;
  int EEXIST=17;
  int EBADF=9;
  int EIO=5;
  int EACCES=13;
  int ENOSYS=38;
  int EFAULT=14;      /* Bad address */

  /* Flags for open syscall. These are from Linux's fcntl.h and are in bl**dy octal! */
  int O_RDONLY=00;
  int O_WRONLY=01;
  int O_RDWR=02;
  int O_CREAT=0100;
  int O_EXCL=0200;
  int O_TRUNC=01000;
  int O_APPEND=02000;
}
