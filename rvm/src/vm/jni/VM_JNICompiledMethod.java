/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Information associated with artifical stackframe inserted at the
 * transition from Jave to JNI Native C.  
 *
 * Exception delivery should never see Native C frames, or the Java to C 
 * transition frame.  Native C code is redispatched during exception
 * handling to either process/handle and clear the exception or to return
 * to Java leaving the exception pending.  If it returns to the transition
 * frame with a pending exception. JNI causes an athrow to happen as if it
 * was called at the call site of the call to the native method.
 *
 * @author Ton Ngo
 */
final class VM_JNICompiledMethod extends VM_CompiledMethod {

  VM_JNICompiledMethod(int id, VM_Method m) {
    super(id,m);    
  }

  final int getCompilerType() throws VM_PragmaUninterruptible { 
    return JNI; 
  }

  final VM_ExceptionDeliverer getExceptionDeliverer() throws VM_PragmaUninterruptible { 
    // this method should never get called.
    //
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    return null;
  }
      
  final void getDynamicLink(VM_DynamicLink dynamicLink, int instructionOffset) throws VM_PragmaUninterruptible { 
    // this method should never get called.
    //
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }

  final int findCatchBlockForInstruction(int instructionOffset, VM_Type exceptionType) {
    return -1;
  }
   
  final int findLineNumberForInstruction(int instructionOffset) {
    return 0;
  }

  final int findBytecodeIndexForInstruction(int instructionOffset) {
    return -1;
  }
      
  final int findInstructionForLineNumber(int lineNumber) {
    return -1;
  }
      
  final int findInstructionForNextLineNumber(int lineNumber) {
    return -1;
  }

  public final VM_LocalVariable[] findLocalVariablesForInstruction(int instructionOffset) {
    return null;
  }

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  final void set(VM_StackBrowser browser, int instr) {
    browser.setBytecodeIndex( -1 );
    browser.setCompiledMethod( this );
    browser.setMethod( method );
  }

  /**
   * Advance the VM_StackBrowser up one internal stack frame, if possible
   */
  final boolean up(VM_StackBrowser browser) {
    return false;
  }

  public final void printStackTrace(int instructionOffset, java.io.PrintStream out) {
    if (method != null) {
      // print name of native method
      out.println("\tat " + method.getDeclaringClass().getDescriptor().classNameFromDescriptor()
		  + "." + method.getName() + " (native method)");
    } else {
      out.println("\tat <native method>");
    }
  }

  public final void printStackTrace(int instructionOffset, java.io.PrintWriter out) {
    if (method != null) {
      // print name of native method
      out.println("\tat " + method.getDeclaringClass().getDescriptor().classNameFromDescriptor()
		  + "." + method.getName() + " (native method)");
    } else {
      out.println("\tat <native method>");
    }
  }
}
