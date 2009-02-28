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

#include "sys.h"
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>

#ifdef RVM_FOR_AIX
#include <sys/cache.h>
#endif

/** Allocate memory. */
EXTERNAL void* sysMalloc(int length)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMalloc %d\n", Me, length);
#ifdef RVM_FOR_HARMONY
  return hymem_allocate_memory(length);
#else
  return malloc(length);
#endif
}

EXTERNAL void* sysCalloc(int length)
{
  SYS_START();
  TRACE_PRINTF("%s: sysCalloc %d\n", Me, length);
#ifdef RVM_FOR_HARMONY
  void *result = hymem_allocate_memory(length);
  memset(result, 0x00, length);
  return result;
#else
  return calloc(1, length);
#endif
}

/** Release memory. */
EXTERNAL void sysFree(void *location)
{
  SYS_START();
  TRACE_PRINTF("%s: sysFree %p\n", Me, location);
#ifdef RVM_FOR_HARMONY
  hymem_free_memory(location);
#else
  free(location);
#endif
}

/** Memory to memory copy. */
EXTERNAL void sysCopy(void *dst, const void *src, Extent cnt)
{
  SYS_START();
  TRACE_PRINTF("%s: sysCopy %p %p %d\n", Me, dst, src, cnt);
  memcpy(dst, src, cnt);
}

/** Zero a range of memory bytes. */
EXTERNAL void sysZero(void *dst, Extent cnt)
{
  SYS_START();
  TRACE_PRINTF("%s: sysZero %p %d\n", Me, dst, cnt);
  memset(dst, 0x00, cnt);
}

/**
 * Zero a range of memory pages.
 * Taken:    start of range (must be a page boundary)
 *           size of range, in bytes (must be multiple of page size, 4096)
 * Returned: nothing
 */
EXTERNAL void sysZeroPages(void *dst, int cnt)
{
  const int STRATEGY=1;
  int rc;
  void *addr;
  SYS_START();
  TRACE_PRINTF("%s: sysZeroPages %p %d\n", Me, dst, cnt);

  if (STRATEGY == 1) {
    // Zero memory by touching all the bytes.
    // Advantage:    fewer page faults during mutation
    // Disadvantage: more page faults during collection, at least until
    //               steady state working set is achieved
    sysZero(dst, cnt);
  } else {
    // Zero memory by using munmap() followed by mmap().
    // This assumes that bootImageRunner.C has used mmap()
    // to acquire memory for the VM bootimage and heap.
    // Advantage:    fewer page faults during collection
    // Disadvantage: more page faults during mutation
    rc = munmap(dst, cnt);
    if (rc != 0)
    {
      ERROR_PRINTF("%s: munmap failed (errno=%d): ", Me, errno);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
    addr = mmap(dst, cnt, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_FIXED, -1, 0);
    if (addr == (void *)-1)
    {
      ERROR_PRINTF("%s: mmap failed (errno=%d): ", Me, errno);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
  }
}

/**
 * Synchronize caches: force data in dcache to be written out to main memory
 * so that it will be seen by icache when instructions are fetched back.
 *
 * Taken:    start of address range
 *           size of address range (bytes)
 * Returned: nothing
 */
EXTERNAL void sysSyncCache(void *address, size_t size)
{
  uintptr_t start, end, addr;
  SYS_START();
  TRACE_PRINTF("%s: sync %p %d\n", Me, address, size);
#ifdef RVM_FOR_HARMONY
  hycpu_flush_icache(address, size);
#else
#ifdef RVM_FOR_POWERPC
#ifdef RVM_FOR_AIX
  _sync_cache_range((caddr_t) address, size);
#else
  if (size < 0) {
    ERROR_PRINTF("%s: tried to sync a region of negative size!\n", Me);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }

  /* See section 3.2.1 of PowerPC Virtual Environment Architecture */
  start = (uintptr_t)address;
  end = start + size;

  /* update storage */
  /* Note: if one knew the cache line size, one could write a better loop */
  for (addr=start; addr < end; ++addr)
    asm("dcbst 0,%0" : : "r" (addr) );

  /* wait for update to commit */
  asm("sync");

  /* invalidate icache */
  /* Note: if one knew the cache line size, one could write a better loop */
  for (addr=start; addr<end; ++addr)
    asm("icbi 0,%0" : : "r" (addr) );

  /* context synchronization */
  asm("isync");
#endif // RVM_FOR_AIX
#endif // RVM_FOR_POWERPC
#endif // RVM_FOR_HARMONY
}

/**
 * mmap - general case
 * Taken: start address (Java ADDRESS)
 *       length of region (Java EXTENT)
 *        desired protection (Java int)
 *        flags (Java int)
 *        file descriptor (Java int)
 *        offset (Java long)  [to cover 64 bit file systems]
 * Returned: address of region (or -1 on failure) (Java ADDRESS)
 */
EXTERNAL void * sysMMap(char *start , size_t length ,
                        int protection , int flags ,
                        int fd , Offset offset)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMMap %p %d %d %d %d %d\n",
               Me, start, length, protection, flags, fd, offset);
#ifdef RVM_FOR_HARMONY
#warning TODO: should use Harmony mmap
#endif
  return mmap(start, (size_t)(length), protection, flags, fd, (off_t)offset);
}

/**
 * mprotect
 * Taken: start address (Java ADDRESS)
 *        length of region (Java EXTENT)
 *        new protection (Java int)
 * Returned: 0 (success) or -1 (failure) (Java int)
 */
EXTERNAL int sysMProtect(char *start, size_t length, int prot)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMProtect %p %d %d\n",
               Me, start, length, prot);
#ifndef RVM_FOR_HARMONY
  return mprotect(start, length, prot);
#else
  return -1;
#endif // RVM_FOR_HARMONY -- TODO
}

/**
 * Same as mmap, but with more debugging support.
 * Returned: address of region if successful; errno (1 to 127) otherwise
 */
EXTERNAL void* sysMMapErrno(char *start , size_t length ,
                            int protection , int flags ,
                            int fd , Offset offset)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMMapErrno %p %d %d %d %d %d\n",
               Me, start, length, protection, flags, fd, offset);
#ifdef RVM_FOR_HARMONY
#warning TODO: should use Harmony mmap
#endif
  void* res = mmap(start, (size_t)(length), protection, flags, fd, (off_t)offset);
  if (res == (void *) -1){
    CONSOLE_PRINTF("%s: sysMMapErrno %p %d %d %d %d %d failed with %d.\n",
                   Me, start, length, protection, flags, fd, offset, errno);
    return (void *) errno;
  } else {
    TRACE_PRINTF("mmap succeeded- region = [0x%x ... 0x%x]    size = %d\n", res, ((size_t)res) + length, length);
    return res;
  }
}

/**
 * getpagesize
 * Taken: (no arguments)
 * Returned: default page size in bytes (Java int)
 */
EXTERNAL int sysGetPageSize()
{
  SYS_START();
  int result;
#ifndef RVM_FOR_HARMONY
  result = (int)(getpagesize());
#else
  result = hyvmem_supported_page_sizes()[0];
#endif // RVM_FOR_HARMONY
  TRACE_PRINTF("%s: sysGetPageSize %d\n", Me, result);
  return result;
}

