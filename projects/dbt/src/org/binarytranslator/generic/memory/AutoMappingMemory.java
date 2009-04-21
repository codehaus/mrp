package org.binarytranslator.generic.memory;

import java.io.RandomAccessFile;

import org.binarytranslator.DBT;


/**
 * A memory implementation that will automatically map pages into memory, as soon
 * as they are requested. Therefore, this memory never fails with an exception
 * due to a non-mapped memory page.
 * 
 * The class is implemented as a decorator, therefore it can work with any underlying
 * memory that implements the {@link Memory} interface. However, automatic mapping
 * of pages is (at the moment) only supported during interpretation.
 * 
 * @author Michael Baer
 *
 */
public class AutoMappingMemory extends CallBasedMemory {
  
  private Memory mem;
  
  public AutoMappingMemory(Memory memoryImplementation) {
    super(AutoMappingMemory.class);
    
    if (DBT.VerifyAssertions) DBT._assert(memoryImplementation != null);
    
    this.mem = memoryImplementation;
  }

  public boolean equals(Object arg0) {
    return mem.equals(arg0);
  }

  public int getPageSize() {
    return mem.getPageSize();
  }

  public int hashCode() {
    return mem.hashCode();
  }

  public boolean isMapped(int addr) {
    return mem.isMapped(addr);
  }

  public boolean isPageAligned(int addr) {
    return mem.isPageAligned(addr);
  }

  public int load32(int addr) {
    try {
      return mem.load32(addr);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 4);
      return mem.load32(addr);
    }
  }

  public int loadInstruction32(int addr) {
    try {
      return mem.loadInstruction32(addr);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 4);
      return mem.loadInstruction32(addr);
    }
  }

  public int loadInstruction8(int addr) {
    try {
      return mem.loadInstruction8(addr);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 1);
      return mem.loadInstruction8(addr);
    }
  }

  public int loadSigned16(int addr) {
    try {
      return mem.loadSigned16(addr);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 2);
      return mem.loadSigned16(addr);
    }
  }

  public int loadSigned8(int addr) {
    try {
      return mem.loadSigned8(addr);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 1);
      return mem.loadSigned8(addr);
    }
  }

  public int loadUnsigned16(int addr) {
    try {
      return mem.loadUnsigned16(addr);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 2);
      return mem.loadUnsigned16(addr);
    }
  }

  public int loadUnsigned8(int addr) {
    try {
      return mem.loadUnsigned8(addr);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 1);
      return mem.loadUnsigned8(addr);
    }
  }

  public int map(int addr, int len, boolean read, boolean write, boolean exec) throws MemoryMapException {
    return mem.map(addr, len, read, write, exec);
  }

  public int map(RandomAccessFile file, long offset, int addr, int len, boolean read, boolean write, boolean exec) throws MemoryMapException {
    return mem.map(file, offset, addr, len, read, write, exec);
  }

  public void store16(int addr, int value) {
    try {
      mem.store16(addr, value);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 2);
      mem.store16(addr, value);
    }
  }

  public void store32(int addr, int value) {
    try {
      mem.store32(addr, value);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 4);
      mem.store32(addr, value);
    }
  }

  public void store8(int addr, int value) {
    try {
      mem.store8(addr, value);
    }
    catch (Exception e) {
      mem.ensureMapped(addr, addr + 1);
      mem.store8(addr, value);
    }
  }

  public String toString() {
    return mem.toString();
  }

  public int truncateToNextPage(int addr) {
    return mem.truncateToNextPage(addr);
  }

  public int truncateToPage(int addr) {
    return mem.truncateToPage(addr);
  }

  public void unmap(int addr, int len) {
    mem.unmap(addr, len);
  }

  @Override
  public void changeProtection(int address, int len, boolean newRead, boolean newWrite, boolean newExec) {
    mem.changeProtection(address, len, newRead, newWrite, newExec);
  }

  @Override
  public int loadInstruction16(int addr) {
    return mem.loadInstruction16(addr);
  }
}
