package org.jikesrvm.architecture;

import org.jikesrvm.VM;

import org.vmmagic.pragma.Uninterruptible;

public class ArchConstants {
  @Uninterruptible
  public static int getLogInstructionWidth() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.RegisterConstants.LG_INSTRUCTION_WIDTH;
    } else {
      return org.jikesrvm.ppc.RegisterConstants.LG_INSTRUCTION_WIDTH;
    }
  }
  @Uninterruptible
  public static int getNumberOfGPRs() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.RegisterConstants.NUM_GPRS;
    } else {
      return org.jikesrvm.ppc.RegisterConstants.NUM_GPRS;
    }
  }
  @Uninterruptible
  public static int getNumberOfFPRs() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.RegisterConstants.NUM_FPRS;
    } else {
      return org.jikesrvm.ppc.RegisterConstants.NUM_FPRS;
    }
  }
}
