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
#include <time.h>
#include <sys/time.h>

#if !defined(RVM_FOR_HARMONY) && defined(__MACH__)
mach_timebase_info_data_t timebaseInfo;
#endif

EXTERNAL long long sysCurrentTimeMillis()
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysCurrentTimeMillis\n", Me);
#ifdef RVM_FOR_HARMONY
  return hytime_current_time_millis();
#else
  int rc;
  long long returnValue;
  struct timeval tv;
  struct timezone tz;
  
  returnValue = 0;

  rc = gettimeofday(&tv, &tz);
  if (rc != 0) {
    returnValue = rc;
  } else {
    returnValue = ((long long) tv.tv_sec * 1000) + tv.tv_usec/1000;
  }
  return returnValue;
#endif // RVM_FOR_HARMONY
}

EXTERNAL long long sysNanoTime()
{
  long long retVal;
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysNanoTime\n", Me);
#if RVM_FOR_HARMONY
  // TODO: there's probably a more accurate way to do this
  retVal = hytime_current_time_millis() * 1000;
#else
#ifdef __MACH__
  Nanoseconds nanoTime;
  unsigned long long high;
  unsigned long long low;

  low = mach_absolute_time();

  high = low >> 32;
  low &= 0xffffffff;

  high *= timebaseInfo.numer;
  low *= timebaseInfo.numer;

  retVal = (high / timebaseInfo.denom) << 32;
  retVal += (low + ((high % timebaseInfo.denom) << 32)) / timebaseInfo.denom;
#else
  struct timespec tp;
  int rc = clock_gettime(CLOCK_MONOTONIC, &tp);
  if (rc != 0) {
    retVal = rc;
    if (lib_verbose) {
      fprintf(stderr, "sysNanoTime: Non-zero return code %d from clock_gettime\n", rc);
    }
  } else {
    retVal = (((long long) tp.tv_sec) * 1000000000) + tp.tv_nsec;
  }
#endif
#endif // RVM_FOR_HARMONY
  return retVal;
}
