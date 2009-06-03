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
#include <time.h>
#include <sys/time.h>
#endif

EXTERNAL long long sysCurrentTimeMillis()
{
  SYS_START();
  TRACE_PRINTF("%s: sysCurrentTimeMillis\n", Me);
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
  TRACE_PRINTF("%s: sysNanoTime\n", Me);
#if RVM_FOR_HARMONY
  // TODO: there's probably a more accurate way to do this
  retVal = hytime_current_time_millis() * 1000000;
#else
#ifdef __MACH__
  struct timeval tv;
  gettimeofday(&tv,NULL);
  retVal=tv.tv_sec;
  retVal*=1000;
  retVal*=1000;
  retVal+=tv.tv_usec;
  retVal*=1000;
#else
  struct timespec tp;
  int rc = clock_gettime(CLOCK_REALTIME, &tp);
  if (rc != 0) {
    retVal = rc;
    ERROR_PRINTF("%s: sysNanoTime: Non-zero return code %d from clock_gettime\n", Me, rc);
  } else {
    retVal = (((long long) tp.tv_sec) * 1000000000) + tp.tv_nsec;
  }
#endif
#endif // RVM_FOR_HARMONY
  return retVal;
}
