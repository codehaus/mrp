package org.binarytranslator.generic.os.loader.elf;

import java.io.IOException;

import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.arch.arm.os.process.linux.ARM_LinuxProcessSpace;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.loader.elf.ELF_File.DynamicSection;
import org.binarytranslator.generic.os.process.ProcessSpace;

public class ELF_Loader extends Loader {
  
  private ELF_File file;
  private ELF_File loader;

  @Override
  public ABI getABI() {
    return file.getHeader().getABI();
  }

  @Override
  public ISA getISA() {
    return file.getHeader().getISA();
  }
  
  public int getProgramHeaderAddress() {
    return file.getProgramHeaderAddress();
  }
  
  public int getNumberOfProgramSegmentHeaders() {
    return file.getHeader().getNumberOfProgramSegmentHeaders();
  }
  
  public int getProgramSegmentHeaderSize() {
    return file.getHeader().getProgramSegmentHeaderSize();
  }
  
  public static boolean conforms(String filename) {
    return ELF_File.conforms(filename);
  }

  @Override
  public ProcessSpace readBinary(String filename) throws IOException {
    
    file = new ELF_File(filename);
    ProcessSpace ps = ProcessSpace.createProcessSpaceFromBinary(this);
    
    ELF_File.SegmentHeader[] segments = file.getProgramSegmentHeaders();
    
    System.out.println("ELF has segments:");
    for (ELF_File.SegmentHeader header : segments) {
      System.out.println(header.toString());
      header.create(ps);
    }

    int brk;
    if (segments.length > 1)
      brk = segments[1].getEnd();
    else
      brk = segments[0].getEnd();
    
    if (file.getDynamicSection() != null && file.getHeader().getABI() == ABI.ARM) {
      
      ARM_ProcessSpace armps = (ARM_ProcessSpace)ps;
      
      //invoke the runtime linker
      RuntimeLinker ld = new RuntimeLinker(file, ps);
      ld.link();
      
      ps.initialise(this, file.getHeader().getEntryPoint(), brk);
      int startInstruction = ps.getCurrentInstructionAddress();
      
      //call the INITs
      Interpreter interpreter = ps.createInstructionInterpreter();
      for (Integer init : ld.initMethods) {
        int pc = init;
        
        ps.setCurrentInstructionAddress(init);
        armps.registers.set(ARM_Registers.LR, init);
      
        while (!ps.finished) {
  
          Interpreter.Instruction instruction = interpreter.decode(pc);
          System.out.println(String.format("[0x%x] %s", pc, instruction.toString()));
          
          instruction.execute();
          pc = instruction.getSuccessor(pc);
          
          if (pc == -1)
            pc = ps.getCurrentInstructionAddress();
          else
            ps.setCurrentInstructionAddress(pc);
          
          //we're done running the init method
          if (pc == init)
            break;
        }
      }
      
      ld._tmpRestore();
      ps.setCurrentInstructionAddress(startInstruction);
    }
    else
      ps.initialise(this, file.getHeader().getEntryPoint(), brk);
    
    return ps;
  }
}
