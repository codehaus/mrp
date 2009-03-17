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
#include <signal.h>

/**
 * Is the given address within the RVM address space?
 *
 * @param addr [in] address to check
 * @return 1 if in address space, else 0
 */
static int inRVMAddressSpace(Address addr)
{
  int which;
  /* get the heap ranges */
  Address *heapRanges = bootRecord->heapRanges;
  for (which = 0; which < MAXHEAPS; which++) {
    Address start = heapRanges[2 * which];
    Address end = heapRanges[2 * which + 1];
    /* Test against sentinel. */
    if (start == ~(Address) 0 && end == ~ (Address) 0) break;
    if (start <= addr  && addr < end) {
      return 1;
    }
  }
  return 0;
}

/**
 * Hardware trap handler
 *
 * @param signo   [in] signal raised
 * @param si      [in] additional signal information
 * @param context [in,out] register contents at the point of the signal
 */
static void hardwareTrapHandler(int signo, siginfo_t *si, void *context)
{
  /** instruction causing trap */
  Address instructionPtr;
  /** instruction following one that trapped */
  Address instructionFollowingPtr;
  /** current thread pointer */
  Address threadPtr;
  /** JTOC ponter */
  Address jtocPtr;
  /** stack frame pointer */
  Address framePtr;
  /** description of trap */
  int trapCode;
  /** extra information such as array index for out-of-bounds traps */
  int trapInfo;
  SYS_START();

  readContextInformation(context, &instructionPtr, &instructionFollowingPtr,
                         &threadPtr, &jtocPtr);
  TRACE_PRINTF("%s: hardwareTrapHandler %d %p - %p %p %p %p\n", Me, signo,
               context, instructionPtr, instructionFollowingPtr, threadPtr, jtocPtr);

  TRACE_PRINTF("%s: hardwareTrapHandler: trap context:\n", Me);
  if(TRACE) dumpContext(context);

  /* die if the signal didn't originate from the RVM */
  if (!inRVMAddressSpace(instructionPtr) || !inRVMAddressSpace(threadPtr)) {
    ERROR_PRINTF("%s: unexpected hardware trap outside of RVM address space - %p %p\n",
                 Me, instructionPtr, threadPtr);
    ERROR_PRINTF("fault address %p\n", si->si_addr);
    dumpContext(context);
    sysExit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }
  /* get frame pointer checking its validity */
  framePtr = readContextFramePointer(context, threadPtr);
  if (!inRVMAddressSpace(framePtr)) {
    ERROR_PRINTF("%s: unexpected hardware trap with frame pointer outside of RVM address space - %p\n",
                 Me, framePtr);
    ERROR_PRINTF("fault address %p\n", si->si_addr);
    dumpContext(context);
    sysExit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }
  /* get trap code and trap info */
  trapCode = readContextTrapCode(context, threadPtr, signo, instructionPtr, &trapInfo);

  Address vmRegisters = *(Address *)((char *)threadPtr + RVMThread_exceptionRegisters_offset);
  unsigned char *inuse = ((unsigned  char*)vmRegisters + Registers_inuse_offset);
  if (*inuse) {
    /* unexpected VM registers in use.. dump VM and die */
    TRACE_PRINTF("%s: VM registers in use whilst delivering hardware trap\n", Me);
    setupDumpStackAndDie(context);
  } else {
    *inuse = 1; /* mark in use */
    setupDeliverHardwareException(context, vmRegisters, trapCode, trapInfo,
				  instructionPtr, instructionFollowingPtr,
				  threadPtr, jtocPtr, framePtr, signo);
  }
  TRACE_PRINTF("%s: hardwareTrapHandler: trap context on exit:\n", Me);
  if(TRACE) dumpContext(context);
}

/**
 * Software signal handler
 *
 * @param signo   [in] signal raised
 * @param si      [in] additional signal information
 * @param context [in,out] register contents at the point of the signal
 */
static void softwareSignalHandler(int signo, siginfo_t UNUSED *si, void *context)
{
  SYS_START();
  TRACE_PRINTF("%s: softwareSignalHandler %d %p\n", Me, signo, context);

  // asynchronous signal used to awaken internal debugger
  if (signo == SIGQUIT) {
    // Turn on debug-request flag.
    unsigned *flag = (unsigned *)((char *)bootRecord->tocRegister + bootRecord->debugRequestedOffset);
    if (*flag) {
      TRACE_PRINTF("%s: debug request already in progress, please wait\n", Me);
    } else {
      TRACE_PRINTF("%s: debug requested, waiting for a thread switch\n", Me);
      *flag = 1;
    }
    return;
  }

  /* We need to adapt this code so that we run the exit handlers
   * appropriately.
   */

  if (signo == SIGTERM) {
    // Presumably we received this signal because someone wants us
    // to shut down.  Exit directly (unless the verbose flag is set).
    // TODO: Run the shutdown hooks instead.
    if (!verbose) {
      /* Now reraise the signal.  We reactivate the signal's
	 default handling, which is to terminate the process.
	 We could just call `exit' or `abort',
	 but reraising the signal sets the return status
	 from the process correctly.
	 TODO: Go run shutdown hooks before we re-raise the signal. */
      signal(signo, SIG_DFL);
      raise(signo);
    }

    TRACE_PRINTF("%s: kill requested: invoking dumpStackAndDie\n", Me);
    setupDumpStackAndDie(context);
    return;
  }

  /* Default case. */
  TRACE_PRINTF("%s: got an unexpected software signal (# %d)", Me, signo);
#if defined __GLIBC__ && defined _GNU_SOURCE
  TRACE_PRINTF(" %s", strsignal(signo));
#endif
  TRACE_PRINTF("; ignoring it.\n");
}

/**
 * Set up signals for a child thread
 *
 * @return data to be provided when this main thread terminates
 */
EXTERNAL void* sysStartMainThreadSignals()
{
  SYS_START();
  /* install a stack for hardwareTrapHandler() to run on */
  stack_t stack;
  char *stackBuf;
  memset (&stack, 0, sizeof stack);
  stackBuf = (char *)sysMalloc(sizeof(char[SIGSTKSZ]));
  stack.ss_sp = stackBuf;
  stack.ss_size = SIGSTKSZ;
  if (sigaltstack (&stack, 0)) {
    ERROR_PRINTF("%s: sigaltstack failed (errno=%d)\n",  Me, errno);
    sysFree(stackBuf);
    return NULL;
  }
  /* install hardware trap signal handler */
  struct sigaction action;

  memset (&action, 0, sizeof action);
  action.sa_sigaction = hardwareTrapHandler;
  /*
   * mask all signal from reaching the signal handler while the signal
   * handler is running
   */
  if (sigfillset(&(action.sa_mask))) {
    ERROR_PRINTF("%s: sigfillset failed (errno=%d)\n", Me, errno);
    return NULL;
  }
  /*
   * exclude the signal used to wake up the daemons
   */
  if (sigdelset(&(action.sa_mask), SIGCONT)) {
    ERROR_PRINTF("%s: sigdelset failed (errno=%d)\n", Me, errno);
    return NULL;
  }
  action.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;
  if (sigaction (SIGSEGV, &action, 0) ||
      sigaction (SIGFPE, &action, 0) ||
      sigaction (SIGTRAP, &action, 0) ||
      sigaction (SIGBUS, &action, 0)) {
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return NULL;
  }

  /* install software signal handler */
  action.sa_sigaction = &softwareSignalHandler;
  if (sigaction (SIGALRM, &action, 0) || /* catch timer ticks (so we can timeslice user level threads) */
      sigaction (SIGQUIT, &action, 0) || /* catch QUIT to invoke debugger thread */
      sigaction (SIGTERM, &action, 0)) { /* catch TERM to dump and die */
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return NULL;
  }

  /* ignore "write (on a socket) with nobody to read it" signals so
   * that sysWriteBytes() will get an EPIPE return code instead of
   * trapping.
   */
  memset (&action, 0, sizeof action);
  action.sa_handler = SIG_IGN;
  if (sigaction(SIGPIPE, &action, 0)) {
    ERROR_PRINTF("%s: sigaction failed (errno=%d)\n", Me, errno);
    return NULL;
  }
  return stackBuf;
}

/**
 * Set up signals for a child thread
 *
 * @return data to be provided when this child thread terminate
 */
EXTERNAL void* sysStartChildThreadSignals()
{
  stack_t stack;
  char *stackBuf;
  int rc;
  SYS_START();
  TRACE_PRINTF("%s: sysSetupChildThreadSignals\n", Me);

  memset (&stack, 0, sizeof stack);
  stackBuf = (char*)sysMalloc(sizeof(char[SIGSTKSZ]));
  stack.ss_sp = stackBuf;
  stack.ss_flags = 0;
  stack.ss_size = SIGSTKSZ;
  if (sigaltstack (&stack, 0)) {
    ERROR_PRINTF("sigaltstack failed (errno=%d)\n",errno);
    sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
  }
  /*
   * Block the CONT signal.  This makes SIGCONT reach this
   * pthread only when this pthread performs a sigwait().
   */
  sigset_t input_set, output_set;
  sigemptyset(&input_set);
  sigaddset(&input_set, SIGCONT);

#ifdef RVM_FOR_AIX
  rc = sigthreadmask(SIG_BLOCK, &input_set, &output_set);
  /* like pthread_sigmask, sigthreadmask can only return EINVAL, EFAULT, and
   * EPERM.  Again, these are all good reasons to complain and croak. */
#else
  rc = pthread_sigmask(SIG_BLOCK, &input_set, &output_set);
  /* pthread_sigmask can only return the following errors.  Either of them
   * indicates serious trouble and is grounds for aborting the process:
   * EINVAL EFAULT.  */
#endif
  if (rc) {
    ERROR_PRINTF ("pthread_sigmask or sigthreadmask failed (errno=%d):", errno);
    sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
  }
  return stackBuf;
}

/**
 * Finish use of signals for a child thread
 *
 * @param stackBuf [in] data provided by sysStartChildThreadSignals
 */
EXTERNAL void sysEndThreadSignals(void *stackBuf)
{
  stack_t stack;
  memset (&stack, 0, sizeof stack);
  stack.ss_flags = SS_DISABLE;
  sigaltstack(&stack, 0);
  sysFree(stackBuf);
}
