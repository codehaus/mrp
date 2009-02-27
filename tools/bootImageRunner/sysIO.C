/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include "sys.h"

/**
 * Check user's perms.
 * @param name  null terminated filename
 * @param king kind of access perm to check for (see FileSystem.ACCESS_W_OK)
 * @return 0 on success (-1=error)
 */
EXTERNAL int sysAccess(char *name, int kind)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: access %s\n", Me, name);
#ifdef RVM_FOR_HARMONY
  CONSOLE_PRINTF(SysTraceFile, "Unsupported call to sysAccess\n");
  return -1; // TODO: Harmony
#else
  return access(name, kind);
#endif
}

/**
 * How many bytes can be read from file/socket without blocking?
 * @param fd file/socket descriptor
 * @return >=0: count, -1: error
 */
EXTERNAL int sysBytesAvailable(int fd)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: bytesAvailable %d\n", Me, fd);
#ifdef RVM_FOR_HARMONY
  CONSOLE_PRINTF(SysTraceFile, "Unsupported call to sysSetFdCloseOnExec\n");
  return -1; // TODO: Harmony
#else
  int count = 0;
  if (ioctl(fd, FIONREAD, &count) == -1)
    {
      bool badFD = (errno == EBADF);
      CONSOLE_PRINTF(SysErrorFile, "%s: FIONREAD ioctl on %d failed: %s (errno=%d)\n", Me, fd, strerror( errno ), errno);
      return -1;
    }
  TRACE_PRINTF(SysTraceFile, "%s: available fd=%d count=%d\n", Me, fd, count);
  return count;
#endif // RVM_FOR_HARMONY
}

/**
 * Sync
 * @param fd file descriptor
 * @return 0, -1 => error
 */
EXTERNAL int sysSyncFile(int fd)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sync %d\n", Me, fd);
#ifdef RVM_FOR_HARMONY
  return hyfile_sync(fd);
#else
  if (fsync(fd) != 0) {
    // some kinds of files cannot be sync'ed, so don't print error message
    // however, do return error code in case some application cares
    return -1;
  }
  return 0;
#endif // RVM_FOR_HARMONY
}

/**
 * Read one byte from file.
 * @param fd file descriptor
 * @return data read (-3: error, -2: operation would block, -1: eof, >= 0: valid)
 */
EXTERNAL int sysReadByte(int fd)
{
  unsigned char ch;
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: readByte %d\n", Me, fd);
#ifdef RVM_FOR_HARMONY
  return hyfile_read(fd, &ch, 1);
#else
  int rc;

 again:
  rc = read(fd, &ch, 1);
  switch (rc)
    {
    case  1:
      /*fprintf(SysTraceFile, "%s: read (byte) ch is %d\n", Me, (int) ch);*/
      return (int) ch;
    case  0:
      /*fprintf(SysTraceFile, "%s: read (byte) rc is 0\n", Me);*/
      return -1;
    default:
      /*fprintf(SysTraceFile, "%s: read (byte) rc is %d\n", Me, rc);*/
      if (errno == EAGAIN)
	return -2;  // Read would have blocked
      else if (errno == EINTR)
	goto again; // Read was interrupted; try again
      else
	return -3;  // Some other error
    }
#endif // RVM_FOR_HARMONY
}

/**
 * Write one byte to file.
 * @param fd file descriptor
 * @param data data to write
 * @return -2 operation would block, -1: error, 0: success
 */
EXTERNAL int
sysWriteByte(int fd, int data)
{
  SYS_START();
  char ch = data;
  TRACE_PRINTF(SysTraceFile, "%s: writeByte %d %c\n", Me, fd, ch);
#ifdef RVM_FOR_HARMONY
  return hyfile_write(fd, &ch, 1);
#else
 again:
  int rc = write(fd, &ch, 1);
  if (rc == 1)
    return 0; // success
  else if (errno == EAGAIN)
    return -2; // operation would block
  else if (errno == EINTR)
    goto again; // interrupted by signal; try again
  else {
    CONSOLE_PRINTF(SysErrorFile, "%s: writeByte, fd=%d, write returned error %d (%s)\n", Me,
                   fd, errno, strerror(errno));
    return -1; // some kind of error
  }
#endif // RVM_FOR_HARMONY
}

/**
 * Read multiple bytes from file or socket.
 * @param fd  file or socket descriptor
 * @param buf buffer to be filled
 * @param cnt number of bytes requested
 * @return number of bytes delivered (-2: error, -1: socket would have blocked)
 */
EXTERNAL int sysReadBytes(int fd, char *buf, int cnt)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: read %d 0x%08x %d\n", Me, fd, buf, cnt);
#ifdef RVM_FOR_HARMONY
  return hyfile_read(fd, buf, cnt);
#else
 again:
  int rc = read(fd, buf, cnt);
  if (rc >= 0)
    return rc;
  int err = errno;
  if (err == EAGAIN)
    {
      TRACE_PRINTF(SysTraceFile, "%s: read on %d would have blocked: needs retry\n", Me, fd);
      return -1;
    }
  else if (err == EINTR)
    goto again; // interrupted by signal; try again
  fprintf(SysTraceFile, "%s: read error %d (%s) on %d\n", Me,
	  err, strerror(err), fd);
  return -2;
#endif // RVM_FOR_HARMONY
}

/**
 * Write multiple bytes to file or socket.
 * @param file or socket descriptor
 * @param buffer to be written
 * @param number of bytes to write
 * @return number of bytes written (-2: error, -1: socket would have blocked, -3 EPIPE error)
 */
EXTERNAL int sysWriteBytes(int fd, char *buf, int cnt)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: write %d 0x%08x %d\n", Me, fd, buf, cnt);
#ifdef RVM_FOR_HARMONY
  return hyfile_write(fd, buf, cnt);
#else
 again:
  int rc = write(fd, buf, cnt);
  if (rc >= 0)
    return rc;
  int err = errno;
  if (err == EAGAIN)
    {
      TRACE_PRINTF(SysTraceFile, "%s: write on %d would have blocked: needs retry\n", Me, fd);
      return -1;
    }
  if (err == EINTR)
    goto again; // interrupted by signal; try again
  if (err == EPIPE)
    {
      TRACE_PRINTF(SysTraceFile, "%s: write on %d with nobody to read it\n", Me, fd);
      return -3;
    }
  fprintf(SysTraceFile, "%s: write error %d (%s) on %d\n", Me,
	  err, strerror( err ), fd);
  return -2;
#endif // RVM_FOR_HARMONY
}

/**
 * Close file or socket.
 * @param file/socket descriptor
 * @return 0: success, -1: file/socket not currently open, -2: i/o error
 */
static int sysClose(int fd)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: close %d\n", Me, fd);
#ifdef RVM_FOR_HARMONY
  return hyfile_close(fd);
#else
  if ( -1 == fd ) return -1;
  int rc = close(fd);
  if (rc == 0) return 0; // success
  if (errno == EBADF) return -1; // not currently open
  return -2; // some other error
#endif // RVM_FOR_HARMONY
}

/**
 * Set the close-on-exec flag for given file descriptor.
 *
 * Taken: the file descriptor
 * Returned: 0 if sucessful, nonzero otherwise
 */
EXTERNAL int sysSetFdCloseOnExec(int fd)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: setFdCloseOnExec %d\n", Me, fd);
#ifdef RVM_FOR_HARMONY
  CONSOLE_PRINTF(SysTraceFile, "Unsupported call to sysSetFdCloseOnExec\n");
  return -1; // TODO: Harmony
#else
  return fcntl(fd, F_SETFD, FD_CLOEXEC);
#endif // RVM_FOR_HARMONY
}

/**
 * Get file status.
 * @param name null terminated filename
 * @param kind of info desired (see FileSystem.STAT_XXX)
 * @return Returned: status (-1=error)
 */
EXTERNAL int sysStat(char *name, int kind)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysStat %s %d\n", Me, name, kind);
#ifdef RVM_FOR_HARMONY
  CONSOLE_PRINTF(SysTraceFile, "Unsupported call to sysStat\n");
  return -1; // TODO: Harmony
#else
  struct stat info;

  if (stat(name, &info))
    return -1; // does not exist, or other trouble

  switch (kind) {
  case FileSystem_STAT_EXISTS:
    return 1;                              // exists
  case FileSystem_STAT_IS_FILE:
    return S_ISREG(info.st_mode) != 0; // is file
  case FileSystem_STAT_IS_DIRECTORY:
    return S_ISDIR(info.st_mode) != 0; // is directory
  case FileSystem_STAT_IS_READABLE:
    return (info.st_mode & S_IREAD) != 0; // is readable by owner
  case FileSystem_STAT_IS_WRITABLE:
    return (info.st_mode & S_IWRITE) != 0; // is writable by owner
  case FileSystem_STAT_LAST_MODIFIED:
    return info.st_mtime;   // time of last modification
  case FileSystem_STAT_LENGTH:
    return info.st_size;    // length
  }
  return -1; // unrecognized request
#endif // RVM_FOR_HARMONY
}
