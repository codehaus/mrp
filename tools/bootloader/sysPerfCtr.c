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

/*
 * Performance counter support using the linux perf event system.
 */
#ifdef RVM_WITH_PERFEVENT
#include <perfmon/pfmlib_perf_event.h>

static int enabled = 0;
static int *perf_event_fds;
static struct perf_event_attr *perf_event_attrs;
#endif

EXTERNAL void sysPerfEventInit(int numEvents)
{
  SYS_START();
  TRACE_PRINTF("%s: sysPerfEventInit %d\n", Me, numEvents);
#ifdef RVM_WITH_PERFEVENT
  int ret = pfm_initialize();
  if (ret != PFM_SUCCESS) {
    ERROR_PRINTF("sysPerfEventInit: error in pfm_initialize: %s", pfm_strerror(ret));
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
  perf_event_fds = (int*)sysCalloc(numEvents * sizeof(int));
  perf_event_attrs = (struct perf_event_attr *)sysCalloc(numEvents * sizeof(struct perf_event_attr));
  for(int i=0; i < numEvents; i++) {
    perf_event_attrs[i].size = sizeof(struct perf_event_attr);
  }
  enabled = 1;
#endif
}

EXTERNAL void sysPerfEventCreate(int id, const char *eventName)
{
  SYS_START();
  TRACE_PRINTF("%s: sysPerfEventCreate %d %s\n", Me, id, eventName);
#ifdef RVM_WITH_PERFEVENT
  struct perf_event_attr *pe = (perf_event_attrs + id);
  int ret = pfm_get_perf_event_encoding(eventName, PFM_PLM3, pe, NULL, NULL);
  if (ret != PFM_SUCCESS) {
    ERROR_PRINTF("sysPerfEventCreate: error in creating event %d '%s': %s\n", id, eventName, pfm_strerror(ret));
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
  pe->read_format = PERF_FORMAT_TOTAL_TIME_ENABLED | PERF_FORMAT_TOTAL_TIME_RUNNING;
  pe->disabled = 1;
  pe->inherit = 1;
  perf_event_fds[id] = perf_event_open(pe, 0, -1, -1, 0);
  if (perf_event_fds[id] == -1) {
    ERROR_PRINTF("sysPerfEventCreate: error in perf_event_open for event %d '%s'", id, eventName);
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
#endif
}

EXTERNAL void sysPerfEventEnable()
{
  SYS_START();
  TRACE_PRINTF("%s: sysPerfEventEnable\n", Me);
#ifdef RVM_WITH_PERFEVENT
  if (enabled) {
    if (prctl(PR_TASK_PERF_EVENTS_ENABLE)) {
      ERROR_PRINTF("sysPerfEventEnable: error in prctl(PR_TASK_PERF_EVENTS_ENABLE)");
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
  }
#endif
}

EXTERNAL void sysPerfEventDisable()
{
  SYS_START();
  TRACE_PRINTF("%s: sysPerfEventDisable\n", Me);
#ifdef RVM_WITH_PERFEVENT
  if (enabled) {
    if (prctl(PR_TASK_PERF_EVENTS_DISABLE)) {
      ERROR_PRINTF("sysPerfEventEnable: error in prctl(PR_TASK_PERF_EVENTS_DISABLE)");
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
  }
#endif
}

EXTERNAL void sysPerfEventRead(int id, int64_t *values)
{
  SYS_START();
  TRACE_PRINTF("%s: sysPerfEventDisable\n", Me);
#ifdef RVM_WITH_PERFEVENT
  int expectedBytes = 3 * 8;
  int ret = sysReadBytes(perf_event_fds[id], values, expectedBytes);
  if (ret < 0) {
    ERROR_PRINTF("sysPerfEventRead: error reading event: %s", strerror(errno));
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
  if (ret != expectedBytes) {
    ERROR_PRINTF("sysPerfEventRead: read of perf event did not return 3 64-bit values");
    sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
  }
#endif
}
