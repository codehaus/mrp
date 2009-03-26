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

/* Macros to modify signal context */
#ifdef RVM_FOR_LINUX
#define __USE_GNU 1
#include <sys/ucontext.h>
#define __MC(context) ((ucontext_t*)context)->uc_mcontext
#define __GREGS(context) (__MC(context).gregs)
#ifndef __x86_64__
#  define IA32_EAX(context) (__GREGS(context)[REG_EAX])
#  define IA32_EBX(context) (__GREGS(context)[REG_EBX])
#  define IA32_ECX(context) (__GREGS(context)[REG_ECX])
#  define IA32_EDX(context) (__GREGS(context)[REG_EDX])
#  define IA32_EDI(context) (__GREGS(context)[REG_EDI])
#  define IA32_ESI(context) (__GREGS(context)[REG_ESI])
#  define IA32_EBP(context) (__GREGS(context)[REG_EBP])
#  define IA32_ESP(context) (__GREGS(context)[REG_ESP])
#  define IA32_EIP(context) (__GREGS(context)[REG_EIP])
#  define IA32_CS(context)  (__GREGS(context)[REG_CS])
#  define IA32_DS(context)  (__GREGS(context)[REG_DS])
#  define IA32_ES(context)  (__GREGS(context)[REG_ES])
#  define IA32_FS(context)  (__GREGS(context)[REG_FS])
#  define IA32_GS(context)  (__GREGS(context)[REG_GS])
#  define IA32_SS(context)  (__GREGS(context)[REG_SS])
#  define IA32_OLDMASK(context) (__MC(context).oldmask)
#  define IA32_FPFAULTDATA(context)     (__MC(context).cr2)
#else
#  define IA32_EAX(context) (__GREGS(context)[REG_RAX])
#  define IA32_EBX(context) (__GREGS(context)[REG_RBX])
#  define IA32_ECX(context) (__GREGS(context)[REG_RCX])
#  define IA32_EDX(context) (__GREGS(context)[REG_RDX])
#  define IA32_EDI(context) (__GREGS(context)[REG_RDI])
#  define IA32_ESI(context) (__GREGS(context)[REG_RSI])
#  define IA32_EBP(context) (__GREGS(context)[REG_RBP])
#  define IA32_ESP(context) (__GREGS(context)[REG_RSP])
#  define IA32_R8(context)  (__GREGS(context)[REG_R8])
#  define IA32_R9(context)  (__GREGS(context)[REG_R9])
#  define IA32_R10(context) (__GREGS(context)[REG_R10])
#  define IA32_R11(context) (__GREGS(context)[REG_R11])
#  define IA32_R12(context) (__GREGS(context)[REG_R12])
#  define IA32_R13(context) (__GREGS(context)[REG_R13])
#  define IA32_R14(context) (__GREGS(context)[REG_R14])
#  define IA32_R15(context) (__GREGS(context)[REG_R15])
#  define IA32_EIP(context) (__GREGS(context)[REG_RIP])
#endif // __x86_64__
#define IA32_EFLAGS(context)  (__GREGS(context)[REG_EFL])
#define IA32_TRAPNO(context) (__GREGS(context)[REG_TRAPNO])
#define IA32_ERR(context) (__GREGS(context)[REG_ERR])
#define IA32_FALUTVADDR(context) (__GREGS(context)[REG_CS])
#define IA32_FPREGS(context) (__MC(context).fpregs)
#endif // RVM_FOR_LINUX

#ifdef RVM_FOR_OSX
#ifdef __DARWIN_UNIX03 
#define DARWIN_PREFIX(x) __##x
#else
#define DARWIN_PREFIX(x) ##x
#endif // RVM_FOR_OSX
#define __MCSS(context) ((ucontext_t*)context)->uc_mcontext->DARWIN_PREFIX(ss)
#define __MCES(context) ((ucontext_t*)context)->uc_mcontext->DARWIN_PREFIX(es)
#define __MCFS(context) ((ucontext_t*)context)->uc_mcontext->DARWIN_PREFIX(fs)
#define IA32_EAX(context)    (__MCSS(context).DARWIN_PREFIX(eax))
#define IA32_EBX(context)    (__MCSS(context).DARWIN_PREFIX(ebx))
#define IA32_ECX(context)    (__MCSS(context).DARWIN_PREFIX(ecx))
#define IA32_EDX(context)    (__MCSS(context).DARWIN_PREFIX(edx))
#define IA32_EDI(context)    (__MCSS(context).DARWIN_PREFIX(edi))
#define IA32_ESI(context)    (__MCSS(context).DARWIN_PREFIX(esi))
#define IA32_EBP(context)    (__MCSS(context).DARWIN_PREFIX(ebp))
#define IA32_ESP(context)    (__MCSS(context).DARWIN_PREFIX(esp))
#define IA32_SS(context)     (__MCSS(context).DARWIN_PREFIX(ss))
#define IA32_EFLAGS(context) (__MCSS(context).DARWIN_PREFIX(eflags))
#define IA32_EIP(context)    (__MCSS(context).DARWIN_PREFIX(eip))
#define IA32_CS(context)     (__MCSS(context).DARWIN_PREFIX(cs))
#define IA32_DS(context)     (__MCSS(context).DARWIN_PREFIX(ds))
#define IA32_ES(context)     (__MCSS(context).DARWIN_PREFIX(es))
#define IA32_FS(context)     (__MCSS(context).DARWIN_PREFIX(fs))
#define IA32_GS(context)     (__MCSS(context).DARWIN_PREFIX(gs))
#define IA32_TRAPNO(context) (__MCES(context).DARWIN_PREFIX(trapno))
#define IA32_ERR(context)    (__MCES(context).DARWIN_PREFIX(err))
#endif // RVM_FOR_OSX

#ifdef RVM_FOR_SOLARIS
#define __MC(context)         ((ucontext_t*)context)->uc_mcontext
#define __GREGS(context)      (__MC(context).gregs)
#define IA32_EAX(context)     (__GREGS(context)[EAX])
#define IA32_EBX(context)     (__GREGS(context)[EBX])
#define IA32_ECX(context)     (__GREGS(context)[ECX])
#define IA32_EDX(context)     (__GREGS(context)[EDX])
#define IA32_EDI(context)     (__GREGS(context)[EDI])
#define IA32_ESI(context)     (__GREGS(context)[ESI])
#define IA32_EBP(context)     (__GREGS(context)[EBP])
#define IA32_ESP(context)     (__GREGS(context)[ESP])
#define IA32_SS(context)      (__GREGS(context)[SS])
#define IA32_EFLAGS(context)  (__GREGS(context)[EFL])
#define IA32_EIP(context)     (__GREGS(context)[EIP])
#define IA32_CS(context)      (__GREGS(context)[CS])
#define IA32_DS(context)      (__GREGS(context)[DS])
#define IA32_ES(context)      (__GREGS(context)[ES])
#define IA32_FS(context)      (__GREGS(context)[FS])
#define IA32_GS(context)      (__GREGS(context)[GS])
#define IA32_TRAPNO(context)  (__GREGS(context)[TRAPNO])
#define IA32_ERR(context)     (__GREGS(context)[ERR])
#define IA32_FPREGS(context)  (__MC(context).fpregs)
#endif // RVM_FOR_SOLARIS

/**
 * Compute the number of bytes used to encode the given modrm part of
 * an Intel instruction
 *
 * @param modrm [in] value to decode
 * @return number of bytes used to encode modrm and optionally an SIB
 * byte and displacement
 */
static int decodeModRMLength(unsigned char modrm)
{
  switch ((modrm >> 6) & 3) {
  case 0: // reg, [reg]
    switch (modrm & 7) {
    case 4: // SIB byte
      return 2;
    case 5: // disp32
      return 5;
    default:
      return 1;
    }
  case 1: // reg, [reg+disp8]
    switch (modrm & 7) {
    case 4: // SIB byte
      return 3;
    default:
      return 2;
    }
  case 2: // reg, [reg+disp32]
    switch (modrm & 7) {
    case 4: // SIB byte
      return 6;
    default:
      return 5;
    }
  case 3: // reg, reg
    return 1;
  }
}

/**
 * So stack maps can treat faults as call-return we must be able to
 * determine the address of the next instruction. This isn't easy on
 * Intel with variable length instructions.
 */
static Address getInstructionFollowing(Address faultingInstructionAddress) {
  SYS_START();
  unsigned char opcode = *((char*)faultingInstructionAddress);
  unsigned char modrm;
  switch (opcode) {
  case 0xCD: // int imm8
    return faultingInstructionAddress+2;
  case 0x39: // cmp r/m,r
  case 0x8B: // mov r,r/m
  case 0xF7: // idiv r/m
  case 0xFF: // push r/m
    modrm = *((unsigned char*)faultingInstructionAddress+1);
    return faultingInstructionAddress+decodeModRMLength(modrm)+1;
  default:
    ERROR_PRINTF(Me, "%s: Unexpected opcode 0x%x treating as opcode followed by modrm\n", Me, opcode);
    modrm = *((unsigned char*)faultingInstructionAddress+1);
    return faultingInstructionAddress+decodeModRMLength(modrm)+1;
  }
}

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
  Address ip = IA32_EIP(context);
  *instructionPtr  = ip;
  *instructionFollowingPtr = getInstructionFollowing(ip);
  *threadPtr = IA32_ESI(context);
  *jtocPtr = bootRecord->tocRegister;
}

/**
 * Read frame pointer at point of the signal
 *
 * @param context [in] context to read from
 * @param threadPtr [in] address of thread information
 * @return address of stack frame
 */
EXTERNAL Address readContextFramePointer(void UNUSED *context, Address threadPtr)
{
  return *(Address *)(threadPtr + Thread_framePointer_offset);
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
  SYS_START();
  switch(signo) {
  case SIGSEGV:
    if (*((unsigned char *)instructionPtr) == 0xCD) {
      /* int imm opcode */
      unsigned char code = *(unsigned char*)(instructionPtr+1);
      switch(code) {
      case Constants_RVM_TRAP_BASE + Runtime_TRAP_NULL_POINTER:
        return Runtime_TRAP_NULL_POINTER;
      case Constants_RVM_TRAP_BASE + Runtime_TRAP_ARRAY_BOUNDS:
        *trapInfo = *(int *) (threadPtr + Thread_arrayIndexTrapParam_offset);
        return Runtime_TRAP_ARRAY_BOUNDS;
      case Constants_RVM_TRAP_BASE + Runtime_TRAP_DIVIDE_BY_ZERO:
        return Runtime_TRAP_DIVIDE_BY_ZERO;
      case Constants_RVM_TRAP_BASE + Runtime_TRAP_STACK_OVERFLOW:
        return Runtime_TRAP_STACK_OVERFLOW;
      case Constants_RVM_TRAP_BASE + Runtime_TRAP_CHECKCAST:
        return Runtime_TRAP_CHECKCAST;
      case Constants_RVM_TRAP_BASE + Runtime_TRAP_REGENERATE:
        return Runtime_TRAP_REGENERATE;
      case Constants_RVM_TRAP_BASE + Runtime_TRAP_JNI_STACK:
        return Runtime_TRAP_JNI_STACK;
      case Constants_RVM_TRAP_BASE + Runtime_TRAP_MUST_IMPLEMENT:
        return Runtime_TRAP_MUST_IMPLEMENT;
      case Constants_RVM_TRAP_BASE + Runtime_TRAP_STORE_CHECK:
        return Runtime_TRAP_STORE_CHECK;
      default:
        ERROR_PRINTF(Me, "%s: Unexpected trap code in int imm instruction 0x%x\n", Me, code);
        return Runtime_TRAP_UNKNOWN;
      }
    } else {
      return Runtime_TRAP_NULL_POINTER;
    }
  case SIGFPE:
    return Runtime_TRAP_DIVIDE_BY_ZERO;
  default:
    ERROR_PRINTF(Me, "%s: Unexpected hardware trap signal 0x%x\n", Me, signo);
    return Runtime_TRAP_UNKNOWN;
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
  SYS_START();
  Address sp, stackLimit, fp;
  Address *vmr_gprs  = *(Address **) (vmRegisters + Registers_gprs_offset);
  Address vmr_ip     =  (Address)    (vmRegisters + Registers_ip_offset);
  Address vmr_fp     =  (Address)    (vmRegisters + Registers_fp_offset);

  /* move gp registers to Registers object */
  vmr_gprs[Constants_EAX] = IA32_EAX(context);
  vmr_gprs[Constants_ECX] = IA32_ECX(context);
  vmr_gprs[Constants_EDX] = IA32_EDX(context);
  vmr_gprs[Constants_EBX] = IA32_EBX(context);
  vmr_gprs[Constants_ESI] = IA32_ESI(context);
  vmr_gprs[Constants_EDI] = IA32_EDI(context);
  vmr_gprs[Constants_ESP] = IA32_ESP(context);
  vmr_gprs[Constants_EBP] = IA32_EBP(context);
#ifdef __x86_64__
  vmr_gprs[Constants_R8]  = IA32_R8(context);
  vmr_gprs[Constants_R8]  = IA32_R9(context);
  vmr_gprs[Constants_R10] = IA32_R10(context);
  vmr_gprs[Constants_R11] = IA32_R11(context);
  vmr_gprs[Constants_R12] = IA32_R12(context);
  vmr_gprs[Constants_R13] = IA32_R13(context);
  vmr_gprs[Constants_R14] = IA32_R14(context);
  vmr_gprs[Constants_R15] = IA32_R15(context);
#endif //__x86_64__

  /*
   * Advance ESP to the guard region of the stack.
   * Enables opt compiler to have ESP point to somewhere
   * other than the bottom of the frame at a PEI (see bug 2570).
   *
   * We'll execute the entire code sequence for
   * Runtime.deliverHardwareException et al. in the guard region of the
   * stack to avoid bashing stuff in the bottom opt-frame.
   */
  sp = IA32_ESP(context);
  stackLimit = *(Address *)(threadPtr + RVMThread_stackLimit_offset);
  if (sp <= stackLimit - 384) {
    ERROR_PRINTF("sp (%p)too far below stackLimit (%p)to recover\n", sp, stackLimit);
    signal(signo, SIG_DFL);
    raise(signo);
    // We should never get here.
    sysExit(EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
  }
  sp = stackLimit - 384;
  stackLimit -= Constants_STACK_SIZE_GUARD;
  *(Address *)(threadPtr + RVMThread_stackLimit_offset) = stackLimit;

  /* Insert artificial stackframe at site of trap. */
  /* This frame marks the place where "hardware exception registers" were saved. */
  sp = sp - Constants_STACKFRAME_HEADER_SIZE;
  fp = sp - __SIZEOF_POINTER__ - Constants_STACKFRAME_BODY_OFFSET;
  /* fill in artificial stack frame */
  ((Address*)(fp + Constants_STACKFRAME_FRAME_POINTER_OFFSET))[0]  = framePtr;
  ((int *)   (fp + Constants_STACKFRAME_METHOD_ID_OFFSET))[0]      = bootRecord->hardwareTrapMethodId;
  ((Address*)(fp + Constants_STACKFRAME_RETURN_ADDRESS_OFFSET))[0] = instructionFollowingPtr;

  /* fill in call to "deliverHardwareException" */
  sp = sp - __SIZEOF_POINTER__; /* first parameter is trap code */
  ((int *)sp)[0] = trapCode;
  IA32_EAX(context) = trapCode;
  TRACE_PRINTF("%s: trap code is %d\n", Me, trapCode);

  sp = sp - __SIZEOF_POINTER__; /* next parameter is trap info */
  ((int *)sp)[0] = trapInfo;
  IA32_EDX(context) = trapInfo;
  TRACE_PRINTF("%s: trap info is %d\n", Me, trapInfo);

  sp = sp - __SIZEOF_POINTER__; /* return address - looks like called from failing instruction */
  *(Address *) sp = instructionFollowingPtr;

  /* store instructionFollowing and fp in Registers.ip and Registers.fp */
  *(Address*)vmr_ip = instructionFollowingPtr;
  TRACE_PRINTF("%s: set vmr_ip to %p\n", Me, instructionFollowingPtr);
  *(Address*)vmr_fp = framePtr;
  TRACE_PRINTF("%s: set vmr_fp to %p\n", Me, framePtr);

  /* set up context block to look like the artificial stack frame is
   * returning
   */
  IA32_ESP(context) = sp;
  IA32_EBP(context) = fp;
  *(Address*) (threadPtr + Thread_framePointer_offset) = fp;

  /* setup to return to deliver hardware exception routine */
  IA32_EIP(context) = *(Address*)(jtocPtr + bootRecord->deliverHardwareExceptionOffset);
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
  Address dumpStack = *(Address *) ((char *) localJTOC + DumpStackAndDieOffset);

  /* get the frame pointer from thread object  */
  Address localNativeThreadAddress = IA32_ESI(context);
  Address localFrameAddress =  *(Address*)(localNativeThreadAddress + Thread_framePointer_offset);

  /* setup stack frame to contain the frame pointer */
  Address *sp = (Address*) IA32_ESP(context);

  /* put fp as a  parameter on the stack  */
  sp -= __SIZEOF_POINTER__;
  *sp = localFrameAddress;

  /* must pass localFrameAddress in first param register! */
  IA32_EAX(context) = localFrameAddress;

  /* put a return address of zero on the stack */
  sp -= __SIZEOF_POINTER__;
  *sp = 0;

  IA32_ESP(context) = sp;

  /* goto dumpStackAndDie routine (in Scheduler) as if called */
  IA32_EIP(context) = dumpStack;
}

/**
 * Print the contents of context to the screen
 *
 * @param context [in] registers at point of signal/trap
 */
EXTERNAL void dumpContext(void *context)
{
  SYS_START();
  ERROR_PRINTF("eip           %p\n", IA32_EIP(context));
  ERROR_PRINTF("eax (T0)      %p\n", IA32_EAX(context));
  ERROR_PRINTF("ebx (ctrs)    %p\n", IA32_EBX(context));
  ERROR_PRINTF("ecx (S0)      %p\n", IA32_ECX(context));
  ERROR_PRINTF("edx (T1)      %p\n", IA32_EDX(context));
  ERROR_PRINTF("esi (TR)      %p\n", IA32_ESI(context));
  ERROR_PRINTF("edi (S1)      %p\n", IA32_EDI(context));
  ERROR_PRINTF("ebp           %p\n", IA32_EBP(context));
  ERROR_PRINTF("esp (SP)      %p\n", IA32_ESP(context));
#ifdef __x86_64__
  ERROR_PRINTF("r8            %p\n", IA32_R8(context));
  ERROR_PRINTF("r9            %p\n", IA32_R9(context));
  ERROR_PRINTF("r10           %p\n", IA32_R10(context));
  ERROR_PRINTF("r11           %p\n", IA32_R11(context));
  ERROR_PRINTF("r12           %p\n", IA32_R12(context));
  ERROR_PRINTF("r13           %p\n", IA32_R13(context));
  ERROR_PRINTF("r14           %p\n", IA32_R14(context));
  ERROR_PRINTF("r15           %p\n", IA32_R15(context));
#else
  ERROR_PRINTF("cs            %p\n", IA32_CS(context));
  ERROR_PRINTF("ds            %p\n", IA32_DS(context));
  ERROR_PRINTF("es            %p\n", IA32_ES(context));
  ERROR_PRINTF("fs            %p\n", IA32_FS(context));
  ERROR_PRINTF("gs            %p\n", IA32_GS(context));
  ERROR_PRINTF("ss            %p\n", IA32_SS(context));
#endif
  ERROR_PRINTF("trapno        0x%08x\n", IA32_TRAPNO(context));
  ERROR_PRINTF("err           0x%08x\n", IA32_ERR(context));
  ERROR_PRINTF("eflags        0x%08x\n", IA32_EFLAGS(context));
  /* null if fp registers haven't been used yet */
  ERROR_PRINTF("fpregs        %p\n", IA32_FPREGS(context));
#ifndef __x86_64__
  ERROR_PRINTF("oldmask       0x%08lx\n", (unsigned long) IA32_OLDMASK(context));
  /* seems to contain mem address that faulting instruction was trying to access */
  ERROR_PRINTF("cr2           0x%08lx\n", (unsigned long) IA32_FPFAULTDATA(context));
#endif
}
