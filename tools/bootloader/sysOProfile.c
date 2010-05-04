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

#ifdef RVM_WITH_OPROFILE
#include <opagent.h>
#include <errno.h>
#endif

EXTERNAL Address sysOProfileOpenAgent()
{
  Address result = NULL;
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileOpenAgent\n", Me);
#ifdef RVM_WITH_OPROFILE
  result = op_open_agent();
  if (result == NULL) {
    ERROR_PRINTF("%s: Trouble opening OProfile agent - %s", Me, strerror(errno));
  }
#endif
  return result;
}

EXTERNAL void sysOProfileCloseAgent(Address opHandle)
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileCloseAgent(%p)\n", Me, opHandle);
#ifdef RVM_WITH_OPROFILE
  int result = op_close_agent(opHandle);
  if (result != 0) {
    ERROR_PRINTF("%s: Trouble closing OProfile agent - %s", Me, strerror(errno));
  }
#endif
}

EXTERNAL void sysOProfileWriteNativeCode(Address opHandle, char const * symbolName,
                                         Address codeAddress, int codeLength)
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileWriteNativeCode(%p,%s,%p,%d)\n", Me, opHandle, symbolName, codeAddress, codeLength);
#ifdef RVM_WITH_OPROFILE
  int result = op_write_native_code(opHandle, symbolName, codeAddress, codeAddress, codeLength);
  if (result != 0) {
    ERROR_PRINTF("%s: Trouble in OProfile write native code - %s", Me, strerror(errno));
  }
#endif
}

EXTERNAL void sysOProfileUnloadNativeCode(Address opHandle, Address codeAddress)
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileUnloadNativeCode(%p,%p)\n", Me, opHandle, codeAddress);
#ifdef RVM_WITH_OPROFILE
  int result = op_unload_native_code(opHandle, codeAddress);
  if (result != 0) {
    ERROR_PRINTF("%s: Trouble in OProfile unload native code - %s", Me, strerror(errno));
  }
#endif
}

#ifdef RVM_WITH_OPROFILE
struct compileMap {
  Address hdl;
  Address code;
  int entries_count;
  int entries_length;
  struct debug_line_info * entries;
};
#endif

EXTERNAL Address sysOProfileStartCompileMap(Address opHandle, Address codeAddress)
{
  Address result;
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileStartCompileMap(%p,%p)\n", Me, opHandle,codeAddress);
#ifdef RVM_WITH_OPROFILE
  struct compileMap *cmap = (struct compileMap *)sysMalloc(sizeof(struct compileMap));
  cmap->hdl = opHandle;
  cmap->code = codeAddress;
  cmap->entries_count = 0;
  cmap->entries_length = 16;
  cmap->entries = (struct debug_line_info *)sysCalloc(sizeof(struct debug_line_info[16]));
  result = cmap;
#endif
  return result;
}

EXTERNAL void sysOProfileAddToCompileMap(Address _cmap, Address offs,
                                         char const * fileName, int lineNumber)
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileAddToCompileMap(%p,%p,%s,%d)\n", Me, _cmap, offs, fileName, lineNumber);
#ifdef RVM_WITH_OPROFILE
  struct compileMap *cmap = (struct compileMap *)_cmap;
  if (cmap->entries_count+1 == cmap->entries_length) {
    struct debug_line_info *newEntries = (struct debug_line_info *)
      sysCalloc(sizeof(struct debug_line_info[cmap->entries_length+16]));
    int i;
    for (i=0; i < cmap->entries_length; i++) {
      newEntries[i].vma = cmap->entries[i].vma;
      newEntries[i].lineno = cmap->entries[i].lineno;
      newEntries[i].filename = cmap->entries[i].filename;
    }
    sysFree(cmap->entries);
    cmap->entries = newEntries;
    cmap->entries_length += 16;
  }
  cmap->entries[cmap->entries_count].vma = offs;
  cmap->entries[cmap->entries_count].lineno = lineNumber;
  cmap->entries[cmap->entries_count].filename = fileName;
  cmap->entries_count++;
#endif
}

EXTERNAL void  sysOProfileFinishCompileMap(Address _cmap)
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileFinishCompileMap(%p)\n", Me, _cmap);
#ifdef RVM_WITH_OPROFILE
  struct compileMap *cmap = (struct compileMap *)_cmap;
  if(cmap->entries_count > 0) {
    int result = op_write_debug_line_info(cmap->hdl, cmap->code, cmap->entries_count, cmap->entries);
    if (result != 0) {
      ERROR_PRINTF("%s: Trouble in OProfile write debug line - %s", Me, strerror(errno));
    }
  }
  sysFree(cmap->entries);
  sysFree(cmap);
#endif
}
