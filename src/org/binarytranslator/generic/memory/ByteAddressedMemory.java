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
import java.nio.channels.FileChannel;
import org.binarytranslator.DBT_Options;

/**
 * ByteAddressedMemory:
 *
 * Memory is arrays of bytes, no endian conversion is performed.
 *
 * The string helo followed by the int of 0xcafebabe appear as:
 *
 * <pre>
 * Byte Address|    
 *------------------
 * .........07 | be|
 * .........06 | ba|
 * .........05 | fe|
 * .........04 | ca|
 * .........03 |'o'|
 * .........02 |'l'|
 * .........01 |'e'|
 * .........00 |'H'|
 * </pre>
 */
public class ByteAddressedMemory extends CallBasedMemory {
  /** 
	* The size of pages
	*/
  private static final int PAGE_SIZE=4096;
  /**
	* Bits in offset
	*/
  private static final int OFFSET_BITS=12;
  /** 
	* The number of pages
	*/
  private static final int NUM_PAGES=0x100000;
  /**
	* The maximum amount of RAM available
	*/
  private static final long MAX_RAM=(long)PAGE_SIZE*(long)NUM_PAGES;
  /**
	* The memory backing store
	*/
  private byte readableMemory[][];
  private byte writableMemory[][];
  private byte executableMemory[][];
  /**
	* Do we have more optimal nio mmap operation?
	*/
  private boolean HAVE_java_nio_FileChannelImpl_nio_mmap_file = false;
  /**
	* Constructor - used when this is the instatiated class
	*/
  public ByteAddressedMemory() {
	 super("org/binarytranslator/generic/memory/ByteAddressedMemory");
	 readableMemory = new byte[NUM_PAGES][];
	 writableMemory = new byte[NUM_PAGES][];
	 executableMemory = new byte[NUM_PAGES][];
  }
  /**
	* Constructor - used when deriving a class
	* @param className the name of the over-riding class
	*/
  protected ByteAddressedMemory(String className) {
	 super(className);
	 readableMemory = new byte[NUM_PAGES][];
	 writableMemory = new byte[NUM_PAGES][];
	 executableMemory = new byte[NUM_PAGES][];
  }
  /**
	* Return the offset part of the address
	*/
  private static final int getOffset(int address) {
	 return address & (PAGE_SIZE - 1);
  }
  /**
	* Return the page table entry part of the address
	*/
  private static final int getPTE(int address) {
	 return address >>> OFFSET_BITS;
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
	 // Create memory
	 int num_pages = (len + PAGE_SIZE - 1) / PAGE_SIZE;
	 byte pages[][] = new byte[num_pages][PAGE_SIZE];
	 // Find address if not specified
	 if (addr == 0) {
		addr = findFreePages(num_pages);
	 }
	 if(DBT_Options.debugMemory) {
		System.err.println("Anonymous mapping: addr=0x" +Integer.toHexString(addr) +
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
	* Map a page of memory from file
	* @param file the file map in from
	* @param addr the address to map or NULL if don't care
	* @param len  the amount of memory to map
	* @param read is the page readable
	* @param write is the page writable
	* @param exec is the page executable
	*/
  public int map(RandomAccessFile file, long offset, int addr, int len, boolean read, boolean write, boolean exec) throws MemoryMapException {
	 // Check address is page aligned
	 if((addr % PAGE_SIZE) != 0) {
		MemoryMapException.unalignedAddress(addr);
	 }
	 // Check file offset is page aligned
	 if((offset % PAGE_SIZE) != 0) {
		MemoryMapException.unalignedFileOffset(offset);
	 }
	 // Calculate number of pages
	 int num_pages = (len + PAGE_SIZE - 1) / PAGE_SIZE;
	 // Find address if not specified
	 if (addr == 0) {
		addr = findFreePages(num_pages);
	 }
	 if(DBT_Options.debugMemory) {
		System.err.println("Mapping file " + file + " offset=" + offset +
								 " addr=0x" +Integer.toHexString(addr) +
								 " len=" + len +
								 (read ? " r" : " -")+ (write ? "w" : "-")+ (exec ? "x" : "-"));
	 }
	 try {
		// Get page table entry
		int pte = getPTE(addr);
		// Can we optimise the reads to use mmap?
		if (!HAVE_java_nio_FileChannelImpl_nio_mmap_file) {
		  // Sub-optimal
		  file.seek(offset);
		  for(int i=0; i < num_pages; i++) {
			 // Check pages aren't already allocated
			 if((readableMemory[pte+i] != null)||
				 (writableMemory[pte+i] != null)||
				 (executableMemory[pte+i] != null)) {
				throw new Error("Memory map of already mapped location addr=0x"+Integer.toHexString(addr)+" len="+len);
			 }
			 // Allocate page
			 byte page[] = new byte[PAGE_SIZE];
			 if (i == 0) { // first read, start from offset upto a page length
				file.read(page, getOffset(addr), PAGE_SIZE-getOffset(addr));
			 }
			 else if (i == (num_pages-1)) { // last read
				file.read(page, 0, ((len - getOffset(addr)) % PAGE_SIZE));
			 }
			 else {
				file.read(page);
			 }
			 if(read) {
				readableMemory[pte+i] = page;
			 }
			 if(write) {
				writableMemory[pte+i] = page;
			 }
			 if(exec) {
				executableMemory[pte+i] = page;
			 }
		  }
		}
		else {
		  for(int i=0; i < num_pages; i++) {
			 // Check pages aren't already allocated
			 if((readableMemory[pte+i] != null)||
				 (writableMemory[pte+i] != null)||
				 (executableMemory[pte+i] != null)) {
				throw new Error("Memory map of already mapped location addr=0x"+Integer.toHexString(addr)+" len="+len);
			 }
			 // Allocate page
			 if(read && write) {
				readableMemory[pte+i] = file.getChannel().map(FileChannel.MapMode.READ_WRITE, offset+(i*PAGE_SIZE), PAGE_SIZE).array();
				writableMemory[pte+i] = readableMemory[pte+i];
				if(exec) {
				  executableMemory[pte+i] = readableMemory[pte+i];
				}
			 }
			 else if (read) {
				readableMemory[pte+i] = file.getChannel().map(FileChannel.MapMode.READ_ONLY, offset+(i*PAGE_SIZE), PAGE_SIZE).array();
				if(exec) {
				  executableMemory[pte+i] = readableMemory[pte+i];
				}
			 }
			 else if (exec) {
				executableMemory[pte+i] = file.getChannel().map(FileChannel.MapMode.READ_ONLY, offset+(i*PAGE_SIZE), PAGE_SIZE).array();
			 }
			 else {
				throw new Error("Unable to map address 0x" + Integer.toHexString(addr) +
									 " with permissions " + (read ? "r" : "-") + (write ? "w" : "-") + (exec ? "x" : "-"));
			 }
		  }
		}
		return addr;
	 }
	 catch(java.io.IOException e){
		throw new Error(e);
	 }
  }
  /**
	* Unmap a page of memory
	* @param addr the address to unmap
	* @param len  the amount of memory to unmap
	*/
  public void unmap(int addr, int len) {
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
	* Make the given address page aligned to the page beneath it
	* @param addr the address to truncate
	* @return the truncated address
	*/
  public int truncateToNextPage(int addr) {
	 return ((addr + PAGE_SIZE - 1) >> OFFSET_BITS) << OFFSET_BITS;
  }
  /**
	* Perform a byte load where the sign extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the sign extended result
	*/
  final public int loadSigned8(int addr) {
	 try {
		if(DBT_Options.debugMemory)
		  System.err.println("LoadS8 address: 0x"+Integer.toHexString(addr) + " val: " + readableMemory[getPTE(addr)][getOffset(addr)]);
		return readableMemory[getPTE(addr)][getOffset(addr)];
	 }
	 catch(NullPointerException e) {
		System.err.println("Null pointer exception at address: 0x"+Integer.toHexString(addr));
		throw e;
	 }
  }
  /**
	* Perform a byte load where the zero extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the zero extended result
	*/
  final public int loadUnsigned8(int addr) {
	 try {
		if(DBT_Options.debugMemory)
		  System.err.println("LoadU8 address: 0x"+Integer.toHexString(addr) + " val: " + readableMemory[getPTE(addr)][getOffset(addr)]);
		return readableMemory[getPTE(addr)][getOffset(addr)] & 0xFF;
	 }
	 catch(NullPointerException e) {
		System.err.println("Null pointer exception at address: 0x"+Integer.toHexString(addr));
		throw e;
	 }
  }
  /**
	* Perform a 16bit load where the sign extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the sign extended result
	*/
  public int loadSigned16(int addr) {
	 return (loadSigned8(addr+1) << 8) | loadUnsigned8(addr);
  }
  /**
	* Perform a 16bit load where the zero extended result fills the
	* return value
	* @param addr the address of the value to load
	* @return the zero extended result
	*/
  public int loadUnsigned16(int addr) {
	 return (loadUnsigned8(addr+1) << 8) | loadUnsigned8(addr);
  }
  /**
	* Perform a 32bit load
	* @param addr the address of the value to load
	* @return the result
	*/
  public int load32(int addr) {
	 return (loadSigned8(addr+3) << 24) | (loadUnsigned8(addr+2) << 16) | (loadUnsigned8(addr+1) << 8) | loadUnsigned8(addr);
  }
  /**
	* Perform a 8bit load from memory that must be executable
	* @param addr the address of the value to load
	* @return the result
	*/
  public int loadInstruction8(int addr) {
	 if(DBT_Options.debugMemory)
		System.err.println("LoadI8 address: 0x"+Integer.toHexString(addr) + " val: " + executableMemory[getPTE(addr)][getOffset(addr)]);
	 return executableMemory[getPTE(addr)][getOffset(addr)] & 0xFF;
  }
  /**
	* Perform a 32bit load from memory that must be executable
	* @param addr the address of the value to load
	* @return the result
	*/
  public int loadInstruction32(int addr) {
	 return (loadInstruction8(addr+3) << 24) | (loadInstruction8(addr+2) << 16) | (loadInstruction8(addr+1) << 8) | loadInstruction8(addr);
  }
  /**
	* Perform a byte store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public final  void store8(int addr, int value) {
	 if(DBT_Options.debugMemory)
		System.err.println("Store8 address: 0x"+Integer.toHexString(addr) + " val: 0x" + Integer.toHexString(value & 0xFF));
	 writableMemory[getPTE(addr)][getOffset(addr)] = (byte)value;
  }
  /**
	* Perform a 16bit store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store16(int addr, int value) {
	 store8(addr+1, value >> 8);
	 store8(addr,value);
  }
  /**
	* Perform a 32bit store
	* @param value the value to store
	* @param addr the address of where to store
	*/
  public void store32(int addr, int value) {
	 store8(addr+3, value >> 24);
	 store8(addr+2, value >> 16);
	 store8(addr+1, value >> 8);
	 store8(addr,value);
  }
}
