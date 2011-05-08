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

/*
 * Architecture specific signal handling routines
 */
#include "sys.h"
#include <signal.h>
#include <sys/ucontext.h>

/* Macros to modify signal context */
#ifdef RVM_FOR_OSX
#define MAKE_INFO(info, context)                                        \
  struct mcontext *info = ((struct ucontext *)context)->uc_mcontext
#define MAKE_SAVE(save, info)                                           \
   ppc_thread_state_t *save = &info->ss;
#define GET_GPR(save, r)        ((unsigned int *)&save->r0)[(r)]
#define SET_GPR(save, r, value) ((unsigned int *)&save->r0)[(r)] = (value)
#define PPC_IAR(save)           save->srr0
#define PPC_LR(save)            save->lr
#define PPC_FP(save)            save->r1
#else
#define MAKE_INFO(info, context)                                        \
  struct ucontext *info = ((struct ucontext *)context)
#define MAKE_SAVE(save, info)                                           \
  struct pt_regs *save = info->uc_mcontext.regs
#define GET_GPR(save, r)             ((save)->gpr[(r)])
#define SET_GPR(save, r, value)     (((save)->gpr[(r)]) = (value))
#define PPC_IAR(save)                  save->nip
#define PPC_LR(save)                   save->link
#define PPC_FP(save)                   save->gpr[1]
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
EXTERNAL int readContextTrapCode(void UNUSED *context, Address threadPtr, int signo, Address instructionPtr, Word *trapInfo)
{
  SYS_START();
  int instruction;

  switch(signo) {
  case SIGSEGV:
    return Runtime_TRAP_NULL_POINTER;
  case SIGTRAP:
    instruction = *((int*)instructionPtr);
    if ((instruction & Constants_ARRAY_INDEX_MASK) == Constants_ARRAY_INDEX_TRAP) {
      MAKE_INFO(info, context);  
      MAKE_SAVE(save, info);
      *trapInfo = GET_GPR(save,
                          (instruction & Constants_ARRAY_INDEX_REG_MASK)
                          >> Constants_ARRAY_INDEX_REG_SHIFT);
      return Runtime_TRAP_ARRAY_BOUNDS;
    }
    if ((instruction & Constants_CONSTANT_ARRAY_INDEX_MASK) == Constants_CONSTANT_ARRAY_INDEX_TRAP) {
      *trapInfo = ((instruction & Constants_CONSTANT_ARRAY_INDEX_INFO)<<16)>>16;
      return Runtime_TRAP_ARRAY_BOUNDS;
    }
    if ((instruction & Constants_DIVIDE_BY_ZERO_MASK) == Constants_DIVIDE_BY_ZERO_TRAP) {
      return Runtime_TRAP_DIVIDE_BY_ZERO;
    }
    if ((instruction & Constants_MUST_IMPLEMENT_MASK) == Constants_MUST_IMPLEMENT_TRAP)  {
      return Runtime_TRAP_MUST_IMPLEMENT;
    }
    if ((instruction & Constants_STORE_CHECK_MASK) == Constants_STORE_CHECK_TRAP) {
      return Runtime_TRAP_STORE_CHECK;
    }
    if ((instruction & Constants_CHECKCAST_MASK ) == Constants_CHECKCAST_TRAP) {
      return Runtime_TRAP_CHECKCAST;
    }
    if ((instruction & Constants_REGENERATE_MASK) == Constants_REGENERATE_TRAP) {
      return Runtime_TRAP_REGENERATE;
    }
    if ((instruction & Constants_NULLCHECK_MASK) == Constants_NULLCHECK_TRAP) {
      return Runtime_TRAP_NULL_POINTER;
    }
    if ((instruction & Constants_JNI_STACK_TRAP_MASK) == Constants_JNI_STACK_TRAP) {
      return Runtime_TRAP_JNI_STACK;
    }
    ERROR_PRINTF(Me, "%s: Unexpected hardware trap 0x%x from instruction 0x%0x\n", Me, signo, instruction);
    return Runtime_TRAP_UNKNOWN;    
  case SIGFPE:
    return Runtime_TRAP_DIVIDE_BY_ZERO;
  default:
    ERROR_PRINTF(Me, "%s: Unexpected hardware trap signal 0x%x\n", Me, signo);
    return Runtime_TRAP_UNKNOWN;
  }
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
  PPC_LR(save) = PPC_IAR(save)+4; // +4 so it looks like a return address
  PPC_IAR(save) = dumpStack;
  SET_GPR(save, Constants_FIRST_VOLATILE_GPR, GET_GPR(save, Constants_FRAME_POINTER));
}

/**
 * Print the contents of context to the screen
 *
 * @param context [in] registers at point of signal/trap
 */
EXTERNAL void dumpContext(void *context)
{
  int i;
  SYS_START();
  MAKE_INFO(info, context);  
  MAKE_SAVE(save, info);
  ERROR_PRINTF("             fp=%p\n", GET_GPR(save, PPC_FP(save)));
  ERROR_PRINTF("             tr=%p\n", GET_GPR(save, Constants_THREAD_REGISTER));
  ERROR_PRINTF("             ip=%p\n", PPC_IAR(save));
  ERROR_PRINTF("          instr=0x%08x\n", *((int*)PPC_IAR(save)));
  ERROR_PRINTF("             lr=%p\n",  PPC_LR(save));
  for (i=0; i<32; i++) {
    ERROR_PRINTF("            r%02d=%p\n", i, GET_GPR(save, i));
  }
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
  setupDumpStackAndDie(context);
}
