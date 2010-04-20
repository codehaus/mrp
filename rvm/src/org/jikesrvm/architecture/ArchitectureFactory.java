package org.jikesrvm.architecture;

import org.jikesrvm.VM;

public final class ArchitectureFactory {
  public static AbstractRegisters createRegisters(){
    if (VM.BuildForIA32) {
      return new org.jikesrvm.ia32.Registers();
    } else {
      return new org.jikesrvm.ppc.Registers();      
    }
  }

  public static void initOutOfLineMachineCode() {
    if(VM.BuildForIA32) {
      org.jikesrvm.ia32.OutOfLineMachineCode.init();
    } else {
      org.jikesrvm.ppc.OutOfLineMachineCode.init();
    }
  }
}
