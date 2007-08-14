package org.binarytranslator.arch.arm.os.process.image;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions;
import org.binarytranslator.arch.arm.os.abi.semihosting.AngelSystemCalls;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.generic.execution.GdbController.GdbTarget;
import org.binarytranslator.generic.fault.InsufficientMemoryException;
import org.binarytranslator.generic.os.loader.Loader;

public class ARM_ImageProcessSpace extends ARM_ProcessSpace {
  
  private AngelSystemCalls sysCalls;
  private final int STACK_SIZE = 4096 * 10;
  private final int HEAP_SIZE  = 4096 * 10;
  
  public ARM_ImageProcessSpace() {
    super();
    
    //make sure that pages of memory are automatically mapped in as they are requested.
    //memory = new AutoMappingMemory(memory);
  }
  
  private int allocateFreeMemoryArea(int stackSize) throws InsufficientMemoryException
  {
    int pagesize = memory.getPageSize();
    int stackStart = -1;
    int checkedAddress = stackStart;
    
    while (checkedAddress < 0 || checkedAddress > pagesize) {
      if (memory.isMapped(checkedAddress)) {
        //we cannot extend the stack into this page
        stackStart = checkedAddress - pagesize;
      }
      else {
        int stackspace = Math.abs(stackStart - checkedAddress) + pagesize;
        
        if (stackspace >= stackSize) {
          memory.ensureMapped(stackStart - stackSize + 1, stackStart);
          return stackStart - stackSize + 1;
        }
      }
      
      checkedAddress -= pagesize;
    }
    
    throw new InsufficientMemoryException(this, "Allocate free memory area for ARM stack and heap.");
  }
  
  @Override
  public void doSysCall() {
    
    //check the SWI instrution to make sure, that we're actually doing an Angel call here
    ARM_Instructions.Instruction instr;
    
    if (registers.getThumbMode()) {
      int instrAddr = getCurrentInstructionAddress() & 0xFFFFFFFE;
      System.out.println("Thumb syscall at: " + instrAddr);
      short instruction = (short)memory.loadInstruction16(instrAddr);
      instr = ARM_InstructionDecoder.Thumb.decode(instruction);
    }
    else {
      int instruction = memory.loadInstruction32(getCurrentInstructionAddress());
      instr = ARM_InstructionDecoder.ARM32.decode(instruction);
    }
    
    if (DBT.VerifyAssertions) {
      DBT._assert(instr instanceof ARM_Instructions.SoftwareInterrupt);
    }
    
    //Thumb system calls start from 0, while ARM calls start from 0x900000.
    //Use a mask to let both calls start from the same address
    int sysCallNr = ((ARM_Instructions.SoftwareInterrupt)instr).getInterruptNumber();
    
    if (sysCallNr == 0x123456 || sysCallNr == 0xab) {    
      sysCalls.doSysCall(registers.get(0));
      
      //simulate a proper return from syscalls
      setCurrentInstructionAddress(getCurrentInstructionAddress() + instr.size());
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
    int stackBegin = allocateFreeMemoryArea(STACK_SIZE);
    int heapBegin = allocateFreeMemoryArea(HEAP_SIZE);
    
    if (DBT_Options.debugMemory || DBT_Options.debugLoader) {
      System.out.println(String.format("Placing ARM Heap from 0x%x to 0x%x.", heapBegin, heapBegin + HEAP_SIZE - 1));
      System.out.println(String.format("Placing ARM Stack from 0x%x to 0x%x.", stackBegin, stackBegin + STACK_SIZE - 1));
    }
    
    sysCalls = new AngelSystemCalls(this, heapBegin, heapBegin + HEAP_SIZE, stackBegin, stackBegin + STACK_SIZE);
  }

}
