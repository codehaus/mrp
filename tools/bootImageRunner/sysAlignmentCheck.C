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

volatile int numNativeAlignTraps;
volatile int numEightByteAlignTraps;
volatile int numBadAlignTraps;

static volatile int numEnableAlignCheckingCalls = 0;
static volatile int numDisableAlignCheckingCalls = 0;

EXTERNAL void sysEnableAlignmentChecking() {
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysEnableAlignmentChecking\n", Me);
#ifdef RVM_WITH_ALIGNMENT_CHECKING
  numEnableAlignCheckingCalls++;
  if (numEnableAlignCheckingCalls > numDisableAlignCheckingCalls) {
    asm("pushf\n\t"
        "orl $0x00040000,(%esp)\n\t"
        "popf");
  }
#endif
}

EXTERNAL void sysDisableAlignmentChecking() {
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysDisableAlignmentChecking\n", Me);
#ifdef RVM_WITH_ALIGNMENT_CHECKING
  numDisableAlignCheckingCalls++;
  asm("pushf\n\t"
      "andl $0xfffbffff,(%esp)\n\t"
      "popf");
#endif
}

EXTERNAL void sysReportAlignmentChecking() {
  SYS_START();
  TRACE_PRINTF(SysTraceFile, "%s: sysReportAlignmentChecking\n", Me);
#ifdef RVM_WITH_ALIGNMENT_CHECKING
  int dummy[2];
  int prevNumNativeTraps;
  int enabled;

  CONSOLE_PRINTF(SysTraceFile, "\nAlignment checking report:\n\n");
  CONSOLE_PRINTF(SysTraceFile, "# native traps (ignored by default):             %d\n", numNativeAlignTraps);
  CONSOLE_PRINTF(SysTraceFile, "# 8-byte access traps (ignored by default):      %d\n", numEightByteAlignTraps);
  CONSOLE_PRINTF(SysTraceFile, "# bad access traps (throw exception by default): %d (should be zero)\n\n", numBadAlignTraps);
  CONSOLE_PRINTF(SysTraceFile, "# calls to sysEnableAlignmentChecking():         %d\n", numEnableAlignCheckingCalls);
  CONSOLE_PRINTF(SysTraceFile, "# calls to sysDisableAlignmentChecking():        %d\n\n", numDisableAlignCheckingCalls);
  CONSOLE_PRINTF(SysTraceFile, "# native traps again (to see if changed):        %d\n", numNativeAlignTraps);
  CONSOLE_PRINTF(SysTraceFile, "# 8-byte access again (to see if changed):       %d\n\n", numEightByteAlignTraps);

  // cause a native trap to see if traps are enabled
  prevNumNativeTraps = numNativeAlignTraps;
  *(int*)((char*)dummy + 1) = 0x12345678;
  enabled = (numNativeAlignTraps != prevNumNativeTraps);

  CONSOLE_PRINTF(SysTraceFile, "# native traps again (to see if changed):        %d\n", numNativeAlignTraps);
  CONSOLE_PRINTF(SysTraceFile, "# 8-byte access again (to see if changed):       %d\n\n", numEightByteAlignTraps);
  CONSOLE_PRINTF(SysTraceFile, "Current status of alignment checking:            %s (should be on)\n\n", (enabled ? "on" : "off"));
#endif
}
