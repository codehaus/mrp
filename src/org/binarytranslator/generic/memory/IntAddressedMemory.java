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
import org.binarytranslator.DBT_Options;
import org.binarytranslator.vmInterface.TranslationHelper;
import org.jikesrvm.VM_Configuration;

/**
 * IntAddressedMemory:
 *
 * Memory is arrays of ints, no endian conversion is performed.
 *
 * The string helloworld following by the int
 * of 0xcafebabe appear as:
 *
 * <pre>
 *               Byte Address
 * Int Address | 0 | 1 | 2 | 3 |
 *------------------------------
 * .........0c | ca| fe| ba| be|
 * .........08 |'l'|'d'| \n| \0|
 * .........04 |'o'|'W'|'o'|'r'|
 * .........00 |'H'|'e'|'l'|'l'|
 * </pre>
 */
public class IntAddressedMemory extends CallBasedMemory {
  /** 
	* The size of pages
	*/
  protected static final int PAGE_SIZE=4096;
  /**
	* Bits in offset
	*/
  protected static final int OFFSET_BITS=12;
  /** 
	* The number of pages
	*/
  protected static final int NUM_PAGES=0x100000;
  /**
	* The maximum amount of RAM available
	*/
  protected static final long MAX_RAM=(long)PAGE_SIZE*(long)NUM_PAGES;
  /**
	* The memory backing store
	*/
  private int readableMemory[][];
  private int writableMemory[][];
  private int executableMemory[][];
  /**
	* Constructor - used when this is the instatiated class
	*/
  public IntAddressedMemory() {
	 super("org/binarytranslator/IntAddressedMemory");
	 readableMemory = new int[NUM_PAGES][];
	 writableMemory = new int[NUM_PAGES][];
	 executableMemory = new int[NUM_PAGES][];
  }
  /**
	* Constructor - used when deriving a class
	* @param className the name of the over-riding class
	*/
  protected IntAddressedMemory(String className) {
	 super(className);
	 readableMemory = new int[NUM_PAGES][];
	 writableMemory = new int[NUM_PAGES][];
	 executableMemory = new int[NUM_PAGES][];
  }
  /**
	* Return the offset part of the address
	*/
  protected int getOffset(int address) {
	 return (address & (PAGE_SIZE - 1)) >>> 2;
  }
  /**
	* Return the page table entry part of the address
	*/
  private static final int getPTE(int address) {
	 return address >>> OFFSET_BITS;
  }
  /**
	* Is the given address aligned on a page boundary?
	* @param addr the address to check
	* @return whether the address is aligned
	*/
  public boolean isPageAligned(int addr) {
	 return (addr % PAGE_SIZE) == 0;
  }
  /**
	* Make the given address page aligned to the page beneath it
	* @param addr the address to truncate
	* @return the truncated address
	*/
  public int truncateToPage(int addr) {
	 return (addr >> OFFSET_BITS) << OFFSET_BITS;
  }
  /**
	* Make the given address page aligned to the page above it
	* @param addr the address to truncate
	* @return the truncated address
	*/
  public int truncateToNextPage(int addr) {
	 return ((addr + PAGE_SIZE - 1) >> OFFSET_BITS) << OFFSET_BITS;
  }
  /**
	* Find free consecutive pages
	* @param pages the number of pages required
	* @return the address found
	*/
  private final int findFreePages(int pages) {
	 starting_page_search:
	 for(int i=0; i < NUM_PAGES; i++) {
		if((readableMemory[i] == null) &&
			(writableMemory[i] == null) &&
			(executableMemory[i] == null)) {
		  int start = i;
		  int end = i+pages;
		  for(; i <= end; i++) {
			 if((readableMemory[i] != null) ||
				 (writableMemory[i] != null) ||
				 (executableMemory[i] != null)) {
				continue starting_page_search;
			 }
		  }
		  return start << OFFSET_BITS;
		}
	 }
	 throw new Error("No mappable consecutive pages found for an anonymous map of size" + (pages*PAGE_SIZE));
  }
  /**
	* Map an anonymous page of memory
	* @param addr the address to map or NULL if don't care
	* @param len  the amount of memory to map
	* @param read is the page readable
	* @param write is the page writable
	* @param exec is the page executable
	*/
  public int map(int addr, int len, boolean read, boolean write, boolean exec) throws MemoryMapException {
	 // Check address is page aligned
	 if((addr % PAGE_SIZE) != 0) {
		MemoryMapException.unalignedAddress(addr);
	 }
	 // Calculate number of pages
	 int num_pages = (len + PAGE_SIZE - 1) / PAGE_SIZE;
	 // Create memory
	 int pages[][] = new int[num_pages][PAGE_SIZE/4];
	 // Find address if not specified
	 if (addr == 0) {
		addr = findFreePages(num_pages);
	 }
	 if(DBT_Options.debugRuntime) {
		System.out.println("Anonymous mapping: addr=0x" +Integer.toHexString(addr) +
								 " len=" + len +
								 (read ? " r" : " -")+ (write ? "w" : "-")+ (exec ? "x" : "-"));
	 }
	 // Get page table entry
	 int pte = getPTE(addr);
	 for(int i=0; i<num_pages; i++) {
		// Check pages aren't already allocated
		if((readableMemory[pte+i] != null)||
			(writableMemory[pte+i] != null)||
			(executableMemory[pte+i] != null)) {
		  throw new Error("Memory map of already mapped location addr=0x"+Integer.toHexString(addr)+" len="+len);
		}
		// Allocate pages
		if (read) {
		  readableMemory[pte+i] = pages[i];
		}
		if (write) {
		  writableMemory[pte+i] = pages[i];
		}
		if (exec) {
		  executableMemory[pte+i] = pages[i];
		}		
	 }
	 return addr;
  }
  /**
	* Read an int from RandomAccessFile ensuring that a byte swap isn't performed
	* @param file file to read from
	* @return native endian read int
	*/
  protected int readInt(RandomAccessFile file) throws java.io.IOException {
      if(VM_Configuration.BuildForPowerPC) {
	  return file.readInt(); // NB this will always read in big-endian format
      } else {
	  return file.readUnsignedByte() | (file.readUnsignedByte() << 8) | (file.readUnsignedByte() << 16)| (file.readByte() << 24);
      }
  }
  /**
	* Map a page of memory from file
	* @param file the file map in from
	* @param addr the address to map or NULL if don't care
	* @param len  the amount of memory to map
	* @param read is the page readable
	* @param write is the page writable
	* @param exec is the page executable
	*/
  public final int map(RandomAccessFile file, long offset, int addr, int len, boolean read, boolean write, boolean exec) throws MemoryMapException {
	 // Check address is page aligned
	 if((addr % PAGE_SIZE) != 0) {
		MemoryMapException.unalignedAddress(addr);
	 }
	 // Check file offset is page aligned
	 if((offset % PAGE_SIZE) != 0) {
		MemoryMapException.unalignedFileOffset(offset);
	 }
	 if(DBT_Options.debugRuntime) {
		System.out.println("Mapping file " + file + " offset=" + offset +
								 " addr=0x" +Integer.toHexString(addr) +
								 " len=" + len +
								 (read ? " r" : " -")+ (write ? "w" : "-")+ (exec ? "x" : "-"));
	 }
	 addr = map(addr,len,read,write,exec);
	 try {
		file.seek(offset);
		for(int i=0; i < len; i+=4) {
		  int value = readInt(file);
		  if(read) {
			 readableMemory[getPTE(addr + i)][getOffset(addr + i)] = value;
		  }
		  else if (write) {
			 writableMemory[getPTE(addr + i)][getOffset(addr + i)] = value;
		  }
		  else if (exec) {
			 executableMemory[getPTE(addr + i)][getOffset(addr + i)] = value;
		  }
		}
		return addr;
	 }
	 catch (java.io.IOException e) {
		throw new Error(e);
	 }
  }
  /**
	* Unmap a page of memory
	* @param addr the address to unmap
	* @param len  the amount of memory to unmap
	*/
  public final void unmap(int addr, int len) {
	 for(int i=0; i < len; i+=PAGE_SIZE) {
		boolean unmapped_something = false;
		if(readableMemory[getPTE(addr+i)] != null){
		  readableMemory[getPTE(addr+i)] = null;
		  unmapped_something = true;
		}
		if(readableMemory[getPTE(addr+i)] != null){
		  writableMemory[getPTE(addr+i)] = null;
		  unmapped_something = true;
		}
		if(readableMemory[getPTE(addr+i)] != null){
		  executableMemory[getPTE(addr+i)] = null;
		  unmapped_something = true;
		}
		if(unmapped_something == false) {
		  throw new Error("Unmapping memory that's not mapped addr=0x"+Integer.toHexString(addr)+ " len=" + len);
		}
	 }
  }
  /**
	* Perform a 32bit load where addr is word aligned
	* @param addr the address of the value to load
	* @return the result
	*/
  protected final int loadWordAligned32(int addr) {
	 return readableMemory[getPTE(addr)][getOffset(addr)];
  }
  /**
	* Perform a 32bit load where addr is word aligned and executable
	* @param addr the address of the value to load
	* @return the result
	*/
  protected final int loadWordAlignedInstruction32(int addr) {
	 return executableMemory[getPTE(addr)][getOffset(addr)];
  }
  /**
	* Perform a byte load where the sign extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the sign extended result
	*/
  public int loadSigned8(int addr) {
	 return (loadWordAligned32(addr) << ((3-(addr & 0x3)) << 3)) >> 24;
// 	 switch(addr & 3) {
// 	 default:
// 		return (loadWordAligned32(addr) << 24) >> 24;
// 	 case 1:
// 		return (loadWordAligned32(addr) << 16) >> 24;
// 	 case 2:
// 		return (loadWordAligned32(addr) << 8) >> 24;
// 	 case 3:
// 		return loadWordAligned32(addr) >> 24;
// 	 }
  }
  /**
	* Perform a byte load where the zero extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the zero extended result
	*/
  public int loadUnsigned8(int addr) {
	 return (loadWordAligned32(addr) >> ((addr & 3) << 3)) & 0xFF;
// 	 switch(addr & 3) {
// 	 default:
// 		return loadWordAligned32(addr) & 0xFF;
// 	 case 1:
// 		return (loadWordAligned32(addr) >> 8) & 0xFF;
// 	 case 2:
// 		return (loadWordAligned32(addr) >> 16) & 0xFF;
// 	 case 3:
// 		return loadWordAligned32(addr) >>> 24;
// 	 }
  }
  /**
	* Perform a 16bit load where the sign extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the sign extended result
	*/
  public int loadSigned16(int addr) {
	 switch(addr & 3) {
	 default:
		return (loadWordAligned32(addr) << 16) >> 16;
	 case 1:
		return (loadWordAligned32(addr) << 8) >> 16;
	 case 2:
		return loadWordAligned32(addr) >> 16;
	 case 3: // 2 loads to deal with spanning int problem
		return  (loadWordAligned32(addr) >>> 24) | ((loadWordAligned32(addr+1) << 24) >>  16);
	 }
  }
  /**
	* Perform a 16bit load where the zero extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the zero extended result
	*/
  public int loadUnsigned16(int addr) {
	 switch(addr & 3) {
	 default:
		return loadWordAligned32(addr) >>> 16;
	 case 1:
		return (loadWordAligned32(addr) >> 8) & 0xFFFF;
	 case 2:
		return loadWordAligned32(addr) & 0xFFFF;
	 case 3: // 2 loads to deal with spanning int problem
		return  (loadWordAligned32(addr) >>> 24) | ((loadWordAligned32(addr+1) << 8)& 0xFF00);
	 }
  }
  /**
	* Perform a 32bit load
	* @param addr the address of the value to load
	* @return the result
	*/
  public int load32(int addr) {
	 switch(addr & 3) {
	 default:
		return loadWordAligned32(addr);
	 case 1: // 2 loads to deal with spanning int problem
		return  ((loadWordAligned32(addr+3) & 0xFF) << 24) | (loadWordAligned32(addr) >>> 8);
	 case 2: // 2 loads to deal with spanning int problem
		return  ((loadWordAligned32(addr+2) & 0xFFFF) << 16) | (loadWordAligned32(addr) >>> 16);
	 case 3: // 2 loads to deal with spanning int problem
		return  ((loadWordAligned32(addr+1) & 0xFFFFFF) << 8) | (loadWordAligned32(addr) >>> 24);
	 }
  }
  /**
	* Perform a 8bit load from memory that must be executable
	* @param addr the address of the value to load
	* @return the result
	*/
  public int loadInstruction8(int addr) {
	 switch(addr & 3) {
	 default:
		return loadWordAlignedInstruction32(addr) & 0xFF;
	 case 1:
		return (loadWordAlignedInstruction32(addr) >> 8) & 0xFF;
	 case 2:
		return (loadWordAlignedInstruction32(addr) >> 16) & 0xFF;
	 case 3:
		return loadWordAlignedInstruction32(addr) >>> 24;
	 }
  }
  /**
	* Perform a 32bit load from memory that must be executable
	* @param addr the address of the value to load
	* @return the result
	*/
  public int loadInstruction32(int addr) {
	 switch(addr & 3) {
	 default:
		return loadWordAlignedInstruction32(addr);
	 case 1: // 2 loads to deal with spanning int problem
		return  ((loadWordAlignedInstruction32(addr+3) & 0xFF) << 24) | (loadWordAlignedInstruction32(addr) >>> 8);
	 case 2: // 2 loads to deal with spanning int problem
		return  ((loadWordAlignedInstruction32(addr+2) & 0xFFFF) << 16) | (loadWordAlignedInstruction32(addr) >>> 16);
	 case 3: // 2 loads to deal with spanning int problem
		return  ((loadWordAlignedInstruction32(addr+1) & 0xFFFFFF) << 8) | (loadWordAlignedInstruction32(addr) >>> 24);
	 }
  }
  /**
	* Perform a 32bit aligned store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  final protected void storeWordAligned32(int addr, int value) {
	 writableMemory[getPTE(addr)][getOffset(addr)] = value;
  }
  /**
	* Perform a 32bit load, from writable memory, where addr is word aligned
	* @param addr the address of the value to load
	* @return the result
	*/
  final protected int loadWordAligned32forWrite(int addr) {
	 return writableMemory[getPTE(addr)][getOffset(addr)];
  }
  /**
	* Perform a byte store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store8(int addr, int value) {
	 switch(addr & 3) {
	 default:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFFFF00) | (value & 0xFF));
		break;
	 case 1:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFF00FF) | ((value & 0xFF) << 8));
		break;
	 case 2:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFF00FFFF) | ((value & 0xFF) << 16));
		break;
	 case 3:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x00FFFFFF) | (value << 24));
		break;
	 }
  }
  /**
	* Perform a 16bit store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store16(int addr, int value) {
	 switch(addr & 3) {
	 default:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFFFF0000) | (value & 0xFFFF));
		break;
	 case 1:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0xFF0000FF) | ((value & 0xFFFF) << 8));
		break;
	 case 2:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x0000FFFF) | (value << 16));
		break;
	 case 3:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x00FFFFFF) | (value << 24));
		storeWordAligned32(addr+1,(loadWordAligned32forWrite(addr+1) & 0xFFFFFF00) | ((value >> 8) & 0xFF));
		break;
	 }
  }
  /**
	* Perform a 32bit store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store32(int addr, int value) {
	 switch(addr & 3) {
	 default:
		storeWordAligned32(addr,value);
		break;
	 case 1:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x000000FF) | (value << 8));
		storeWordAligned32(addr+3,(loadWordAligned32forWrite(addr+3) & 0xFFFFFF00) | (value >>> 24));
		break;
	 case 2:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x0000FFFF) | (value << 16));
		storeWordAligned32(addr+2,(loadWordAligned32forWrite(addr+2) & 0xFFFF0000) | (value >>> 16));
		break;
	 case 3:
		storeWordAligned32(addr,(loadWordAligned32forWrite(addr) & 0x00FFFFFF) | (value << 24));
		storeWordAligned32(addr+1,(loadWordAligned32forWrite(addr+1) & 0xFF000000) | (value >>> 8));
		break;
	 }
  }
}
