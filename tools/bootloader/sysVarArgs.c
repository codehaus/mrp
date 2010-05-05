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
#include <stdarg.h>

#ifdef RVM_FOR_WINDOWS
#define va_copy(aq, ap) (aq = ap);
#endif // RVM_FOR_WINDOWS

/**
 * Create copy of va_list that can be updated and worked upon
 */
EXTERNAL va_list* sysVaCopy(va_list ap)
{
  SYS_START();
  va_list *ap_;
  TRACE_PRINTF("%s: sysVaCopy\n", Me);
  ap_ = (va_list*)sysMalloc(sizeof(va_list));
  va_copy (*ap_, ap);
  return ap_;
}

/**
 * End use of va_list
 */
EXTERNAL void sysVaEnd(va_list *ap)
{
  SYS_START();
  TRACE_PRINTF("%s: sysVaEnd\n", Me);
  va_end(*ap);
  sysFree(ap);
}


/**
 * Read jboolean var arg
 *
 * @param va_list [in,out] var arg list update to next entry after read
 * @return argument
 */
EXTERNAL jboolean sysVaArgJboolean(va_list *ap)
{
  SYS_START();
  jboolean result = (jboolean)va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJboolean %d\n", Me, result);
  return result;
}

/**
 * Read jbyte var arg
 *
 * @param va_list [in,out] var arg list update to next entry after read
 * @return argument
 */
EXTERNAL jbyte sysVaArgJbyte(va_list *ap)
{
  SYS_START();
  jbyte result = (jbyte)va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJbyte %d\n", Me, result);
  return result;
}

/**
 * Read jchar var arg
 *
 * @param va_list [in,out] var arg list update to next entry after read
 * @return argument
 */
EXTERNAL jchar sysVaArgJchar(va_list *ap)
{
  SYS_START();
  jchar result = (jchar)va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJchar %d\n", Me, result);
  return result;
}

/**
 * Read jshort var arg
 *
 * @param va_list [in,out] var arg list update to next entry after read
 * @return argument
 */
EXTERNAL jshort sysVaArgJshort(va_list *ap)
{
  SYS_START();
  jshort result = (jshort)va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJshort %d\n", Me, result);
  return result;
}

/**
 * Read jint var arg
 *
 * @param va_list [in,out] var arg list update to next entry after read
 * @return argument
 */
EXTERNAL jint sysVaArgJint(va_list *ap)
{
  SYS_START();
  jint result = va_arg(*ap, jint);
  TRACE_PRINTF("%s: sysVaArgJint %d\n", Me, result);
  return result;
}

/**
 * Read jlong var arg
 *
 * @param va_list [in,out] var arg list update to next entry after read
 * @return argument
 */
EXTERNAL jlong sysVaArgJlong(va_list *ap)
{
  SYS_START();
  jlong result = va_arg(*ap, jlong);
  TRACE_PRINTF("%s: sysVaArgJlong %lld\n", Me, result);
  return result;
}

/**
 * Read jfloat var arg
 *
 * @param va_list [in,out] var arg list update to next entry after read
 * @return argument
 */
EXTERNAL jfloat sysVaArgJfloat(va_list *ap)
{
  SYS_START();
  jfloat result = (jfloat)va_arg(*ap, jdouble);
  TRACE_PRINTF("%s: sysVaArgJfloat %f\n", Me, result);
  return result;
}

/**
 * Read jdouble var arg
 *
 * @param va_list [in,out] var arg list update to next entry after read
 * @return argument
 */
EXTERNAL jdouble sysVaArgJdouble(va_list *ap)
{
  SYS_START();
  jdouble result = va_arg(*ap, jdouble);
  TRACE_PRINTF("%s: sysVaArgJdouble %f\n", Me, result);
  return result;
}

/**
 * Read jobject var arg
 *
 * @param va_list [in,out] var arg list update to next entry after read
 * @return argument
 */
EXTERNAL jobject sysVaArgJobject(va_list *ap)
{
  SYS_START();
  jobject result = va_arg(*ap, jobject);
  TRACE_PRINTF("%s: sysVaArgJobject %p\n", Me, result);
  return result;
}
