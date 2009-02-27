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

/**
 * Implementation of Harmony VMI Invocation API for Jikes RVM.
 */

#include "sys.h"

struct VMInterfaceFunctions_ vmi_impl = {
   &CheckVersion,
   &GetJavaVM,
   &GetPortLibrary,
   &GetVMLSFunctions,
#ifndef HY_ZIP_API
   &GetZipCachePool,
#else /* HY_ZIP_API */
   &GetZipFunctions,
#endif /* HY_ZIP_API */
   &GetInitArgs,
   &GetSystemProperty,
   &SetSystemProperty,
   &CountSystemProperties,
   &IterateSystemProperties
};

VMInterface vmi = &vmi_impl;
HyPortLibrary hyPortLibrary;
HyPortLibraryVersion hyPortLibraryVersion;
#ifndef HY_ZIP_API
HyZipCachePool *hyZipCachePool;
#endif

extern UDATA JNICALL HyVMLSAllocKeys (JNIEnv * env, UDATA * pInitCount, ...);
extern void JNICALL HyVMLSFreeKeys (JNIEnv * env, UDATA * pInitCount, ...);
extern void * JNICALL HyVMLSGet (JNIEnv * env, void *key);
extern void * JNICALL HyVMLSSet (JNIEnv * env, void **pKey, void *value);

HyVMLSFunctionTable vmls_impl = {
    &HyVMLSAllocKeys,
    &HyVMLSFreeKeys,
    &HyVMLSGet,
    &HyVMLSSet
};

HyZipCachePool* zipCachePool = NULL;
extern HyPortLibrary hyPortLibrary;
#ifndef HY_ZIP_API
extern HyZipCachePool *hyZipCachePool;
#endif

extern void initializeVMLocalStorage(JavaVM * vm);

vmiError JNICALL CheckVersion (VMInterface * vmi, vmiVersion * version)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call CheckVersion (unimplemented)\n", Me);
  return VMI_ERROR_UNIMPLEMENTED;
}

JavaVM * JNICALL GetJavaVM (VMInterface * vmi)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call GetJavaVM\n", Me);
  return &sysJavaVM;
}

HyPortLibrary * JNICALL GetPortLibrary (VMInterface * vmi)
{
  // NB can't trace this function as it is used to implement tracing!
  return &hyPortLibrary;
}

HyVMLSFunctionTable * JNICALL GetVMLSFunctions (VMInterface * vmi)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call GetVMLSFunctions\n", Me);
  return &vmls_impl;
}

#ifndef HY_ZIP_API
extern HyZipCachePool *hyZipCachePool;
HyZipCachePool * JNICALL GetZipCachePool (VMInterface * vmi)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call GetZipCachePool\n", Me);
  return hyZipCachePool;
}
#else /* HY_ZIP_API */
struct VMIZipFunctionTable * JNICALL GetZipFunctions (VMInterface * vmi)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call GetZipFunctions\n", Me);
  CONSOLE_PRINTF(SysErrorFile, "UNIMPLEMENTED VMI call GetZipFunctions\n");
  return NULL;
}
#endif /* HY_ZIP_API */

JavaVMInitArgs * JNICALL GetInitArgs (VMInterface * vmi)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call GetInitArgs\n", Me);
  return JavaArgs;
}

vmiError JNICALL GetSystemProperty (VMInterface * vmi, char *key, char **valuePtr)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call GetSystemProperty (unimplemented)\n", Me);
  return VMI_ERROR_UNIMPLEMENTED;
}

vmiError JNICALL SetSystemProperty (VMInterface * vmi, char *key, char *value)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call SetSystemProperty (unimplemented)\n", Me);
  return VMI_ERROR_UNIMPLEMENTED;
}

vmiError JNICALL CountSystemProperties (VMInterface * vmi, int *countPtr)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call CountSystemProperties (unimplemented)\n", Me);
  return VMI_ERROR_UNIMPLEMENTED;
}

vmiError JNICALL IterateSystemProperties (VMInterface * vmi, vmiSystemPropertyIterator iterator, void *userData)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI call IterateSystemProperties (unimplemented)\n", Me);
  return VMI_ERROR_UNIMPLEMENTED;
}

/**
 * Extract the VM Interface from a JNI JavaVM
 *
 * @param[in] vm  The JavaVM to query
 *
 * @return a VMInterface pointer
 */
VMInterface* JNICALL VMI_GetVMIFromJavaVM(JavaVM* vm)
{
  // NB can't trace this function as it is used to implement tracing!
  return &vmi;
}

/**
 * Extract the VM Interface from a JNIEnv
 *
 * @param[in] env  The JNIEnv to query
 *
 * @return a VMInterface pointer
 */
VMInterface* JNICALL 
VMI_GetVMIFromJNIEnv(JNIEnv* env)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: GetVMIFromJNIEnv\n", Me);
  return &vmi;
}

EXTERNAL void VMI_Initialize()
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: VMI_Initialize\n", Me);
  HYPORT_SET_VERSION (&hyPortLibraryVersion, HYPORT_CAPABILITY_MASK);
  if (0 != hyport_init_library (&hyPortLibrary, &hyPortLibraryVersion, sizeof (HyPortLibrary))) {
    CONSOLE_PRINTF(SysErrorFile, "Harmony port library init failed\n");
    abort();
  }
#ifndef HY_ZIP_API
  hyZipCachePool = zipCachePool_new(&hyPortLibrary);
  if (hyZipCachePool == NULL)
  {
    CONSOLE_PRINTF(SysErrorFile, "Error accessing zip functions");
    abort();
  }
#endif
  initializeVMLocalStorage(&sysJavaVM);
}

