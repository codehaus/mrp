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
#include <setjmp.h>
#include <stdlib.h>
#include <string.h>

#ifndef RVM_FOR_HARMONY
#include <errno.h>
#include <pthread.h>
#ifdef RVM_FOR_LINUX
#  include <sys/sysinfo.h>
#  include <sys/ucontext.h>
#endif // def RVM_FOR_LINUX
#endif // ndef RVM_FOR_HARMONY

#ifdef RVM_FOR_HARMONY
#define TLS_KEY_TYPE hythread_tls_key_t
#else
#define TLS_KEY_TYPE pthread_key_t
typedef struct {
  pthread_mutex_t mutex;
  pthread_cond_t cond;
} vmmonitor_t;
#endif

/** keys for managing thread termination */
static TLS_KEY_TYPE TerminateJmpBufKey;
static TLS_KEY_TYPE VmThreadKey;
static TLS_KEY_TYPE IsVmThreadKey;
static Address DeathLock;
static int systemExiting = 0;

/** Constant to show that the newly created thread is a child */
static const Address CHILD_THREAD=0;
/**
 * Constant to show that the newly created thread is the main thread
 * and can terminate after execution (ie. the VM is running as a child
 * thread of a larger process).
 */
static const Address MAIN_THREAD_ALLOW_TERMINATE=1;
/**
 * Constant to show that the newly created thread is the main thread
 * and shouldn't terminate. This is used when the VM isn't running as
 * part of a larger system and terminating the main thread will stop
 * the process.
 */
static const Address MAIN_THREAD_DONT_TERMINATE=2;

/* Function prototypes */
#ifdef RVM_FOR_HARMONY
static int sysThreadStartup(void *args);
#else
static void* sysThreadStartup(void *args);
#endif

/** Initialize for sysCalls */
EXTERNAL void sysInitialize()
{
#ifdef RVM_FOR_HARMONY
  VMInterface *vmi;
  HyPortLibrary *privatePortLibrary;
  VMI_Initialize();
  vmi = VMI_GetVMIFromJavaVM((JavaVM*)(&sysJavaVM));
  privatePortLibrary = (*vmi)->GetPortLibrary(vmi);
  DefaultPageSize = hyvmem_supported_page_sizes()[0];
#else
#ifdef __MACH__
  // Initialize timer information on OS/X
  (void) mach_timebase_info(&timebaseInfo);
#endif // __MACH__
#endif // RVM_FOR_HARMONY
  DeathLock = sysMonitorCreate();
}

/** Exit with a return code. */
EXTERNAL void sysExit(int value)
{
  SYS_START();
  TRACE_PRINTF("%s: sysExit %d\n", Me, value);
  // alignment checking: report info before exiting, then turn off checking
#ifdef RVM_WITH_ALIGNMENT_CHECKING
  if (numEnableAlignCheckingCalls > 0) {
    sysReportAlignmentChecking();
    sysDisableAlignmentChecking();
  }
#endif // RVM_WITH_ALIGNMENT_CHECKING

#ifndef RVM_FOR_HARMONY
  fflush(SysErrorFile);
  fflush(SysTraceFile);
  fflush(stdout);
#endif
  systemExiting = 1;
  if (DeathLock != (Address)NULL) {
    sysMonitorEnter(DeathLock);
  }
#ifndef RVM_FOR_HARMONY
  exit(value);
#else
  hyexit_shutdown_and_exit(value);
#endif
}

/** Utility to create a thread local key */
static TLS_KEY_TYPE createThreadLocal()
{
  SYS_START();
  TLS_KEY_TYPE key;
  int rc;
#ifdef RVM_FOR_HARMONY
  rc = hythread_tls_alloc(&key);
#else
  rc = pthread_key_create(&key, 0);
#endif
  if (rc != 0) {
    ERROR_PRINTF("%s: alloc tls key failed (err=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
  return key;
}

/**
 * Utility to get a thread local key
 *
 * @param key [in] thread local key
 * @return value associated with key
 */
static void* getThreadLocal(TLS_KEY_TYPE key) {
#ifdef RVM_FOR_HARMONY
  return hythread_tls_get(hythread_self(), key);
#else
  return pthread_getspecific(key);
#endif
}

/** Utility to set a thread local key */
static void setThreadLocal(TLS_KEY_TYPE key, void *value) {
  SYS_START();
#ifdef RVM_FOR_HARMONY
  int rc = hythread_tls_set(hythread_self(), key, value);
#else
  int rc = pthread_setspecific(key, value);
#endif
  if (rc != 0) {
    ERROR_PRINTF("%s: set tls failed (err=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
}

/**
 * Stash address of the Thread object in the thread-specific data for
 * the current native thread.  This allows us to get a handle on the
 * Thread (and its associated state) from arbitrary native code.
 */
EXTERNAL void sysStashVMThread(Address vmThread)
{
  SYS_START();
  TRACE_PRINTF("%s: sysStashVmProcessorInPthread %p\n", Me, vmThread);
  setThreadLocal(VmThreadKey, (void*)vmThread);
}

/** Read the VMThread stashed earlier, used in jvm.C */
EXTERNAL void * getVMThread()
{
  SYS_START();
  TRACE_PRINTF("%s: getVMThread\n", Me);
  return getThreadLocal(VmThreadKey);
}

/** Create keys for thread-specific data. */
static void createThreadSpecificDataKeys()
{
  int rc;
  SYS_START();
  TRACE_PRINTF("%s: sysThreadSpecificDataKeys\n", Me);

  // Create a key for thread-specific data so we can associate
  // the id of the Processor object with the pthread it is running on.
  VmThreadKey = createThreadLocal();
  TerminateJmpBufKey = createThreadLocal();
  TRACE_PRINTF("%s: vm processor key=%u\n", Me, VmThreadKey);
}

/**
 * How many physical cpu's are present and actually online?
 * Assume 1 if no other good ansewr.
 * @return number of cpu's
 *
 * Note: this function is only called once.  If it were called more often
 * than that, we would want to use a static variable to indicate that we'd
 * already printed the WARNING messages and were not about to print any more.
 */
EXTERNAL int sysNumProcessors()
{
  int numCpus = -1;  /* -1 means failure. */
  SYS_START();
  TRACE_PRINTF("%s: sysNumProcessors\n", Me);  
#ifdef RVM_FOR_HARMONY
  numCpus = hysysinfo_get_number_CPUs();
#else
#ifdef __GNU_LIBRARY__      // get_nprocs is part of the GNU C library.
  /* get_nprocs_conf will give us a how many processors the operating
     system configured.  The number of processors actually online is what
     we want.  */
  // numCpus = get_nprocs_conf();
  errno = 0;
  numCpus = get_nprocs();
  // It is not clear if get_nprocs can ever return failure; assume it might.
  if (numCpus < 1) {
    CONSOLE_PRINTF("%s: WARNING: get_nprocs() returned %d (errno=%d)\n",
                   Me, numCpus, errno);
    /* Continue on.  Try to get a better answer by some other method, not
       that it's likely, but this should not be a fatal error. */
  }
#endif

#if defined(CTL_HW) && defined(HW_NCPU)
  if (numCpus < 1) {
    int mib[2];
    size_t len;
    mib[0] = CTL_HW;
    mib[1] = HW_NCPU;
    len = sizeof(numCpus);
    errno = 0;
    if (sysctl(mib, 2, &numCpus, &len, NULL, 0) < 0) {
      CONSOLE_PRINTF("%s: WARNING: sysctl(CTL_HW,HW_NCPU) failed;"
                                   " errno = %d\n", Me, errno);
      numCpus = -1;       // failed so far...
    };
  }
#endif

#if defined(_SC_NPROCESSORS_ONLN)
  if (numCpus < 0) {
    /* This alternative is probably the same as
     *  _system_configuration.ncpus.  This one says how many CPUs are
     *  actually on line.  It seems to be supported on AIX, at least; I
     *  yanked this out of sysVirtualProcessorBind.
     */
    numCpus = sysconf(_SC_NPROCESSORS_ONLN); // does not set errno
    if (numCpus < 0) {
      ERROR_PRINTF("%s: WARNING: sysconf(_SC_NPROCESSORS_ONLN) failed\n", Me);
    }
  }
#endif

#ifdef _AIX
  if (numCpus < 0) {
    numCpus = _system_configuration.ncpus;
    if (numCpus < 0) {
      ERROR_PRINTF("%s: WARNING: _system_configuration.ncpus"
                   " has the insane value %d\n" , Me, numCpus);
    }
  }
#endif
#endif // RVM_FOR_HARMONY

  if (numCpus < 0) {
    TRACE_PRINTF("%s: WARNING: Can not figure out how many CPUs"
                               " are online; assuming 1\n");
    numCpus = 1;            // Default
  }
  TRACE_PRINTF("%s: sysNumProcessors: returning %d\n", Me, numCpus);
  return numCpus;
}

/**
 * Create main thread
 *
 * @param vmInSeperateThread [in] should the VM be placed in a
 * separate thread
 * @param ip [in] address of VM.boot method
 * @param sp [in,out] address of stack
 * @param tr [in,out] address of thread data structure
 * @param jtoc [in,out] address of jtoc
 */
EXTERNAL void sysStartMainThread(jboolean vmInSeparateThread, Address ip, Address sp, Address tr, Address jtoc, uint32_t *bootCompleted)
{
  Address        *sysThreadArguments;
#ifndef RVM_FOR_HARMONY
  pthread_attr_t sysThreadAttributes;
  pthread_t      sysThreadHandle;
#else
  hythread_t     sysThreadHandle;
#endif
  int            rc;
  SYS_START();
  TRACE_PRINTF("%s: sysStartMainThread %d\n", Me, vmInSeparateThread);

  createThreadSpecificDataKeys();

  /* Set up thread stack - TODO: move to bootimagewriter */
#ifdef RVM_FOR_IA32
  *(Address *) (tr + Thread_framePointer_offset) = (Address)sp - (2*__SIZEOF_POINTER__);
  sp-=__SIZEOF_POINTER__;
  *(uint32_t*)sp = 0xdeadbabe;         /* STACKFRAME_RETURN_ADDRESS_OFFSET */
  sp -= __SIZEOF_POINTER__;
  *(Address*)sp = Constants_STACKFRAME_SENTINEL_FP; /* STACKFRAME_FRAME_POINTER_OFFSET */
  sp -= __SIZEOF_POINTER__;
  ((Address *)sp)[0] = Constants_INVISIBLE_METHOD_ID;    /* STACKFRAME_METHOD_ID_OFFSET */
#else
  Address  fp = sp - Constants_STACKFRAME_HEADER_SIZE;  // size in bytes
  fp = fp & ~(Constants_STACKFRAME_ALIGNMENT -1);     // align fp
  *(Address *)(fp + Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET) = ip;
  *(int *)(fp + Constants_STACKFRAME_METHOD_ID_OFFSET) = Constants_INVISIBLE_METHOD_ID;
  *(Address *)(fp + Constants_STACKFRAME_FRAME_POINTER_OFFSET) = Constants_STACKFRAME_SENTINEL_FP;
  sp = fp;
#endif

  /* create arguments - memory reclaimed in sysThreadStartup */
  sysThreadArguments = (Address *)sysMalloc(sizeof(Address[5]));
  sysThreadArguments[0] = ip;
  sysThreadArguments[1] = sp;
  sysThreadArguments[2] = tr;
  sysThreadArguments[3] = jtoc;
  if (!vmInSeparateThread) {
    sysThreadArguments[4] = MAIN_THREAD_DONT_TERMINATE;
    sysThreadStartup(sysThreadArguments);
  } else {
    *bootCompleted = 0;
    sysThreadArguments[4] = MAIN_THREAD_ALLOW_TERMINATE;
#ifndef RVM_FOR_HARMONY
    // create attributes
    rc = pthread_attr_init(&sysThreadAttributes);
    if (rc) {
      ERROR_PRINTF("%s: pthread_attr_init failed (rc=%d)\n", Me, rc);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
    // force 1:1 pthread to kernel thread mapping (on AIX 4.3)
    pthread_attr_setscope(&sysThreadAttributes, PTHREAD_SCOPE_SYSTEM);
#endif
    // create native thread
#ifdef RVM_FOR_HARMONY
    rc = hythread_create(&sysThreadHandle, 0, HYTHREAD_PRIORITY_NORMAL, 0,
                         (hythread_entrypoint_t)sysThreadStartup,
                         sysThreadArguments);
#else
    rc = pthread_create(&sysThreadHandle,
                        &sysThreadAttributes,
                        sysThreadStartup,
                        sysThreadArguments);
#endif
    if (rc)
    {
      ERROR_PRINTF("%s: thread_create failed (rc=%d)\n", Me, rc);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#ifndef RVM_FOR_HARMONY
    rc = pthread_detach(sysThreadHandle);
    if (rc)
    {
      ERROR_PRINTF("%s: pthread_detach failed (rc=%d)\n", Me, rc);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif
    /* exit start up when VM has booted */
    while (*bootCompleted == 0) {
      sysThreadYield();
    }
  }
}

/**
 * Create a native thread
 *
 * @param ip [in] address of first instruction
 * @param fp [in] address of stack
 * @param tr [in] address for thread register
 * @param jtoc [in] address for the jtoc register
 * @param jtoc [in] address for the jtoc register
 * @return OS handle
 */
EXTERNAL Address sysThreadCreate(Address ip, Address fp, Address tr, Address jtoc)
{
  Address        *sysThreadArguments;
#ifndef RVM_FOR_HARMONY
  pthread_attr_t sysThreadAttributes;
  pthread_t      sysThreadHandle;
#else
  hythread_t     sysThreadHandle;
#endif
  int            rc;
  SYS_START();
  TRACE_PRINTF("%s: sysThreadCreate %p %p %p %p\n", Me, ip, fp, tr, jtoc);

  /* create arguments - memory reclaimed in sysThreadStartup */
  sysThreadArguments = (Address *)sysMalloc(sizeof(Address[5]));
  sysThreadArguments[0] = ip;
  sysThreadArguments[1] = fp;
  sysThreadArguments[2] = tr;
  sysThreadArguments[3] = jtoc;
  sysThreadArguments[4] = CHILD_THREAD;

#ifndef RVM_FOR_HARMONY
  // create attributes
  if ((rc = pthread_attr_init(&sysThreadAttributes))) {
    ERROR_PRINTF("%s: pthread_attr_init failed (rc=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
  // force 1:1 pthread to kernel thread mapping (on AIX 4.3)
  pthread_attr_setscope(&sysThreadAttributes, PTHREAD_SCOPE_SYSTEM);
#endif
  // create native thread
#ifdef RVM_FOR_HARMONY
  rc = hythread_create(&sysThreadHandle, 0, HYTHREAD_PRIORITY_NORMAL, 0,
                       (hythread_entrypoint_t)sysThreadStartup,
                       sysThreadArguments);
#else
  rc = pthread_create(&sysThreadHandle,
                      &sysThreadAttributes,
                      sysThreadStartup,
                      sysThreadArguments);
#endif
  if (rc)
  {
    ERROR_PRINTF("%s: thread_create failed (rc=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }

#ifndef RVM_FOR_HARMONY
  rc = pthread_detach(sysThreadHandle);
  if (rc)
  {
    ERROR_PRINTF("%s: pthread_detach failed (rc=%d)\n", Me, rc);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
#endif
  TRACE_PRINTF("%s: pthread_create %p\n", Me, (Address) sysThreadHandle);

  return (Address)sysThreadHandle;
}

/**
 * Function called by pthread startup
 *
 * @param args [in] encoded of initial register values and main thread
 * controls
 * @return ignored
 */
#ifdef RVM_FOR_HARMONY
static int sysThreadStartup(void *args)
#else
static void* sysThreadStartup(void *args)
#endif
{
  Address jtoc, tr, ip, fp, threadData;
  jmp_buf *jb;
  void *sigStack;

  SYS_START();
  ip = ((Address *)args)[0];
  fp = ((Address *)args)[1];
  tr = ((Address *)args)[2];
  jtoc = ((Address *)args)[3];
  threadData = ((Address *)args)[4];
  TRACE_PRINTF("%s: sysThreadStartup: ip=%p fp=%p tr=%p jtoc=%p data=%d\n",
	       Me, ip, fp, tr, jtoc, (int)threadData);
  sysFree(args);
  if (threadData == CHILD_THREAD) {
    sigStack = sysStartChildThreadSignals();
#ifdef RVM_FOR_IA32 /* TODO: refactor */
    *(Address *)(tr + Thread_framePointer_offset) = fp;
    fp = fp + Constants_STACKFRAME_BODY_OFFSET;
#endif
  } else {
    sigStack = sysStartMainThreadSignals();
  }
  jb = (jmp_buf*)sysMalloc(sizeof(jmp_buf));
  if (setjmp(*jb)) {
    // this is where we come to terminate the thread
    TRACE_PRINTF("%s: sysThreadStartup: terminating\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_detach(NULL);
#endif
    sysFree(jb);
    *(int*)(tr + RVMThread_execStatus_offset) = RVMThread_TERMINATED;
    sysEndThreadSignals(sigStack);
    if (threadData == MAIN_THREAD_DONT_TERMINATE) {
      while(1) {
#ifndef RVM_FOR_HARMONY
	pause();
#else
	hythread_sleep(-1);
#endif
      }
    }
  } else {
    TRACE_PRINTF("%s: sysThreadStartup: booting\n", Me);
    setThreadLocal(TerminateJmpBufKey, (void*)jb);
    // branch to vm code
    bootThread((void*)ip, (void*)tr, (void*)fp, (void*)jtoc);
    // not reached
    ERROR_PRINTF("%s: sysThreadStartup: failed\n", Me);
  }
  return 0;
}

/** Terminate a thread */
EXTERNAL void sysThreadTerminate()
{
  jmp_buf *jb;
  SYS_START();
  TRACE_PRINTF("%s: sysThreadTerminate\n", Me);
#ifdef RVM_FOR_POWERPC
  asm("sync");
#endif
  jb = (jmp_buf*)getThreadLocal(TerminateJmpBufKey);
  longjmp(*jb,1);
}

EXTERNAL int sysThreadBindSupported()
{
  int result=0;
  SYS_START();
  TRACE_PRINTF("%s: sysThreadBindSupported");
#ifdef RVM_FOR_AIX
  result=1;
#endif
#ifdef RVM_FOR_LINUX
  result=1;
#endif
  return result;
}

/**
 * Bind execution of current thread to specified physical cpu.
 * Taken:    physical cpu id (0, 1, 2, ...)
 * Returned: nothing
 */
EXTERNAL void sysThreadBind(int UNUSED cpuId)
{
  SYS_START();
  TRACE_PRINTF("%s: sysThreadBind");
#ifndef RVM_FOR_HARMONY
#ifdef RVM_FOR_AIX
  // bindprocessor() seems to be only on AIX
  int rc = bindprocessor(BINDTHREAD, thread_self(), cpuId);
  fprintf("%s: bindprocessor pthread %d (kernel thread %d) %s to cpu %d\n", Me, pthread_self(), thread_self(), (rc ? "NOT bound" : "bound"), cpuId);

  if (rc) {
    ERROR_PRINTF("%s: bindprocessor failed (errno=%d): ", Me, errno);
    perror(NULL);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
#endif

#if defined RVM_FOR_LINUX && defined __USE_GNU
  /* GNU features for thread binding */
  cpu_set_t cpuset;
  CPU_ZERO(&cpuset);
  CPU_SET(cpuId, &cpuset);

  pthread_setaffinity_np(pthread_self(), sizeof(cpuset), &cpuset);
#endif
#endif
}

/** Return the thread ID of the current thread */
EXTERNAL Address sysThreadSelf()
{
  void *thread;
  SYS_START();
#ifdef RVM_FOR_HARMONY
  thread = hythread_self();
#else
  thread = (void*)pthread_self();
#endif
  TRACE_PRINTF("%s: sysThreadSelf: thread %p\n", Me, thread);
  return (Address)thread;
}

/**
 * Yield execution of current thread back to o/s.
 */
EXTERNAL void sysThreadYield()
{
  SYS_START();
  TRACE_PRINTF("%s: sysThreadYield\n", Me);
#ifdef RVM_FOR_HARMONY
  hythread_yield();
#else
  /*  According to the Linux manpage, sched_yield()'s presence can be
   *  tested for by using the #define _POSIX_PRIORITY_SCHEDULING, and if
   *  that is not present to use the sysconf feature, searching against
   *  _SC_PRIORITY_SCHEDULING.  However, I don't really trust it, since
   *  the AIX 5.1 include files include this definition:
   *      ./unistd.h:#undef _POSIX_PRIORITY_SCHEDULING
   *  so my trust that it is implemented properly is scanty.  --augart
   */
  sched_yield();
#endif // RVM_FOR_HARMONY
}


/**
 * Routine to sleep for a number of nanoseconds (howLongNanos).  This is
 * ridiculous on regular Linux, where we actually only sleep in increments of
 * 1/HZ (1/100 of a second on x86).  Luckily, Linux will round up.
 *
 * This is just used internally in the scheduler, but we might as well make
 * the function work properly even if it gets used for other purposes.
 *
 * We don't return anything, since we don't need to right now.  Just try to
 * sleep; if interrupted, return.
 */
EXTERNAL void sysNanoSleep(long long howLongNanos)
{
  SYS_START();
  TRACE_PRINTF("%s: sysNanosleep %lld\n", Me, howLongNanos);
#ifdef RVM_FOR_HARMONY
  hythread_sleep(howLongNanos/1000);
#else
  struct timespec req;
  const long long nanosPerSec = 1000LL * 1000 * 1000;
  req.tv_sec = howLongNanos / nanosPerSec;
  req.tv_nsec = howLongNanos % nanosPerSec;
  int ret = nanosleep(&req, (struct timespec *) NULL);
  if (ret < 0) {
    if (errno == EINTR)
       /* EINTR is expected, since we do use signals internally. */
      return;
    ERROR_PRINTF("%s: nanosleep(<tv_sec=%ld,tv_nsec=%ld>) failed:"
                   " %s (errno=%d)\n"
                   "  That should never happen; please report it as a bug.\n",
                   Me, req.tv_sec, req.tv_nsec,
                   strerror( errno ), errno);
  }
#endif
}

EXTERNAL Address sysMonitorCreate()
{
  SYS_START();
#ifdef RVM_FOR_HARMONY
  hythread_monitor_t monitor;
  hythread_monitor_init_with_name(&monitor, 0, NULL);
#else
  vmmonitor_t *monitor = (vmmonitor_t*)sysMalloc(sizeof(vmmonitor_t));
  pthread_mutex_init(&monitor->mutex, NULL);
  pthread_cond_init(&monitor->cond, NULL);
#endif
  TRACE_PRINTF("%s: sysMonitorCreate %p\n", Me, monitor);
  return (Address)monitor;
}

EXTERNAL void sysMonitorDestroy(Address _monitor)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMonitorDestroy\n", Me);
#ifdef RVM_FOR_HARMONY
  hythread_monitor_destroy((hythread_monitor_t)_monitor);
#else
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  pthread_mutex_destroy(&monitor->mutex);
  pthread_cond_destroy(&monitor->cond);
  sysFree(monitor);
#endif
}

EXTERNAL void sysMonitorEnter(Address _monitor)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMonitorEnter %p\n", Me, _monitor);
#ifdef RVM_FOR_HARMONY
  hythread_monitor_enter((hythread_monitor_t)_monitor);
#else
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  pthread_mutex_lock(&monitor->mutex);
#endif
}

EXTERNAL void sysMonitorExit(Address _monitor)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMonitorExit %p\n", Me, _monitor);
#ifdef RVM_FOR_HARMONY
  hythread_monitor_exit((hythread_monitor_t)_monitor);
#else
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  pthread_mutex_unlock(&monitor->mutex);
#endif
}

EXTERNAL void sysMonitorTimedWaitAbsolute(Address _monitor, long long whenWakeupNanos)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMonitorTimedWaitAbsolute %ld\n", Me, whenWakeupNanos);
#ifdef RVM_FOR_HARMONY
  // syscall wait is absolute, but harmony monitor wait is relative.
  whenWakeupNanos -= sysNanoTime();
  if (whenWakeupNanos <= 0) return;
  TRACE_PRINTF("%s: sysMonitorTimedWaitAbsolute - wait for %ld %d\n", Me, (I_64)(whenWakeupNanos / 1000000LL), (IDATA)(whenWakeupNanos % 1000000LL));
  hythread_monitor_wait_timed((hythread_monitor_t)_monitor, (I_64)(whenWakeupNanos / 1000000LL), (IDATA)(whenWakeupNanos % 1000000LL));
#else
  struct timespec ts;
  ts.tv_sec = (time_t)(whenWakeupNanos/1000000000LL);
  ts.tv_nsec = (long)(whenWakeupNanos%1000000000LL);
  TRACE_PRINTF("starting wait at %lld until %lld (%ld, %ld)\n",
               sysNanoTime(),whenWakeupNanos,ts.tv_sec,ts.tv_nsec);
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  int rc = pthread_cond_timedwait(&monitor->cond, &monitor->mutex, &ts);
  TRACE_PRINTF("returned from wait at %lld instead of %lld with res = %d\n",
               sysNanoTime(),whenWakeupNanos,rc);
#endif
}

EXTERNAL void sysMonitorWait(Address _monitor)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMonitorWait\n", Me);
#ifdef RVM_FOR_HARMONY
  hythread_monitor_wait((hythread_monitor_t)_monitor);
#else
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  pthread_cond_wait(&monitor->cond, &monitor->mutex);
#endif
}

EXTERNAL void sysMonitorNotifyAll(Address _monitor)
{
  SYS_START();
  TRACE_PRINTF("%s: sysMonitorBroadcast\n", Me);
#ifdef RVM_FOR_HARMONY
  hythread_monitor_notify_all((hythread_monitor_t)_monitor);
#else
  vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
  pthread_cond_broadcast(&monitor->cond);
#endif
}
