package org.binarytranslator.generic.os.abi.linux;

import org.binarytranslator.generic.memory.Memory;

/** Namespace for all structures. */
public class LinuxStructureFactory {
  
  public static class stat64_3 {
    public int st_dev;
    public short st_mode;
    
    /** Implemented with a reflection by a base class*/
    public void store(Memory m) {}
    public void read(Memory m) {}
  }
  
  /** Solution 2 - automatically generated class. Fast, but pretty unflexible.*/
  public static class stat64_2 {
    
    private Memory mem;
    private int addr;
    
    void st_dev(int value) {
      mem.store32(addr + 3, value);
    }
    
    int st_dev() {
      return mem.load32(addr + 3);
    }
  }
  
  /** Solution 3 - class members created from factory method. More flexible, but higher overhead. */
  public static class stat64 extends Structure {
    public final _Int st_dev = newInt();
    public final _Short st_mode = newShort();
  }
  
  
  
  
  protected static class Structure {
    
    protected Memory memory;
    private int curOffset = 0;
    
    public abstract class StructureElement<T> {

      protected int address;
      public abstract T get();
      public abstract void set(T value);
    }
    
    protected _Int newInt() {
      _Int i = new _Int(curOffset);
      curOffset += 4;
      
      return i;
    }
    
    protected _Short newShort() {
      _Short s = new _Short(curOffset);
      curOffset += 2;
      
      return s;
    }

    public class _Int extends StructureElement<Integer> {
      
      private _Int(int offset) {
        this.address = offset;
      }

      @Override
      public Integer get() {
        return memory.load32(address);
      }

      @Override
      public void set(Integer value) {
        memory.store32(address, value);
      }
    }

    public class _Short extends StructureElement<Short> {
      
      private _Short(int offset) {
        this.address = offset;
      }

      @Override
      public Short get() {
        return (short) memory.loadUnsigned16(address);
      }

      @Override
      public void set(Short value) {
        memory.store16(address, value);
      }
    }
  }
}
