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
#include <stdarg.h>
#include <string.h>

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

static void  sysSetJNILinkage();

static jobject JNICALL NewObject(JNIEnv *env, jclass clazz, jmethodID methodID, ...);

static jobject JNICALL CallObjectMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jboolean JNICALL CallBooleanMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jbyte JNICALL CallByteMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jchar JNICALL CallCharMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jshort JNICALL CallShortMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jint JNICALL CallIntMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jlong JNICALL CallLongMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jfloat JNICALL CallFloatMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static jdouble JNICALL CallDoubleMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);
static void JNICALL CallVoidMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...);

static jobject JNICALL CallNonvirtualObjectMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jboolean JNICALL CallNonvirtualBooleanMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jbyte JNICALL CallNonvirtualByteMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jchar JNICALL CallNonvirtualCharMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jshort JNICALL CallNonvirtualShortMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jint JNICALL CallNonvirtualIntMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jlong JNICALL CallNonvirtualLongMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jfloat JNICALL CallNonvirtualFloatMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static jdouble JNICALL CallNonvirtualDoubleMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);
static void JNICALL CallNonvirtualVoidMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...);

static jobject JNICALL CallStaticObjectMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jboolean JNICALL CallStaticBooleanMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jbyte JNICALL CallStaticByteMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jchar JNICALL CallStaticCharMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jshort JNICALL CallStaticShortMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jint JNICALL CallStaticIntMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jlong JNICALL CallStaticLongMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jfloat JNICALL CallStaticFloatMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static jdouble JNICALL CallStaticDoubleMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);
static void JNICALL CallStaticVoidMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...);

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
#ifndef RVM_WITH_OPROFILE
		    MAP_FIXED | MAP_PRIVATE | MAP_NORESERVE,
                    fileno(fin), 0);
#else
#ifdef MAP_ANONYMOUS
                    MAP_FIXED | MAP_PRIVATE | MAP_NORESERVE | MAP_ANONYMOUS,
#else
                    MAP_FIXED | MAP_PRIVATE | MAP_NORESERVE | MAP_ANON,
#endif
                    -1, 0);
#endif
  if (bootRegion == (void *) MAP_FAILED) {
    char *error_msg=strerror(errno);
    ERROR_PRINTF("%s: mmap failed (errno=%d): %s\n", Me, errno, error_msg);
    return 0;
  }
#ifdef RVM_WITH_OPROFILE
  size_t read_len = fread(bootRegion, actualImageSize, 1, fin); 
  if(read_len != 1) {
    char *error_msg=strerror(errno);
    ERROR_PRINTF("%s: image read failed (errno=%d): %s\n", Me, errno, error_msg);
    return 0;
  }
#endif
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
  long roundedDataRegionSize;
  void *bootCodeRegion;
  long roundedCodeRegionSize;
  void *bootRMapRegion;
  long roundedRMapRegionSize;
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
                 Me, (void*)bootRecord->bootImageDataStart, bootDataRegion);
    return 1;
  }

  if (bootRecord->bootImageCodeStart != (Address) bootCodeRegion) {
    ERROR_PRINTF("%s: image load error: built for %p but loaded at %p\n",
                 Me, (void*)bootRecord->bootImageCodeStart, bootCodeRegion);
    return 1;
  }

  if (bootRecord->bootImageRMapStart != (Address) bootRMapRegion) {
    ERROR_PRINTF("%s: image load error: built for %p but loaded at %p\n",
                 Me, (void*)bootRecord->bootImageRMapStart, bootRMapRegion);
    return 1;
  }

  if ((bootRecord->spRegister % __SIZEOF_POINTER__) != 0) {
    ERROR_PRINTF("%s: image format error: sp (%p) is not word aligned\n",
                 Me, (void*)bootRecord->spRegister);
    return 1;
  }

  if ((bootRecord->ipRegister % __SIZEOF_POINTER__) != 0) {
    ERROR_PRINTF("%s: image format error: ip (%p) is not word aligned\n",
                 Me, (void*)bootRecord->ipRegister);
    return 1;
  }

  if (((uint32_t *) bootRecord->spRegister)[-1] != 0xdeadbabe) {
    ERROR_PRINTF("%s: image format error: missing stack sanity check marker (%p)\n",
                 Me, (void*)(((int *) bootRecord->spRegister)[-1]));
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

  /* write syscall linkage information into boot record */
  sysSetLinkage();

  /* add C defined JNI functions into the JNI function table */
  sysSetJNILinkage();

  if (verbose) {
    TRACE_PRINTF("%s: boot record contents:\n", Me);
    TRACE_PRINTF("   bootImageDataStart:   %p\n", (void*)bootRecord->bootImageDataStart);
    TRACE_PRINTF("   bootImageDataEnd:     %p\n", (void*)bootRecord->bootImageDataEnd);
    TRACE_PRINTF("   bootImageCodeStart:   %p\n", (void*)bootRecord->bootImageCodeStart);
    TRACE_PRINTF("   bootImageCodeEnd:     %p\n", (void*)bootRecord->bootImageCodeEnd);
    TRACE_PRINTF("   bootImageRMapStart:   %p\n", (void*)bootRecord->bootImageRMapStart);
    TRACE_PRINTF("   bootImageRMapEnd:     %p\n", (void*)bootRecord->bootImageRMapEnd);
    TRACE_PRINTF("   initialHeapSize:      %d\n", bootRecord->initialHeapSize);
    TRACE_PRINTF("   maximumHeapSize:      %d\n", bootRecord->maximumHeapSize);
    TRACE_PRINTF("   spRegister:           %p\n", (void*)bootRecord->spRegister);
    TRACE_PRINTF("   ipRegister:           %p\n", (void*)bootRecord->ipRegister);
    TRACE_PRINTF("   tocRegister:          %p\n", (void*)bootRecord->tocRegister);
    TRACE_PRINTF("   sysConsoleWriteCharIP:%p\n", (void*)bootRecord->sysConsoleWriteCharIP);
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

/**
 * Insert missing ... JNI methods into JNI function table
 */
static void  sysSetJNILinkage()
{
  struct JNINativeInterface_ *jniFunctions = (struct JNINativeInterface_ *)(bootRecord->JNIFunctions);

  jniFunctions->NewObject = NewObject;
  jniFunctions->CallObjectMethod  = CallObjectMethod;
  jniFunctions->CallBooleanMethod = CallBooleanMethod;
  jniFunctions->CallByteMethod    = CallByteMethod;
  jniFunctions->CallCharMethod    = CallCharMethod;
  jniFunctions->CallShortMethod   = CallShortMethod;
  jniFunctions->CallIntMethod     = CallIntMethod;
  jniFunctions->CallLongMethod    = CallLongMethod;
  jniFunctions->CallFloatMethod   = CallFloatMethod;
  jniFunctions->CallDoubleMethod  = CallDoubleMethod;
  jniFunctions->CallVoidMethod  = CallVoidMethod;
  jniFunctions->CallNonvirtualObjectMethod  = CallNonvirtualObjectMethod;
  jniFunctions->CallNonvirtualBooleanMethod = CallNonvirtualBooleanMethod;
  jniFunctions->CallNonvirtualByteMethod    = CallNonvirtualByteMethod;
  jniFunctions->CallNonvirtualCharMethod    = CallNonvirtualCharMethod;
  jniFunctions->CallNonvirtualShortMethod   = CallNonvirtualShortMethod;
  jniFunctions->CallNonvirtualIntMethod     = CallNonvirtualIntMethod;
  jniFunctions->CallNonvirtualLongMethod    = CallNonvirtualLongMethod;
  jniFunctions->CallNonvirtualFloatMethod   = CallNonvirtualFloatMethod;
  jniFunctions->CallNonvirtualDoubleMethod  = CallNonvirtualDoubleMethod;
  jniFunctions->CallNonvirtualVoidMethod    = CallNonvirtualVoidMethod;
  jniFunctions->CallStaticObjectMethod  = CallStaticObjectMethod;
  jniFunctions->CallStaticBooleanMethod = CallStaticBooleanMethod;
  jniFunctions->CallStaticByteMethod    = CallStaticByteMethod;
  jniFunctions->CallStaticCharMethod    = CallStaticCharMethod;
  jniFunctions->CallStaticShortMethod   = CallStaticShortMethod;
  jniFunctions->CallStaticIntMethod     = CallStaticIntMethod;
  jniFunctions->CallStaticLongMethod    = CallStaticLongMethod;
  jniFunctions->CallStaticFloatMethod   = CallStaticFloatMethod;
  jniFunctions->CallStaticDoubleMethod  = CallStaticDoubleMethod;
  jniFunctions->CallStaticVoidMethod  = CallStaticVoidMethod;
}

/* Methods to box ... into a va_list */

static jobject JNICALL NewObject(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jobject result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: NewObject %p %p\n", Me, clazz, methodID);
  result = (*env)->NewObjectV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jobject JNICALL CallObjectMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jobject result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallObjectMethod %p %p\n", Me, obj, methodID);
  result = (*env)->CallObjectMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jboolean JNICALL CallBooleanMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jboolean result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallBooleanMethod %p %p\n", Me, obj, methodID);
  result = (*env)->CallBooleanMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jbyte JNICALL CallByteMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jbyte result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallByteMethod %p %p\n", Me, obj, methodID);
  result = (*env)->CallByteMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jchar JNICALL CallCharMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jchar result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallCharMethod %p %p\n", Me, obj, methodID);
  result = (*env)->CallCharMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jshort JNICALL CallShortMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jshort result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallShortMethod %p %p\n", Me, obj, methodID);
  result = (*env)->CallShortMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jint JNICALL CallIntMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jint result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallIntMethod %p %p\n", Me, obj, methodID);
  result = (*env)->CallIntMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jlong JNICALL CallLongMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jlong result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallLongMethod %p %p\n", Me, obj, methodID);
  result = (*env)->CallLongMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jfloat JNICALL CallFloatMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jfloat result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallFloatMethod %p %p\n", Me, obj, methodID);
  result = (*env)->CallFloatMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static jdouble JNICALL CallDoubleMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jdouble result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallDoubleMethod %p %p\n", Me, obj, methodID);
  result = (*env)->CallDoubleMethodV(env, obj, methodID, ap);
  va_end(ap);
  return result;
}

static void JNICALL CallVoidMethod(JNIEnv *env, jobject obj, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallVoidMethod %p %p\n", Me, obj, methodID);
  (*env)->CallVoidMethodV(env, obj, methodID, ap);
  va_end(ap);
}

static jobject JNICALL CallNonvirtualObjectMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jobject result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualObjectMethod %p %p %p\n", Me, obj, clazz, methodID);
  result = (*env)->CallNonvirtualObjectMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jboolean JNICALL CallNonvirtualBooleanMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jboolean result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualBooleanMethod %p %p %p\n", Me, obj, clazz, methodID);
  result = (*env)->CallNonvirtualBooleanMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jbyte JNICALL CallNonvirtualByteMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jbyte result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualByteMethod %p %p %p\n", Me, obj, clazz, methodID);
  result = (*env)->CallNonvirtualByteMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jchar JNICALL CallNonvirtualCharMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jchar result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualCharMethod %p %p %p\n", Me, obj, clazz, methodID);
  result = (*env)->CallNonvirtualCharMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jshort JNICALL CallNonvirtualShortMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jshort result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualShortMethod %p %p %p\n", Me, obj, clazz, methodID);
  result = (*env)->CallNonvirtualShortMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jint JNICALL CallNonvirtualIntMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jint result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualIntMethod %p %p %p\n", Me, obj, clazz, methodID);
  result = (*env)->CallNonvirtualIntMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jlong JNICALL CallNonvirtualLongMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jlong result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualLongMethod %p %p %p\n", Me, obj, clazz, methodID);
  result = (*env)->CallNonvirtualLongMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jfloat JNICALL CallNonvirtualFloatMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jfloat result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualFloatMethod %p %p %p\n", Me, obj, clazz, methodID);
  result = (*env)->CallNonvirtualFloatMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jdouble JNICALL CallNonvirtualDoubleMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jdouble result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualDoubleMethod %p %p %p\n", Me, obj, clazz, methodID);
  result = (*env)->CallNonvirtualDoubleMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static void JNICALL CallNonvirtualVoidMethod(JNIEnv *env, jobject obj, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallNonvirtualVoidMethod %p %p %p\n", Me, obj, clazz, methodID);
  (*env)->CallNonvirtualVoidMethodV(env, obj, clazz, methodID, ap);
  va_end(ap);
}

static jobject JNICALL CallStaticObjectMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jobject result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticObjectMethod %p %p\n", Me, clazz, methodID);
  result = (*env)->CallStaticObjectMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jboolean JNICALL CallStaticBooleanMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jboolean result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticBooleanMethod %p %p\n", Me, clazz, methodID);
  result = (*env)->CallStaticBooleanMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jbyte JNICALL CallStaticByteMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jbyte result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticByteMethod %p %p\n", Me, clazz, methodID);
  result = (*env)->CallStaticByteMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jchar JNICALL CallStaticCharMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jchar result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticCharMethod %p %p\n", Me, clazz, methodID);
  result = (*env)->CallStaticCharMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jshort JNICALL CallStaticShortMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jshort result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticShortMethod %p %p\n", Me, clazz, methodID);
  result = (*env)->CallStaticShortMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jint JNICALL CallStaticIntMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jint result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticIntMethod %p %p\n", Me, clazz, methodID);
  result = (*env)->CallStaticIntMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jlong JNICALL CallStaticLongMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jlong result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticLongMethod %p %p\n", Me, clazz, methodID);
  result = (*env)->CallStaticLongMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jfloat JNICALL CallStaticFloatMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jfloat result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticFloatMethod %p %p\n", Me, clazz, methodID);
  result = (*env)->CallStaticFloatMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static jdouble JNICALL CallStaticDoubleMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  jdouble result;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticDoubleMethod %p %p\n", Me, clazz, methodID);
  result = (*env)->CallStaticDoubleMethodV(env, clazz, methodID, ap);
  va_end(ap);
  return result;
}

static void JNICALL CallStaticVoidMethod(JNIEnv *env, jclass clazz, jmethodID methodID, ...)
{
  SYS_START();
  va_list ap;
  va_start(ap, methodID);
  TRACE_PRINTF("%s: CallStaticVoidMethod %p %p\n", Me, clazz, methodID);
  (*env)->CallStaticVoidMethodV(env, clazz, methodID, ap);
  va_end(ap);
}
