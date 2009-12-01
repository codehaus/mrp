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

#ifdef RVM_FOR_OPROFILE
#include <opagent.h>
#endif

EXTERNAL Address sysOProfileOpenAgent()
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileOpenAgent\n", Me);
#ifdef RVM_FOR_OPROFILE
  return op_open_agent();
#else
  return 0;
#endif
}

EXTERNAL void sysOProfileCloseAgent(Address opHandle)
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileCloseAgent(%p)\n", Me, opHandle);
#ifdef RVM_FOR_OPROFILE
  return op_close_agent(opHandle);
#else
  return 0;
#endif
}

EXTERNAL void sysOProfileWriteNativeCode(Address opHandle, char const * symbolName,
                                         Address codeAddress, int codeLength)
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileCloseAgent(%p)\n", Me, opHandle);
#ifdef RVM_FOR_OPROFILE
  op_write_native_code(opHandle, symbolName, codeAddress, codeAddress, codeLength);
#endif
}

#ifdef RVM_FOR_OPROFILE
struct compileMap {
  Address hdl;
  Address code;
  int entries_count;
  int entries_length;
  struct debug_line_info const * entries;
};
#endif

EXTERNAL Address sysOProfileStartCompileMap(Address opHandle, Address codeAddress)
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileStartCompileMap(%p,%p)\n", Me, opHandle,codeAddress);
#ifdef RVM_FOR_OPROFILE
  struct compileMap *cmap = (struct compileMap *)sysMalloc(sizeof(struct compileMap));
  cmap->hdl = opHandle;
  cmap->code = codeAddress;
  cmap->entries_count = 0;
  cmap->entries_length = 16;
  cmap->entries = (struct debug_line_info *)sysMalloc(sizeof(struct debug_line_info[16]));
#endif
}

EXTERNAL void sysOProfileAddToCompileMap(Address _cmap, Address offs,
                                         char const * fileName, int lineNumber)
{
  SYS_START();
  TRACE_PRINTF("%s: sysOProfileAddToCompileMap(%p,%p,%s,%d)\n", Me, _cmap, offs, fileName, lineNumber);
#ifdef RVM_FOR_OPROFILE
  struct compileMap *cmap = (strucy compileMap *)_cmap;
  if (cmap->entries_count+1 == cmap->entries_length) {
    struct debug_line_info *newEntries = (struct debug_line_info *)
      sysMalloc(sizeof(struct debug_line_info[cmap->entries_length+16]));
    for (int i=0; i < cmap->entries_length; i++) {
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
#ifdef RVM_FOR_OPROFILE
  struct compileMap *cmap = (strucy compileMap *)_cmap;
  op_write_debug_line_info(cmap->hdl, cmap->code, cmap->entries_count, cmap->entries);
  sysFree(cmap->entries);
  sysFree(cmap);
#endif
}
