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
package org.binarytranslator.generic.os.abi.linux;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.binarytranslator.generic.memory.Memory;

/**
 * Linux heavily uses certain c-structs when to pass parameters for system calls.
 * To allow the pearcolator linux ABIs to share a common set of structure definitions, this
 * namespace provides a factory for Java representations of the most commonly used structures.
 * Each structure provides methods to read the structure from or write it to memory.
 * 
 * @author Michael Baer
 */
public class LinuxStructureFactory {
  
  /** A factory method which will create a stat64 structure.*/
  public final stat64 new_stat64() {
    return new stat64();
  }
  
  /**
   * The stat64 structure, as defined in <code>include\linux\stat64.h</code>.
   */
  public class stat64 extends Structure {
    @_unsigned short st_dev;
    @_unsigned @_padding(10) byte __pad0;
    @_unsigned long __st_ino;
    @_unsigned int st_mode;
    @_unsigned int st_nlink;
    @_unsigned long st_uid;
    @_unsigned long st_gid;
    @_unsigned short st_rdev;
    @_unsigned @_padding(10) byte __pad3;
    @_long long st_size;
    @_unsigned long st_blksize;
    @_unsigned long st_blocks;
    @_unsigned @_padding(1) long __pad4;   
    @_unsigned long st_atime;
    @_unsigned @_padding(1) long __pad5;
    @_unsigned long st_mtime;
    @_unsigned @_padding(1) long __pad6;
    @_unsigned long st_ctime;
    @_unsigned @_padding(1) long __pad7;   
    @_unsigned @_long long st_ino;
    
    /**
     * At the moment, we have to enumerate the members of a structure in the constructor 
     * (in the order of definition within the c file), because the JVM specs say that no code shall
     * rely on the order of the fields returned by reflection methods (namely {@link Class#getFields()}.
     * 
     * Therefore, we have to persist the order of fields, which is why we are enumerating them again.
     * However, this is not necessary the final solution. Possibly we're going to introduce a 
     * Java annotation that keeps the information about the order - or we might even solve the problem
     * completely differently.
     *
     */
    protected stat64() {
      super(new String[]{ "st_dev", "__pad0", "__st_ino", "st_mode", "st_nlink", "st_uid",
          "st_gid", "st_rdev", "__pad3", "st_size", "st_blksize", "st_blocks", "__pad4", "st_atime",
          "__pad5", "st_mtime", "__pad6", "st_ctime", "__pad7", "st_ino"});
    }
    
    /** This overriden structure enables Michael to debug the pearcolator in windows. 
     * Otherwise, it should not be used, which is why it is deprecated. */
    @Override
    @Deprecated
    public void read(Memory mem, int addr) {
      StructureAdapter reader = createStructureAdapter(mem, addr);
      
      st_dev = reader.loadShort();
      reader.skipPadding(10);
      __st_ino = reader.loadLong();
      st_mode = reader.loadInt();
      st_nlink = reader.loadInt();
      st_uid = reader.loadLong();
      st_gid = reader.loadLong();
      st_rdev = reader.loadShort();
      reader.skipPadding(10);
      st_size = reader.loadLongLong();
      st_blksize = reader.loadLong();
      st_blocks = reader.loadLong();
      __pad4 = reader.loadLong();
      st_atime = reader.loadLong();
      __pad5 = reader.loadLong();
      st_mtime = reader.loadLong();
      __pad6 = reader.loadLong();
      st_ctime = reader.loadLong();
      __pad7 = reader.loadLong();
      st_ino = reader.loadLongLong();
    }
    
    /** 
     * This overriden structure enables Michael to debug the pearcolator in windows. 
     * Otherwise, it should not be used, which is why it is deprecated. */
    @Override
    @Deprecated
    public void write(Memory mem, int addr) {
      StructureAdapter writer = createStructureAdapter(mem, addr);
      writer.storeShort(st_dev);
      writer.skipPadding(10);
      writer.storeLong(__st_ino);
      writer.storeInt(st_mode);
      writer.storeInt(st_nlink);
      writer.storeLong(st_uid);
      writer.storeLong(st_gid);
      writer.storeShort(st_rdev);
      writer.skipPadding(10);
      writer.storeLongLong(st_size);
      writer.storeLong(st_blksize);
      writer.storeLong(st_blocks);
      writer.storeLong(__pad4);
      writer.storeLong(st_atime);
      writer.storeLong(__pad5);
      writer.storeLong(st_mtime);
      writer.storeLong(__pad6);
      writer.storeLong(st_ctime);
      writer.storeLong(__pad7);
      writer.storeLongLong(st_ino);
    }
  }
  
  private @interface _long {}
  private @interface _unsigned {}
  private @interface _padding { int value(); }
  
  private class Structure {
    
    /** A list of all structure members in the order that they appear in the source code. */
    protected Field[] members;
    
    /**
     * This constructor is uncommented because reflection does not work when debugging pearcolator 
     * under windows, which is what I (=Michael Baer) am currently doing.
     * 
     * However, as soon as this is done, the deprecated-tag shall be removed and the code in this 
     * function be uncommented.
     * @param memberNames
     */
    @Deprecated
    protected Structure(String[] memberNames) {
      
      /*Class myType = getClass();
      members = new Field[memberNames.length];
      
      for (int i = 0; i < memberNames.length; i++) {
        try {
          Field field = myType.getField(memberNames[i]);
          members[i] = field;
        }
        catch (NoSuchFieldException e) {
          throw new RuntimeException("Invalid field: " + memberNames[i] + " in struct: " + myType.getSimpleName(), e);
        }
      }*/
    }
    
    /**
     * Read all members of this structure from memory into the structure.
     * 
     * @param mem
     *  The memory that the structure is read from.
     * @param addr
     *  The address of the structure's beginning in the memory.
     */
    public void read(Memory mem, int addr) {

      // get a structure writer, which will keep track of the object offsets
      StructureAdapter reader = createStructureAdapter(mem, addr);

      // traverse all public fields
      for (Field f : members) {
        if (!Modifier.isPrivate(f.getModifiers())) {
          try {
            // store each public field one after the other
            Class<?> type = f.getClass();

            if (type == int.class) {
              f.setInt(this, reader.loadInt());
              continue;
            }

            if (type == long.class) {
              
              if (f.getAnnotation(_long.class) != null)
                f.setLong(this, reader.loadLongLong());
              else
                f.setLong(this, reader.loadLong());
              continue;
            }

            if (type == short.class) {
              f.setShort(this, reader.loadShort());
              continue;
            }

            if (type == byte.class) {
              f.setByte(this, reader.loadByte());
              continue;
            }

            throw new RuntimeException(
                "Unknown data type while persisting struct: " + this);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(
                "Unknown data type while persisting struct: " + this, e);
          }
        }
      }
    }

    /**
     * Writes the contents of the structure from its members into memory.
     * 
     * @param mem
     *  The Memory into which the structure shall be written.
     * @param addr
     *  The address, to which the structure is written.
     */
    public void write(Memory mem, int addr) {

      // get a structure writer, which will keep track of the object offsets
      StructureAdapter writer = createStructureAdapter(mem, addr);

      // traverse all public fields
      for (Field f : members) {
        if (!Modifier.isPrivate(f.getModifiers())) {
          try {
            // store each public field one after the other
            Class<?> type = f.getClass();

            // check if that data type represents a certain amount of padding
            _padding padding = f.getAnnotation(_padding.class);
            if (padding != null) {
              writer.skipPadding(f, padding.value());
              continue;
            }

            if (type == int.class) {
              writer.storeInt(f.getInt(this));
              continue;
            }

            if (type == long.class) {
              if (f.getAnnotation(_long.class) != null)
                writer.storeLongLong(f.getLong(this));
              else
                writer.storeLong(f.getLong(this));
              
              continue;
            }

            if (type == short.class) {
              writer.storeShort(f.getShort(this));
              continue;
            }

            if (type == byte.class) {
              writer.storeByte(f.getByte(this));
              continue;
            }

            throw new RuntimeException(
                "Unknown data type while persisting struct: " + this);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(
                "Unknown data type while persisting struct: " + this, e);
          }
        }
      }
    }
    
    /**
     * Converts a structure into a c-like, human readable format */
    @Override
    public String toString() {
      
      StringBuilder struct = new StringBuilder();
      struct.append("struct ");
      struct.append(getClass().getSimpleName());
      struct.append(" {\n");
      
      // traverse all struct members      
      for (Field f : members) {
        if (!Modifier.isPrivate(f.getModifiers())) {
          try {
            struct.append(f.getName());
            struct.append(" = ");
            struct.append(f.get(this).toString());
          }
          catch (IllegalAccessException e) {}
          
          struct.append('\n');
        }
      }
      
      struct.append('}');
      
      return struct.toString();
    }
  }
  
  /**
   *  A factory method that creates a {@link StructureAdapter}. StructureAdapters are used to
   *  encapsulate the architecture specifics (like <code>sizeof(int)</code> and padding) when 
   *  reading a structure.
   * 
   *  Implement your own StructureAdapter when you need to override the default implementations of
   *  how a structure is being read, while still keeping the default structures defined here. 
   *  
   * @param mem
   * @param addr
   * @return
   */
  protected final StructureAdapter createStructureAdapter(Memory mem, int addr) {
    return new StructureAdapter_32Bit(mem, addr);
  }
  
  /**
   * A StructureAdapter abstracts the properties (size, padding, maybe endianess) of the elements
   * within a structure. Provide an own implementation of this class when the default data sizes 
   * and member alignments are not suitable for your architecture.
   * 
   * Structures are always being read in the order of element definition. Therefore, any implementation
   * of StructureAdapter can rely on the load/store functions being called in order, from the first
   * to the last structure element.
   *
   */
  protected static abstract class StructureAdapter {
    
    public abstract byte  loadByte();
    public abstract short loadShort();
    public abstract int   loadInt();
    public abstract long  loadLong();
    public abstract long  loadLongLong();
    
    public abstract void storeByte(byte value);
    public abstract void storeShort(short value);
    public abstract void storeInt(int value);
    public abstract void storeLong(long value);
    public abstract void storeLongLong(long value);
    
    /**
     * Some structures contain rather large padding fields. To prevent use from reading these from
     * memory byte-by-byte, this function may be used to skip a padding of type <code>field</code>
     * that occurs <code>count</code> times.
     * 
     * For example, when skipping a member like this <code>char pad[10]</char> the <code>field</code>
     * would identify this as a char field, while the <code>count</code> member would say that we want
     * to skip the char 10 times.
     * 
     * @param field
     *  The type of the field that is being skipped.
     * @param count
     *  The number of times this field as a padding in the structure.
     */
    public abstract void skipPadding(Field field, int count);
    
    /** 
     * This function is used temporarly to debug pearcolator in windows. However,
     * it should normally not be invoked and as soon as windows-debugging is done, this
     * function shall be removed from the source (along with all invokations of it).*/
    @Deprecated
    public abstract void skipPadding(int count);
  }
  
  /**
   *  A default implementation of a structure adapter. It has the following properties:
   * 
   *  <ul>
   *    <li>Every member is memory aligned to its size (i.e. a 4-byte data type is aligned to 4-byte 
   *    boundaries</li>
   *    <li>The minimal needed amount of padding is inserted where the member is not already 
   *    aligned to its size.</li>
   *    <li><code>sizeof(int) == 4</code></li>
   *    <li><code>sizeof(long) == 4</code></li>
   *    <li><code>sizeof(long long) == 8</code></li>
   *    <li><code>sizeof(short) == 2</code></li>
   *    <li><code>sizeof(byte) == 1</code></li>
   *  </ul>
   */
  protected static class StructureAdapter_32Bit extends StructureAdapter {
    
    private final Memory mem;
    private int addr;
    
    public StructureAdapter_32Bit(Memory mem, int addr) {
      this.mem = mem;
      this.addr = addr;
    }
    
    private final void shortAlignAddress() {
      if ((addr & 0xFFFFFFFE) != addr)
        addr = (addr & 0xFFFFFFFE) + 2;
    }
    
    private final void intAlignAddress() {
      if ((addr & 0xFFFFFFFC) != addr)
        addr = (addr & 0xFFFFFFFC) + 4;
    }
    
    private final void longAlignAddress() {
      if ((addr & 0xFFFFFFFC) != addr)
        addr = (addr & 0xFFFFFFFC) + 8;
    }

    @Override
    public void storeByte(byte value) {
      mem.store8(addr, value);
      addr++;
    }

    @Override
    public void storeInt(int value) {
      //make sure that address is int aligned
      intAlignAddress();
      mem.store32(addr, value);
      addr += 4;
    }

    @Override
    public void storeLong(long value) {
      //in a lot of C dialects, longs are treated as ints
      storeInt((int)value);
    }

    @Override
    public void storeLongLong(long value) {
      longAlignAddress();
      
      mem.store32(addr, (int)(value & 0xFFFFFFFF));
      mem.store32(addr, (int)(value >> 32));
      addr += 8;
    }

    @Override
    public void storeShort(short value) {
      shortAlignAddress();
      mem.store16(addr, value);
      addr += 2;
    }

    @Override
    public byte loadByte() {
      return (byte)mem.loadUnsigned8(addr++);
    }

    @Override
    public int loadInt() {
      intAlignAddress();
      int value = mem.load32(addr);
      addr += 4;
      
      return value;
    }

    @Override
    public long loadLong() {
      return loadInt();
    }

    @Override
    public long loadLongLong() {
      longAlignAddress();
      long value = mem.load32(addr);
      addr += 8;
      value = value | (mem.load32(addr) << 32);
      addr += 8;
      
      return value;
    }

    @Override
    public short loadShort() {
      shortAlignAddress();
      short value = (short)mem.loadUnsigned16(addr);
      addr += 2;
      
      return value;
    }
    
    @Override
    public void skipPadding(Field field, int count) {
      
      Class<?> type = field.getType();
      
      if (type == byte.class) {
        addr += count;
        return;
      }
      
      if (type == short.class) {
        shortAlignAddress();
        addr += count * 2;
        return;
      }
      
      if (type == int.class) {
        intAlignAddress();
        addr += count * 4;
        return;
      }
      
      if (type == long.class) {
        
        if (field.getAnnotation(_long.class) != null) {
          longAlignAddress();
          addr += count * 8;          
        }
        else {
          intAlignAddress();
          addr += count * 4;
        }
        
        return;
      }
    }

    @Override
    public void skipPadding(int count) {
      addr += count;
    }
  }
}
