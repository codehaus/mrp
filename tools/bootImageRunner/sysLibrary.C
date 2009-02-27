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

#ifndef RVM_FOR_HARMONY
#include <dlfcn.h>
#include <errno.h>
#endif

/**
 * Load dynamic library.
 * @return a handler for this library, null if none loaded
 */
EXTERNAL void* sysDlopen(char *libname)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysDlopen %s\n", Me, libname);
#ifdef RVM_FOR_HARMONY
  UDATA descriptor;
  if (hysl_open_shared_library(libname, &descriptor, TRUE) != 0) {
    hyfile_printf(PORTLIB, SysErrorFile, "%s: error loading library %s\n", Me, libname);
    return NULL;
  } else {
    return (void*)descriptor;
  }
#else
  void * libHandler;
  do {
    libHandler = dlopen(libname, RTLD_LAZY|RTLD_GLOBAL);
  }
  while( (libHandler == 0 /*null*/) && (errno == EINTR) );
  if (libHandler == 0) {
    fprintf(SysErrorFile,
            "%s: error loading library %s: %s\n", Me,
            libname, dlerror());
  }
  return libHandler;
#endif // RVM_FOR_HARMONY
}

/**
 * Look up symbol in dynamic library.
 */
EXTERNAL void* sysDlsym(Address libHandler, char *symbolName)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysDlsym %s\n", Me, symbolName);
#ifdef RVM_FOR_HARMONY
  UDATA func;
  char *argSignature=NULL;
  if(hysl_lookup_name((UDATA)libHandler, symbolName, &func, argSignature) != 0) {
    return NULL;
  } else {
    return (void*)func;
  }
#else
  return dlsym((void *) libHandler, symbolName);
#endif // RVM_FOR_HARMONY
}
