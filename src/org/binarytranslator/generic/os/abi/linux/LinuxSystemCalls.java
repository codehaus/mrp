/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.abi.linux;

import java.io.*;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.memory.MemoryMapException;
import java.util.ArrayList;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Linux system call handling class
 */
abstract public class LinuxSystemCalls {
  /**
   * The source of the system calls
   */
  private LinuxSystemCallGenerator src;

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
   * List of (RandomAccessFile(s)) files currently open
   */
  private ArrayList files;
  /**
   * Convert integer file descriptor into Java RandomAccessFile
   */
  private RandomAccessFile getRAFile(int fd) {
    return (RandomAccessFile)files.get(fd);
  }
  /**
   * Append the given file to the files list returning the location
   */
  private int appendRAFile(RandomAccessFile raFile) {
    // replace an unused entry if possible
    for (int i=3; i < files.size(); i++) {
      if(files.get(i) == null) {
        files.set(i, raFile);
        return i;
      }
    }
    // not possible so append to end
    files.add(raFile);
    return files.size() - 1;
  }
  /**
   * Remove the file for the given file descriptor
   */
  private void removeRAFile(int fd) {
    // check descriptor is valid
    if((fd > 0) && (fd < files.size())) {
      // can we remove it from the end of the list?
      if (fd == (files.size() - 1)) {
        files.remove(fd);
      }
      else {
        // no. set it to null
        files.set(fd, null);
      }
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
    files = new ArrayList();
    files.add(System.in);
    files.add(System.out);
    files.add(System.err);
  }

  /**
   * Handle a system call
   * @param src the system call generator
   */
  public void doSysCall() {
    int sysCallNumber = src.getSysCallNumber();
    systemCallTable[sysCallNumber].doSysCall();
  }
  
  /**
   * Turn the given system call number into a string describing its
   * function
   */
  abstract public String sysCallToString(int syscall);

  /*
   * ABI constants
   */

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
    public final static int O_RDONLY=0x0;
    public final static int O_WRONLY=0x1;
    public final static int O_RDWR  =0x2;
    public final static int O_CREAT =0x40;
    public final static int O_EXCL  =0x80;
    public final static int O_TRUNC =0x200;
    public final static int O_APPEND=0x400;
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

    /** Don't use a file */
    public final static int MAP_ANONYMOUS=0x20;
  }
  /*
   * Local classes
   */

  /**
   * Define the system call interface
   */
  abstract class SystemCall {
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
      int[] args = src.getSysCallArguments(1);
      int status = args[0];

      System.exit(status);
    }
  }

  /**
   * Read from a file
   */
  public class SysRead extends SystemCall {
    public void doSysCall() {
      int[] args = src.getSysCallArguments(3);
      int fd = args[0];
      int buf = args[1];
      int count = args[2];

      if(fd == 0) { // read from stdin
        byte[] b = new byte[256];
        try {
          int len = System.in.read(b);
          for (int i=0; i < len; i++) {
            src.memoryStore32(buf + i, b[i]);
          }
          src.setSysCallReturn(len);
        }
        catch(IOException e) {
          src.setSysCallError(errno.EIO);
          //cr |= PPC_errno.CR0_SO;
        }
      }
      else {
        RandomAccessFile raFile = getRAFile(fd);        
        if(raFile == null) {
          src.setSysCallError(errno.EBADF);
        } else {
          // Where to put the data
          int addr = buf;
          // Copy from file into buffer.
          int b = 0; // Number of bytes read.          
          int i;
          try {
            while((b < count) && ((i = raFile.read()) != -1)) {
              byte by = (byte)i;              
              b++;
              src.memoryStore8(addr++, by);
            }
            src.setSysCallReturn(b); // Return number of bytes read.
          }
          catch(IOException e) {
            src.setSysCallReturn(errno.EIO);
          }
        }
      }
    }
  }

  /**
   * Write to a file
   */
  public class SysWrite extends SystemCall {
    public void doSysCall() {
      int[] args = src.getSysCallArguments(3);
      int fd = args[0];
      int buf = args[1];
      int count = args[2];

      if(fd == 1) { // stdout       
        for(int c = 0 ; c < count; c++) {
          System.out.print((char) src.memoryLoad8(buf + c));
        }
        src.setSysCallReturn(count);       
      } else if(fd == 2) { // sterr
        for(int c = 0 ; c < count ; c++) {
          System.err.print((char) src.memoryLoad8(buf + c));
        }
        src.setSysCallReturn(count);
      } else {
        // Check that fd is a valid file descriptor.
        RandomAccessFile raFile = getRAFile(fd);
        if(raFile == null) {
          src.setSysCallReturn(errno.EBADF);
        } else {
          // Where to get the data.
          int addr = buf;
      
          // Copy from buffer into file.
          int b; //  Number of bytes written.
          byte by;
      
          try {
            for(b = 1 ; b <= count ; b++) {
              by = src.memoryLoad8(addr++);
              raFile.write(by);
            }         
            // Return number of bytes written, having accounted for b
            // being incremented once too many at the end of the
            // writing
            src.setSysCallReturn(b-1);
          }
          catch(IOException e) {
            src.setSysCallReturn(errno.EIO);
          }
        }    
      }
    }
  }

  /**
   * Write data into multiple buffers
   */
  public class SysWriteV extends SystemCall {
    public void doSysCall() {
      int[] args = src.getSysCallArguments(3);
      int fd = args[0];
      int vector = args[1];
      int count = args[2];

      if((fd == 1)||(fd == 2)) { // stdout || stderr
        PrintStream out = (fd == 1) ? System.out : System.err;
        int base = src.memoryLoad32(vector);
        int len  = src.memoryLoad32(vector+4);
        int currentVector = 0;
        int curVectorPos = 0;
        for(int c = 0 ; c < count; c++) {
          if(curVectorPos == len) {
            currentVector++;
            base = src.memoryLoad32(vector+(currentVector*8));
            len  = src.memoryLoad32(vector+(currentVector*8)+4);
            curVectorPos = 0;
          }
          System.out.print((char) src.memoryLoad8(base + curVectorPos));
          curVectorPos++;
        }
        src.setSysCallReturn(count);       
      } else {
        throw new Error("TODO: "+ fd);
      }
    }
  }

  public class SysOpen extends SystemCall {
    public void doSysCall() {
      int[] args = src.getSysCallArguments(2);
      int pathname = args[0];
      int flags = args[1];

      // Examine the flags argument and open read or read-write
      // accordingly. args[0] points to the file name.   
      String fileName = src.memoryReadString(pathname);
   
      // Create a File object so we can test for the existance and
      // properties of the file.
      File testFile = new File(fileName);
   
      if(((flags & fcntl.O_CREAT) != 0) &&
         ((flags & fcntl.O_EXCL) != 0) &&
         testFile.exists()) {
        // O_CREAT specified. If O_EXCL also specified, we require
        // that the file does not already exist
        src.setSysCallError(errno.EEXIST);
      }
      else if(((flags & fcntl.O_CREAT) != 0) && !testFile.exists()) {
        // O_CREAT not specified. We require that the file exists.    
        src.setSysCallError(errno.ENOENT);
      }
      else {
        // we have not found an error, so we go ahead and try to open the file  
        int fileDesc;
       
        try {
          RandomAccessFile raFile;
          if((flags & 0x3) == fcntl.O_RDONLY) {
            raFile = new RandomAccessFile(fileName, "r");
          }
          else {
            // NB Java RandomAccessFile has no write only
            raFile = new RandomAccessFile(fileName, "rw"); 
          }
          src.setSysCallReturn(appendRAFile(raFile));
        }
        catch(FileNotFoundException e) {
          throw new Error("File not found: " + fileName + " " + e);
        }
      }
      // NOT YET HANDLING ALL THE flags OPTIONS (IW have included
      // TRUNC & APPEND but not properly!)
      if((flags & ~(fcntl.O_WRONLY | fcntl.O_RDWR | fcntl.O_CREAT | fcntl.O_EXCL | fcntl.O_TRUNC | fcntl.O_APPEND)) != 0)
        throw new Error("Not yet implemented option to sys_open. " + Integer.toString(flags,8));
    }
  }
    
  public class SysClose extends SystemCall {
    public void doSysCall() {
      int[] args = src.getSysCallArguments(1);
      int fd = args[0];
      RandomAccessFile raFile = getRAFile(fd);
      // Check that fd is a valid file descriptor
      if(raFile == null) {
        src.setSysCallError(errno.EBADF);
      } else {
        try {
          raFile.close();
      
          src.setSysCallReturn(0); // return success code
        } catch(IOException e) {
          src.setSysCallReturn(errno.EIO);
          //cr |= PPC_errno.CR0_SO;
        }
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
      int args[] = src.getSysCallArguments(1);
      if(args[0] == 0)  {          
        // Request for the current top of bss.
        src.setSysCallReturn(src.getBrk());          
      }
      else {
        // Changing the value.
        src.setBrk(args[0]);
      }
    }
  }

  public class SysFstat64 extends SystemCall {
    public void doSysCall() {
    }
  }
    
  public class SysFcntl64 extends SystemCall {
    public void doSysCall() {
      // This is complicated so fudge it for now.
      int[] args = src.getSysCallArguments(2);

      int fd = args[0];
      int cmd = args[1];
                              
      if( ((fd == 0) | (fd == 1) | (fd == 2)) & (cmd == 1) )
        src.setSysCallReturn(0);
      else
        throw new Error("Unrecognised system call.");
    }
  }

  public class SysUname extends SystemCall {
    public void doSysCall() {
      // Simple uname support
      int[] args = src.getSysCallArguments(1);
      if (args[0] != 0) {
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
        src.memoryWriteString (args[0],     getSysName()); // sysname
        src.memoryWriteString (args[0]+65,  hostName);     // nodename
        src.memoryWriteString (args[0]+130, getRelease()); // release
        src.memoryWriteString (args[0]+195, getVersion()); // version
        src.memoryWriteString (args[0]+260, getMachine()); // machine
        src.memoryWriteString (args[0]+325, domainName);   // __domainname
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
      int[] args = src.getSysCallArguments(6);
      int start = args[0];
      int length = args[1];
      int prot = args[2];
      int flags = args[3];
      int fd = args[4];
      int offset = args[5];
      if((flags & mman.MAP_ANONYMOUS) != 0 ) {
        try {
          src.setSysCallReturn(src.memoryMap(start, length,
                                             (prot & mman.PROT_READ) != 0,
                                             (prot & mman.PROT_WRITE) != 0,
                                             (prot & mman.PROT_EXEC) != 0));
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
      int[] args = src.getSysCallArguments(2);
      int start = args[0];
      int length = args[1];
      throw new Error("TODO!");
      //src.setSysCallReturn(src.munmap(start, length));
    }
  }

  public class SysExitGroup extends SystemCall {
    public void doSysCall() {
      // For now, equivalent to SysExit
      System.exit(src.getSysCallArguments(1)[0]);
    }
  }
}
