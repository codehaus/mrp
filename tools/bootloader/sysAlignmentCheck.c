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

volatile int numNativeAlignTraps;
volatile int numBadAlignTraps;

volatile int numEnableAlignCheckingCalls = 0;
static volatile int numDisableAlignCheckingCalls = 0;

EXTERNAL void sysEnableAlignmentChecking() {
  SYS_START();
  TRACE_PRINTF("%s: sysEnableAlignmentChecking\n", Me);
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
  TRACE_PRINTF("%s: sysDisableAlignmentChecking\n", Me);
#ifdef RVM_WITH_ALIGNMENT_CHECKING
  numDisableAlignCheckingCalls++;
  asm("pushf\n\t"
      "andl $0xfffbffff,(%esp)\n\t"
      "popf");
#endif
}

EXTERNAL void sysReportAlignmentChecking() {
  SYS_START();
  TRACE_PRINTF("%s: sysReportAlignmentChecking\n", Me);
#ifdef RVM_WITH_ALIGNMENT_CHECKING
  int dummy[2];
  int prevNumNativeTraps;
  int enabled;

  CONSOLE_PRINTF("\nAlignment checking report:\n\n");
  CONSOLE_PRINTF("# native traps (ignored by default):             %d\n", numNativeAlignTraps);
  CONSOLE_PRINTF("# bad access traps (throw exception by default): %d (should be zero)\n\n", numBadAlignTraps);
  CONSOLE_PRINTF("# calls to sysEnableAlignmentChecking():         %d\n", numEnableAlignCheckingCalls);
  CONSOLE_PRINTF("# calls to sysDisableAlignmentChecking():        %d\n\n", numDisableAlignCheckingCalls);
  CONSOLE_PRINTF("# native traps again (to see if changed):        %d\n", numNativeAlignTraps);

  // cause a native trap to see if traps are enabled
  prevNumNativeTraps = numNativeAlignTraps;
  *(int*)((char*)dummy + 1) = 0x12345678;
  enabled = (numNativeAlignTraps != prevNumNativeTraps);

  CONSOLE_PRINTF("# native traps again (to see if changed):        %d\n", numNativeAlignTraps);
  CONSOLE_PRINTF("Current status of alignment checking:            %s (should be on)\n\n", (enabled ? "on" : "off"));
#endif
}
