package org.binarytranslator;

import org.jikesrvm.VM;

public final class DBT {
  public static final boolean VerifyAssertions = true;
  
  public static void _assert(boolean cond) {
    
    if (!VerifyAssertions)
      return;
    
    if (VM.VerifyAssertions)
      VM._assert(cond);
    else
    {
      if (!cond) {
        //assertion failed, see if we can get some info on where the assertion occured
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        
        if (trace.length > 0) {
          StackTraceElement source = trace[trace.length-1];
          System.err.println("Assertion failed in: " + source.getFileName() + "(" + source.getLineNumber() + ")");
        }
        else {
          System.err.println("Assertion failed. No stack trace on assertion source available.");
        }
        
        System.exit(-1);
      }
    }
  }
}
