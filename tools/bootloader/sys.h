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

#ifndef RVM_SYSCALL_DEFINITIONS
#define RVM_SYSCALL_DEFINITIONS

#include <stdio.h>
#include <jni.h>
#include "cAttributePortability.h"
#ifdef __MACH__
#include <mach/mach_time.h>
#endif
#ifndef _WIN32
#include <stdint.h>
#endif

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

#ifdef _WIN32
typedef __int32 int32_t;
typedef unsigned __int32 uint32_t;
typedef __int32 int32_t;
typedef unsigned __int32 uint32_t;
typedef __int64 int64_t;
typedef unsigned __int64 uint64_t;
typedef __int64 int64_t;
typedef unsigned __int64 uint64_t;
#endif

#ifdef RVM_FOR_32_ADDR
typedef uint32_t Address;
typedef  int32_t Offset;
typedef uint32_t Extent;
typedef uint32_t Word;
#else
typedef uint64_t Address;
typedef  int64_t Offset;
typedef uint64_t Extent;
typedef uint64_t Word;
#endif

#ifndef __SIZEOF_POINTER__
#  ifdef RVM_FOR_32_ADDR
#    define __SIZEOF_POINTER__ 4
#  else
#    define __SIZEOF_POINTER__ 8
#  endif
#endif

/** Macro that starts all sys related functions */
#ifdef RVM_FOR_HARMONY
#define SYS_START()  PORT_ACCESS_FROM_VMI(VMI_GetVMIFromJavaVM((JavaVM*)(&sysJavaVM)))
#else
#define SYS_START()
#endif

#ifndef RVM_FOR_HARMONY
/** Sink for messages relating to serious errors detected by C runtime. */
extern FILE *SysErrorFile;
#endif

#ifndef RVM_FOR_HARMONY
/** Sink for trace messages produced by VM.sysWrite(). */
extern FILE *SysTraceFile;
#endif

/** Portable default printf */
#ifdef RVM_FOR_HARMONY
#define CONSOLE_PRINTF(...) hytty_printf(PORTLIB, __VA_ARGS__)
#else
#define CONSOLE_PRINTF(...) printf(__VA_ARGS__)
#endif

/** Portable error printf */
#ifdef RVM_FOR_HARMONY
#define ERROR_PRINTF(...) hytty_err_printf(PORTLIB, __VA_ARGS__)
#else
#define ERROR_PRINTF(...) fprintf(SysErrorFile, __VA_ARGS__)
#endif

#if defined(NOT_JVM_DLL)
#define DLLSYMBOL JNIIMPORT
#else
#define DLLSYMBOL JNIEXPORT
#endif

/** String used for name of RVM */
extern DLLSYMBOL char *Me;

/** Number of Java args */
extern DLLSYMBOL int JavaArgc;

/** Java args */
extern DLLSYMBOL char **JavaArgs;

/** C access to shared C/Java boot record data structure */
extern DLLSYMBOL struct BootRecord *bootRecord;

/** JVM datastructure used for JNI declared in jvm.c */
extern DLLSYMBOL const struct JavaVM_ sysJavaVM;

/** Verbose command line option */
extern DLLSYMBOL int verbose;

/** File name for part of boot image containing code */
extern DLLSYMBOL char *bootCodeFilename;

/** File name for part of boot image containing data */
extern DLLSYMBOL char *bootDataFilename;

/** File name for part of boot image containing the root map */
extern DLLSYMBOL char *bootRMapFilename;

/** Specified or default initial heap size */
extern DLLSYMBOL uint64_t initialHeapSize;

/** Specified or default maximum heap size */
extern DLLSYMBOL uint64_t maximumHeapSize;

/** Verbose boot up set */
extern DLLSYMBOL int verboseBoot;

/** JNI standard JVM initialization arguments */
extern DLLSYMBOL JavaVMInitArgs *sysInitArgs;

#ifdef RVM_WITH_ALIGNMENT_CHECKING
extern volatile int numEnableAlignCheckingCalls;
#endif // RVM_WITH_ALIGNMENT_CHECKING

/** Trace execution of syscalls */
#define TRACE verbose

#ifdef RVM_FOR_HARMONY
#  define TRACE_PRINTF(...) if(TRACE) hytty_err_printf(PORTLIB, __VA_ARGS__)
#else
#  define TRACE_PRINTF(...) if(TRACE) fprintf(SysTraceFile, __VA_ARGS__)
#endif

/* String utilities */
#define STREQUAL(s1, s2) (strcmp(s1, s2) == 0)
#define STRNEQUAL(s1, s2, n) (strncmp(s1, s2, n) == 0)

/* Console declarations */

EXTERNAL void sysConsoleWriteChar(unsigned value);
EXTERNAL void sysConsoleWriteInteger(int value, int hexToo);
EXTERNAL void sysConsoleWriteLong(long long value, int hexToo);
EXTERNAL void sysConsoleWriteDouble(double value,  int postDecimalDigits);

/* IO declarations */

EXTERNAL int sysReadByte(int fd);
EXTERNAL int sysWriteByte(int fd, int data);
EXTERNAL int sysReadBytes(int fd, char *buf, int cnt);
EXTERNAL int sysWriteBytes(int fd, char *buf, int cnt);

/* Library declarations */

EXTERNAL void* sysDlopen(char *libname);
EXTERNAL void* sysDlsym(Address libHandler, char *symbolName, char *argSignature);

/* Math declarations */

EXTERNAL long long sysLongDivide(long long a, long long b);
EXTERNAL long long sysLongRemainder(long long a, long long b);
EXTERNAL double sysLongToDouble(long long a);
EXTERNAL float sysLongToFloat(long long a);
EXTERNAL int sysFloatToInt(float a);
EXTERNAL int sysDoubleToInt(double a);
EXTERNAL long long sysFloatToLong(float a);
EXTERNAL long long sysDoubleToLong(double a);
EXTERNAL double sysDoubleRemainder(double a, double b);
EXTERNAL float sysPrimitiveParseFloat(const char * buf);
EXTERNAL int sysPrimitiveParseInt(const char * buf);
EXTERNAL double sysVMMathSin(double a);
EXTERNAL double sysVMMathCos(double a);
EXTERNAL double sysVMMathTan(double a);
EXTERNAL double sysVMMathAsin(double a);
EXTERNAL double sysVMMathAcos(double a);
EXTERNAL double sysVMMathAtan(double a);
EXTERNAL double sysVMMathAtan2(double a, double b);
EXTERNAL double sysVMMathCosh(double a);
EXTERNAL double sysVMMathSinh(double a);
EXTERNAL double sysVMMathTanh(double a);
EXTERNAL double sysVMMathExp(double a);
EXTERNAL double sysVMMathLog(double a);
EXTERNAL double sysVMMathSqrt(double a);
EXTERNAL double sysVMMathPow(double a, double b);
EXTERNAL double sysVMMathIEEEremainder(double a, double b);
EXTERNAL double sysVMMathCeil(double a);
EXTERNAL double sysVMMathFloor(double a);
EXTERNAL double sysVMMathRint(double a);
EXTERNAL double sysVMMathRint(double a);
EXTERNAL double sysVMMathCbrt(double a);
EXTERNAL double sysVMMathExpm1(double a);
EXTERNAL double sysVMMathHypot(double a, double b);
EXTERNAL double sysVMMathLog10(double a);
EXTERNAL double sysVMMathLog1p(double a);

/* Memory declarations */

EXTERNAL void* sysMalloc(int length);
EXTERNAL void* sysCalloc(int length);
EXTERNAL void sysFree(void *location);
EXTERNAL void sysCopy(void *dst, const void *src, Extent cnt);
EXTERNAL void sysZero(void *dst, Extent cnt);
EXTERNAL void sysZeroPages(void *dst, int cnt);
EXTERNAL void sysSyncCache(void *address, size_t size);
EXTERNAL void* sysMemoryReserve(char *start, size_t length,
                                jboolean read, jboolean write,
                                jboolean exec, jboolean commit);
EXTERNAL jboolean sysMemoryFree(char *start, size_t length);
EXTERNAL jboolean sysMemoryCommit(char *start, size_t length,
				  jboolean read, jboolean write,
				  jboolean exec);
EXTERNAL jboolean sysMemoryDecommit(char *start, size_t length);
EXTERNAL int sysGetPageSize();
EXTERNAL void findMappable();

/* Signal declarations */

EXTERNAL void* sysStartMainThreadSignals();
EXTERNAL void* sysStartChildThreadSignals();
EXTERNAL void sysEndThreadSignals(void *stackBuf);

/* Signal architecture specific declarations */

EXTERNAL void readContextInformation(void *context, Address *instructionPtr,
                                     Address *instructionFollowingPtr,
                                     Address *threadPtr, Address *jtocPtr);
EXTERNAL Address readContextFramePointer(void UNUSED *context, Address threadPtr);
EXTERNAL int readContextTrapCode(void UNUSED *context, Address threadPtr, int signo, Address instructionPtr, Word *trapInfo);
EXTERNAL void setupDeliverHardwareException(void *context, Address vmRegisters,
					    int trapCode, int trapInfo,
					    Address instructionPtr,
					    Address instructionFollowingPtr,
					    Address threadPtr, Address jtocPtr,
					    Address framePtr, int signo);
EXTERNAL void setupDumpStackAndDie(void *context);
EXTERNAL void dumpContext(void *context);

/* Thread declarations */

EXTERNAL void sysInitialize();
EXTERNAL void sysExit(int) NORETURN;
EXTERNAL void sysStashVMThread(Address vmThread);
EXTERNAL void* getVMThread();
EXTERNAL int sysNumProcessors();
EXTERNAL void sysStartMainThread(jboolean vmInSeparateThread, Address ip, Address fp, Address tr, Address jtoc, uint32_t *bootCompleted);
EXTERNAL Address sysThreadCreate(Address ip, Address fp, Address tr, Address jtoc);
EXTERNAL void sysThreadTerminate();
EXTERNAL int sysThreadBindSupported();
EXTERNAL void sysThreadBind(int UNUSED cpuId);
EXTERNAL Address sysThreadSelf();
EXTERNAL void sysThreadSetPriority(Address thread, int priority);
EXTERNAL void sysThreadYield();
EXTERNAL void sysNanoSleep(long long howLongNanos);
EXTERNAL Address sysMonitorCreate();
EXTERNAL void sysMonitorDestroy(Address _monitor);
EXTERNAL void sysMonitorEnter(Address _monitor);
EXTERNAL void sysMonitorExit(Address _monitor);
EXTERNAL void sysMonitorTimedWaitAbsolute(Address _monitor, long long whenWakeupNanos);
EXTERNAL void sysMonitorWait(Address _monitor);
EXTERNAL void sysMonitorNotifyAll(Address _monitor);

/* Thread architecture specific declarations */

EXTERNAL void bootThread (void *ip, void *tr, void *sp, void *jtoc);

/* Time declarations */

EXTERNAL long long sysCurrentTimeMillis();
EXTERNAL long long sysNanoTime();

/* Misc declarations */

EXTERNAL int sysArg(int argno, char *buf, int buflen);
EXTERNAL int sysGetenv(const char *varName, char *buf, int limit);
EXTERNAL unsigned int parse_memory_size(const char *sizeName, const char *sizeFlag, 
					const char *defaultFactor, unsigned roundTo,
					const char *token, const char *subtoken,
					int *fastExit);
EXTERNAL jlong sysParseMemorySize(const char *sizeName, const char *sizeFlag,
                                  const char *defaultFactor, int roundTo, 
                                  const char *token, const char *subtoken);

/* PerfCtr declarations */

EXTERNAL int sysPerfCtrInit(int metric);
EXTERNAL long long sysPerfCtrReadCycles();
EXTERNAL long long sysPerfCtrReadMetric();
EXTERNAL int sysPerfCtrRead(char *str);

/* Alignment check declarations */

EXTERNAL void sysEnableAlignmentChecking();
EXTERNAL void sysDisableAlignmentChecking();
EXTERNAL void sysReportAlignmentChecking();

/* Extra declarations automatically generated by the generateInterfaceDeclarations */
#define NEED_BOOT_RECORD_DECLARATIONS 1
#define NEED_EXIT_STATUS_CODES 1
#define NEED_MEMORY_MANAGER_DECLARATIONS 1
#define NEED_VIRTUAL_MACHINE_DECLARATIONS 1
#include <InterfaceDeclarations.h>

#endif // RVM_SYSCALL_DEFINITIONS
