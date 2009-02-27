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

/*
 * Performance counter support using the 'perfctr' system.
 * 
 * The implementations are 'out of line' in perfctr.C
 */

EXTERNAL int sysPerfCtrInit(int metric)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysPerfCtrInit\n", Me);
#ifdef RVM_WITH_PERFCTR
  return perfCtrInit(metric);
#else
  return 0;
#endif
}

EXTERNAL long long sysPerfCtrReadCycles()
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysPerfCtrReadCycles\n", Me);
#ifdef RVM_WITH_PERFCTR
  return perfCtrReadCycles();
#else
  return 0;
#endif
}

EXTERNAL long long sysPerfCtrReadMetric()
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysPerfCtrReadMetric\n", Me);
#ifdef RVM_WITH_PERFCTR
  return perfCtrReadMetric();
#else
  return 0;
#endif
}

/*
 * The following is unused at present
 */
EXTERNAL int sysPerfCtrRead(char *str)
{
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysPerfCtrRead\n", Me);
#ifdef RVM_WITH_PERFCTR
  return perfCtrRead(str);
#else
  return 0;
#endif
}
