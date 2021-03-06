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
package org.binarytranslator.generic.os.loader.image;

import java.io.IOException;
import java.io.RandomAccessFile;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.memory.MemoryMapException;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.process.ProcessSpace;

/**
 * A very simple loader, that will load binary directly into memory and then start executing it from address 0.
 * Actually, this is just a hack to the .bin file format to include some header information that tells us the 
 * architecture for this image.
 * The file format is:
 * [Identifier]      <8 ASCII-CHARS> EXT_IMG\0  
 * [File Version]    <1 BYTE>        0x0
 * [Architecture ID] <4 ASCII-CHARS> <ARM|PPC|X86> \0
 * [Padding]         <3 BYTE>
 * [Image data]      <BINARY-Data>   <Assembly Code, to be loaded at addr #0>
 * @author baerm
 *
 */
public class ImageLoader extends Loader {
  
  private String architecture;
  
  @Override
  public ABI getABI() {
    return ABI.Undefined;
  }

  @Override
  public ISA getISA() {
    if (architecture.equals("ARM"))
      return ISA.ARM;
    else
      if (architecture.equals("PPC"))
        return ISA.PPC;
      else
        if (architecture.equals("X86"))
          return ISA.X86;
    
    return ISA.Undefined;
  }
  
  /**
   * Read from the given file and determine whether it contains the correct ID string for an
   * ASM Loader file. 
   */
  private static boolean readAndCheckID(RandomAccessFile file) {
    try {
      byte[] ID = new byte[8];
      if (file.read(ID) != ID.length)
        return false;
      
      return ID[0] == 'E' && ID[1] == 'X' && ID[2] == 'T' && ID[3] == '_'
          && ID[4] == 'I' && ID[5] == 'M' && ID[6] == 'G' && ID[7] == 0;
    }
    catch (Exception e) {
      return false;
    }
  }

  /**
   */
  public ProcessSpace readBinary(String filename) throws IOException {
    
    report("Reading: " + filename);
    RandomAccessFile file = new RandomAccessFile(filename, "r");
    
    if (!readAndCheckID(file))
      throw new IOException("File does not contain the expected EXT_IMG ID string.");
    
    //check the file version. Currently, we only support one version anyway.
    byte version = file.readByte();
    
    if (version != 0) {
      throw new IOException("Unsupported image file version.");
    }
    
    byte[] architecture = new byte[3];
    if (file.read(architecture) != architecture.length)
      throw new IOException("Unable to read architecture string.");
    
    //read the architecture string
    this.architecture = new String(architecture, "ASCII");
    
    //skip the double-word padding and the delimiter of the architecture string
    //file.skipBytes(4);
    
    ProcessSpace ps = ProcessSpace.createProcessSpaceFromBinary(this);
    
    int fileAndMemSize = (int)file.length() - 16;
    try {
      ps.memory.map(file, 16, 0, fileAndMemSize, true, true, true);
    }
    catch (MemoryMapException e) {
      e.printStackTrace();
      return null;
    }
    
    ps.initialise(this);
    return ps;
  }
  
  /**
   * Checks if the given file is an ASM Loader file.
   */
  public static boolean conforms(String filename) {

    report("Testing if file conforms: " + filename);
    RandomAccessFile file = null;
    
    try {
      //if the file contains the correct ID string, then we assume it to be an ASM loader file
      file = new RandomAccessFile(filename, "r");
      return readAndCheckID(file);      
    }
    catch (Exception e) {
      return false;
    }
    finally {
      try {
        file.close();
      }
      catch (Exception e) {}
    }
  }
  
  private static void report(String s){
    if (DBT_Options.debugLoader) {
      System.out.print("Image Loader:");
      System.out.println(s);
    }
  }

  @Override
  public int getBrk() {
    return -1;
  }

  @Override
  public int getEntryPoint() {
    return 0;
  }
}
