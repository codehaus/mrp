/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.loader.elf;

import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.memory.Memory;
import org.binarytranslator.generic.memory.MemoryMapException;
import org.binarytranslator.DBT_Options;

import java.io.*;

public class ELF_Loader extends Loader {
  /*
   * Instance variables
   */

  /**
   * Wrapper class used for reading the ELF file with the required endianness
   */
  private ELF_BinaryReader reader;

  /**
   * Header of ELF file
   */
  private ELF_Header elfHeader;

  /**
   * Program segment headers
   */
  private ELF_ProgramSegmentHeader segmentHeaders[];

  /*
   * Utility functions
   */

  /**
   * Debug information
   * @param s string of debug information
   */
  private static void report(String s) {
    if (DBT_Options.debugLoader) {
      System.out.print("ELF Loader:");
      System.out.println(s);
    }
  }

  /*
   * Utility classes
   */

  /**
   * Reader for byte and multibyte values respecting endianness
   */
  private static class ELF_BinaryReader {
    /**
     * Do we need to byte swap multi-byte values?
     */
    private final boolean needsBswap;

    /**
     * File to read from
     */
    RandomAccessFile rFile;

    /**
     * Constructor
     * @param rFile file to read from
     * @param needsBswap do multibyte values need byte swapping
     */
    ELF_BinaryReader(RandomAccessFile rFile, boolean needsBswap) {
      this.rFile = rFile;
      this.needsBswap = needsBswap;
    }

    /**
     * Byte swap a 32-bit integer
     */
    private static int bswap(int x) {
      return ((x & 0xFF) << 24) | ((x & 0xFF00) << 8) | ((x & 0xFF0000) >> 8)
          | (x >>> 24);
    }

    /**
     * Byte swap a 16-bit integer
     */
    private static short bswap(short x) {
      short result = (short) (((x & 0xFF) << 8) | ((x & 0xFF00) >> 8));
      return result;
    }

    /**
     * Read a 32bit int value
     */
    int readInt() throws IOException {
      int result = rFile.readInt();
      return needsBswap ? bswap(result) : result;
    }

    /**
     * Read a 16bit short value
     */
    short readShort() throws IOException {
      short result = rFile.readShort();
      return needsBswap ? bswap(result) : result;
    }

    /**
     * Seek to location from beginning of file
     */
    void seek(long pos) throws IOException {
      rFile.seek(pos);
    }
  }

  /*
   * Methods
   */

  /**
   * Main entry point that loads the binary
   * @param filename the program file name
   * @return process space containing loaded binary
   */
  public ProcessSpace readBinary(String filename) throws IOException {
    report("Opening File: " + filename);
    RandomAccessFile rFile = new RandomAccessFile(filename, "r");

    elfHeader = new ELF_Header(rFile); // NB also sets up reader
    report("ELF header read successfully");

    ProcessSpace ps = ProcessSpace.createProcessSpaceFromBinary(this);
    report("Created process space of type " + ps.getClass());

    report("Reading program segment headers");
    segmentHeaders = readHeaders();

    report("Creating program segments");
    for (int i = 0; i < segmentHeaders.length; i++) {
      report("Creating: " + segmentHeaders[i]);
      segmentHeaders[i].create(ps);
    }

    int brk;
    if (segmentHeaders.length > 1) {
      brk = segmentHeaders[1].getEnd();
    } else {
      brk = segmentHeaders[0].getEnd();
    }
    report("Initialising the process space: " + "entry = 0x"
        + Integer.toHexString(elfHeader.getEntryPoint()) + " brk = 0x"
        + Integer.toHexString(brk));
    ps.initialise(this, elfHeader.getEntryPoint(), brk);

    return ps;
  }

  /**
   * Determine if the id array corresponds with the initial part of an ELF
   * binary
   * @param filename Name of the file to check
   * @return whether this is an ELF binary
   */
  public static boolean conforms(String filename) {

    RandomAccessFile rFile = null;
    report("Testing is file is ELF: " + filename);

    try {
      rFile = new RandomAccessFile(filename, "r");
      byte[] id = new byte[4];
      rFile.read(id);

      return (id[0] == 0x7f) && (id[1] == 'E') && (id[2] == 'L')
          && (id[3] == 'F');
    } catch (Exception e) {
      return false;
    } finally {
      try {
        rFile.close();
      } catch (Exception e) {
      }
    }
  }

  /**
   * Read and construct the program segment headers
   */
  private ELF_ProgramSegmentHeader[] readHeaders() throws IOException {
    ELF_ProgramSegmentHeader segmentHeaders[] = new ELF_ProgramSegmentHeader[elfHeader
        .getNumberOfProgramSegmentHeaders()];
    reader.seek(elfHeader.getProgramSegmentHeaderOffset());
    for (int i = 0; i < segmentHeaders.length; i++) {
      segmentHeaders[i] = new ELF_ProgramSegmentHeader();
    }
    return segmentHeaders;
  }

  /**
   * Return the application binary interface (ABI) supported by this file
   */
  public String getABIString() {
    return elfHeader.getABIString();
  }

  /**
   * Return the architecture (ISA) supported by this file
   */
  public String getArchitectureString() {
    return elfHeader.getArchitectureString();
  }

  /**
   * Is the ELF's machine field Intel 80386
   */
  public boolean isX86_ISA() {
    return elfHeader.isX86_ISA();
  }

  /**
   * Is the ELF's machine field PowerPC
   */
  public boolean isPPC_ISA() {
    return elfHeader.isPPC_ISA();
  }

  /**
   * Is this binary for the ARM architecture?
   */
  public boolean isARM_ISA() {
    return elfHeader.isARM_ISA();
  }

  /**
   * Does this file support the SysV ABI?
   */
  public boolean isSysV_ABI() {
    return elfHeader.isSysV_ABI();
  }

  /**
   * Does this file support the Linux ABI?
   */
  public boolean isLinuxABI() {
    return elfHeader.isLinuxABI();
  }

  /**
   * Does this file support the ARM ABI?
   * @return
   */
  public boolean isARM_ABI() {
    return elfHeader.isARM_ABI();
  }

  /*
   * Local classes holding structures from the ELF file
   */

  /**
   * Class to read and hold ELF header information
   */
  @SuppressWarnings("unused")
  class ELF_Header {
    /**
     * Class to read and hold ELF header indentity information
     */
    @SuppressWarnings("unused")
    private class ELF_Identity {
      /**
       * Size of ELF identity structure
       */
      private static final int EI_NIDENT = 16;

      /**
       * Backing store for identity structure:
       * {0x7f,'E','L','F',class,data,version,padding..}
       */
      private byte[] e_ident = new byte[EI_NIDENT];

      /**
       * ELF Magic numbers locations in identity
       */
      private static final int EI_MAG0 = 0, EI_MAG1 = 1, EI_MAG2 = 2,
          EI_MAG3 = 3;

      /**
       * ELF magic values indicating an ELF file
       */
      private static final byte ELFMAG0 = 0x7f, ELFMAG1 = 'E', ELFMAG2 = 'L',
          ELFMAG3 = 'F';

      /**
       * Location of class data in identity structure
       */
      private static final int EI_CLASS = 4;

      /**
       * ELF file is invalid
       */
      private static final byte ELFCLASSNONE = 0;

      /**
       * ELF file contains 32bit data
       */
      private static final byte ELFCLASS32 = 1;

      /**
       * ELF file contains 64bit data
       */
      private static final byte ELFCLASS64 = 2;

      /**
       * Location of data information in identity structure
       */
      private static final int EI_DATA = 5;

      /**
       * Invalid data encoding
       */
      private static final byte ELFDATANONE = 0;

      /**
       * LSB or little-endian encoding N.B. not the native Java format
       */
      private static final byte ELFDATA2LSB = 1;

      /**
       * MSB or big-endian encoding N.B. the native Java format
       */
      private static final byte ELFDATA2MSB = 2;

      /**
       * Is this ELF MSB encoded?
       */
      boolean isMSB() throws IOException {
        switch (e_ident[EI_DATA]) {
        case ELFDATA2LSB:
          return false;
        case ELFDATA2MSB:
          return true;
        default:
          throw new IOException("Unrecognized data encoding");
        }
      }

      /**
       * Location of version data - should be EV_CURRENT as defined by the ELF
       * header
       */
      private static final int EI_VERSION = 6;

      /**
       * Location of OS ABI data
       */
      private static final int EI_OSABI = 7;

      /**
       * UNIX System V ABI.
       */
      private static final byte ELFOSABI_SYSV = 0;

      /**
       * HP-UX ABI
       */
      private static final byte ELFOSABI_HPUX = 1;

      /**
       * NetBSD ABI
       */
      private static final byte ELFOSABI_NETBSD = 2;

      /**
       * Linux ABI
       */
      private static final byte ELFOSABI_LINUX = 3;

      /**
       * Solaris ABI
       */
      private static final byte ELFOSABI_SOLARIS = 6;

      /**
       * AIX ABI
       */
      private static final byte ELFOSABI_AIX = 7;

      /**
       * IRIX ABI
       */
      private static final byte ELFOSABI_IRIX = 8;

      /**
       * FreeBSD ABI
       */
      private static final byte ELFOSABI_FREEBSD = 9;

      /**
       * TRU64 UNIX ABI
       */
      private static final byte ELFOSABI_TRU64 = 10;

      /**
       * Novell Modesto
       */
      private static final byte ELFOSABI_MODESTO = 11;

      /**
       * Open BSD
       */
      private static final byte ELFOSABI_OPENBSD = 12;

      /**
       * Open VMS
       */
      private static final byte ELFOSABI_OPENVMS = 13;

      /**
       * Hewlett-Packard Non-Stop Kernel
       */
      private static final byte ELFOSABI_NSK = 14;

      /**
       * ARM ABI, probably using the ARM AAPCS.
       */
      private static final byte ELFOSABI_ARM = 97;

      /**
       * Return the application binary interface (ABI) supported by this file
       */
      String getABIString() {
        switch (e_ident[EI_OSABI]) {
        case ELFOSABI_SYSV:
          return "SysV";
        case ELFOSABI_HPUX:
          return "HP-UX";
        case ELFOSABI_NETBSD:
          return "NetBSD";
        case ELFOSABI_LINUX:
          return "Linux";
        case ELFOSABI_SOLARIS:
          return "Solaris";
        case ELFOSABI_AIX:
          return "AIX";
        case ELFOSABI_IRIX:
          return "IRIX";
        case ELFOSABI_FREEBSD:
          return "FreeBSD";
        case ELFOSABI_TRU64:
          return "TRU64";
        case ELFOSABI_MODESTO:
          return "Novell Modesto";
        case ELFOSABI_OPENBSD:
          return "OpenBSD";
        case ELFOSABI_OPENVMS:
          return "OpenVMS";
        case ELFOSABI_NSK:
          return "Hewlett-Packard Non-Stop Kernel";
        case ELFOSABI_ARM:
          return "ARM ABI";
        default:
          return "Unknown ELF ABI: " + e_ident[EI_OSABI];
        }
      }

      /**
       * Does this file support the SysV ABI?
       */
      boolean isSysV_ABI() {
        return e_ident[EI_OSABI] == ELFOSABI_SYSV;
      }

      /**
       * Does this file support the Linux ABI?
       */
      boolean isLinuxABI() {
        return e_ident[EI_OSABI] == ELFOSABI_LINUX;
      }

      boolean isARM_ABI() {
        return e_ident[EI_OSABI] == ELFOSABI_ARM;
      }

      /**
       * Location of OS ABI version data
       */
      private static final int EI_ABIVERSION = 8;

      /**
       * Location of padding bytes
       */
      private static final int EI_PAD = 9;

      /**
       * Construct/read ELF identity
       */
      ELF_Identity(RandomAccessFile rFile) throws IOException {
        // Identification is in bytes and therefore is endian agnostic
        rFile.read(e_ident);
        // Check magic is correct
        if ((ELFMAG0 != e_ident[EI_MAG0]) || (ELFMAG1 != e_ident[EI_MAG1])
            || (ELFMAG2 != e_ident[EI_MAG2]) || (ELFMAG3 != e_ident[EI_MAG3])) {
          throw new IOException("Bad ELF file magic: " + rFile);
        }
      }
    }

    /**
     * Field holding identity information
     */
    private ELF_Identity identity;

    /**
     * Return the application binary interface (ABI) supported by this file
     */
    String getABIString() {
      return identity.getABIString();
    }

    /**
     * Does this file support the SysV ABI?
     */
    boolean isSysV_ABI() {
      return identity.isSysV_ABI();
    }

    /**
     * Does this file support the Linux ABI?
     */
    boolean isLinuxABI() {
      return identity.isLinuxABI();
    }

    /**
     * Does this file support the ARM ABI?
     */
    boolean isARM_ABI() {
      return identity.isARM_ABI();
    }

    /**
     * Object file type
     */
    private short e_type;

    /**
     * No file type
     */
    private static final short ET_NONE = 0;

    /**
     * Relocatable file
     */
    private static final short ET_REL = 1;

    /**
     * Executable file
     */
    private static final short ET_EXEC = 2;

    /**
     * Shared object file
     */
    private static final short ET_DYN = 3;

    /**
     * Core file
     */
    private static final short ET_CORE = 4;

    /**
     * Number of defined types
     */
    private static final short ET_NUM = 5;

    /**
     * Start of OS reserved region
     */
    private static final short ET_LOOS = (short) 0xfe00;

    /**
     * End of OS reserved region
     */
    private static final short ET_HIOS = (short) 0xfeff;

    /**
     * Start of processor-specific reserved region
     */
    private static final short ET_LOPROC = (short) 0xff00;

    /**
     * End of processor-specific reserved region
     */
    private static final short ET_HIPROC = (short) 0xffff;

    /**
     * The required architecture (machine) for the object file
     */
    private short e_machine;

    /**
     * No machine
     */
    private static final short EM_NONE = 0;

    /**
     * AT&amp;T WE 32100
     */
    private static final short EM_M32 = 1;

    /**
     * SPARC
     */
    private static final short EM_SPARC = 2;

    /**
     * Intel 80386
     */
    private static final short EM_386 = 3;

    /**
     * Motoral 68000
     */
    private static final short EM_68K = 4;

    /**
     * Motorola 88000
     */
    private static final short EM_88K = 5;

    /**
     * Intel 80860
     */
    private static final short EM_860 = 7;

    /**
     * MIPS RS3000
     */
    private static final short EM_MIPS = 8;

    /**
     * PowerPC
     */
    private static final short EM_PPC = 20;

    /**
     * ARM
     */
    private static final short EM_ARM = 40;

    /**
     * Alpha
     */
    private static final short EM_ALPHA = 41;

    /**
     * Sparc V9
     */
    private static final short EM_SPARCV9 = 43;

    /**
     * Is the ELF's machine field Intel 80386
     */
    boolean isX86_ISA() {
      return e_machine == EM_386;
    }

    /**
     * Is the ELF's machine field PowerPC
     */
    boolean isPPC_ISA() {
      return e_machine == EM_PPC;
    }

    /**
     * Is the elf binary for an ARM architecture?
     */
    boolean isARM_ISA() {
      return e_machine == EM_ARM;
    }

    /**
     * Return the architecture (ISA) supported by this file
     */
    public String getArchitectureString() {
      switch (e_machine) {
      case EM_M32:
        return "AT&T WE 32100";
      case EM_SPARC:
        return "SPARC";
      case EM_386:
        return "Intel 80386";
      case EM_68K:
        return "Motorola 68000";
      case EM_88K:
        return "Motorola 88000";
      case EM_860:
        return "Intel 80860";
      case EM_MIPS:
        return "MIPS RS3000";
      case EM_PPC:
        return "PowerPC";
      case EM_ARM:
        return "ARM";
      case EM_ALPHA:
        return "Alpha";
      case EM_SPARCV9:
        return "SPARC V9";
      default:
        return "Unknown architecture " + e_machine;
      }
    }

    /**
     * Object file version
     */
    private int e_version;

    /**
     * Invalid version
     */
    private static final int EV_NONE = 0;

    /**
     * Current version
     */
    private static final int EV_CURRENT = 1;

    /**
     * Entry point virtual address. The virtual address to which the system
     * first transfers control, thus starting the process.
     */
    private int e_entry;

    /**
     * Return the entry point of the binary
     */
    int getEntryPoint() {
      return e_entry;
    }

    /**
     * Program header table file offset
     */
    private int e_phoff;

    /**
     * What is the offset in the file of the program headers
     */
    int getProgramSegmentHeaderOffset() {
      return e_phoff;
    }

    /**
     * Section header table file offset
     */
    private int e_shoff;

    /**
     * Processor-specific flags
     */
    private int e_flags;

    /**
     * ELF header size in bytes
     */
    private short e_ehsize;

    /**
     * Program header table entry size
     */
    private short e_phentsize;

    /**
     * What's the size of a program segment header?
     */
    int getProgramSegmentHeaderSize() {
      return e_phentsize;
    }

    /**
     * Program header table entry count
     */
    private short e_phnum;

    /**
     * How many program segments are in this ELF binary?
     */
    int getNumberOfProgramSegmentHeaders() {
      return e_phnum;
    }

    /**
     * Section header table entry size
     */
    private short e_shentsize;

    /**
     * Section header table entry count
     */
    private short e_shnum;

    /**
     * Section header table index
     */
    private short e_shstrndx;

    /**
     * Construct/read ELF header
     */
    ELF_Header(RandomAccessFile rFile) {
      try {
        // Identification is in bytes and therefore is endian agnostic
        identity = new ELF_Identity(rFile);
        // Set up reader to handle endianness for the rest of the file
        reader = new ELF_BinaryReader(rFile, !identity.isMSB());
        // Read in rest of header
        e_type = reader.readShort();
        e_machine = reader.readShort();
        e_version = reader.readInt();
        e_entry = reader.readInt();
        e_phoff = reader.readInt();
        e_shoff = reader.readInt();
        e_flags = reader.readInt();
        e_ehsize = reader.readShort();
        e_phentsize = reader.readShort();
        e_phnum = reader.readShort();
        e_shentsize = reader.readShort();
        e_shnum = reader.readShort();
        e_shstrndx = reader.readShort();
      } catch (IOException e) {
        throw new Error(e);
      }
    }
  }

  /**
   * Header representing a segment in the process (e.g. stack, heap, code aka
   * text) in the ELF file. These are known as program header's in the ELF
   * literature, but they don't represent programs, rather separate segments.
   */
  @SuppressWarnings("unused")
  class ELF_ProgramSegmentHeader {
    /**
     * Type of the segment
     */
    private final int p_type;

    /**
     * Null header, contains no data and can be ignored
     */
    private static final int PT_NULL = 0;

    /**
     * A loadable segment
     */
    private static final int PT_LOAD = 1;

    /**
     * Segment containing dynamic linking information
     */
    private static final int PT_DYNAMIC = 2;

    /**
     * A segment containing a string to invoke as interpreter for this file
     */
    private static final int PT_INTERP = 3;

    /**
     * A segment describing the location and size of auxiliary information
     */
    private static final int PT_NOTE = 4;

    /**
     * A reserved segment type with unspecified semantics
     */
    private static final int PT_SHLIB = 5;

    /**
     * A segment describing the ELF's header, present once before any loadable
     * segments
     */
    private static final int PT_PHDR = 6;

    /**
     * Thread local storage (TLS) segment
     */
    private static final int PT_TLS = 7;

    /**
     * Number of defined types
     */
    private static final int PT_NUM = 8;

    /**
     * Start of OS reserved segment types
     */
    private static final int PT_LOOS = 0x60000000;

    /**
     * SUN unwind table segment
     */
    private static final int PT_SUNW_UNWIND = 0x6464e550;

    /**
     * GCC .eh_frame_hdr segment
     */
    private static final int PT_GNU_EH_FRAME = 0x6474e550;

    /**
     * Indicates stack executability
     */
    private static final int PT_GNU_STACK = 0x6474e551;

    /**
     * Read-only after relocation
     */
    private static final int PT_GNU_RELRO = 0x6474e552;

    /**
     * Start of SUN reserved segments
     */
    private static final int PT_LOSUNW = 0x6ffffffa;

    /**
     * The array element has the same attributes as a PT_LOAD element and is
     * used to describe a .SUNW_bss section.
     */
    private static final int PT_SUNWBSS = 0x6ffffffa;

    /**
     * Describes a process stack. Only one PT_SUNWSTACK element can exist. Only
     * access permissions, as defined in the p_flags field, are meaningful.
     */
    private static final int PT_SUNWSTACK = 0x6ffffffb;

    /**
     * Reserved for internal use by dtrace
     */
    private static final int PT_SUNWDTRACE = 0x6ffffffc;

    /**
     * Specifies hardware capability requirements
     */
    private static final int PT_SUNWCAP = 0x6ffffffd;

    /**
     * End of SUN reserved segments
     */
    private static final int PT_HISUNW = 0x6fffffff;

    /**
     * End of OS reserved segment types
     */
    private static final int PT_HIOS = 0x6fffffff;

    /**
     * Start of processor reserved segment types
     */
    private static final int PT_LOPROC = 0x70000000;

    /**
     * End of processor reserved segment types
     */
    private static final int PT_HIPROC = 0x7fffffff;

    /**
     * Offset of first byte of segment data in file
     */
    private final int p_offset;

    /**
     * Virtual address of first byte in segment
     */
    private final int p_vaddr;

    /**
     * Corresponding physical addressed used by some systems
     */
    private final int p_paddr;

    /**
     * Size of segment in file
     */
    private final int p_filesz;

    /**
     * Size of segment in memory
     */
    private final int p_memsz;

    /**
     * Read/Write/Execute flags for segment in memory
     */
    private final int p_flags;

    /**
     * Executable flag
     */
    private static final int PF_X = 0x1;

    /**
     * Writable flag
     */
    private static final int PF_W = 0x2;

    /**
     * Readable flag
     */
    private static final int PF_R = 0x4;

    /**
     * OS specific reserved bits
     */
    private static final int PF_MASKOS = 0x0ff00000;

    /**
     * Processor specific reserved bits
     */
    private static final int PF_MASKPROC = 0xf0000000;

    /**
     * This member gives the value to which the segments are aligned in memory
     * and in the file. Values 0 and 1 mean no alignment is required. Otherwise,
     * p_align should be a positive, integral power of 2, and p_vaddr should
     * equal p_offset, modulo p_align.
     */
    private final int p_align;

    /**
     * Construct a program segment header reading in from the reader
     */
    private ELF_ProgramSegmentHeader() throws IOException {
      p_type = reader.readInt();
      p_offset = reader.readInt();
      p_vaddr = reader.readInt();
      p_paddr = reader.readInt();
      p_filesz = reader.readInt();
      p_memsz = reader.readInt();
      p_flags = reader.readInt();
      p_align = reader.readInt();
      // Move file onto next program segment header offset
      reader.rFile.skipBytes(elfHeader.getProgramSegmentHeaderSize() - 32);
    }

    /**
     * Load/create the program segment
     */
    void create(ProcessSpace ps) {
      switch (p_type) {
      case PT_NULL: // Null header, contains no data and can be ignored
        break;
      case PT_LOAD: // A loadable segment
        try {
          createSegment(ps.memory, reader.rFile, p_offset, p_vaddr, p_filesz,
              p_memsz, (p_flags & PF_R) != 0, (p_flags & PF_W) != 0,
              (p_flags & PF_X) != 0);
        } catch (MemoryMapException e) {
          throw new Error("Error in creating: " + this, e);
        }
        break;
      case PT_NOTE: // A segment describing the location and size of auxiliary
                    // information
        // ignore
        break;
      case PT_GNU_STACK: // A segment describing the permissions for the stack
        // ignore
        break;
      case PT_TLS: // A segment describing thread local storage
        // ignore for now
        break;
      case PT_INTERP: // A segment containing a string to invoke as interpreter
                      // for this file
      case PT_DYNAMIC: // Segment containing dynamic linking information
      case PT_SHLIB: // A reserved segment type with unspecified semantics
      case PT_PHDR: // A segment describing the ELF's header; present once
                    // before any loadable segments
      default:
        throw new Error("Segment type " + toString() + " not yet supported");
      }
    }

    /**
     * Create a segment
     * @param memory The memory into which the segment is to be mapped.
     * @param file file to read segment data from if file size != 0
     * @param offset file offset
     * @param address location of segment
     * @param filesize size of segment in file
     * @param memsize size of segment in memory
     * @param read is segment readable
     * @param write is segment writable
     * @param exec is segment executable
     */
    public void createSegment(Memory memory, RandomAccessFile file,
        long offset, int address, int filesize, int memsize, boolean read,
        boolean write, boolean exec) throws MemoryMapException {
      // Sanity check
      if (memsize < filesize) {
        throw new Error("Segment memory size (" + memsize
            + ")less than file size (" + filesize + ")");
      }
      // Are we mapping anything from a file?
      if (filesize == 0) {
        // No: map anonymously
        memory.map(address, memsize, read, write, exec);
      } else {
        // align offset and address
        int alignedAddress;
        long alignedOffset;
        int alignedFilesize;
        if (memory.isPageAligned(address)) {
          // memory and therefore offset should be aligned
          alignedAddress = address;
          alignedOffset = offset;
          alignedFilesize = filesize;
        } else {
          // Address not aligned
          alignedAddress = memory.truncateToPage(address);
          int delta = address - alignedAddress;
          // adjust offset and length too
          alignedOffset = offset - delta;
          alignedFilesize = filesize + delta;
        }
        memory.map(file, alignedOffset, alignedAddress, alignedFilesize, read,
            write, exec);
        // Do we need to map in some blank pages at the end of the segment?
        if (filesize < memsize) {
          alignedAddress = memory.truncateToNextPage(address + filesize);
          memory.map(alignedAddress, memsize - filesize, read, write, exec);
        }
      }
    }

    /**
     * Round the give value up so that it is at the beginning of the next
     * aligned region
     */
    private int truncateToNextAlignment(int x) {
      return ((x + p_align - 1) / p_align) * p_align;
    }

    /**
     * Get the end of the segment
     */
    public int getEnd() {
      return truncateToNextAlignment(p_vaddr + p_memsz);
    }

    /**
     * String representation of header
     */
    public String toString() {
      switch (p_type) {
      case PT_NULL:
        return "Null segment header (ignored)";
      case PT_LOAD:
        return "Loadable segment (offset=0x" + Long.toHexString(p_offset)
            + ", address=0x" + Integer.toHexString(p_vaddr) + ", file size="
            + p_filesz + ", memory size=" + p_memsz + ", permissions="
            + (((p_flags & PF_R) != 0) ? 'r' : '-')
            + (((p_flags & PF_W) != 0) ? 'w' : '-')
            + (((p_flags & PF_X) != 0) ? 'x' : '-') + ")";
      case PT_TLS:
        return "TLS segment (offset=0x" + Long.toHexString(p_offset)
            + ", address=0x" + Integer.toHexString(p_vaddr) + ", file size="
            + p_filesz + ", memory size=" + p_memsz + ", permissions="
            + (((p_flags & PF_R) != 0) ? 'r' : '-')
            + (((p_flags & PF_W) != 0) ? 'w' : '-')
            + (((p_flags & PF_X) != 0) ? 'x' : '-') + ")";
      case PT_NOTE:
        return "Note: segment containing auxiliary information";
      case PT_INTERP:
        return "Interpreter";
      case PT_DYNAMIC:
        return "Dynamic link information";
      case PT_SHLIB:
        return "SHLIB/Unspecified - semantics unknown";
      case PT_PHDR:
        return "Program header";
      case PT_GNU_STACK:
        return "GNU stack executability";
      default:
        if ((p_type > PT_LOPROC) && (p_type <= PT_HIPROC)) {
          return "Processor specific segment 0x" + Integer.toHexString(p_type);
        } else if ((p_type > PT_LOOS) && (p_type <= PT_HIOS)) {
          if ((p_type > PT_LOSUNW) && (p_type <= PT_HISUNW)) {
            return "Sun OS specific segment 0x" + Integer.toHexString(p_type);
          } else {
            return "OS specific segment 0x" + Integer.toHexString(p_type);
          }
        } else {
          return "Unknown segment: 0x" + Integer.toHexString(p_type);
        }
      }
    }
  }
}
