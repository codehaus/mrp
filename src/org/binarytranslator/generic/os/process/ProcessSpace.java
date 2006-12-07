/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.process;

import java.util.Hashtable;
import java.io.*;

import com.ibm.jikesrvm.VM_CompiledMethod;
import com.ibm.jikesrvm.VM_CodeArray;
import com.ibm.jikesrvm.opt.ir.OPT_GenerationContext;
import com.ibm.jikesrvm.opt.ir.OPT_HIRGenerator;

import org.binarytranslator.vmInterface.DBT_Trace;
import org.binarytranslator.vmInterface.DynamicCodeRunner;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.branch.BranchLogic;
import org.binarytranslator.generic.memory.Memory;
import org.binarytranslator.generic.memory.MemoryMapException;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.generic.gdbstub.GDBStub;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.arch.x86.os.process.X86_ProcessSpace;
import org.binarytranslator.arch.ppc.os.process.PPC_ProcessSpace;

/**
 * A process space encapsulates a running process. This superclass
 * contains non operating and architecture specific details of the
 * process.
 */
public abstract class ProcessSpace {
  /*
	* Runtime information
	*/

  /**
	* A record of branches to guide translation
	*/
  public BranchLogic branchInfo;

  /**
	* A hashtable containing translated traces of code
	*/
  protected Hashtable codeHash = new Hashtable();

  /**
	* Has a system call been called to terminate the process
	*/
  public boolean finished = false;

  /*
	* Interface to memory
	*/

  /**
	* The memory for the process. As this is user mode code, it is a
	* virtual address space
	*/
  public Memory memory;

  /**
	* Load a 32bit value from memory
	*/
  public int memoryLoad32(int wordAddr) {
	 return memory.load32(wordAddr);
  }
  /**
	* Store a 32bit value to memory
	*/
  public void memoryStore32(int address, int data) {
	 memory.store32(address,data);
  }
    
  /**
	* Load a 16bit value from memory
	*/
  public int memoryLoad16(int hwAddr) {
	 return memory.loadSigned16(hwAddr);
  }
  /**
	* Store a 16bit value to memory
	*/
  public void memoryStore16(int hwAddr, int iValue) {
	 memory.store16(hwAddr,iValue);
  }

  /**
	* Load a 8bit value from memory
	*/
  public byte memoryLoad8(int address) {
	 return (byte)memory.loadSigned8(address);
  }
  /**
	* Store a 8bit value to memory
	*/
  public void memoryStore8(int address, byte data) {
	 memory.store8(address, data);
  }

  /**
	* Read an ASCIIZ string from the process' memory into a Java String
	*/
  public String memoryReadString(int address) {
	 StringBuffer str = new StringBuffer();
	 char c;
	
	 while( (c = (char)memoryLoad8(address++)) != 0 )
		str.append(c);
	
	 return str.toString();
  }

  /**
	* Write a Java string (crudely) to an ASCIIZ string in the process'
	* memory
	*/
  public void memoryWriteString(int byteAddr, String value) {
	 if (value != null) {
		for(int i=0; i < value.length(); i++) {
		  memoryStore8(byteAddr+i, (byte)value.charAt(i));
		}
		memoryStore8(byteAddr+value.length(), (byte)0);
	 }
  }

  /**
   * Map an anonymous page of memory
   * @param addr the address to map or NULL if don't care
   * @param len  the amount of memory to map
   * @param read is the page readable
   * @param write is the page writable
   * @param exec is the page executable
   */
  public int memoryMap(int addr, int len, boolean read, boolean write, boolean exec) throws MemoryMapException
  {
	 return memory.map(addr, len, read, write, exec);
  }

  /**
	* Simulate an munmap system call.
	* @param start start of memory area to unmap.
	* @param length length of area.
	*/
  public int munmap(int start, int length)
  {
	 memory.unmap(start,length);
	 return 0;
  }

  /*
	* Utility functions
	*/

  /**
	* Debug information
	* @param s string of debug information
	*/
  private static void report(String s){
	 if (DBT_Options.debugLoader) {
		System.out.print("ProcessSpace:");
		System.out.println(s);
	 }
  }

  /*
	* Methods
	*/

  /**
	* Create an optimizing compiler HIR code generator suitable for a
	* particular architecture
	* @param context the generation context for the HIR generation
	* @return a HIR generator
	*/
  public abstract OPT_HIRGenerator createHIRGenerator(OPT_GenerationContext context);

  /**
	* Given an ELF binary loader, create the appropriate process space
	* @param elf the elf binary loader
	* @return the appropriate process space
	*/
  public static ProcessSpace createProcessSpaceFromBinary (Loader loader) throws IOException {
	 ProcessSpace result;
	 if (loader.isX86_ISA()) {
		report("X86 ELF Binary");
		result = X86_ProcessSpace.createProcessSpaceFromBinary(loader);
	 }
	 else if(loader.isPPC_ISA()){
		report("PPC ELF Binary");
		result = PPC_ProcessSpace.createProcessSpaceFromBinary(loader);
	 }
	 else {
		throw new IOException("Binary of " + loader.getArchitectureString() + " architecture is unsupported");
	 }
	 return result;
  }
  /**
	* Create a segment
	* @param RandomAccessFile file to read segment data from if file size != 0
	* @param offset file offset
	* @param address location of segment
	* @param filesize size of segment in file
	* @param memsize size of segment in memory
	* @param read is segment readable
	* @param write is segment writable
	* @param exec is segment executable
	*/
  public void createSegment(RandomAccessFile file,
									 long offset,
									 int address,
									 int filesize,
									 int memsize,
									 boolean read,
									 boolean write,
									 boolean exec) throws MemoryMapException {
	 // Sanity check
	 if (memsize < filesize) {
		throw new Error("Segment memory size (" + memsize + ")less than file size (" + filesize + ")");
	 }
	 // Are we mapping anything from a file?
	 if(filesize == 0) {
		// No: map anonymously
		memory.map(address, memsize, read, write, exec);
	 }
	 else {
		// align offset and address
		int alignedAddress;
		long alignedOffset;
		int alignedFilesize;
		if (memory.isPageAligned(address)) {
		  // memory and therefore offset should be aligned
		  alignedAddress = address;
		  alignedOffset = offset;
		  alignedFilesize = filesize;
		}
		else {
		  // Address not aligned
		  alignedAddress = memory.truncateToPage(address);
		  int delta = address - alignedAddress;
		  // adjust offset and length too
		  alignedOffset = offset - delta;
		  alignedFilesize = filesize + delta;
		}
		memory.map(file, alignedOffset, alignedAddress, alignedFilesize, read, write, exec);
		// Do we need to map in some blank pages at the end of the segment?
		if (filesize < memsize) {
		  alignedAddress = memory.truncateToNextPage(address + filesize);
		  memory.map(alignedAddress, memsize-filesize, read, write, exec);
		}
	 }
  }

  /**
	* Initialise the process space
	* @param loader the loader that's created the process space
	* @param pc the entry point
	* @param brk the initial value for the top of BSS
	* @param args command line arguments
	*/
  public abstract void initialise(Loader loader, int pc, int brk, String args[]);

  /**
	* Constructor
	*/
  protected ProcessSpace() {
	 branchInfo = new BranchLogic();
  }

  /**
	* Constructor
	*/
  public ProcessSpace(String[] args) throws IOException {
	 branchInfo = new BranchLogic();
  }


  /**
	* Replace the compiled code for a trace (called by the adaptive
	* system)
	*/
  public synchronized void replaceCompiledTrace(VM_CompiledMethod cm, DBT_Trace trace) {
	 VM_CodeArray code = cm.getInstructions();
	 codeHash.put(new Integer(trace.pc), code);
  }


  public synchronized VM_CodeArray getCodeForPC(int pc) {
	 VM_CodeArray code = (VM_CodeArray)codeHash.get(new Integer(pc));
	 if(code == null) {
		code = translateCode(new DBT_Trace(this, pc));
	 }
	 return code;
  }
    
  private VM_CodeArray translateCode(DBT_Trace trace) {
	 if (DBT_Options.debugRuntime) {
		report("Translating code for 0x" + Integer.toHexString(trace.pc));
	 }	 
	 trace.compile();
	 VM_CompiledMethod cm = trace.getCurrentCompiledMethod();
	 replaceCompiledTrace(cm, trace);
	 return cm.getInstructions();
  }

  /**
   * Record a branch instruction
   */
  public void recordBranch(int location, int destination) {
	 branchInfo.registerBranch(location, destination);
  }

  /**
	* Return as an integer the current instruction's address
	*/
  public abstract int getCurrentInstructionAddress();
  /**
	* Sets the current instruction's address
	*/
  public abstract void setCurrentInstructionAddress(int pc);
  /**
	* Return as an integer the current instruction's address
	*/
  public abstract int getCurrentStackAddress();
  /**
	* Print the stack
	* @param words how many words to print from the stack
	*/
  public void dumpStack(int words) {
	 int stackPtr = getCurrentStackAddress();
	 for(int i=0; i < words; i++) {
		if((i % 4) == 0) {
		  if(i != 0) {
			 System.out.println();
		  }
		  System.out.print("0x" + Integer.toHexString(stackPtr)+":");
		}
		String hexValue = Integer.toHexString(memory.load32(stackPtr));
		System.out.print("   0x");
		for(int j=0; j < (8-hexValue.length()); j++) {
		  System.out.print("0");
		}
		System.out.print(hexValue);
		stackPtr+=4;
	 }
	 System.out.println();
  }
  /**
   * Runtime loop, goes through the binary and looks in the Hashtable
   * codeHash to see if we have already translated/compiled this piece
   * of code, if not it is compiled. The compiled code is then run.
   */
  public void run() {
    if (DBT_Options.debugRuntime) {
      System.out.println("Main: run");
    }
            
    // The current block of compiled code. 
    VM_CodeArray code;  

    if(DBT_Options.debugPS) {
      System.out.println("***** INITIAL PROCESS SPACE *****\n" + this);
		System.out.println(this);
      //dumpStack(20);
    }

	 if(DBT_Options.gdbStub == false) {
		try {
		  // interpretFrom(); // Interpreter - experimental
		  while(finished == false) {
			 // Get the compiled code
			 code = getCodeForPC(getCurrentInstructionAddress());
			 // Run the compiled code.
			 setCurrentInstructionAddress(DynamicCodeRunner.invokeCode(code, this));
		  }
		}
		catch(BadInstructionException e) {
		  System.out.println(e.toString());
		}
	 }
	 else {
		GDBStub gdbStub = new GDBStub(DBT_Options.gdbStubPort, this);
		gdbStub.run();
	 }
  }

  /**
	* Entry point for system calls
	*/
  public abstract void doSysCall();

  /**
	* Method to return environment variables
	* @return an array of environment variable strings
	*/
  protected String[] getEnvironmentVariables() {
	 throw new Error("TODO!");
	 /*
	 if (!DBT_Options.loadEnv) {
		Process printenv = Runtime.exec("/usr/bin/printenv");
		InputStream variables = new DataInputStream(printenv.getInputStream());
		variables.readUTF();		
	 }
	 */
  }

  /* GDB stub interface */
  /**
	* Read a register and turn into a byte array conforming to the
	* endianness of the architecture
	*/
  public abstract byte[] readRegisterGDB(int regNum);
  /**
	* Run a single instruction
	*/
  public abstract void runOneInstruction() throws BadInstructionException;
  /**
	* Has frame base register?
	*/
  public boolean hasFrameBaseRegister() {
	 return false;
  }
  /**
	* Get the value of the frame base register
	*/
  public int getGDBFrameBaseRegister() {
	 return -1;
  }
  /**
	* Get the value of the frame base register
	*/
  public abstract int getGDBStackPointerRegister();
  /**
	* Get the value of the frame base register
	*/
  public abstract int getGDBProgramCountRegister();
}
