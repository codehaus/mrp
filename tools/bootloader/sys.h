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

#ifndef RVM_SYSCALL_DEFINITIONS
#define RVM_SYSCALL_DEFINITIONS

#include <stdio.h>
#define NEED_VIRTUAL_MACHINE_DECLARATIONS 1
#define NEED_EXIT_STATUS_CODES 1
#include <InterfaceDeclarations.h>
#include "bootImageRunner.h"
#include "cAttributePortability.h"

#ifdef __cplusplus
#define EXTERNAL extern "C"
#else
#define EXTERNAL
#endif

#ifdef RVM_FOR_HARMONY
#ifdef RVM_FOR_LINUX
#  define LINUX 1
#endif
#include <hycomp.h>
#include <hyport.h>
#include <vmi.h>
EXTERNAL VMInterface vmi;
EXTERNAL void VMI_Initialize();
EXTERNAL UDATA DefaultPageSize;
#endif

/** Macro that starts all sys related functions */
#ifdef RVM_FOR_HARMONY
#define SYS_START()  PORT_ACCESS_FROM_VMI(VMI_GetVMIFromJavaVM(&sysJavaVM))
#else
#define SYS_START()
#endif

/** Sink for messages relating to serious errors detected by C runtime. */
#ifndef RVM_FOR_HARMONY
extern FILE *SysErrorFile;
#endif

/** Sink for trace messages produced by VM.sysWrite(). */
#ifndef RVM_FOR_HARMONY
extern FILE *SysTraceFile;
#endif

/** Portable default printf */
#ifdef RVM_FOR_HARMONY
#define CONSOLE_PRINTF(...) hytty_err_printf(PORTLIB, __VA_ARGS__)
#else
#define CONSOLE_PRINTF(...) fprintf(SysTraceFile, __VA_ARGS__)
#endif

/** Portable error printf */
#ifdef RVM_FOR_HARMONY
#define ERROR_PRINTF(...) hytty_err_printf(PORTLIB, __VA_ARGS__)
#else
#define ERROR_PRINTF(...) fprintf(SysErrorFile, __VA_ARGS__)
#endif

/** String used for name of RVM */
extern char *Me;

/** Trace execution of syscalls */
#define TRACE 0

#ifdef RVM_FOR_HARMONY
#define TRACE_PRINTF(...) if(TRACE)hytty_err_printf(PORTLIB, __VA_ARGS__)
#else
#define TRACE_PRINTF(...) if(TRACE) fprintf(SysTraceFile, __VA_ARGS__)
#endif

/* Routines used elsewhere within boot image runner */

EXTERNAL void findMappable();
EXTERNAL long long sysNanoTime();
EXTERNAL void sysExit(int) NORETURN;

#endif // RVM_SYSCALL_DEFINITIONS
