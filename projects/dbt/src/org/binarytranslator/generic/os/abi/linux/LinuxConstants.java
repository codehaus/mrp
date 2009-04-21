package org.binarytranslator.generic.os.abi.linux;

interface LinuxConstants {
  /**
   * Class capturing errno constants
   */
  static interface errno {
    /** Operation not permitted */
    final static int EPERM = 1;

    /** No such file or directory */
    final static int ENOENT = 2;

    /** No such process */
    final static int ESRCH = 3;

    /** Interrupted system call */
    final static int EINTR = 4;

    /** I/O error */
    final static int EIO = 5;

    /** No such device or address */
    final static int ENXIO = 6;

    /** Argument list too long */
    final static int E2BIG = 7;

    /** Exec format error */
    final static int ENOEXEC = 8;

    /** Bad file number */
    final static int EBADF = 9;

    /** No child processes */
    final static int ECHILD = 10;

    /** Try again */
    final static int EAGAIN = 11;

    /** Out of memory */
    final static int ENOMEM = 12;

    /** Permission denied */
    final static int EACCES = 13;

    /** Bad address */
    final static int EFAULT = 14;

    /** Block device required */
    final static int ENOTBLK = 15;

    /** Device or resource busy */
    final static int EBUSY = 16;

    /** File exists */
    final static int EEXIST = 17;

    /** Cross-device link */
    final static int EXDEV = 18;

    /** No such device */
    final static int ENODEV = 19;

    /** Not a directory */
    final static int ENOTDIR = 20;

    /** Is a directory */
    final static int EISDIR = 21;

    /** Invalid argument */
    final static int EINVAL = 22;

    /** File table overflow */
    final static int ENFILE = 23;

    /** Too many open files */
    final static int EMFILE = 24;

    /** Not a typewriter */
    final static int ENOTTY = 25;

    /** Text file busy */
    final static int ETXTBSY = 26;

    /** File too large */
    final static int EFBIG = 27;

    /** No space left on device */
    final static int ENOSPC = 28;

    /** Illegal seek */
    final static int ESPIPE = 29;

    /** Read-only file system */
    final static int EROFS = 30;

    /** Too many links */
    final static int EMLINK = 31;

    /** Broken pipe */
    final static int EPIPE = 32;

    /** Math argument out of domain of func */
    final static int EDOM = 33;

    /** Math result not representable */
    final static int ERANGE = 34;

    /** Resource deadlock would occur */
    final static int EDEADLK = 35;

    final static int EDEADLOCK = EDEADLK;

    /** File name too long */
    final static int ENAMETOOLONG = 36;

    /** No record locks available */
    final static int ENOLCK = 37;

    /** Function not implemented */
    final static int ENOSYS = 38;

    /** Directory not empty */
    final static int ENOTEMPTY = 39;

    /** Too many symbolic links encountered */
    final static int ELOOP = 40;

    /** Operation would block */
    final static int EWOULDBLOCK = EAGAIN;

    /** No message of desired type */
    final static int ENOMSG = 42;

    /** Identifier removed */
    final static int EIDRM = 43;

    /** Channel number out of range */
    final static int ECHRNG = 44;

    /** Level 2 not synchronized */
    final static int EL2NSYNC = 45;

    /** Level 3 halted */
    final static int EL3HLT = 46;

    /** Level 3 reset */
    final static int EL3RST = 47;

    /** Link number out of range */
    final static int ELNRNG = 48;

    /** Protocol driver not attached */
    final static int EUNATCH = 49;

    /** No CSI structure available */
    final static int ENOCSI = 50;

    /** Level 2 halted */
    final static int EL2HLT = 51;

    /** Invalid exchange */
    final static int EBADE = 52;

    /** Invalid request descriptor */
    final static int EBADR = 53;

    /** Exchange full */
    final static int EXFULL = 54;

    /** No anode */
    final static int ENOANO = 55;

    /** Invalid request code */
    final static int EBADRQC = 56;

    /** Invalid slot */
    final static int EBADSLT = 57;

    /** Bad font file format */
    final static int EBFONT = 59;

    /** Device not a stream */
    final static int ENOSTR = 60;

    /** No data available */
    final static int ENODATA = 61;

    /** Timer expired */
    final static int ETIME = 62;

    /** Out of streams resources */
    final static int ENOSR = 63;

    /** Machine is not on the network */
    final static int ENONET = 64;

    /** Package not installed */
    final static int ENOPKG = 65;

    /** Object is remote */
    final static int EREMOTE = 66;

    /** Link has been severed */
    final static int ENOLINK = 67;

    /** Advertise error */
    final static int EADV = 68;

    /** Srmount error */
    final static int ESRMNT = 69;

    /** Communication error on send */
    final static int ECOMM = 70;

    /** Protocol error */
    final static int EPROTO = 71;

    /** Multihop attempted */
    final static int EMULTIHOP = 72;

    /** RFS specific error */
    final static int EDOTDOT = 73;

    /** Not a data message */
    final static int EBADMSG = 74;

    /** Value too large for defined data type */
    final static int EOVERFLOW = 75;

    /** Name not unique on network */
    final static int ENOTUNIQ = 76;

    /** File descriptor in bad state */
    final static int EBADFD = 77;

    /** Remote address changed */
    final static int EREMCHG = 78;

    /** Can not access a needed shared library */
    final static int ELIBACC = 79;

    /** Accessing a corrupted shared library */
    final static int ELIBBAD = 80;

    /** .lib section in a.out corrupted */
    final static int ELIBSCN = 81;

    /** Attempting to link in too many shared libraries */
    final static int ELIBMAX = 82;

    /** Cannot exec a shared library directly */
    final static int ELIBEXEC = 83;

    /** Illegal byte sequence */
    final static int EILSEQ = 84;

    /** Interrupted system call should be restarted */
    final static int ERESTART = 85;

    /** Streams pipe error */
    final static int ESTRPIPE = 86;

    /** Too many users */
    final static int EUSERS = 87;

    /** Socket operation on non-socket */
    final static int ENOTSOCK = 88;

    /** Destination address required */
    final static int EDESTADDRREQ = 89;

    /** Message too long */
    final static int EMSGSIZE = 90;

    /** Protocol wrong type for socket */
    final static int EPROTOTYPE = 91;

    /** Protocol not available */
    final static int ENOPROTOOPT = 92;

    /** Protocol not supported */
    final static int EPROTONOSUPPORT = 93;

    /** Socket type not supported */
    final static int ESOCKTNOSUPPORT = 94;

    /** Operation not supported on transport endpoint */
    final static int EOPNOTSUPP = 95;

    /** Protocol family not supported */
    final static int EPFNOSUPPORT = 96;

    /** Address family not supported by protocol */
    final static int EAFNOSUPPORT = 97;

    /** Address already in use */
    final static int EADDRINUSE = 98;

    /** Cannot assign requested address */
    final static int EADDRNOTAVAIL = 99;

    /** Network is down */
    final static int ENETDOWN = 100;

    /** Network is unreachable */
    final static int ENETUNREACH = 101;

    /** Network dropped connection because of reset */
    final static int ENETRESET = 102;

    /** Software caused connection abort */
    final static int ECONNABORTED = 103;

    /** Connection reset by peer */
    final static int ECONNRESET = 104;

    /** No buffer space available */
    final static int ENOBUFS = 105;

    /** Transport endpoint is already connected */
    final static int EISCONN = 106;

    /** Transport endpoint is not connected */
    final static int ENOTCONN = 107;

    /** Cannot send after transport endpoint shutdown */
    final static int ESHUTDOWN = 108;

    /** Too many references: cannot splice */
    final static int ETOOMANYREFS = 109;

    /** Connection timed out */
    final static int ETIMEDOUT = 110;

    /** Connection refused */
    final static int ECONNREFUSED = 111;

    /** Host is down */
    final static int EHOSTDOWN = 112;

    /** No route to host */
    final static int EHOSTUNREACH = 113;

    /** Operation already in progress */
    final static int EALREADY = 114;

    /** Operation now in progress */
    final static int EINPROGRESS = 115;

    /** Stale NFS file handle */
    final static int ESTALE = 116;

    /** Structure needs cleaning */
    final static int EUCLEAN = 117;

    /** Not a XENIX named type file */
    final static int ENOTNAM = 118;

    /** No XENIX semaphores available */
    final static int ENAVAIL = 119;

    /** Is a named type file */
    final static int EISNAM = 120;

    /** Remote I/O error */
    final static int EREMOTEIO = 121;

    /** Quota exceeded */
    final static int EDQUOT = 122;

    /** No medium found */
    final static int ENOMEDIUM = 123;

    /** Wrong medium type */
    final static int EMEDIUMTYPE = 124;

    /** Operation Canceled */
    final static int ECANCELED = 125;

    /** Required key not available */
    final static int ENOKEY = 126;

    /** Key has expired */
    final static int EKEYEXPIRED = 127;

    /** Key has been revoked */
    final static int EKEYREVOKED = 128;

    /** Key was rejected by service */
    final static int EKEYREJECTED = 129;

    /** Owner died */
    final static int EOWNERDEAD = 130;

    /** State not recoverable */
    final static int ENOTRECOVERABLE = 131;
  }

  static interface mman {
    /** Page can be read */
    public final static int PROT_READ = 0x1;

    /** Page can be written */
    public final static int PROT_WRITE = 0x2;

    /** Page can be executed */
    public final static int PROT_EXEC = 0x4;

    /** Map pages that are shared between processes */
    public final static int MAP_SHARED = 0x1;

    /** Map pages without sharing them */
    public final static int MAP_PRIVATE = 0x2;

    /** Don't use a file */
    public final static int MAP_ANONYMOUS = 0x20;
  }

  /**
   * Class capturing file control (fcntl) constants
   */
  static interface fcntl {
    public final static int O_RDONLY = 0;
    public final static int O_WRONLY = 1;
    public final static int O_RDWR = 2;
    public final static int O_CREAT = 0100;
    public final static int O_EXCL = 0200;
    public final static int O_NOCTTY = 0400;
    public final static int O_TRUNC = 01000;
    public final static int O_APPEND = 02000;
    public final static int O_NONBLOCK = 04000;
  }

}
