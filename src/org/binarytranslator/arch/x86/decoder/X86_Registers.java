/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.x86;

import org.binarytranslator.generic.memory.Registers;

public class X86_Registers extends Registers {
  String[] name32 = {"eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi"};

  private int[] GP32 = new int[8];
  //private short[] GP16 = new short[8];
  //private byte[] GP8 = new byte[8];

  private short[] Seg16 = new short[6];

  private int flags;

  private short fpControlRegister;
  private int mxcsr;

  public X86_Registers() {
	 super();

	 fpControlRegister = 0x037f;
	 mxcsr = 0x1F80;
  }

  public int readGP32(int reg) {
	 //	System.out.println("readGP32 " + name32[reg]);
	 //System.out.println(Integer.toHexString(GP32[reg]));
	 return GP32[reg];
  }
    
  public short readGP16(int reg) {
	 //System.out.println("readGP16");
	 //System.out.println(Integer.toHexString((GP32[reg] & 0xffff)));
	 return (short) (GP32[reg] & 0xffff);
  }
    
  public byte readGP8(int reg) {
	 //System.out.println("readGP8");
	 if (reg < 4) {
		//  System.out.println(Integer.toHexString(GP32[reg] & 0xff));
		return (byte) (GP32[reg] & 0xff);
	 } else {
		//System.out.println(Integer.toHexString((GP32[reg - 4] & 0xff00) >>> 8));
		return (byte) ((GP32[reg - 4] & 0xff00) >>> 8);
	 }
  }

  public short readSeg16(int reg) {
	 //System.out.println("readSeg16");
	 return Seg16[reg];
  }

  public int readFlags() {
	 //System.out.println("readFlags");
	 //System.out.println(Integer.toHexString(flags));
	 return flags;
  }

  public short readFpControlRegister() {
	 //System.out.println("readFpControlRegister" + Integer.toHexString(fpControlRegister));
	 return fpControlRegister;
  }

  public int readMxcsr() {
	 //System.out.println("readMxcsr" + Integer.toHexString(mxcsr));
	 return mxcsr;
  }

  /*    public boolean readFlagBit(int reg, int pos) {
  //System.out.println("readFlagBit");
  return flags[pos];
  }*/

  public void writeGP32(int reg, int val) {
	 //System.out.println("writeGP32 " + name32[reg]);
	 //System.out.println(Integer.toHexString(val));
	 GP32[reg] = val;
  }

  public void writeGP16(int reg, short val) {
	 //System.out.println("writeGP16");
	 //System.out.println(Integer.toHexString((GP32[reg] & 0xffff0000) | (((int) val) & 0x0000ffff)));
	 GP32[reg] = (GP32[reg] & 0xffff0000) | (((int) val) & 0x0000ffff);
  }

  public void writeGP8(int reg, byte val) {
	 //System.out.println("writeGP8");
	 if (reg < 4) {
		//  System.out.println(Integer.toHexString((GP32[reg] & 0xffffff00) | (((int) val) & 0x000000ff)));
		GP32[reg] = (GP32[reg] & 0xffffff00) | (((int) val) & 0x000000ff);
	 } else {
		//System.out.println(Integer.toHexString((GP32[reg - 4] & 0xffff00ff) | 
		//				   ((((int) val) << 8) & 0x0000ff00)));
		GP32[reg - 4] = (GP32[reg - 4] & 0xffff00ff) | 
		  ((((int) val) << 8) & 0x0000ff00);
	 }
  }

  public void writeSeg16(int reg, short val) {
	 Seg16[reg] = val;
  }

  public void writeFlags(int val) {
	 //System.out.println("writeFlags");
	 //System.out.println(Integer.toHexString(val));
	 flags = val;
  }

  public void writeFpControlRegister(short val) {
	 //System.out.println("writeFpControlRegister" + Integer.toHexString(val));
	 fpControlRegister = val;
  }

  public void writeMxcsr(int val) {
	 //System.out.println("writeMxcsr " + Integer.toHexString(val));
	 mxcsr = val;
  }

  /*    public void writeFlagBit(int reg, int pos, boolean val) {
		  flags[pos] = val;
		  }*/

  public int GP32Num() {
	 return 8;
  }

  public int GP16Num() {
	 return 8;
  }

  public int GP8Num() {
	 return 8;
  }

  public int Seg16Num() {
	 return 6;
  }

  public int FlagsNum() {
	 return 1;
  }
}
