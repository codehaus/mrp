package org.binarytranslator.arch.arm.os.process.image;

import org.binarytranslator.arch.arm.decoder.ARM2IR;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.generic.gdbstub.GDBTarget;
import org.binarytranslator.generic.os.loader.Loader;
import org.jikesrvm.compilers.opt.ir.OPT_GenerationContext;
import org.jikesrvm.compilers.opt.ir.OPT_HIRGenerator;

public class ARM_ImageProcessSpace extends ARM_ProcessSpace {

  @Override
  public OPT_HIRGenerator createHIRGenerator(OPT_GenerationContext context) 
  {
    throw new UnsupportedOperationException("Not yet implemented.");
    //return new ARM2IR(context);
  }
  
  @Override
  public void doSysCall() {
    throw new UnsupportedOperationException("Syscalls not supported.");
  }

  @Override
  public GDBTarget getGDBTarget() {
    throw new UnsupportedOperationException("GDB not implemented.");
  }

  @Override
  public void initialise(Loader loader, int pc, int brk) {
    registers.write(ARM_Registers.PC, pc);
  }

}
