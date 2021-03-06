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
#include <string.h>

#ifndef RVM_FOR_HARMONY
#include <errno.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/mman.h>
#ifdef RVM_FOR_AIX
#include <sys/cache.h>
#endif // RVM_FOR_AIX
#endif // RVM_FOR_HARMONY

#ifdef RVM_FOR_HARMONY
UDATA DefaultPageSize;
#endif

/** Allocate memory. */
EXTERNAL void* sysMalloc(int length)
{
  void *result;
  SYS_START();
  TRACE_PRINTF("%s: sysMalloc %d\n", Me, length);
#ifdef RVM_FOR_HARMONY
  result = hymem_allocate_memory(length);
#else
  result = malloc(length);
#endif
  if (result == NULL) {
    ERROR_PRINTF("%s: error to allocate memory in sysMalloc\n", Me);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
  return result;
}

EXTERNAL void* sysCalloc(int length)
{
  void *result;
  SYS_START();
  TRACE_PRINTF("%s: sysCalloc %d\n", Me, length);
#ifdef RVM_FOR_HARMONY
  result = hymem_allocate_memory(length);
#else
  result = calloc(1, length);
#endif
  if (result == NULL) {
    ERROR_PRINTF("%s: error to allocate memory in sysCalloc\n", Me);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
#ifdef RVM_FOR_HARMONY
  memset(result, 0x00, length);
#endif
  return result;
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
  TRACE_PRINTF("%s: sysCopy %p %p %d\n", Me, dst, src, (int)cnt);
  memcpy(dst, src, cnt);
}

/** Zero a range of memory bytes. */
EXTERNAL void sysZero(void *dst, Extent cnt)
{
  SYS_START();
  TRACE_PRINTF("%s: sysZero %p %d\n", Me, dst, (int)cnt);
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
  SYS_START();
  TRACE_PRINTF("%s: sysZeroPages %p %d\n", Me, dst, cnt);

#ifdef RVM_FOR_HARMONY
  sysZero(dst, cnt);
#else
  const int STRATEGY=1;
  int rc;
  void *addr;
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
#ifdef MAP_ANONYMOUS
    addr = mmap(dst, cnt, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_FIXED, -1, 0);
#else
    addr = mmap(dst, cnt, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANON | MAP_FIXED, -1, 0);
#endif
    if (addr == (void *)-1)
    {
      ERROR_PRINTF("%s: mmap failed (errno=%d): ", Me, errno);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
  }
#endif // RVM_FOR_HARMONY
}

/**
 * Synchronize caches: force data in dcache to be written out to main memory
 * so that it will be seen by icache when instructions are fetched back.
 *
 * @param address [in] start of address range
 * @param size    [in] size of address range (bytes)
 */
EXTERNAL void sysSyncCache(void *address, size_t size)
{
  uintptr_t start, end, addr;
  SYS_START();
  TRACE_PRINTF("%s: sync %p %zd\n", Me, address, size);
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
 * Reserve memory at specified address, size
 *
 * @param start  [in] address in virtual memory to reserve (Java ADDRESS)
 * @param length [in] size of region (Java EXTENT)
 * @param read   [in] is memory readable (Java boolean)
 * @param write  [in] is memory writable (Java boolean)
 * @param exec   [in] is memory executable (Java boolean)
 * @param commit [in] shall we commit and not just reserve the memory(Java boolean)
 * @return address of region (errno or NULL on failure) (Java ADDRESS)
 */
EXTERNAL void * sysMemoryReserve(char *start, size_t length,
                                 jboolean read, jboolean write,
                                 jboolean exec, jboolean commit)
{
  void* result;
  SYS_START();
#ifdef RVM_FOR_HARMONY
  HyPortVmemIdentifier ident;
  TRACE_PRINTF("%s: sysMemoryReserve %p %zd - %d %d %d %d\n",
               Me, start, length, read, write, exec, commit);
  ident.pageSize = DefaultPageSize;
  ident.mode = 0;
  if (read) {
    ident.mode |= HYPORT_VMEM_MEMORY_MODE_READ;
  }
  if (write) {
    ident.mode |= HYPORT_VMEM_MEMORY_MODE_WRITE;
  }
  if (exec) {
    ident.mode |= HYPORT_VMEM_MEMORY_MODE_EXECUTE;
  }
#ifndef RVM_FOR_WINDOWS
  if (commit) {
    ident.mode |= HYPORT_VMEM_MEMORY_MODE_COMMIT;
  }
  result = hyvmem_reserve_memory(start, length, &ident, ident.mode, ident.pageSize);
#else
  /* Work around bug HARMONY-6138 */
  result = hyvmem_reserve_memory(start, length, &ident, ident.mode, ident.pageSize);
  if(result != NULL) {
    if (commit) {
      ident.mode |= HYPORT_VMEM_MEMORY_MODE_COMMIT;
      result = hyvmem_reserve_memory(start, length, &ident, ident.mode, ident.pageSize);
    }
  }
#endif // RVM_FOR_WINDOWS
#else
  int protection = 0;
  int flags = MAP_PRIVATE;
  int fd = -1;
  off_t offset = 0;
  TRACE_PRINTF("%s: sysMemoryReserve %p %zd - %d %d %d %d\n",
               Me, start, length, read, write, exec, commit);
#if defined(MAP_ANONYMOUS)
  flags |= MAP_ANONYMOUS;
#elif defined(MAP_ANON)
  flags |= MAP_ANON;
#else
  fd = open("/dev/zero", O_RDWR, 0);
#endif  
  if (commit) {
    if (read) {
      protection |= PROT_READ;
    }
    if (write) {
      protection |= PROT_WRITE;
    }
    if (exec) {
      protection |= PROT_EXEC;
    }
  } else {
    protection = PROT_NONE;
    flags |= MAP_NORESERVE;
  }
  result = mmap(start, (size_t)(length), protection, flags, fd, offset);
#if !defined(MAP_ANONYMOUS) && !defined(MAP_ANON)
  close(fd);
#endif
  if (result == (void *) -1){
    CONSOLE_PRINTF("%s: sysMemoryReserve %p %zd %d %d %d %ld failed with %d.\n",
                   Me, start, length, protection, flags, fd, (long)offset, errno);
    return (void *)((Address) errno);
  }
#endif // RVM_FOR_HARMONY
  if (result != NULL) {
    TRACE_PRINTF("MemoryReserve succeeded- region = [%p ... %p]    size = %zd\n", result, (void*)(((size_t)result) + length), length);
  }
  return result;
}

/**
 * Release memory at specified address, size
 * @param start address (Java ADDRESS)
 * @param length of region (Java EXTENT)
 * @return true iff success (Java boolean)
 */
EXTERNAL jboolean sysMemoryFree(char *start, size_t length)
{
  SYS_START();
#ifdef RVM_FOR_HARMONY
  HyPortVmemIdentifier ident;
  TRACE_PRINTF("%s: sysMemoryFree %p %d\n", Me, start, length);
  ident.pageSize = DefaultPageSize;
  ident.mode = 0;
  if (hyvmem_free_memory(start, length, &ident) == 0) {
    return JNI_TRUE;
  } else {
    return JNI_FALSE;
  }
#else
  TRACE_PRINTF("%s: sysMemoryFree %p %zd\n", Me, start, length);
  if (munmap(start, length) == 0) {
    return JNI_TRUE;
  } else {
    return JNI_FALSE;
  }
#endif // RVM_FOR_HARMONY
}

/**
 * Commit memory
 * @param start address (Java ADDRESS)
 * @param length of region (Java EXTENT)
 * @param read (Java boolean)
 * @param write (Java boolean)
 * @param exec (Java boolean)
 * @return true iff success (Java boolean)
 */
EXTERNAL jboolean sysMemoryCommit(char *start, size_t length,
                                 jboolean read, jboolean write,
                                 jboolean exec)
{
  SYS_START();
#ifdef RVM_FOR_HARMONY
  HyPortVmemIdentifier ident;
  TRACE_PRINTF("%s: sysMemoryCommit %p %d - %d %d %d\n",
               Me, start, length, read, write, exec);
  ident.pageSize = DefaultPageSize;
  ident.mode = 0;
  if (read) {
    ident.mode |= HYPORT_VMEM_MEMORY_MODE_READ;
  }
  if (write) {
    ident.mode |= HYPORT_VMEM_MEMORY_MODE_WRITE;
  }
  if (exec) {
    ident.mode |= HYPORT_VMEM_MEMORY_MODE_EXECUTE;
  }
  if(hyvmem_commit_memory(start, length, &ident) == start) {
    return JNI_TRUE;
  } else {
    return JNI_FALSE;
  }
#else
  int protection = 0;
  TRACE_PRINTF("%s: sysMemoryCommit %p %zd - %d %d %d\n",
               Me, start, length, read, write, exec);
  if (read) {
    protection |= PROT_READ;
  }
  if (write) {
    protection |= PROT_WRITE;
  }
  if (exec) {
    protection |= PROT_EXEC;
  }
  if (mprotect(start, length, protection) == 0) {
    return JNI_TRUE; // success
  } else {
    return JNI_FALSE; // failure
  }
#endif // RVM_FOR_HARMONY
}

/**
 * Decommit memory
 * @param start address (Java ADDRESS)
 * @param length of region (Java EXTENT)
 * @return true iff success (Java boolean)
 */
EXTERNAL jboolean sysMemoryDecommit(char *start, size_t length)
{
  SYS_START();
#ifdef RVM_FOR_HARMONY
  HyPortVmemIdentifier ident;
  TRACE_PRINTF("%s: sysMemoryDecommit %p %d\n", Me, start, length);
  ident.pageSize = DefaultPageSize;
  if(hyvmem_decommit_memory(start, length, &ident) == 0) {
    return JNI_TRUE;
  } else {
    return JNI_FALSE;
  }
#else
  TRACE_PRINTF("%s: sysMemoryDecommit %p %zd\n", Me, start, length);
  return JNI_TRUE; // success - unsupported operation for UNIX environments
#endif // RVM_FOR_HARMONY
}

/**
 * @return default page size in bytes (Java int)
 */
EXTERNAL int sysGetPageSize()
{
  SYS_START();
  int result;
#ifndef RVM_FOR_HARMONY
  result = (int)(getpagesize());
#else
  result = DefaultPageSize;
#endif // RVM_FOR_HARMONY
  TRACE_PRINTF("%s: sysGetPageSize %d\n", Me, result);
  return result;
}

/**
 * Sweep through memory to find which areas of memory are mappable.
 * This is invoked from a command-line argument.
 */
EXTERNAL void findMappable()
{
  Address i;
  Address granularity = 1 << 22; // every 4 megabytes
  Address max = (1L << ((sizeof(Address)*8)-2)) / (granularity >> 2);
  int pageSize = sysGetPageSize();
  SYS_START();
  CONSOLE_PRINTF("Attempting to find mappable blocks of size %d\n", pageSize);
  for (i=0; i<max; i++) {
    char *start = (char *) (i * granularity);
    void *result = sysMemoryReserve(start, pageSize, JNI_TRUE, JNI_TRUE, JNI_TRUE, JNI_FALSE);
    if (result == NULL || result != start) {
      CONSOLE_PRINTF("%p %p FAILED\n", start, result);
    } else {
      CONSOLE_PRINTF("%p SUCCESS\n", start);
      sysMemoryFree(start, pageSize);
    }
  }
}
