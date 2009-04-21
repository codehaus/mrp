/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.memory;

import java.io.RandomAccessFile;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.fault.SegmentationFault;
import org.vmmagic.pragma.Inline;

/**
 * IntAddressedMemory:
 * 
 * Memory is arrays of ints, no endian conversion is performed.
 * 
 * The string helloworld following by the int of 0xcafebabe appear as:
 * 
 * <pre>
 *               Byte Address
 * Int Address | 0 | 1 | 2 | 3 |
 * -----------------------------
 * .........0c | ca| fe| ba| be|
 * .........08 |'l'|'d'| \n| \0|
 * .........04 |'o'|'W'|'o'|'r'|
 * .........00 |'H'|'e'|'l'|'l'|
 * </pre>
 */
public class IntAddressedBigEndianMemory extends CallBasedMemory {
  /**
   * The size of pages
   */
  protected static final int PAGE_SIZE = 4096;

  /**
   * Bits in offset
   */
  protected static final int OFFSET_BITS = 12;

  /**
   * The number of pages
   */
  protected static final int NUM_PAGES = 0x100000;

  /**
   * The maximum amount of RAM available
   */
  protected static final long MAX_RAM = (long) PAGE_SIZE * (long) NUM_PAGES;

  /**
   * The memory backing store
   */
  private int readableMemory[][];

  private int writableMemory[][];

  private int executableMemory[][];

  /**
   * Constructor - used when this is the instatiated class
   */
  public IntAddressedBigEndianMemory() {
    this(null);
  }

  /**
   * Constructor - used when deriving a class
   * 
   * @param classType
   *          the type of the over-riding class
   */
  protected IntAddressedBigEndianMemory(Class classType) {
    super(classType != null ? classType : IntAddressedBigEndianMemory.class);
    readableMemory = new int[NUM_PAGES][];
    writableMemory = new int[NUM_PAGES][];
    executableMemory = new int[NUM_PAGES][];
  }

  /**
   * Return the offset part of the address
   */
  @Inline
  private static final int getOffset(int address) {
    return (address & (PAGE_SIZE - 1)) >>> 2;
  }

  /**
   * Return the page table entry part of the address
   */
  @Inline
  private static final int getPTE(int address) {
    return address >>> OFFSET_BITS;
  }

  /**
   * Is the given address mapped into memory?
   * @param addr to check
   * @return true => memory is mapped
   */
  public boolean isMapped(int addr) {
    return getPage(getPTE(addr)) != null;
  }
  
  /**
   * @return the size of a page
   */
  public int getPageSize() {
    return PAGE_SIZE;
  }
  
  /**
   * Is the given address aligned on a page boundary?
   * 
   * @param addr
   *          the address to check
   * @return whether the address is aligned
   */
  public boolean isPageAligned(int addr) {
    return (addr % PAGE_SIZE) == 0;
  }

  /**
   * Make the given address page aligned to the page beneath it
   * 
   * @param addr
   *          the address to truncate
   * @return the truncated address
   */
  public int truncateToPage(int addr) {
    return (addr >> OFFSET_BITS) << OFFSET_BITS;
  }

  /**
   * Make the given address page aligned to the page above it
   * 
   * @param addr
   *          the address to truncate
   * @return the truncated address
   */
  public int truncateToNextPage(int addr) {
    return ((addr + PAGE_SIZE - 1) >> OFFSET_BITS) << OFFSET_BITS;
  }

  /**
   * Find free consecutive pages
   * 
   * @param pages
   *          the number of pages required
   * @return the address found
   */
  private final int findFreePages(int pages) {
    starting_page_search: for (int i = 0; i < NUM_PAGES; i++) {
      if (getPage(i) == null) {
        int start = i;
        int end = i + pages;
        for (; i <= end; i++) {
          if (getPage(i) != null) {
            continue starting_page_search;
          }
        }
        return start << OFFSET_BITS;
      }
    }
    throw new Error(
        "No mappable consecutive pages found for an anonymous map of size"
            + (pages * PAGE_SIZE));
  }

  /**
   * Map an anonymous page of memory
   * 
   * @param addr
   *          the address to map or NULL if don't care
   * @param len
   *          the amount of memory to map
   * @param read
   *          is the page readable
   * @param write
   *          is the page writable
   * @param exec
   *          is the page executable
   */
  public int map(int addr, int len, boolean read, boolean write, boolean exec)
      throws MemoryMapException {
    // Check that the address is page aligned
    if ((addr % PAGE_SIZE) != 0) {
      // if it is not, truncate the address down to the next page boundary and
      // start mapping from there
      int validPageCount = addr / PAGE_SIZE;
      int oldStartAddress = addr;
      addr = validPageCount * PAGE_SIZE;

      if (DBT.VerifyAssertions)
        DBT._assert(oldStartAddress > addr);

      // we have to map more more memory now to reach the same end address
      len += (oldStartAddress - addr);
    }
    
    // Calculate number of pages
    int num_pages = (len + PAGE_SIZE - 1) / PAGE_SIZE;
    // Create memory
    int pages[][] = new int[num_pages][PAGE_SIZE / 4];
    // Find address if not specified
    if (addr == 0) {
      addr = findFreePages(num_pages);
    }
    if (DBT_Options.debugRuntime) {
      System.out.println("Anonymous mapping: addr=0x"
          + Integer.toHexString(addr) + " len=" + len + (read ? " r" : " -")
          + (write ? "w" : "-") + (exec ? "x" : "-"));
    }
    // Get page table entry
    int pte = getPTE(addr);
    for (int i = 0; i < num_pages; i++) {
      // Check pages aren't already allocated
      if (getPage(pte + i) != null) {
        throw new Error("Memory map of already mapped location addr=0x"
            + Integer.toHexString(addr) + " len=" + len);
      }
      
      readableMemory[pte+i]   = read  ? pages[i] : null;
      writableMemory[pte+i]   = write ? pages[i] : null;
      executableMemory[pte+i] = exec  ? pages[i] : null;
    }
    return addr;
  }

  /**
   * Returns the page currently mapped at the given page table entry.
   * 
   * @param pte
   *  The page table entry, for which a page is to be retrieved.
   * @return
   *  The page mapped at the given page table entry or null, if no page is currently mapped
   *  to that entry.
   */
  private int[] getPage(int pte) {
    
    if (readableMemory[pte] != null)
      return readableMemory[pte];
    
    if (writableMemory[pte] != null)
      return writableMemory[pte];
    
    if (executableMemory[pte] != null)
      return executableMemory[pte];
    
    return null;
  }

  @Override
  public void changeProtection(int address, int len, boolean newRead, boolean newWrite, boolean newExec) {
    
    while (len > 0) {
      int pte = getPTE(address);
      int[] page = getPage(pte);
      
      if (page == null)
        throw new SegmentationFault(address);
      
      readableMemory[pte]   = newRead  ? page : null;
      writableMemory[pte]   = newWrite ? page : null;
      executableMemory[pte] = newExec  ? page : null;
      
      address += PAGE_SIZE;
      len -= PAGE_SIZE;
    }
  }

  /**
   * Read an int from RandomAccessFile ensuring that a byte swap isn't performed
   * 
   * @param file
   *          file to read from
   * @return native endian read int
   */
  protected int readInt(RandomAccessFile file) throws java.io.IOException {

    return file.readByte() << 24 | (file.readUnsignedByte() << 16)
        | (file.readUnsignedByte() << 8) | (file.readUnsignedByte());
  }

  /**
   * Map a page of memory from file
   * 
   * @param file
   *          the file map in from
   * @param addr
   *          the address to map or NULL if don't care
   * @param len
   *          the amount of memory to map
   * @param read
   *          is the page readable
   * @param write
   *          is the page writable
   * @param exec
   *          is the page executable
   */
  public final int map(RandomAccessFile file, long offset, int addr, int len,
      boolean read, boolean write, boolean exec) throws MemoryMapException {
    // Check address is page aligned
    if ((addr % PAGE_SIZE) != 0) {
      MemoryMapException.unalignedAddress(addr);
    }
    // Check file offset is page aligned
    /*if ((offset % PAGE_SIZE) != 0) {
      MemoryMapException.unalignedFileOffset(offset);
    }*/
    
    if (DBT_Options.debugRuntime) {
      System.out.println("Mapping file " + file + " offset=" + offset
          + " addr=0x" + Integer.toHexString(addr) + " len=" + len
          + (read ? " r" : " -") + (write ? "w" : "-") + (exec ? "x" : "-"));
    }
    addr = map(addr, len, read, write, exec);
    
    try {  
      file.seek(offset);
      for (int i = 0; i < len; i += 4) {
        
        int[] page = getPage(getPTE(addr + i));
        page[getOffset(addr + i)] = readInt(file);
      }
      
      return addr;
      
    } 
    catch (java.io.IOException e) {
      throw new Error(e);
    }
  }

  /**
   * Unmap a page of memory
   * 
   * @param addr
   *          the address to unmap
   * @param len
   *          the amount of memory to unmap
   */
  public final void unmap(int addr, int len) {
    for (int i = 0; i < len; i += PAGE_SIZE) {
      
      int pte = getPTE(addr + i);
      
      if (getPage(pte) != null) {
        readableMemory[pte] = null;
        writableMemory[pte] = null;
        executableMemory[pte] = null;
      }
      else  {
        throw new Error("Unmapping memory that's not mapped addr=0x"
            + Integer.toHexString(addr) + " len=" + len);
      }
    }
  }

  /**
   * Perform a 32bit load where addr is word aligned
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  protected final int loadWordAligned32(int addr) {
    return readableMemory[getPTE(addr)][getOffset(addr)];
  }

  /**
   * Perform a 32bit load where addr is word aligned and executable
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  protected final int loadWordAlignedInstruction32(int addr) {
    return executableMemory[getPTE(addr)][getOffset(addr)];
  }

  /**
   * Perform a byte load where the sign extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the sign extended result
   */
  public int loadSigned8(int addr) {
    return (loadWordAligned32(addr) << ((addr & 0x3) << 3)) >> 24;
    // switch(addr & 3) {
    // default:
    // return (loadWordAligned32(addr) << 24) >> 24;
    // case 1:
    // return (loadWordAligned32(addr) << 16) >> 24;
    // case 2:
    // return (loadWordAligned32(addr) << 8) >> 24;
    // case 3:
    // return loadWordAligned32(addr) >> 24;
    // }
  }

  /**
   * Perform a byte load where the zero extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the zero extended result
   */
  public int loadUnsigned8(int addr) {
    return (loadWordAligned32(addr) >> ((3 - (addr & 3)) << 3)) & 0xFF;
    // switch(addr & 3) {
    // default:
    // return loadWordAligned32(addr) & 0xFF;
    // case 1:
    // return (loadWordAligned32(addr) >> 8) & 0xFF;
    // case 2:
    // return (loadWordAligned32(addr) >> 16) & 0xFF;
    // case 3:
    // return loadWordAligned32(addr) >>> 24;
    // }
  }

  /**
   * Perform a 16bit load where the sign extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the sign extended result
   */
  public int loadSigned16(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAligned32(addr) >> 16;
    case 1:
      return (loadWordAligned32(addr) << 8) >> 16;
    case 2:
      return (loadWordAligned32(addr) << 16) >> 16;
    case 3: // 2 loads to deal with spanning int problem
      return ((loadWordAligned32(addr) << 24) >> 16)
          | (loadWordAligned32(addr + 1) >>> 24);
    }
  }

  /**
   * Perform a 16bit load where the zero extended result fills the return value
   * 
   * @param addr
   *          the address of the value to load
   * @return the zero extended result
   */
  public int loadUnsigned16(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAligned32(addr) >>> 16;
    case 1:
      return (loadWordAligned32(addr) >> 8) & 0xFFFF;
    case 2:
      return loadWordAligned32(addr) & 0xFFFF;
    case 3: // 2 loads to deal with spanning int problem
      return ((loadWordAligned32(addr) << 8) & 0xFF00)
          | loadWordAligned32(addr + 1) >>> 24;
    }
  }

  /**
   * Perform a 32bit load
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  public int load32(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAligned32(addr);
    case 1: // 2 loads to deal with spanning int problem
      return (loadWordAligned32(addr + 3) >>> 24)
          | (loadWordAligned32(addr) << 8);
    case 2: // 2 loads to deal with spanning int problem
      return (loadWordAligned32(addr + 2) >>> 16)
          | (loadWordAligned32(addr) << 16);
    case 3: // 2 loads to deal with spanning int problem
      return (loadWordAligned32(addr + 1) >>> 8)
          | (loadWordAligned32(addr) << 24);
    }
  }

  /**
   * Perform a 8bit load from memory that must be executable
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  public int loadInstruction8(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAlignedInstruction32(addr) >>> 24;
    case 1:
      return (loadWordAlignedInstruction32(addr) >> 16) & 0xFF;
    case 2:
      return (loadWordAlignedInstruction32(addr) >> 8) & 0xFF;
    case 3:
      return loadWordAlignedInstruction32(addr) & 0xFF;
    }
  }
  
  @Override
  public int loadInstruction16(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAlignedInstruction32(addr) >>> 16;
    case 1:
      return (loadWordAlignedInstruction32(addr) >> 8) & 0xFFFF;
    case 2:
      return loadWordAlignedInstruction32(addr) & 0xFFFF;
    case 3: // 2 loads to deal with spanning int problem
      return ((loadWordAlignedInstruction32(addr) << 8) & 0xFF00)
          | (loadWordAlignedInstruction32(addr + 1) >>> 25);
    }
  }

  /**
   * Perform a 32bit load from memory that must be executable
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  public int loadInstruction32(int addr) {
    switch (addr & 3) {
    default:
      return loadWordAlignedInstruction32(addr);
    case 1: // 2 loads to deal with spanning int problem
      return (loadWordAlignedInstruction32(addr + 3) >>> 24)
          | (loadWordAlignedInstruction32(addr) << 8);
    case 2: // 2 loads to deal with spanning int problem
      return (loadWordAlignedInstruction32(addr + 2) >>> 16)
          | (loadWordAlignedInstruction32(addr) << 16);
    case 3: // 2 loads to deal with spanning int problem
      return (loadWordAlignedInstruction32(addr + 1) >>> 8)
          | (loadWordAlignedInstruction32(addr) << 24);
    }
  }

  /**
   * Perform a 32bit aligned store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  final protected void storeWordAligned32(int addr, int value) {
    writableMemory[getPTE(addr)][getOffset(addr)] = value;
  }

  /**
   * Perform a 32bit load, from writable memory, where addr is word aligned
   * 
   * @param addr
   *          the address of the value to load
   * @return the result
   */
  final protected int loadWordAligned32forWrite(int addr) {
    return writableMemory[getPTE(addr)][getOffset(addr)];
  }

  /**
   * Perform a byte store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  public void store8(int addr, int value) {
    switch (addr & 3) {
    default:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0x00FFFFFF)
          | (value << 24));
      break;
    case 1:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFF00FFFF)
          | ((value & 0xFF) << 16));
      break;
    case 2:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFFFF00FF)
          | ((value & 0xFF) << 8));
      break;
    case 3:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFFFFFF00)
          | (value & 0xFF));
      break;
    }
  }

  /**
   * Perform a 16bit store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  public void store16(int addr, int value) {
    switch (addr & 3) {
    default:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0x0000FFFF)
          | (value << 16));
      break;
    case 1:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFF0000FF)
          | ((value & 0xFFFF) << 8));
      break;
    case 2:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFFFF0000)
          | (value & 0xFFFF));
      break;
    case 3:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFFFFFF00)
          | ((value >> 8) & 0xFF));
      storeWordAligned32(addr + 1,
          (loadWordAligned32forWrite(addr + 1) & 0x00FFFFFF)
              | (value << 24));
      break;
    }
  }

  /**
   * Perform a 32bit store
   * 
   * @param value
   *          the value to store
   * @param addr
   *          the address of where to store
   */
  public void store32(int addr, int value) {
    switch (addr & 3) {
    default:
      storeWordAligned32(addr, value);
      break;
    case 1:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFF000000)
          | (value >>> 8));
      storeWordAligned32(addr + 3,
          (loadWordAligned32forWrite(addr + 3) & 0x00FFFFFF) | (value << 24));
      break;
    case 2:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFFFF0000)
          | (value >>> 16));
      storeWordAligned32(addr + 2,
          (loadWordAligned32forWrite(addr + 2) & 0x0000FFFF) | (value << 16));
      break;
    case 3:
      storeWordAligned32(addr, (loadWordAligned32forWrite(addr) & 0xFFFFFF00)
          | (value >>> 24));
      storeWordAligned32(addr + 1,
          (loadWordAligned32forWrite(addr + 1) & 0x000000FF) | (value << 8));
      break;
    }
  }
}
