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
#include <signal.h>
#include <sys/ucontext.h>

/* Macros to modify signal context */
#ifdef RVM_FOR_OSX
#define MAKE_INFO(info, context)                                        \
  struct mcontext* info = ((struct ucontext *)context)->uc_mcontext
#define MAKE_SAVE(save, info)                                           \
   ppc_thread_state_t *save = &info->ss;
#define GET_GPR(save, r)        ((unsigned int *)&save->r0)[(r)]
#define SET_GPR(save, r, value) ((unsigned int *)&save->r0)[(r)] = (value)
#define PPC_IAR(save)           save->srr0
#else
#define GET_GPR(save, r)             ((save)->gpr[(r)])
#define SET_GPR(save, r, value)     (((save)->gpr[(r)]) = (value))
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
  MAKE_SAVE(save, info);
  Address ip = PPC_IAR(save);
  *instructionPtr  = ip;
  *instructionFollowingPtr = ip+4;
  *threadPtr = GET_GPR(save, Constants_FRAME_POINTER);
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
  MAKE_SAVE(save, info);
  return GET_GPR(save, Constants_FRAME_POINTER);
}

/**
 * Read trap code from context of signal
 *
 * @param context   [in] context to read from
 * @param threadPtr [in] address of thread information
 * @param signo     [in] signal number
 * @param instructionPtr [in] address of instruction
 * @param trapInfo  [out] extra information about trap
 * @return trap code
 */
EXTERNAL int readContextTrapCode(void UNUSED *context, Address threadPtr, int signo, Address instructionPtr, int *trapInfo)
{
  return 0;
}

/**
 * Set up the context to invoke RVMThread.dumpStackAndDie
 *
 * @param context [in,out] registers at point of signal/trap
 */
EXTERNAL void setupDumpStackAndDie(void *context)
{    
  MAKE_INFO(info, context);  
  MAKE_SAVE(save, info);
  Offset DumpStackAndDieOffset = bootRecord->dumpStackAndDieOffset;  
  Address localJTOC = bootRecord->tocRegister;
  Address dumpStack = *(Address *)((char *)localJTOC + DumpStackAndDieOffset);
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

/**
 * Print the contents of context to the screen
 *
 * @param context [in] registers at point of signal/trap
 */
EXTERNAL void dumpContext(void *context)
{
}

/**
 * Set up the context to invoke RuntimeEntrypoints.deliverHardwareException.
 *
 * @param context  [in,out] registers at point of signal/trap
 * @param vmRegisters [out]
 */
EXTERNAL void setupDeliverHardwareException(void *context, Address vmRegisters,
					    int trapCode, int trapInfo,
					    Address instructionPtr,
					    Address instructionFollowingPtr,
					    Address threadPtr, Address jtocPtr,
					    Address framePtr, int signo)
{
}
