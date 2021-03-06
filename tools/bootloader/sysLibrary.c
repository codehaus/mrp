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

#ifndef RVM_FOR_HARMONY
#include <dlfcn.h>
#include <errno.h>
#endif

/**
 * Load dynamic library.
 *
 * @return a handler for this library, null if none loaded
 */
EXTERNAL void* sysDlopen(char *libname)
{
  SYS_START();
#ifdef RVM_FOR_HARMONY
  UDATA descriptor;
  TRACE_PRINTF("%s: sysDlopen %s\n", Me, libname);
  if (hysl_open_shared_library(libname, &descriptor, FALSE) != 0) {
    TRACE_PRINTF("%s: error loading library %s\n", Me, libname);
    return NULL;
  } else {
    return (void*)descriptor;
  }
#else
  void * libHandler;
  TRACE_PRINTF("%s: sysDlopen %s\n", Me, libname);
  do {
    libHandler = dlopen(libname, RTLD_LAZY|RTLD_GLOBAL);
  }
  while( (libHandler == 0 /*null*/) && (errno == EINTR) );
  if (libHandler == 0) {
    TRACE_PRINTF("%s: error loading library %s: %s\n", Me,
                 libname, dlerror());
  }
  return libHandler;
#endif // RVM_FOR_HARMONY
}

/**
 * Look up symbol in dynamic library.
 */
EXTERNAL void* sysDlsym(Address libHandler, char *symbolName, char *argSignature)
{
  SYS_START();
#ifdef RVM_FOR_HARMONY
  UDATA func;
  TRACE_PRINTF("%s: sysDlsym %s %s\n", Me, symbolName, argSignature);
  if(hysl_lookup_name((UDATA)libHandler, symbolName, &func, argSignature) != 0) {
    return NULL;
  } else {
    return (void*)func;
  }
#else
  TRACE_PRINTF("%s: sysDlsym %s\n", Me, symbolName);
  return dlsym((void *) libHandler, symbolName);
#endif // RVM_FOR_HARMONY
}
