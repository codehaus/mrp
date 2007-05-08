package org.binarytranslator.generic.os.loader.elf;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import org.binarytranslator.DBT;
import org.binarytranslator.generic.os.loader.elf.ELF_File.RelocationTable;
import org.binarytranslator.generic.os.loader.elf.ELF_File.SegmentHeader;
import org.binarytranslator.generic.os.loader.elf.ELF_File.StringTable;
import org.binarytranslator.generic.os.loader.elf.ELF_File.SymbolTable;
import org.binarytranslator.generic.os.process.ProcessSpace;

public class RuntimeLinker {
  
  /** */
  private final ProcessSpace ps;
  
  /** the file that we're ultimately trying to link */
  private ELF_File file;
  
  /** A list of all libraries that are referenced within the loading process. */
  private LinkedList<Library> libraries = new LinkedList<Library>();
  
  /** A list of the star addres of all init routines, that we're supposed to execute. */
  public LinkedList<Integer> initMethods = new LinkedList<Integer>();
  
  /** The address of the memory block that is going to be allocated to the next library*/
  private int nextMemoryBlock = 1024*1024*1024; //start mapping libraries at 1GB (0x40000000)
  
  /** Maps a library name to a filename. */
  private static HashMap<String, String> libNames;
  
  static {
    String libDir = "C:\\0Dateien\\University of Manchester\\MSc Thesis\\ARM Executables\\Dynamically Linked\\Hello World\\";
    
    libNames = new HashMap<String, String>();
    libNames.put("libc.so.6", libDir + "libc-2.2.5.so");
    libNames.put("ld-linux.so.2", libDir + "ld-linux.so.2");
  }
  
  private static class Library {
    /** The libary's name. */
    public String filename;
    
    /** The elf file that this library can be loaded from. */
    public ELF_File file = null;
    
    /** The dynamic section of this library */
    public ELF_File.DynamicSection dynamicSection = null;
    
    /** Is the library already relocated? */
    public boolean relocated = false;
    
    /** The address at which this library has been loaded. */
    public int relocatedTo;
    
    /** Other libraries, that this library depends upon. */
    public Library[] dependencies = null;
    
    public Library(String filename) {
      this.filename = filename;
    }
    
    @Override
    public String toString() {
      File f = new File(filename);
      return f.getName();
    }
  }
  
  public RuntimeLinker(ELF_File file, ProcessSpace ps) {
    this.file = file;
    this.ps = ps;
  }
  
  private Library addLibByName(String name) {
    
    String originalName = name;
    name = libNames.get(name);
    
    if (name == null)
      throw new RuntimeException("Library not present: " + originalName);
    
    for (int i = 0; i < libraries.size(); i++) {
      Library lib = libraries.get(i);
      
      if (lib.filename.equals(name))
        return lib;
    }
    
    Library lib = new Library(name);
    libraries.addLast(lib);
    return lib;
  }
  
  public void link() throws IOException {
    ELF_File.DynamicSection dynSection = file.getDynamicSection();
    
    //align the library start address to a page
    nextMemoryBlock = ps.memory.truncateToPage(nextMemoryBlock);
    
    if (dynSection == null) {
      System.out.println("Unable to find dynamic linking segment.");
      throw new UnsupportedOperationException("Trying to runtime-link a static executable.");
    }
    
    System.out.println(dynSection);
    
    ELF_File.DynamicSection.Entry[] neededLibs = dynSection.getEntriesByType(ELF_File.DynamicSection.DT_NEEDED);
    ELF_File.StringTable strTab = dynSection.findStringTable();
    
    //grab the libraries that are needed by this executable
    for (int i = 0; i < neededLibs.length; i++) {
      String libName = strTab.lookup(neededLibs[i].value);
      System.out.println("Needs lib: " + libName);
      
      Library lib = addLibByName(libName);
      loadLibAndRecordDependencies(lib);
    } 
    
    //finally, link the remaining symbols from the executable
    Library programfile = new Library("<Program File>");
    programfile.file = file;
    programfile.dynamicSection = dynSection;
    programfile.dependencies = new Library[0];
    libraries.add(programfile);
    
    //load the dynamic libraries
    Iterator<Library> libs = libraries.listIterator();
    
    while (libs.hasNext()) {
      Library lib = libs.next();
      
      if (!lib.relocated)
        relocateLibRecursively(lib);
    }
    
    System.out.println("Resolving undefined symbols in executable.");
    
    RelocationTable relTab = dynSection.findRelTable();
    if (relTab != null) {
      System.out.println("Resolving REL in executable.");
      relocate(programfile, relTab);
    }
    else
      System.out.println("Could not find REL table in executable.");
    
    relTab = dynSection.findRelaTable();
    
    if (relTab != null) {
      System.out.println("Resolving RELA in executable.");
      relocate(programfile, relTab);
    }
    else
      System.out.println("Could not find RELA table in executable.");
    
    relTab = dynSection.findJmpRelTable();
    
    if (relTab != null) {
      System.out.println("Resolving JMPREL in executable.");
      relocate(programfile, relTab);
    }
    else
      System.out.println("Could not find JMPREL table in executable.");
    
    //Does the program file have a init method? If yes, call it later
    ELF_File.DynamicSection.Entry initMethod = dynSection.getEntryByType(ELF_File.DynamicSection.DT_INIT);
    
    if (initMethod != null) {
      initMethods.add(initMethod.value);
    }
    
    
    int symAddr = resolveSymbolAddress("_dl_starting_up");
    
    DBT._assert(symAddr != -1);
    ps.memory.store32(symAddr, 1);
    
    
  }
  
  //TODO: Remove
  public void _tmpRestore() throws IOException {
    int symAddr = resolveSymbolAddress("_dl_starting_up");
    
    DBT._assert(symAddr != -1);
    ps.memory.store32(symAddr, 0);
  }

  private void loadLibAndRecordDependencies(Library lib) throws IOException {
    
    //skip libraries, if their dependencies have already been resolved
    if (lib.dependencies != null)
      return;
    
    //get a few essential elements within the library (.dynamic section, string table etc.)
    System.out.println("Resolving dependencies for: " + lib);
    lib.file = new ELF_File(lib.filename);      
    lib.dynamicSection = lib.file.getDynamicSection();

    if (lib.dynamicSection == null)
      throw new RuntimeException("Library is not a shared library.");
    
    StringTable libStrTab = lib.dynamicSection.findStringTable();
    
    if (libStrTab == null)
      throw new RuntimeException("Unable to find String table of shared library.");
                
    //resolve the library's dependencies
    ELF_File.DynamicSection.Entry[] libDepends = lib.dynamicSection.getEntriesByType(ELF_File.DynamicSection.DT_NEEDED);
    lib.dependencies = new Library[libDepends.length];
    


    //add all the libraries that this one depends on
    for (int i = 0; i < libDepends.length; i++) {
      String libName = libStrTab.lookup(libDepends[i].value);
      System.out.println("  Depends on: " + libName);
      
      //note the dependency
      lib.dependencies[i] = addLibByName(libName);
      
      //and also resolve their dependencies
      loadLibAndRecordDependencies(lib.dependencies[i]);
    }
    
    //load the library to memory
    loadLibToMemory(lib);


  }
  
  /** Load a library recursively by first loading its dependencies and then the library itself*/
  private void relocateLibRecursively(Library lib) throws IOException {
    
    //nothing to do if the library is already loaded
    if (lib.relocated) {
      return;
    }
    
    //to avoid recursion due to circular dependencies, mark this library as loaded
    lib.relocated = true;
    
    //load its dependencies
    for (int i = 0; i < lib.dependencies.length; i++)
      relocateLibRecursively(lib.dependencies[i]);
    
    //relocate the library itself
    relocateSingleLib(lib);
    
    //Remember to execute it's INIT function later on
    ELF_File.DynamicSection.Entry initMethod = lib.dynamicSection.getEntryByType(ELF_File.DynamicSection.DT_INIT);
    
    if (initMethod != null) {
      initMethods.add(initMethod.value + lib.relocatedTo);
    }
  }
  
  /** Loads and relocates a single library (none of the dependencies). */
  private void relocateSingleLib(Library lib) throws IOException {
    
    ELF_File.RelocationTable relTable = lib.dynamicSection.findRelTable();
    
    if (relTable == null)
      System.out.println("Did not find a REL table in " + lib);
    else {
      System.out.println("Performing REL relocation in " + lib);
      relocate(lib, relTable);
    }
    
    relTable = lib.dynamicSection.findRelaTable();
    
    if (relTable == null)
      System.out.println("Did not find a RELA table in " + lib);
    else {
      System.out.println("Performing RELA relocation in " + lib);
      relocate(lib, relTable);
    }
    
    relTable = lib.dynamicSection.findJmpRelTable();
    
    if (relTable == null)
      System.out.println("Did not find a JMPREL table in " + lib);
    else {
      System.out.println("Performing JMPREL relocation in " + lib);
      relocate(lib, relTable);
    }
  }

  private void loadLibToMemory(Library lib) {
    
    if (DBT.VerifyAssertions) DBT._assert(lib.relocatedTo == 0);
    
    //load the library to the next available address
    lib.relocatedTo = nextMemoryBlock;
    
    //do we need to write into the text segment?
    boolean needRelocText = lib.dynamicSection.getEntryByType(ELF_File.DynamicSection.DT_TEXTREL) != null;
    
    //start by mapping the library into memory
    SegmentHeader segments[] = lib.file.getProgramSegmentHeaders();
    
    //the highest offset from nextMemoryBlock that this shared object uses
    long highestUsedAddress = 1;

    for (int i = 0; i < segments.length; i++) {

      //TODO: This is only a hack. We are making this segment writeable, because we need to relocate within it...
      if (needRelocText)
        segments[i].p_flags |= ELF_File.SegmentHeader.PF_W;
    
      //create the actual segment
      segments[i].create(ps, lib.relocatedTo);
      
      long thisAddress = segments[i].p_vaddr;
      thisAddress += segments[i].p_memsz;
      
      if (thisAddress >= highestUsedAddress)
        highestUsedAddress = thisAddress + 1;
    }
    
    //page-align the next memory block
    nextMemoryBlock = ps.memory.truncateToNextPage((int)(highestUsedAddress + nextMemoryBlock + 5000));
  }
  
  private int resolveSymbolAddress(String symbol) throws IOException {
    Iterator<Library> libs = libraries.iterator();
    
    //iterate over the symbol table of every library that we already loaded
    while (libs.hasNext()) {
      Library lib = libs.next();
      ELF_File.SymbolHashTable hashTab = lib.dynamicSection.findHashTable();
      
      //see if <symbol> is defined within this library
      SymbolTable.Entry entry = hashTab.lookup(symbol);
      
      if (entry != null && !entry.isUndefined())
        return entry.value + lib.relocatedTo;
    }
    
    return -1;
  }
  
  private void relocate(Library lib, ELF_File.RelocationTable reloc) throws IOException {
    
    if (reloc.hasAddends)
      throw new RuntimeException("Not yet implemented");
    
    //get the symbol table for this library
    SymbolTable symtab = lib.dynamicSection.findSymbolTable();
    
    //get the library's string table
    StringTable strTab = lib.dynamicSection.findStringTable();

    //now start processing the library's relocation entries
    for (RelocationTable.Entry entry : reloc.entries) {
      //SegmentHeader segment = lib.file.virtualAddressToSegment(entry.offset);
      //int offsetFromSegment = entry.offset - segment.p_vaddr;
      //int addr = segment.p_paddr + offsetFromSegment;
      int addr = entry.offset + lib.relocatedTo;

      switch (entry.relocationType) {
      case 21:
      {
        //R_ARM_GLOB_DAT
        SymbolTable.Entry symbol = symtab.getEntry(entry.symbolIndex);
        String symbolName = strTab.lookup(symbol.nameIdx);
        System.out.println(symbolName);
        if (symbol.isUndefined()) {

          int symAddr = resolveSymbolAddress(symbolName);
          
          if (symAddr == -1) {
            //we allow only weak symbols to be unresolved
            if (symbol.binding != SymbolTable.STB_WEAK && symAddr == -1) {
              throw new RuntimeException("Unable to resolve: " + symbolName + " in " + lib);
            }
            
            continue;
          }
          
          //store the resolved symbol
          ps.memory.store32(entry.offset + lib.relocatedTo, symAddr);
        }
        else {
          //this a local symbol that is already resolved          
          ps.memory.store32(entry.offset + lib.relocatedTo, symbol.value + lib.relocatedTo);
        }
      }
      break;
      
      case 22:
      {
        //R_ARM_JUMP_SLOT
        SymbolTable.Entry symbol = symtab.getEntry(entry.symbolIndex);
        String symbolName = strTab.lookup(symbol.nameIdx);
        System.out.println(symbolName);
        if (symbol.isUndefined()) {

          int symAddr = resolveSymbolAddress(symbolName);
          
          //we allow only weak symbols to be unresolved
          if (symbol.binding != SymbolTable.STB_WEAK && symAddr == -1) {
            throw new RuntimeException("Unable to resolve: " + symbolName + " in " + lib);
          }
          
          //store the resolved symbol
          ps.memory.store32(entry.offset + lib.relocatedTo, symAddr);
        }
        else {
          //this a local symbol that we can easily resolve
          ps.memory.store32(entry.offset + lib.relocatedTo, symbol.value + lib.relocatedTo);
        }
      }
      break;
      case 23:
      {
        //R_ARM_RELATIVE
        int address = ps.memory.load32(addr);
        //System.out.println(address);
        ps.memory.store32(addr, address + lib.relocatedTo);
      }
      break;
      
      
      default:
        break;
      }
    }
  }
}
