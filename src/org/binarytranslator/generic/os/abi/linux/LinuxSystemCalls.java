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
  protected SystemCall systemCallTable[];

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
    systemCallTable = new SystemCall[MAX_SYSCALLS];
    UnknownSystemCall USC = new UnknownSystemCall();
    for(int i=0; i < MAX_SYSCALLS; i++) {
      systemCallTable[i] = USC;
    }
    
    openFiles = new OpenFileList();
    
    if (openFiles.open(new ConsoleIn(), 0) != 0 ||
        openFiles.open(new ConsoleOut(System.out), 1) != 1 ||
        openFiles.open(new ConsoleOut(System.err), 2) != 2) {
      
      throw new RuntimeException("File descriptors for standard streams could not be assigned correctly.");
    }

    structures = new LinuxStructureFactory();
  }

  /**
   * Handle a system call
   * @param src the system call generator
   */
  public void doSysCall() {
    int sysCallNumber = src.getSysCallNumber();
    System.err.println("Syscall "+ sysCallToString(sysCallNumber));
    arguments = src.getSysCallArguments();
    systemCallTable[sysCallNumber].doSysCall();
  }
  
  /**
   * Turn the given system call number into a string describing its
   * function
   */
  abstract public String sysCallToString(int syscall);

  /**
   * Class capturing errno constants
   */
  private final static class errno {
    /** Operation not permitted */
    final static int EPERM=1;
    /** No such file or directory */
    final static int ENOENT=2;
    /** No such process */
    final static int ESRCH=3;
    /** Interrupted system call */
    final static int EINTR=4;
    /** I/O error */
    final static int EIO=5;
    /** No such device or address */
    final static int ENXIO=6;
    /** Argument list too long */
    final static int E2BIG=7;
    /** Exec format error */
    final static int ENOEXEC=8;
    /** Bad file number */
    final static int EBADF=9;
    /** No child processes */
    final static int ECHILD=10;
    /** Try again */
    final static int EAGAIN=11;
    /** Out of memory */
    final static int ENOMEM=12;
    /** Permission denied */
    final static int EACCES=13;
    /** Bad address */
    final static int EFAULT=14;
    /** Block device required */
    final static int ENOTBLK=15;
    /** Device or resource busy */
    final static int EBUSY=16;
    /** File exists */
    final static int EEXIST=17;
    /** Cross-device link */
    final static int EXDEV=18;
    /** No such device */
    final static int ENODEV=19;
    /** Not a directory */
    final static int ENOTDIR=20;
    /** Is a directory */
    final static int EISDIR=21;
    /** Invalid argument */
    final static int EINVAL=22;
    /** File table overflow */
    final static int ENFILE=23;
    /** Too many open files */
    final static int EMFILE=24;
    /** Not a typewriter */
    final static int ENOTTY=25;
    /** Text file busy */
    final static int ETXTBSY=26;
    /** File too large */
    final static int EFBIG=27;
    /** No space left on device */
    final static int ENOSPC=28;
    /** Illegal seek */
    final static int ESPIPE=29;
    /** Read-only file system */
    final static int EROFS=30;
    /** Too many links */
    final static int EMLINK=31;
    /** Broken pipe */
    final static int EPIPE=32;
    /** Math argument out of domain of func */
    final static int EDOM=33;
    /** Math result not representable */
    final static int ERANGE=34;

    /** Resource deadlock would occur */
    final static int EDEADLK=35;
    final static int EDEADLOCK=EDEADLK;
    /** File name too long */
    final static int ENAMETOOLONG=36;
    /** No record locks available */
    final static int ENOLCK=37;
    /** Function not implemented */
    final static int ENOSYS=38;
    /** Directory not empty */
    final static int ENOTEMPTY=39;
    /** Too many symbolic links encountered */
    final static int ELOOP=40;
    /** Operation would block */
    final static int EWOULDBLOCK=EAGAIN;
    /** No message of desired type */
    final static int ENOMSG=42;
    /** Identifier removed */
    final static int EIDRM=43;
    /** Channel number out of range */
    final static int ECHRNG=44;
    /** Level 2 not synchronized */
    final static int EL2NSYNC=45;
    /** Level 3 halted */
    final static int EL3HLT=46;
    /** Level 3 reset */
    final static int EL3RST=47;
    /** Link number out of range */
    final static int ELNRNG=48;
    /** Protocol driver not attached */
    final static int EUNATCH=49;
    /** No CSI structure available */
    final static int ENOCSI=50;
    /** Level 2 halted */
    final static int EL2HLT=51;
    /** Invalid exchange */
    final static int EBADE=52;
    /** Invalid request descriptor */
    final static int EBADR=53;
    /** Exchange full */
    final static int EXFULL=54;
    /** No anode */
    final static int ENOANO=55;
    /** Invalid request code */
    final static int EBADRQC=56;
    /** Invalid slot */
    final static int EBADSLT=57;

    /** Bad font file format */
    final static int EBFONT=59;
    /** Device not a stream */
    final static int ENOSTR=60;
    /** No data available */
    final static int ENODATA=61;
    /** Timer expired */
    final static int ETIME=62;
    /** Out of streams resources */
    final static int ENOSR=63;
    /** Machine is not on the network */
    final static int ENONET=64;
    /** Package not installed */
    final static int ENOPKG=65;
    /** Object is remote */
    final static int EREMOTE=66;
    /** Link has been severed */
    final static int ENOLINK=67;
    /** Advertise error */
    final static int EADV=68;
    /** Srmount error */
    final static int ESRMNT=69;
    /** Communication error on send */
    final static int ECOMM=70;
    /** Protocol error */
    final static int EPROTO=71;
    /** Multihop attempted */
    final static int EMULTIHOP=72;
    /** RFS specific error */
    final static int EDOTDOT=73;
    /** Not a data message */
    final static int EBADMSG=74;
    /** Value too large for defined data type */
    final static int EOVERFLOW=75;
    /** Name not unique on network */
    final static int ENOTUNIQ=76;
    /** File descriptor in bad state */
    final static int EBADFD=77;
    /** Remote address changed */
    final static int EREMCHG=78;
    /** Can not access a needed shared library */
    final static int ELIBACC=79;
    /** Accessing a corrupted shared library */
    final static int ELIBBAD=80;
    /** .lib section in a.out corrupted */
    final static int ELIBSCN=81;
    /** Attempting to link in too many shared libraries */
    final static int ELIBMAX=82;
    /** Cannot exec a shared library directly */
    final static int ELIBEXEC=83;
    /** Illegal byte sequence */
    final static int EILSEQ=84;
    /** Interrupted system call should be restarted */
    final static int ERESTART=85;
    /** Streams pipe error */
    final static int ESTRPIPE=86;
    /** Too many users */
    final static int EUSERS=87;
    /** Socket operation on non-socket */
    final static int ENOTSOCK=88;
    /** Destination address required */
    final static int EDESTADDRREQ=89;
    /** Message too long */
    final static int EMSGSIZE=90;
    /** Protocol wrong type for socket */
    final static int EPROTOTYPE=91;
    /** Protocol not available */
    final static int ENOPROTOOPT=92;
    /** Protocol not supported */
    final static int EPROTONOSUPPORT=93;
    /** Socket type not supported */
    final static int ESOCKTNOSUPPORT=94;
    /** Operation not supported on transport endpoint */
    final static int EOPNOTSUPP=95;
    /** Protocol family not supported */
    final static int EPFNOSUPPORT=96;
    /** Address family not supported by protocol */
    final static int EAFNOSUPPORT=97;
    /** Address already in use */
    final static int EADDRINUSE=98;
    /** Cannot assign requested address */
    final static int EADDRNOTAVAIL=99;
    /** Network is down */
    final static int ENETDOWN=100;
    /** Network is unreachable */
    final static int ENETUNREACH=101;
    /** Network dropped connection because of reset */
    final static int ENETRESET=102;
    /** Software caused connection abort */
    final static int ECONNABORTED=103;
    /** Connection reset by peer */
    final static int ECONNRESET=104;
    /** No buffer space available */
    final static int ENOBUFS=105;
    /** Transport endpoint is already connected */
    final static int EISCONN=106;
    /** Transport endpoint is not connected */
    final static int ENOTCONN=107;
    /** Cannot send after transport endpoint shutdown */
    final static int ESHUTDOWN=108;
    /** Too many references: cannot splice */
    final static int ETOOMANYREFS=109;
    /** Connection timed out */
    final static int ETIMEDOUT=110;
    /** Connection refused */
    final static int ECONNREFUSED=111;
    /** Host is down */
    final static int EHOSTDOWN=112;
    /** No route to host */
    final static int EHOSTUNREACH=113;
    /** Operation already in progress */
    final static int EALREADY=114;
    /** Operation now in progress */
    final static int EINPROGRESS=115;
    /** Stale NFS file handle */
    final static int ESTALE=116;
    /** Structure needs cleaning */
    final static int EUCLEAN=117;
    /** Not a XENIX named type file */
    final static int ENOTNAM=118;
    /** No XENIX semaphores available */
    final static int ENAVAIL=119;
    /** Is a named type file */
    final static int EISNAM=120;
    /** Remote I/O error */
    final static int EREMOTEIO=121;
    /** Quota exceeded */
    final static int EDQUOT=122;

    /** No medium found */
    final static int ENOMEDIUM=123;
    /** Wrong medium type */
    final static int EMEDIUMTYPE=124;
    /** Operation Canceled */
    final static int ECANCELED=125;
    /** Required key not available */
    final static int ENOKEY=126;
    /** Key has expired */
    final static int EKEYEXPIRED=127;
    /** Key has been revoked */
    final static int EKEYREVOKED=128;
    /** Key was rejected by service */
    final static int EKEYREJECTED=129;

    /** Owner died */
    final static int EOWNERDEAD=130;
    /** State not recoverable */
    final static int ENOTRECOVERABLE=131;
  }

  /**
   * Class capturing file control (fcntl) constants
   */
  private final static class fcntl {
    public final static int O_RDONLY   =     0;
    public final static int O_WRONLY   =     1;
    public final static int O_RDWR     =     2;
    public final static int O_CREAT    =  0100;
    public final static int O_EXCL     =  0200;
    public final static int O_NOCTTY   =  0400;
    public final static int O_TRUNC    = 01000;
    public final static int O_APPEND   = 02000;
    public final static int O_NONBLOCK = 04000;
  }

  /**
   * Class capturing memory management constants
   */
  private final static class mman {
    /** Page can be read */
    public final static int PROT_READ=0x1;
    /** Page can be written */
    public final static int PROT_WRITE=0x2;
    /** Page can be executed */
    public final static int PROT_EXEC=0x4;
    /** Map pages that are shared between processes */
    public final static int MAP_SHARED = 0x1;
    /** Map pages without sharing them */
    public final static int MAP_PRIVATE = 0x2;
    /** Don't use a file */
    public final static int MAP_ANONYMOUS=0x20;
  }

  /**
   * Define the system call interface
   */
  public abstract class SystemCall {
    /**
     * Handle a system call
     */
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
      }
      else {
        throw new Error("System Call " + sysCallToString(src.getSysCallNumber()) + " Unknown");
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
      }
      catch (InvalidFileDescriptor e) {
        src.setSysCallError(errno.EBADF);
      }
      catch (Exception e) {
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
          buffer[i] = (byte)mem.loadUnsigned8(buf++);
        
        //write that buffer to the file
        file.write(buffer);
        src.setSysCallReturn(count);
      }
      catch (InvalidFileDescriptor e) {
        src.setSysCallError(errno.EBADF);
      }
      catch (Exception e) {
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
          int len  = mem.load32(vector + 4);
          vector += 8;
          
          //read the buffer from memory
          byte[] buffer = new byte[len];
          for (int i = 0; i < count; i++)
            buffer[i] = (byte)mem.loadUnsigned8(base + i);
          
          //and write it to the file
          file.write(buffer);
          bytesWritten += len;
        }
        
        src.setSysCallReturn(bytesWritten);
      }
      catch (InvalidFileDescriptor e) {
        src.setSysCallError(errno.EBADF);
      }
      catch (Exception e) {
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
   
      if(((flags & fcntl.O_CREAT) != 0) &&
         ((flags & fcntl.O_EXCL) != 0) &&
         testFile.exists()) {
        // O_CREAT specified. If O_EXCL also specified, we require
        // that the file does not already exist
        src.setSysCallError(errno.EEXIST);
        return;
      }
      else if(((flags & fcntl.O_CREAT) != 0) && !testFile.exists()) {
        // O_CREAT not specified. We require that the file exists.    
        src.setSysCallError(errno.ENOENT);
        return;
      }

      // we have not found an error, so we go ahead and try to open the file  
      try {
        RandomAccessFile raFile;
        int fd;
        
        if((flags & 0x3) == fcntl.O_RDONLY) {
          raFile = new RandomAccessFile(fileName, "r");
          fd = openFiles.open( HostFile.forReading(raFile) );
        }
        else {
          // NB Java RandomAccessFile has no write only
          raFile = new RandomAccessFile(fileName, "rw"); 
          fd = openFiles.open( HostFile.forWriting(raFile) );
        }
        src.setSysCallReturn(fd);
      }
      catch(FileNotFoundException e) {
        System.err.println("Open tried to open non-existent file: " + fileName);
        src.setSysCallError(errno.ENOENT);
        return;
      }

      // NOT YET HANDLING ALL THE flags OPTIONS (IW have included
      // TRUNC & APPEND but not properly!)
      if((flags & ~(fcntl.O_WRONLY | fcntl.O_RDWR | fcntl.O_CREAT | fcntl.O_EXCL | fcntl.O_TRUNC |
          fcntl.O_APPEND | fcntl.O_NOCTTY | fcntl.O_NONBLOCK)) != 0) {
        throw new Error("Not yet implemented option to sys_open. 0" + Integer.toString(flags,8) +
            " flag 0" + Integer.toString(flags & ~(fcntl.O_WRONLY | fcntl.O_RDWR | fcntl.O_CREAT |
                fcntl.O_EXCL | fcntl.O_TRUNC | fcntl.O_APPEND | fcntl.O_NOCTTY | fcntl.O_NONBLOCK),8));
      }
    }
  }
    
  public class SysClose extends SystemCall {
    public void doSysCall() {
      int fd = arguments.nextInt();
      
      try {
        OpenFile f = openFiles.get(fd);
        f.close();
      }
      catch (InvalidFileDescriptor e) {
        src.setSysCallError(errno.EBADF);
      }
      catch (IOException e) {
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
      if(brk != 0)  {          
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
        buf.st_rdev = (short)0x8800;
        buf.__st_ino = buf.st_ino = 2;
        buf.st_blksize = 0x400;
      }
      else 
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
      
      if (DBT_Options.debugSyscallMore) System.err.println("Stat64() denies existance of file: " + filename);
      src.setSysCallError(errno.ENOENT);
    }
    
  }
    
  public class SysFcntl64 extends SystemCall {
    public void doSysCall() {
      // This is complicated so fudge it for now.
      int fd = arguments.nextInt();
      int cmd = arguments.nextInt();
                              
      if( ((fd == 0) || (fd == 1) || (fd == 2)) && (cmd == 1) )
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
        }
        catch (UnknownHostException e) {
          localhostString = "localhost.localdomain";
        }
        int index = localhostString.indexOf('.');
        if (index == -1) {
          domainName = null;
          hostName = localhostString;
        }
        else {
          domainName = localhostString.substring(index + 1);
          hostName = localhostString.substring(0,index);
        }
        
        // Fill in utsname struct - see /usr/include/sys/utsname.h
        memoryWriteString (addr,     getSysName()); // sysname
        memoryWriteString (addr+65,  hostName);     // nodename
        memoryWriteString (addr+130, getRelease()); // release
        memoryWriteString (addr+195, getVersion()); // version
        memoryWriteString (addr+260, getMachine()); // machine
        memoryWriteString (addr+325, domainName);   // __domainname
        src.setSysCallReturn(0);
      }
      else {
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
      
      if((flags & mman.MAP_ANONYMOUS) != 0 ) {
        
        if ((flags & mman.MAP_SHARED) != 0)
          throw new Error("Mapping of shared pages is currently not supported.");
        
        //Anonymous private mappings just request memory.
        //Ignore the file descriptor and offset, map the required amount of memory and
        //return the address of the mapped memory;
        addr = 0;
        
        try {
          Memory mem = src.getProcessSpace().memory;
          addr = mem.map(addr, length,
                        (prot & mman.PROT_READ) != 0,
                        (prot & mman.PROT_WRITE) != 0,
                        (prot & mman.PROT_EXEC) != 0);
   
          src.setSysCallReturn(addr);
        }
        catch (MemoryMapException e) {
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
