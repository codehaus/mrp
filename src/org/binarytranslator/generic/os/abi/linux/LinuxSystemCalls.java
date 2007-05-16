/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.abi.linux;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.memory.Memory;
import org.binarytranslator.generic.memory.MemoryMapException;
import org.binarytranslator.generic.os.abi.linux.LinuxConstants.errno;
import org.binarytranslator.generic.os.abi.linux.LinuxConstants.fcntl;
import org.binarytranslator.generic.os.abi.linux.LinuxConstants.mman;
import org.binarytranslator.generic.os.abi.linux.files.ConsoleIn;
import org.binarytranslator.generic.os.abi.linux.files.ConsoleOut;
import org.binarytranslator.generic.os.abi.linux.files.OpenFile;
import org.binarytranslator.generic.os.abi.linux.files.OpenFileList;
import org.binarytranslator.generic.os.abi.linux.files.ReadableFile;
import org.binarytranslator.generic.os.abi.linux.files.WriteableFile;
import org.binarytranslator.generic.os.abi.linux.files.OpenFileList.InvalidFileDescriptor;
import org.binarytranslator.generic.os.abi.linux.filesystem.FileProvider;
import org.binarytranslator.generic.os.abi.linux.filesystem.HostFileSystem;
import org.binarytranslator.generic.os.abi.linux.filesystem.ReadonlyFilesystem;
import org.binarytranslator.generic.os.abi.linux.filesystem.TempFileSystem;
import org.binarytranslator.generic.os.abi.linux.filesystem.FileProvider.FileMode;

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
  protected OpenFileList openFiles;
  
  /** List of currently opened files. */
  protected FileProvider filesystem;
  
  /** The top of the bss segment */
  protected int brk;

  /**
   * Maximum number of system calls
   */
  protected static final int MAX_SYSCALLS = 294;

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
  protected String memoryReadString(int address) {
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
  protected void memoryWriteString(int address, String data) {
    Memory m = src.getProcessSpace().memory;

    if (data != null) {
      for (int i = 0; i < data.length(); i++) {
        m.store8(address + i, (byte) data.charAt(i));
      }

      m.store8(address + data.length(), (byte) 0);
    }
  }
  
  /**
   * Sets up the linux operating space
   *      
   * @param brk
   * the initial value for the top of BSS
   */
  public void initialize(int brk) {
    LinuxSystemCalls.this.brk = brk;
    src.getProcessSpace().memory.ensureMapped(brk, brk+1);
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

    if (openFiles.add(new ConsoleIn(), 0) != 0
        || openFiles.add(new ConsoleOut(System.out), 1) != 1
        || openFiles.add(new ConsoleOut(System.err), 2) != 2) {

      throw new RuntimeException(
          "File descriptors for standard streams could not be assigned correctly.");
    }

    structures = new LinuxStructureFactory();
    filesystem = buildFileSystem();
  }
  
  /**
   * This method creates a virtual file system for this linux host. Override it, to create a specific file systems
   * for an architecture. 
   * 
   * The standard implementation mounts the host's file system readonly but enables writes to the host's temp directory.
   * @return
   *  A {@link FileProvider} that will be used to access the file system for this host.
   */
  protected FileProvider buildFileSystem() {
    FileProvider fs = new HostFileSystem(null);
    fs = new ReadonlyFilesystem(fs); 
    fs = new TempFileSystem(fs);
    return fs;
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
      
      if (DBT_Options.debugSyscall)
        System.err.println("Program tries to open: " + fileName);
      
      FileMode mode;

      //shall we create the target file?
      if ((flags & fcntl.O_CREAT) != 0) {
        //We want to create a file. This also implies that we're gonna write to it
        mode = FileMode.WriteCreate;
        if (DBT.VerifyAssertions) DBT._assert((flags & 0x3) != fcntl.O_RDONLY);
        
        //Yes, we want to create that file. Do we demand that it does not yet exist?
        if ((flags & fcntl.O_EXCL) != 0) {
          //check if it does not exist by trying to open it
          OpenFile testfile = filesystem.openFile(fileName, FileMode.Read);
          
          if (testfile != null) {
            //the file we're trying to create already exists. With O_EXCL, this is an error condition.
            src.setSysCallError(errno.EEXIST);
            try {
              testfile.close();
            } catch (IOException e) {}
            
            return;
          }
        }
      }
      else {
        //we shall not create the file. Check if we're supposed to open it for reading, though
        if ((flags & 0x3) == fcntl.O_RDONLY)
          mode = FileMode.Read;
        else
          mode = FileMode.Write;
      }
      
      OpenFile file = filesystem.openFile(fileName, mode);
      
      //did we successfully open the file?
      if (file == null) {
        //if not, return with an error
        src.setSysCallError(errno.ENOENT);
        return;
      }
      
      //otherwise add it to the list of open files and return a handle to the file
      int fd = openFiles.add(file);
      src.setSysCallReturn(fd);

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
      int newBrk = arguments.nextInt();
      if (newBrk != 0) {
        // Request to set the current top of bss.        
        brk = newBrk;
        src.getProcessSpace().memory.ensureMapped(brk, brk+1);
      }
      
      src.setSysCallReturn(brk);
    }
  }

  public class SysFstat64 extends SystemCall {
    public void doSysCall() {
      int fd = arguments.nextInt();
      int structAddr = arguments.nextInt();

      LinuxStructureFactory.stat64 buf = structures.new_stat64();

      if (fd == 0 || fd == 1 || fd == 2) {
        buf.st_mode = 0x2180;
        buf.st_rdev = (short) 0x8800;
        buf.__st_ino = buf.st_ino = 2;
        buf.st_blksize = 0x400;
      } else {
        
        try {
          //get the file from the file descriptor table
          OpenFile file = openFiles.get(fd);
          OpenFile.Info info = file.getFileInfo();

          buf.st_size = info.size;
        } catch (InvalidFileDescriptor e) {
          src.setSysCallError(errno.EBADF);
        } catch (Exception e) {
          src.setSysCallError(errno.EIO);
        }
      }

      buf.write(src.getProcessSpace().memory, structAddr);
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
