/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
/**
 *  Generate inline machine instructions for special methods that cannot be 
 *  implemented in java bytecodes. These instructions are generated whenever  
 *  we encounter an "invokestatic" bytecode that calls a method with a 
 *  signature of the form "static native VM_Magic.xxx(...)".
 *  23 Jan 1998 Derek Lieber
 * 
 *  NOTE: when adding a new "methodName" to "generate()", be sure to also 
 * consider how it affects the values on the stack and update 
 * "checkForActualCall()" accordingly.
 * If no call is actually generated, the map will reflect the status of the 
 * locals (including parameters) at the time of the call but nothing on the 
 * operand stack for the call site will be mapped.
 *  7 Jul 1998 Janice Shepherd
 *
 * @author Derek Lieber
 * @author Janice Sheperd
 */
class VM_MagicCompiler implements VM_BaselineConstants, 
				  VM_AssemblerConstants {

  // These constants do not really belong here, but since I am making this change
  // I might as well make it a little better.  All size in bytes.
  static final int SIZE_IP = 4;
  static final int SIZE_TOC = 4;
  static final int SIZE_ADDRESS = 4;
  static final int SIZE_INTEGER = 4;

  static final int DEBUG = 0;

  //-----------//
  // interface //
  //-----------//
   
  // Generate inline code sequence for specified method.
  // Taken:    compiler we're generating code with
  //           method whose name indicates semantics of code to be generated
  // Returned: true if there was magic defined for the method
  //
  static boolean  generateInlineCode(VM_Compiler compiler, VM_MethodReference methodToBeCalled) {
    VM_Atom      methodName       = methodToBeCalled.getName();
    VM_Assembler asm              = compiler.asm;
    int          spSaveAreaOffset = compiler.spSaveAreaOffset;
      
    if (methodName == VM_MagicNames.sysCall0) {
      generateSysCall1(asm, 0, false );
      generateSysCall2(asm, 0);
      generateSysCallRet_I(asm, 0);
    } else if (methodName == VM_MagicNames.sysCall1) {
      int valueOffset = generateSysCall1(asm, SIZE_INTEGER, false );
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3, valueOffset,  SP);          // load value
      generateSysCall2(asm, SIZE_INTEGER);
      generateSysCallRet_I(asm, SIZE_INTEGER);
    } else if (methodName == VM_MagicNames.sysCall2) {
      int valueOffset = generateSysCall1(asm, 2 * SIZE_INTEGER, false );
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3, valueOffset,  SP);		 		 // load value1
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3 + 1, valueOffset,  SP);		 		 // load value2
      generateSysCall2(asm, 2 * SIZE_INTEGER);
      generateSysCallRet_I(asm, 2 * SIZE_INTEGER);
    } else if (methodName == VM_MagicNames.sysCall3) {
      int valueOffset = generateSysCall1(asm, 3 * SIZE_INTEGER, false );
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3, valueOffset,  SP);		 		 // load value1
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3 + 1, valueOffset,  SP);		 		 // load value2
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3 + 2, valueOffset,  SP);		 		 // load value3
      generateSysCall2(asm, 3 * SIZE_INTEGER);
      generateSysCallRet_I(asm, 3 * SIZE_INTEGER);
    } else if (methodName == VM_MagicNames.sysCall4) {
      int valueOffset = generateSysCall1(asm, 4 * SIZE_INTEGER, false );
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3, valueOffset,  SP);		 		 // load value1
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3 + 1, valueOffset,  SP);		 		 // load value2
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3 + 2, valueOffset,  SP);		 		 // load value3
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3 + 3, valueOffset,  SP);		 		 // load value4
      generateSysCall2(asm, 4 * SIZE_INTEGER);
      generateSysCallRet_I(asm, 4 * SIZE_INTEGER);
    } else if (methodName == VM_MagicNames.sysCall_L_0) {
      generateSysCall1(asm, 0, false );
      generateSysCall2(asm, 0);
      generateSysCallRet_L(asm, 0);
    } else if (methodName == VM_MagicNames.sysCall_L_I) {
      int valueOffset = generateSysCall1(asm, SIZE_INTEGER, false );
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3, valueOffset,  SP);          // load value
      generateSysCall2(asm, SIZE_INTEGER);
      generateSysCallRet_L(asm, SIZE_INTEGER);
    } else if (methodName == VM_MagicNames.sysCallAD) {
      int valueOffset = generateSysCall1(asm, 3 * SIZE_INTEGER, false );
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3, valueOffset,  SP);		 		 // load value1
      valueOffset -= SIZE_INTEGER;
      asm.emitLFD(0, valueOffset,  SP);		 		 // load value2
      generateSysCall2(asm, 3 * SIZE_INTEGER);
      generateSysCallRet_I(asm, 3 * SIZE_INTEGER);
    } else if (methodName == VM_MagicNames.sysCallSigWait) {
      int   ipOffset = VM_Entrypoints.registersIPField.getOffset();
      int gprsOffset = VM_Entrypoints.registersGPRsField.getOffset();
      asm.emitLWZ  (T0, 0, SP);	// t0 := address of VM_Registers object
      asm.emitADDI (SP, 4, SP);	// pop address of VM_Registers object
      VM_ForwardReference fr1 = asm.emitForwardBL();
      fr1.resolve(asm);
      asm.emitMFLR(0);
      asm.emitSTW (0, ipOffset, T0 ); // store ip into VM_Registers Object
      asm.emitLWZ  (T0, gprsOffset, T0); // TO <- registers.gprs[]
      asm.emitSTW (FP, FP*4, T0);  
      int valueOffset = generateSysCall1(asm, 2 * SIZE_INTEGER, true );
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3, valueOffset,  SP);		 		 // load value1
      valueOffset -= SIZE_INTEGER;
      asm.emitLWZ(3 + 1, valueOffset,  SP);		 		 // load value2
      generateSysCall2(asm, 2 * SIZE_INTEGER);
      generateSysCallRet_I(asm, 2 * SIZE_INTEGER);
    } else if (methodName == VM_MagicNames.getFramePointer) {
      asm.emitSTWU(FP, -4, SP); // push FP
    } else if (methodName == VM_MagicNames.getCallerFramePointer) {
      asm.emitLWZ(T0, 0, SP);                               // pop  frame pointer of callee frame
      asm.emitLWZ(T1, STACKFRAME_FRAME_POINTER_OFFSET, T0); // load frame pointer of caller frame
      asm.emitSTW(T1, 0, SP);                               // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.setCallerFramePointer) {
      asm.emitLWZ (T0, +4, SP); // fp
      asm.emitLWZ (T1,  0, SP); // value
      asm.emitSTW(T1,  STACKFRAME_FRAME_POINTER_OFFSET, T0); // *(address+SFPO) := value
      asm.emitADDI(SP,  8, SP); // pop address, pop value
    } else if (methodName == VM_MagicNames.getCompiledMethodID) {
      asm.emitLWZ(T0, 0, SP);                           // pop  frame pointer of callee frame
      asm.emitLWZ(T1, STACKFRAME_METHOD_ID_OFFSET, T0); // load frame pointer of caller frame
      asm.emitSTW(T1, 0, SP);                           // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.setCompiledMethodID) {
      asm.emitLWZ (T0, +4, SP); // fp
      asm.emitLWZ (T1,  0, SP); // value
      asm.emitSTW(T1,  STACKFRAME_METHOD_ID_OFFSET, T0); // *(address+SNIO) := value
      asm.emitADDI(SP,  8, SP); // pop address, pop value
    } else if (methodName == VM_MagicNames.getNextInstructionAddress) {
      asm.emitLWZ(T0, 0, SP);                                  // pop  frame pointer of callee frame
      asm.emitLWZ(T1, STACKFRAME_NEXT_INSTRUCTION_OFFSET, T0); // load frame pointer of caller frame
      asm.emitSTW(T1, 0, SP);                                  // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.setNextInstructionAddress) {
      asm.emitLWZ (T0, +4, SP); // fp
      asm.emitLWZ (T1,  0, SP); // value
      asm.emitSTW(T1,  STACKFRAME_NEXT_INSTRUCTION_OFFSET, T0); // *(address+SNIO) := value
      asm.emitADDI(SP,  8, SP); // pop address, pop value
    } else if (methodName == VM_MagicNames.getReturnAddressLocation) {
      asm.emitLWZ  (T0, 0, SP);                                  // pop  frame pointer of callee frame
      asm.emitLWZ  (T1, STACKFRAME_FRAME_POINTER_OFFSET, T0);    // load frame pointer of caller frame
      asm.emitADDI (T2, STACKFRAME_NEXT_INSTRUCTION_OFFSET, T1); // get location containing ret addr
      asm.emitSTW (T2, 0, SP);                                  // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.getTocPointer ||
	       methodName == VM_MagicNames.getJTOC) {
      asm.emitSTWU(JTOC, -4, SP); // push JTOC
    } else if (methodName == VM_MagicNames.getThreadId) {
      asm.emitSTWU(TI, -4, SP); // push TI
    } else if (methodName == VM_MagicNames.setThreadId) {
      asm.emitLWZ (TI, 0, SP); // TI := (shifted) thread index
      asm.emitADDI(SP, 4, SP); // pop threadid arg
    } else if (methodName == VM_MagicNames.getProcessorRegister) {
      asm.emitSTWU(PROCESSOR_REGISTER, -4, SP);
    } else if (methodName == VM_MagicNames.setProcessorRegister) {
      asm.emitLWZ (PROCESSOR_REGISTER, 0, SP); // register := arg
      asm.emitADDI(SP, 4, SP);                 // pop arg
    } else if (methodName == VM_MagicNames.getTimeBase) {
      int label = asm.getMachineCodeIndex();
      asm.emitMFTBU(T0);                      // T0 := time base, upper
      asm.emitMFTB (T1);                      // T1 := time base, lower
      asm.emitMFTBU(T2);                      // T2 := time base, upper
      asm.emitCMP  (T0, T2);                  // T0 == T2?
      asm.emitBC   (NE, label);               // lower rolled over, try again
      asm.emitSTWU  (T1, -4, SP);              // push low
      asm.emitSTWU  (T0, -4, SP);              // push high
    } else if (methodName == VM_MagicNames.getTime) {
      asm.emitLWZ (T0, 0, SP); // t0 := address of VM_Processor object
      asm.emitADDI(SP, 4, SP); // pop arg
      asm.emitLWZtoc(S0, VM_Entrypoints.getTimeInstructionsField.getOffset());
      asm.emitMTCTR(S0);
      asm.emitCall(spSaveAreaOffset);             // call out of line machine code
      asm.emitSTFDU (F0, -8, SP); // push return value
    } else if (methodName == VM_MagicNames.invokeMain) {
      asm.emitLWZ  (T0, 0, SP); // t0 := ip
      asm.emitMTCTR(T0);
      asm.emitADDI (SP, 4, SP); // pop ip
      asm.emitLWZ  (T0, 0, SP); // t0 := parameter
      asm.emitCall(spSaveAreaOffset);          // call
      asm.emitADDI (SP, 4, SP); // pop parameter
    } else if (methodName == VM_MagicNames.invokeClassInitializer) {
      asm.emitLWZ  (T0, 0, SP); // t0 := address to be called
      asm.emitADDI (SP, 4, SP); // pop ip
      asm.emitMTCTR(T0);
      asm.emitCall(spSaveAreaOffset);          // call
    } else if (methodName == VM_MagicNames.invokeMethodReturningVoid) {
      generateMethodInvocation(asm, spSaveAreaOffset); // call method
    } else if (methodName == VM_MagicNames.invokeMethodReturningInt) {
      generateMethodInvocation(asm, spSaveAreaOffset); // call method
      asm.emitSTWU(T0, -4, SP);       // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningLong) {
      generateMethodInvocation(asm, spSaveAreaOffset); // call method
      asm.emitSTWU(T1, -4, SP);       // push result
      asm.emitSTWU(T0, -4, SP);       // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningFloat) {
      generateMethodInvocation(asm, spSaveAreaOffset); // call method
      asm.emitSTFSU(F0, -4, SP);     // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningDouble) {
      generateMethodInvocation(asm, spSaveAreaOffset); // call method
      asm.emitSTFDU(F0, -8, SP);     // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningObject) {
      generateMethodInvocation(asm, spSaveAreaOffset); // call method
      asm.emitSTWU(T0, -4, SP);       // push result
    } else if (methodName == VM_MagicNames.addressArrayCreate) {
      try {
	VM_Array type = methodToBeCalled.getType().resolve().asArray();
	compiler.emit_resolved_newarray(type);
      } catch (ClassNotFoundException e) {
	InternalError ex = new InternalError();
	e.initCause(ex);
	throw ex;
      }
    } else if (methodName == VM_MagicNames.addressArrayLength) {
      compiler.emit_arraylength();
    } else if (methodName == VM_MagicNames.addressArrayGet) {
      if (VM.BuildFor32Addr) {
	compiler.emit_iaload();
      } else if (VM.BuildFor64Addr) {
	compiler.emit_laload();
      } else {
	VM._assert(NOT_REACHED);
      }
    } else if (methodName == VM_MagicNames.addressArraySet) {
      if (VM.BuildFor32Addr) {
        compiler.emit_iastore();  
      } else if (VM.BuildFor64Addr) {
	VM._assert(false);  // not implemented
      } else {
	VM._assert(NOT_REACHED);
      }
    } else if (methodName == VM_MagicNames.getIntAtOffset ||
	       methodName == VM_MagicNames.getObjectAtOffset ||
	       methodName == VM_MagicNames.getObjectArrayAtOffset) {
      asm.emitLWZ (T0, +4, SP); // pop object
      asm.emitLWZ (T1,  0, SP); // pop offset
      asm.emitLWZX (T0, T1, T0); // *(object+offset)
      asm.emitSTWU (T0, 4, SP); // push *(object+offset)
    } else if (methodName == VM_MagicNames.getByteAtOffset) {
      asm.emitLWZ  (T0, +4, SP);   // pop object
      asm.emitLWZ  (T1,  0, SP);   // pop offset
      asm.emitLBZX(T0, T1, T0);   // load byte with zero extension.
      asm.emitSTWU (T0, 4, SP);    // push *(object+offset) 
    } else if (methodName == VM_MagicNames.setIntAtOffset ||
	       methodName == VM_MagicNames.setObjectAtOffset) {
      asm.emitLWZ (T0, +8, SP); // pop object
      asm.emitLWZ (T1, +4, SP); // pop offset
      asm.emitLWZ (T2,  0, SP); // pop newvalue
      asm.emitSTWX(T2, T1, T0); // *(object+offset) = newvalue
      asm.emitADDI(SP, 12, SP); // drop all args
    } else if (methodName == VM_MagicNames.setByteAtOffset) {
      asm.emitLWZ (T0, +8, SP); // pop object
      asm.emitLWZ (T1, +4, SP); // pop offset
      asm.emitLWZ (T2,  0, SP); // pop newvalue
      asm.emitSTBX(T2, T1, T0); // *(object+offset) = newvalue
      asm.emitADDI(SP, 12, SP); // drop all args
    } else if (methodName == VM_MagicNames.getLongAtOffset) {
      asm.emitLWZ (T1, +4, SP); // pop object
      asm.emitLWZ (T2,  0, SP); // pop offset
      asm.emitLWZX (T0, T1, T2); // *(object+offset)
      asm.emitADDI(T2, +4, T2); // offset += 4
      asm.emitLWZX (T1, T1, T2); // *(object+offset+4)
      asm.emitSTW(T0,  0, SP); // *sp := *(object+offset)
      asm.emitSTW(T1, +4, SP); // *sp+4 := *(object+offset+4)
    } else if ((methodName == VM_MagicNames.setLongAtOffset) 
	       || (methodName == VM_MagicNames.setDoubleAtOffset)) {
      asm.emitLWZ (T0,+12, SP); // pop object
      asm.emitLWZ (T1, +8, SP); // pop offset
      asm.emitLWZ (T2,  0, SP); // pop newvalue low 
      asm.emitSTWX(T2, T1, T0); // *(object+offset) = newvalue low
      asm.emitADDI(T1, +4, T1); // offset += 4
      asm.emitLWZ (T2, +4, SP); // pop newvalue high 
      asm.emitSTWX(T2, T1, T0); // *(object+offset) = newvalue high
      asm.emitADDI(SP, 16, SP); // drop all args
    } else if (methodName == VM_MagicNames.getMemoryInt ||
	       methodName == VM_MagicNames.getMemoryWord ||
	       methodName == VM_MagicNames.getMemoryAddress) {
      asm.emitLWZ (T0,  0, SP); // address
      asm.emitLWZ (T0,  0, T0); // *address
      asm.emitSTW(T0,  0, SP); // *sp := *address
    } else if (methodName == VM_MagicNames.setMemoryInt ||
	       methodName == VM_MagicNames.setMemoryWord ||
	       methodName == VM_MagicNames.setMemoryAddress) {
      asm.emitLWZ (T0,  4, SP); // address
      asm.emitLWZ (T1,  0, SP); // value
      asm.emitSTW(T1,  0, T0); // *address := value
      asm.emitADDI(SP,  8, SP); // pop address, pop value
    } else if (methodName == VM_MagicNames.prepareInt ||
	       methodName == VM_MagicNames.prepareObject ||
	       methodName == VM_MagicNames.prepareAddress) {
      asm.emitLWZ   (T0,  4, SP); // pop object
      asm.emitLWZ   (T1,  0, SP); // pop offset
      if (VM.BuildForSingleVirtualProcessor) {
	asm.emitLWZX (T0, T1, T0); // *(object+offset)
      } else {
	asm.emitLWARX(T0,  T1, T0); // *(object+offset), setting processor's reservation address
      }
      asm.emitSTWU (T0,  4, SP); // push *(object+offset)
    } else if (methodName == VM_MagicNames.attemptInt ||
	       methodName == VM_MagicNames.attemptObject ||
	       methodName == VM_MagicNames.attemptAddress) {
      asm.emitLWZ    (T0, 12, SP);  // pop object
      asm.emitLWZ    (T1,  8, SP);  // pop offset
      asm.emitLWZ    (T2,  0, SP);  // pop newValue (ignore oldValue)
      if (VM.BuildForSingleVirtualProcessor) {
	asm.emitSTWX   (T2,  T1, T0); // store new value (on one VP this succeeds by definition)
	asm.emitADDI   (T0,  1, 0);   // T0 := true
	asm.emitSTWU   (T0,  12, SP);  // push success of conditional store
      } else {
	asm.emitSTWCXr(T2,  T1, T0); // store new value and set CR0
	asm.emitADDI   (T0,  0, 0);  // T0 := false
	VM_ForwardReference fr = asm.emitForwardBC(NE); // skip, if store failed
	asm.emitADDI   (T0,  1, 0);   // T0 := true
	fr.resolve(asm);
	asm.emitSTWU   (T0,  12, SP);  // push success of conditional store
      }
    } else if (methodName == VM_MagicNames.saveThreadState) {
      asm.emitLWZ  (T0, 0, SP); // T0 := address of VM_Registers object
      asm.emitLWZtoc(S0, VM_Entrypoints.saveThreadStateInstructionsField.getOffset());
      asm.emitMTCTR(S0);
      asm.emitCall(spSaveAreaOffset); // call out of line machine code
      asm.emitADDI(SP, 4, SP);  // pop arg
    } else if (methodName == VM_MagicNames.threadSwitch) {
      asm.emitLWZ(T0, 4, SP); // T0 := address of previous VM_Thread object
      asm.emitLWZ(T1, 0, SP); // T1 := address of VM_Registers of new thread
      asm.emitLWZtoc(S0, VM_Entrypoints.threadSwitchInstructionsField.getOffset());
      asm.emitMTCTR(S0);
      asm.emitCall(spSaveAreaOffset);
      asm.emitADDI(SP, 8, SP);  // pop two args
    } else if (methodName == VM_MagicNames.restoreHardwareExceptionState) {
      asm.emitLWZ(T0, 0, SP); // T0 := address of VM_Registers object
      asm.emitLWZtoc(S0, VM_Entrypoints.restoreHardwareExceptionStateInstructionsField.getOffset());
      asm.emitMTLR(S0);
      asm.emitBCLR(); // branch to out of line machine code (does not return)
    } else if (methodName == VM_MagicNames.returnToNewStack) {
      asm.emitLWZ  (FP, 0, SP);                                  // FP := new stackframe
      asm.emitLWZ  (S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // fetch...
      asm.emitMTLR(S0);                                         // ...return address
      asm.emitBCLR ();                                           // return to caller
    } else if (methodName == VM_MagicNames.dynamicBridgeTo) {
      if (VM.VerifyAssertions) VM._assert(compiler.klass.isDynamicBridge());
         
      // fetch parameter (address to branch to) into CT register
      //
      asm.emitLWZ(T0, 0, SP);
      asm.emitMTCTR(T0);

      // restore volatile and non-volatile registers
      // (note that these are only saved for "dynamic bridge" methods)
      //
      int offset = compiler.frameSize;

      // restore non-volatile and volatile fprs
      for (int i = LAST_NONVOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
	asm.emitLFD(i, offset -= 8, FP);
      
      // restore non-volatile gprs
      for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_NONVOLATILE_GPR; --i)
	asm.emitLWZ(i, offset -= 4, FP);
            
      // skip saved thread-id, processor, and scratch registers
      offset -= (FIRST_NONVOLATILE_GPR - LAST_VOLATILE_GPR - 1) * 4;
         
      // restore volatile gprs
      for (int i = LAST_VOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
	asm.emitLWZ(i, offset -= 4, FP);
          
      // pop stackframe
      asm.emitLWZ(FP, 0, FP);
         
      // restore link register
      asm.emitLWZ(S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
     asm.emitMTLR(S0);

      asm.emitBCCTR(); // branch always, through count register
    } else if (methodName == VM_MagicNames.objectAsAddress         ||
	       methodName == VM_MagicNames.addressAsByteArray      ||
	       methodName == VM_MagicNames.addressAsIntArray       ||
	       methodName == VM_MagicNames.addressAsObject         ||
	       methodName == VM_MagicNames.addressAsObjectArray    ||
	       methodName == VM_MagicNames.addressAsType           ||
	       methodName == VM_MagicNames.objectAsType            ||
	       methodName == VM_MagicNames.objectAsByteArray       ||
	       methodName == VM_MagicNames.objectAsShortArray      ||
	       methodName == VM_MagicNames.objectAsIntArray        ||
	       methodName == VM_MagicNames.addressAsThread         ||
	       methodName == VM_MagicNames.objectAsThread          ||
	       methodName == VM_MagicNames.objectAsProcessor       ||
	       //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
	       methodName == VM_MagicNames.addressAsBlockControl   ||
	       methodName == VM_MagicNames.addressAsSizeControl    ||
	       methodName == VM_MagicNames.addressAsSizeControlArray   ||
	       //-#endif
	       methodName == VM_MagicNames.threadAsCollectorThread ||
	       methodName == VM_MagicNames.addressAsRegisters      ||
	       methodName == VM_MagicNames.addressAsStack          ||
	       methodName == VM_MagicNames.floatAsIntBits          ||
	       methodName == VM_MagicNames.intBitsAsFloat          ||
	       methodName == VM_MagicNames.doubleAsLongBits        ||
	       methodName == VM_MagicNames.longBitsAsDouble) {
      // no-op (a type change, not a representation change)
    } else if (methodName == VM_MagicNames.getObjectType) {
      generateGetObjectType(asm);
    } else if (methodName == VM_MagicNames.getArrayLength) {
      generateGetArrayLength(asm);
    } else if (methodName == VM_MagicNames.sync) {
      asm.emitSYNC();
    } else if (methodName == VM_MagicNames.isync) {
      asm.emitISYNC();
    } else if (methodName == VM_MagicNames.dcbst) {
      asm.emitLWZ(T0, 0, SP);    // address
      asm.emitADDI(SP, 4, SP);  // pop
      asm.emitDCBST(0, T0);
    } else if (methodName == VM_MagicNames.icbi) {
      asm.emitLWZ(T0, 0, SP);    // address
      asm.emitADDI(SP, 4, SP);  // pop
      asm.emitICBI(0, T0);
    } else if (methodName == VM_MagicNames.wordFromInt ||
	       methodName == VM_MagicNames.wordFromIntZeroExtend ||
	       methodName == VM_MagicNames.wordFromIntSignExtend ||
	       methodName == VM_MagicNames.wordToInt ||
	       methodName == VM_MagicNames.wordToAddress ||
	       methodName == VM_MagicNames.wordToWord) {
      // no-op
    } else if (methodName == VM_MagicNames.wordAdd) {
      // same as an integer add
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.add as integer add");
      asm.emitLWZ (T0,  0, SP);
      asm.emitLWZ (T1,  4, SP);
      asm.emitADD  (T2, T1, T0);
      asm.emitSTWU(T2,  4, SP);
    } else if (methodName == VM_MagicNames.wordSub ||
	       methodName == VM_MagicNames.wordDiff) {
      // same as an integer subtraction
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.sub/diff as integer sub");
      asm.emitLWZ (T0,  0, SP);
      asm.emitLWZ (T1,  4, SP);
      asm.emitSUBFC (T2, T0, T1);
      asm.emitSTWU(T2,  4, SP);
    } else if (methodName == VM_MagicNames.wordLT) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.LT as unsigned comparison");
      generateAddrComparison(asm, LT);
    } else if (methodName == VM_MagicNames.wordLE) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.LE as unsigned comparison");
      generateAddrComparison(asm, LE);
    } else if (methodName == VM_MagicNames.wordEQ) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.EQ as unsigned comparison");
      generateAddrComparison(asm, EQ);
    } else if (methodName == VM_MagicNames.wordNE) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.NE as unsigned comparison");
      generateAddrComparison(asm, NE);
    } else if (methodName == VM_MagicNames.wordGT) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.GT as unsigned comparison");
      generateAddrComparison(asm, GT);
    } else if (methodName == VM_MagicNames.wordGE) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.GE as unsigned comparison");
      generateAddrComparison(asm, GE);
    } else if (methodName == VM_MagicNames.wordIsZero) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.isZero as unsigned comparison");
      asm.emitLI (T0,  0);
      asm.emitSTWU (T0, -4, SP);
      generateAddrComparison(asm, EQ);
    } else if (methodName == VM_MagicNames.wordIsMax) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.isMax as unsigned comparison");
      asm.emitLI (T0, -1);
      asm.emitSTWU (T0, -4, SP);
      generateAddrComparison(asm, EQ);
    } else if (methodName == VM_MagicNames.wordZero) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.zero as 0");
      asm.emitLI (T0,  0);
      asm.emitSTWU (T0, -4, SP);
    } else if (methodName == VM_MagicNames.wordMax) {
      // unsigned comparison generating a boolean
      if (DEBUG >= 1) VM.sysWriteln("VM_MagicCompiler.java: Translating VM_Word/Address.max as -1");
      asm.emitLI (T0, -1);
      asm.emitSTWU (T0, -4, SP);
    } else if (methodName == VM_MagicNames.wordAnd) {
      asm.emitLWZ (T0,  0, SP);
      asm.emitLWZ (T1,  4, SP);
      asm.emitAND(T2, T1, T0);
      asm.emitSTWU(T2,  4, SP);
    } else if (methodName == VM_MagicNames.wordOr) {
      asm.emitLWZ (T0,  0, SP);
      asm.emitLWZ (T1,  4, SP);
      asm.emitOR (T2, T1, T0);
      asm.emitSTWU(T2,  4, SP);
    } else if (methodName == VM_MagicNames.wordNot) {
      asm.emitLWZ (T0,  0, SP);
      asm.emitLI(T1, -1);
      asm.emitXOR(T2, T1, T0);
      asm.emitSTWU(T2,  0, SP);
    } else if (methodName == VM_MagicNames.wordXor) {
      asm.emitLWZ (T0,  0, SP);
      asm.emitLWZ (T1,  4, SP);
      asm.emitXOR(T2, T1, T0);
      asm.emitSTWU(T2,  4, SP);
    } else {
      // VM.sysWrite("VM_MagicCompiler.java: no magic for " + methodToBeCalled + ".  Hopefully it is synthetic magic.\n");
      // if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
      return false;
    }
    return true;
  }

  private static void generateAddrComparison(VM_Assembler asm, int cc) {
    asm.emitLWZ (T1,  0, SP);
    asm.emitLWZ (T0,  4, SP);
    asm.emitLI(T2,  1);
    asm.emitCMPL(T0, T1);    // unsigned comparison
    VM_ForwardReference fr = asm.emitForwardBC(cc);
    asm.emitLI(T2,  0);
    fr.resolve(asm);
    asm.emitSTWU(T2,  4, SP);
  }


  // Indicate if specified VM_Magic method causes a frame to be created on the runtime stack.
  // Taken:   VM_Method of the magic method being called
  // Returned: true if method causes a stackframe to be created
  //
  public static boolean checkForActualCall(VM_MethodReference methodToBeCalled) {
    VM_Atom methodName = methodToBeCalled.getName();
    return methodName == VM_MagicNames.invokeMain                  ||
      methodName == VM_MagicNames.invokeClassInitializer      ||
      methodName == VM_MagicNames.invokeMethodReturningVoid   ||
      methodName == VM_MagicNames.invokeMethodReturningInt    ||
      methodName == VM_MagicNames.invokeMethodReturningLong   ||
      methodName == VM_MagicNames.invokeMethodReturningFloat  ||
      methodName == VM_MagicNames.invokeMethodReturningDouble ||
      methodName == VM_MagicNames.invokeMethodReturningObject;
  }


  //----------------//
  // implementation //
  //----------------//

  // Generate code to invoke arbitrary method with arbitrary parameters/return value.
  //
  // We generate inline code that calls "VM_OutOfLineMachineCode.reflectiveMethodInvokerInstructions"
  // which, at runtime, will create a new stackframe with an appropriately sized spill area
  // (but no register save area, locals, or operand stack), load up the specified
  // fpr's and gpr's, call the specified method, pop the stackframe, and return a value.
  //
  private static void generateMethodInvocation(VM_Assembler asm, 
					       int spSaveAreaOffset) {
    // On entry the stack looks like this:
    //
    //                       hi-mem
    //            +-------------------------+    \
    //            |         code[]          |     |
    //            +-------------------------+     |
    //            |         gprs[]          |     |
    //            +-------------------------+     |- java operand stack
    //            |         fprs[]          |     |
    //            +-------------------------+     |
    //    SP ->   |         spills[]        |     |
    //            +-------------------------+    /

    // fetch parameters and generate call to method invoker
    //
    asm.emitLWZtoc (S0, VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset());
    asm.emitLWZ   (T0, 12, SP);        // t0 := code
    asm.emitMTCTR (S0);
    asm.emitLWZ   (T1,  8, SP);        // t1 := gprs
    asm.emitLWZ   (T2,  4, SP);        // t2 := fprs
    asm.emitLWZ   (T3,  0, SP);        // t3 := spills
    asm.emitCall(spSaveAreaOffset);
    asm.emitADDI  (SP,  16, SP);       // pop parameters
  }

  // Generate code for "VM_Type VM_Magic.getObjectType(Object object)".
  //
  static void generateGetObjectType(VM_Assembler asm) {
    // On entry the stack looks like this:
    //
    //                     hi-mem
    //            +-------------------------+    \
    //    SP ->   |    (Object object)      |     |- java operand stack
    //            +-------------------------+    /

    asm.emitLWZ(T0,  0, SP);                   // get object pointer
    VM_ObjectModel.baselineEmitLoadTIB(asm,T0,T0);
    asm.emitLWZ(T0,  TIB_TYPE_INDEX << LOG_BYTES_IN_ADDRESS, T0); // get "type" field from type information block
    asm.emitSTW(T0,  0, SP);                   // *sp := type
  }

  // Generate code for "int VM_Magic.getArrayLength(Object object)".
  //
  static void generateGetArrayLength(VM_Assembler asm) {
    // On entry the stack looks like this:
    //
    //                     hi-mem
    //            +-------------------------+    \
    //    SP ->   |    (Object object)      |     |- java operand stack
    //            +-------------------------+    /

    asm.emitLWZ(T0,  0, SP);                   // get object pointer
    asm.emitLWZ(T0,  VM_ObjectModel.getArrayLengthOffset(), T0); // get array length field
    asm.emitSTW(T0,  0, SP);                   // *sp := length
  }

  // Generate code for "int VM_Magic.sysCallN(int ip, int toc, int val0, int val1, ..., valN-1)".
  // Taken: number of bytes in parameters (not including JTOC, IP)
  //
  static int generateSysCall1(VM_Assembler asm, 
			      int rawParametersSize, 
			      boolean check_stack) {
    // Make sure stack has enough space to run the C function and any calls it makes.
    // We must do this prior to calling the function because there's no way to expand our stack
    // if the C function causes a guard page trap: the C stackframe cannot be relocated and
    // its contents cannot be scanned for object references.
    //
    if (check_stack) {
      asm.emitStackOverflowCheck(STACK_SIZE_NATIVE);
    }
     
    // Create a linkage area that's compatible with RS6000 "C" calling conventions.
    // Just before the call, the stack looks like this:
    //
    //                     hi-mem
    //            +-------------------------+  . . . . . . . .
    //            |          ...            |                  \
    //            +-------------------------+                   |
    //            |          ...            |    \              |
    //            +-------------------------+     |             |
    //            |       (int ip)          |     |             |
    //            +-------------------------+     |             |
    //            |       (int val0)        |     |  java       |- java
    //            +-------------------------+     |-  operand   |   stack
    //            |       (int val1)        |     |    stack    |    frame
    //            +-------------------------+     |             |
    //            |          ...            |     |             |
    //            +-------------------------+     |             |
    //  SP ->     |      (int valN-1)       |     |             |
    //            +-------------------------+    /              |
    //            |          ...            |                   |
    //            +-------------------------+                   |
    //            |                         | <-- spot for this frame's callee's return address
    //            +-------------------------+                   |
    //            |          MI             | <-- this frame's method id
    //            +-------------------------+                   |
    //            |       saved FP          | <-- this frame's caller's frame
    //            +-------------------------+  . . . . . . . . /
    //            |      saved JTOC         |
    //            +-------------------------+
    //            |      saved SP           |
    //            +-------------------------+  . . . . . . . . . . . . . .
    //            | parameterN-1 save area  | +  \                         \
    //            +-------------------------+     |                         |
    //            |          ...            | +   |                         |
    //            +-------------------------+     |- register save area for |
    //            |  parameter1 save area   | +   |    use by callee        |
    //            +-------------------------+     |                         |
    //            |  parameter0 save area   | +  /                          |  rs6000
    //            +-------------------------+                               |-  linkage
    //        +20 |       TOC save area     | +                             |    area
    //            +-------------------------+                               |
    //        +16 |       (reserved)        | -    + == used by callee      |
    //            +-------------------------+      - == ignored by callee   |
    //        +12 |       (reserved)        | -                             |
    //            +-------------------------+                               |
    //         +8 |       LR save area      | +                             |
    //            +-------------------------+                               |
    //         +4 |       CR save area      | +                             |
    //            +-------------------------+                               |
    //  FP ->  +0 |       (backlink)        | -                             |
    //            +-------------------------+  . . . . . . . . . . . . . . /
    //
    // Notes:
    // 1. C parameters are passed in registers R3...R10
    // 2. space is also reserved on the stack for use by callee
    //    as parameter save area
    // 3. parameters are pushed on the java operand stack left to right
    //    java conventions) but if callee saves them, they will
    //    appear in the parameter save area right to left (C conventions)
    //
    // generateSysCall1  set ups the call
    // generateSysCall2  branches and cleans up
    // generateSysCallRet_<type> fix stack pushes return values
 
    int parameterAreaSize = rawParametersSize + SIZE_IP; 
    int ipOffset        = parameterAreaSize - SIZE_IP;		 // offset of ip parameter from SP
    int endValueOffset  = ipOffset; 		 		 // offset of end of value0 parameter from SP

    int linkageAreaSize   = rawParametersSize +		// values
      (2 * SIZE_TOC) +		 		        // saveJTOC & SP
      (6 * 4);		 		 		// backlink + cr + lr + res + res + TOC

    asm.emitSTWU (FP,  -linkageAreaSize, FP);        // create linkage area
    asm.emitSTW (JTOC, linkageAreaSize-4, FP);      // save JTOC
    asm.emitSTW (SP,   linkageAreaSize-8, FP);      // save SP

    asm.emitLWZtoc(S0, VM_Entrypoints.the_boot_recordField.getOffset()); // load sysTOC into JTOC
    asm.emitLWZ  (JTOC, VM_Entrypoints.sysTOCField.getOffset(), S0);
    asm.emitLWZ  (0,    ipOffset,  SP);              // load new IP
     
    return endValueOffset;
  }


  static void generateSysCall2(VM_Assembler asm, int rawParametersSize) {
    int parameterAreaSize = rawParametersSize + SIZE_IP;
    int linkageAreaSize   = rawParametersSize +		 		 // values
      (2 * SIZE_TOC) +		 		 // saveJTOC & SP
      (6 * 4);		 		 		 // backlink + cr + lr + res + res + TOC

    asm.emitMTLR(0);                                // call desired...
    asm.emitBCLRL();                                 // ...function

    asm.emitLWZ  (JTOC, linkageAreaSize - 4, FP);    // restore JTOC
    asm.emitLWZ  (SP,   linkageAreaSize - 8, FP);    // restore SP
    asm.emitADDI (FP,  +linkageAreaSize, FP);        // remove linkage area
  }


  // generate call and return sequence to invoke a C arithmetic helper function through the boot record
  // field specificed by target.  See comments above in sysCall1 about AIX linkage conventions.
  // Caller deals with expression stack (setting up args, pushing return, adjusting stack height)
  static void generateSysCall(VM_Assembler asm, int parametersSize, VM_Field target) {
    int linkageAreaSize   = parametersSize + (2 * SIZE_TOC) + (6 * 4);

    asm.emitSTWU (FP,  -linkageAreaSize, FP);        // create linkage area
    asm.emitSTW (JTOC, linkageAreaSize-4, FP);      // save JTOC
    asm.emitSTW (SP,   linkageAreaSize-8, FP);      // save SP

    // acquire toc and ip from bootrecord
    asm.emitLWZtoc(S0, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitLWZ  (JTOC, VM_Entrypoints.sysTOCField.getOffset(), S0);
    asm.emitLWZ  (0, target.getOffset(), S0);

    // call it
    asm.emitMTLR(0);
    asm.emitBCLRL(); 

    // cleanup
    asm.emitLWZ  (JTOC, linkageAreaSize - 4, FP);    // restore JTOC
    asm.emitLWZ  (SP,   linkageAreaSize - 8, FP);    // restore SP
    asm.emitADDI (FP,   linkageAreaSize, FP);        // remove linkage area
  }


  static void generateSysCallRet_I(VM_Assembler asm, int rawParametersSize) {
    int parameterAreaSize = rawParametersSize + SIZE_IP;
    asm.emitADDI (SP, parameterAreaSize - 4, SP);    // pop args, push space for return value
    asm.emitSTW (3, 0, SP);                         // deposit C return value (R3) on stacktop
  }

  static void generateSysCallRet_L(VM_Assembler asm, int rawParametersSize) {
    int parameterAreaSize = rawParametersSize + SIZE_IP;
    asm.emitADDI (SP, parameterAreaSize - 8, SP);    // pop args, push space for return value
    asm.emitSTW (3, 0, SP);                         // deposit C return value (R3) on stacktop
    asm.emitSTW (4, 4, SP);                         // deposit C return value (R4) on stacktop
  }
}

