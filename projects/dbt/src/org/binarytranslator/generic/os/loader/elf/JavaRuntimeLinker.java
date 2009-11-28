/*
 *  This file is part of MRP (http://mrp.codehaus.org/).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.binarytranslator.generic.os.loader.elf;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.os.loader.elf.ELF_File.DynamicSection;
import org.binarytranslator.generic.os.loader.elf.ELF_File.SegmentHeader;
import org.binarytranslator.generic.os.loader.elf.ELF_File.StringTable;
import org.binarytranslator.generic.os.loader.elf.ELF_File.SymbolTable;
import org.binarytranslator.generic.os.process.ProcessSpace;

public abstract class JavaRuntimeLinker extends RuntimeLinker {
  
  /** The process space that we're linking the file in. */
  protected final ProcessSpace ps;
  
  /** The loader that triggered the runtime linking. */
  private final ELF_Loader loader;
  
  /** the file that we're ultimately trying to link */
  private ELF_File file;
  
  /** A list of all libraries that are referenced within the loading process. */
  private LinkedList<SharedObject> libraries = new LinkedList<SharedObject>();
  
  /** A list of the star addres of all init routines, that we're supposed to execute. */
  private LinkedList<Integer> initMethods = new LinkedList<Integer>();
  
  /** The address of the memory block that is going to be allocated to the next library*/
  private int nextMemoryBlock = 1024*1024*1024; //start mapping libraries at 1GB (0x40000000)
  
  /** Maps a library name to a filename. */
  protected final HashMap<String, String> libNames = new HashMap<String, String>();

  public static class SharedObject {
    /** The libary's name. */
    private final String filename;
    
    /** The elf file that this library can be loaded from. */
    private ELF_File file = null;
    
    /** The dynamic section of this library */
    private ELF_File.DynamicSection dynamicSection = null;
    
    /** Is the library already relocated? */
    private boolean relocated = false;
    
    /** Stores if a library can only be loaded at a specific load offset. This is generally only true for the program file. */
    private final boolean hasFixedLoadOffset;
    
    /** The address at which this library has been loaded. */
    private int loadedAt;
    
    /** Other libraries, that this library depends upon. */
    private SharedObject[] dependencies = null;
    
    /** 
     * A list of segments that have additionally received write protection to allow
     * relocations within the segment, although the segment was originally write protected. */
    private LinkedList<SegmentHeader> removedWriteProtection = new LinkedList<SegmentHeader>();
    
    public SharedObject(String filename) {
      this.filename = filename;
      this.hasFixedLoadOffset = false;
    }
    
    public SharedObject(String filename, int fixedLoadOffset) {
      this.filename = filename;
      this.loadedAt = fixedLoadOffset;
      this.hasFixedLoadOffset = true;
    }
    
    /** The elf file that this library can be loaded from. */
    public ELF_File getFile() throws IOException {
      if (file == null)
        file = new ELF_File(filename);
      
      return file;
    }
    
    /** Returns the dynamic section of this library */
    public ELF_File.DynamicSection getDynamicSection() throws IOException {
      if (dynamicSection == null) {
        dynamicSection = getFile().getDynamicSection();
      }
      
      return dynamicSection;
    }
    
    /** Returns the address at which this library has been loaded. */
    public int getLoadOffset() {
      return loadedAt;
    }
    
    /** Returns true if a libary can only be loaded at a fixed offset in memory. False otherwise. */
    public boolean hasFixedLoadOffset() {
      return hasFixedLoadOffset;
    }

    @Override
    public String toString() {
      File f = new File(filename);
      return f.getName();
    }
  }
  
  public JavaRuntimeLinker(ProcessSpace ps, ELF_Loader loader) {
    this.ps = ps;
    this.loader = loader;
    this.file = loader.getFile();
  }
  
  /**
   * This function is the main entry point into the dynamic linker and will steer the whole
   * linking process.
   */
  @Override
  public final void link() throws IOException {
    ELF_File.DynamicSection dynSection = file.getDynamicSection();
    
    //align the library start address to a page
    nextMemoryBlock = ps.memory.truncateToPage(nextMemoryBlock);
    
    if (dynSection == null) {
      System.out.println("Unable to find dynamic linking segment.");
      throw new UnsupportedOperationException("Trying to runtime-link a static executable.");
    }
    
    //Create a representation for the program file, which is then treated like any other shared object
    SharedObject programfile = new SharedObject("<Program File>", 0);
    programfile.file = file;
    libraries.add(programfile);

    //load the program file and all its dependend libraries
    loadLibRecursively(programfile);
    
    //relocate all dynamic libraries    
    relocateLibRecursively(programfile);
    
    //Add write protections to all segments from which we removed it during relocation
    for (SharedObject lib : libraries) {
      for (SegmentHeader segment : lib.removedWriteProtection) {
        boolean read = (segment.p_flags & SegmentHeader.PF_R) != 0;
        boolean exec = (segment.p_flags & SegmentHeader.PF_X) != 0;    
        ps.memory.changeProtection(segment.p_vaddr + lib.loadedAt, segment.p_filesz, read, false, exec);
      }
    }

    //Call the init routines that were registered by the different libraries
    callInitRoutines();
    
    //prepare to start the real program
    ps.setCurrentInstructionAddress(loader.getEntryPoint());
  }
  
  /**
   * Resolves a library, given by its <code>name</code> into a {@link SharedObject} object.
   * If the library is referenced for the first time, it is added to the global list
   * of available {@link #libraries}.
   * 
   * @param name
   *  The name of the library that is to be resolved.
   * @return
   *  A library object representing that library.
   */
  private SharedObject resolveLibraryByName(String name) {
    
    String originalName = name;
    name = libNames.get(name);
    
    if (name == null)
      throw new RuntimeException("SharedObject not present: " + originalName);
    
    for (int i = 0; i < libraries.size(); i++) {
      SharedObject lib = libraries.get(i);
      
      if (lib.filename.equals(name))
        return lib;
    }
    
    SharedObject lib = new SharedObject(name);
    libraries.addLast(lib);
    return lib;
  }
  
  /**
   * Calls {@link #runInitRoutine(int)} several times to run all INIT routines for all loaded
   * libraries.
   */
  protected void callInitRoutines() throws IOException {
    //the linker has a special symbol that determines if we're currently starting up
    int dl_starting_up = resolveSymbolAddress("_dl_starting_up", null);
    
    if (dl_starting_up != -1)
      ps.memory.store32(dl_starting_up, 1);
    
    //Initialize the process space
    ps.initialise(loader);
    
    //call the INIT functions
    for (int init : initMethods) {
      runInitRoutine(init);
    }
    
    //note that we're done running the inits
    if (dl_starting_up != -1)
      ps.memory.store32(dl_starting_up, 0);
  }

  /**
   * Loads the library <code>lib</code> and all its dependencies into memory.
   * All newly found dependencies (i.e. libraries that have to be loaded) are
   * put into the global library collection.
   * 
   * @param lib
   *  The library that is to be loaded.
   */
  private void loadLibRecursively(SharedObject lib) throws IOException {
    
    //skip libraries, if their dependencies have already been resolved
    if (lib.dependencies != null)
      return;
    
    //get a few essential elements within the library (.dynamic section, string table etc.)
    if (DBT_Options.debugLoader) System.out.println("Resolving dependencies for: " + lib);
    DynamicSection libDynamicSection = lib.getDynamicSection();

    if (libDynamicSection == null)
      throw new RuntimeException("SharedObject " + lib + "is not a shared library.");
    
    StringTable libStrTab = libDynamicSection.findStringTable();
    
    if (libStrTab == null)
      throw new RuntimeException("Unable to find String table of shared library.");
                
    //resolve the library's dependencies
    ELF_File.DynamicSection.Entry[] libDepends = libDynamicSection.getEntriesByType(ELF_File.DynamicSection.DT_NEEDED);
    lib.dependencies = new SharedObject[libDepends.length];

    //add all the libraries that this one depends on
    for (int i = 0; i < libDepends.length; i++) {
      String dependencyName = libStrTab.lookup(libDepends[i].value);
      if (DBT_Options.debugLoader) System.out.println("  Depends on: " + dependencyName);
      
      //note the dependency
      lib.dependencies[i] = resolveLibraryByName(dependencyName);
      
      //and also resolve their dependencies
      loadLibRecursively(lib.dependencies[i]);
    }
    
    //load the library to memory
    loadSingleLibrary(lib);
  }
  
  /**
   * Relocate a library recursively by first relocating its dependencies and then 
   * relocating the library itself.
   * 
   * @param lib
   *  The library that is to be relocated.
   */
  private void relocateLibRecursively(SharedObject lib) throws IOException {
    
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
    ELF_File.DynamicSection.Entry initMethod = lib.getDynamicSection().getEntryByType(ELF_File.DynamicSection.DT_INIT);
    
    if (initMethod != null) {
      initMethods.add(initMethod.value + lib.loadedAt);
    }
  }
  
  /**
   * Relocates a single library, but none of its dependencies.
   * 
   * @param lib
   *  The library that is to be relocated.
   */
  private void relocateSingleLib(SharedObject lib) throws IOException {
    
    DynamicSection libDynamicSection = lib.getDynamicSection();
    ELF_File.RelocationTable relTable = libDynamicSection.findRelTable();
    
    if (DBT_Options.debugLoader) System.out.println("Performing relocation for: " + lib);
    
    if (relTable == null) {
      if (DBT_Options.debugLoader) System.out.println("  No REL in " + lib);
    }
    else {
      if (DBT_Options.debugLoader) System.out.println("  Performing REL in " + lib);
      relocate(lib, relTable);
    }
    
    relTable = libDynamicSection.findRelaTable();
    
    if (relTable == null) {
      if (DBT_Options.debugLoader) System.out.println("  No RELA in " + lib);
    }
    else {
      if (DBT_Options.debugLoader) System.out.println("  Performing RELA in " + lib);
      relocate(lib, relTable);
    }
    
    relTable = libDynamicSection.findJmpRelTable();
    
    if (relTable == null) {
      if (DBT_Options.debugLoader) System.out.println("  No JMPREL in " + lib);
    }
    else {
      if (DBT_Options.debugLoader) System.out.println("  Performing JMPREL in " + lib);
      relocate(lib, relTable);
    }
  }

  /**
   * Loads all segments belong to the library <code>lib</code> into memory.
   *  
   * @param lib
   *  The library that shall be loaded to memory.
   */
  private void loadSingleLibrary(SharedObject lib) {
    
    if (DBT.VerifyAssertions) DBT._assert(lib.loadedAt == 0 || lib.hasFixedLoadOffset());
    
    if (!lib.hasFixedLoadOffset()) {
    //  load the library to the next available address
      lib.loadedAt = nextMemoryBlock;
    }
    
    //do we need to write into the text segment?
    boolean needRelocText = !lib.hasFixedLoadOffset() && lib.dynamicSection.getEntryByType(ELF_File.DynamicSection.DT_TEXTREL) != null;
    
    //start by mapping the library into memory
    SegmentHeader segments[] = lib.file.getProgramSegmentHeaders();
    
    //the highest offset from nextMemoryBlock that this shared object uses
    long highestUsedAddress = 1;
    
    if (DBT_Options.debugLoader) 
      System.out.println(String.format("Loading Shared Object: %s to 0x%x", lib, lib.loadedAt));

    for (int i = 0; i < segments.length; i++) {
      
      SegmentHeader segment = segments[i];
      if (DBT_Options.debugLoader) System.out.println(" Loading Segment: " + segment);

      if (needRelocText && (segment.p_flags & ELF_File.SegmentHeader.PF_W) == 0) {
        //We are making this segment writeable, because we need to relocate inside of it.
        //Remember that segment to remove the write protection later on
        lib.removedWriteProtection.add(segment);
        segment.p_flags |= ELF_File.SegmentHeader.PF_W;
      }
    
      //create the actual segment
      segment.create(ps, lib.loadedAt);
      
      long thisAddress = segments[i].p_vaddr;
      thisAddress += segments[i].p_memsz;
      
      if (thisAddress >= highestUsedAddress)
        highestUsedAddress = thisAddress + 1;
    }
    
    if (!lib.hasFixedLoadOffset()) {
      //page-align the next memory block
      nextMemoryBlock = ps.memory.truncateToNextPage((int)(highestUsedAddress + nextMemoryBlock + 5000));
    }
  }
  
  /**
   * Resolves the given <code>symbol</code> name into the address, to which the symbol
   * has been relocated.
   *  
   * @param symbol
   *  The name of the symbol that is to be resolved.
   * @param obj
   *  The shared object for which we are trying to resolve the symbol address. Local symbols from
   *  this object will be returned as a match as well.
   * @return
   *  The address of the symbol or -1, if the symbol's address could not be resolved.
   */
  protected final int resolveSymbolAddress(String symbol, SharedObject obj) throws IOException {
    Iterator<SharedObject> libs = libraries.iterator();
    
    //iterate over the symbol table of every library that we already loaded
    while (libs.hasNext()) {
      SharedObject lib = libs.next();
      ELF_File.SymbolHashTable hashTab = lib.dynamicSection.findHashTable();
      
      //see if <symbol> is defined within this library
      SymbolTable.Entry entry = hashTab.lookup(symbol);
      
      if (entry != null && !entry.isUndefined() && entry.binding != SymbolTable.STB_LOCAL) {   
        if (entry.binding == SymbolTable.STB_GLOBAL || lib == obj)
          return entry.value + lib.loadedAt;
      }
    }
    
    return -1;
  }
  
  /**
   * This function is called as a request to run a libraries INIT routine.
   * Implementations shall run the init routine, which is starting at address <code>startPC</code>.
   * 
   * @param startPC
   *  The address at which the INIT routine is starting.
   */
  protected abstract void runInitRoutine(int startPC);
  
  /**
   * This function is called as a request to relocate the entries in <code>relocations</code>
   * which belong to <code>library</code>. 
   * 
   * @param library
   *  The library that is to be relocated.
   * @param relocations
   *  A table of relocation entries that are to be processed to relocate the library.
   */
  protected abstract void relocate(SharedObject library, ELF_File.RelocationTable relocations) throws IOException;
}
