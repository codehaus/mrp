/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.ppc.os.abi.linux;

import org.binarytranslator.generic.os.abi.linux.LinuxSystemCalls;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;
import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * Linux system call handling class
 */
final public class PPC_LinuxSystemCalls extends LinuxSystemCalls {
  /**
   * Constructor that ensures systemCallTable is correctly initialized
   * for X86 Linux system calls
   */
  public PPC_LinuxSystemCalls(LinuxSystemCallGenerator src) {
    super(src);
    systemCallTable[1] = new LinuxSystemCalls.SysExit();
    systemCallTable[3] = new LinuxSystemCalls.SysRead();
    systemCallTable[4] = new LinuxSystemCalls.SysWrite();
    systemCallTable[5] = new LinuxSystemCalls.SysOpen();
    systemCallTable[6] = new LinuxSystemCalls.SysClose();
    systemCallTable[24] = new LinuxSystemCalls.SysGetUID();
    systemCallTable[45] = new LinuxSystemCalls.SysBrk();
    systemCallTable[47] = new LinuxSystemCalls.SysGetGID();
    systemCallTable[49] = new LinuxSystemCalls.SysGetEUID();
    systemCallTable[50] = new LinuxSystemCalls.SysGetEGID();
    systemCallTable[122] = new LinuxSystemCalls.SysUname();
    systemCallTable[204] = new LinuxSystemCalls.SysFcntl64();
  }
  /**
   * The machine for this system given by uname
   */
  protected String getMachine() {
    return "ppc";
  }
  /**
   * Turn the given system call number into a string describing its
   * function
   */
  public String sysCallToString(int syscall) {
    switch(syscall) {
    case 0: return "restart_syscall";
    case 1: return "exit";
    case 2: return "fork";
    case 3: return "read";
    case 4: return "write";
    case 5: return "open";
    case 6: return "close";
    case 7: return "waitpid";
    case 8: return "creat";
    case 9: return "link";
    case 10: return "unlink";
    case 11: return "execve";
    case 12: return "chdir";
    case 13: return "time";
    case 14: return "mknod";
    case 15: return "chmod";
    case 16: return "lchown";
    case 17: return "break";
    case 18: return "oldstat";
    case 19: return "lseek";
    case 20: return "getpid";
    case 21: return "mount";
    case 22: return "umount";
    case 23: return "setuid";
    case 24: return "getuid";
    case 25: return "stime";
    case 26: return "ptrace";
    case 27: return "alarm";
    case 28: return "oldfstat";
    case 29: return "pause";
    case 30: return "utime";
    case 31: return "stty";
    case 32: return "gtty";
    case 33: return "access";
    case 34: return "nice";
    case 35: return "ftime";
    case 36: return "sync";
    case 37: return "kill";
    case 38: return "rename";
    case 39: return "mkdir";
    case 40: return "rmdir";
    case 41: return "dup";
    case 42: return "pipe";
    case 43: return "times";
    case 44: return "prof";
    case 45: return "brk";
    case 46: return "setgid";
    case 47: return "getgid";
    case 48: return "signal";
    case 49: return "geteuid";
    case 50: return "getegid";
    case 51: return "acct";
    case 52: return "umount2";
    case 53: return "lock";
    case 54: return "ioctl";
    case 55: return "fcntl";
    case 56: return "mpx";
    case 57: return "setpgid";
    case 58: return "ulimit";
    case 59: return "oldolduname";
    case 60: return "umask";
    case 61: return "chroot";
    case 62: return "ustat";
    case 63: return "dup2";
    case 64: return "getppid";
    case 65: return "getpgrp";
    case 66: return "setsid";
    case 67: return "sigaction";
    case 68: return "sgetmask";
    case 69: return "ssetmask";
    case 70: return "setreuid";
    case 71: return "setregid";
    case 72: return "sigsuspend";
    case 73: return "sigpending";
    case 74: return "sethostname";
    case 75: return "setrlimit";
    case 76: return "getrlimit";
    case 77: return "getrusage";
    case 78: return "gettimeofday";
    case 79: return "settimeofday";
    case 80: return "getgroups";
    case 81: return "setgroups";
    case 82: return "select";
    case 83: return "symlink";
    case 84: return "oldlstat";
    case 85: return "readlink";
    case 86: return "uselib";
    case 87: return "swapon";
    case 88: return "reboot";
    case 89: return "readdir";
    case 90: return "mmap";
    case 91: return "munmap";
    case 92: return "truncate";
    case 93: return "ftruncate";
    case 94: return "fchmod";
    case 95: return "fchown";
    case 96: return "getpriority";
    case 97: return "setpriority";
    case 98: return "profil";
    case 99: return "statfs";
    case 100: return "fstatfs";
    case 101: return "ioperm";
    case 102: return "socketcall";
    case 103: return "syslog";
    case 104: return "setitimer";
    case 105: return "getitimer";
    case 106: return "stat";
    case 107: return "lstat";
    case 108: return "fstat";
    case 109: return "olduname";
    case 110: return "iopl";
    case 111: return "vhangup";
    case 112: return "idle";
    case 113: return "vm86";
    case 114: return "wait4";
    case 115: return "swapoff";
    case 116: return "sysinfo";
    case 117: return "ipc";
    case 118: return "fsync";
    case 119: return "sigreturn";
    case 120: return "clone";
    case 121: return "setdomainname";
    case 122: return "uname";
    case 123: return "modify_ldt";
    case 124: return "adjtimex";
    case 125: return "mprotect";
    case 126: return "sigprocmask";
    case 127: return "create_module";
    case 128: return "init_module";
    case 129: return "delete_module";
    case 130: return "get_kernel_syms";
    case 131: return "quotactl";
    case 132: return "getpgid";
    case 133: return "fchdir";
    case 134: return "bdflush";
    case 135: return "sysfs";
    case 136: return "personality";
    case 137: return "afs_syscall";
    case 138: return "setfsuid";
    case 139: return "setfsgid";
    case 140: return "_llseek";
    case 141: return "getdents";
    case 142: return "_newselect";
    case 143: return "flock";
    case 144: return "msync";
    case 145: return "readv";
    case 146: return "writev";
    case 147: return "getsid";
    case 148: return "fdatasync";
    case 149: return "_sysctl";
    case 150: return "mlock";
    case 151: return "munlock";
    case 152: return "mlockall";
    case 153: return "munlockall";
    case 154: return "sched_setparam";
    case 155: return "sched_getparam";
    case 156: return "sched_setscheduler";
    case 157: return "sched_getscheduler";
    case 158: return "sched_yield";
    case 159: return "sched_get_priority_max";
    case 160: return "sched_get_priority_min";
    case 161: return "sched_rr_get_interval";
    case 162: return "nanosleep";
    case 163: return "mremap";
    case 164: return "setresuid";
    case 165: return "getresuid";
    case 166: return "query_module";
    case 167: return "poll";
    case 168: return "nfsservctl";
    case 169: return "setresgid";
    case 170: return "getresgid";
    case 171: return "prctl";
    case 172: return "rt_sigreturn";
    case 173: return "rt_sigaction";
    case 174: return "rt_sigprocmask";
    case 175: return "rt_sigpending";
    case 176: return "rt_sigtimedwait";
    case 177: return "rt_sigqueueinfo";
    case 178: return "rt_sigsuspend";
    case 179: return "pread64";
    case 180: return "pwrite64";
    case 181: return "chown";
    case 182: return "getcwd";
    case 183: return "capget";
    case 184: return "capset";
    case 185: return "sigaltstack";
    case 186: return "sendfile";
    case 187: return "getpmsg";
    case 188: return "putpmsg";
    case 189: return "vfork";
    case 190: return "ugetrlimit";
    case 191: return "readahead";
    case 192: return "mmap2";
    case 193: return "truncate64";
    case 194: return "ftruncate64";
    case 195: return "stat64";
    case 196: return "lstat64";
    case 197: return "fstat64";
    case 198: return "pciconfig_read";
    case 199: return "pciconfig_write";
    case 200: return "pciconfig_iobase";
    case 201: return "multiplexer";
    case 202: return "getdents64";
    case 203: return "pivot_root";
    case 204: return "fcntl64";
    case 205: return "madvise";
    case 206: return "mincore";
    case 207: return "gettid";
    case 208: return "tkill";
    case 209: return "setxattr";
    case 210: return "lsetxattr";
    case 211: return "fsetxattr";
    case 212: return "getxattr";
    case 213: return "lgetxattr";
    case 214: return "fgetxattr";
    case 215: return "listxattr";
    case 216: return "llistxattr";
    case 217: return "flistxattr";
    case 218: return "removexattr";
    case 219: return "lremovexattr";
    case 220: return "fremovexattr";
    case 221: return "futex";
    case 222: return "sched_setaffinity";
    case 223: return "sched_getaffinity";

    case 225: return "tuxcall";
    case 226: return "sendfile64";
    case 227: return "io_setup";
    case 228: return "io_destroy";
    case 229: return "io_getevents";
    case 230: return "io_submit";
    case 231: return "io_cancel";
    case 232: return "set_tid_address";
    case 233: return "fadvise64";
    case 234: return "exit_group";
    case 235: return "lookup_dcookie";
    case 236: return "epoll_create";
    case 237: return "epoll_ctl";
    case 238: return "epoll_wait";
    case 239: return "remap_file_pages";
    case 240: return "timer_create";
    case 241: return "timer_settime";
    case 242: return "timer_gettime";
    case 243: return "timer_getoverrun";
    case 244: return "timer_delete";
    case 245: return "clock_settime";
    case 246: return "clock_gettime";
    case 247: return "clock_getres";
    case 248: return "clock_nanosleep";
    case 249: return "swapcontext";
    case 250: return "tgkill";
    case 251: return "utimes";
    case 252: return "statfs64";
    case 253: return "fstatfs64";
    case 254: return "fadvise64_64";
    case 255: return "rtas";
    case 256: return "sys_debug_setcontext";

    case 262: return "mq_open";
    case 263: return "mq_unlink";
    case 264: return "mq_timedsend";
    case 265: return "mq_timedreceive";
    case 266: return "mq_notify";
    case 267: return "mq_getsetattr";
    case 268: return "kexec_load";
    case 269: return "add_key";
    case 270: return "request_key";
    case 271: return "keyctl";
    case 272: return "waitid";
    case 273: return "ioprio_set";
    case 274: return "ioprio_get";
    case 275: return "inotify_init";
    case 276: return "inotify_add_watch";
    case 277: return "inotify_rm_watch";
    default:
      if ((syscall > 0) && (syscall < 278)) {
        return "unused system call number " + syscall;
      }
      else {
        return "invalid system call number " + syscall;
      }
    }
  }
}
