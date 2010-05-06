/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
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
package org.jikesrvm.tools.header_gen;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import org.jikesrvm.VM;
import org.jikesrvm.architecture.StackFrameLayout;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Emit a header file containing declarations required to access VM
 * data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
 */
public class GenerateInterfaceDeclarations {

  static PrintStream out;
  static final GenArch arch;

  static {
    GenArch tmp = null;
    try {
      tmp =
          (GenArch) Class.forName(VM.BuildForIA32 ? "org.jikesrvm.tools.header_gen.GenArch_ia32" : "org.jikesrvm.tools.header_gen.GenArch_ppc").newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);     // we must *not* go on if the above has failed
    }
    arch = tmp;
  }

  static void p(String s) {
    out.print(s);
  }

  static void p(String s, Offset off) {
    if (VM.BuildFor64Addr) {
      out.print(s + off.toLong());
    } else {
      out.print(s + VM.addressAsHexString(off.toWord().toAddress()));
    }
  }

  static void pln(String s) {
    out.println(s);
  }

  static void pln(String s, int i) {
    out.print("#define " + s + " 0x" + Integer.toHexString(i) + "\n");
  }

  static void pln(String s, Address addr) {
    out.print("#define " + s + " ((Address)" + VM.addressAsHexString(addr) + ")\n");
  }

  static void pln(String s, Offset off) {
    out.print("#define " + s + " ((Offset)" + VM.addressAsHexString(off.toWord().toAddress()) + ")\n");
  }

  static void pln() {
    out.println();
  }

  GenerateInterfaceDeclarations() {
  }

  static long bootImageDataAddress = 0;
  static long bootImageCodeAddress = 0;
  static long bootImageRMapAddress = 0;
  static String outFileName;

  private static long decodeLong(String s) {
    if(s.endsWith("L")) {
      s = s.substring(0, s.length()-1);
    }
    return Long.decode(s);
  }

  public static void main(String[] args) throws Exception {

    // Process command line directives.
    //
    for (int i = 0, n = args.length; i < n; ++i) {
      if (args[i].equals("-da")) {              // image address
        if (++i == args.length) {
          System.err.println("Error: The -da flag requires an argument");
          System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        bootImageDataAddress = decodeLong(args[i]);
        continue;
      }
      if (args[i].equals("-ca")) {              // image address
        if (++i == args.length) {
          System.err.println("Error: The -ca flag requires an argument");
          System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        bootImageCodeAddress = decodeLong(args[i]);
        continue;
      }
      if (args[i].equals("-ra")) {              // image address
        if (++i == args.length) {
          System.err.println("Error: The -ra flag requires an argument");
          System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        bootImageRMapAddress = decodeLong(args[i]);
        continue;
      }
      if (args[i].equals("-out")) {              // output file
        if (++i == args.length) {
          System.err.println("Error: The -out flag requires an argument");
          System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
        }
        outFileName = args[i];
        continue;
      }
      System.err.println("Error: unrecognized command line argument: " + args[i]);
      System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }

    if (bootImageDataAddress == 0) {
      System.err.println("Error: Must specify boot image data load address.");
      System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    if (bootImageCodeAddress == 0) {
      System.err.println("Error: Must specify boot image code load address.");
      System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    if (bootImageRMapAddress == 0) {
      System.err.println("Error: Must specify boot image ref map load address.");
      System.exit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    if (outFileName == null) {
      out = System.out;
    } else {
      try {
        // We'll let an unhandled exception throw an I/O error for us.
        out = new PrintStream(new FileOutputStream(outFileName));
      } catch (IOException e) {
        reportTrouble("Caught an exception while opening" + outFileName + " for writing: " + e.toString());
      }
    }

    VM.initForTool();

    emitStuff();
    if (out.checkError()) {
      reportTrouble("an output error happened");
    }
    //    try {
    out.close();              // exception thrown up.
    //    } catch (IOException e) {
    //      reportTrouble("An output error when closing the output: " + e.toString());
    //    }
    System.exit(0);
  }

  private static void reportTrouble(String msg) {
    System.err.println(
        "org.jikesrvm.tools.header_gen.GenerateInterfaceDeclarations: While we were creating InterfaceDeclarations.h, there was a problem.");
    System.err.println(msg);
    System.err.print("The build system will delete the output file");
    if (outFileName != null) {
      System.err.print(" ");
      System.err.print(outFileName);
    }
    System.err.println();

    System.exit(1);
  }

  private static void emitStuff() {
    p("/*------ MACHINE GENERATED by ");
    p("org.jikesrvm.tools.header_gen.GenerateInterfaceDeclarations.java: DO NOT EDIT");
    p("------*/\n\n");

    pln("#define PRODUCTION " + (VM.Production ? "1" : "0"));
    pln();

    pln("#define DEBUG_DUMP_THREAD " + mrp.debug.DebugEntrypoints.DUMP_THREAD);
    pln("#define DEBUG_DUMP_STACK  " + mrp.debug.DebugEntrypoints.DUMP_STACK);
    pln("#define DEBUG_DUMP_METHOD " + mrp.debug.DebugEntrypoints.DUMP_METHOD);
    pln();

    if (VM.PortableNativeSync) {
      pln("#define PORTABLE_NATIVE_SYNC 1");
      pln();
    }

    pln("#ifdef NEED_BOOT_RECORD_DECLARATIONS");
    emitBootRecordDeclarations();
    pln("#endif /* NEED_BOOT_RECORD_DECLARATIONS */");
    pln();

    pln("#ifdef NEED_BOOT_RECORD_INITIALIZATION");
    emitBootRecordInitialization();
    pln("#endif /* NEED_BOOT_RECORD_INITIALIZATION */");
    pln();

    pln("#ifdef NEED_VIRTUAL_MACHINE_DECLARATIONS");
    emitVirtualMachineDeclarations(bootImageDataAddress, bootImageCodeAddress, bootImageRMapAddress);
    pln("#endif /* NEED_VIRTUAL_MACHINE_DECLARATIONS */");
    pln();

    pln("#ifdef NEED_EXIT_STATUS_CODES");
    emitExitStatusCodes();
    pln("#endif /* NEED_EXIT_STATUS_CODES */");
    pln();

    pln("#ifdef NEED_ASSEMBLER_DECLARATIONS");
    emitAssemblerDeclarations();
    pln("#endif /* NEED_ASSEMBLER_DECLARATIONS */");

    pln("#ifdef NEED_MEMORY_MANAGER_DECLARATIONS");
    pln("#define MAXHEAPS " + org.jikesrvm.mm.mminterface.MemoryManager.getMaxHeaps());
    pln("#endif /* NEED_MEMORY_MANAGER_DECLARATIONS */");
    pln();

  }

  static void emitCDeclarationsForJavaType(String Cname, RVMClass cls) {

    // How many instance fields are there?
    //
    RVMField[] allFields = cls.getDeclaredFields();
    int fieldCount = 0;
    for (RVMField field : allFields) {
      if (!field.isStatic()) {
        fieldCount++;
      }
    }

    // Sort them in ascending offset order
    //
    SortableField[] fields = new SortableField[fieldCount];
    for (int i = 0, j = 0; i < allFields.length; i++) {
      if (!allFields[i].isStatic()) {
        fields[j++] = new SortableField(allFields[i]);
      }
    }
    Arrays.sort(fields);

    // Emit field declarations
    //
    p("struct " + Cname + " {\n");

    // Set up cursor - scalars will waste 4 bytes on 64-bit arch
    //
    boolean needsAlign = VM.BuildFor64Addr;
    int addrSize = VM.BuildFor32Addr ? 4 : 8;

    // Header Space for objects
    int startOffset = ObjectModel.objectStartOffset(cls);
    Offset current = Offset.fromIntSignExtend(startOffset);
    for (int i = 0; current.sLT(fields[0].f.getOffset()); i++) {
      pln("  uint32_t    headerPadding" + i + ";\n");
      current = current.plus(4);
    }

    for (int i = 0; i < fields.length; i++) {
      RVMField field = fields[i].f;
      TypeReference t = field.getType();
      Offset offset = field.getOffset();
      String name = field.getName().toString();
      // Align by blowing 4 bytes if needed
      if (needsAlign && current.plus(4).EQ(offset)) {
        pln("  uint32_t    padding" + i + ";");
        current = current.plus(4);
      }
      if (!current.EQ(offset)) {
        System.err.printf("current (%d) and offset (%d) are neither identical nor differ by 4",
                          current.toInt(),
                          offset.toInt());
        System.exit(1);
      }
      if (t.isIntType()) {
        current = current.plus(4);
        p("   uint32_t " + name + ";\n");
      } else if (t.isLongType()) {
        current = current.plus(8);
        p("   uint64_t " + name + ";\n");
      } else if (t.isWordLikeType()) {
        p("   Address " + name + ";\n");
        current = current.plus(addrSize);
      } else if (t.isArrayType() && t.getArrayElementType().isWordLikeType()) {
        p("   Address * " + name + ";\n");
        current = current.plus(addrSize);
      } else if (t.isArrayType() && t.getArrayElementType().isIntType()) {
        p("   unsigned int * " + name + ";\n");
        current = current.plus(addrSize);
      } else if (t.isReferenceType()) {
        p("   Address " + name + ";\n");
        current = current.plus(addrSize);
      } else {
        System.err.println("Unexpected field " + name + " with type " + t);
        throw new RuntimeException("unexpected field type");
      }
    }

    p("};\n");
  }

  static void emitBootRecordDeclarations() {
    RVMClass bootRecord = TypeReference.findOrCreate(org.jikesrvm.runtime.BootRecord.class).resolve().asClass();
    emitCDeclarationsForJavaType("BootRecord", bootRecord);
  }

  // Emit declarations for BootRecord object.
  //
  static void emitBootRecordInitialization() {
    RVMClass bootRecord = TypeReference.findOrCreate(org.jikesrvm.runtime.BootRecord.class).resolve().asClass();
    RVMField[] fields = bootRecord.getDeclaredFields();

    // emit field initializers
    //
    p("static void setLinkage(struct BootRecord* br){\n");
    for (int i = fields.length; --i >= 0;) {
      RVMField field = fields[i];
      if (field.isStatic()) {
        continue;
      }

      String fieldName = field.getName().toString();
      if (fieldName.indexOf("gcspy") > -1 && !VM.BuildWithGCSpy) {
        continue;  // ugh.  NOTE: ugly hack to side-step unconditional inclusion of GCSpy stuff
      }
      int suffixIndex = fieldName.indexOf("IP");
      if (suffixIndex > 0) {
        // java field "xxxIP" corresponds to C function "xxx"
        String functionName = fieldName.substring(0, suffixIndex);
        // e. g.,
        //sysFOOIP = (int) sysFOO;
        p("  br->" + fieldName + " = (Address)" + functionName + ";\n");
      } else if (fieldName.equals("sysJavaVM")) {
        p("  br->" + fieldName + " = (Address)&" + fieldName + ";\n");
      }
    }

    p("}\n");
  }

  // Emit virtual machine class interface information.
  //
  static void emitVirtualMachineDeclarations(long bootImageDataAddress, long bootImageCodeAddress,
                                             long bootImageRMapAddress) {

    // load address for the boot image
    //
    pln("bootImageDataAddress", Address.fromLong(bootImageDataAddress));
    pln("bootImageCodeAddress", Address.fromLong(bootImageCodeAddress));
    pln("bootImageRMapAddress", Address.fromLong(bootImageRMapAddress));

    // values in Constants, from Configuration
    //
    pln("Constants_STACK_SIZE_GUARD", StackFrameLayout.getStackSizeGuard());
    pln("Constants_INVISIBLE_METHOD_ID", StackFrameLayout.getInvisibleMethodID());
    pln("Constants_STACKFRAME_HEADER_SIZE", StackFrameLayout.getStackFrameHeaderSize());
    pln("Constants_STACKFRAME_METHOD_ID_OFFSET", StackFrameLayout.getStackFrameMethodIDOffset());
    pln("Constants_STACKFRAME_FRAME_POINTER_OFFSET", StackFrameLayout.getStackFramePointerOffset());
    pln("Constants_STACKFRAME_SENTINEL_FP", StackFrameLayout.getStackFrameSentinelFP());
    pln();

    // values in ObjectModel
    //
    pln("ObjectModel_ARRAY_LENGTH_OFFSET", ObjectModel.getArrayLengthOffset());
    pln();

    // values in RuntimeEntrypoints
    //
    pln("Runtime_TRAP_UNKNOWN", RuntimeEntrypoints.TRAP_UNKNOWN);
    pln("Runtime_TRAP_NULL_POINTER", RuntimeEntrypoints.TRAP_NULL_POINTER);
    pln("Runtime_TRAP_ARRAY_BOUNDS", RuntimeEntrypoints.TRAP_ARRAY_BOUNDS);
    pln("Runtime_TRAP_DIVIDE_BY_ZERO", RuntimeEntrypoints.TRAP_DIVIDE_BY_ZERO);
    pln("Runtime_TRAP_STACK_OVERFLOW", RuntimeEntrypoints.TRAP_STACK_OVERFLOW);
    pln("Runtime_TRAP_CHECKCAST", RuntimeEntrypoints.TRAP_CHECKCAST);
    pln("Runtime_TRAP_REGENERATE", RuntimeEntrypoints.TRAP_REGENERATE);
    pln("Runtime_TRAP_JNI_STACK", RuntimeEntrypoints.TRAP_JNI_STACK);
    pln("Runtime_TRAP_MUST_IMPLEMENT", RuntimeEntrypoints.TRAP_MUST_IMPLEMENT);
    pln("Runtime_TRAP_STORE_CHECK", RuntimeEntrypoints.TRAP_STORE_CHECK);
    pln();

    // Value in org.mmtk.vm.Constants:
    pln("MMTk_Constants_BYTES_IN_PAGE", org.mmtk.utility.Constants.BYTES_IN_PAGE);

    // fields in RVMThread
    //
    Offset offset = Entrypoints.threadStackField.getOffset();
    pln("RVMThread_stack_offset", offset);
    offset = Entrypoints.stackLimitField.getOffset();
    pln("RVMThread_stackLimit_offset", offset);
    offset = Entrypoints.threadExceptionRegistersField.getOffset();
    pln("RVMThread_exceptionRegisters_offset", offset);
    offset = Entrypoints.jniEnvField.getOffset();
    pln("RVMThread_jniEnv_offset", offset);
    offset = Entrypoints.execStatusField.getOffset();
    pln("RVMThread_execStatus_offset", offset);
    // constants in RVMThread
    pln("RVMThread_TERMINATED", RVMThread.TERMINATED);
    // fields in Registers
    //
    offset = ArchEntrypoints.registersGPRsField.getOffset();
    pln("Registers_gprs_offset", offset);
    offset = ArchEntrypoints.registersFPRsField.getOffset();
    pln("Registers_fprs_offset", offset);
    offset = ArchEntrypoints.registersIPField.getOffset();
    pln("Registers_ip_offset", offset);

    offset = ArchEntrypoints.registersInUseField.getOffset();
    pln("Registers_inuse_offset", offset);

    // fields in JNIEnvironment
    offset = Entrypoints.JNIExternalFunctionsField.getOffset();
    pln("JNIEnvironment_JNIExternalFunctions_offset", offset);

    arch.emitArchVirtualMachineDeclarations();
  }

  // Codes for exit(3).
  static void emitExitStatusCodes() {
    pln("/* Automatically generated from the exitStatus declarations in ExitStatus.java */");
    pln("EXIT_STATUS_EXECUTABLE_NOT_FOUND", VM.EXIT_STATUS_EXECUTABLE_NOT_FOUND);
    pln("EXIT_STATUS_COULD_NOT_EXECUTE", VM.EXIT_STATUS_COULD_NOT_EXECUTE);
    pln("EXIT_STATUS_MISC_TROUBLE", VM.EXIT_STATUS_MISC_TROUBLE);
    pln("EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR", VM.EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    pln("EXIT_STATUS_SYSCALL_TROUBLE", VM.EXIT_STATUS_SYSCALL_TROUBLE);
    pln("EXIT_STATUS_TIMER_TROUBLE", VM.EXIT_STATUS_TIMER_TROUBLE);
    pln("EXIT_STATUS_UNSUPPORTED_INTERNAL_OP", VM.EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
    pln("EXIT_STATUS_UNEXPECTED_CALL_TO_SYS", VM.EXIT_STATUS_UNEXPECTED_CALL_TO_SYS);
    pln("EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION", VM.EXIT_STATUS_DYING_WITH_UNCAUGHT_EXCEPTION);
    pln("EXIT_STATUS_BOGUS_COMMAND_LINE_ARG", VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    pln("EXIT_STATUS_JNI_TROUBLE", VM.EXIT_STATUS_JNI_TROUBLE);
    pln("EXIT_STATUS_BAD_WORKING_DIR", VM.EXIT_STATUS_BAD_WORKING_DIR);
  }

  // Emit assembler constants.
  //
  static void emitAssemblerDeclarations() {
    arch.emitArchAssemblerDeclarations();
  }
}
