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
#include <stdlib.h>

/**
 * Access host o/s command line arguments.
 * Taken:    -1
 *           null
 * Returned: number of arguments
 *           /or/
 * Taken:    arg number sought
 *           buffer to fill
 * Returned: number of bytes written to buffer (-1: arg didn't fit, buffer too small)
 */
EXTERNAL int sysArg(int argno, char *buf, int buflen)
{
  SYS_START();
  TRACE_PRINTF(SysErrorFile, "%s: sysArg %d\n", Me, argno);
  if (argno == -1) { // return arg count
    return JavaArgc;
  } else { // return i-th arg
    const char *src = JavaArgs[argno];
    for (int i = 0;; ++i)
    {
      if (*src == 0)
        return i;
      if (i == buflen)
        return -1;
      *buf++ = *src++;
    }
  }
  /* NOTREACHED */
}

/**
 * Copy SRC, a null-terminated string or a NULL pointer, into DEST, a buffer
 * with LIMIT characters capacity.   This is a helper function used by
 * sysGetEnv() and, later on, to be used by other functions returning strings
 * to Java.
 *
 * Handle the error handling for running out of space in BUF, in accordance
 * with the C '99 specification for snprintf() -- see sysGetEnv().
 *
 * Returned:  -2 if SRC is a NULL pointer.
 * Returned:  If enough space, the number of bytes copied to DEST.
 *
 *            If there is not enough space, we write what we can and return
 *            the # of characters that WOULD have been written to the final
 *            string BUF if enough space had been available, excluding any
 *            trailing '\0'.  This error handling is consistent with the C '99
 *            standard's behavior for the snprintf() system library function.
 *
 *            Note that this is NOT consistent with the behavior of most of
 *            the functions in this file that return strings to Java.
 *
 *            That should change with time.
 *
 *            This function will append a trailing '\0', if there is enough
 *            space, even though our caller does not need it nor use it.
 */
static int loadResultBuf(char * dest, int limit, const char *src)
{
  if ( ! src )      // Is it set?
    return -2;      // Tell caller it was unset.

  for (int i = 0;; ++i) {
    if ( i < limit ) // If there's room for the next char of the value ...
      dest[i] = src[i];   // ... write it into the destination buffer.
    if (src[i] == '\0')
      return i;      // done, return # of chars needed for SRC
  }
}


/**
 * Get the value of an enviroment variable.  (This refers to the C
 * per-process environment.)   Used, indirectly, by VMSystem.getenv()
 *
 * Taken:    VARNAME, name of the envar we want.
 *           BUF, a buffer in which to place the value of that envar
 *           LIMIT, the size of BUF
 * Returned: See the convention documented in loadResultBuf().
 *           0: A return value of 0 indicates that the envar was set with a
 *           zero-length value.   (Distinguised from unset, see below)
 *           -2: Indicates that the envar was unset.  This is distinguished
 *           from a zero-length value (see above).
 */
EXTERNAL int sysGetenv(const char *varName, char *buf, int limit)
{
  SYS_START();
  TRACE_PRINTF(SysErrorFile, "%s: sysGetenv %d\n", Me, varName);
  return loadResultBuf(buf, limit, getenv(varName));
}

/**
 * Parse memory sizes.
 * @param sizeName "initial heap" or "maximum heap" or "initial stack" or "maximum stack"
 * @param sizeFlag "ms" or "mx" or "ss" or "sg" or "sx"
 * @param defaultFactor "M" or "K" are used
 * @param roundTo round to PAGE_SIZE_BYTES or to 4.
 * @param token e.g., "-Xms200M" or "-Xms200"
 * @param subtoken /* e.g., "200M" or "200"
 * @return negative values to indicate errors.
 */
EXTERNAL jlong sysParseMemorySize(const char *sizeName, const char *sizeFlag,
                                  const char *defaultFactor, int roundTo, 
                                  const char *token, const char *subtoken)
{
  SYS_START();
  TRACE_PRINTF(SysErrorFile, "%s: sysParseMemorySize %s\n", Me, token);
  bool fastExit = false;
  unsigned ret_uns=  parse_memory_size(sizeName, sizeFlag, defaultFactor,
                                       (unsigned) roundTo, token, subtoken,
                                       &fastExit);
  if (fastExit)
    return -1;
  else
    return (jlong) ret_uns;
}
