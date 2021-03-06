/*
 *  This file is part of MRP (http://mrp.codehaus.org/).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.binarytranslator.arch.arm.os.abi.linux;

import org.binarytranslator.arch.arm.os.process.linux.ARM_LinuxProcessSpace;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCalls;
import org.binarytranslator.generic.os.abi.linux.filesystem.FileProvider;

public class ARM_LinuxSystemCalls extends LinuxSystemCalls {
  

  public ARM_LinuxSystemCalls(ARM_LinuxProcessSpace ps, LinuxSystemCallGenerator src) {
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
    systemCallTable[90] = new LinuxSystemCalls.SysMmap();
    systemCallTable[91] = new LinuxSystemCalls.SysMunmap();
    systemCallTable[122] = new LinuxSystemCalls.SysUname();
    systemCallTable[146] = new LinuxSystemCalls.SysWriteV();
    systemCallTable[195] = new LinuxSystemCalls.SysStat64();
    systemCallTable[197] = new LinuxSystemCalls.SysFstat64();
    systemCallTable[199] = new LinuxSystemCalls.SysGetEUID();
    systemCallTable[200] = new LinuxSystemCalls.SysGetEGID();
    systemCallTable[201] = new LinuxSystemCalls.SysGetEUID();
    systemCallTable[202] = new LinuxSystemCalls.SysGetEGID();
    systemCallTable[221] = new LinuxSystemCalls.SysFcntl64();
    systemCallTable[252] = new LinuxSystemCalls.SysExitGroup();
  }
  
  @Override
  public void doSysCall() {
    super.doSysCall();
  }

  @Override
  protected String getMachine() {
    //TODO: Grab this from a real machine
    return "ARM";
  }
  
  @Override
  protected FileProvider buildFileSystem() {
    ARM_ProcFileSystem fs = new ARM_ProcFileSystem(super.buildFileSystem());
    return fs;
  }

  @Override
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
    case 113: return "vm86old";
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
    case 166: return "vm86";
    case 167: return "query_module";
    case 168: return "poll";
    case 169: return "nfsservctl";
    case 170: return "setresgid";
    case 171: return "getresgid";
    case 172: return "prctl";
    case 173: return "rt_sigreturn";
    case 174: return "rt_sigaction";
    case 175: return "rt_sigprocmask";
    case 176: return "rt_sigpending";
    case 177: return "rt_sigtimedwait";
    case 178: return "rt_sigqueueinfo";
    case 179: return "rt_sigsuspend";
    case 180: return "pread64";
    case 181: return "pwrite64";
    case 182: return "chown";
    case 183: return "getcwd";
    case 184: return "capget";
    case 185: return "capset";
    case 186: return "sigaltstack";
    case 187: return "sendfile";
    case 188: return "getpmsg";
    case 189: return "putpmsg";
    case 190: return "vfork";
    case 191: return "ugetrlimit";
    case 192: return "mmap2";
    case 193: return "truncate64";
    case 194: return "ftruncate64";
    case 195: return "stat64";
    case 196: return "lstat64";
    case 197: return "fstat64";
    case 198: return "lchown32";
    case 199: return "getuid32";
    case 200: return "getgid32";
    case 201: return "geteuid32";
    case 202: return "getegid32";
    case 203: return "setreuid32";
    case 204: return "setregid32";
    case 205: return "getgroups32";
    case 206: return "setgroups32";
    case 207: return "fchown32";
    case 208: return "setresuid32";
    case 209: return "getresuid32";
    case 210: return "setresgid32";
    case 211: return "getresgid32";
    case 212: return "chown32";
    case 213: return "setuid32";
    case 214: return "setgid32";
    case 215: return "setfsuid32";
    case 216: return "setfsgid32";
    case 217: return "pivot_root";
    case 218: return "mincore";
    case 219: return "madvise";
    case 220: return "getdents64";
    case 221: return "fcntl64";

    case 224: return "gettid";
    case 225: return "readahead";
    case 226: return "setxattr";
    case 227: return "lsetxattr";
    case 228: return "fsetxattr";
    case 229: return "getxattr";
    case 230: return "lgetxattr";
    case 231: return "fgetxattr";
    case 232: return "listxattr";
    case 233: return "llistxattr";
    case 234: return "flistxattr";
    case 235: return "removexattr";
    case 236: return "lremovexattr";
    case 237: return "fremovexattr";
    case 238: return "tkill";
    case 239: return "sendfile64";
    case 240: return "futex";
    case 241: return "sched_setaffinity";
    case 242: return "sched_getaffinity";
    case 243: return "set_thread_area";
    case 244: return "get_thread_area";
    case 245: return "io_setup";
    case 246: return "io_destroy";
    case 247: return "io_getevents";
    case 248: return "io_submit";
    case 249: return "io_cancel";
    case 250: return "fadvise64";
    case 251: return "set_zone_reclaim";
    case 252: return "exit_group";
    case 253: return "lookup_dcookie";
    case 254: return "epoll_create";
    case 255: return "epoll_ctl";
    case 256: return "epoll_wait";
    case 257: return "remap_file_pages";
    case 258: return "set_tid_address";
    case 259: return "timer_create";
    case 260: return "timer_settime";
    case 261: return "timer_gettime";
    case 262: return "timer_getoverrun";
    case 263: return "timer_delete";
    case 264: return "clock_settime";
    case 265: return "clock_gettime";
    case 266: return "clock_getres";
    case 267: return "clock_nanosleep";
    case 268: return "statfs64";
    case 269: return "fstatfs64";
    case 270: return "tgkill";
    case 271: return "utimes";
    case 272: return "fadvise64_64";
    case 273: return "vserver";
    case 274: return "mbind";
    case 275: return "get_mempolicy";
    case 276: return "set_mempolicy";
    case 277: return "mq_open";
    case 278: return "mq_unlink";
    case 279: return "mq_timedsend";
    case 280: return "mq_timedreceive";
    case 281: return "mq_notify";
    case 282: return "mq_getsetattr";
    case 283: return "sys_kexec_load";
    case 284: return "waitid";
    case 285: return "sys_setaltroot";
    case 286: return "add_key";
    case 287: return "request_key";
    case 288: return "keyctl";
    case 289: return "ioprio_set";
    case 290: return "ioprio_get";
    case 291: return "inotify_init";
    case 292: return "inotify_add_watch";
    case 293: return "inotify_rm_watch";
    default:
      if ((syscall > 0) && (syscall < 294)) {
        return "unused";
      }
      else {
        return "invalid system call number " + syscall;
      }
    }
  }

}
