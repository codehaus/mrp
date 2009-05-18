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

/**
 * Implementation of JNI Invocation API for Jikes RVM.
 */

#include "sys.h"

#ifndef RVM_FOR_HARMONY
#include <sys/mman.h>
#include <errno.h>
#endif

/** String used for name of RVM */
char *Me;

/** C access to shared C/Java boot record data structure */
struct BootRecord *bootRecord;

/** Number of Java args */
int JavaArgc;

/** Java args */
char **JavaArgs;

#ifndef RVM_FOR_HARMONY
/** Sink for messages relating to serious errors detected by C runtime. */
FILE *SysErrorFile;
#endif

#ifndef RVM_FOR_HARMONY
/** Sink for trace messages produced by VM.sysWrite(). */
FILE *SysTraceFile;
#endif

/** Verbose command line option */
int verbose=0;

/** File name for part of boot image containing code */
char *bootCodeFilename;

/** File name for part of boot image containing data */
char *bootDataFilename;

/** File name for part of boot image containing the root map */
char *bootRMapFilename;

/** Specified or default initial heap size */
uint64_t initialHeapSize;

/** Specified or default maximum heap size */
uint64_t maximumHeapSize;

/** Verbose boot up set */
int verboseBoot=0;

/* Prototypes */
static jint JNICALL DestroyJavaVM(JavaVM UNUSED * vm);
static jint JNICALL AttachCurrentThread(JavaVM UNUSED * vm, /* JNIEnv */ void ** penv, /* JavaVMAttachArgs */ void *args);
static jint JNICALL DetachCurrentThread(JavaVM UNUSED *vm);
static jint JNICALL GetEnv(JavaVM UNUSED *vm, void **penv, jint version);
static jint JNICALL AttachCurrentThreadAsDaemon(JavaVM UNUSED * vm, /* JNIEnv */ void UNUSED ** penv, /* JavaVMAttachArgs */ void UNUSED *args);

/** JNI invoke interface implementation */
static const struct JNIInvokeInterface_ externalJNIFunctions = {
  NULL, // reserved0
  NULL, // reserved1
  NULL, // reserved2
  DestroyJavaVM,
  AttachCurrentThread,
  DetachCurrentThread,
  GetEnv,         // JNI 1.2
  AttachCurrentThreadAsDaemon   // JNI 1.4
};

/** JavaVM interface implementation */
const struct JavaVM_ sysJavaVM = {
  &externalJNIFunctions, // functions
  NULL, // reserved0
  NULL, // reserved1
  NULL, // reserved2
  NULL, // threadIDTable
  NULL, // jniEnvTable
};

/** JNI standard JVM initialization arguments */
JavaVMInitArgs *sysInitArgs;

/**
 * Fish out an address stored in an instance field of an object.
 */
static void *getFieldAsAddress(void *objPtr, int fieldOffset)
{
    char *fieldAddress = ((char*) objPtr) + fieldOffset;
    return *((void**) fieldAddress);
}

/**
 * Get the JNI environment object from the VM thread.
 */
static JNIEnv * getJniEnvFromVmThread(void *vmThreadPtr)
{
  void *jniEnvironment;
  void *jniEnv;
  if (vmThreadPtr == 0)
    return 0; // oops

  // Follow chain of pointers:
  // RVMThread -> JNIEnvironment -> thread's native JNIEnv
  jniEnvironment = getFieldAsAddress(vmThreadPtr, RVMThread_jniEnv_offset);
  // Convert JNIEnvironment to JNIEnv* expected by native code
  // by creating the appropriate interior pointer.
  jniEnv = ((char*)jniEnvironment + JNIEnvironment_JNIExternalFunctions_offset);
  return (JNIEnv*) jniEnv;
}


//////////////////////////////////////////////////////////////
// JNI Invocation API functions
//////////////////////////////////////////////////////////////

/**
 * Destroying the Java VM only makes sense if programs can create a VM
 * on-the-fly.   Further, as of Sun's Java 1.2, it still didn't support
 * unloading virtual machine instances.  It is supposed to block until all
 * other user threads are gone, and then return an error code.
 *
 * TODO: Implement.
 */
static jint JNICALL DestroyJavaVM(JavaVM UNUSED * vm)
{
  SYS_START();
  ERROR_PRINTF("JikesRVM: Unimplemented JNI call DestroyJavaVM\n");
  return JNI_ERR;
}

/**
 * "Trying to attach a thread that is already attached is a no-op".  We
 * implement that common case.  (In other words, it works like GetEnv()).
 * However, we do not implement the more difficult case of actually attempting
 * to attach a native thread that is not currently attached to the VM.
 *
 * TODO: Implement for actually attaching unattached threads.
 */
static jint JNICALL AttachCurrentThread(JavaVM UNUSED * vm, /* JNIEnv */ void ** penv, /* JavaVMAttachArgs */ void *args)
{
  SYS_START();
  JavaVMAttachArgs *aargs = (JavaVMAttachArgs *) args;
  jint version;
  jint retval;
  if (args == NULL) {
    version = JNI_VERSION_1_1;
  } else {
    version = aargs->version ;
    /* We'd like to handle aargs->name and aargs->group */
  }

  // Handled for us by GetEnv().  We do it here anyway so that we avoid
  // printing an error message further along in this function.
  if (version > JNI_VERSION_1_4)
    return JNI_EVERSION;

  /* If we're already attached, we're gold. */
  retval = GetEnv(vm, penv, version);
  if (retval == JNI_OK)
    return retval;
  else if (retval == JNI_EDETACHED) {
    ERROR_PRINTF("JikesRVM: JNI call AttachCurrentThread Unimplemented for threads not already attached to the VM\n");
  } else {
    ERROR_PRINTF("JikesRVM: JNI call AttachCurrentThread failed; returning UNEXPECTED error code %d\n", (int) retval);
  }

  // Upon failure:
  *penv = NULL;               // Make sure we don't yield a bogus one to use.
  return retval;
}

/* TODO: Implement */
static jint JNICALL DetachCurrentThread(JavaVM UNUSED *vm)
{
  SYS_START();
  ERROR_PRINTF("UNIMPLEMENTED JNI call DetachCurrentThread\n");
  return JNI_ERR;
}

static jint JNICALL GetEnv(JavaVM UNUSED *vm, void **penv, jint version)
{
  void *vmThread;
  JNIEnv *env;
  if (version > JNI_VERSION_1_4)
    return JNI_EVERSION;

  // Return NULL if we are not on a VM thread
  vmThread = getVMThread();
  if (vmThread == NULL) {
    *penv = NULL;
    return JNI_EDETACHED;
  }

  // Get the JNIEnv from the RVMThread object
  env = getJniEnvFromVmThread(vmThread);

  *((JNIEnv**)penv) = env;

  return JNI_OK;
}

/** JNI 1.4 */
/* TODO: Implement */
static jint JNICALL AttachCurrentThreadAsDaemon(JavaVM UNUSED * vm, /* JNIEnv */ void UNUSED ** penv, /* JavaVMAttachArgs */ void UNUSED *args)
{
  SYS_START();
  ERROR_PRINTF("Unimplemented JNI call AttachCurrentThreadAsDaemon\n");
  return JNI_ERR;
}

/**
 * Round give size to the nearest default page
 *
 * @param size [in] size to round up
 * @return rounded size
 */
static long pageRoundUp(long size)
{
  int pageSize = sysGetPageSize();
  return (size + pageSize - 1) / pageSize * pageSize;
}

/**
 * Map the given file to memory
 *
 * @param fileName         [in] name of file
 * @param targetAddress    [in] address to load file to
 * @param executable       [in] are we mapping code into memory
 * @param writable         [in] do we need to write to this memory?
 * @param roundedImageSize [out] size of mapped memory rounded up to a whole
 * @return address of mapped region
 */
static void* mapImageFile(const char *fileName, const void *targetAddress,
                          jboolean executable, jboolean writable, long *roundedImageSize) {
  long actualImageSize;
  void *bootRegion = 0;
#ifdef RVM_FOR_HARMONY
  IDATA fin;
#endif
  SYS_START();
  TRACE_PRINTF("%s: mapImageFile \"%s\" to %p\n", Me, fileName, targetAddress);
  /* TODO: respect access protection when mapping. Problems, need to
   * write over memory for Harmony when reading from file.
   */
  writable = JNI_TRUE;
  executable = JNI_TRUE;
#ifdef RVM_FOR_HARMONY
  fin = hyfile_open(fileName, HyOpenRead, 0);
  if (fin < 0) {
    ERROR_PRINTF("%s: can't find bootimage file\"%s\"\n", Me, fileName);
    return 0;
  }
  actualImageSize = hyfile_length(fileName);
  *roundedImageSize = pageRoundUp(actualImageSize);
  bootRegion = sysMemoryReserve((void*)targetAddress, *roundedImageSize,
                                JNI_TRUE, writable, executable, JNI_TRUE);
  if (bootRegion == targetAddress) {
    hyfile_read(fin, bootRegion, actualImageSize);
  } else {
    ERROR_PRINTF("%s: Attempted to mapImageFile to the address %p; "
                 " got %p instead.  This should never happen.",
		 Me, targetAddress, bootRegion);
  }
  hyfile_close(fin);
#else
  FILE *fin = fopen (fileName, "r");
  if (!fin) {
    ERROR_PRINTF("%s: can't find bootimage file\"%s\"\n", Me, fileName);
    return 0;
  }
  /* measure image size */
  fseek (fin, 0L, SEEK_END);
  actualImageSize = ftell(fin);
  *roundedImageSize = pageRoundUp(actualImageSize);
  fseek (fin, 0L, SEEK_SET);
  int prot = PROT_READ;
  if (writable)
    prot |= PROT_WRITE;
  if (executable)
    prot |= PROT_EXEC;
  bootRegion = mmap((void*)targetAddress, *roundedImageSize,
		    prot,
		    MAP_FIXED | MAP_PRIVATE | MAP_NORESERVE,
		    fileno(fin), 0);
  if (bootRegion == (void *) MAP_FAILED) {
    ERROR_PRINTF("%s: mmap failed (errno=%d): %s\n", Me, errno, strerror(errno));
    return 0;
  }
  /* Quoting from the Linux mmap(2) manual page:
     "closing the file descriptor does not unmap the region."
  */
  if (fclose (fin) != 0) {
    ERROR_PRINTF("%s: close failed (errno=%d)\n", Me, errno);
    return 0;
  }
  if (bootRegion != targetAddress) {
    ERROR_PRINTF("%s: Attempted to mapImageFile to the address %p; "
		 " got %p instead.  This should never happen.",
		 Me, targetAddress, bootRegion);
    (void) munmap(bootRegion, *roundedImageSize);
    return 0;
  }
#endif
  return bootRegion;
}

/**
 * Start the VM
 *
 * @param vmInSeparateThread [in] create a thread for the VM to
 * execute in rather than this thread
 * @return 1 upon any errors.  Never returns except to report an
 * error.
 */
static int createVM(int vmInSeparateThread)
{
  void *bootDataRegion;
  unsigned roundedDataRegionSize;
  void *bootCodeRegion;
  unsigned roundedCodeRegionSize;
  void *bootRMapRegion;
  unsigned roundedRMapRegionSize;
  SYS_START();

  bootDataRegion = mapImageFile(bootDataFilename,
				(void*)bootImageDataAddress,
				JNI_FALSE,
                                JNI_TRUE,
				&roundedDataRegionSize);
  if (bootDataRegion != (void*)bootImageDataAddress)
    return 1;


  bootCodeRegion = mapImageFile(bootCodeFilename,
	                        (void*)bootImageCodeAddress,
				JNI_TRUE,
                                JNI_FALSE,
				&roundedCodeRegionSize);
  if (bootCodeRegion != (void*)bootImageCodeAddress)
    return 1;

  bootRMapRegion = mapImageFile(bootRMapFilename,
	                        (void*)bootImageRMapAddress,
				JNI_FALSE,
                                JNI_FALSE,
				&roundedRMapRegionSize);
  if (bootRMapRegion != (void*)bootImageRMapAddress)
    return 1;


  /* validate contents of boot record */
  bootRecord = (struct BootRecord *) bootDataRegion;

  if (bootRecord->bootImageDataStart != (Address) bootDataRegion) {
    ERROR_PRINTF("%s: image load error: built for %p but loaded at %p\n",
	    Me, bootRecord->bootImageDataStart, bootDataRegion);
    return 1;
  }

  if (bootRecord->bootImageCodeStart != (Address) bootCodeRegion) {
    ERROR_PRINTF("%s: image load error: built for %p but loaded at %p\n",
	    Me, bootRecord->bootImageCodeStart, bootCodeRegion);
    return 1;
  }

  if (bootRecord->bootImageRMapStart != (Address) bootRMapRegion) {
    ERROR_PRINTF("%s: image load error: built for %p but loaded at %p\n",
	    Me, bootRecord->bootImageRMapStart, bootRMapRegion);
    return 1;
  }

  if ((bootRecord->spRegister % __SIZEOF_POINTER__) != 0) {
    ERROR_PRINTF("%s: image format error: sp (%p) is not word aligned\n",
	    Me, bootRecord->spRegister);
    return 1;
  }

  if ((bootRecord->ipRegister % __SIZEOF_POINTER__) != 0) {
    ERROR_PRINTF("%s: image format error: ip (%p) is not word aligned\n",
	    Me, bootRecord->ipRegister);
    return 1;
  }

  if (((uint32_t *) bootRecord->spRegister)[-1] != 0xdeadbabe) {
    ERROR_PRINTF("%s: image format error: missing stack sanity check marker (%p)\n",
		 Me, ((int *) bootRecord->spRegister)[-1]);
    return 1;
  }

  /* write freespace information into boot record */
  bootRecord->initialHeapSize  = initialHeapSize;
  bootRecord->maximumHeapSize  = maximumHeapSize;
  bootRecord->bootImageDataStart   = (Address) bootDataRegion;
  bootRecord->bootImageDataEnd     = (Address) bootDataRegion + roundedDataRegionSize;
  bootRecord->bootImageCodeStart   = (Address) bootCodeRegion;
  bootRecord->bootImageCodeEnd     = (Address) bootCodeRegion + roundedCodeRegionSize;
  bootRecord->bootImageRMapStart   = (Address) bootRMapRegion;
  bootRecord->bootImageRMapEnd     = (Address) bootRMapRegion + roundedRMapRegionSize;
  bootRecord->verboseBoot      = verboseBoot;

  /* write sys.C linkage information into boot record */
  sysSetLinkage();

  if (verbose) {
    TRACE_PRINTF("%s: boot record contents:\n", Me);
    TRACE_PRINTF("   bootImageDataStart:   %p\n", bootRecord->bootImageDataStart);
    TRACE_PRINTF("   bootImageDataEnd:     %p\n", bootRecord->bootImageDataEnd);
    TRACE_PRINTF("   bootImageCodeStart:   %p\n", bootRecord->bootImageCodeStart);
    TRACE_PRINTF("   bootImageCodeEnd:     %p\n", bootRecord->bootImageCodeEnd);
    TRACE_PRINTF("   bootImageRMapStart:   %p\n", bootRecord->bootImageRMapStart);
    TRACE_PRINTF("   bootImageRMapEnd:     %p\n", bootRecord->bootImageRMapEnd);
    TRACE_PRINTF("   initialHeapSize:      %p\n", bootRecord->initialHeapSize);
    TRACE_PRINTF("   maximumHeapSize:      %p\n", bootRecord->maximumHeapSize);
    TRACE_PRINTF("   spRegister:           %p\n", bootRecord->spRegister);
    TRACE_PRINTF("   ipRegister:           %p\n", bootRecord->ipRegister);
    TRACE_PRINTF("   tocRegister:          %p\n", bootRecord->tocRegister);
    TRACE_PRINTF("   sysConsoleWriteCharIP:%p\n", bootRecord->sysConsoleWriteCharIP);
    TRACE_PRINTF("   ...etc...                   \n");
  }
  
  /* force any machine code within image that's still in dcache to be
   * written out to main memory so that it will be seen by icache when
   * instructions are fetched back
   */
  sysSyncCache(bootCodeRegion, roundedCodeRegionSize);

#ifdef RVM_FOR_HARMONY
  hythread_attach(NULL);
#endif

  sysStartMainThread(vmInSeparateThread, bootRecord->ipRegister, bootRecord->spRegister,
                     *(Address *) (bootRecord->tocRegister + bootRecord->bootThreadOffset),
                     bootRecord->tocRegister, &bootRecord->bootCompleted);
  return 0;
}

JNIEXPORT jint JNICALL JNI_CreateJavaVM(JavaVM **mainJavaVM, JNIEnv **mainJNIEnv, void *initArgs)
{
  SYS_START();
  TRACE_PRINTF("%s: JNI call CreateJavaVM\n", Me);
  *mainJavaVM = (JavaVM*)&sysJavaVM;
  *mainJNIEnv = NULL;
  sysInitArgs = initArgs;
  return createVM(0);
}

JNIEXPORT jint JNICALL JNI_GetDefaultJavaVMInitArgs(void *initArgs)
{
  SYS_START();
  ERROR_PRINTF("UNIMPLEMENTED JNI call JNI_GetDefaultJavaVMInitArgs\n");
  return JNI_ERR;
}

JNIEXPORT jint JNICALL JNI_GetCreatedJavaVMs(JavaVM **vmBuf, jsize buflen, jsize *nVMs)
{
  SYS_START();
  ERROR_PRINTF("UNIMPLEMENTED JNI call JNI_GetCreatedJavaVMs\n");
  return JNI_ERR;
}
