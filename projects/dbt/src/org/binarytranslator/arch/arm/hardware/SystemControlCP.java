package org.binarytranslator.arch.arm.hardware;

import org.binarytranslator.arch.arm.decoder.ARM2IR;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.CoprocessorDataProcessing;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.CoprocessorDataTransfer;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.CoprocessorRegisterTransfer;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;

public class SystemControlCP extends Coprocessor {
  
  private final int processorID;
  
  public SystemControlCP(ARM_ProcessSpace ps) {
    super(ps);
    
    //expected processor ID on a ARM710
    processorID = 0x4104710;
  }

  @Override
  public void interpretDataProcessing(CoprocessorDataProcessing instr) {
    throw new RuntimeException("Not supported");
  }

  @Override
  public void interpretDataTransfer(CoprocessorDataTransfer instr) {
    throw new RuntimeException("Not supported");
  }

  @Override
  public void interpretRegisterTransfer(CoprocessorRegisterTransfer instr) {
    if (instr.getCoprocessorRn() == 0) {
      if (!instr.isLoadFromCP())
        throw new RuntimeException("Not supported");
      
      regs.set(instr.getRd(), processorID);
    }
    else
    {
      if (instr.isLoadFromCP())
        throw new RuntimeException("Not supported");
      
      System.out.println("Coprocessor store to register " + instr.getCoprocessorRn());
    }
  }

  @Override
  public void translateDataProcessing(CoprocessorDataProcessing instr, ARM2IR arm2ir, int pc) {
    throw new RuntimeException("Not supported");
  }

  @Override
  public void translateDataTransfer(CoprocessorDataTransfer instr, ARM2IR arm2ir, int pc) {
    throw new RuntimeException("Not supported");
  }

  @Override
  public void translateRegisterTransfer(CoprocessorRegisterTransfer instr, ARM2IR arm2ir, int pc) {
    arm2ir.appendInterpretedInstruction(pc, null);
  }

}
