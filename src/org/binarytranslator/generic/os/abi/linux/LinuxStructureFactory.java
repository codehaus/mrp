package org.binarytranslator.generic.os.abi.linux;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.binarytranslator.generic.memory.Memory;

/** Namespace for all structures. */
public class LinuxStructureFactory {
  
  public final stat64 new_stat64() {
    return new stat64();
  }
  
  public class stat64 extends Structure {
    @_unsigned short st_dev;
    @_unsigned @_padding(10) byte __pad0;
    @_unsigned @_long long __st_ino;
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
    
    public stat64() {
      super(new String[]{ "st_dev", "__pad0", "__st_ino", "st_mode", "st_nlink", "st_uid",
          "st_gid", "st_rdev", "__pad3", "st_size", "st_blksize", "st_blocks", "__pad4", "st_atime",
          "__pad5", "st_mtime", "__pad6", "st_ctime", "__pad7", "st_ino"});
    }
  }
  
  private @interface _long {}
  private @interface _unsigned {}
  private @interface _padding { int value(); }
  
  private class Structure {
    
    /** A list of all structure members in the order that they appear in the source code. */
    protected Field[] members;
    
    protected Structure(String[] memberNames) {
      
      Class myType = getClass();
      members = new Field[memberNames.length];
      
      for (int i = 0; i < memberNames.length; i++) {
        try {
          Field field = myType.getField(memberNames[i]);
          members[i] = field;
        }
        catch (NoSuchFieldException e) {
          throw new RuntimeException("Invalid field: " + memberNames[i] + " in struct: " + myType.getSimpleName(), e);
        }
      }
    }
    
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
  
  protected final StructureAdapter createStructureAdapter(Memory mem, int addr) {
    return new DefaultStructureAdapter(mem, addr);
  }
  
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
    
    public abstract void skipPadding(Field field, int count);
  }
  
  protected static class DefaultStructureAdapter extends StructureAdapter {
    
    private final Memory mem;
    private int addr;
    
    public DefaultStructureAdapter(Memory mem, int addr) {
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
  }
}
