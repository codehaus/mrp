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

/*
 * Architecture specific signal handling routines
 */
#include "sys.h"

/* Macros to modify signal context */
#ifdef RVM_FOR_OSX
#define GET_GPR(info, rnum)        (info->r##rnum)
#define SET_GPR(info, rnum, value) (info->r##rnum=value)
#else
#define GET_GPR(info, r)             ((info)->gpr[(r)])
#define SET_GPR(info, r, value)     (((info)->gpr[(r)]) = (value))
#endif


/**
 * Read the addresses of pointers for important values out of the context
 *
 * @param context [in] context to read from
 * @param instructionPtr [out] pointer to instruction
 * @param instructionFollowingPtr [out] pointer to instruction following this
 * @param threadPtr [out] ptr to current VM thread object
 * @param jtocPtr [out] ptr to JTOC
 */
EXTERNAL void readContextInformation(void *context, Address *instructionPtr,
                                     Address *instructionFollowingPtr,
                                     Address *threadPtr, Address *jtocPtr)
{
  MAKE_INFO(info, context);
  Address ip = PPC_IAR(context);
  *instructionPtr  = ip;
  *instructionFollowingPtr = ip+4;
  *threadPtr = GET_GPR(info, Constants_FRAME_POINTER);
  *jtocPtr = bootRecord->tocRegister; /* could use register holding JTOC on PPC */
}


/**
 * Read frame pointer at point of the signal
 *
 * @param context [in] context to read from
 * @param threadPtr [in] address of thread information
 * @return address of stack frame
 */
EXTERNAL Address readContextFramePointer(void *context, Address UNUSED threadPtr)
{
  MAKE_INFO(info, context);  
  return GET_GPR(info, Constants_FRAME_POINTER);
}

/**
 * Set up the context to invoke RVMThread.dumpStackAndDie
 *
 * @param context [in,out] registers at point of signal/trap
 */
EXTERNAL void setupDumpStackAndDie(void *context)
{    
  Offset DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;  
  Address localJTOC = bootRecord->tocRegister;
  Address dumpStack = *(Address *)((char *)VmToc + DumpStackAndDieOffset);
#ifdef RVM_FOR_LINUX
  save->link = save->nip + 4; // +4 so it looks like a return address
  save->nip = dumpStack;
#elif defined RVM_FOR_OSX
  save->lr = save->srr0 + 4; // +4 so it looks like a return address
  save->srr0 = dumpStack;
#elif defined RVM_FOR_AIX
  save->lr = save->iar + 4; // +4 so it looks like a return address
  save->iar = dumpStack;
#endif
  SET_GPR(save, Constants_FIRST_VOLATILE_GPR,
          GET_GPR(save, Constants_FRAME_POINTER));
}
