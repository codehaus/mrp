package org.binarytranslator.arch.arm.os.abi.linux;

import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;
import org.binarytranslator.generic.os.abi.linux.LinuxSystemCalls;

public class ARM_LinuxSystemCalls extends LinuxSystemCalls {
  
  public ARM_LinuxSystemCalls(LinuxSystemCallGenerator src) {
    super(src);
  }

  @Override
  protected String getMachine() {
    //TODO: Grab this from a real machine
    return "ARM";
  }

  @Override
  public String sysCallToString(int syscall) {
    throw new RuntimeException("Not yet implemented.");
  }

}
