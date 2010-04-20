/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.VM;
import java.util.Enumeration;

/**
 *
 * @author ian
 */
public class GenericPhysicalDefUse {
  public static int getMaskTSPUses() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskTSPUses;
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskTSPUses;      
    }
  }
  public static int getMaskTSPDefs(){
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskTSPUses;
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskTSPUses;
    }
  }
  public static String getString(int code){
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.getString(code);
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.getString(code);
    }
  }
  public static Enumeration<Register> enumerate(int code, IR ir) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.enumerate(code,ir);
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.enumerate(code,ir);
    }
  }
  public static Enumeration<Register> enumerateAllImplicitDefUses(IR ir) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.enumerateAllImplicitDefUses(ir);
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.enumerateAllImplicitDefUses(ir);
    }
  }
  public static int mask() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.mask;
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.mask;
    }
  }
  public static int maskcallDefs() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskcallDefs;
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskcallDefs;
    }
  }
  public static int maskcallUses() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskcallUses;
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskcallUses;
    }
  }
  public static int maskAF_CF_OF_PF_SF_ZF() {
    VM._assert(VM.BuildForIA32);
    return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskAF_CF_OF_PF_SF_ZF;
  }
  public static int maskCF() {
    VM._assert(VM.BuildForIA32);
    return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskCF;
  }
  public static int maskIEEEMagicUses() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskIEEEMagicUses;
    } else {
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskIEEEMagicUses;
    }
  }
}
