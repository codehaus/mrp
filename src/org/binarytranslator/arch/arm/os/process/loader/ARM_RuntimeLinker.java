package org.binarytranslator.arch.arm.os.process.loader;

import java.io.IOException;

import org.binarytranslator.DBT;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.generic.decoder.Interpreter;
import org.binarytranslator.generic.os.loader.elf.ELF_File;
import org.binarytranslator.generic.os.loader.elf.ELF_Loader;
import org.binarytranslator.generic.os.loader.elf.JavaRuntimeLinker;
import org.binarytranslator.generic.os.loader.elf.ELF_File.RelocationTable;
import org.binarytranslator.generic.os.loader.elf.ELF_File.StringTable;
import org.binarytranslator.generic.os.loader.elf.ELF_File.SymbolTable;
import org.binarytranslator.generic.os.process.ProcessSpace;

public class ARM_RuntimeLinker extends JavaRuntimeLinker {
  
  /** Introduce symbolic names for the different ARM relocation types. */
  private final static int R_ARM_ABS32 = 2;
  private final static int R_ARM_GLOB_DAT = 21;
  private final static int R_ARM_JUMP_SLOT = 22;
  private final static int R_ARM_RELATIVE = 23;

  public ARM_RuntimeLinker(ProcessSpace ps, ELF_Loader loader) {
    super(ps, loader);

    //TODO: Introduce some kind of class for ELF library managements
    String libDir = "C:\\0Dateien\\University of Manchester\\MSc Thesis\\ARM Executables\\Dynamically Linked\\Hello World\\";
    libNames.put("libc.so.6", libDir + "libc-2.2.5.so");
    libNames.put("ld-linux.so.2", libDir + "ld-linux.so.2");
  }

  @Override
  protected void relocate(SharedObject lib, ELF_File.RelocationTable reloc) throws IOException {
    
    if (reloc.hasAddends)
      throw new Error("ARM should not encounter RELA sections when runtime linking executable files.");
    
    //get the symbol table for this library
    SymbolTable symtab = lib.getDynamicSection().findSymbolTable();
    
    //get the library's string table
    StringTable strTab = lib.getDynamicSection().findStringTable();
  
    //now start processing the library's relocation entries
    for (RelocationTable.Entry entry : reloc.entries) {
      
      //where shall we store the resolved symbol
      int resolveToAddress = entry.offset + lib.getLoadOffset();
  
      switch (entry.relocationType) {
      case R_ARM_ABS32:
      {
        SymbolTable.Entry symbol = symtab.getEntry(entry.symbolIndex);
        
        if (DBT.VerifyAssertions) DBT._assert(!symbol.isUndefined());
        
        //this a local symbol that we can easily resolve
        int offset = ps.memory.load32(resolveToAddress);
        ps.memory.store32(resolveToAddress, symbol.value + lib.getLoadOffset() + offset);
      }
      break;
      
      case R_ARM_GLOB_DAT:
      case R_ARM_JUMP_SLOT:
      {
        SymbolTable.Entry symbol = symtab.getEntry(entry.symbolIndex);
        int value;
        
        if (symbol.isUndefined()) {
          String symbolName = strTab.lookup(symbol.nameIdx);
          value = resolveSymbolAddress(symbolName);
          
          if (value == -1) {
            //we allow only weak symbols to be unresolved
            if (symbol.binding != SymbolTable.STB_WEAK) {
              throw new RuntimeException("Unable to resolve: " + symbolName + " in " + lib);
            }
            
            continue;
          }
        }
        else {
          //this a local symbol that is already resolved. Just relocate it
          value = symbol.value + lib.getLoadOffset();
        }
        
        //store the resolved symbol
        ps.memory.store32(resolveToAddress, value);
      }
      break;
  
      case R_ARM_RELATIVE:
      {
        //R_ARM_RELATIVE
        int address = ps.memory.load32(resolveToAddress);
        ps.memory.store32(resolveToAddress, address + lib.getLoadOffset());
      }
      break;
      
      default:
        throw new RuntimeException("Unknown relocation type: " + entry.relocationType);
      }
    }
  }

  @Override
  protected void runInitRoutine(int startPC) {
    
    int pc = startPC;
    ps.setCurrentInstructionAddress(startPC);
    
    Interpreter interpreter = ps.createInstructionInterpreter();
    
    ((ARM_ProcessSpace)ps).registers.set(ARM_Registers.LR, startPC);
  
    while (!ps.finished) {
  
      Interpreter.Instruction instruction = interpreter.decode(pc);      
      instruction.execute();
      pc = instruction.getSuccessor(pc);
      
      if (pc == -1)
        pc = ps.getCurrentInstructionAddress();
      else
        ps.setCurrentInstructionAddress(pc);
      
      //we're done running the init method
      if (pc == startPC)
        break;
    }
    
    if (ps.finished) {
      throw new RuntimeException("ProcessSpace exited while running the INIT routine of a dynamically linked library.");
    }
  }

}
