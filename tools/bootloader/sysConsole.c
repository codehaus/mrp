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

/** Console write (java character). */
EXTERNAL void sysConsoleWriteChar(unsigned value)
{
  SYS_START();
  char c = (value > 127) ? '?' : (char)value;
  // use high level stdio to ensure buffering policy is observed
  CONSOLE_PRINTF("%c", c);
}

/** Console write (java integer). */
EXTERNAL void sysConsoleWriteInteger(int value, int hexToo)
{
  SYS_START();
  if (hexToo==0 /*false*/)
    CONSOLE_PRINTF("%d", value);
  else if (hexToo==1 /*true - also print in hex*/)
    CONSOLE_PRINTF("%d (0x%08x)", value, value);
  else    /* hexToo==2 for only in hex */
    CONSOLE_PRINTF("0x%08x", value);
}

/** Console write (java long). */
EXTERNAL void sysConsoleWriteLong(long long value, int hexToo)
{
  SYS_START();
  if (hexToo==0 /*false*/)
    CONSOLE_PRINTF("%lld", value);
  else if (hexToo==1 /*true - also print in hex*/) {
    int value1 = (value >> 32) & 0xFFFFFFFF;
    int value2 = value & 0xFFFFFFFF;
    CONSOLE_PRINTF("%lld (0x%08x%08x)", value, value1, value2);
  } else { /* hexToo==2 for only in hex */
    int value1 = (value >> 32) & 0xFFFFFFFF;
    int value2 = value & 0xFFFFFFFF;
    CONSOLE_PRINTF("0x%08x%08x", value1, value2);
  }
}

/** Console write (java double). */
EXTERNAL void sysConsoleWriteDouble(double value,  int postDecimalDigits)
{
  SYS_START();
  char tmp[5] = {'%', '.', '0'+postDecimalDigits, 'f', 0};
  if (value != value) {
    CONSOLE_PRINTF("NaN");
  } else {
    if (postDecimalDigits > 9) postDecimalDigits = 9;
    CONSOLE_PRINTF(tmp, value);
  }
}
