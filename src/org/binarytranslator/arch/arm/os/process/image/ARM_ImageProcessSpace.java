package org.binarytranslator.arch.arm.os.process.image;

import org.binarytranslator.DBT;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions;
import org.binarytranslator.arch.arm.os.abi.semihosting.AngelSystemCalls;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.generic.execution.GdbController.GdbTarget;
import org.binarytranslator.generic.memory.AutoMappingMemory;
import org.binarytranslator.generic.os.loader.Loader;

public class ARM_ImageProcessSpace extends ARM_ProcessSpace {
  
  private AngelSystemCalls sysCalls = new AngelSystemCalls(this);
  
  public ARM_ImageProcessSpace() {
    super();
    
    //make sure that pages of memory are automatically mapped in as they are requested.
    memory = new AutoMappingMemory(memory);
  }
  
  @Override
  public void doSysCall() {
    
    //check the SWI instrution to make sure, that we're actually doing an Angel call here
    int instruction = memory.loadInstruction32(getCurrentInstructionAddress());
    ARM_Instructions.Instruction instr = ARM_InstructionDecoder.decode(instruction);
    
    if (DBT.VerifyAssertions) {
      DBT._assert(instr instanceof ARM_Instructions.SoftwareInterrupt);
    }
    
    //Thumb system calls start from 0, while ARM calls start from 0x900000.
    //Use a mask to let both calls start from the same address
    int sysCallNr = ((ARM_Instructions.SoftwareInterrupt)instr).getInterruptNumber();
    
    if (sysCallNr == 0x123456) {    
      sysCalls.doSysCall(registers.get(0));
      
      //simulate a proper return from syscalls
      setCurrentInstructionAddress(getCurrentInstructionAddress() + 4);
    }
    else {
      //switch the operating mode to Supervisor
      registers.switchOperatingModeAndStoreSPSR(ARM_Registers.OperatingMode.SVC);
      registers.setInterruptsEnabled(false);
      
      //put the return address into the link register
      registers.set(ARM_Registers.LR, getCurrentInstructionAddress() + 4);
      
      //jump to the respective SWI handler
      setCurrentInstructionAddress(0x8);
    }
  }
  
  /**
   * This implementation of the undefined instruction handler behaves as
   * the ARM processor would: It switches the operating mode,
   * jumps to the undefined instruction handler and tries to execute the instruction 
   * stored there.
   */
  @Override
  public void doUndefinedInstruction() {
    registers.switchOperatingModeAndStoreSPSR(ARM_Registers.OperatingMode.SVC);
    registers.setInterruptsEnabled(false);
    
    //put the return address into the link register
    registers.set(ARM_Registers.LR, getCurrentInstructionAddress() + 4);
    
    //jump to the respective SWI handler
    setCurrentInstructionAddress(0x4);
  }
  
  @Override
  public GdbTarget getGdbTarget() {
    throw new UnsupportedOperationException("GDB not implemented.");
  }

  @Override
  public void initialise(Loader loader) {
    registers.set(ARM_Registers.PC, loader.getEntryPoint());
  }

}
