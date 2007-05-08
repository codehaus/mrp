/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.os.loader.elf;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.WeakHashMap;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.memory.Memory;
import org.binarytranslator.generic.memory.MemoryMapException;
import org.binarytranslator.generic.os.loader.Loader;
import org.binarytranslator.generic.os.process.ProcessSpace;

public class ELF_File {

  /**
   * Wrapper class used for reading the ELF file with the required endianness
   */
  private BinaryReader reader;

  /**
   * Header of ELF file
   */
  private Header header;

  /**
   * Program segment headers
   */
  private SegmentHeader segmentHeaders[];

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
  /**
   * Reader for byte and multibyte values respecting endianness.
   */
  private abstract static class BinaryReader {
    
    /** File to read from */
    protected RandomAccessFile rFile;
    
    /**
     * Returns a new ELF_BinaryReader for the specified byte order and file.
     * 
     * @param byteOrder
     *  The byte order that the file posesses.
     * @param file
     *  The file that is to be read.
     * @return
     *  An ELF_BinaryReader, that hides the details of the byte order.
     */
    public static BinaryReader create(ELF_Identity.ByteOrder byteOrder, RandomAccessFile file) {
      
      if (byteOrder == ELF_Identity.ByteOrder.BigEndian)
        return new NonSwappingReader(file); 
      else
        return new ByteSwappingReader(file);
    }

    /** Hide the constructor, because this class shall only be instantiated by using the factory method {@link #create(org.binarytranslator.generic.os.loader.elf.ELF_Loader.Header.ByteOrder, RandomAccessFile)}. */
    private BinaryReader(RandomAccessFile rFile) {
      this.rFile = rFile;
    }
    
    /** Seek to location from beginning of file */
    void seek(long pos) throws IOException {
      rFile.seek(pos);
    }
    
    /** Reads a single byte from an ELF file. */
    public byte readByte() throws IOException {
      return rFile.readByte();
    }
    
    /** Read an integer from the file. This function is supposed to hide the difference between little and big endian reads. */
    public abstract int readInt() throws IOException;
    
    /** Read a short from the file. This function is supposed to hide the difference between little and big endian reads. */
    public abstract short readShort() throws IOException;
    
    /** Reader that performs byte swaps for each int/short read. */
    private static class ByteSwappingReader extends BinaryReader {
      
      ByteSwappingReader(RandomAccessFile rFile) {
        super(rFile);
      }

      /** Byte swap a 32-bit integer */
      private static int bswap(int x) {
        return ((x & 0xFF) << 24) | ((x & 0xFF00) << 8) | ((x & 0xFF0000) >> 8)
            | (x >>> 24);
      }

      /** Byte swap a 16-bit integer */
      private static short bswap(short x) {
        short result = (short) (((x & 0xFF) << 8) | ((x & 0xFF00) >> 8));
        return result;
      }

      @Override
      public int readInt() throws IOException {
        return bswap(rFile.readInt());
      }

      @Override
      public short readShort() throws IOException {
        return bswap(rFile.readShort());
      }
    }
    
    /** Reader that does not perform any byte swaps.*/
    private static class NonSwappingReader extends BinaryReader {

      NonSwappingReader(RandomAccessFile rFile) {
        super(rFile);
      }

      @Override
      public int readInt() throws IOException {
        return rFile.readInt();
      }

      @Override
      public short readShort() throws IOException {
        return rFile.readShort();
      }
    }
  }
  
  /**
   * Returns an Object representation of this ELF's header file.
   */
  public Header getHeader() {
    return header;
  }
  
  /** Returns an array of program segment headers within this elf.*/
  public SegmentHeader[] getProgramSegmentHeaders() {
    return segmentHeaders;
  }
  
  /** Returns the dynamic section, which is identifed by the PT_DYNAMIC segment header. */
  public DynamicSection getDynamicSection() throws IOException {
    
    for (ELF_File.SegmentHeader segment : segmentHeaders)
      if (segment.p_type == ELF_File.SegmentHeader.PT_DYNAMIC) {
        return segment.asDynamicSection();
      }
    
    return null;
  }

  /**
   * Reads an ELF File from a binary.
   * @param filename the program file name
   * @return An object representation of the ELF file
   */
  public ELF_File(String filename) throws IOException {
    report("Opening File: " + filename);
    RandomAccessFile rFile = new RandomAccessFile(filename, "r");
    
    // Read the ELF Header, which will also provide us with a reader object, that hides ELF endianness
    header = new Header(rFile);
    reader = header.getReader();
    
    report("ELF header read successfully");

    //read the program segment headers
    report("Reading program segment headers");
    segmentHeaders = new SegmentHeader[header.getNumberOfProgramSegmentHeaders()];
    reader.seek(header.getProgramSegmentHeaderOffset());
    
    for (int i = 0; i < segmentHeaders.length; i++) {
      segmentHeaders[i] = new SegmentHeader(reader);
    }

    /*
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

    return ps;*/
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
   * Where did the program header get loaded in memory?
   */
  public int getProgramHeaderAddress() {
    return segmentHeaders[0].p_vaddr;
    //return header.e_phoff - segmentHeaders[0].p_offset + segmentHeaders[0].p_vaddr;
  }

  /** An interface for enums where each value is identified by an identifier.*/
  private static interface IdentifiedEnum {
    int getIdentifier();
  }
  
  /** Returns the segment that the given virtual address belongs to. */
  public SegmentHeader virtualAddressToSegment(int virtualAddress) {
    
    for (int i = 0; i < segmentHeaders.length; i++) {
      int offsetWithinSegment = (virtualAddress - segmentHeaders[i].p_vaddr);
      
      if (offsetWithinSegment > 0 && offsetWithinSegment < segmentHeaders[i].p_memsz) {
        return segmentHeaders[i];
      }
    }
    
    return null;
  }
  
  /** 
   * Creates a corresponding enum value from an integer identifier for enums implementing the {@link IdentifiedEnum} interface.
   * In case no corresponding enum value is available, the function returns null. */
  private static <T extends Enum<T> & IdentifiedEnum> T getEnumFromIdentifier(Class<T> enumClass, int identifier) {
    for (T value : enumClass.getEnumConstants())
      if (value.getIdentifier() == identifier)
        return value;
    
    return null;
  }
  
  /**
   * Class to read and hold ELF header indentity information
   */
  @SuppressWarnings("unused")
  private static class ELF_Identity {
    
    private BinaryReader reader;
    
    /** Represents acceptable ELF address sizes. */
    private enum AddressSize implements IdentifiedEnum {
      Size32(1),
      Size64(2);
      
      private int identifier;
      
      private AddressSize(int identifier) {
        this.identifier = identifier;
      }

      public int getIdentifier() {
        return identifier;
      }
    }
    
    /** Represents accepted ELF byte orders. */
    private enum ByteOrder implements IdentifiedEnum {
      LittleEndian(1),
      BigEndian(2);
      
      private int identifier;
      
      private ByteOrder(int identifier) {
        this.identifier = identifier;
      }

      public int getIdentifier() {
        return identifier;
      }
    }
    
    /* Symbolic names for the most widely used ABIs. This is not an enum, because the list isn't complete. */
    private static final byte ELFOSABI_SYSTEMV = 0;
    private static final byte ELFOSABI_HPUX = 1;
    private static final byte ELFOSABI_NETBSD = 2;
    private static final byte ELFOSABI_LINUX = 3;
    private static final byte ELFOSABI_SOLARIS = 6;
    private static final byte ELFOSABI_AIX = 7;
    private static final byte ELFOSABI_IRIX = 8;
    private static final byte ELFOSABI_FREEBSD = 9;
    private static final byte ELFOSABI_TRU64 = 10;
    private static final byte ELFOSABI_MODESTO = 11;
    private static final byte ELFOSABI_OPENBSD = 12;
    private static final byte ELFOSABI_OPENVMS = 13;
    private static final byte ELFOSABI_NSK = 14;
    private static final byte ELFOSABI_ARM = 97;

    /**
     * ELF magic values indicating an ELF file
     */
    private static final byte ELF_MAGIC_VALUE[] = { 0x7f, 'E', 'L','F' };

    /** Specifies the size of an address within this elf.*/
    private AddressSize addressSize;
    
    /** The byte order used by this elf.*/
    private ByteOrder byteOrder;
    
    /** The ABI that is used by this ELF.*/
    private byte abi;
    
    /**
     * Construct/read ELF identity
     */
    ELF_Identity(RandomAccessFile rFile) throws IOException {
      // Identification is in bytes and therefore is endian agnostic
      byte[] magic = new byte[ELF_MAGIC_VALUE.length];
      
      if (rFile.read(magic) != magic.length)
        throw new IOException("ELF file too short.");
      
      // Check that the ELF magic is correct
      for (int i = 0; i < ELF_MAGIC_VALUE.length; i++) {
        if (magic[i] != ELF_MAGIC_VALUE[i]) {
          throw new IOException("Bad ELF file magic: " + rFile);
        }
      }
      
      //read the address size
      addressSize = getEnumFromIdentifier(AddressSize.class, rFile.readByte());
      if (addressSize == null)
        throw new IOException("Invalid address sizer specified by ELF file.");

      //read the byte order
      byteOrder = getEnumFromIdentifier(ByteOrder.class, rFile.readByte());
      if (byteOrder == null)
        throw new IOException("Invalid byte order specified by ELF file.");
      
      //Check the ELF's file version
      if (rFile.readByte() != 1) {
        throw new IOException("Invalid ELF File version.");
      }
      
      //read a byte describing the target ABI
      abi = rFile.readByte();
      
      //skip the remaining padding bytes so that we're arriving at a 16 byte alignment.
      if (rFile.skipBytes(8) != 8) {
        throw new IOException("ELF file is too short.");
      }
      
      reader = BinaryReader.create(byteOrder, rFile);
    }

    public Loader.ABI getABI() 
    {
      //read the OS ABI
      switch (abi) {
        case ELFOSABI_SYSTEMV:
          return Loader.ABI.SystemV;
          
        case ELFOSABI_LINUX:
          return Loader.ABI.Linux;
          
        case ELFOSABI_ARM:
          return Loader.ABI.ARM;
        
        default:
          return Loader.ABI.Undefined;
      }
    }
    
    protected BinaryReader getReader() {
      return reader;
    }
  }

  /**
   * Class to read and hold ELF header information
   */
  @SuppressWarnings("unused")
  public static class Header extends ELF_Identity {

    /** A list of possible object file types. */
    enum ObjectFileType implements IdentifiedEnum {
      Relocatable(1),
      Executable(2),
      SharedObject(3), 
      Core(4);
      
      private int identifier;
      
      private ObjectFileType(int identifier) {
        this.identifier = identifier;
      }

      public int getIdentifier() {
        return identifier;
      }
    }
    
    /** Object file type */
    private ObjectFileType e_type;

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

    /* Short names for a few known machine types. Not an enum, because this list is not complete. */
    private static final short EM_M32 = 1;
    private static final short EM_SPARC = 2;
    private static final short EM_386 = 3;
    private static final short EM_68K = 4;
    private static final short EM_88K = 5;
    private static final short EM_860 = 7;
    private static final short EM_MIPS = 8;
    private static final short EM_PPC = 20;
    private static final short EM_ARM = 40;
    private static final short EM_ALPHA = 41;
    private static final short EM_SPARCV9 = 43;

    /**
     * Object file version
     */
    private int e_version;

    /**
     * Entry point virtual address. The virtual address to which the system
     * first transfers control, thus starting the process.
     */
    private int e_entry;

    /**
     * Program header table file offset
     */
    private int e_phoff;

    /**
     * Section header table file offset
     */
    private final int e_shoff;

    /**
     * Processor-specific flags
     */
    private final int e_flags;

    /**
     * ELF header size in bytes
     */
    private final short e_ehsize;

    /**
     * Program header table entry size
     */
    private final short e_phentsize;
    
    /**
     * Program header table entry count
     */
    private final short e_phnum;

    /**
     * Section header table entry size
     */
    private final short e_shentsize;

    /**
     * Section header table entry count
     */
    private final short e_shnum;

    /**
     * Section header table index
     */
    private final short e_shstrndx;

    /**
     * Construct/read ELF header
     */
    Header(RandomAccessFile file) throws IOException {
      super(file);
      
      BinaryReader reader = getReader();
      
      try {
        // Read in rest of header
        e_type = getEnumFromIdentifier(ObjectFileType.class, reader.readShort());
        
        if (e_type == null) {
          throw new Error("Invalid Object file type.");
        }
        
        e_machine = reader.readShort();
        e_version = reader.readInt();
        
        if (e_version != 1) {
          throw new Error("Unexpected ELF File version: " + e_version);
        }
        
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
    
    /**
     * What is the offset in the file of the program headers
     */
    public int getProgramSegmentHeaderOffset() {
      return e_phoff;
    }

    /**
     * What's the size of a program segment header?
     */
    public int getProgramSegmentHeaderSize() {
      return e_phentsize;
    }

    /**
     * How many program segments are in this ELF binary?
     */
    public int getNumberOfProgramSegmentHeaders() {
      return e_phnum;
    }
    
    /**
     * Return the entry point of the binary
     */
    public int getEntryPoint() {
      return e_entry;
    }
        
    /** Maps the ISA specified within the ELF file to an ISA supported by Pearcolator. */
    public Loader.ISA getISA() {
      switch (e_machine) {
        case EM_ARM:
          return Loader.ISA.ARM;
          
        case EM_386:
          return Loader.ISA.X86;
          
        case EM_PPC:
          return Loader.ISA.PPC;
          
        default:
          return Loader.ISA.Undefined;
      }
    }
  }
  
  /** Identifies a named segment range. */
  private enum SegmentRange {
    /** SUN reserved segments */
    SunReserved(0x6ffffffa, 0x6fffffff, "SUN Reserved Segment"),
    
    /** OS reserved segment types */
    OperatingSystem(0x60000000, 0x6fffffff, ("Operating System Segment")),
    
    /** processor reserved segment types */
    Processor(0x70000000, 0x7fffffff, "Processor Segment"),
    
    /** remaining (unknown) segment types */
    Unknown(0x0, 0xffffffff, "Unknown Segment"); 
    
    private int lowAddress;
    private int highAddress;
    private String description;
    
    private SegmentRange(int from, int to, String description) {
      
      if (DBT.VerifyAssertions) DBT._assert(from < to);
      
      lowAddress = from;
      highAddress = to;
      this.description = description;
    }
    
    public static SegmentRange fromInteger(int address) {
      for (SegmentRange range : values()) {
        if (range.lowAddress >= address && range.highAddress <= address)
          return range;
      }
      
      return null;
    }
    
    @Override
    public String toString() {
      return description;
    }
  }
  
  public class StringTable {
    
    /** Every string that has been resolved is cached in this map to allow faster retrieval for the next time. */
    private WeakHashMap<Integer, String> cachedStrings = new WeakHashMap<Integer, String>();
    
    /** A buffer that holds the table's contents*/
    private byte[] buffer;
    
    public StringTable(int virtualAddress, int size) throws IOException {
      
      //initialize the buffer
      buffer = new byte[size];
      
      //find the segment within which the string table resides
      SegmentHeader strTableSeg = virtualAddressToSegment(virtualAddress);
      
      if (strTableSeg == null)
        throw new IOException("Unable to locate the segment within which the string table resides.");
      
      int offsetWithinSegment = virtualAddress - strTableSeg.p_vaddr;
      reader.seek(strTableSeg.p_offset + offsetWithinSegment);
      
      int readBytes = reader.rFile.read(buffer);
      
      if (readBytes != buffer.length)
        throw new IOException("Unable to read string table from file.");
    }
    
    /** Looks a string table entry up by address. */
    public String lookup(int index) {
      if (index >= buffer.length)
        throw new IndexOutOfBoundsException("Invalid string table index: " + index);
      
      String result = cachedStrings.get(index);
      
      if (result != null)
        return result;
      
      int nextIndex = index;
      
      result = "";
      byte b = (byte)buffer[nextIndex++];
      
      while (b != 0) {
        result += (char)b;
        b = (byte)buffer[nextIndex++];
      }
      
      //cache the string that we just found
      cachedStrings.put(index, result);
      
      //and return the result
      return result;
    }
  }
  
  public class SymbolHashTable {
    private final SymbolTable symTab;
    private final StringTable strTab;
    
    private final int buckets[];
    private final int chains[];
    
    public SymbolHashTable(SymbolTable symTab, StringTable strTab, int virtualAddress) throws IOException {
      this.symTab = symTab;
      this.strTab = strTab;
      
      //find the hash table within the file
      SegmentHeader segment = virtualAddressToSegment(virtualAddress);
      
      if (segment == null)
        throw new IOException("Unable to locate the segment within which the hash table resides."); 
      
      int offsetWithinSegment = virtualAddress - segment.p_vaddr;
      reader.seek(segment.p_offset + offsetWithinSegment);
      
      //load the hash table from the file
      int bucketCount = reader.readInt();
      int chainCount = reader.readInt();
      
      buckets = new int[bucketCount];
      chains = new int[chainCount];
      
      for (int i = 0; i < bucketCount; i++)
        buckets[i] = reader.readInt();

      for (int i = 0; i < chainCount; i++)
        chains[i] = reader.readInt();
    }
    
    public SymbolTable.Entry lookup(String symbol) throws IOException {
      int hash = getElfHash(symbol);
      
      //what is the index of that symbol in the symbol table?
      int symbolIndex = buckets[hash % buckets.length];
      
      while (symbolIndex != SymbolTable.STN_UNDEF) {
        //get the symbol that we retrieved from the hash table
        SymbolTable.Entry entry = symTab.getEntry(symbolIndex);
        String curSymbolname = strTab.lookup(entry.nameIdx);
        
        //is that the symbol we're looking for?
        if (curSymbolname.equals(symbol))
          return entry;
        
        //if not, follow the chain to the next symbol
        symbolIndex = chains[symbolIndex];
      }
      
      //obviously, we couldn't find this symbol
      return null;
    }

    private int getElfHash(String symbol) {

      int h = 0;
      int g = 0;
      int nextChar = 0;

      while (nextChar < symbol.length()) {
        h = (h << 4) + (byte) symbol.charAt(nextChar++);

        g = h & 0xf0000000;
        if (g != 0)
          h ^= g >>> 24;

        h &= ~g;
      }
      
      return h;
    }
    
    
  }
  
  public class SymbolTable {
    
    /** The undefined symbol index. */
    public final static int STN_UNDEF = 0;
    
    /** Symbol binding values. All other values are processor-dependant. */
    public final static byte STB_LOCAL = 0;
    public final static byte STB_GLOBAL = 1;
    public final static byte STB_WEAK = 2;
    
    /** Symbol types. */
    public final static byte STT_NOTYPE = 0;
    public final static byte STT_OBJECT = 1;
    public final static byte STT_FUNC = 2;
    public final static byte STT_SECTION = 3;
    public final static byte STT_FILE = 4;
    
    /** Some section indices have special meanings: */
    public final static short SHN_ABS = (short)0xfff1;
    public final static short SHN_COMMON = (short)0xfff2;
    public final static short SHN_UNDEF = 0;
    
    public class Entry {
      public final int nameIdx;
      public final int value;
      public final int size;
      public final byte binding;
      public final byte type;
      public final short sectionIndex;
      
      public Entry(int nameIdx, int value, int size, byte binding, byte type, short sectionIndex) {
        this.nameIdx = nameIdx;
        this.value = value;
        this.size = size;
        this.binding = binding;
        this.type = type;
        this.sectionIndex = sectionIndex;
      }
      
      final boolean isUndefined() {
        return sectionIndex == SHN_UNDEF;
      }
    }
    
    /** Every symbol that has been retrieved previously is cached in this map to allow faster access for the next time. */
    private WeakHashMap<Integer, Entry> cachedSymbols = new WeakHashMap<Integer, Entry>();
    
    /** Offset at which the symbol table starts within the file. */
    private final int fileOffset;
    
    /** size of a single entry within the symbol table */
    private final int entrySize;
    
    public SymbolTable(int virtualAddress, int entrySize) throws IOException {
      SegmentHeader segment = virtualAddressToSegment(virtualAddress);
      int offset = virtualAddress - segment.p_vaddr;
      
      fileOffset = segment.p_offset + offset;
      this.entrySize = entrySize;
    }
    
    public Entry getEntry(int index) throws IOException {
      
      //check if we already have cached version of this symbol
      Entry result = cachedSymbols.get(index);
      
      if (result != null)
        return result;
      
      //no, we have to read it from the file
      reader.seek(fileOffset + entrySize * index);
      
      int name = reader.readInt();
      int addr = reader.readInt();
      int size = reader.readInt();
      byte info = reader.readByte();
      reader.readByte(); //skip an empty byte
      short sectionIndex = reader.readShort();
      
      //create the requested entry...
      result = new Entry(name, addr, size, (byte)(info >>> 4), (byte)(info & 0xF), sectionIndex);
      
      //cache it for the next time
      cachedSymbols.put(index, result);

      return result;
    }
  }
  
  public class RelocationTable {
    
    public final ArrayList<Entry> entries;
    public final boolean hasAddends;
    
    public class Entry {
      public final int offset;
      public final int symbolIndex;
      public final byte relocationType;
      public final int addend;
      
      public Entry(int offset, int symbolindex, byte relocationType, int addend) {
        this.offset = offset;
        this.symbolIndex = symbolindex;
        this.relocationType = relocationType;
        this.addend = addend;
      }
      
      @Override
      public String toString() {
        return String.format("Offset: %d, Symbol: %d, Type: %d, Addend: %d", offset, symbolIndex, relocationType, addend);
      }
    }
    
    public RelocationTable(int virtualAddress, int tableSize, int entrySize, boolean hasAddends) throws IOException {
      
      //check the parameters
      if (tableSize < 0 || entrySize < 0) {
        throw new InvalidParameterException("Cannot create relocation section from invalid section sizes.");
      }
      
      this.entries = new ArrayList<Entry>();
      this.hasAddends = hasAddends;
      
      //find the segment within which the string table resides
      SegmentHeader strTableSeg = virtualAddressToSegment(virtualAddress);
      
      if (strTableSeg == null)
        throw new IOException("Unable to locate the segment within which the relocation section resides.");
      
      int offsetWithinSegment = virtualAddress - strTableSeg.p_vaddr;
      virtualAddress = strTableSeg.p_offset + offsetWithinSegment; 
      
      while (tableSize >= entrySize) {
        reader.seek(virtualAddress);
        
        //read a single relocation entry 
        int offset = reader.readInt();
        int info = reader.readInt();
        int addend = 0;
        
        if (hasAddends)
          addend = reader.readInt();
        
        //add it to the list of relocation entries
        entries.add(new Entry(offset, info >>> 8, (byte)(info & 0xFF), addend));
        
        //skip to the next entry
        virtualAddress += entrySize;
        tableSize -= entrySize;
      }
    }
    
    @Override
    public String toString() {
      String result = "";
      
      for (int i = 0; i < entries.size(); i++)
        result += entries.get(i).toString() + "\n";
      
      return result;
    }
  }
  
  public class DynamicSection {
    public final static int DT_NULL = 0;
    public final static int DT_NEEDED = 1;
    public final static int DT_PLTRELSZ = 2;
    public final static int DT_PLTGOT = 3;
    public final static int DT_HASH = 4;
    public final static int DT_STRTAB = 5;
    public final static int DT_SYMTAB = 6;
    public final static int DT_RELA = 7;
    public final static int DT_RELASZ = 8;
    public final static int DT_RELAENT = 9;
    public final static int DT_STRSZ = 10;
    public final static int DT_SYMENT = 11;
    public final static int DT_INIT = 12;
    public final static int DT_FINI = 13;
    public final static int DT_SONAME = 14;
    public final static int DT_RPATH = 15;
    public final static int DT_SYMBOLIC = 16;
    public final static int DT_REL = 17;
    public final static int DT_RELSZ = 18;
    public final static int DT_RELENT = 19;
    public final static int DT_PLTREL = 20;
    public final static int DT_DEBUG = 21;
    public final static int DT_TEXTREL = 22;
    public final static int DT_JMPREL = 23;
    public final static int DT_BIND_NOW = 24;
    
    public class Entry {
      public final int type;
      public final int value;
      
      private Entry(int type, int value) {
        this.type = type;
        this.value = value;
      }
      
      @Override
      public String toString() {
        return String.format("Type: %d, Value: 0x%x", type, value);
      }
    }
    
    /** the entries within the dynamic section */
    private ArrayList<Entry> entries = new ArrayList<Entry>();
    
    /** the string table referenced by the dynamic section */
    private StringTable strTab;
    
    /** The symbol table referenced by this dynamic section. */
    private SymbolTable symTab;
    
    /** The hash table referenced by this dynamic section. */
    private SymbolHashTable hashTab;
    
    /** The relA section (if present) that is referenced within this dynamic section */
    private RelocationTable relaTab;
    
    /** The rel section (if present) that is referenced within this dynamic section */
    private RelocationTable relTab;
    
    public DynamicSection(SegmentHeader segment) throws IOException {
      
      if (segment.p_type != SegmentHeader.PT_DYNAMIC)
        throw new InvalidParameterException("segment is not of type PT_DYNAMIC.");
      
      reader.seek(segment.p_offset);
      Entry entry;
      
      do {
        int type = reader.readInt();
        int value = reader.readInt();
        
        entry = new Entry(type, value);
        entries.add(entry);
      }
      while (entry.type != DT_NULL);
    }
    
    public Entry getEntryByType(int type) {
      for(Entry entry : entries)
        if (entry.type == type)
          return entry;
      
      return null;
    }
    
    public Entry[] getEntriesByType(int type) {
      ArrayList<Entry> matches = new ArrayList<Entry>();
      
      for(Entry entry : entries)
        if (entry.type == type)
          matches.add(entry);
      
      Entry[] result = new Entry[matches.size()];
      return matches.toArray(result);
    }
    
    public SymbolHashTable findHashTable() throws IOException {
      if (hashTab != null)
        return hashTab;
      
      Entry tabLocation = getEntryByType(DT_HASH);
      
      SymbolTable symTab = findSymbolTable();
      StringTable strTab = findStringTable();
      
      if (symTab == null || strTab == null || tabLocation == null)
        return null;
      
      hashTab = new SymbolHashTable(symTab, strTab, tabLocation.value);
      return hashTab;
    }
    
    public SymbolTable findSymbolTable() throws IOException {
      
      if (symTab != null)
        return symTab;
      
      Entry tabLocation = getEntryByType(DT_SYMTAB);
      Entry entrySize = getEntryByType(DT_SYMENT);
      
      if (tabLocation == null || entrySize == null)
        return null;
      
      symTab = new SymbolTable(tabLocation.value, entrySize.value); 
      return symTab; 
    }
    
    public StringTable findStringTable() throws IOException {
      
      if (strTab != null)
        return strTab;
      
      Entry tabLocation = getEntryByType(DT_STRTAB);
      Entry tabSize = getEntryByType(DT_STRSZ);
      
      if (tabLocation == null || tabSize == null)
        return null;
      
      strTab = new StringTable(tabLocation.value, tabSize.value); 
      return strTab;
    }
    
    public RelocationTable findJmpRelTable() throws IOException {

      Entry tabLocation = getEntryByType(DT_JMPREL);
      Entry tabSize = getEntryByType(DT_PLTRELSZ);
      Entry entrySize = getEntryByType(DT_RELENT);
      Entry entryType = getEntryByType(DT_PLTREL);
      
      if (tabLocation == null || tabSize == null || entrySize == null | entryType == null)
        return null;
           
      relaTab = new RelocationTable(tabLocation.value, tabSize.value, entrySize.value, entryType.value == DT_RELA); 
      
      return relaTab;
    }
    
    public RelocationTable findRelaTable() throws IOException {
      
      if (relaTab != null)
        return relaTab;
      
      Entry tabLocation = getEntryByType(DT_RELA);
      Entry tabSize = getEntryByType(DT_RELASZ);
      Entry entrySize = getEntryByType(DT_RELAENT);
      
      if (tabLocation == null || tabSize == null || entrySize == null)
        return null;
      
      relaTab = new RelocationTable(tabLocation.value, tabSize.value, entrySize.value, true); 
      
      return relaTab;
    }
    
    public RelocationTable findRelTable() throws IOException {
      
      if (relTab != null)
        return relTab;
      
      Entry tabLocation = getEntryByType(DT_REL);
      Entry tabSize = getEntryByType(DT_RELSZ);
      Entry entrySize = getEntryByType(DT_RELENT);
      
      if (tabLocation == null || tabSize == null || entrySize == null)
        return null;
      
      relTab = new RelocationTable(tabLocation.value, tabSize.value, entrySize.value, false);
      return relTab;
    }
    
    @Override
    public String toString() {
      String result = "";
      
      for(Entry entry : entries)
        result = result + entry.toString() + "\n";
      
      return result;
    }
  }

  /**
   * Header representing a segment in the process (e.g. stack, heap, code aka
   * text) in the ELF file. These are known as program header's in the ELF
   * literature, but they don't represent programs, rather separate segments.
   */
  @SuppressWarnings("unused")
  public class SegmentHeader {
    /**
     * Type of the segment
     */
    public final int p_type;

    /**
     * Null header, contains no data and can be ignored
     */
    public static final int PT_NULL = 0;

    /**
     * A loadable segment
     */
    public static final int PT_LOAD = 1;

    /**
     * Segment containing dynamic linking information
     */
    public static final int PT_DYNAMIC = 2;

    /**
     * A segment containing a string to invoke as interpreter for this file
     */
    public static final int PT_INTERP = 3;

    /**
     * A segment describing the location and size of auxiliary information
     */
    public static final int PT_NOTE = 4;

    /**
     * A reserved segment type with unspecified semantics
     */
    public static final int PT_SHLIB = 5;

    /**
     * A segment describing the ELF's header, present once before any loadable
     * segments
     */
    public static final int PT_PHDR = 6;

    /**
     * Thread local storage (TLS) segment
     */
    public static final int PT_TLS = 7;


    /**
     * Start of OS reserved segment types
     */

    /**
     * SUN unwind table segment
     */
    public static final int PT_SUNW_UNWIND = 0x6464e550;

    /**
     * GCC .eh_frame_hdr segment
     */
    public static final int PT_GNU_EH_FRAME = 0x6474e550;

    /**
     * Indicates stack executability
     */
    public static final int PT_GNU_STACK = 0x6474e551;

    /**
     * Read-only after relocation
     */
    public static final int PT_GNU_RELRO = 0x6474e552;

    /**
     * Start of SUN reserved segments
     */

    /**
     * The array element has the same attributes as a PT_LOAD element and is
     * used to describe a .SUNW_bss section.
     */
    public static final int PT_SUNWBSS = 0x6ffffffa;

    /**
     * Describes a process stack. Only one PT_SUNWSTACK element can exist. Only
     * access permissions, as defined in the p_flags field, are meaningful.
     */
    public static final int PT_SUNWSTACK = 0x6ffffffb;

    /**
     * Reserved for internal use by dtrace
     */
    public static final int PT_SUNWDTRACE = 0x6ffffffc;

    /**
     * Specifies hardware capability requirements
     */
    public static final int PT_SUNWCAP = 0x6ffffffd;

    /**
     * End of SUN reserved segments
     */

    /**
     * End of OS reserved segment types
     */


    /**
     * Offset of first byte of segment data in file
     */
    public final int p_offset;

    /**
     * Virtual address of first byte in segment
     */
    public final int p_vaddr;

    /**
     * Corresponding physical addressed used by some systems
     */
    public int p_paddr;

    /**
     * Size of segment in file
     */
    public final int p_filesz;

    /**
     * Size of segment in memory
     */
    public final int p_memsz;

    /**
     * Read/Write/Execute flags for segment in memory
     */
    public int p_flags;

    /**
     * Executable flag
     */
    public static final int PF_X = 0x1;

    /**
     * Writable flag
     */
    public  static final int PF_W = 0x2;

    /**
     * Readable flag
     */
    public  static final int PF_R = 0x4;

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
    private SegmentHeader(BinaryReader reader) throws IOException {
      p_type = reader.readInt();
      p_offset = reader.readInt();
      p_vaddr = reader.readInt();
      p_paddr = reader.readInt();
      p_filesz = reader.readInt();
      p_memsz = reader.readInt();
      p_flags = reader.readInt();
      p_align = reader.readInt();
      // Move file onto next program segment header offset
      reader.rFile.skipBytes(header.getProgramSegmentHeaderSize() - 32);
    }
    
    public DynamicSection asDynamicSection() throws IOException {
      return new DynamicSection(this);
    }
    
    void create(ProcessSpace ps) {
      create(ps, 0);
    }

    /**
     * Load/create the program segment
     */
    void create(ProcessSpace ps, int offset) {
      switch (p_type) {
      case PT_NULL: // Null header, contains no data and can be ignored
        break;
      
      case PT_LOAD: // A loadable segment
        try {
          createSegment(ps.memory, reader.rFile, p_offset, p_vaddr + offset, p_filesz,
              p_memsz, (p_flags & PF_R) != 0, (p_flags & PF_W) != 0,
              (p_flags & PF_X) != 0);
        } catch (MemoryMapException e) {
          throw new Error("Error in creating: " + this, e);
        }
        break;
      case PT_PHDR:
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
      //case PT_PHDR: // A segment describing the ELF's header; present once
                    // before any loadable segments
      default:
        return;
        //throw new Error("Segment type " + toString() + " not yet supported");
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
        SegmentRange range = SegmentRange.fromInteger(p_type);
        return range.toString() + "(0x" + Integer.toHexString(p_type) + ")";
      }
    }
  }
}
