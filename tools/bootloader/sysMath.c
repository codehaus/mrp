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

#include <errno.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include "sys.h"

const double maxlong = 0.5 + (double)0x7fffffffffffffffLL;
const double maxint  = 0.5 + (double)0x7fffffff;

EXTERNAL double sysLongToDouble(long long a)
{
  SYS_START();
  TRACE_PRINTF("%s: sysLongToDouble %ld\n", Me, a);
  return (double)a;
}

EXTERNAL float sysLongToFloat(long long a)
{
  SYS_START();
  TRACE_PRINTF("%s: sysLongToFloat %ld\n", Me, a);
  return (float)a;
}

EXTERNAL int sysFloatToInt(float a)
{
  SYS_START();
  TRACE_PRINTF("%s: sysFloatToInt %f\n", Me, a);
  if (maxint <= a) return 0x7fffffff;
  if (a <= -maxint) return 0x80000000;
  if (a != a) return 0; // NaN => 0
  return (int)a;
}

EXTERNAL int sysDoubleToInt(double a)
{
  SYS_START();
  TRACE_PRINTF("%s: sysDoubleToInt %f\n", Me, a);
  if (maxint <= a) return 0x7fffffff;
  if (a <= -maxint) return 0x80000000;
  if (a != a) return 0; // NaN => 0
  return (int)a;
}

EXTERNAL long long sysFloatToLong(float a)
{
  SYS_START();
  TRACE_PRINTF("%s: sysFloatToLong %f\n", Me, a);
  if (maxlong <= a) return 0x7fffffffffffffffLL;
  if (a <= -maxlong) return 0x8000000000000000LL;
  return (long long)a;
}

EXTERNAL long long sysDoubleToLong(double a)
{
  SYS_START();
  TRACE_PRINTF("%s: sysDoubleToLong %f\n", Me, a);
  if (maxlong <= a) return 0x7fffffffffffffffLL;
  if (a <= -maxlong) return 0x8000000000000000LL;
  return (long long)a;
}

// sysDoubleRemainder is only used on PPC
EXTERNAL double sysDoubleRemainder(double a, double b)
{
  double tmp;
  SYS_START();
  TRACE_PRINTF("%s: sysDoubleRemainder %f %% %f\n", Me, a);
#ifdef _WIN32
  tmp = fmod(a, b);
#else
  tmp = remainder(a, b);
#endif
  if (a > 0.0) {
    if (b > 0.0) {
      if (tmp < 0.0) {
        tmp += b;
      }
    } else if (b < 0.0) {
      if (tmp < 0.0) {
        tmp -= b;
      }
    }
  } else {
    if (b > 0.0) {
      if (tmp > 0.0) {
        tmp -= b;
      }
    } else {
      if (tmp > 0.0) {
        tmp += b;
      }
    }
  }
  return tmp;
}

/**
 * Used to parse command line arguments that are doubles and floats
 * early in booting before it is safe to call Float.valueOf or
 * Double.valueOf.  This is only used in parsing command-line
 * arguments, so we can safely print error messages that assume the
 * user specified this number as part of a command-line argument.
 */
EXTERNAL float sysPrimitiveParseFloat(const char * buf)
{
  char *end;
  float f;
  SYS_START();
  TRACE_PRINTF("%s: sysPrimitiveParseFloat %s\n", Me, buf);
  if (! buf[0] ) {
    ERROR_PRINTF("%s: Got an empty string as a command-line"
                 " argument that is supposed to be a"
                 " floating-point number\n", Me);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  errno = 0;
  f = (float)strtod(buf, &end);
  if (errno) {
    ERROR_PRINTF("%s: Trouble while converting the"
                 " command-line argument \"%s\" to a"
                 " floating-point number: %s\n", Me,
                 buf, strerror(errno));
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  if (*end != '\0') {
    ERROR_PRINTF("%s: Got a command-line argument that"
                 " is supposed to be a floating-point value,"
                 " but isn't: %s\n", Me, buf);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  return f;
}

/**
 * Used to parse command line arguments that are ints and bytes early
 * in booting before it is safe to call Integer.parseInt and
 * Byte.parseByte This is only used in parsing command-line arguments,
 * so we can safely print error messages that assume the user
 * specified this number as part of a command-line argument.
 */
EXTERNAL int sysPrimitiveParseInt(const char * buf)
{
  char *end;
  long l;
  int32_t ret;
  SYS_START();
  TRACE_PRINTF("%s: sysPrimitiveParseInt %s\n", Me, buf);
  if (! buf[0] ) {
    ERROR_PRINTF("%s: Got an empty string as a command-line"
                 " argument that is supposed to be an integer\n", Me);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  errno = 0;
  l = strtol(buf, &end, 0);
  if (errno) {
    ERROR_PRINTF("%s: Trouble while converting the"
                 " command-line argument \"%s\" to an integer: %s\n",
                 Me, buf, strerror(errno));
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  if (*end != '\0') {
    ERROR_PRINTF("%s: Got a command-line argument that is supposed to be an integer, but isn't: %s\n", Me, buf);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  ret = l;
  if ((long) ret != l) {
    ERROR_PRINTF("%s: Got a command-line argument that is supposed to be an integer, but its value does not fit into a Java 32-bit integer: %s\n", Me, buf);
    exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
  }
  return ret;
}

// VMMath
EXTERNAL double sysVMMathSin(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathSin %f\n", Me, a);
  return sin(a);
}

EXTERNAL double sysVMMathCos(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathCos %f\n", Me, a);
  return cos(a);
}

EXTERNAL double sysVMMathTan(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathTan %f\n", Me, a);
  return tan(a);
}

EXTERNAL double sysVMMathAsin(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathAsin %f\n", Me, a);
  return asin(a);
}

EXTERNAL double sysVMMathAcos(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathAcos %f\n", Me, a);
  return acos(a);
}

EXTERNAL double sysVMMathAtan(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathAtan %f\n", Me, a);
  return atan(a);
}

EXTERNAL double sysVMMathAtan2(double a, double b) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathAtan2 %f %f\n", Me, a, b);
  return atan2(a, b);
}

EXTERNAL double sysVMMathCosh(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathCosh %f\n", Me, a);
  return cosh(a);
}

EXTERNAL double sysVMMathSinh(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathSinh %f\n", Me, a);
  return sinh(a);
}

EXTERNAL double sysVMMathTanh(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathTanh %f\n", Me, a);
  return tanh(a);
}

EXTERNAL double sysVMMathExp(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathExp %f\n", Me, a);
  return exp(a);
}

EXTERNAL double sysVMMathLog(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathLog %f\n", Me, a);
  return log(a);
}

EXTERNAL double sysVMMathSqrt(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathSqrt %f\n", Me, a);
  return sqrt(a);
}

EXTERNAL double sysVMMathPow(double a, double b) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathPow %f %f\n", Me, a, b);
  return pow(a, b);
}

EXTERNAL double sysVMMathIEEEremainder(double a, double b) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathIEEEremainder %f %f\n", Me, a, b);
#ifdef _WIN32
  return fmod(a, b);
#else
  return remainder(a, b);
#endif
}

EXTERNAL double sysVMMathCeil(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathCeil %f\n", Me, a);
  return ceil(a);
}

EXTERNAL double sysVMMathFloor(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathFloor %f\n", Me, a);
  return floor(a);
}

EXTERNAL double sysVMMathRint(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathRint %f\n", Me, a);
#ifdef _WIN32
  ERROR_PRINTF("%s: unsupported math operation rint\n", Me);
  sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  return 0.0;
#else
  return rint(a);
#endif
}

EXTERNAL double sysVMMathCbrt(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathCbrt %f\n", Me, a);
#ifdef _WIN32
  ERROR_PRINTF("%s: unsupported math operation cbrt\n", Me);
  sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  return 0.0;
#else
  return cbrt(a);
#endif
}

EXTERNAL double sysVMMathExpm1(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathExpm1 %f\n", Me, a);
#ifdef _WIN32
  ERROR_PRINTF("%s: unsupported math operation expm1\n", Me);
  sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  return 0.0;
#else
  return expm1(a);
#endif
}

EXTERNAL double sysVMMathHypot(double a, double b) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathHypot %f %f\n", Me, a, b);
  return hypot(a, b);
}

EXTERNAL double sysVMMathLog10(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathLog10 %f\n", Me, a);
  return log10(a);
}

EXTERNAL double sysVMMathLog1p(double a) {
  SYS_START();
  TRACE_PRINTF("%s: sysVMMathLog1p %f\n", Me, a);
#ifdef _WIN32
  ERROR_PRINTF("%s: unsupported math operation log1p\n", Me);
  sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  return 0.0;
#else
  return log1p(a);
#endif
}
