/*
 *  This file is part of the Metacircular Research Platform (MRP)
 *
 *      http://mrp.codehaus.org/
 *
 *  This file is licensed to you under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the license at:
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

#include "sys.h"

#ifndef RVM_FOR_HARMONY
#include <errno.h>
#include <string.h>
#include <unistd.h>
#endif

/**
 * Read one byte from file.
 *
 * @param fd file descriptor
 * @return data read (-3: error, -2: operation would block, -1: eof, >= 0: valid)
 */
EXTERNAL int sysReadByte(int fd)
{
  unsigned char ch;
  SYS_START();
  TRACE_PRINTF("%s: sysReadByte %d\n", Me, fd);
#ifdef RVM_FOR_HARMONY
  return hyfile_read(fd, &ch, 1);
#else
  int rc;

 again:
  rc = read(fd, &ch, 1);
  switch (rc)
    {
    case  1:
      /*fprintf("%s: read (byte) ch is %d\n", Me, (int) ch);*/
      return (int) ch;
    case  0:
      /*fprintf("%s: read (byte) rc is 0\n", Me);*/
      return -1;
    default:
      /*fprintf("%s: read (byte) rc is %d\n", Me, rc);*/
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
 *
 * @param fd file descriptor
 * @param data data to write
 * @return -2 operation would block, -1: error, 0: success
 */
EXTERNAL int sysWriteByte(int fd, int data)
{
  SYS_START();
  char ch = data;
  TRACE_PRINTF("%s: sysWriteByte %d %c\n", Me, fd, ch);
#ifdef RVM_FOR_HARMONY
  return hyfile_write(fd, &ch, 1);
#else
  while(1) {
    int rc = write(fd, &ch, 1);
    if (rc == 1) {
      return 0; // success
    } else if (errno == EAGAIN) {
      return -2; // operation would block
    } else if (errno == EINTR) {
    } else {
      ERROR_PRINTF("%s: writeByte, fd=%d, write returned error %d (%s)\n", Me,
                     fd, errno, strerror(errno));
      return -1; // some kind of error
    }
  }
#endif // RVM_FOR_HARMONY
}

/**
 * Read multiple bytes from file or socket.
 *
 * @param fd  file or socket descriptor
 * @param buf buffer to be filled
 * @param cnt number of bytes requested
 * @return number of bytes delivered (-2: error, -1: socket would have blocked)
 */
EXTERNAL int sysReadBytes(int fd, char *buf, int cnt)
{
  SYS_START();
  TRACE_PRINTF("%s: sysReadBytes %d 0x%08x %d\n", Me, fd, buf, cnt);
#ifdef RVM_FOR_HARMONY
  return hyfile_read(fd, buf, cnt);
#else
  while (1) {
    int rc = read(fd, buf, cnt);
    if (rc >= 0)
      return rc;
    int err = errno;
    if (err == EAGAIN) {
      TRACE_PRINTF("%s: read on %d would have blocked: needs retry\n", Me, fd);
      return -1;
    }else if (err != EINTR) {
      ERROR_PRINTF("%s: read error %d (%s) on %d\n", Me,
		   err, strerror(err), fd);
      return -2;
    } else {
      // interrupted by signal; try again
    }
  }
#endif // RVM_FOR_HARMONY
}

/**
 * Write multiple bytes to file or socket.
 *
 * @param file or socket descriptor
 * @param buffer to be written
 * @param number of bytes to write
 * @return number of bytes written (-2: error, -1: socket would have blocked, -3 EPIPE error)
 */
EXTERNAL int sysWriteBytes(int fd, char *buf, int cnt)
{
  SYS_START();
  TRACE_PRINTF("%s: sysWriteBytes %d 0x%08x %d\n", Me, fd, buf, cnt);
#ifdef RVM_FOR_HARMONY
  return hyfile_write(fd, buf, cnt);
#else
  while(1) {
    int rc = write(fd, buf, cnt);
    if (rc >= 0)
      return rc;
    int err = errno;
    if (err == EAGAIN) {
      TRACE_PRINTF("%s: write on %d would have blocked: needs retry\n", Me, fd);
      return -1;
    } else  if (err == EINTR) {
      // interrupted by signal; try again
    } else if (err == EPIPE) {
      TRACE_PRINTF("%s: write on %d with nobody to read it\n", Me, fd);
      return -3;
    } else {
      ERROR_PRINTF("%s: write error %d (%s) on %d\n", Me,
		   err, strerror( err ), fd);
      return -2;
    }
  }
#endif // RVM_FOR_HARMONY
}
