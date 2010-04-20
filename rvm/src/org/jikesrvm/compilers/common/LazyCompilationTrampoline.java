package org.jikesrvm.compilers.common;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class LazyCompilationTrampoline {

  public static CodeArray getInstructions() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.LazyCompilationTrampoline.instructions;
    } else {
      return org.jikesrvm.ppc.LazyCompilationTrampoline.instructions;
    }
  }
}