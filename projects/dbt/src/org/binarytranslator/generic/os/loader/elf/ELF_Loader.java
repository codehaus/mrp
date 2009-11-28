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

import java.io.IOException;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.process.ProcessSpace;

public class ELF_Loader extends Loader {
  
  /** The file that we're trying to load. */
  private ELF_File file;
  
  /** The top of the stack segment. */
  private int brk;
  
  /** The entry point at which execution of the program starts. */
  private int entryPoint;
  
  @Override
  public int getEntryPoint() {
    return entryPoint;
  }
  
  @Override
  public int getBrk() {
    return brk;
  }

  @Override
  public ABI getABI() {
    return file.getHeader().getABI();
  }

  @Override
  public ISA getISA() {
    return file.getHeader().getISA();
  }
  
  /** Returns the file that we're trying to load. */
  public ELF_File getFile() {
    return file;
  }
  
  /** Returns the address at which the executable's program header has been mapped into memory. */
  public int getProgramHeaderAddress() {
    return file.getProgramHeaderAddress();
  }
  
  /** Returns the number of program headers within the executable. */
  public int getNumberOfProgramSegmentHeaders() {
    return file.getHeader().getNumberOfProgramSegmentHeaders();
  }

  /** Returns the size of a single program header within memory. */
  public int getProgramSegmentHeaderSize() {
    return file.getHeader().getProgramSegmentHeaderSize();
  }
  
  /**
   * Checks if the given file is in ELF format.
   * 
   * @param filename
   *  The name of the file that is to be checked.
   * @return
   *  True if the file is an ELF file, false otherwise.
   */
  public static boolean conforms(String filename) {
    return ELF_File.conforms(filename);
  }

  @Override
  public ProcessSpace readBinary(String filename) throws IOException {
    
    file = new ELF_File(filename);
    ProcessSpace ps = ProcessSpace.createProcessSpaceFromBinary(this);
    
    ELF_File.SegmentHeader[] segments = file.getProgramSegmentHeaders();
   
    if (DBT_Options.debugLoader) {
      System.out.println("ELF has segments:");
      
      for (ELF_File.SegmentHeader segment : segments) {
        System.out.println(" " + segment.toString());
      }
    }

    //Determine the top of the stack.
    //NB: I just copied that code from the old ELF loader. Is this really correct?
    brk = segments[segments.length > 1 ? 1 : 0].getEnd();
    
    //determine the entry point to the program
    entryPoint = file.getHeader().getEntryPoint();
    
    if (file.getDynamicSection() != null) {
    
      if (DBT_Options.debugLoader) System.out.println("Executable is dynamically linked.");
      
      //This is a dynamically linked file, so hand over control to the runtime linker
      RuntimeLinker ld = RuntimeLinker.create(ps, this);      
      ld.link();
    }
    else {
      if (DBT_Options.debugLoader) System.out.println("Executable is statically linked.");
      
      //this is a statically linked file. We simply need to map all of its segments into memory
      for (ELF_File.SegmentHeader segment : segments) {
        segment.create(ps);
      }
      
      //and then initialize the process space
      ps.initialise(this);
    }
    
    return ps;
  }
}
