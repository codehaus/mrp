package org.binarytranslator.arch.arm.os.process.image;

import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.generic.gdbstub.GDBTarget;
import org.binarytranslator.generic.os.loader.Loader;

public class ARM_ImageProcessSpace extends ARM_ProcessSpace {

  @Override
  public void doSysCall() {
    throw new UnsupportedOperationException("Syscalls not supported.");
  }

  @Override
  public GDBTarget getGDBTarget() {
    throw new UnsupportedOperationException("GDB not implemented.");
  }

  @Override
  public void initialise(Loader loader, int pc, int brk, String[] args) {
    registers.write(ARM_Registers.PC, pc);
  }

}
