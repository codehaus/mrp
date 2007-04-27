/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.abi.linux;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.memory.Memory;
import org.binarytranslator.generic.memory.MemoryMapException;
import org.binarytranslator.generic.os.abi.linux.files.ConsoleIn;
import org.binarytranslator.generic.os.abi.linux.files.ConsoleOut;
import org.binarytranslator.generic.os.abi.linux.files.HostFile;
import org.binarytranslator.generic.os.abi.linux.files.OpenFile;
import org.binarytranslator.generic.os.abi.linux.files.OpenFileList;
import org.binarytranslator.generic.os.abi.linux.files.ReadableFile;
import org.binarytranslator.generic.os.abi.linux.files.WriteableFile;
import org.binarytranslator.generic.os.abi.linux.files.OpenFileList.InvalidFileDescriptor;
import org.binarytranslator.generic.os.abi.linux.LinuxConstants.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Linux system call handling class
 */
abstract public class LinuxSystemCalls {
  /**
   * The source of the system calls
   */
  protected LinuxSystemCallGenerator src;

  /** Allows access to the system call's arguments */
  protected LinuxSystemCallGenerator.CallArgumentIterator arguments;

  /** Allows access to a number of operating-system specific structures. */
  protected LinuxStructureFactory structures;

  /** List of currently opened files. */
  private OpenFileList openFiles;

  /**
   * Maximum number of system calls
   */
  private static final int MAX_SYSCALLS = 294;

  /**
   * Array to de-multiplex Linux system calls. NB we will have to
   * handle IA32 call gates entry at some point
   */
  protected SystemCall systemCallTable[] = new SystemCall[MAX_SYSCALLS];

  /**
   * The name for this system given by uname
   */
  protected String getSysName() {
    return "Linux";
  }

  /**
   * The release for this system given by uname
   */
  protected String getRelease() {
    return "2.6.13";
  }

  /**
   * The version for this system given by uname
   */
  protected String getVersion() {
    return "#1";
  }

  /**
   * The machine for this system given by uname
   */
  protected abstract String getMachine();

  /**
   * Load an ASCIIZ string from the memory of the system call
   * generator and return it as a Java String.
   * @param address where to read
   * @return the String read
   */
  private String memoryReadString(int address) {
    Memory m = src.getProcessSpace().memory;

    StringBuffer str = new StringBuffer();
    char c;

    while ((c = (char) m.loadUnsigned8(address++)) != 0)
      str.append(c);

    return str.toString();
  }

  /**
   * Store an ASCIIZ string to the memory of the system call generator
   * @param address where to read
   * @param data the String to write
   */
  public void memoryWriteString(int address, String data) {
    Memory m = src.getProcessSpace().memory;

    if (data != null) {
      for (int i = 0; i < data.length(); i++) {
        m.store8(address + i, (byte) data.charAt(i));
      }

      m.store8(address + data.length(), (byte) 0);
    }
  }

  /**
   * Constructor
   */
  public LinuxSystemCalls(LinuxSystemCallGenerator src) {
    this.src = src;
    UnknownSystemCall USC = new UnknownSystemCall();
    for (int i = 0; i < MAX_SYSCALLS; i++) {
      systemCallTable[i] = USC;
    }

    openFiles = new OpenFileList();

    if (openFiles.open(new ConsoleIn(), 0) != 0
        || openFiles.open(new ConsoleOut(System.out), 1) != 1
        || openFiles.open(new ConsoleOut(System.err), 2) != 2) {

      throw new RuntimeException(
          "File descriptors for standard streams could not be assigned correctly.");
    }

    structures = new LinuxStructureFactory();
  }

  /**
   * Handle a system call
   * @param src the system call generator
   */
  public void doSysCall() {
    int sysCallNumber = src.getSysCallNumber();
    System.err.println("Syscall " + sysCallToString(sysCallNumber));
    arguments = src.getSysCallArguments();
    systemCallTable[sysCallNumber].doSysCall();
  }

  /**
   * Turn the given system call number into a string describing its
   * function
   */
  abstract public String sysCallToString(int syscall);

  /** Base class for all system calls. */
  public abstract class SystemCall {
    
    /** Handle a system call */
    public abstract void doSysCall();
  }

  /**
   * Unknown System Call
   */
  private class UnknownSystemCall extends SystemCall {
    /**
     * Handle a system call
     */
    public void doSysCall() {
      if (!DBT_Options.unimplementedSystemCallsFatal) {
        src.setSysCallError(errno.ENOSYS);
      } else {
        throw new Error("System Call "
            + sysCallToString(src.getSysCallNumber()) + " Unknown");
      }
    }
  }

  /**
   * Exit system call
   */
  public class SysExit extends SystemCall {
    public void doSysCall() {
      int status = arguments.nextInt();
      System.exit(status);
    }
  }

  /**
   * Read from a file
   */
  public class SysRead extends SystemCall {
    public void doSysCall() {
      int fd = arguments.nextInt();
      int buf = arguments.nextInt();
      int count = arguments.nextInt();

      Memory mem = src.getProcessSpace().memory;

      try {
        //get the file from the file descriptor table
        ReadableFile file = openFiles.getRead(fd);
        byte[] buffer = new byte[count];

        //try to fill the buffer from the file
        int readBytes = file.read(buffer);

        //copy the filled buffer into the memory
        for (int i = 0; i < readBytes; i++)
          mem.store8(buf++, buffer[i]);

        src.setSysCallReturn(readBytes);
      } catch (InvalidFileDescriptor e) {
        src.setSysCallError(errno.EBADF);
      } catch (Exception e) {
        src.setSysCallError(errno.EIO);
      }
    }
  }

  /**
   * Write to a file
   */
  public class SysWrite extends SystemCall {
    public void doSysCall() {
      int fd = arguments.nextInt();
      int buf = arguments.nextInt();
      int count = arguments.nextInt();

      Memory mem = src.getProcessSpace().memory;

      try {
        //get the file from the file descriptor table
        WriteableFile file = openFiles.getWrite(fd);

        //load the supplied buffer from memory
        byte[] buffer = new byte[count];
        for (int i = 0; i < count; i++)
          buffer[i] = (byte) mem.loadUnsigned8(buf++);

        //write that buffer to the file
        file.write(buffer);
        src.setSysCallReturn(count);
      } catch (InvalidFileDescriptor e) {
        src.setSysCallError(errno.EBADF);
      } catch (Exception e) {
        src.setSysCallError(errno.EIO);
      }
    }
  }

  /**
   * Write data into multiple buffers
   */
  public class SysWriteV extends SystemCall {
    public void doSysCall() {
      int fd = arguments.nextInt();
      int vector = arguments.nextInt();
      int count = arguments.nextInt();
      Memory mem = src.getProcessSpace().memory;

      try {
        //get the file from the file descriptor table
        WriteableFile file = openFiles.getWrite(fd);
        int bytesWritten = 0;

        //for each of the supplied buffers...
        for (int n = 0; n < count; n++) {
          //get the base address & length for the current buffer
          int base = mem.load32(vector);
          int len = mem.load32(vector + 4);
          vector += 8;

          //read the buffer from memory
          byte[] buffer = new byte[len];
          for (int i = 0; i < count; i++)
            buffer[i] = (byte) mem.loadUnsigned8(base + i);

          //and write it to the file
          file.write(buffer);
          bytesWritten += len;
        }

        src.setSysCallReturn(bytesWritten);
      } catch (InvalidFileDescriptor e) {
        src.setSysCallError(errno.EBADF);
      } catch (Exception e) {
        src.setSysCallError(errno.EIO);
      }
    }
  }

  public class SysOpen extends SystemCall {
    public void doSysCall() {
      int pathname = arguments.nextInt();
      int flags = arguments.nextInt();

      // Examine the flags argument and open read or read-write
      // accordingly. args[0] points to the file name.   
      String fileName = memoryReadString(pathname);

      // Create a File object so we can test for the existance and
      // properties of the file.
      File testFile = new File(fileName);

      if (((flags & fcntl.O_CREAT) != 0) && ((flags & fcntl.O_EXCL) != 0)
          && testFile.exists()) {
        // O_CREAT specified. If O_EXCL also specified, we require
        // that the file does not already exist
        src.setSysCallError(errno.EEXIST);
        return;
      } else if (((flags & fcntl.O_CREAT) != 0) && !testFile.exists()) {
        // O_CREAT not specified. We require that the file exists.    
        src.setSysCallError(errno.ENOENT);
        return;
      }

      // we have not found an error, so we go ahead and try to open the file  
      try {
        RandomAccessFile raFile;
        int fd;

        if ((flags & 0x3) == fcntl.O_RDONLY) {
          raFile = new RandomAccessFile(fileName, "r");
          fd = openFiles.open(HostFile.forReading(raFile));
        } else {
          // NB Java RandomAccessFile has no write only
          raFile = new RandomAccessFile(fileName, "rw");
          fd = openFiles.open(HostFile.forWriting(raFile));
        }
        src.setSysCallReturn(fd);
      } catch (FileNotFoundException e) {
        System.err.println("Open tried to open non-existent file: " + fileName);
        src.setSysCallError(errno.ENOENT);
        return;
      }

      // NOT YET HANDLING ALL THE flags OPTIONS (IW have included
      // TRUNC & APPEND but not properly!)
      if ((flags & ~(fcntl.O_WRONLY | fcntl.O_RDWR | fcntl.O_CREAT
          | fcntl.O_EXCL | fcntl.O_TRUNC | fcntl.O_APPEND | fcntl.O_NOCTTY | fcntl.O_NONBLOCK)) != 0) {
        throw new Error("Not yet implemented option to sys_open. 0"
            + Integer.toString(flags, 8)
            + " flag 0"
            + Integer.toString(flags
                & ~(fcntl.O_WRONLY | fcntl.O_RDWR | fcntl.O_CREAT
                    | fcntl.O_EXCL | fcntl.O_TRUNC | fcntl.O_APPEND
                    | fcntl.O_NOCTTY | fcntl.O_NONBLOCK), 8));
      }
    }
  }

  public class SysClose extends SystemCall {
    public void doSysCall() {
      int fd = arguments.nextInt();

      try {
        OpenFile f = openFiles.get(fd);
        f.close();
      } catch (InvalidFileDescriptor e) {
        src.setSysCallError(errno.EBADF);
      } catch (IOException e) {
        src.setSysCallError(errno.EIO);
      }
    }
  }

  public class SysGetEUID extends SystemCall {
    public void doSysCall() {
      src.setSysCallReturn(DBT_Options.UID);
    }
  }

  public class SysGetUID extends SystemCall {
    public void doSysCall() {
      src.setSysCallReturn(DBT_Options.UID);
    }
  }

  public class SysGetEGID extends SystemCall {
    public void doSysCall() {
      src.setSysCallReturn(DBT_Options.GID);
    }
  }

  public class SysGetGID extends SystemCall {
    public void doSysCall() {
      src.setSysCallReturn(DBT_Options.GID);
    }
  }

  public class SysBrk extends SystemCall {
    public void doSysCall() {
      int brk = arguments.nextInt();
      if (brk != 0) {
        // Request to set the current top of bss.        
        src.setBrk(brk);
      }
      src.setSysCallReturn(src.getBrk());
    }
  }

  public class SysFstat64 extends SystemCall {
    public void doSysCall() {
      int fd = arguments.nextInt();

      LinuxStructureFactory.stat64 buf = structures.new_stat64();

      if (fd == 0 || fd == 1 || fd == 2) {
        buf.st_mode = 0x2180;
        buf.st_rdev = (short) 0x8800;
        buf.__st_ino = buf.st_ino = 2;
        buf.st_blksize = 0x400;
      } else
        throw new RuntimeException("Unimplemented system call.");

      buf.write(src.getProcessSpace().memory, arguments.nextInt());
      src.setSysCallReturn(0);
    }
  }

  public class SysStat64 extends SystemCall {

    @Override
    public void doSysCall() {
      int ptrFilename = arguments.nextInt();
      int ptrStruct64 = arguments.nextInt();

      String filename = memoryReadString(ptrFilename);

      if (DBT_Options.debugSyscallMore)
        System.err.println("Stat64() denies existance of file: " + filename);
      src.setSysCallError(errno.ENOENT);
    }

  }

  public class SysFcntl64 extends SystemCall {
    public void doSysCall() {
      // This is complicated so fudge it for now.
      int fd = arguments.nextInt();
      int cmd = arguments.nextInt();

      if (((fd == 0) || (fd == 1) || (fd == 2)) && (cmd == 1))
        src.setSysCallReturn(0);
      else
        throw new Error("Unrecognised system call.");
    }
  }

  public class SysUname extends SystemCall {
    public void doSysCall() {
      // Simple uname support
      int addr = arguments.nextInt();

      if (addr != 0) {
        String localhostString, domainName, hostName;
        try {
          InetAddress localhost = InetAddress.getLocalHost();
          localhostString = localhost.getHostName();
        } catch (UnknownHostException e) {
          localhostString = "localhost.localdomain";
        }
        int index = localhostString.indexOf('.');
        if (index == -1) {
          domainName = null;
          hostName = localhostString;
        } else {
          domainName = localhostString.substring(index + 1);
          hostName = localhostString.substring(0, index);
        }

        // Fill in utsname struct - see /usr/include/sys/utsname.h
        memoryWriteString(addr, getSysName()); // sysname
        memoryWriteString(addr + 65, hostName); // nodename
        memoryWriteString(addr + 130, getRelease()); // release
        memoryWriteString(addr + 195, getVersion()); // version
        memoryWriteString(addr + 260, getMachine()); // machine
        memoryWriteString(addr + 325, domainName); // __domainname
        src.setSysCallReturn(0);
      } else {
        // attempt to write to address 0
        src.setSysCallError(errno.EFAULT);
      }
    }
  }

  public class SysMmap extends SystemCall {
    public void doSysCall() {

      int addr = arguments.nextInt();
      int length = arguments.nextInt();
      int prot = arguments.nextInt();
      int flags = arguments.nextInt();
      int fd = arguments.nextInt();
      int offset = arguments.nextInt();

      if ((flags & mman.MAP_ANONYMOUS) != 0) {

        if ((flags & mman.MAP_SHARED) != 0)
          throw new Error("Mapping of shared pages is currently not supported.");

        //Anonymous private mappings just request memory.
        //Ignore the file descriptor and offset, map the required amount of memory and
        //return the address of the mapped memory;
        addr = 0;

        try {
          Memory mem = src.getProcessSpace().memory;
          addr = mem.map(addr, length, (prot & mman.PROT_READ) != 0,
              (prot & mman.PROT_WRITE) != 0, (prot & mman.PROT_EXEC) != 0);

          src.setSysCallReturn(addr);
        } catch (MemoryMapException e) {
          throw new Error("Error in mmap", e);
        }
      } else {
        throw new Error("Non-anonymous sys_mmap not yet implemented.");
      }
    }
  }

  public class SysMunmap extends SystemCall {
    public void doSysCall() {

      int start = arguments.nextInt();
      int length = arguments.nextInt();

      src.getProcessSpace().memory.unmap(start, length);
      src.setSysCallReturn(0);
    }
  }

  public class SysExitGroup extends SystemCall {
    public void doSysCall() {
      // For now, equivalent to SysExit
      System.exit(arguments.nextInt());
    }
  }
}
