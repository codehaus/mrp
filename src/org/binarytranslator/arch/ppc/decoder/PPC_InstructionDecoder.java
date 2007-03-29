/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.arch.ppc.decoder;

import org.binarytranslator.DBT_Options;
import org.binarytranslator.vmInterface.DBT_OptimizingCompilerException;
import org.binarytranslator.arch.ppc.os.process.PPC_ProcessSpace;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.generic.decoder.InstructionDecoder;
import org.binarytranslator.generic.branch.BranchLogic;

import org.jikesrvm.opt.ir.*;
import org.jikesrvm.*;
import org.jikesrvm.classloader.*;
import org.jikesrvm.opt.*;
import org.jikesrvm.ppc.PPC_Disassembler;

import java.util.Enumeration;
import java.util.NoSuchElementException;

import java.util.*;

/**
 * This class is the super class of all instruction translators, it
 * implements default methods that just throw errors
 */
public class PPC_InstructionDecoder extends InstructionDecoder implements OPT_Operators {
    /* Different instruction formats */
  
    /** Invalid opcode */
    protected static final int  INVALID_OP = 0;
    /** D form opcode */
    protected static final int  D_FORM   = 1;
    /** B form opcode */
    protected static final int  B_FORM   = 2;
    /** I form opcode */
    protected static final int  I_FORM   = 3;
    /** SC form opcode */
    protected static final int  SC_FORM  = 4;
    /** X form opcode */
    protected static final int  X_FORM   = 5;
    /** XL form opcode */
    protected static final int  XL_FORM  = 6;
    /** XFX form opcode */
    protected static final int  XFX_FORM = 7;
    /** XFL form opcode */
    protected static final int  XFL_FORM = 8;
    /** XO form opcode */
    protected static final int  XO_FORM  = 9;
    /** A form opcode */
    protected static final int  A_FORM   = 10;
    /** M form opcode */
    protected static final int  M_FORM   = 11;
    /** Exetended form opcode */
    protected static final int  EXTENDED = 12;

    /* Types of comparison */

    /** Unsigned integer comparison */
    static final int UNSIGNED_INT_CMP=1;
    /** Signed integer comparison */
    static final int SIGNED_INT_CMP=2;
    /** Floating point unordered comparison */
    static final int FP_CMPU=3;

    /**
     * Look up table to find instruction translator, performed using the
     * instruction opcode.
     */
    static final OpCodeLookUp[] opCodeLookUp = {
	/*   OPCD                        form    mnemonic    translator */
	/*   ----                        ----    --------    ---------- */
	/*    0,   */  null,
	/*    1,   */  null,
	/*    2,   */  new OpCodeLookUp (D_FORM, "tdi",      new tdi_decoder()),
	/*    3,   */  new OpCodeLookUp (D_FORM, "twi",      new twi_decoder()), 
	/*    4,   */  null,
       
	/*    5,   */  null,
	/*    6,   */  null,
	/*    7,   */  new OpCodeLookUp (D_FORM, "mulli",    new mulli_decoder()),
	/*    8,   */  new OpCodeLookUp (D_FORM, "subfic",   new subfic_decoder()),
	/*    9,   */  null,
    
	/*    10,  */  new OpCodeLookUp (D_FORM, "cmpli",    new cmpli_decoder()),
	/*    11,  */  new OpCodeLookUp (D_FORM, "cmpi",     new cmpi_decoder()),
	/*    12,  */  new OpCodeLookUp (D_FORM, "addic",    new addic_decoder()),
	/*    13,  */  new OpCodeLookUp (D_FORM, "addic.",   new addic__decoder()),
	/*    14,  */  new OpCodeLookUp (D_FORM, "addi",     new addi_decoder()),
       
	/*    15,  */  new OpCodeLookUp (D_FORM, "addis",    new addis_decoder()),
	/*    16,  */  new OpCodeLookUp (B_FORM, "bc",       new bc_decoder()),
	/*    17,  */  new OpCodeLookUp (SC_FORM,"sc",       new sc_decoder()),
	/*    18,  */  new OpCodeLookUp (I_FORM, "b",        new b_decoder()),
	/*    19,  */  new OpCodeLookUp (XL_FORM,"XL_FORM",  new xlForm_decoder()),
    
	/*    20,  */  new OpCodeLookUp (M_FORM, "rlwimi",   new rlwimi_decoder()),
	/*    21,  */  new OpCodeLookUp (M_FORM, "rlwinm",   new rlwinm_decoder()),

	/*    22,  */  null,
	/*    23,  */  new OpCodeLookUp (M_FORM, "rlwnm",    new rlwnm_decoder()),
	/*    24,  */  new OpCodeLookUp (D_FORM, "ori",      new ori_decoder()),
    
	/*    25,  */  new OpCodeLookUp (D_FORM, "oris",     new oris_decoder()),
	/*    26,  */  new OpCodeLookUp (D_FORM, "xori",     new xori_decoder()),
	/*    27,  */  new OpCodeLookUp (D_FORM, "xoris",    new xoris_decoder()),
	/*    28,  */  new OpCodeLookUp (D_FORM, "andi.",    new andi__decoder()),
	/*    29,  */  new OpCodeLookUp (D_FORM, "andis.",   new andis__decoder()),
    
	/*    30,  */  null,
	/*    31,  */  new OpCodeLookUp (EXTENDED, "EXT 31", new extended31_decoder()),
	/*    32,  */  new OpCodeLookUp (D_FORM, "lwz",      new lwz_decoder()),
	/*    33,  */  new OpCodeLookUp (D_FORM, "lwzu",     new lwzu_decoder()),
	/*    34,  */  new OpCodeLookUp (D_FORM, "lbz",      new lbz_decoder()),
    
	/*    35,  */  new OpCodeLookUp (D_FORM, "lbzu",     new lbzu_decoder()),
	/*    36,  */  new OpCodeLookUp (D_FORM, "stw",      new stw_decoder()),
	/*    37,  */  new OpCodeLookUp (D_FORM, "stwu",     new stwu_decoder()),
	/*    38,  */  new OpCodeLookUp (D_FORM, "stb",      new stb_decoder()),
	/*    39,  */  new OpCodeLookUp (D_FORM, "stbu",     new stbu_decoder()),
    
	/*    40,  */  new OpCodeLookUp (D_FORM, "lhz",      new lhz_decoder()),
	/*    41,  */  new OpCodeLookUp (D_FORM, "lhzu",     new lhzu_decoder()),
	/*    42,  */  new OpCodeLookUp (D_FORM, "lha",      new lha_decoder()),
	/*    43,  */  new OpCodeLookUp (D_FORM, "lhau",     new lhau_decoder()),
	/*    44,  */  new OpCodeLookUp (D_FORM, "sth",      new sth_decoder()),
    
	/*    45,  */  new OpCodeLookUp (D_FORM, "sthu",     new sthu_decoder()),
	/*    46,  */  new OpCodeLookUp (D_FORM, "lmw",      new lmw_decoder()),
	/*    47,  */  new OpCodeLookUp (D_FORM, "stmw",     new stmw_decoder()),
	/*    48,  */  new OpCodeLookUp (D_FORM, "lfs",      new lfs_decoder()),
	/*    49,  */  new OpCodeLookUp (D_FORM, "lfsu",     new lfsu_decoder()),
    
	/*    50,  */  new OpCodeLookUp (D_FORM, "lfd",      new lfd_decoder()),
	/*    51,  */  new OpCodeLookUp (D_FORM, "lfdu",     new lfdu_decoder()),
	/*    52,  */  new OpCodeLookUp (D_FORM, "stfs",     new stfs_decoder()),
	/*    53,  */  new OpCodeLookUp (D_FORM, "stfsu",    new stfsu_decoder()),
	/*    54,  */  new OpCodeLookUp (D_FORM, "stfd",     new stfd_decoder()),
    
	/*    55,  */  new OpCodeLookUp (D_FORM, "stfdu",    new stfdu_decoder()),
	/*    56,  */  null,
	/*    57,  */  null,
	/*    58,  */  null,
	/*    59,  */  new OpCodeLookUp (EXTENDED, "EXT 59", new extended59_decoder()),

	/*    60,  */  null,
	/*    61,  */  null,
	/*    62,  */  null,
	/*    63,  */  new OpCodeLookUp (EXTENDED, "EXT 63", new extended63_decoder())
    };

    /**
     * Constructor
     */
    protected PPC_InstructionDecoder() {
    }

    /**
     * Return a range of bits from an int
     * @param x value to return range of bits from
     * @param n the start of the range of bits
     * @param m the end of the range of bits
     * @return the specified bits
     */
    protected static final int bits (int x, int n, int m) {
	return ((x >> (31-m)) & ((1 << (m-n+1)) - 1));
    }

    /**
     * Give the decoder for a given instruction. Extended instruction
     * decoders should return the appropriate instruction from opcode 2.
     * @param the instruction to get the decoder for
     * @param the appropriate instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr){
	int primaryOpcode =  bits(instr, 0, 5);
	return opCodeLookUp[primaryOpcode].decoder.getDecoder(instr);
    }
    /**
     * Static version of getDecoder, to descend the decoder tree
     */
    public static PPC_InstructionDecoder findDecoder(int instr) {
	int primaryOpcode =  bits(instr, 0, 5);
	return opCodeLookUp[primaryOpcode].decoder.getDecoder(instr);
    }

    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	ps.currentInstruction = ps.memoryLoad32(ps.getCurrentInstructionAddress());
	try {
	    return getDecoder(ps.currentInstruction).interpretInstruction(ps);
	}
	catch(NullPointerException e) {
	    throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
	}
    }
    /**
     * Following interpreting an instruction, move the program counter
     * along, set the current instruction and find the appropriate
     * decoder.
     */
    protected static PPC_InstructionDecoder moveInstructionOnAndReturnDecoder(PPC_ProcessSpace ps) {
	ps.setCurrentInstructionAddress(ps.getCurrentInstructionAddress() + 4);
	ps.currentInstruction = ps.memoryLoad32(ps.getCurrentInstructionAddress());
	return findDecoder(ps.currentInstruction);
    }

    /**
     * Translate a single instruction
     * @param ppc2ir the object containing the translation sequence
     * @param ps the process space of the translation
     * @param pc the address of the instruction to translate
     * @return the address of the next instruction or -1 if this
     * instruction has branched to the end of the trace
     */
    public static int translateInstruction(PPC2IR ppc2ir, PPC_ProcessSpace ps, PPC_Laziness lazy, int pc) {
	int instr = ps.memoryLoad32(pc);

	if(DBT_Options.debugInstr) {
	    System.out.println(lazy.makeKey(pc) + PPC_Disassembler.disasm(instr, pc) + " " + ppc2ir.getCurrentBlock() + " " + ppc2ir.numberOfInstructions);
	}
	 
	int primaryOpcode =  bits(instr, 0, 5);
	if (opCodeLookUp[primaryOpcode] == null) {
	    if(DBT_Options.debugInstr) {
		System.out.println("Unknown extended instruction. Primary " + primaryOpcode);
	    }
	    ppc2ir.plantThrowBadInstruction((PPC_Laziness)lazy.clone(), pc);
	    return -1;
	    // throw new Error("Unknown extended instruction. Primary " + primaryOpcode);
	}
	int form = opCodeLookUp[primaryOpcode].getForm();
	 
	switch(form)  /* decode known instruction format */
	    {
	    case D_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateD_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,31));
	    case B_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateB_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,29), bits(instr,30,30), instr & 0x1);
	    case I_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateI_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,29), bits(instr,30,30), instr & 0x1);
	    case SC_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateSC_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,29), bits(instr,30,30), bits(instr,31,31));
	    case X_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateX_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), bits(instr,21,30), instr & 0x1);
	    case XFX_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateXFX_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,20), bits(instr,21,30), instr & 0x1);
	    case XFL_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateXFL_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,6), bits(instr,7,14), bits(instr,15,15), bits(instr,16,20), bits(instr,21,30), instr & 0x1);
	    case XL_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateXL_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), bits(instr,21,30), instr & 0x1);
	    case XO_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateXO_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), bits(instr,21,21), bits(instr,22,30), instr & 0x1);
	    case M_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateM_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), bits(instr,21,25), bits(instr,26,30), instr & 0x1);
	    case A_FORM:
		return opCodeLookUp[primaryOpcode].decoder.translateA_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), bits(instr,21,25), bits(instr,26,30), instr & 0x1);
	    case EXTENDED:
		return opCodeLookUp[primaryOpcode].decoder.translateEXTENDED(ppc2ir, lazy, pc, instr, primaryOpcode);
	    case INVALID_OP:      /* More work to do... */
	    default:
		throw new Error("Invalid opcode");
	    }
    }

    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int bits6_to_10, int bits11_to_15, int bits16_to_31){
	throw new Error("Attempting to translateD_Form on a non D_Form instruction\n" +
			"Address=0x" + Integer.toHexString(pc) +
			" Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(opcode) +
			" bits6_to_10=" + bits6_to_10 +
			" bits11_to_15=" + bits11_to_15 +
			" bits16_to_31=" + bits16_to_31 +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateDS_FORM(int inst, int opcode){
	throw new Error("Attempting to translateDS_Form on a non DS_Form instruction\n" +
			"Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(inst));
    }
    protected int translateB_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int primaryOpcode, int bits_6_to_10, int bits_11_to_15, int bits16_to_29, int bit30, int bit31) {
	throw new Error("Attempting to translateB_Form on a non B_Form instruction\n" +
			"Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(primaryOpcode) +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateI_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int bits_6_to_29, int bit30, int bit31) {
	throw new Error("Attempting to translateI_Form on a non I_Form instruction\n" +
			"Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(opcode) +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateSC_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int primaryOpcode, int bits_6_to_10, int bits_11_to_15, int bits_16_to_29, int bit30, int bit31){
	throw new Error("Attempting to translateSC_Form on a non SC_Form instruction\n" +
			"Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(primaryOpcode) +
			" bits_6_to_10=" + bits_6_to_10 +
			" bits_11_to_15=" + bits_11_to_15 +
			" bits_16_to_29=" + bits_16_to_29 +
			" bit30=" + bit30 +
			" bit31=" + bit31 +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int bits6_to_10, int bits11_to_15, int bits16_to_20, int secondaryOpcode, int bit31) {
	throw new Error("Attempting to translateX_Form on a non X_Form instruction\n" +
			"Address=0x" + Integer.toHexString(pc) +
			" Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(opcode) +
			" bits6_to_10=" + bits6_to_10 +
			" bits11_to_15=" + bits11_to_15 +
			" bits16_to_20=" + bits16_to_20 +
			" secondaryOpcode=" + secondaryOpcode +
			" bit31=" + bit31+
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateXFX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int bits6_to_10, int bits11_to_20, int secondaryOpcode, int bit31) {
	throw new Error("Attempting to translateXFX_Form on a non XFX_Form instruction\n" +
			"Address=0x" + Integer.toHexString(pc) +
			" Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(opcode) +
			" bits6_to_10=" + bits6_to_10 +
			" bits11_to_20=" + bits11_to_20 +
			" secondaryOpcode=" + secondaryOpcode +
			" bit31=" + bit31 +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateXFL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy,int  pc, int inst, int opcode, int bit6, int bits7_to_14, int bit15, int bits16_to_20, int secondaryOpcode, int bit31) {
	throw new Error("Attempting to translateXFL_Form on a non XFL_Form instruction\n" +
			"Address=0x" + Integer.toHexString(pc) +
			" Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(opcode) +
			" bit6=" + bit6 +
			" bits7_to_14=" + bits7_to_14 +
			" bit15=" + bit15 +
			" secondaryOpcode=" + secondaryOpcode +
			" bit31=" + bit31 +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateXL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int bits6_to_10, int bits11_to_15, int bits16_to_20, int secondaryOpcode, int bit31){
	throw new Error("Attempting to translateXL_Form on a non XL_Form instruction\n" +
			"Address=0x" + Integer.toHexString(pc) +
			" Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(inst) +
			" bits6_to_10=" + bits6_to_10 +
			" bits11_to_15=" + bits11_to_15 +
			" bits16_to_20=" + bits16_to_20 +
			" secondaryOpcode=" + secondaryOpcode +
			" bit31=" + bit31 +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int bits6_to_10, int bits11_to_15, int bits16_to_20, int bit21, int secondaryOpcode, int bit31) {
	throw new Error("Attempting to translateXO_Form on a non XO_Form instruction\n" +
			"Address=0x" + Integer.toHexString(pc) +
			" Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(inst) +
			" bits6_to_10=" + bits6_to_10 +
			" bits11_to_15=" + bits11_to_15 +
			" bits16_to_20=" + bits16_to_20 +
			" bit21=" + bit21 +
			" secondaryOpcode=" + secondaryOpcode +
			" bit31=" + bit31 +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateM_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int bits6_to_10, int bits11_to_15, int bits16_to_20, int bits21_to_25, int bits26_to_30, int bit31){
	throw new Error("Attempting to translateM_Form on a non M_Form instruction\n" +
			"Address=0x" + Integer.toHexString(pc) +
			" Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(inst) +
			" bits6_to_10=" + bits6_to_10 +
			" bits11_to_15=" + bits11_to_15 +
			" bits16_to_20=" + bits16_to_20 +
			" bits21_to_25=" + bits21_to_25 +
			" bits26_to_30=" + bits26_to_30 +
			" bit31=" + bit31 +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int bits6_to_10, int bits11_to_15, int bits16_to_20, int bits21_to_25, int bits26_to_30, int bit31){
	throw new Error("Attempting to translateM_Form on a non M_Form instruction\n" +
			"Address=0x" + Integer.toHexString(pc) +
			" Instruction 0x" + Integer.toHexString(inst) + "\n" +
			"Opcode 0x" + Integer.toHexString(inst) +
			" bits6_to_10=" + bits6_to_10 +
			" bits11_to_15=" + bits11_to_15 +
			" bits16_to_20=" + bits16_to_20 +
			" bits21_to_25=" + bits21_to_25 +
			" bits26_to_30=" + bits26_to_30 +
			" bit31=" + bit31 +
			"\nDisassembly: " + PPC_Disassembler.disasm(inst, pc));
    }
    protected OpCodeLookUp getOpCodeLookUp(int primaryOpcode, int secondaryOpcode) {
	throw new Error("Unknown opcode in opcode look up. Primary opcode " + primaryOpcode + " secondary opcode " + secondaryOpcode);
    }

    protected int translateEXTENDED(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int primaryOpcode){
	int secondaryOpcode =  bits(instr, 21, 30);
	OpCodeLookUp opCodeLookUp = getOpCodeLookUp(primaryOpcode, secondaryOpcode);
	if (opCodeLookUp == null) {
	    if(DBT_Options.debugInstr) {
		System.out.println("Unknown extended instruction. Primary " + primaryOpcode + " secondary " + secondaryOpcode);
	    }
	    ppc2ir.plantThrowBadInstruction((PPC_Laziness)lazy.clone(), pc);
	    return -1;
	    // throw new Error("Unknown extended instruction. Primary " + primaryOpcode + " secondary " + secondaryOpcode);
	}
	switch(opCodeLookUp.getForm())  /* decode known instruction format */
	    {
	    case D_FORM:
		return opCodeLookUp.decoder.translateD_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,31));
	    case B_FORM:
		return opCodeLookUp.decoder.translateB_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,29), bits(instr,30,30), instr & 0x1);
	    case I_FORM:
		return opCodeLookUp.decoder.translateI_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,29), bits(instr,30,30), instr & 0x1);
	    case SC_FORM:
		return opCodeLookUp.decoder.translateSC_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,29), bits(instr,30,30), instr & 0x1);
	    case X_FORM:
		return opCodeLookUp.decoder.translateX_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), secondaryOpcode, instr & 0x1);
	    case XFX_FORM:
		return opCodeLookUp.decoder.translateXFX_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,20), secondaryOpcode, instr & 0x1);
	    case XFL_FORM:
		return opCodeLookUp.decoder.translateXFL_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,6), bits(instr,7,14), bits(instr,15,15), bits(instr,16,20), secondaryOpcode, instr & 0x1);
	    case XL_FORM:
		return opCodeLookUp.decoder.translateXL_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), secondaryOpcode, instr & 0x1);
	    case XO_FORM:
		return opCodeLookUp.decoder.translateXO_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), bits(instr,21,21), bits(instr,22,30), instr & 0x1);
	    case M_FORM:
		return opCodeLookUp.decoder.translateM_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), bits(instr,21,25), bits(instr,26,30), instr & 0x1);
	    case A_FORM:
		return opCodeLookUp.decoder.translateA_FORM(ppc2ir, lazy, pc, instr, primaryOpcode, bits(instr,6,10), bits(instr,11,15), bits(instr,16,20), bits(instr,21,25), bits(instr,26,30), instr & 0x1);
	    case EXTENDED:
		return opCodeLookUp.decoder.translateEXTENDED(ppc2ir, lazy, pc, instr, primaryOpcode);
	    case INVALID_OP:      /* More work to do... */
	    default:
		throw new Error("Invalid opcode. primary " + primaryOpcode + " secondary " + secondaryOpcode);
	    }
    }
    /** 
     * Sign extend for &lt;32 bit quantities copied into an integer.
     * @param value a signed number occupying &lt;32 bits. 
     * @param n the number of digits used in the original value.
     */
    protected static int EXTS(int value, int n) {
	return (value << (32 - n)) >> (32 - n);
    }

    /**
     * Decrement CTR register
     */
    protected static void plantDecrementCTR(PPC2IR ppc2ir) {
	OPT_RegisterOperand ctr = ppc2ir.getCTRRegister();
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, ctr,
							     ctr.copyRO(),
							     new OPT_IntConstantOperand(1)));
    }

    /**
     * Set a condition register field
     * @param ppc2ir the current translation
     * @param lazy the current lazy state
     * @param crf the field to change
     * @param rA the lhs operand for the compare
     * @param rB the rhs operand for the compare
     * @param compareType whether to do a signed or unsigned comparison on rA and rB
     */
    protected static void setCRfield(PPC2IR ppc2ir, PPC_Laziness lazy, int crf, OPT_Operand rA, OPT_Operand rB, int compareType) {
	OPT_RegisterOperand lt = ppc2ir.getCR_Lt_Register(crf);
	OPT_RegisterOperand gt = ppc2ir.getCR_Gt_Register(crf);
	OPT_RegisterOperand eq = ppc2ir.getCR_Eq_Register(crf);
	OPT_RegisterOperand so = ppc2ir.getCR_SO_Register(crf);
	
	switch(compareType) {
	case SIGNED_INT_CMP:
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, lt,
								     rA, rB,
								     OPT_ConditionOperand.LESS(),
								     OPT_BranchProfileOperand.unlikely()
								     ));
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, gt,
								     rA.copy(), rB.copy(),
								     OPT_ConditionOperand.GREATER(),
								     OPT_BranchProfileOperand.unlikely()
								     ));
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, eq,
								     rA.copy(), rB.copy(),
								     OPT_ConditionOperand.EQUAL(),
								     OPT_BranchProfileOperand.unlikely()
								     ));
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, so, ppc2ir.getXER_SO_Register()));
	    break;
	case UNSIGNED_INT_CMP:
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, lt,
								     rA, rB,
								     OPT_ConditionOperand.LOWER(),
								     OPT_BranchProfileOperand.unlikely()
								     ));
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, gt,
								     rA.copy(), rB.copy(),
								     OPT_ConditionOperand.HIGHER(),
								     OPT_BranchProfileOperand.unlikely()
								     ));
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, eq,
								     rA.copy(), rB.copy(),
								     OPT_ConditionOperand.EQUAL(),
								     OPT_BranchProfileOperand.unlikely()
								     ));
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, so, ppc2ir.getXER_SO_Register()));
	    break;
	case FP_CMPU:
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, lt,
								     rA, rB,
								     OPT_ConditionOperand.CMPG_LESS(),
								     OPT_BranchProfileOperand.unlikely()
								     ));
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, gt,
								     rA.copy(), rB.copy(),
								     OPT_ConditionOperand.CMPL_GREATER(),
								     OPT_BranchProfileOperand.unlikely()
								     ));
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, eq,
								     rA.copy(), rB.copy(),
								     OPT_ConditionOperand.CMPL_EQUAL(),
								     OPT_BranchProfileOperand.unlikely()
								     ));
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp2.create(BOOLEAN_CMP2_INT_OR,
								      so,
								      rA.copy(), rA.copy(),
								      OPT_ConditionOperand.CMPL_NOT_EQUAL(),
								      OPT_BranchProfileOperand.unlikely(),
								      rB.copy(), rB.copy(),
								      OPT_ConditionOperand.CMPL_NOT_EQUAL(),
								      OPT_BranchProfileOperand.unlikely()
								      ));
	    break;
	default:
	    DBT_OptimizingCompilerException.UNREACHABLE();
	}
    }

    /**
     * Plant (likely) branch to block if ctr is zero (ctr_zero == true)
     * or not zero (ctr_zero == false). NB as this code is likely to be
     * used to branch to a fall through case the likely and ctr_zero
     * values are usually the opposite of what the instruction encodes
     */
    protected static void plantBranchToBlockDependentOnCTR(PPC2IR ppc2ir, OPT_BasicBlock targetBlock, boolean ctr_zero, boolean likely) {
	OPT_RegisterOperand ctr = ppc2ir.getCTRRegister();
	OPT_RegisterOperand guardResult = ppc2ir.getTempValidation(0);
	OPT_ConditionOperand condOp;
	if (ctr_zero) {
	    condOp = OPT_ConditionOperand.EQUAL();
	}
	else {
	    condOp = OPT_ConditionOperand.NOT_EQUAL();
	}
	OPT_BranchProfileOperand likelyOp = ppc2ir.getConditionalBranchProfileOperand(likely);

	OPT_BasicBlock fallThrough = ppc2ir.createBlockAfterCurrent();
	ppc2ir.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, guardResult,
							    ctr,
							    new OPT_IntConstantOperand(0),
							    condOp,
							    targetBlock.makeJumpTarget(),
							    likelyOp));
	ppc2ir.getCurrentBlock().insertOut(targetBlock);
	ppc2ir.setCurrentBlock(fallThrough);
    }

    /**
     * Plant (likely) branch to block if condition is cond_true
     */
    protected static void plantBranchToBlockDependentOnCondition(PPC2IR ppc2ir, OPT_BasicBlock targetBlock, PPC_Laziness lazy, int BI, boolean cond_true, boolean likely) {
	if (VM.VerifyAssertions) VM._assert ((BI >= 0) && (BI < 32));

	// Calculate the condition register field being tested
	int crf = BI >> 2;

	// Block entered after the test
	OPT_BasicBlock fallThrough = ppc2ir.createBlockAfterCurrent();

	// Create a branch likelihood operand
	OPT_BranchProfileOperand likelyOp = ppc2ir.getConditionalBranchProfileOperand(likely);

	// Grab the flag to compare
	OPT_RegisterOperand flag = ppc2ir.getCRB_Register(BI);

	// The condition to test
	OPT_ConditionOperand condOp;
	if (cond_true) { // NB invert sense of cond_true as we're comparing with 0
	    condOp = OPT_ConditionOperand.NOT_EQUAL();
	}
	else {
	    condOp = OPT_ConditionOperand.EQUAL();
	}

	// Create the instruction
	OPT_RegisterOperand guardResult = ppc2ir.getTempValidation(0);
	ppc2ir.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, guardResult,
							    flag,
							    new OPT_IntConstantOperand(0),
							    condOp,
							    targetBlock.makeJumpTarget(),
							    likelyOp));
	ppc2ir.getCurrentBlock().insertOut(targetBlock);
	// Move currentBlock onto fall through
	ppc2ir.setCurrentBlock(fallThrough);
    }
}

// -oO OpCodeLookUp Oo-

/**
 * A class holding what can be looked up from an instruction by its opcode
 */
class OpCodeLookUp {
    private final int form;
    final String mnemonic;
    final PPC_InstructionDecoder decoder;
    OpCodeLookUp(int _form, String _mnemonic, PPC_InstructionDecoder _instrDecoder) {
	form = _form;
	mnemonic = _mnemonic;
	decoder=_instrDecoder;
    }
    int getForm() {
	return form;
    }
}

// -oO Extended Instructions Oo-
/**
 * The decoder for the XL_FORM instructions
 */
final class xlForm_decoder extends PPC_InstructionDecoder {
    /**
     * Table for the XL instruction format
     */
    private static final OpCodeLookUp[] XLform = {
	/*   OPCD      EO                           form     mnemonic         decoder */
	/*   ----      --                           ----     --------         ---------- */ 
	/*    19,      0,      */  new OpCodeLookUp(XL_FORM, "mcrf",          new mcrf_decoder()),
	/*    19,      16,     */  new OpCodeLookUp(XL_FORM, "bclr or bclrl", new bclr_decoder()),
	/*    19,      33,     */  new OpCodeLookUp(XL_FORM, "crnor",         new crnor_decoder()),
	/*    19,      50,     */  new OpCodeLookUp(XL_FORM, "rfi",           new rfi_decoder()),
	/*    19,      XXX,    */  null,
    
	/*    19,      XXX,    */  null,
	/*    19,      XXX,    */  null,
	/*    19,      XXX,    */  null,
	/*    19,      129,    */  new OpCodeLookUp(XL_FORM, "crandc",        new crandc_decoder()),
	/*    19,      150,    */  new OpCodeLookUp(XL_FORM, "isync",         new isync_transltor()),
    
	/*    19,      XXX,    */  null,
	/*    19,      XXX,    */  null,
	/*    19,      193,    */  new OpCodeLookUp(XL_FORM, "crxor",         new crxor_decoder()),
	/*    19,      XXX,    */  null,
	/*    19,      225,    */  new OpCodeLookUp(XL_FORM, "crnand",        new crnand_decoder()),
    
	/*    19,      XXX,    */  null,
	/*    19,      257,    */  new OpCodeLookUp(XL_FORM, "crand",         new crand_decoder()),
	/*    19,      XXX,    */  null,
	/*    19,      289,    */  new OpCodeLookUp(XL_FORM, "creqv",         new creqv_decoder()),
	/*    19,      XXX,    */  null,
    
	/*    19,      XXX,    */  null,
	/*    19,      XXX,    */  null,
	/*    19,      XXX,    */  null,
	/*    19,      XXX,    */  null,
	/*    19,      XXX,    */  null,
    
	/*    19,      XXX,    */  null,
	/*    19,      417,    */  new OpCodeLookUp(XL_FORM, "crorc",         new croc_decoder()),
	/*    19,      XXX,    */  null,
	/*    19,      449,    */  new OpCodeLookUp(XL_FORM, "cror",          new cror_traslator()),
	/*    19,      XXX,    */  null,
    
	/*    19,      XXX,    */  null,
	/*    19,      XXX,    */  null,
	/*    19,      XXX,    */  null,
	/*    19,      528,    */  new OpCodeLookUp(XL_FORM, "bcctr or bcctrl", new bcctr_decoder())
    };
    /**
     * Give the decoder for a given instruction. Extended instruction
     * decoders should return the appropriate instruction from opcode 2.
     * @param the instruction to get the decoder for
     * @param the appropriate instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	int secondaryOpcode =  bits(instr,21,30);
	return XLform[secondaryOpcode >> 4].decoder.getDecoder(instr);
    }
    /**
     * Call the appropriate XL translator
     */
    protected int translateXL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int bits6_to_10, int bits11_to_15, int bits16_to_20, int secondaryOpcode, int bit31){
	return XLform[secondaryOpcode >> 4].decoder.translateXL_FORM(ppc2ir, lazy, pc, inst, opcode, bits6_to_10, bits11_to_15, bits16_to_20, secondaryOpcode, bit31);
    }
}

/**
 * The decoder for the EXTENDED 31 instructions
 */
final class extended31_decoder extends PPC_InstructionDecoder {

    // The decoders for the XO_Form instructions
    private static final OpCodeLookUp subfc_lookup = new OpCodeLookUp(XO_FORM, "subfc", new subfc_decoder());
    private static final OpCodeLookUp addc_lookup = new OpCodeLookUp(XO_FORM, "addc", new addc_decoder());
    private static final OpCodeLookUp mulhwu_lookup = new OpCodeLookUp(XO_FORM, "mulhwu", new mulhwu_decoder());
    private static final OpCodeLookUp subf_lookup = new OpCodeLookUp(XO_FORM, "subf", new subf_decoder());
    private static final OpCodeLookUp mulhw_lookup = new OpCodeLookUp(XO_FORM, "mulhw", new mulhw_decoder());
    private static final OpCodeLookUp neg_lookup = new OpCodeLookUp(XO_FORM, "neg", new neg_decoder());
    private static final OpCodeLookUp subfe_lookup = new OpCodeLookUp(XO_FORM, "subfe", new subfe_decoder());
    private static final OpCodeLookUp adde_lookup = new OpCodeLookUp(XO_FORM, "adde", new adde_decoder());
    private static final OpCodeLookUp subfze_lookup = new OpCodeLookUp(XO_FORM, "subfze", new subfze_decoder());
    private static final OpCodeLookUp addze_lookup = new OpCodeLookUp(XO_FORM, "addze", new addze_decoder());
    private static final OpCodeLookUp subfme_lookup = new OpCodeLookUp(XO_FORM, "subfme", new subfme_decoder());
    private static final OpCodeLookUp addme_lookup = new OpCodeLookUp(XO_FORM, "addme", new addme_decoder());
    private static final OpCodeLookUp mullw_lookup = new OpCodeLookUp(XO_FORM, "mullw", new mullw_decoder());
    private static final OpCodeLookUp add_lookup = new OpCodeLookUp(XO_FORM, "add", new add_decoder());
    private static final OpCodeLookUp divwu_lookup = new OpCodeLookUp(XO_FORM, "divwu", new divwu_decoder());
    private static final OpCodeLookUp divw_lookup = new OpCodeLookUp(XO_FORM, "divw", new divw_decoder());
    private static final OpCodeLookUp subfco_lookup = new OpCodeLookUp(XO_FORM, "subfco", new subfco_decoder());
    private static final OpCodeLookUp addco_lookup = new OpCodeLookUp(XO_FORM, "addco", new addco_decoder());
    private static final OpCodeLookUp subfo_lookup = new OpCodeLookUp(XO_FORM, "subfo", new subfo_decoder());
    private static final OpCodeLookUp subfeo_lookup = new OpCodeLookUp(XO_FORM, "subfeo", new subfeo_decoder());
    private static final OpCodeLookUp addeo_lookup = new OpCodeLookUp(XO_FORM, "addeo", new addeo_decoder());
    private static final OpCodeLookUp subfzeo_lookup = new OpCodeLookUp(XO_FORM, "subfzeo", new subfzeo_decoder());
    private static final OpCodeLookUp addzeo_lookup = new OpCodeLookUp(XO_FORM, "addzeo", new addzeo_decoder());
    private static final OpCodeLookUp subfmeo_lookup = new OpCodeLookUp(XO_FORM, "subfmeo", new subfmeo_decoder());
    private static final OpCodeLookUp addmeo_lookup = new OpCodeLookUp(XO_FORM, "addmeo", new addmeo_decoder());
    private static final OpCodeLookUp mullwo_lookup = new OpCodeLookUp(XO_FORM, "mullwo", new mullwo_decoder());
    private static final OpCodeLookUp nego_lookup = new OpCodeLookUp(XO_FORM, "nego", new nego_decoder());
    private static final OpCodeLookUp addo_lookup = new OpCodeLookUp(XO_FORM, "addo", new addo_decoder());
    private static final OpCodeLookUp divwuo_lookup = new OpCodeLookUp(XO_FORM, "divwuo", new divwuo_decoder());
    private static final OpCodeLookUp divwo_lookup = new OpCodeLookUp(XO_FORM, "divwo", new divwo_decoder());

    // The decoders for the XFX_Form instructions
    private static final OpCodeLookUp mtcrf_lookup = new OpCodeLookUp(XFX_FORM, "mtcrf", new mtcrf_decoder());
    private static final OpCodeLookUp mfspr_lookup = new OpCodeLookUp(XFX_FORM, "mfspr", new mfspr_decoder());
    private static final OpCodeLookUp mtspr_lookup = new OpCodeLookUp(XFX_FORM, "mtspr", new mtspr_decoder());

    // The decoders for the X_Form instructions
    private static final OpCodeLookUp cmp_lookup = new OpCodeLookUp(X_FORM, "cmp", new cmp_decoder());
    private static final OpCodeLookUp tw_lookup = new OpCodeLookUp(X_FORM, "tw", new tw_decoder());
    private static final OpCodeLookUp lvsl_lookup = new OpCodeLookUp(X_FORM, "lvsl", new lvsl_decoder());
    private static final OpCodeLookUp lvebx_lookup = new OpCodeLookUp(X_FORM, "lvebx", new lvebx_decoder());
    private static final OpCodeLookUp mulhdu_lookup = new OpCodeLookUp(X_FORM, "mulhdu", new mulhdu_decoder());
    private static final OpCodeLookUp mfcr_lookup = new OpCodeLookUp(X_FORM, "mfcr", new mfcr_decoder());
    private static final OpCodeLookUp lwarx_lookup = new OpCodeLookUp(X_FORM, "lwarx", new lwarx_decoder());
    private static final OpCodeLookUp ldx_lookup = new OpCodeLookUp(X_FORM, "ldx", new ldx_decoder());
    private static final OpCodeLookUp lwzx_lookup = new OpCodeLookUp(X_FORM, "lwzx", new lwzx_decoder());
    private static final OpCodeLookUp slw_lookup = new OpCodeLookUp(X_FORM, "slw", new slw_decoder());
    private static final OpCodeLookUp cntlzw_lookup = new OpCodeLookUp(X_FORM, "cntlzw", new cntlzw_decoder());
    private static final OpCodeLookUp sld_lookup = new OpCodeLookUp(X_FORM, "sld", new sld_decoder());
    private static final OpCodeLookUp and_lookup = new OpCodeLookUp(X_FORM, "and", new and_decoder());
    private static final OpCodeLookUp cmpl_lookup = new OpCodeLookUp(X_FORM, "cmpl", new cmpl_decoder());
    private static final OpCodeLookUp lvsr_lookup = new OpCodeLookUp(X_FORM, "lvsr", new lvsr_decoder());
    private static final OpCodeLookUp lvehx_lookup = new OpCodeLookUp(X_FORM, "lvehx", new lvehx_decoder());
    private static final OpCodeLookUp ldux_lookup = new OpCodeLookUp(X_FORM, "ldux", new ldux_decoder());
    private static final OpCodeLookUp dcbst_lookup = new OpCodeLookUp(X_FORM, "dcbst", new dcbst_decoder());
    private static final OpCodeLookUp lwzux_lookup = new OpCodeLookUp(X_FORM, "lwzux", new lwzux_decoder());
    private static final OpCodeLookUp andc_lookup = new OpCodeLookUp(X_FORM, "andc", new andc_decoder());
    private static final OpCodeLookUp td_lookup = new OpCodeLookUp(X_FORM, "td", new td_decoder());
    private static final OpCodeLookUp mulhd_lookup = new OpCodeLookUp(X_FORM, "mulhd", new mulhd_decoder());
    private static final OpCodeLookUp mfmsr_lookup = new OpCodeLookUp(X_FORM, "mfmsr", new mfmsr_decoder());
    private static final OpCodeLookUp ldarx_lookup = new OpCodeLookUp(X_FORM, "ldarx", new ldarx_decoder());
    private static final OpCodeLookUp dcbf_lookup = new OpCodeLookUp(X_FORM, "dcbf", new dcbf_decoder());
    private static final OpCodeLookUp lbzx_lookup = new OpCodeLookUp(X_FORM, "lbzx", new lbzx_decoder());
    private static final OpCodeLookUp lbzux_lookup = new OpCodeLookUp(X_FORM, "lbzux", new lbzux_decoder());
    private static final OpCodeLookUp nor_lookup = new OpCodeLookUp(X_FORM, "nor", new nor_decoder());
    private static final OpCodeLookUp mtmsr_lookup = new OpCodeLookUp(X_FORM, "mtmsr", new mtmsr_decoder());
    private static final OpCodeLookUp stdx_lookup = new OpCodeLookUp(X_FORM, "stdx", new stdx_decoder());
    private static final OpCodeLookUp stwcx_lookup = new OpCodeLookUp(X_FORM, "stwcx", new stwcx_decoder());
    private static final OpCodeLookUp stwx_lookup = new OpCodeLookUp(X_FORM, "stwx", new stwx_decoder());
    private static final OpCodeLookUp stdux_lookup = new OpCodeLookUp(X_FORM, "stdux", new stdux_decoder());
    private static final OpCodeLookUp stwux_lookup = new OpCodeLookUp(X_FORM, "stwux", new stwux_decoder());
    private static final OpCodeLookUp mtsr_lookup = new OpCodeLookUp(X_FORM, "mtsr", new mtsr_decoder());
    private static final OpCodeLookUp stdcx_lookup = new OpCodeLookUp(X_FORM, "stdcx", new stdcx_decoder());
    private static final OpCodeLookUp stbx_lookup = new OpCodeLookUp(X_FORM, "stbx", new stbx_decoder());
    private static final OpCodeLookUp mulld_lookup = new OpCodeLookUp(X_FORM, "mulld", new mulld_decoder());
    private static final OpCodeLookUp mtsrin_lookup = new OpCodeLookUp(X_FORM, "mtsrin", new mtsrin_decoder());
    private static final OpCodeLookUp dcbtst_lookup = new OpCodeLookUp(X_FORM, "dcbtst", new dcbtst_decoder());
    private static final OpCodeLookUp stbux_lookup = new OpCodeLookUp(X_FORM, "stbux", new stbux_decoder());
    private static final OpCodeLookUp dcbt_lookup = new OpCodeLookUp(X_FORM, "dcbt", new dcbt_decoder());
    private static final OpCodeLookUp lhzx_lookup = new OpCodeLookUp(X_FORM, "lhzx", new lhzx_decoder());
    private static final OpCodeLookUp eqv_lookup = new OpCodeLookUp(X_FORM, "eqv", new eqv_decoder());
    private static final OpCodeLookUp tlbie_lookup = new OpCodeLookUp(X_FORM, "tlbie", new tlbie_decoder());
    private static final OpCodeLookUp eciwx_lookup = new OpCodeLookUp(X_FORM, "eciwx", new eciwx_decoder());
    private static final OpCodeLookUp lhzux_lookup = new OpCodeLookUp(X_FORM, "lhzux", new lhzux_decoder());
    private static final OpCodeLookUp xor_lookup = new OpCodeLookUp(X_FORM, "xor", new xor_decoder());
    private static final OpCodeLookUp lwax_lookup = new OpCodeLookUp(X_FORM, "lwax", new lwax_decoder());
    private static final OpCodeLookUp dst_lookup = new OpCodeLookUp(X_FORM, "dst", new dst_decoder());
    private static final OpCodeLookUp lhax_lookup = new OpCodeLookUp(X_FORM, "lhax", new lhax_decoder());
    private static final OpCodeLookUp lvxl_lookup = new OpCodeLookUp(X_FORM, "lvxl", new lvxl_decoder());
    private static final OpCodeLookUp tlbia_lookup = new OpCodeLookUp(X_FORM, "tlbia", new tlbia_decoder());
    private static final OpCodeLookUp mftb_lookup = new OpCodeLookUp(X_FORM, "mftb", new mftb_decoder());
    private static final OpCodeLookUp lwaux_lookup = new OpCodeLookUp(X_FORM, "lwaux", new lwaux_decoder());
    private static final OpCodeLookUp dstst_lookup = new OpCodeLookUp(X_FORM, "dstst", new dstst_decoder());
    private static final OpCodeLookUp lhaux_lookup = new OpCodeLookUp(X_FORM, "lhaux", new lhaux_decoder());
    private static final OpCodeLookUp sthx_lookup = new OpCodeLookUp(X_FORM, "sthx", new sthx_decoder());
    private static final OpCodeLookUp orc_lookup = new OpCodeLookUp(X_FORM, "orc", new orc_decoder());
    private static final OpCodeLookUp slbie_lookup = new OpCodeLookUp(X_FORM, "slbie", new slbie_decoder());
    private static final OpCodeLookUp ecowx_lookup = new OpCodeLookUp(X_FORM, "ecowx", new ecowx_decoder());
    private static final OpCodeLookUp sthux_lookup = new OpCodeLookUp(X_FORM, "sthux", new sthux_decoder());
    private static final OpCodeLookUp or_lookup = new OpCodeLookUp(X_FORM, "or", new or_decoder());
    private static final OpCodeLookUp divdu_lookup = new OpCodeLookUp(X_FORM, "divdu", new divdu_decoder());
    private static final OpCodeLookUp dcbi_lookup = new OpCodeLookUp(X_FORM, "dcbi", new dcbi_decoder());
    private static final OpCodeLookUp nand_lookup = new OpCodeLookUp(X_FORM, "nand", new nand_decoder());
    private static final OpCodeLookUp divd_lookup = new OpCodeLookUp(X_FORM, "divd", new divd_decoder());
    private static final OpCodeLookUp slbia_lookup = new OpCodeLookUp(X_FORM, "slbia", new slbia_decoder());
    private static final OpCodeLookUp mcrxr_lookup = new OpCodeLookUp(X_FORM, "mcrxr", new mcrxr_decoder());
    private static final OpCodeLookUp lswx_lookup = new OpCodeLookUp(X_FORM, "lswx", new lswx_decoder());
    private static final OpCodeLookUp lwbrx_lookup = new OpCodeLookUp(X_FORM, "lwbrx", new lwbrx_decoder());
    private static final OpCodeLookUp lfsx_lookup = new OpCodeLookUp(X_FORM, "lfsx", new lfsx_decoder());
    private static final OpCodeLookUp srw_lookup = new OpCodeLookUp(X_FORM, "srw", new srw_decoder());
    private static final OpCodeLookUp srd_lookup = new OpCodeLookUp(X_FORM, "srd", new srd_decoder());
    private static final OpCodeLookUp cntlzd_lookup = new OpCodeLookUp(X_FORM, "cntlzd", new cntlzd_decoder());
    private static final OpCodeLookUp lfsux_lookup = new OpCodeLookUp(X_FORM, "lfsux", new lfsux_decoder());
    private static final OpCodeLookUp mfsr_lookup = new OpCodeLookUp(X_FORM, "mfsr", new mfsr_decoder());
    private static final OpCodeLookUp lswi_lookup = new OpCodeLookUp(X_FORM, "lswi", new lswi_decoder());
    private static final OpCodeLookUp sync_lookup = new OpCodeLookUp(X_FORM, "sync", new sync_decoder());
    private static final OpCodeLookUp lfdx_lookup = new OpCodeLookUp(X_FORM, "lfdx", new lfdx_decoder());
    private static final OpCodeLookUp lfdux_lookup = new OpCodeLookUp(X_FORM, "lfdux", new lfdux_decoder());
    private static final OpCodeLookUp mfsrin_lookup = new OpCodeLookUp(X_FORM, "mfsrin", new mfsrin_decoder());
    private static final OpCodeLookUp stswx_lookup = new OpCodeLookUp(X_FORM, "stswx", new stswx_decoder());
    private static final OpCodeLookUp stwbrx_lookup = new OpCodeLookUp(X_FORM, "stwbrx", new stwbrx_decoder());
    private static final OpCodeLookUp stfsx_lookup = new OpCodeLookUp(X_FORM, "stfsx", new stfsx_decoder());
    private static final OpCodeLookUp stfsux_lookup = new OpCodeLookUp(X_FORM, "stfsux", new stfsux_decoder());
    private static final OpCodeLookUp stswi_lookup = new OpCodeLookUp(X_FORM, "stswi", new stswi_decoder());
    private static final OpCodeLookUp stfdx_lookup = new OpCodeLookUp(X_FORM, "stfdx", new stfdx_decoder());
    private static final OpCodeLookUp dcba_lookup = new OpCodeLookUp(X_FORM, "dcba", new dcba_decoder());
    private static final OpCodeLookUp stfdux_lookup = new OpCodeLookUp(X_FORM, "stfdux", new stfdux_decoder());
    private static final OpCodeLookUp lhbrx_lookup = new OpCodeLookUp(X_FORM, "lhbrx", new lhbrx_decoder());
    private static final OpCodeLookUp sraw_lookup = new OpCodeLookUp(X_FORM, "sraw", new sraw_decoder());
    private static final OpCodeLookUp srad_lookup = new OpCodeLookUp(X_FORM, "srad", new srad_decoder());
    private static final OpCodeLookUp dss_lookup = new OpCodeLookUp(X_FORM, "dss", new dss_decoder());
    private static final OpCodeLookUp srawi_lookup = new OpCodeLookUp(X_FORM, "srawi", new srawi_decoder());
    private static final OpCodeLookUp sradi_lookup = new OpCodeLookUp(X_FORM, "sradi", new sradi_decoder());
    // private static final OpCodeLookUp sradi_lookup = new OpCodeLookUp(X_FORM, "sradi", new sradi_decoder());
    private static final OpCodeLookUp eieio_lookup = new OpCodeLookUp(X_FORM, "eieio", new eieio_decoder());
    private static final OpCodeLookUp sthbrx_lookup = new OpCodeLookUp(X_FORM, "sthbrx", new sthbrx_decoder());
    private static final OpCodeLookUp extsh_lookup = new OpCodeLookUp(X_FORM, "extsh", new extsh_decoder());
    private static final OpCodeLookUp extsb_lookup = new OpCodeLookUp(X_FORM, "extsb", new extsb_decoder());
    private static final OpCodeLookUp tlbld_lookup = new OpCodeLookUp(X_FORM, "tlbld", new tlbld_decoder());
    private static final OpCodeLookUp icbi_lookup = new OpCodeLookUp(X_FORM, "icbi", new icbi_decoder());
    private static final OpCodeLookUp stfiwx_lookup = new OpCodeLookUp(X_FORM, "stfiwx", new stfiwx_decoder());
    private static final OpCodeLookUp extsw_lookup = new OpCodeLookUp(X_FORM, "extsw", new extsw_decoder());
    private static final OpCodeLookUp tlbli_lookup = new OpCodeLookUp(X_FORM, "tlbli", new tlbli_decoder());
    private static final OpCodeLookUp dcbz_lookup = new OpCodeLookUp(X_FORM, "dcbz", new dcbz_decoder());
    protected OpCodeLookUp getOpCodeLookUp(int primaryOpcode, int secondaryOpcode) {
	switch(secondaryOpcode){
	case 0: return cmp_lookup;
	case 4: return tw_lookup;
	case 6: return lvsl_lookup;
	case 7: return lvebx_lookup;
	case 8: return subfc_lookup;
	case 9: return mulhdu_lookup;
	case 10: return addc_lookup;
	case 11: return mulhwu_lookup;
	case 19: return mfcr_lookup;
	case 20: return lwarx_lookup;
	case 21: return ldx_lookup;
	case 23: return lwzx_lookup;
	case 24: return slw_lookup;
	case 26: return cntlzw_lookup;
	case 27: return sld_lookup;
	case 28: return and_lookup;
	case 32: return cmpl_lookup;
	case 38: return lvsr_lookup;
	case 39: return lvehx_lookup;
	case 40: return subf_lookup;
	case 53: return ldux_lookup;
	case 54: return dcbst_lookup;
	case 55: return lwzux_lookup;
	case 60: return andc_lookup;
	case 68: return td_lookup;
	case 73: return mulhd_lookup;
	case 75: return mulhw_lookup;
	case 83: return mfmsr_lookup;
	case 84: return ldarx_lookup;
	case 86: return dcbf_lookup;
	case 87: return lbzx_lookup;
	case 104: return neg_lookup;
	case 119: return lbzux_lookup;
	case 124: return nor_lookup;
	case 136: return subfe_lookup;
	case 138: return adde_lookup;
	case 144: return mtcrf_lookup;
	case 146: return mtmsr_lookup;
	case 149: return stdx_lookup;
	case 150: return stwcx_lookup;
	case 151: return stwx_lookup;
	case 181: return stdux_lookup;
	case 183: return stwux_lookup;
	case 200: return subfze_lookup;
	case 202: return addze_lookup;
	case 210: return mtsr_lookup;
	case 214: return stdcx_lookup;
	case 215: return stbx_lookup;
	case 232: return subfme_lookup;
	case 233: return mulld_lookup;
	case 234: return addme_lookup;
	case 235: return mullw_lookup;
	case 242: return mtsrin_lookup;
	case 246: return dcbtst_lookup;
	case 247: return stbux_lookup;
	case 266: return add_lookup;
	case 278: return dcbt_lookup;
	case 279: return lhzx_lookup;
	case 284: return eqv_lookup;
	case 306: return tlbie_lookup;
	case 310: return eciwx_lookup;
	case 311: return lhzux_lookup;
	case 316: return xor_lookup;
	case 339: return mfspr_lookup;
	case 341: return lwax_lookup;
	case 342: return dst_lookup;
	case 343: return lhax_lookup;
	case 359: return lvxl_lookup;
	case 370: return tlbia_lookup;
	case 371: return mftb_lookup;
	case 373: return lwaux_lookup;
	case 374: return dstst_lookup;
	case 375: return lhaux_lookup;
	case 407: return sthx_lookup;
	case 412: return orc_lookup;
	case 434: return slbie_lookup;
	case 438: return ecowx_lookup;
	case 439: return sthux_lookup;
	case 444: return or_lookup;
	case 457: return divdu_lookup;
	case 459: return divwu_lookup;
	case 467: return mtspr_lookup;
	case 470: return dcbi_lookup;
	case 476: return nand_lookup;
	case 489: return divd_lookup;
	case 491: return divw_lookup;
	case 498: return slbia_lookup;
	case 512: return mcrxr_lookup;
	case 520: return subfco_lookup;
	case 522: return addco_lookup;
	case 533: return lswx_lookup;
	case 534: return lwbrx_lookup;
	case 535: return lfsx_lookup;
	case 536: return srw_lookup;
	case 539: return srd_lookup;
	case 552: return subfo_lookup;
	case 558: return cntlzd_lookup;
	case 566: return lfsux_lookup;
	case 595: return mfsr_lookup;
	case 597: return lswi_lookup;
	case 598: return sync_lookup;
	case 599: return lfdx_lookup;
	case 631: return lfdux_lookup;
	case 648: return subfeo_lookup;
	case 650: return addeo_lookup;
	case 659: return mfsrin_lookup;
	case 661: return stswx_lookup;
	case 662: return stwbrx_lookup;
	case 663: return stfsx_lookup;
	case 695: return stfsux_lookup;
	case 712: return subfzeo_lookup;
	case 714: return addzeo_lookup;
	case 725: return stswi_lookup;
	case 727: return stfdx_lookup;
	case 744: return subfmeo_lookup;
	case 746: return addmeo_lookup;
	case 747: return mullwo_lookup;
	case 758: return dcba_lookup;
	case 759: return stfdux_lookup;
	case 772: return nego_lookup;
	case 778: return addo_lookup;
	case 790: return lhbrx_lookup;
	case 792: return sraw_lookup;
	case 794: return srad_lookup;
	case 822: return dss_lookup;
	case 824: return srawi_lookup;
	case 826: // both 826 & 827 are sradi
	case 827: return sradi_lookup;
	case 854: return eieio_lookup;
	case 918: return sthbrx_lookup;
	case 922: return extsh_lookup;
	case 954: return extsb_lookup;
	case 971: return divwuo_lookup;
	case 978: return tlbld_lookup;
	case 982: return icbi_lookup;
	case 983: return stfiwx_lookup;
	case 986: return extsw_lookup;
	case 1003: return divwo_lookup;
	case 1010: return tlbli_lookup;
	case 1014: return dcbz_lookup;
	default: return null;
	}
    }
    /**
     * Give the decoder for a given instruction. Extended instruction
     * decoders should return the appropriate instruction from opcode 2.
     * @param the instruction to get the decoder for
     * @param the appropriate instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	int secondaryOpcode =  bits(instr,21,30);
	return getOpCodeLookUp(-1,secondaryOpcode).decoder.getDecoder(instr);
    }
}

/**
 * The decoder for the EXTENDED 59 instructions
 */
final class extended59_decoder extends PPC_InstructionDecoder {
    private static final OpCodeLookUp fdivs_lookup = new OpCodeLookUp(A_FORM, "fdivs", new fdivs_decoder());
    private static final OpCodeLookUp fsubs_lookup = new OpCodeLookUp(A_FORM, "fsubs", new fsubs_decoder());  
    private static final OpCodeLookUp fadds_lookup = new OpCodeLookUp(A_FORM, "fadds", new fadds_decoder());
    private static final OpCodeLookUp fsqrts_lookup = new OpCodeLookUp(A_FORM, "fsqrts", new fsqrts_decoder());
    private static final OpCodeLookUp fres_lookup = new OpCodeLookUp(A_FORM, "fres", new fres_decoder());
    private static final OpCodeLookUp fmuls_lookup = new OpCodeLookUp(A_FORM, "fmuls", new fmuls_decoder());  
    private static final OpCodeLookUp fmsubs_lookup = new OpCodeLookUp(A_FORM, "fmsubs", new fmsubs_decoder());  
    private static final OpCodeLookUp fmadds_lookup = new OpCodeLookUp(A_FORM, "fmadds", new fmadds_decoder());  
    private static final OpCodeLookUp fnmsubs_lookup = new OpCodeLookUp(A_FORM, "fnmsubs", new fnmsubs_decoder());
    private static final OpCodeLookUp fnmadds_lookup = new OpCodeLookUp(A_FORM, "fnmadds", new fnmadds_decoder()); 

    protected OpCodeLookUp getOpCodeLookUp(int primaryOpcode, int secondaryOpcode) {
	switch(secondaryOpcode){
	case 18: return fdivs_lookup;   
	case 20: return fsubs_lookup;  
	case 21: return fadds_lookup;
	case 22: return fsqrts_lookup;
	case 24: return fres_lookup;
	default: break;
	}
	switch(secondaryOpcode & 0x1f) {
	case 25: return fmuls_lookup;  
	case 28: return fmsubs_lookup;  
	case 29: return fmadds_lookup;
	case 30: return fnmsubs_lookup;
	case 31: return fnmadds_lookup; 
	default: break;
	}
	return null;
    }
    /**
     * Give the decoder for a given instruction. Extended instruction
     * decoders should return the appropriate instruction from opcode 2.
     * @param the instruction to get the decoder for
     * @param the appropriate instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	int secondaryOpcode =  bits(instr,21,30);
	return getOpCodeLookUp(-1,secondaryOpcode).decoder.getDecoder(instr);
    }
}

/**
 * The decoder for the EXTENDED 63 instructions
 */
final class extended63_decoder extends PPC_InstructionDecoder {
    // The decoders for the X_Form instructions
    private static final OpCodeLookUp fcmpu_lookup = new OpCodeLookUp(X_FORM, "fcmpu", new fcmpu_decoder());
    private static final OpCodeLookUp frsp_lookup = new OpCodeLookUp(X_FORM, "frsp", new frsp_decoder());
    private static final OpCodeLookUp fctiw_lookup = new OpCodeLookUp(X_FORM, "fctiw", new fctiw_decoder());  
    private static final OpCodeLookUp fctiwz_lookup = new OpCodeLookUp(X_FORM, "fctiwz", new fctiwz_decoder());  
    private static final OpCodeLookUp fcmpo_lookup = new OpCodeLookUp(X_FORM, "fcmpo", new fcmpo_decoder());
    private static final OpCodeLookUp mtfsb1_lookup = new OpCodeLookUp(X_FORM, "mtfsb1", new mtfsb1_decoder());
    private static final OpCodeLookUp fneg_lookup = new OpCodeLookUp(X_FORM, "fneg", new fneg_decoder());
    private static final OpCodeLookUp mcrfs_lookup = new OpCodeLookUp(X_FORM, "mcrfs", new mcrfs_decoder());
    private static final OpCodeLookUp mtfsb0_lookup = new OpCodeLookUp(X_FORM, "mtfsb0", new mtfsb0_decoder());
    private static final OpCodeLookUp fmr_lookup = new OpCodeLookUp(X_FORM, "fmr", new fmr_decoder());
    private static final OpCodeLookUp mtfsfi_lookup = new OpCodeLookUp(X_FORM, "mtfsfi", new mtfsfi_decoder());
    private static final OpCodeLookUp fnabs_lookup = new OpCodeLookUp(X_FORM, "fnabs", new fnabs_decoder());
    private static final OpCodeLookUp fabs_lookup = new OpCodeLookUp(X_FORM, "fabs", new fabs_decoder());
    private static final OpCodeLookUp mffs_lookup = new OpCodeLookUp(X_FORM, "mffs", new mffs_decoder());
    private static final OpCodeLookUp fctid_lookup = new OpCodeLookUp(X_FORM, "fctid", new fctid_decoder());
    private static final OpCodeLookUp fctidz_lookup = new OpCodeLookUp(X_FORM, "fctidz", new fctidz_decoder());
    private static final OpCodeLookUp fcfid_lookup = new OpCodeLookUp(X_FORM, "fcfid", new fcfid_decoder());

    // The decoders for the XFL_Form instructions
    private static final OpCodeLookUp mtfsf_lookup = new OpCodeLookUp(XFL_FORM, "mtfsf", new mtfsf_decoder());

    // The decoders for the A_Form instructions
    private static final OpCodeLookUp fdiv_lookup = new OpCodeLookUp(A_FORM, "fdiv", new fdiv_decoder());  
    private static final OpCodeLookUp fsub_lookup = new OpCodeLookUp(A_FORM, "fsub", new fsub_decoder());  
    private static final OpCodeLookUp fadd_lookup = new OpCodeLookUp(A_FORM, "fadd", new fadd_decoder());  
    private static final OpCodeLookUp fmul_lookup = new OpCodeLookUp(A_FORM, "fmul", new fmul_decoder()); 
    private static final OpCodeLookUp fmsub_lookup = new OpCodeLookUp(A_FORM, "fmsub", new fmsub_decoder());  
    private static final OpCodeLookUp fmadd_lookup = new OpCodeLookUp(A_FORM, "fmadd", new fmadd_decoder());  
    private static final OpCodeLookUp fnmsub_lookup = new OpCodeLookUp(A_FORM, "fnmsub", new fnmsub_decoder());  
    private static final OpCodeLookUp fnmadd_lookup = new OpCodeLookUp(A_FORM, "fnmadd", new fnmadd_decoder());  

    protected OpCodeLookUp getOpCodeLookUp(int primaryOpcode, int secondaryOpcode) {
	switch(secondaryOpcode){
	case 0: return fcmpu_lookup;
	case 12: return frsp_lookup;
	case 14: return fctiw_lookup;  
	case 15: return fctiwz_lookup;
	case 18: return fdiv_lookup;
	case 20: return fsub_lookup;
	case 21: return fadd_lookup;
	case 22: throw new Error("fsqrt!");
	case 32: return fcmpo_lookup;
	case 38: return mtfsb1_lookup;
	case 40: return fneg_lookup;
	case 64: return mcrfs_lookup;
	case 70: return mtfsb0_lookup;
	case 72: return fmr_lookup;
	case 134: return mtfsfi_lookup;
	case 136: return fnabs_lookup;
	case 264: return fabs_lookup;
	case 583: return mffs_lookup;
	case 711: return mtfsf_lookup;
	case 814: return fctid_lookup;
	case 815: return fctidz_lookup;
	case 846: return fcfid_lookup;
	default: break;
	}
	switch(secondaryOpcode & 0x1f) {
	case 23: throw new Error("fsel!");
	case 25: return fmul_lookup;
	case 28: return fmsub_lookup;
	case 29: return fmadd_lookup;
	case 30: return fnmsub_lookup;
	case 31: return fnmadd_lookup;
	default: break;
	}
	return null;
    }
    /**
     * Give the decoder for a given instruction. Extended instruction
     * decoders should return the appropriate instruction from opcode 2.
     * @param the instruction to get the decoder for
     * @param the appropriate instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	int secondaryOpcode =  bits(instr,21,30);
	return getOpCodeLookUp(-1,secondaryOpcode).decoder.getDecoder(instr);
    }
}

// -oO Start of D form instructions Oo-

/**
 * The decoder for the tdi instruction
 */
final class tdi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }
    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the twi instruction
 */
final class twi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
} 
/**
 * The decoder for the mulli instruction
 */
final class mulli_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mulli (multiply low immediate):
     * prod[0-48] <- (rA) * SIMM
     * (rD) <- prod[16-48]
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int simm) {
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_MUL, ppc2ir.getGPRegister(rD),
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(EXTS(simm,16))));
	return pc+4;
    }
}
/**
 * The decoder for the subfic instruction
 */
final class subfic_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate subfic (subtract from immediate carrying):
     * <listing>
     * rD <- ¬ (rA) + EXTS(SIMM) + 1
     * </listing>
     * Also modifies XER[CA]. Equivalent to:
     * <listing>
     * rD <- EXTS(SIMM) - (rA)
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int imm) {
	// Get rA & simm
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	int simm = EXTS(imm,16);

	// Perform subtract for rD
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, ppc2ir.getGPRegister(rD),
							     new OPT_IntConstantOperand(simm),
							     reg_rA.copyRO()));
	// Set XER CA based on result
	ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, ppc2ir.getXER_CA_Register(),
								 new OPT_IntConstantOperand(simm),
								 reg_rA.copyRO(),
								 OPT_ConditionOperand.LOWER(),
								 OPT_BranchProfileOperand.unlikely()));
	return pc+4;
    }
}
/**
 * The decoder for the cmpli instruction
 */
final class cmpli_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int crfD = bits(ps.currentInstruction,6,10) >>> 2;
	int rA = bits(ps.currentInstruction,11,15);
	int imm = EXTS(bits(ps.currentInstruction,16,31),16);
	ps.setCRfield_unsignedCompare(crfD, ps.getRegister(rA), EXTS(imm,16));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate cmpli (compare logical immediate):
     * <listing>
     * a <- (rA)
     * if a <U ((16)0 || UIMM)
     *    then c <- 0b100
     *    else if a >U ((16)0 || UIMM)
     *            then c <- 0b010
     *            else c <- 0b001
     * CR[(4 * crfD)-(4 * crfD + 3)] <- c || XER[SO]
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crfD0L, int rA, int uimm) {
	int crfD = crfD0L >>> 2;

	setCRfield(ppc2ir, lazy, crfD, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(uimm), UNSIGNED_INT_CMP);

	return pc+4;
    }
}
/**
 * The decoder for the cmpi instruction
 */
final class cmpi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int crfD = bits(ps.currentInstruction,6,10) >>> 2;
	int rA = bits(ps.currentInstruction,11,15);
	int imm = EXTS(bits(ps.currentInstruction,16,31),16);
	ps.setCRfield_signedCompare(crfD, ps.getRegister(rA), EXTS(imm,16));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate cmpi (compare immediate):
     * <listing>
     * a <- (rA)
     * if a < EXTS(SIMM)
     *    then c <- 0b100
     *    else if a > EXTS(SIMM)
     *            then c <- 0b010
     *            else c <- 0b001
     * CR[(4 * crfD)-(4 * crfD + 3)] <- c || XER[SO]
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crfD0L, int rA, int imm) {
	int crfD = crfD0L >>> 2;

	setCRfield(ppc2ir, lazy, crfD, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(EXTS(imm, 16)), SIGNED_INT_CMP);

	return pc+4;
    }
}
/**
 * The decoder for the addic instruction
 */
final class addic_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate addic (add immediate carrying)
     * <listing>
     * rD <- (rA) + EXTS(SIMM)
     * </listing>
     * Other simplified mnemonics: subic
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int imm) {
	// Get rA & simm
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rD = ppc2ir.getGPRegister(rD);
	int simm = EXTS(imm,16);

	// Perform add for rD
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD,
							     new OPT_IntConstantOperand(simm),
							     reg_rA.copyRO()));
	// Set XER CA based on result
	ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, ppc2ir.getXER_CA_Register(),
								 reg_rD.copyRO(),
								 new OPT_IntConstantOperand(simm), // could equally be reg_rA
								 OPT_ConditionOperand.LOWER(),
								 OPT_BranchProfileOperand.unlikely()));
	return pc+4;
    }
}
/**
 * The decoder for the addic. instruction
 */
final class addic__decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate addic. (add immediate carrying and record)
     * <listing>
     * rD <- (rA) + EXTS(SIMM)
     * </listing>
     * Other simplified mnemonics: subfic.
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int imm) {
	// Get rA & simm
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rD = ppc2ir.getGPRegister(rD);
	int simm = EXTS(imm,16);

	// Perform add for rD
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD,
							     new OPT_IntConstantOperand(simm),
							     reg_rA.copyRO()));
	// Set XER CA based on result
	ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, ppc2ir.getXER_CA_Register(),
								 reg_rD.copyRO(),
								 new OPT_IntConstantOperand(simm), // could equally be reg_rA
								 OPT_ConditionOperand.LOWER(),
								 OPT_BranchProfileOperand.unlikely()));
	// Make copies of operands for lazy state
	setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);

	return pc+4;
    }
}
/**
 * The decoder for the addi instruction
 */
final class addi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int simm = EXTS(bits(ps.currentInstruction,16,31),16);
	ps.setRegister(rD, (rA == 0) ? simm : (ps.getRegister(rA) + simm));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate addi (add immediate)
     * <listing>
     * if rA = 0 then rD <- EXTS(SIMM)
     * else rD <- (rA) + EXTS(SIMM)
     * </listing>
     * Other simplified mnemonics: li, la, subi
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int imm) {
	int simm = EXTS(imm, 16);

	if (rA == 0) {
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       ppc2ir.getGPRegister(rD),
							       new OPT_IntConstantOperand(simm)));        
	}
	else {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, ppc2ir.getGPRegister(rD),
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(simm)));
	}
	return pc+4;
    }
}
/**
 * The decoder for the addis instruction
 */
final class addis_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int simm = EXTS(bits(ps.currentInstruction,16,31),16) << 16;
	if (rA != 0) {
	    ps.setRegister(rD, ps.getRegister(rA) + simm);
	}
	else {
	    ps.setRegister(rD, simm);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate addis (add immediate shifted)
     * <listing>
     * if rA = 0 then rD <- EXTS(SIMM || (16)0)
     * else rD <- (rA) + EXTS(SIMM || (16)0)
     * </listing>
     * Other simplified mnemonics: lis, subis
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int imm) {
	int simm = EXTS(imm, 16);

	if (rA == 0) {
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       ppc2ir.getGPRegister(rD),
							       new OPT_IntConstantOperand(simm << 16)));
	}
	else {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, ppc2ir.getGPRegister(rD),
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(simm << 16)));
	}
	return pc+4;
    }
}

/**
 * The decoder for the ori instruction
 */
final class ori_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int uimm = bits(ps.currentInstruction,16,31);
	ps.setRegister(rA, ps.getRegister(rS) | uimm);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate ori (or immediate)
     * <listing>
     * rA <- (rS) | ((16)0 || UIMM)
     * </listing>
     * Other simplified mnemonics: nop
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int uimm) {
	if((uimm == 0) && (rA == rS)) {
	    // Don't translate nop cases
	}
	else {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rS),
								 new OPT_IntConstantOperand(uimm)));
	}
	return pc+4;
    }
}

/**
 * The decoder for the oris instruction
 */
final class oris_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate oris (or immediate shifted)
     * <listing>
     * rA <- (rS) | (UIMM || (16)0)
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int uimm) {
	if((uimm == 0) && (rA == rS)) {
	    // Don't translate nop case
	}
	else {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rS),
								 new OPT_IntConstantOperand(uimm << 16)));
	}
	return pc+4;
    }
}
/**
 * The decoder for the xori instruction
 */
final class xori_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate xori (xor immediate)
     * <listing>
     * rA <- (rS) (+) ((16)0 || UIMM)
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int uimm) {
	if((uimm == 0) && (rA == rS)) {
	    // Don't translate nop case
	}
	else {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_XOR, ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rS),
								 new OPT_IntConstantOperand(uimm)));
	}
	return pc+4;
    }
}
/**
 * The decoder for the xoris instruction
 */
final class xoris_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate xoris (xor immediate shifted)
     * <listing>
     * rA <- (rS) (+) (UIMM || (16)0)
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int uimm) {
	if((uimm == 0) && (rA == rS)) {
	    // Don't translate nop case
	}
	else {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_XOR, ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rS),
								 new OPT_IntConstantOperand(uimm << 16)));
	}
	return pc+4;
    }
}
/**
 * The decoder for the andi. instruction
 */
final class andi__decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int uimm = EXTS(bits(ps.currentInstruction,16,31),16);
	ps.setRegister(rA, ps.getRegister(rS) & uimm);
	ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate andi. (and immediate (and record))
     * <listing>
     * rA <- (rS) & ((16)0 || UIMM)
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int uimm) {
	OPT_RegisterOperand result =  ppc2ir.getGPRegister(rA);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, result,
							     ppc2ir.getGPRegister(rS),
							     new OPT_IntConstantOperand(uimm)));
	// Make copies of operands for lazy state
	setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	return pc+4;
    }
}

/**
 * The decoder for the andis. instruction
 */
final class andis__decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int uimm = EXTS(bits(ps.currentInstruction,16,31),16) << 16;
	ps.setRegister(rA, ps.getRegister(rS) & uimm);
	ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate andis. (and immediate shifted (and record))
     * <listing>
     * rA <- (rS) & (UIMM || (16)0)
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int uimm) {
	OPT_RegisterOperand result =  ppc2ir.getGPRegister(rA);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, result,
							     ppc2ir.getGPRegister(rS),
							     new OPT_IntConstantOperand(uimm << 16)));
	// Make copies of operands for lazy state
	setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	return pc+4;
    }
}

/**
 * The decoder for the lwz instruction
 */
final class lwz_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	ps.setRegister(rD, ps.memory.load32(EA));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lwz (load word and zero)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b+ EXTS(d)
     * rD <- MEM(EA, 4)
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Load value from memory at EA into rD.
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), ppc2ir.getGPRegister(rD));
	return pc+4;
    }
}
/**
 * The decoder for the lwzu instruction
 */
final class lwzu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = ps.getRegister(rA) + d;
	ps.setRegister(rD, ps.memory.load32(EA));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lwzu (load word and zero with update)
     * <listing>
     * EA <- rA + EXTS(d)
     * rD <- MEM(EA, 4)
     * rA <- EA
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	// EA = rA + EXTS(d)
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d)));
	// Load value from memory at EA into rD.
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), ppc2ir.getGPRegister(rD));
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the lbz instruction
 */
final class lbz_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	ps.setRegister(rD, ps.memory.loadUnsigned8(EA));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lbz (load byte and zero)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b+ EXTS(d)
     * rD <- (24)0 || MEM(EA, 1)
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoadUnsigned8(EA.copyRO(), result);
	return pc+4;
    }
}
/**
 * The decoder for the lbzu instruction
 * <listing>
 * EA <- rA + EXTS(d)
 * rD <- (24)0 || MEM(EA, 1)
 * rA <- EA
 * </listing>
 */
final class lbzu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = ps.getRegister(rA) + d;
	ps.setRegister(rD, ps.memory.loadUnsigned8(EA));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lbzu (load byte and zero with update)
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	// EA = rA + EXTS(d)
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d)));
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoadUnsigned8(EA.copyRO(), result);
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the stw instruction
 * <listing>
 * if rA = 0 then b <- 0
 * else b <- (rA)
 * EA <- b+ EXTS(d)
 * MEM(EA, 4) <- rS
 * </listing>
 */
final class stw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	ps.memory.store32(EA, ps.getRegister(rS));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stw (store word)
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), ppc2ir.getGPRegister(rS));
	return pc+4;
    }
}
/**
 * The decoder for the stwu instruction
 */
final class stwu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = ps.getRegister(rA) + d;
	ps.memory.store32(EA, ps.getRegister(rS));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stwu (store word with update)
     * <listing>
     * EA <- rA + EXTS(d)
     * MEM(EA, 4) <- rS
     * rA <- EA
     * </listing>
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	// EA = rA + EXTS(d)
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d)));
	// Load value from memory at EA into rD.
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), ppc2ir.getGPRegister(rS));
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the stb (store byte) instruction
 * <listing>
 * if rA = 0 then b <- 0
 * else b <- (rA)
 * EA <- b+ EXTS(d)
 * MEM(EA, 1) <- rS[24-31]
 * </listing>
 */
final class stb_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	ps.memory.store8(EA, ps.getRegister(rS));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stb
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore8(EA.copyRO(), ppc2ir.getGPRegister(rS));
	return pc+4;
    }
}

/**
 * The decoder for the stbu (store byte with update) instruction
 * <listing>
 * EA <- rA + EXTS(d)
 * MEM(EA, 1) <- rS[24-31]
 * rA <- EA
 * </listing>
 */
final class stbu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	ps.memory.store8(EA, ps.getRegister(rS));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stbu
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);

	if(rA == 0) {
	    throw new Error("Invalid form of stbu at 0x" + Integer.toHexString(pc));
	}

	// EA = rA + EXTS(d)
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d)));
	// Load value from memory at EA into rS.
	ppc2ir.ps.memory.translateStore8(EA.copyRO(), ppc2ir.getGPRegister(rS));
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}

/**
 * The decoder for the lhz (load half word and zero) instruction
 * <listing>
 * if rA = 0 then b <- 0
 * else b <- (rA)
 * EA <- b+ EXTS(d)
 * rD <- (16)0 || MEM(EA, 2)
 * </listing>
 */
final class lhz_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = ps.getRegister(rA) + d;
	ps.setRegister(rD, ps.memory.loadUnsigned16(EA));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lhz
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoadUnsigned16(EA.copyRO(), result);
	return pc+4;
    }
}
/**
 * The decoder for the lhzu (load half word and zero with update) instruction
 * <listing>
 * EA <- (rA) + EXTS(d)
 * rD <- (16)0 || MEM(EA, 2)
 * rA <- EA
 * </listing>
 */
final class lhzu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = ps.getRegister(rA) + d;
	ps.setRegister(rD, ps.memory.loadUnsigned16(EA));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lhzu
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), result);
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							   ppc2ir.getGPRegister(rA),
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the lha (load half word algabraeic) instruction
 * <listing>
 * if rA = 0 then b <- 0
 * else b <- (rA)
 * EA <- b+ EXTS(d)
 * rD <- EXTS(MEM(EA, 2))
 * </listing>
 */
final class lha_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = ps.getRegister(rA) + d;
	ps.setRegister(rD, ps.memory.loadSigned16(EA));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lha
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoadSigned16(EA.copyRO(), result);
	return pc+4;
    }
}
/**
 * The decoder for the lhau (load half word algabraeic with update) instruction
 * <listing>
 * EA <- (rA)+ EXTS(d)
 * rD <- EXTS(MEM(EA, 2))
 * rA <- EA
 * </listing>
 */
final class lhau_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = ps.getRegister(rA) + d;
	ps.setRegister(rD, ps.memory.loadSigned16(EA));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lhau
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	// EA = rA + EXTS(d)
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d)));
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoadSigned16(EA.copyRO(), result);
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the sth (store half word) instruction
 * <listing>
 * if rA = 0 then b <- 0
 * else b <- (rA)
 * EA <- b+ EXTS(d)
 * MEM(EA, 2) <- rS[16-31]
 * </listing>
 */
final class sth_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	ps.memory.store16(EA, ps.getRegister(rS));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate sth
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore16(EA.copyRO(), ppc2ir.getGPRegister(rS));
	return pc+4;
    }
}

/**
 * The decoder for the sthu (store half word with update) instruction
 * <listing>
 * EA <- (rA) + EXTS(d)
 * MEM(EA, 2) <- rS[16-31]
 * rA <- EA
 * </listing>
 */
final class sthu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = ps.getRegister(rA) + d;
	ps.memory.store16(EA, ps.getRegister(rS));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate sthu
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    throw new Error("Invalid form of sthu at 0x" + Integer.toHexString(pc));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore16(EA.copyRO(), ppc2ir.getGPRegister(rS));
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the lmw instruction
 */
final class lmw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stmw instruction
 */
final class stmw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lfs (load floating-point single) instruction
 * <listing>
 * if rA = 0 then b <- 0
 * else b <- (rA)
 * EA <- b+ EXTS(d)
 * frD <- DOUBLE(MEM(EA, 4))
 * </listing>
 */
final class lfs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int frD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : ps.getRegister(rA) + d;
	ps.setFPregister(frD, (double)Float.intBitsToFloat(ps.memory.load32(EA)));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lfs
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// Load value from memory at EA into tempInt
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0+1);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), tempInt);
	// Convert bit pattern to a float.
	OPT_RegisterOperand tempFloat = ppc2ir.getTempFloat(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_BITS_AS_FLOAT, tempFloat,
							    tempInt.copyRO()));

	// Now to a double, and put into register.
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_2DOUBLE, ppc2ir.getFPRegister(frD),
							    tempFloat.copyRO()));
	return pc+4;
    }
}

/**
 * The decoder for the lfsu (load floating-point single with update) instruction
 * <listing>
 * EA <- (rA)+ EXTS(d)
 * frD <- DOUBLE(MEM(EA, 4))
 * rA <- EA
 * </listing>
 */
final class lfsu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int frD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = ps.getRegister(rA) + d;
	ps.setFPregister(frD, (double)Float.intBitsToFloat(ps.memory.load32(EA)));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lfsu
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	// EA = rA + EXTS(d)
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d)));
	// Load value from memory at EA into tempInt
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0+1);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), tempInt);
	// Convert bit pattern to a float.
	OPT_RegisterOperand tempFloat = ppc2ir.getTempFloat(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_BITS_AS_FLOAT, tempFloat,
							    tempInt.copyRO()));

	// Now to a double, and put into register.
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_2DOUBLE, ppc2ir.getFPRegister(frD),
							    tempFloat.copyRO()));
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the lfd (load floating-point double) instruction
 * <listing>
 * if rA = 0 then b <- 0
 * else b <- (rA)
 * EA <- b+ EXTS(d)
 * frD <- MEM(EA, 8)
 * </listing>
 */
final class lfd_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int frD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	long value = (((long)ps.memory.load32(EA)) << 32) | (((long)ps.memory.load32(EA+4)) & 0xFFFFFFFF);
	ps.setFPregister(frD, Double.longBitsToDouble(value));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lfd
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	OPT_RegisterOperand EA2 = ppc2ir.getTempInt(0+1);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA2,
							       new OPT_IntConstantOperand(d+4)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA2,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d+4)));
	}
	// Load value from memory at EA & EA2 into tempInt & tempInt2 (msb at low address)
	OPT_RegisterOperand tempInt  = ppc2ir.getTempInt(0+2);
	OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(0+3);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), tempInt);
	ppc2ir.ps.memory.translateLoad32(EA2.copyRO(), tempInt2);

	// Merge ints into a long
	// tempLong = ((long)tempInt << 32) | (long)tempInt2
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);
	OPT_RegisterOperand tempLong2 = ppc2ir.getTempLong(1);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, tempLong, tempInt.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, tempLong2, tempInt2.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_SHL, tempLong.copyRO(), 
							     tempLong.copyRO(),
							     new OPT_IntConstantOperand(32)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, tempLong2.copyRO(), 
							     tempLong2.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffl)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_OR, tempLong.copyRO(), 
							     tempLong.copyRO(),
							     tempLong2.copyRO()));
	// Convert long to a double, and put into register.
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_BITS_AS_DOUBLE, ppc2ir.getFPRegister(frD),
							    tempLong.copyRO()));
	return pc+4;
    }
}

/**
 * The decoder for the lfdu (load floating-point double with update) instruction
 * <listing>
 * EA <- (rA)+ EXTS(d)
 * frD <- MEM(EA, 8)
 * rA <- EA
 * </listing>
 */
final class lfdu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int frD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	long value = (((long)ps.memory.load32(EA)) << 32) | (((long)ps.memory.load32(EA+4)) & 0xFFFFFFFF);
	ps.setFPregister(frD, Double.longBitsToDouble(value));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lfdu
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	OPT_RegisterOperand EA2 = ppc2ir.getTempInt(0+1);
	d = EXTS(d,16);
	// EA = rA + EXTS(d)
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA2,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d+4)));
	// Load value from memory at EA & EA2 into tempInt & tempInt2 (msb at low address)
	OPT_RegisterOperand tempInt  = ppc2ir.getTempInt(0+2);
	OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(0+3);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), tempInt);
	ppc2ir.ps.memory.translateLoad32(EA2.copyRO(), tempInt2);

	// Merge ints into a long
	// tempLong = ((long)tempInt << 32) | (long)tempInt2
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);
	OPT_RegisterOperand tempLong2 = ppc2ir.getTempLong(1);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, tempLong, tempInt.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, tempLong2, tempInt2.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_SHL, tempLong.copyRO(), 
							     tempLong.copyRO(),
							     new OPT_IntConstantOperand(32)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, tempLong2.copyRO(), 
							     tempLong2.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffl)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_OR, tempLong.copyRO(), 
							     tempLong.copyRO(),
							     tempLong2.copyRO()));
	// Convert long to a double, and put into register.
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_BITS_AS_DOUBLE, ppc2ir.getFPRegister(frD),
							    tempLong.copyRO()));
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the stfs (store floating point single) instruction
 * <listing>
 * if rA = 0 then b <- 0
 * else b <- (rA)
 * EA <- b+ EXTS(d)
 * MEM(EA, 4) <- SINGLE(frS)
 * </listing>
 */
final class stfs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int frS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	int value = Float.floatToIntBits((float)ps.getFPregister(frS));
	ps.memoryStore32(EA, value);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stfs
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// tempInt = SINGLE(frS)
	OPT_RegisterOperand tempFloat = ppc2ir.getTempFloat(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat,
							    ppc2ir.getFPRegister(frS)));
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0+1);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_AS_INT_BITS, tempInt, 
							    tempFloat.copyRO()));

	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), tempInt.copyRO());
	return pc+4;
    }
}
  
/**
 * The decoder for the stfsu (store floating point single with update) instruction
 * <listing>
 * EA <- (rA)+ EXTS(d)
 * MEM(EA, 4) <- SINGLE(frS)
 * rA <- EA
 * </listing>
 */
final class stfsu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int frS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	int value = Float.floatToIntBits((float)ps.getFPregister(frS));
	ps.memoryStore32(EA, value);
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stfsu
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	}
	// tempInt = SINGLE(frS)
	OPT_RegisterOperand tempFloat = ppc2ir.getTempFloat(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat,
							    ppc2ir.getFPRegister(frS)));
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0+1);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_AS_INT_BITS, tempInt, 
							    tempFloat.copyRO()));

	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), tempInt.copyRO());
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the stfd (store floating-point double) instruction
 * <listing>
 * if rA = 0 then b <- 0
 * else b <- (rA)
 * EA <- b+ EXTS(d)
 * MEM(EA, 8) <- (frS)
 * </listing>
 */
final class stfd_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int frS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	long value = Double.doubleToLongBits(ps.getFPregister(frS));
	ps.memory.store32(EA, (int)(value >>> 32));
	ps.memory.store32(EA+4, (int)(value & 0xFFFFFFFF));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stfd
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	OPT_RegisterOperand EA2 = ppc2ir.getTempInt(0+1);
	d = EXTS(d,16);
	if (rA == 0) {
	    // EA = EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       new OPT_IntConstantOperand(d)));
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA2,
							       new OPT_IntConstantOperand(d+4)));
	}
	else {
	    // EA = rA + EXTS(d)
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d)));
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA2,
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(d+4)));
	}
	// Split double into ints
	// tempLong = DOUBLE_AS_LONG_BITS(frS)
	// tempLong2 = tempLong >>> 32
	// tempLong = tempLong & 0xffffffff
	// tempInt = (int)tempLong
	// tempInt2 = (int)tempLong2 <- msb
	// mem[EA] = tempInt2
	// mem[EA2] = tempInt
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);
	OPT_RegisterOperand tempLong2 = ppc2ir.getTempLong(1);
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0+2);
	OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(0+3);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_AS_LONG_BITS, tempLong,
							    ppc2ir.getFPRegister(frS)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_USHR, tempLong2, tempLong.copyRO(),
							     new OPT_IntConstantOperand(32)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, tempLong.copyRO(), tempLong.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffl)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, tempInt, tempLong.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, tempInt2, tempLong2.copyRO()));

	// Store value into memory at address EA (msb at low address)
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), tempInt2.copyRO());
	ppc2ir.ps.memory.translateStore32(EA2.copyRO(),tempInt.copyRO());
	return pc+4;
    }
}

/**
 * The decoder for the stfdu (store floating-point double with update) instruction
 * <listing>
 * EA <- (rA)+ EXTS(d)
 * MEM(EA, 8) <- (frS)
 * rA <- EA
 * </listing>
 */
final class stfdu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int frS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int d = EXTS(bits(ps.currentInstruction,16,31),16);
	int EA = (rA == 0) ? d : (ps.getRegister(rA) + d);
	long value = Double.doubleToLongBits(ps.getFPregister(frS));
	ps.memory.store32(EA, (int)(value >>> 32));
	ps.memory.store32(EA+4, (int)(value & 0xFFFFFFFF));
	ps.setRegister(rA, EA);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stfdu
     */
    protected int translateD_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frS, int rA, int d) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	OPT_RegisterOperand EA2 = ppc2ir.getTempInt(0+1);
	d = EXTS(d,16);
	// EA = rA + EXTS(d)
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA2,
							     ppc2ir.getGPRegister(rA),
							     new OPT_IntConstantOperand(d+4)));
	// Split double into ints
	// tempLong = DOUBLE_AS_LONG_BITS(frS)
	// tempLong2 = tempLong >>> 32
	// tempLong = tempLong & 0xffffffff
	// tempInt = (int)tempLong
	// tempInt2 = (int)tempLong2 <- msb
	// mem[EA] = tempInt2
	// mem[EA2] = tempInt
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);
	OPT_RegisterOperand tempLong2 = ppc2ir.getTempLong(1);
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0+2);
	OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(0+3);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_AS_LONG_BITS, tempLong,
							    ppc2ir.getFPRegister(frS)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_USHR, tempLong2, tempLong.copyRO(),
							     new OPT_IntConstantOperand(32)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, tempLong.copyRO(), tempLong.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffl)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, tempInt, tempLong.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, tempInt2, tempLong2.copyRO()));

	// Store value into memory at address EA (msb at low address)
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), tempInt2.copyRO());
	ppc2ir.ps.memory.translateStore32(EA2.copyRO(), tempInt.copyRO());
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
  
// -oO Start of XL form instructions Oo-
/**
 * The decoder for the mcrf instruction
 */
final class mcrf_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mcrf (move condition register field)
     * <listing>
     * CR[(4 * crfD) through (4 * crfD + 3)] <- CR[(4 * crfS) through (4 * crfS + 3)]
     * </listing>
     */
    protected int translateXL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crfD00, int crfS00, int zero, int secondaryOpcode, int zero2){
	if (VM.VerifyAssertions) VM._assert((secondaryOpcode == 0) && (zero == 0) && (zero2 == 0));
	int crfD = crfD00 >> 2;
	int crfS = crfS00 >> 2;
	
	if (crfD != crfS) {
	    OPT_RegisterOperand ltS = ppc2ir.getCR_Lt_Register(crfS);
	    OPT_RegisterOperand gtS = ppc2ir.getCR_Gt_Register(crfS);
	    OPT_RegisterOperand eqS = ppc2ir.getCR_Eq_Register(crfS);
	    OPT_RegisterOperand soS = ppc2ir.getCR_SO_Register(crfS);

	    OPT_RegisterOperand ltD = ppc2ir.getCR_Lt_Register(crfD);
	    OPT_RegisterOperand gtD = ppc2ir.getCR_Gt_Register(crfD);
	    OPT_RegisterOperand eqD = ppc2ir.getCR_Eq_Register(crfD);
	    OPT_RegisterOperand soD = ppc2ir.getCR_SO_Register(crfD);

	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ltD, ltS));
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, gtD, gtS));
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, eqD, eqS));
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, soD, soS));
	}
	return pc+ 4;
    }
}

/**
 * The decoder for the bclr instruction
 */
final class bclr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int BO = bits(ps.currentInstruction,6,10);
	int BI = bits(ps.currentInstruction,11,15);
	int LK = ps.currentInstruction & 0x1;
	int target_address = ps.lr;
	if (LK != 0) {
	    ps.lr = ps.getCurrentInstructionAddress() + 4;
	    ps.branchInfo.registerCall(ps.getCurrentInstructionAddress(), ps.getCurrentInstructionAddress()+4, target_address);
	}
	// decode BO
	boolean branch_if_ctr_zero = ((BO & 2) == 0);
	if ((BO & 4) == 0) {
	    ps.ctr--;
	    if ((branch_if_ctr_zero && (ps.ctr != 0))||
		(!branch_if_ctr_zero && (ps.ctr == 0))) {
		return moveInstructionOnAndReturnDecoder(ps);
	    }
	}
	if ((BO & 16) == 0) {
	    boolean branch_if_cond_true = ((BO & 8) != 0);
	    boolean cr_bit_set = ps.getCR_bit(BI);
	    if((branch_if_cond_true && !cr_bit_set) ||
	       (!branch_if_cond_true && cr_bit_set)){
		return moveInstructionOnAndReturnDecoder(ps);
	    }
	}
	ps.setCurrentInstructionAddress(target_address);
	ps.currentInstruction = ps.memoryLoad32(target_address);
	return findDecoder(ps.currentInstruction);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate bclr (branch conditional to link register)
     * <listing>
     * if ¬ BO[2] then CTR <- CTR - 1
     * ctr_ok <- BO[2] | ((CTR != 0) (+) BO[3])
     * cond_ok <- BO[0] | (CR[BI] == BO[1])
     * if ctr_ok & cond_ok then
     *   NIA <-iea LR || 0b00
     *   if LK then LR <-iea CIA + 4
     * </listing>
     * Other simplified mnemonics: bltlr, bnelr, bdnzlr
     */
    protected int translateXL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int BO, int BI, int zero, int secondaryOpcode, int lk){
	if (VM.VerifyAssertions) VM._assert((secondaryOpcode == 16) && (zero == 0));

	// Translation process:
	// decode BO and optionally plant any of:
	//  1: decrement CTR
	//  2: branch to instructionEndBlock if CTR == 0 (or CTR != 0)
	//  3: condition field test and branch to instructionEndBlock if true (or false)
	// plant branch block (and if lk alter lr value)

	// The block gone to if the branch isn't taken
	OPT_BasicBlock instructionEndBlock = ppc2ir.getNextBlock();

	// Decode BO
	boolean likely_to_fallthrough = ((BO & 1) == 0);
	boolean branch_if_ctr_zero = ((BO & 2) == 0);
	if ((BO & 4) == 0) {
	    plantDecrementCTR(ppc2ir);
	    plantBranchToBlockDependentOnCTR(ppc2ir, instructionEndBlock, branch_if_ctr_zero, likely_to_fallthrough);
	}
	if ((BO & 16) == 0) {
	    boolean branch_if_cond_true = ((BO & 8) != 0);
	    plantBranchToBlockDependentOnCondition(ppc2ir, instructionEndBlock, lazy, BI, !branch_if_cond_true, likely_to_fallthrough);
	}

	// Plant branch block
	OPT_RegisterOperand branchAddress;
	if (lk != 0){
	    // @todo: record the pc as an address that set lr
	    OPT_RegisterOperand lr = ppc2ir.getLRRegister();
	    branchAddress = ppc2ir.getTempInt(0);
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       branchAddress.copyRO(),
							       lr));
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, lr.copyRO(),
							       new OPT_IntConstantOperand(pc+4)));
	}
	else {
	    branchAddress = ppc2ir.getLRRegister();
	}
	OPT_BasicBlock fallThrough = ppc2ir.createBlockAfterCurrent();
	OPT_Instruction lookupswitch_instr;
	lookupswitch_instr = LookupSwitch.create(LOOKUPSWITCH, branchAddress, null, null, 
						 fallThrough.makeJumpTarget(), 
						 null, 0);
	ppc2ir.appendInstructionToCurrentBlock(lookupswitch_instr);
	ppc2ir.registerLookupSwitchForReturnUnresolved(lookupswitch_instr, pc, (PPC_Laziness)lazy.clone());
	ppc2ir.setCurrentBlock(fallThrough);
	ppc2ir.plantRecordUncaughtBranch(pc, branchAddress.copyRO(), BranchLogic.RETURN);
	ppc2ir.setReturnValueResolveLazinessAndBranchToFinish((PPC_Laziness)lazy.clone(), branchAddress.copyRO());

	// stop translation on branch always
	if (BO == 0x14) {
	    return -1;
	}
	else {
	    // continue translation of next instruction 
	    return pc+4;
	}
    }
}

/**
 * The decoder for the crnor instruction
 */
final class crnor_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate crnor (condition register nor)
     * <listing>
     * CR[crbD] <-  ¬(CR[crbA] | CR[crbB])
     * </listing>
     * Other simplified mnemonics: crnot
     */
    protected int translateXL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crbD, int crbA, int crbB, int secondaryOpcode, int zero){
	if (VM.VerifyAssertions) VM._assert((secondaryOpcode == 33) && (zero == 0));      

	// Resolve laziness as this instruction encodes lots of
	// nonsensical states for the condition register :-(
	ppc2ir.resolveLazinessCrBit(lazy, crbA);

	OPT_RegisterOperand cr = ppc2ir.getCRRegister();
	if((crbA == crbB)&&(crbA == crbD)) { // crnot crbA, crbA -> just twiddle bit in position
	    int bitMask = 0x80000000 >>> crbA;
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_XOR, cr.copyRO(),
								 cr,
								 new OPT_IntConstantOperand(bitMask)));
	}
	else {
	    ppc2ir.resolveLazinessCrBit(lazy, crbB);
	    ppc2ir.resolveLazinessCrBit(lazy, crbD);

	    // Move crbA into position
	    OPT_RegisterOperand tempInt1;
	    if (crbA < crbD) {
		tempInt1 = ppc2ir.getTempInt(0);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt1,
								     cr,
								     new OPT_IntConstantOperand(crbD - crbA)));
	    }
	    else if (crbA > crbD) {
		tempInt1 = ppc2ir.getTempInt(0);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt1,
								     cr,
								     new OPT_IntConstantOperand(crbA - crbD)));
	    }
	    else {
		tempInt1 = cr;
	    }
	    // Move crbB into position
	    OPT_RegisterOperand tempInt2;
	    if (crbB < crbD) {
		tempInt2 = ppc2ir.getTempInt(1);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt2,
								     cr.copyRO(),
								     new OPT_IntConstantOperand(crbD - crbB)));
	    }
	    else if (crbB > crbD) {
		tempInt2 = ppc2ir.getTempInt(1);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt2,
								     cr.copyRO(),
								     new OPT_IntConstantOperand(crbB - crbD)));
	    }
	    else {
		tempInt2 = cr;
	    }
	    // Perform NOR
	    OPT_RegisterOperand tempInt3 = ppc2ir.getTempInt(2);
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, tempInt3,
								 tempInt1.copyRO(),
								 tempInt2.copyRO()));
	    ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, tempInt3.copyRO(),
								tempInt3.copyRO()));
	    // Mask out bits that don't concern this instruction
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt3.copyRO(),
								 tempInt3.copyRO(),
								 new OPT_IntConstantOperand(0x80000000 >>> crbD)));
	    // Clear desitination bit
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, cr.copyRO(),
								 cr.copyRO(),
								 new OPT_IntConstantOperand(~(0x80000000 >>> crbD))));
	    // Combine result
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, cr.copyRO(),
								 cr.copyRO(),
								 tempInt3.copyRO()));
	}
	return pc+4;
    }
}
/**
 * The decoder for the rfi instruction
 */
final class rfi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the crandc instruction
 */
final class crandc_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the isync instruction
 */
final class isync_transltor extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the crxor instruction
 */
final class crxor_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int crbD = bits(ps.currentInstruction,6,10);
	int crbA = bits(ps.currentInstruction,11,15);
	int crbB = bits(ps.currentInstruction,16,20);

	ps.setCR_bit(crbD, ps.getCR_bit(crbA) != ps.getCR_bit(crbB));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate crxor (condition register xor)
     * <listing>
     * CR[crbD] <-  CR[crbA] (+) CR[crbB]
     * </listing>
     * Other simplified mnemonics: crclr
     */
    protected int translateXL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crbD, int crbA, int crbB, int secondaryOpcode, int zero){
	if (VM.VerifyAssertions) VM._assert((secondaryOpcode == 193) && (zero == 0));      

	// Resolve laziness as this instruction encodes lots of
	// nonsensical states for the condition register :-(
	ppc2ir.resolveLazinessCrBit(lazy, crbA);

	OPT_RegisterOperand cr = ppc2ir.getCRRegister();
	if((crbA == crbB) && (crbA == crbD)) { // crclr crbD -> just zero bit in position
	    OPT_RegisterOperand crb_regOp = ppc2ir.getCRB_Register(crbA);
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, crb_regOp,
							       new OPT_IntConstantOperand(0)));
	}
	else {
	    OPT_RegisterOperand crbA_regOp = ppc2ir.getCRB_Register(crbA);
	    OPT_RegisterOperand crbB_regOp = ppc2ir.getCRB_Register(crbB);
	    OPT_RegisterOperand crbD_regOp = ppc2ir.getCRB_Register(crbD);
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, crbD_regOp,
								     crbB_regOp, crbA_regOp,
								     OPT_ConditionOperand.NOT_EQUAL(),
								     OPT_BranchProfileOperand.unlikely()));
	}
	return pc+4;
    }
}

/**
 * The decoder for the crnand instruction
 */
final class crnand_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the crand instruction
 */
final class crand_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the creqv instruction
 */
final class creqv_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate creqv (condition register equivalent)
     * <listing>
     * CR[crbD] <-  CR[crbA] == CR[crbB]
     * </listing>
     * Other simplified mnemonics: crset
     */
    protected int translateXL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crbD, int crbA, int crbB, int secondaryOpcode, int zero){
	if (VM.VerifyAssertions) VM._assert((secondaryOpcode == 289) && (zero == 0));      

	// Resolve laziness as this instruction encodes lots of
	// nonsensical states for the condition register :-(
	ppc2ir.resolveLazinessCrBit(lazy, crbA);

	OPT_RegisterOperand cr = ppc2ir.getCRRegister();
	if((crbA == crbB)&&(crbA == crbD)) { // crset crbD -> just set bit in position
	    int bitMask = 0x80000000 >>> crbA;
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, cr.copyRO(),
								 cr,
								 new OPT_IntConstantOperand(bitMask)));
	}
	else {
	    ppc2ir.resolveLazinessCrBit(lazy, crbB);
	    ppc2ir.resolveLazinessCrBit(lazy, crbD);

	    // Move crbA into position
	    OPT_RegisterOperand tempInt1;
	    if (crbA < crbD) {
		tempInt1 = ppc2ir.getTempInt(0);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt1,
								     cr,
								     new OPT_IntConstantOperand(crbD - crbA)));
	    }
	    else if (crbA > crbD) {
		tempInt1 = ppc2ir.getTempInt(0);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt1,
								     cr,
								     new OPT_IntConstantOperand(crbA - crbD)));
	    }
	    else {
		tempInt1 = cr;
	    }
	    // Move crbB into position
	    OPT_RegisterOperand tempInt2;
	    if (crbB < crbD) {
		tempInt2 = ppc2ir.getTempInt(1);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt2,
								     cr.copyRO(),
								     new OPT_IntConstantOperand(crbD - crbB)));
	    }
	    else if (crbB > crbD) {
		tempInt2 = ppc2ir.getTempInt(1);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt2,
								     cr.copyRO(),
								     new OPT_IntConstantOperand(crbB - crbD)));
	    }
	    else {
		tempInt2 = cr;
	    }
	    // Perform equivalence
	    OPT_RegisterOperand tempInt3 = ppc2ir.getTempInt(2);
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_XOR, tempInt3,
								 tempInt1.copyRO(),
								 tempInt2.copyRO()));
	    ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, tempInt3.copyRO(),
								tempInt3.copyRO()));
	    // Mask out bits that don't concern this instruction
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt3.copyRO(),
								 tempInt3.copyRO(),
								 new OPT_IntConstantOperand(0x80000000 >>> crbD)));
	    // Clear desitination bit
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, cr.copyRO(),
								 cr.copyRO(),
								 new OPT_IntConstantOperand(~(0x80000000 >>> crbD))));
	    // Combine result
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, cr.copyRO(),
								 cr.copyRO(),
								 tempInt3.copyRO()));
	}
	return pc+4;
    }
}

/**
 * The decoder for the croc instruction
 */
final class croc_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the cror instruction
 */
final class cror_traslator extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate cror (condition register or)
     * <listing>
     * CR[crbD] <-  CR[crbA] | CR[crbB]
     * </listing>
     * Other simplified mnemonics: crclr
     */
    protected int translateXL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crbD, int crbA, int crbB, int secondaryOpcode, int zero){
	if (VM.VerifyAssertions) VM._assert((secondaryOpcode == 449) && (zero == 0));      

	// Resolve laziness as this instruction encodes lots of
	// nonsensical states for the condition register :-(
	ppc2ir.resolveLazinessCrBit(lazy, crbA);


	if((crbA == crbB) && (crbA == crbD)) {
	    // nop
	}
	else if (crbA == crbB) {
	    // crmove
	    OPT_RegisterOperand cr = ppc2ir.getCRRegister();
	    // Move crbA into position
	    OPT_RegisterOperand tempInt1;
	    if (crbA < crbD) {
		ppc2ir.resolveLazinessCrBit(lazy, crbB);
		ppc2ir.resolveLazinessCrBit(lazy, crbD);

		tempInt1 = ppc2ir.getTempInt(0);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt1,
								     cr,
								     new OPT_IntConstantOperand(crbD - crbA)));
	    }
	    else if (crbA > crbD) {
		tempInt1 = ppc2ir.getTempInt(0);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt1,
								     cr,
								     new OPT_IntConstantOperand(crbA - crbD)));
	    }
	    else {
		tempInt1 = cr;
	    }
	    // Mask out bits that don't concern this instruction
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt1.copyRO(),
								 tempInt1.copyRO(),
								 new OPT_IntConstantOperand(0x80000000 >>> crbD)));
	    // Clear desitination bit
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, cr.copyRO(),
								 cr.copyRO(),
								 new OPT_IntConstantOperand(~(0x80000000 >>> crbD))));
	    // Combine result
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, cr.copyRO(),
								 cr.copyRO(),
								 tempInt1.copyRO()));		
	}
	else {
	    OPT_RegisterOperand cr = ppc2ir.getCRRegister();
	    // Move crbA into position
	    OPT_RegisterOperand tempInt1;
	    if (crbA < crbD) {
		ppc2ir.resolveLazinessCrBit(lazy, crbB);
		ppc2ir.resolveLazinessCrBit(lazy, crbD);

		tempInt1 = ppc2ir.getTempInt(0);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt1,
								     cr,
								     new OPT_IntConstantOperand(crbD - crbA)));
	    }
	    else if (crbA > crbD) {
		tempInt1 = ppc2ir.getTempInt(0);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt1,
								     cr,
								     new OPT_IntConstantOperand(crbA - crbD)));
	    }
	    else {
		tempInt1 = cr;
	    }
	    // Move crbB into position
	    OPT_RegisterOperand tempInt2;
	    if (crbB < crbD) {
		tempInt2 = ppc2ir.getTempInt(1);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt2,
								     cr.copyRO(),
								     new OPT_IntConstantOperand(crbD - crbB)));
	    }
	    else if (crbB > crbD) {
		tempInt2 = ppc2ir.getTempInt(1);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt2,
								     cr.copyRO(),
								     new OPT_IntConstantOperand(crbB - crbD)));
	    }
	    else {
		tempInt2 = cr;
	    }
	    // Perform OR
	    OPT_RegisterOperand tempInt3 = ppc2ir.getTempInt(2);
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, tempInt3,
								 tempInt1.copyRO(),
								 tempInt2.copyRO()));
	    // Mask out bits that don't concern this instruction
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt3.copyRO(),
								 tempInt3.copyRO(),
								 new OPT_IntConstantOperand(0x80000000 >>> crbD)));
	    // Clear desitination bit
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, cr.copyRO(),
								 cr.copyRO(),
								 new OPT_IntConstantOperand(~(0x80000000 >>> crbD))));
	    // Combine result
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, cr.copyRO(),
								 cr.copyRO(),
								 tempInt3.copyRO()));
	}
	return pc+4;
    }
}
/**
 * The decoder for the bcctr instruction
 */
final class bcctr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int BO = bits(ps.currentInstruction,6,10);
	int BI = bits(ps.currentInstruction,11,15);
	int LK = ps.currentInstruction & 0x1;
	int target_address = ps.ctr;
	if (LK != 0) {
	    ps.lr = ps.getCurrentInstructionAddress() + 4;
	    ps.branchInfo.registerCall(ps.getCurrentInstructionAddress(), ps.getCurrentInstructionAddress()+4, target_address);
	}
	// decode BO
	boolean branch_if_ctr_zero = ((BO & 2) == 0);
	if ((BO & 4) == 0) {
	    ps.ctr--;
	    if ((branch_if_ctr_zero && (ps.ctr != 0))||
		(!branch_if_ctr_zero && (ps.ctr == 0))) {
		return moveInstructionOnAndReturnDecoder(ps);
	    }
	}
	if ((BO & 16) == 0) {
	    boolean branch_if_cond_true = ((BO & 8) != 0);
	    boolean cr_bit_set = ps.getCR_bit(BI);
	    if((branch_if_cond_true && !cr_bit_set) ||
	       (!branch_if_cond_true && cr_bit_set)){
		return moveInstructionOnAndReturnDecoder(ps);
	    }
	}
	ps.setCurrentInstructionAddress(target_address);
	ps.currentInstruction = ps.memoryLoad32(target_address);
	return findDecoder(ps.currentInstruction);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate bcctr (branch conditional to count register)
     * <listing>
     * if ¬ BO[2] then CTR <- CTR - 1
     * ctr_ok <- BO[2] | ((CTR != 0) (+) BO[3])
     * cond_ok <- BO[0] | (CR[BI] == BO[1])
     * if ctr_ok & cond_ok then
     *   NIA <-iea CTR || 0b00
     *   if LK then LR <-iea CIA + 4
     * </listing>
     * Other simplified mnemonics: bltctr, bnectr
     */
    protected int translateXL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int BO, int BI, int zero, int secondaryOpcode, int lk){
	if (VM.VerifyAssertions) VM._assert((secondaryOpcode == 528) && (zero == 0));
	 
	// Translation process:
	// decode BO and optionally plant any of:
	//  1: decrement CTR
	//  2: branch to instructionEndBlock if CTR == 0 (or CTR != 0)
	//  3: condition field test and branch to instructionEndBlock if true (or false)
	// plant branch block (and if lk alter lr value)

	// The block gone to if the branch isn't taken
	OPT_BasicBlock instructionEndBlock = ppc2ir.getNextBlock();

	// Decode BO
	boolean likely_to_fallthrough = ((BO & 1) == 0);
	boolean branch_if_ctr_zero = ((BO & 2) == 0);
	if ((BO & 4) == 0) {
	    plantDecrementCTR(ppc2ir);
	    plantBranchToBlockDependentOnCTR(ppc2ir, instructionEndBlock, branch_if_ctr_zero, likely_to_fallthrough);
	}
	if ((BO & 16) == 0) {
	    boolean branch_if_cond_true = ((BO & 8) != 0);
	    plantBranchToBlockDependentOnCondition(ppc2ir, instructionEndBlock, lazy, BI, !branch_if_cond_true, likely_to_fallthrough);
	}

	// Plant branch block
	if (lk != 0){
	    // @todo: record the pc as an address that set lr
	    OPT_RegisterOperand lr = ppc2ir.getLRRegister();
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       lr,
							       new OPT_IntConstantOperand(pc+4)));
	}
	OPT_RegisterOperand branchAddress = ppc2ir.getCTRRegister();
	OPT_BasicBlock fallThrough = ppc2ir.createBlockAfterCurrent();
	OPT_Instruction lookupswitch_instr;
	lookupswitch_instr = LookupSwitch.create(LOOKUPSWITCH, branchAddress, null, null, 
						 fallThrough.makeJumpTarget(), 
						 null, 0);
	ppc2ir.appendInstructionToCurrentBlock(lookupswitch_instr);
	ppc2ir.registerLookupSwitchForSwitchUnresolved(lookupswitch_instr, pc, (PPC_Laziness)lazy.clone(), lk != 0);
	ppc2ir.setCurrentBlock(fallThrough);
	ppc2ir.plantRecordUncaughtBranch(pc, branchAddress.copyRO(), BranchLogic.INDIRECT_BRANCH);
	ppc2ir.setReturnValueResolveLazinessAndBranchToFinish((PPC_Laziness)lazy.clone(), branchAddress.copyRO());

	// stop translation on branch always
	if (BO == 0x14) {
	    return -1;
	}
	else {
	    // continue translation of next instruction 
	    return pc+4;
	}
    }
}

// -oO Start of XO form instructions Oo-

/**
 * The decoder for the subfc instruction
 */
final class subfc_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate subfc (subtract from carrying):
     * <listing>
     * rD <- ¬ (rA) + (rB) + 1
     * </listing>
     * Also modifies XER[CA]. Equivalent to:
     * <listing>
     * rD <- (rB) - (rA)
     * </listing>
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Get rA & rB
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rB = ppc2ir.getGPRegister(rB);

	// Perform subtract for rD
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, ppc2ir.getGPRegister(rD),
							     reg_rB.copyRO(),
							     reg_rA.copyRO()));
	// Set XER CA based on result
	ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, ppc2ir.getXER_CA_Register(),
								 reg_rB.copyRO(),
								 reg_rA.copyRO(),
								 OPT_ConditionOperand.LOWER(),
								 OPT_BranchProfileOperand.unlikely()));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}
/**
 * The decoder for the addc instruction
 */
final class addc_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate addc (add carrying):
     * <listing>
     * rD <- (rA) + (rB)
     * </listing>
     * Also modifies XER[CA].
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Get rA, rB & rD
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rB = ppc2ir.getGPRegister(rB);
	OPT_RegisterOperand reg_rD = ppc2ir.getGPRegister(rD);

	// Perform operation for rD
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD,
							     reg_rB.copyRO(),
							     reg_rA.copyRO()));
	// Set XER CA based on result
	ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, ppc2ir.getXER_CA_Register(),
								 reg_rD.copyRO(),
								 reg_rA.copyRO(), // could equally be reg_rB
								 OPT_ConditionOperand.LOWER(),
								 OPT_BranchProfileOperand.unlikely()));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, reg_rD.copyRO(), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the mulhwu instruction
 */
final class mulhwu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mulhwu (multiply high word unsigned):
     * <listing>
     * prod[0-63] <- rA * rB
     * rD <- prod[0-31]
     * </listing>
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Get rA & rB as longs
	OPT_RegisterOperand long_rA = ppc2ir.getTempLong(0);
	OPT_RegisterOperand long_rB = ppc2ir.getTempLong(1);
	OPT_RegisterOperand long_rD = ppc2ir.getTempLong(2);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, long_rA, ppc2ir.getGPRegister(rA)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, long_rB, ppc2ir.getGPRegister(rB)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, long_rA.copyRO(),
							     long_rA.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffL)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, long_rB.copyRO(),
							     long_rB.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffL)));
	// Perform multiply
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_MUL, long_rD,
							     long_rB.copyRO(),
							     long_rA.copyRO()));
	// Get high 32bits
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_USHR, long_rD.copyRO(),
							     long_rD.copyRO(),
							     new OPT_IntConstantOperand(32)));
	// Set rD
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, ppc2ir.getGPRegister(rD),
							    long_rD.copyRO()));
	 
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the subf instruction
 */
final class subf_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	int Rc = ps.currentInstruction & 0x1;
	ps.setRegister(rD, ps.getRegister(rB) - ps.getRegister(rA));
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rD), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate subf (subtract from):
     * <listing>
     * rD <- ¬ (rA) + (rB) + 1
     * </listing>
     * Equivalent to:
     * <listing>
     * rD <- (rB) - (rA)
     * </listing>
     * Other simplified mnemonics: subf
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Get rA & rB
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rB = ppc2ir.getGPRegister(rB);

	// Perform subtract for rD
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, ppc2ir.getGPRegister(rD),
							     reg_rB,
							     reg_rA));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the mulhw instruction
 */
final class mulhw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mulhw (multiply high word):
     * <listing>
     * prod[0-63] <- rA * rB
     * rD <- prod
     * </listing>
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Get rA & rB as longs
	OPT_RegisterOperand long_rA = ppc2ir.getTempLong(0);
	OPT_RegisterOperand long_rB = ppc2ir.getTempLong(1);
	OPT_RegisterOperand long_rD = ppc2ir.getTempLong(2);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, long_rA, ppc2ir.getGPRegister(rA)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, long_rB, ppc2ir.getGPRegister(rB)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, long_rA.copyRO(),
							     long_rA.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffl)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, long_rB.copyRO(),
							     long_rB.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffl)));


	// Perform multiply
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_MUL, long_rD,
							     long_rB.copyRO(),
							     long_rA.copyRO()));
	// Get high 32bits
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_USHR, long_rD.copyRO(),
							     long_rD.copyRO(),
							     new OPT_IntConstantOperand(32)));
	// Set rD
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, ppc2ir.getGPRegister(rD),
							    long_rD.copyRO()));

	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the neg instruction
 */
final class neg_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int Rc = ps.currentInstruction & 0x1;
	ps.setRegister(rD, 0 - ps.getRegister(rA));
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rD), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate neg (negate):
     * <listing>
     * rD <- ¬ (rA) + 1
     * </listing>
     * Equivalent to:
     * <listing>
     * rD <- -(rA)
     * </listing>
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	if (VM.VerifyAssertions) VM._assert(rB == 0);

	// Perform negation
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NEG, ppc2ir.getGPRegister(rD), ppc2ir.getGPRegister(rA)));

	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the subfe instruction
 */
final class subfe_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate subfe (subtract from extended):
     * <listing>
     * rD <- ¬ (rA) + (rB) + XER[CA]
     * </listing>
     * Also modifies XER[CA].
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Get rA & rB
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rB = ppc2ir.getGPRegister(rB);
	OPT_RegisterOperand reg_rD = ppc2ir.getGPRegister(rD);
	OPT_RegisterOperand tempInt  = ppc2ir.getTempInt(0);
	OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(1);

	// tempInt := XER_CA ? 1 : 0
	ppc2ir.appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, tempInt,
							       ppc2ir.getXER_CA_Register(),
							       new OPT_IntConstantOperand(0),
							       OPT_ConditionOperand.EQUAL(),
							       new OPT_IntConstantOperand(0),
							       new OPT_IntConstantOperand(1)));
	// tempInt2 := ¬ (rA)
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, tempInt2,
							    reg_rA));
	// rD := tempInt2 + (rB) + tempInt
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD,
							     tempInt2.copyRO(),
							     reg_rB));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD.copyRO(),
							     reg_rD.copyRO(),
							     tempInt.copyRO()));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, reg_rD.copyRO(), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the adde instruction
 */
final class adde_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate adde (add extended):
     * <listing>
     * rD <- (rA) + (rB) + XER[CA]
     * </listing>
     * Also modifies XER[CA].
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Get rA & rB
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rB = ppc2ir.getGPRegister(rB);
	OPT_RegisterOperand reg_rD = ppc2ir.getGPRegister(rD);
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);

	// tempInt := XER_CA ? 1 : 0
	ppc2ir.appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, tempInt,
							       ppc2ir.getXER_CA_Register(),
							       new OPT_IntConstantOperand(0),
							       OPT_ConditionOperand.EQUAL(),
							       new OPT_IntConstantOperand(0),
							       new OPT_IntConstantOperand(1)));
	// rD := (rA) + (rB) + tempInt
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD,
							     reg_rA,
							     reg_rB));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD.copyRO(),
							     reg_rD.copyRO(),
							     tempInt.copyRO()));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, reg_rD.copyRO(), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}
/**
 * The decoder for the subfze instruction
 */
final class subfze_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate subfze (subtract from zero extended):
     * <listing>
     * rD <- ¬(rA) + XER[CA]
     * </listing>
     * Also modifies XER[CA].
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	if (VM.VerifyAssertions) VM._assert(rB == 0);
	// Get rA
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rD = ppc2ir.getGPRegister(rD);
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);

	// tempInt := XER_CA ? 1 : 0
	ppc2ir.appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, tempInt,
							       ppc2ir.getXER_CA_Register(),
							       new OPT_IntConstantOperand(0),
							       OPT_ConditionOperand.EQUAL(),
							       new OPT_IntConstantOperand(0),
							       new OPT_IntConstantOperand(1)));
	// rD := ¬ (rA)
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, reg_rD,
							    reg_rA));
	// rD := rD + tempInt
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD.copyRO(),
							     reg_rD.copyRO(),
							     tempInt.copyRO()));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, reg_rD.copyRO(), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}
/**
 * The decoder for the addze instruction
 */
final class addze_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate adde (add to zero extended):
     * <listing>
     * rD <- (rA) + XER[CA]
     * </listing>
     * Also modifies XER[CA].
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	if (VM.VerifyAssertions) VM._assert(rB == 0);
	// Get rA & rD
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rD = ppc2ir.getGPRegister(rD);
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);

	// tempInt := XER_CA ? 1 : 0
	ppc2ir.appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, tempInt,
							       ppc2ir.getXER_CA_Register(),
							       new OPT_IntConstantOperand(0),
							       OPT_ConditionOperand.EQUAL(),
							       new OPT_IntConstantOperand(0),
							       new OPT_IntConstantOperand(1)));
	// rD := rA + tempInt
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD,
							     reg_rA,
							     tempInt.copyRO()));

	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, reg_rD.copyRO(), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}
/**
 * The decoder for the subfme instruction
 */
final class subfme_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the addme instruction
 */
final class addme_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate addme (add to minus one extended):
     * <listing>
     * rD <- (rA) + XER[CA] - 1
     * </listing>
     * Also modifies XER[CA].
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
    	   
	if (VM.VerifyAssertions) VM._assert(rB == 0);
	// Get rA & rD
	OPT_RegisterOperand reg_rA = ppc2ir.getGPRegister(rA);
	OPT_RegisterOperand reg_rD = ppc2ir.getGPRegister(rD);
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);

	// tempInt := XER_CA ? 1 : 0
	ppc2ir.appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, tempInt,
							       ppc2ir.getXER_CA_Register(),
							       new OPT_IntConstantOperand(0),
							       OPT_ConditionOperand.EQUAL(),
							       new OPT_IntConstantOperand(0),
							       new OPT_IntConstantOperand(1)));
	// rD := rA + tempInt
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD,
							     reg_rA,
							     tempInt.copyRO()));
	// rD := rD + -1
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, reg_rD.copyRO(),
							     reg_rD.copyRO(),
							     new OPT_IntConstantOperand(0xffffffff)));	 
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, reg_rD.copyRO(), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the mullw instruction
 */
final class mullw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mullw (multiply low word):
     * <listing>
     * rD <- rA * rB
     * </listing>
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Perform multiply
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_MUL, ppc2ir.getGPRegister(rD),
							     ppc2ir.getGPRegister(rA),
							     ppc2ir.getGPRegister(rB)));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the add instruction
 */
final class add_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	int Rc = ps.currentInstruction & 0x1;
	ps.setRegister(rD, ps.getRegister(rA) + ps.getRegister(rB));
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rD), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate add
     * <listing>
     * rD <- rA + rB
     * </listing>
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Perform add
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, ppc2ir.getGPRegister(rD),
							     ppc2ir.getGPRegister(rA),
							     ppc2ir.getGPRegister(rB)));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the divwu instruction
 */
final class divwu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate divwu (divide word unsigned):
     * <listing>
     * dividend <- (rA)
     * divisor <- (rB)
     * rD <- dividend / divisor
     * </listing>
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Get rA & rB as longs
	OPT_RegisterOperand long_rA = ppc2ir.getTempLong(0);
	OPT_RegisterOperand long_rB = ppc2ir.getTempLong(1);
	OPT_RegisterOperand long_rD = ppc2ir.getTempLong(2);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, long_rA, ppc2ir.getGPRegister(rA)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, long_rB, ppc2ir.getGPRegister(rB)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, long_rA.copyRO(),
							     long_rA.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffL)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, long_rB.copyRO(),
							     long_rB.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffL)));
	// Perform divide
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_DIV, long_rD,
							     long_rA.copyRO(),
							     long_rB.copyRO()));
	// Set rD
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, ppc2ir.getGPRegister(rD),
							    long_rD.copyRO()));

	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the divw instruction
 */
final class divw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate divw (divide word)
     * <listing>
     * dividend <- (rA)
     * divisor <- (rB)
     * rD <- dividend / divisor
     * </listing>
     */
    protected int translateXO_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int OE, int secondaryOpcode, int Rc) {
	// Perform divide
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_DIV, ppc2ir.getGPRegister(rD),
							     ppc2ir.getGPRegister(rA),
							     ppc2ir.getGPRegister(rB)));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rD), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}

/**
 * The decoder for the subfco instruction
 */
final class subfco_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the addco instruction
 */
final class addco_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the subfo instruction
 */
final class subfo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the subfeo instruction
 */
final class subfeo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the addeo instruction
 */
final class addeo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the subfzeo instruction
 */
final class subfzeo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the addzeo instruction
 */
final class addzeo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the subfmeo instruction
 */
final class subfmeo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the addmeo instruction
 */
final class addmeo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mullwo instruction
 */
final class mullwo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the nego instruction
 */
final class nego_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the addo instruction
 */
final class addo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the divwuo instruction
 */
final class divwuo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the divwo instruction
 */
final class divwo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}

// -oO Start of XFL form instructions Oo-

/**
 * The decoder for the mtfsf instruction
 */
final class mtfsf_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int FM = bits(ps.currentInstruction,7,14);
	int frB = bits(ps.currentInstruction,16,20);
	int Rc = ps.currentInstruction & 0x1;

	// FM has one bit per field of the FPSCR (set to 1 if that field
	// is to be written to). Make FMask have 4 identical bits per
	// field, to correspond to the full 32 bits of the FPSCR.
	int FMask = 0;
	for(int f = 0 ; f < 8 ; f++)	{
	    // Extract bit number f from FM.
	    int FMf = ( FM & (0x80 >>> f) ) >>> (7 -f);
	    if(FMf == 1) {
		int partMask = (0xf0000000 >>> (4 * f));
		FMask |= partMask;
	    }
	}

	// First, get the bit pattern from frB
	long tempLong = Double.doubleToLongBits(ps.getFPregister(frB));
	// Extract the low order word
	int tempInt = (int)(tempLong & 0xFFFFFFFF);
	// This is a MTFS
	if (FMask == 0xffffffff){
	    ps.fpscr = tempInt;
	}
	else {
	    ps.fpscr = (tempInt & FMask) | (ps.fpscr & ~FMask);
	}
	// If we are setting field 0, bits 0 and 3 are copied from frB, but
	// bits 1 and 2 are set according to the usual rules (page 2-8 of
	// the programming environments manual)
	if( (FM & 0x80) == 1) {
	    throw new Error("Todo mtfsf 1!!!");
	    //setVX();
	    //setFEX();
	}

	// Check the condition register bit. */
	if(Rc != 0){
	    throw new Error("Todo mtfsf 2!!!");
	    //setCR1(ppc2ir.getFPRegister(frB));
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Update the VX bit of the FPSCR (bit 2).
     */
    private void setVX(PPC2IR ppc2ir) {            
	// Clear the VX bit then test if we need to set it
	OPT_RegisterOperand FPSCR = ppc2ir.getFPSCRRegister();
	// FPSCR = FPSCR & 0b 1101 1111 1111 1111
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, FPSCR.copyRO(),
							     FPSCR,
							     new OPT_IntConstantOperand(0xdfffffff)));
	OPT_BasicBlock testVX = ppc2ir.getCurrentBlock();
	OPT_BasicBlock setVX = ppc2ir.createBlockAfterCurrent();
	OPT_BasicBlock end = ppc2ir.createBlockAfter(setVX);

	// Test whether to set VX bit
	{
	    // If any one (or more) of a set of bits (VXxxx) is non-zero,
	    // the VX bit is set.
	    OPT_RegisterOperand vxBits = ppc2ir.getTempInt(1);
	    testVX.appendInstruction(Binary.create(INT_AND, vxBits, FPSCR.copyRO(),
						   new OPT_IntConstantOperand(0x1f80700)));
        
	    OPT_RegisterOperand guardResult = null;
	    testVX.appendInstruction(IfCmp.create(INT_IFCMP, guardResult, vxBits.copyRO(),
						  new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(),
						  end.makeJumpTarget(),
						  OPT_BranchProfileOperand.likely()));
	    testVX.insertOut(end);
	}
	// Set the VX bit
	{
	    setVX.appendInstruction(Binary.create(INT_OR, FPSCR.copyRO(),
						  FPSCR.copyRO(),
						  new OPT_IntConstantOperand(0x20000000)));
	}
	// Block entered from all paths through setting the VX bit      
	ppc2ir.setCurrentBlock(end);
    }
    /**
     * Update the FEX bit of the FPSCR (bit 1).
     */
    protected void setFEX(PPC2IR ppc2ir) {
	OPT_RegisterOperand guardResult;
	OPT_BasicBlock currentBlock = ppc2ir.getCurrentBlock();
	// Clear the FEX bit then test if we need to set it
	OPT_RegisterOperand FPSCR = ppc2ir.getFPSCRRegister();
	// FPSCR = FPSCR & 0b 1011 1111 1111 1111
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, FPSCR.copyRO(),
							     FPSCR,
							     new OPT_IntConstantOperand(0xbfffffff)));     

	// We need to make several tests of the bits of the FPSCR, so
	// make some blocks to do this.
	OPT_BasicBlock testVE_OE_UE_ZE_XE = ppc2ir.getCurrentBlock();
	OPT_BasicBlock testV = ppc2ir.createBlockAfter(testVE_OE_UE_ZE_XE);
	OPT_BasicBlock testO = ppc2ir.createBlockAfter(testV);
	OPT_BasicBlock testU = ppc2ir.createBlockAfter(testO);
	OPT_BasicBlock testZ = ppc2ir.createBlockAfter(testU);
	OPT_BasicBlock testX = ppc2ir.createBlockAfter(testZ);
	// The block to set the FEX bit
	OPT_BasicBlock setFEX = ppc2ir.createBlockAfter(testX);
	// The block after everything is done
	OPT_BasicBlock end = ppc2ir.createBlockAfter(setFEX);

	// Make a block to set the FEX bit
	{
	    // FPSCR = FPSCR | 0b 0100 0000 0000 0000
	    setFEX.appendInstruction(Binary.create(INT_OR, FPSCR.copyRO(),
						   FPSCR.copyRO(),
						   new OPT_IntConstantOperand(0x40000000)));
	}

	// Make a test to see if any exception bits are enabled, if not goto end
	{
	    OPT_RegisterOperand VE_OE_UE_ZE_XE = ppc2ir.getTempInt(1);
	    testVE_OE_UE_ZE_XE.appendInstruction(Binary.create(INT_AND, VE_OE_UE_ZE_XE,
							       FPSCR.copyRO(),
							       new OPT_IntConstantOperand(0xf8)));
	    testVE_OE_UE_ZE_XE.appendInstruction(IfCmp.create(INT_IFCMP, null,
							      VE_OE_UE_ZE_XE.copyRO(),
							      new OPT_IntConstantOperand(0),
							      OPT_ConditionOperand.EQUAL(),
							      end.makeJumpTarget(),
							      OPT_BranchProfileOperand.likely()));
	    testVE_OE_UE_ZE_XE.insertOut(end);
        
	}
      
	/* First we test (VX & VE), i.e. (bit 2 & bit bit 24). */
	{
	    OPT_RegisterOperand tempVX = ppc2ir.getTempInt(1);
	    OPT_RegisterOperand tempVE = ppc2ir.getTempInt(2);
	    OPT_RegisterOperand VX = ppc2ir.getTempInt(3);
	    OPT_RegisterOperand VE = ppc2ir.getTempInt(4);
	    OPT_RegisterOperand andV = ppc2ir.getTempInt(5);
	    // Extract VX bit.
	    testV.appendInstruction(Binary.create(INT_AND,  tempVX, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x20000000)));
	    // Shift to the last bit position.
	    testV.appendInstruction(Binary.create(INT_USHR, VX, tempVX.copyRO(),
						  new OPT_IntConstantOperand(29)));
	    // Extract VE bit.
	    testV.appendInstruction(Binary.create(INT_AND, tempVE, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x80)));
	    // Shift to the last bit position.
	    testV.appendInstruction(Binary.create(INT_USHR, VE, tempVE.copyRO(),
						  new OPT_IntConstantOperand(7)));
	    // AND them together.
	    testV.appendInstruction(Binary.create(INT_AND, andV, VX.copyRO(),
						  VE.copyRO()));
	    // If the result is 1, jump to set the FEX bit.
	    guardResult = null;
	    testV.appendInstruction(IfCmp.create(INT_IFCMP, guardResult, andV,
						 new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(),
						 setFEX.makeJumpTarget(),
						 OPT_BranchProfileOperand.unlikely()));
	    // Add out edge to the jump block. 
	    testV.insertOut(setFEX);
	}

	/* Now test (OX & OE), i.e. (bit 3 & bit 25). */
	{
	    OPT_RegisterOperand tempOX = ppc2ir.getTempInt(1);
	    OPT_RegisterOperand tempOE = ppc2ir.getTempInt(2);
	    OPT_RegisterOperand OX = ppc2ir.getTempInt(3);
	    OPT_RegisterOperand OE = ppc2ir.getTempInt(4);
	    OPT_RegisterOperand andO = ppc2ir.getTempInt(5);
	    // Extract OX bit.
	    testO.appendInstruction(Binary.create(INT_AND,  tempOX, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x10000000)));
	    // Shift to the last bit position.
	    testO.appendInstruction(Binary.create(INT_USHR, OX, tempOX.copyRO(),
						  new OPT_IntConstantOperand(28)));
	    // Extract OE bit.
	    testO.appendInstruction(Binary.create(INT_AND, tempOE, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x40)));
	    // Shift to the last bit position.
	    testO.appendInstruction(Binary.create(INT_USHR, OE, tempOE.copyRO(),
						  new OPT_IntConstantOperand(6)));
	    // AND them together.
	    testO.appendInstruction(Binary.create(INT_AND, andO, OX.copyRO(),
						  OE.copyRO()));
	    // If the result is 1, jump to set the FEX bit.
	    guardResult = null;
	    testO.appendInstruction(IfCmp.create(INT_IFCMP, guardResult, andO,
						 new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(),
						 setFEX.makeJumpTarget(),
						 OPT_BranchProfileOperand.unlikely()));
	    // Add out edge to the jump block. 
	    testO.insertOut(setFEX);
	}
      
	/* Now test (UX & UE), i.e. (bit 4 & bit 26). */
	{
	    OPT_RegisterOperand tempUX = ppc2ir.getTempInt(1);
	    OPT_RegisterOperand tempUE = ppc2ir.getTempInt(2);
	    OPT_RegisterOperand UX = ppc2ir.getTempInt(3);
	    OPT_RegisterOperand UE = ppc2ir.getTempInt(4);
	    OPT_RegisterOperand andU = ppc2ir.getTempInt(5);
	    // Extract UX bit.
	    testU.appendInstruction(Binary.create(INT_AND,  tempUX, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x8000000)));
	    // Shift to the last bit position.
	    testU.appendInstruction(Binary.create(INT_USHR, UX, tempUX.copyRO(),
						  new OPT_IntConstantOperand(27)));
	    // Extract UE bit.
	    testU.appendInstruction(Binary.create(INT_AND, tempUE, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x20)));
	    // Shift to the last bit position.
	    testU.appendInstruction(Binary.create(INT_USHR, UE, tempUE.copyRO(),
						  new OPT_IntConstantOperand(5)));
	    // AND them together.
	    testU.appendInstruction(Binary.create(INT_AND, andU, UX.copyRO(),
						  UE.copyRO()));
	    // If the result is 1, jump to set the FEX bit.
	    guardResult = null;
	    testU.appendInstruction(IfCmp.create(INT_IFCMP, guardResult, andU,
						 new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(),
						 setFEX.makeJumpTarget(),
						 OPT_BranchProfileOperand.unlikely()));
	    // Add out edge to the jump block. 
	    testU.insertOut(setFEX);
	}
      
	/* Now test (ZX & ZE), i.e. (bit 5 & bit 27). */
	{
	    OPT_RegisterOperand tempZX = ppc2ir.getTempInt(1);
	    OPT_RegisterOperand tempZE = ppc2ir.getTempInt(2);
	    OPT_RegisterOperand ZX = ppc2ir.getTempInt(3);
	    OPT_RegisterOperand ZE = ppc2ir.getTempInt(4);
	    OPT_RegisterOperand andZ = ppc2ir.getTempInt(5);
	    // Extract ZX bit.
	    testZ.appendInstruction(Binary.create(INT_AND,  tempZX, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x4000000)));
	    // Shift to the last bit position.
	    testZ.appendInstruction(Binary.create(INT_USHR, ZX, tempZX.copyRO(),
						  new OPT_IntConstantOperand(26)));
	    // Extract ZE bit.
	    testZ.appendInstruction(Binary.create(INT_AND, tempZE, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x10)));
	    // Shift to the last bit position.
	    testZ.appendInstruction(Binary.create(INT_USHR, ZE, tempZE.copyRO(),
						  new OPT_IntConstantOperand(4)));
	    // AND them together.
	    testZ.appendInstruction(Binary.create(INT_AND, andZ, ZX.copyRO(),
						  ZE.copyRO()));
	    // If the result is 1, jump to set the FEX bit.
	    guardResult = null;
	    testZ.appendInstruction(IfCmp.create(INT_IFCMP, guardResult, andZ,
						 new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(),
						 setFEX.makeJumpTarget(),
						 OPT_BranchProfileOperand.unlikely()));
	    // Add out edge to the jump block. 
	    currentBlock.insertOut(setFEX);
	}

	/* Now test (XX & XE), i.e. (bit 6 & bit 28). */
	{
	    OPT_RegisterOperand tempXX = ppc2ir.getTempInt(1);
	    OPT_RegisterOperand tempXE = ppc2ir.getTempInt(2);
	    OPT_RegisterOperand XX = ppc2ir.getTempInt(3);
	    OPT_RegisterOperand XE = ppc2ir.getTempInt(4);
	    OPT_RegisterOperand andX = ppc2ir.getTempInt(5);
	    // Extract XX bit.
	    testX.appendInstruction(Binary.create(INT_AND,  tempXX, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x2000000)));
	    // Shift to the last bit position.
	    testX.appendInstruction(Binary.create(INT_USHR, XX, tempXX.copyRO(),
						  new OPT_IntConstantOperand(25)));
	    // Extract XE bit.
	    testX.appendInstruction(Binary.create(INT_AND, tempXE, ppc2ir.getFPSCRRegister(),
						  new OPT_IntConstantOperand(0x8)));
	    // Shift to the last bit position.
	    testX.appendInstruction(Binary.create(INT_USHR, XE, tempXE.copyRO(),
						  new OPT_IntConstantOperand(3)));
	    // AND them together.
	    testX.appendInstruction(Binary.create(INT_AND, andX, XX.copyRO(),
						  XE.copyRO()));
	    // If the result is not 1, jump to avoid setting the FEX bit.
	    guardResult = null;
	    testX.appendInstruction(IfCmp.create(INT_IFCMP, guardResult, andX,
						 new OPT_IntConstantOperand(1), OPT_ConditionOperand.NOT_EQUAL(),
						 end.makeJumpTarget(),
						 OPT_BranchProfileOperand.likely()));
	    // Add out edge to the end block
	    testX.insertOut(end);
	}
	// Block entered from all paths through setting the FEX bit
	ppc2ir.setCurrentBlock(end);
    }

    /**
     * Translate mtfsf (move to fspscr fields)
     */
    protected int translateXFL_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int zero, int FM, int zero2, int frB, int secondaryOpcode, int Rc) {

	// FM has one bit per field of the FPSCR (set to 1 if that field
	// is to be written to). Make FMask have 4 identical bits per
	// field, to correspond to the full 32 bits of the FPSCR.
	int FMask = 0;
	for(int f = 0 ; f < 8 ; f++)	{
	    // Extract bit number f from FM.
	    int FMf = ( FM & (0x80 >>> f) ) >>> (7 -f);
	    if(FMf == 1) {
		int partMask = (0xf0000000 >>> (4 * f));
		FMask |= partMask;
	    }
	}

	// First, get the bit pattern from frB
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_AS_LONG_BITS, tempLong,
							    ppc2ir.getFPRegister(frB)));
	 
	// Extract the low order word
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, tempLong.copyRO(),
							     tempLong.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffL)));

	// Copy to an int
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, tempInt,
							    tempLong.copyRO()));

	// This is a MTFS
	if (FMask == 0xffffffff){
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getFPSCRRegister(),
							       tempInt.copyRO()));
	}
	else {
	    // Calculate tempInt = tempInt & mask
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt.copyRO(),
								 tempInt.copyRO(),
								 new OPT_IntConstantOperand(FMask)));
	    // Calculate FPSCR & NOT mask
	    OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(1);
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt2,
								 ppc2ir.getFPSCRRegister(),
								 new OPT_IntConstantOperand(~FMask)));
		
	    // Calculate ((tempInt) & mask) | (FPSCR & NOT mask), and put into FPSCR
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, ppc2ir.getFPSCRRegister(),
								 tempInt.copyRO(),
								 tempInt2.copyRO()));
	}

	// If we are setting field 0, bits 0 and 3 are copied from frB, but
	// bits 1 and 2 are set according to the usual rules (page 2-8 of
	// the programming environments manual)
	if( (FM & 0x80) == 1) {
	    throw new Error("Todo mtfsf 1!!!");
	    //setVX();
	    //setFEX();
	}

	// Check the condition register bit. */
	if(Rc != 0){
	    throw new Error("Todo mtfsf 2!!!");
	    //setCR1(ppc2ir.getFPRegister(frB));
	}
	return pc+4;
    }
}

// -oO Start of XFX form instructions Oo-

/**
 * The decoder for the mtcrf instruction
 */
final class mtcrf_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int CRM = bits(ps.currentInstruction,11,20) >> 1;

	if(CRM == 0xff) { // mtcr rS
	    ps.setCR(ps.getRegister(rS));
	}
	else {
	    // Values to set in CR register
	    int val = ps.getRegister(rS);

	    // CRM has one bit per field of the CR (set to 1 if that field
	    // is to be written to). Make CRMask have 4 identical bits per
	    // field, to correspond to the full 32 bits of the CR.
	    for(int f=0 ; f < 8 ; f++) {
		if(((CRM >>> (7 - f)) & 0x1) != 0) {
		    int part_mask = (0xf0000000 >>> (4 * f));
		    int crf_val = val >>> (4 * f);
		    ps.crf_lt[f] = (crf_val & 0x1) != 0;
		    ps.crf_gt[f] = (crf_val & 0x2) != 0;
		    ps.crf_eq[f] = (crf_val & 0x4) != 0;
		    ps.crf_so[f] = (crf_val & 0x8) != 0;
		}
	    }
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mtcrf (move to condition register fields)
     * <listing>
     * mask <- (4)(CRM[0]) || (4)(CRM[1]) ||... (4)(CRM[7])
     * CR <- (rS & mask) | (CR & ¬mask)
     * </listing>
     */
    protected int translateXFX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int CRM0, int secondaryOpcode, int zero) {
	int CRM = CRM0 >> 1;

	throw new Error("TODO!");
	/*
	  if(CRM == 0xff) { // mtcr rS
	  ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
	  ppc2ir.getCRRegister(),
	  ppc2ir.getGPRegister(rS)));
	  for(int f=0; f < 8; f++) {
	  lazy.setConditionFieldLaziness(f, PPC_Laziness.NOT_LAZY);
	  }
	  }
	  else {
	  int CRMask = 0; // CR mask (1 bit per bit of the CR)

	  // CRM has one bit per field of the CR (set to 1 if that field
	  // is to be written to). Make CRMask have 4 identical bits per
	  // field, to correspond to the full 32 bits of the CR.
	  for(int f=0 ; f < 8 ; f++) {
	  if(((CRM >>> (7 - f)) & 0x1) != 0) {
	  CRMask |= (0xf0000000 >>> (4 * f));
	  lazy.setConditionFieldLaziness(f, PPC_Laziness.NOT_LAZY);
	  }
	  }
	  // Set bits in CR register
	  OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);
	  OPT_RegisterOperand cr = ppc2ir.getCRRegister();

	  ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, cr,
	  cr.copyRO(),
	  new OPT_IntConstantOperand(~CRMask)));
	  ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt,
	  ppc2ir.getGPRegister(rS),
	  new OPT_IntConstantOperand(CRMask)));
	  ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, cr.copyRO(),
	  tempInt.copyRO(),
	  cr.copyRO()));
	  }
	  return pc + 4;
	*/
    }
}
/**
 * The decoder for the mfspr instruction
 */
final class mfspr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int spr = bits(ps.currentInstruction,11,20);
	spr = ((spr & 0x1f) << 5) | (spr >> 5);
	switch(spr) {
	case 1: // XER
	    throw new Error("mfxer unimplemented");
	case 8: // LR
	    ps.setRegister(rS, ps.lr);
	    break;
	case 9: // CTR
	    ps.setRegister(rS, ps.ctr);
	    break;
	default:
	    throw new Error("Unknown spr "+spr);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mfspr (move from special purpose register)
     * <listing>
     * n <- spr[5-9] || spr[0-4]
     * rS <- SPR(n)
     * </listing>
     */
    protected int translateXFX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int spr, int secondaryOpcode, int zero) {
	spr = ((spr & 0x1f) << 5) | (spr >> 5);

	switch(spr) {
	case 1: // XER
	    {
		OPT_RegisterOperand XER = ppc2ir.getTempInt(0);
		OPT_RegisterOperand XER_SO_Set = ppc2ir.getTempInt(1);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, XER_SO_Set,
								     ppc2ir.getXER_ByteCountRegister(),
								     new OPT_IntConstantOperand(1 << 31)));
		ppc2ir.appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, XER,
								       ppc2ir.getXER_SO_Register(),
								       new OPT_IntConstantOperand(0),
								       OPT_ConditionOperand.EQUAL(),
								       ppc2ir.getXER_ByteCountRegister(),
								       XER_SO_Set.copyRO()));
		OPT_RegisterOperand XER_OV_Set = ppc2ir.getTempInt(1);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, XER_OV_Set,
								     XER.copyRO(),
								     new OPT_IntConstantOperand(1 << 30)));
		ppc2ir.appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, XER.copyRO(),
								       ppc2ir.getXER_OV_Register(),
								       new OPT_IntConstantOperand(0),
								       OPT_ConditionOperand.EQUAL(),
								       XER.copyRO(),
								       XER_OV_Set.copyRO()));
		OPT_RegisterOperand XER_CA_Set = ppc2ir.getTempInt(1);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, XER_OV_Set,
								     XER.copyRO(),
								     new OPT_IntConstantOperand(1 << 29)));
		ppc2ir.appendInstructionToCurrentBlock(CondMove.create(INT_COND_MOVE, XER.copyRO(),
								       ppc2ir.getXER_CA_Register(),
								       new OPT_IntConstantOperand(0),
								       OPT_ConditionOperand.EQUAL(),
								       XER.copyRO(),
								       XER_CA_Set.copyRO()));

		ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
								   ppc2ir.getGPRegister(rS),
								   XER.copyRO()));
	    }
	    break;
	case 8: // LR
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       ppc2ir.getGPRegister(rS),
							       ppc2ir.getLRRegister()));
	    break;
	case 9: // CTR
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       ppc2ir.getGPRegister(rS),
							       ppc2ir.getCTRRegister()));
	    break;
	default:
	    throw new Error("Unknown spr "+spr);
	}
	return pc+4;
    }
}
/**
 * The decoder for the mtspr instruction
 */
final class mtspr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int spr = bits(ps.currentInstruction,11,20);
	spr = ((spr & 0x1f) << 5) | (spr >> 5);
	switch(spr) {
	case 1: // XER
	    throw new Error("mtxer unimplemented");
	case 8: // LR
	    ps.lr = ps.getRegister(rS);
	    break;
	case 9: // CTR
	    ps.ctr = ps.getRegister(rS);
	    break;
	default:
	    throw new Error("Unknown spr "+spr);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mtspr (move to special purpose register)
     * <listing>
     * n <- spr[5-9] || spr[0-4]
     * SPR(n) <- rS
     * </listing>
     */
    protected int translateXFX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int spr, int secondaryOpcode, int zero) {
	spr = ((spr & 0x1f) << 5) | (spr >> 5);

	switch(spr) {
	case 1: // XER
	    {
		OPT_RegisterOperand rS_RegOp = ppc2ir.getGPRegister(rS);
		// Copy SO bit into XER[SO] register
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, ppc2ir.getXER_SO_Register(),
								     rS_RegOp,
								     new OPT_IntConstantOperand(1 << 31)));
		// Copy OV bit into XER[OV] register
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, ppc2ir.getXER_OV_Register(),
								     rS_RegOp.copyRO(),
								     new OPT_IntConstantOperand(1 << 30)));
		// Copy byte count bits out
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, ppc2ir.getXER_ByteCountRegister(),
								     rS_RegOp.copyRO(),
								     new OPT_IntConstantOperand(0x3f)));
		// Copy CA bit into XER[CA] register
		OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt,
								     rS_RegOp.copyRO(),
								     new OPT_IntConstantOperand(1 << 29)));
		ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, ppc2ir.getXER_CA_Register(),
									 tempInt.copyRO(),
									 new OPT_IntConstantOperand(0),
									 OPT_ConditionOperand.NOT_EQUAL(),
									 OPT_BranchProfileOperand.unlikely()));
	    }
	    break;
	case 8: // LR
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       ppc2ir.getLRRegister(),
							       ppc2ir.getGPRegister(rS)));
	    break;
	case 9: // CTR
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       ppc2ir.getCTRRegister(),
							       ppc2ir.getGPRegister(rS)));
	    break;
	default:
	    throw new Error("Unknown spr "+spr);
	}
	return pc+4;
    }
}

// -oO Start of X form instructions Oo-

/**
 * The decoder for the cmp instruction
 */
final class cmp_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int crfD = bits(ps.currentInstruction,6,10) >>> 2;
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	ps.setCRfield_signedCompare(crfD, ps.getRegister(rA), ps.getRegister(rB));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate cmp (compare):
     * <listing>
     * if L=0 then a <- EXTS(rA)
     *             b <- EXTS(rB)
     * else        a <- rA
     *             b <- rB
     * if a < b
     *    then c <- 0b100
     *    else if a > EXTS(SIMM)
     *            then c <- 0b010
     *            else c <- 0b001
     * CR[(4 * crfD)-(4 * crfD + 3)] <- c || XER[SO]
     * </listing>
     * The L bit has no effect on 32-it operations.
     * Other simplified mnemonics: cmpw
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crfD0L, int rA, int rB, int zero, int zero2) {
	if (VM.VerifyAssertions) VM._assert((zero == zero2) && (zero2 == 0));
	int crfD = crfD0L >>> 2;

	setCRfield(ppc2ir, lazy, crfD, ppc2ir.getGPRegister(rA), ppc2ir.getGPRegister(rB), SIGNED_INT_CMP);

	return pc+4;
    }
}
/**
 * The decoder for the tw instruction
 */
final class tw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate tw (trap word)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crfD0L, int rA, int rB, int zero, int zero2) {
	// @TODO
	ppc2ir.plantThrowBadInstruction((PPC_Laziness)lazy.clone(), pc);
	return -1;
    }
}
/**
 * The decoder for the lvsl instruction
 */
final class lvsl_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lvebx instruction
 */
final class lvebx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mulhdu instruction
 */
final class mulhdu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mfcr instruction
 */
final class mfcr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rD = bits(ps.currentInstruction,6,10);
	ps.setRegister(rD, ps.getCR());
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mfcr (move from condition register):
     * <listing>
     * rD <- CR
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int zero1, int zero2, int secodaryOpcode, int zero3) {
	if (VM.VerifyAssertions) VM._assert((zero1 == zero2) && (zero2 == zero3) && (zero3 == 0));

	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rD),
							   ppc2ir.getCRRegister()));
	return pc+4;
    }
}
/**
 * The decoder for the lwarx instruction
 */
final class lwarx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lwarx (load word and reserve indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b + (rB)
     * RESERVE <- 1
     * RESERVE_ADDR <- physical_addr(EA)
     * rD <- MEM(EA, 4)
     * </listing>
     * See also: stwcx
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Load value from memory at EA into rD.
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), ppc2ir.getGPRegister(rD));
	// Ignore the reserve part of the instruction for the time being
	return pc+4;
    }
}
/**
 * The decoder for the ldx instruction
 */
final class ldx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lwzx instruction
 */
final class lwzx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lwzx (load word and zero indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b + (rB)
     * rD <- MEM(EA, 4)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Load value from memory at EA into rD.
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), ppc2ir.getGPRegister(rD));
	return pc+4;
    }
}

/**
 * The decoder for the slw instruction
 */
final class slw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate slw (shift left word)
     * <listing>
     * n <- rB[27-31]
     * r <- ROTL(rS,n)
     * </listing>
     * Equivalent to:
     * <listing>
     * rD <- rA << rB
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, ppc2ir.getGPRegister(rA),
							     ppc2ir.getGPRegister(rS),
							     ppc2ir.getGPRegister(rB)));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}

/**
 * The decoder for the cntlzw instruction
 */
final class cntlzw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int Rc = ps.currentInstruction & 0x1;

	int x = ps.getRegister(rA);
	int y,m,n;
	y = -(x >> 16);      // If left half of x is 0,
	m = (y >> 16) & 16;  // set n = 16.  If left half
	n = 16 - m;          // is nonzero, set n = 0 and
	x = x >> m;          // shift x right 16.
	// Now x is of the form 0000xxxx.
	y = x - 0x100;       // If positions 8-15 are 0,
	m = (y >> 16) & 8;   // add 8 to n and shift x left 8.
	n = n + m;
	x = x << m;
	 
	y = x - 0x1000;      // If positions 12-15 are 0,
	m = (y >> 16) & 4;   // add 4 to n and shift x left 4.
	n = n + m;
	x = x << m;
	 
	y = x - 0x4000;      // If positions 14-15 are 0,
	m = (y >> 16) & 2;   // add 2 to n and shift x left 2.
	n = n + m;
	x = x << m;
	 
	y = x >> 14;         // Set y = 0, 1, 2, or 3.
	m = y & ~(y >> 1);   // Set m = 0, 1, 2, or 2 resp.
	ps.setRegister(rS, n + 2 - m);
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate cntlzw (count leading zeros word)
     * <listing>
     * n <- 0
     * do while n < 32
     *   if rS[n] = then leave
     *   n <- n + 1
     * rA <- n
     * </listing>
     *
     * There are a number of count leading zero algorithms (a good list
     * is at {@url http://www.hackersdelight.org/HDcode/nlz.cc}). This
     * routine delegates to the routine it conceives to be more optimal
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int zero, int secondaryOpcode, int Rc) {
	nlz_count_no_branches(ppc2ir, rS, rA);
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc + 4;
    }
    /**
     * Perform a count of the number of leading zeros with no branch operations:
     * <listing>
     * int nlz4(unsigned x) {
     *   int y, m, n;
     *
     *   y = -(x >> 16);      // If left half of x is 0,
     *   m = (y >> 16) & 16;  // set n = 16.  If left half
     *   n = 16 - m;          // is nonzero, set n = 0 and
     *   x = x >> m;          // shift x right 16.
     *                        // Now x is of the form 0000xxxx.
     *   y = x - 0x100;       // If positions 8-15 are 0,
     *   m = (y >> 16) & 8;   // add 8 to n and shift x left 8.
     *   n = n + m;
     *   x = x << m;
     *
     *   y = x - 0x1000;      // If positions 12-15 are 0,
     *   m = (y >> 16) & 4;   // add 4 to n and shift x left 4.
     *   n = n + m;
     *   x = x << m;

     *   y = x - 0x4000;      // If positions 14-15 are 0,
     *   m = (y >> 16) & 2;   // add 2 to n and shift x left 2.
     *   n = n + m;
     *   x = x << m;
     *
     *   y = x >> 14;         // Set y = 0, 1, 2, or 3.
     *   m = y & ~(y >> 1);   // Set m = 0, 1, 2, or 2 resp.
     *   return n + 2 - m;
     * }
     * </listing>
     */
    private static void nlz_count_no_branches(PPC2IR ppc2ir, int rS, int rA) {
	OPT_RegisterOperand x = ppc2ir.getTempInt(0);
	OPT_RegisterOperand y = ppc2ir.getTempInt(1);
	OPT_RegisterOperand m = ppc2ir.getTempInt(2);
	OPT_RegisterOperand n = ppc2ir.getTempInt(3);

	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, x,
							   ppc2ir.getGPRegister(rS)));

	// y = -(x >> 16);      // If left half of x is 0,
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, y,
							     x.copyRO(),
							     new OPT_IntConstantOperand(16)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NEG, y.copyRO(),
							    y.copyRO()));
	// m = (y >> 16) & 16;  // set n = 16.  If left half
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, m,
							     y.copyRO(),
							     new OPT_IntConstantOperand(16)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, m.copyRO(),
							     m.copyRO(),
							     new OPT_IntConstantOperand(16)));
	// n = 16 - m;          // is nonzero, set n = 0 and
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, n,
							     new OPT_IntConstantOperand(16),
							     m.copyRO()));
	// x = x >> m;          // shift x right 16.
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, x.copyRO(),
							     x.copyRO(),
							     m.copyRO()));
	//                      // Now x is of the form 0000xxxx.
	// y = x - 0x100;       // If positions 8-15 are 0,
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, y.copyRO(),
							     x.copyRO(),
							     new OPT_IntConstantOperand(0x100)));
	//  m = (y >> 16) & 8;   // add 8 to n and shift x left 8.
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, m.copyRO(),
							     y.copyRO(),
							     new OPT_IntConstantOperand(16)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, m.copyRO(),
							     m.copyRO(),
							     new OPT_IntConstantOperand(8)));

	// n = n + m;
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, n.copyRO(),
							     n.copyRO(),
							     m.copyRO()));
	// x = x << m;
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, x.copyRO(),
							     x.copyRO(),
							     m.copyRO()));
	// y = x - 0x1000;      // If positions 12-15 are 0,
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, y.copyRO(),
							     x.copyRO(),
							     new OPT_IntConstantOperand(0x1000)));
	// m = (y >> 16) & 4;   // add 4 to n and shift x left 4.
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, m.copyRO(),
							     y.copyRO(),
							     new OPT_IntConstantOperand(16)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, m.copyRO(),
							     m.copyRO(),
							     new OPT_IntConstantOperand(4)));
	// n = n + m;
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, n.copyRO(),
							     n.copyRO(),
							     m.copyRO()));
	// x = x << m;
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, x.copyRO(),
							     x.copyRO(),
							     m.copyRO()));
	// y = x - 0x4000;      // If positions 14-15 are 0,
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, y.copyRO(),
							     x.copyRO(),
							     new OPT_IntConstantOperand(0x4000)));
	// m = (y >> 16) & 2;   // add 2 to n and shift x left 2.
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, m.copyRO(),
							     y.copyRO(),
							     new OPT_IntConstantOperand(16)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, m.copyRO(),
							     m.copyRO(),
							     new OPT_IntConstantOperand(2)));
	// n = n + m;
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, n.copyRO(),
							     n.copyRO(),
							     m.copyRO()));
	// x = x << m;
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, x.copyRO(),
							     x.copyRO(),
							     m.copyRO()));
	// y = x >> 14;         // Set y = 0, 1, 2, or 3.
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, y.copyRO(),
							     x.copyRO(),
							     new OPT_IntConstantOperand(14)));
	// m = y & ~(y >> 1);   // Set m = 0, 1, 2, or 2 resp.
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, m.copyRO(),
							     y.copyRO(),
							     new OPT_IntConstantOperand(1)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, m.copyRO(),
							    m.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, m.copyRO(),
							     m.copyRO(),
							     y.copyRO()));
	// return n + 2 - m;
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, n.copyRO(),
							     n.copyRO(),
							     new OPT_IntConstantOperand(2)));    
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, ppc2ir.getGPRegister(rA),
							     n.copyRO(),
							     m.copyRO()));    
    }
    /**
     * Perform leading zero count using a loop
     */
    private static void nlz_count_loop(PPC2IR ppc2ir, int rS, int rA) {
	throw new Error("Todo");
    }
}
/**
 * The decoder for the sld instruction
 */
final class sld_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the and instruction
 */
final class and_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	int Rc = ps.currentInstruction & 0x1;
	ps.setRegister(rA, ps.getRegister(rS) & ps.getRegister(rB));
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate and
     * <listing>
     * rA <- (rS) & (rB)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	// Perform and
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, ppc2ir.getGPRegister(rA),
							     ppc2ir.getGPRegister(rS),
							     ppc2ir.getGPRegister(rB)));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}

	return pc+4;
    }
}
/**
 * The decoder for the cmpl instruction
 */
final class cmpl_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int crfD = bits(ps.currentInstruction,6,10) >>> 2;
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	ps.setCRfield_unsignedCompare(crfD, ps.getRegister(rA), ps.getRegister(rB));
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate cmpl (compare logical):
     * <listing>
     * a <- (rA)
     * b <- (rB)
     * if a <U b
     *    then c <- 0b100
     *    else if a >U ((16)0 || UIMM)
     *            then c <- 0b010
     *            else c <- 0b001
     * CR[(4 * crfD)-(4 * crfD + 3)] <- c || XER[SO]
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crfD0L, int rA, int rB, int secondaryOpcode, int Rc) {
	int crfD = crfD0L >>> 2;

	setCRfield(ppc2ir, lazy, crfD, ppc2ir.getGPRegister(rA), ppc2ir.getGPRegister(rB), UNSIGNED_INT_CMP);

	return pc+4;
    }
}
/**
 * The decoder for the lvsr instruction
 */
final class lvsr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lvehx instruction
 */
final class lvehx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the ldux instruction
 */
final class ldux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the dcbst instruction
 */
final class dcbst_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate dcbst (data cache block store)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int zero, int rA, int rB, int secondaryOpcode, int zero2) {
	// translated into a NOP
	return pc+4;
    }
}
/**
 * The decoder for the lwzux instruction
 */
final class lwzux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the andc instruction
 */
final class andc_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	int Rc = ps.currentInstruction & 0x1;
	ps.setRegister(rA, ps.getRegister(rS) & ~ps.getRegister(rB));
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate andc (and with complement)
     * <listing>
     * rA <- (rS) & ¬(rB)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);
	// Perform complement
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, tempInt,
							    ppc2ir.getGPRegister(rB)));

	// Perform and
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, ppc2ir.getGPRegister(rA),
							     tempInt.copyRO(),
							     ppc2ir.getGPRegister(rS)));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}
/**
 * The decoder for the td instruction
 */
final class td_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mulhd instruction
 */
final class mulhd_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mfmsr instruction
 */
final class mfmsr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the ldarx instruction
 */
final class ldarx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the dcbf instruction
 */
final class dcbf_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lbzx instruction
 */
final class lbzx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lbz (load byte and zero indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b + (rB)
     * rD <- (24)0 || MEM(EA, 1)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), result);
	// Turn 32bit address into appropriate shift using last 2 bits
	// of the address
	//-#if RVM_FOR_POWERPC
	// EA = (EA & 0x3) * 8
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	//-#else
	// EA = (3 - (EA & 0x3)) * 8
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, EA.copyRO(),
							     new OPT_IntConstantOperand(3),
							     EA.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	//-#endif
	// rD >>>= EA
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, result.copyRO(),
							     result.copyRO(),
							     EA.copyRO()));
	// rD &= 0xff
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, result.copyRO(),
							     result.copyRO(),
							     new OPT_IntConstantOperand(0xff)));
	return pc+4;
    }
}
/**
 * The decoder for the lbzux instruction
 */
final class lbzux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lbzux (load byte and zero with update indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b + (rB)
     * rD <- (24)0 || MEM(EA, 1)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if ((rA == 0)||(rA == rD)) {
	    throw new Error("Invalid form of lbzux at 0x" + Integer.toHexString(pc));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), result);
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	// Turn 32bit address into appropriate shift using last 2 bits
	// of the address
	//-#if RVM_FOR_POWERPC
	// EA = (EA & 0x3) * 8
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	//-#else
	// EA = (3 - (EA & 0x3)) * 8
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, EA.copyRO(),
							     new OPT_IntConstantOperand(3),
							     EA.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	//-#endif
	// rD >>>= EA
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, result.copyRO(),
							     result.copyRO(),
							     EA.copyRO()));
	// rD &= 0xff
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, result.copyRO(),
							     result.copyRO(),
							     new OPT_IntConstantOperand(0xff)));
	return pc+4;
    }
}
/**
 * The decoder for the nor instruction
 */
final class nor_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	int Rc = ps.currentInstruction & 0x1;
	ps.setRegister(rA, ~(ps.getRegister(rS) | ps.getRegister(rB)));
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate nor
     * <listing>
     * rA <- ¬((rS) | (rB))
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);
	// Perform or
	if (rS == rB) {
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       tempInt,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, tempInt,
								 ppc2ir.getGPRegister(rS),
								 ppc2ir.getGPRegister(rB)));
	}
	// Perform complement
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, ppc2ir.getGPRegister(rA),
							    tempInt.copyRO()));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}
/**
 * The decoder for the mtmsr instruction
 */
final class mtmsr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stdx instruction
 */
final class stdx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stwcx instruction
 */
final class stwcx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stwx (store word conditional indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b + rB
     * MEM(EA, 4) <- rS
     * if RESERVE then
     *  if RESERVE_ADDR = physical_addr(EA)
     *   MEM(EA,4) <- rS
     *   CR0 <- 0b00 || 0b1 || XER[SO]
     *  else
     *   u <- undefined 1-bit value
     *   if u then MEM(EA,4) <- rS
     *   CR0 <- 0b00 || u || XER[SO]
     *  RESERVE <- 0
     * else
     *   CR0 <- 0b00 || 0b0 || XER[SO]
     * </listing>
     * See also: lwarx
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), ppc2ir.getGPRegister(rS));
	// Set CR field to say store succeeded
	setCRfield(ppc2ir, lazy, 0, new OPT_IntConstantOperand(0), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	return pc+4;
    }
}
/**
 * The decoder for the stwx instruction
 */
final class stwx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stwx (store word indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b + rB
     * MEM(EA, 4) <- rS
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), ppc2ir.getGPRegister(rS));
	return pc+4;
    }
}
/**
 * The decoder for the stdux instruction
 */
final class stdux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stwux instruction
 */
final class stwux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stwux (store word with update indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b + rB
     * MEM(EA, 4) <- rS
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), ppc2ir.getGPRegister(rS));
	// Place EA into rA
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA), 
							   EA.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the mtsr instruction
 */
final class mtsr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stdcx instruction
 */
final class stdcx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stbx instruction
 */
final class stbx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stbx (store byte indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b + rB
     * MEM(EA, 1) <- rS[24-31]
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA.copyRO(),
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore8(EA.copyRO(), ppc2ir.getGPRegister(rS));
	return pc+4;
    }
}
/**
 * The decoder for the mulld instruction
 */
final class mulld_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mtsrin instruction
 */
final class mtsrin_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the dcbtst instruction
 */
final class dcbtst_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate dcbtst (data cache block touch for store)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int zero, int rA, int rB, int secondaryOpcode, int zero2) {
	// translated into a NOP
	return pc+4;
    }
}
/**
 * The decoder for the stbux instruction
 */
final class stbux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the dcbt instruction
 */
final class dcbt_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate dcbt (data cache block touch)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int zero, int rA, int rB, int secondaryOpcode, int zero2) {
	// translated into a NOP
	return pc+4;
    }
}
/**
 * The decoder for the lhzx instruction
 */
final class lhzx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lhzx (load half word and zero indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b + (rB)
     * rD <- (16)0 || MEM(EA, 2)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), result);
	// Turn 32bit address into appropriate shift using last 2 bits
	// of the address
	//-#if RVM_FOR_POWERPC
	// EA = (EA & 0x2) * 8
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(0x2)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	//-#else
	// EA = (~EA & 0x2) * 8
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, EA.copyRO(),
							    EA.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(0x2)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	//-#endif
	// rD >>>= EA
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, result.copyRO(),
							     result.copyRO(),
							     EA.copyRO()));
	// rD &= 0xffff
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, result.copyRO(),
							     result.copyRO(),
							     new OPT_IntConstantOperand(0xffff)));
	return pc+4;
    }
}
/**
 * The decoder for the eqv instruction
 */
final class eqv_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the tlbie instruction
 */
final class tlbie_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the eciwx instruction
 */
final class eciwx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lhzux instruction
 */
final class lhzux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the xor instruction
 */
final class xor_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	int Rc = ps.currentInstruction & 0x1;
	ps.setRegister(rA, ps.getRegister(rS) ^ ps.getRegister(rB));
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate xor
     * <listing>
     * rA <- (rS) (+) (rB)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	// Perform xor
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_XOR, ppc2ir.getGPRegister(rA),
							     ppc2ir.getGPRegister(rS),
							     ppc2ir.getGPRegister(rB)));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}
/**
 * The decoder for the lwax instruction
 */
final class lwax_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the dst instruction
 */
final class dst_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lhax instruction
 */
final class lhax_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lhax (load half word algabraeic indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b+ (rB)
     * rD <- EXTS(MEM(EA, 2))
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rD, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Load value from memory at EA into rD.
	OPT_RegisterOperand result = ppc2ir.getGPRegister(rD);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), result);
	// Turn 32bit address into appropriate shift using last 2 bits
	// of the address
	//-#if RVM_FOR_POWERPC
	// EA = (EA & 0x2) * 8
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(0x2)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	//-#else
	// EA = (~EA & 0x2) * 8
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, EA.copyRO(),
							    EA.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(0x2)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, EA.copyRO(),
							     EA.copyRO(),
							     new OPT_IntConstantOperand(3)));
	//-#endif
	// rD >>>= EA
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, result.copyRO(),
							     result.copyRO(),
							     EA.copyRO()));
	// rD = (rD << 16) >> 16
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, result.copyRO(),
							     result.copyRO(),
							     new OPT_IntConstantOperand(16)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, result.copyRO(),
							     result.copyRO(),
							     new OPT_IntConstantOperand(16)));
	return pc+4;
    }
}
/**
 * The decoder for the lvxl instruction
 */
final class lvxl_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the tlbia instruction
 */
final class tlbia_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mftb instruction
 */
final class mftb_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lwaux instruction
 */
final class lwaux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the dstst instruction
 */
final class dstst_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lhaux instruction
 */
final class lhaux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the sthx instruction
 */
final class sthx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate sth (store half word)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b+ EXTS(d)
     * MEM(EA, 2) <- rS[16-31]
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);

	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Store value from rS into memory at address EA
	ppc2ir.ps.memory.translateStore16(EA.copyRO(), ppc2ir.getGPRegister(rS));
	return pc+4;
    }
}
/**
 * The decoder for the orc instruction
 */
final class orc_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate orc (or with complement)
     * <listing>
     * rA <- (rS) | ¬(rB)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);
	// Perform complement
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, tempInt,
							    ppc2ir.getGPRegister(rB)));
	// Perform or
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, ppc2ir.getGPRegister(rA),
							     ppc2ir.getGPRegister(rS),
							     tempInt.copyRO()));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}
/**
 * The decoder for the slbie instruction
 */
final class slbie_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the ecowx instruction
 */
final class ecowx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the sthux instruction
 */
final class sthux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the or instruction
 */
final class or_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	int Rc = ps.currentInstruction & 0x1;
	ps.setRegister(rA, ps.getRegister(rS) | ps.getRegister(rB));
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate or
     * <listing>
     * rA <- (rS) | (rB)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	// Perform or
	if (rS == rB) {
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA),
							       ppc2ir.getGPRegister(rS)));
	}
	else {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rS),
								 ppc2ir.getGPRegister(rB)));
	}
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}

/**
 * The decoder for the divdu instruction
 */
final class divdu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the dcbi instruction
 */
final class dcbi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the nand instruction
 */
final class nand_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate and
     * <listing>
     * rA <- ¬((rS) & (rB))
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);
	// Perform and
	if (rS == rB) {
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       tempInt,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt,
								 ppc2ir.getGPRegister(rS),
								 ppc2ir.getGPRegister(rB)));
	}
	// Perform complement
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_NOT, ppc2ir.getGPRegister(rA),
							    tempInt.copyRO()));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}
/**
 * The decoder for the duvd instruction
 */
final class divd_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the slbia instruction
 */
final class slbia_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mcrxr instruction
 */
final class mcrxr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lswx instruction
 */
final class lswx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lwbrx instruction
 */
final class lwbrx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lfsx instruction
 */
final class lfsx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lfsx (load floating-point single indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b+ (rB)
     * frD <- DOUBLE(MEM(EA, 4))
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int rA, int rB, int secondaryOpcode, int Rc) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	// Load value from memory at EA into tempInt
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0+1);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), tempInt);
	// Convert bit pattern to a float.
	OPT_RegisterOperand tempFloat = ppc2ir.getTempFloat(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_BITS_AS_FLOAT, tempFloat,
							    tempInt.copyRO()));

	// Now to a double, and put into register.
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_2DOUBLE, ppc2ir.getFPRegister(frD),
							    tempFloat.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the srw instruction
 */
final class srw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int rB = bits(ps.currentInstruction,16,20);
	int Rc = ps.currentInstruction & 0x1;
	ps.setRegister(rA, ps.getRegister(rS) >>> ps.getRegister(rB));
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate srw (shift right word)
     * <listing>
     * n <- rB[27-31]
     * r <- ROTL(rS,32-n)
     * </listing>
     * Equivalent to:
     * <listing>
     * rD <- rA >>> rB
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, ppc2ir.getGPRegister(rA),
							     ppc2ir.getGPRegister(rS),
							     ppc2ir.getGPRegister(rB)));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}
/**
 * The decoder for the srd instruction
 */
final class srd_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the cntlzd instruction
 */
final class cntlzd_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lfsux instruction
 */
final class lfsux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mfsr instruction
 */
final class mfsr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lswi instruction
 */
final class lswi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the sync instruction
 */
final class sync_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lfdx instruction
 */
final class lfdx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate lfdx (load floating-point double indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b+ (rB)
     * frD <- MEM(EA, 8)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	OPT_RegisterOperand EA2 = ppc2ir.getTempInt(0+1);
	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA2,
							     EA.copyRO(),
							     new OPT_IntConstantOperand(4)));

	// Load value from memory at EA & EA2 into tempInt & tempInt2 (msb at low address)
	OPT_RegisterOperand tempInt  = ppc2ir.getTempInt(0+2);
	OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(0+3);
	ppc2ir.ps.memory.translateLoad32(EA.copyRO(), tempInt);
	ppc2ir.ps.memory.translateLoad32(EA2.copyRO(), tempInt2);

	// Merge ints into a long
	// tempLong = ((long)tempInt << 32) | (long)tempInt2
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);
	OPT_RegisterOperand tempLong2 = ppc2ir.getTempLong(1);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, tempLong, tempInt.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, tempLong2, tempInt2.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_SHL, tempLong.copyRO(), 
							     tempLong.copyRO(),
							     new OPT_IntConstantOperand(32)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, tempLong2.copyRO(), 
							     tempLong2.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffl)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_OR, tempLong.copyRO(), 
							     tempLong.copyRO(),
							     tempLong2.copyRO()));
	// Convert long to a double, and put into register.
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_BITS_AS_DOUBLE, ppc2ir.getFPRegister(frD),
							    tempLong.copyRO()));
	return pc+4;
    }
}
/**
 * The decoder for the lfdux instruction
 */
final class lfdux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mfsrin instruction
 */
final class mfsrin_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stswx instruction
 */
final class stswx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stwbrx instruction
 */
final class stwbrx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stfsx instruction
 */
final class stfsx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stfsux instruction
 */
final class stfsux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stswi instruction
 */
final class stswi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stfdx instruction
 */
final class stfdx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate stfdx (store floating-point double indexed)
     * <listing>
     * if rA = 0 then b <- 0
     * else b <- (rA)
     * EA <- b+ (rB)
     * MEM(EA, 8) <- (frS)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frS, int rA, int rB, int secondaryOpcode, int zero) {
	OPT_RegisterOperand EA = ppc2ir.getTempInt(0);
	OPT_RegisterOperand EA2 = ppc2ir.getTempInt(0+1);
	if (rA == 0) {
	    // EA = rB
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       EA,
							       ppc2ir.getGPRegister(rB)));
	}
	else {
	    // EA = rA + rB
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA,
								 ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rB)));
	}
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_ADD, EA2,
							     EA.copyRO(),
							     new OPT_IntConstantOperand(4)));

	// Split double into ints
	// tempLong = DOUBLE_AS_LONG_BITS(frS)
	// tempLong2 = tempLong >>> 32
	// tempLong = tempLong & 0xffffffff
	// tempInt = (int)tempLong
	// tempInt2 = (int)tempLong2 <- msb
	// mem[EA] = tempInt2
	// mem[EA2] = tempInt
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);
	OPT_RegisterOperand tempLong2 = ppc2ir.getTempLong(1);
	OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0+2);
	OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(0+3);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_AS_LONG_BITS, tempLong,
							    ppc2ir.getFPRegister(frS)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_USHR, tempLong2, tempLong.copyRO(),
							     new OPT_IntConstantOperand(32)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, tempLong.copyRO(), tempLong.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffl)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, tempInt, tempLong.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_2INT, tempInt2, tempLong2.copyRO()));

	// Store value into memory at address EA (msb at low address)
	ppc2ir.ps.memory.translateStore32(EA.copyRO(), tempInt2.copyRO());
	ppc2ir.ps.memory.translateStore32(EA2.copyRO(), tempInt.copyRO());
	return pc+4;
    }
}
/**
 * The decoder for the dcba instruction
 */
final class dcba_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stfdux instruction
 */
final class stfdux_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the lhbrx instruction
 */
final class lhbrx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the sraw instruction
 */
final class sraw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate sraw (shift right algebraic word)
     * <listing>
     * n <- rB[27-31]
     * r <- ROTL(rS,32-n)
     * if rB[26] = 0 then m <- MASK(n)
     * else m <- (32)0
     * S <- rS
     * rA <- r & m | S & ¬m
     * XER[CA] <- S & ((r & ¬m) != 0)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int secondaryOpcode, int Rc) {
	OPT_BasicBlock rB26_set = ppc2ir.createBlockAfterCurrent();
	OPT_BasicBlock rB26_clear = ppc2ir.createBlockAfter(rB26_set);
	OPT_BasicBlock performSraw = ppc2ir.createBlockAfter(rB26_clear);

	// test rB[26]	 
	OPT_RegisterOperand mask = ppc2ir.getTempInt(0);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, mask,
							     ppc2ir.getGPRegister(rB),
							     new OPT_IntConstantOperand(0x10)));
	ppc2ir.appendInstructionToCurrentBlock(IfCmp.create(INT_IFCMP, null,
							    mask.copyRO(),
							    new OPT_IntConstantOperand(0),
							    OPT_ConditionOperand.EQUAL(),
							    rB26_clear.makeJumpTarget(),
							    ppc2ir.getConditionalBranchProfileOperand(true)));
	ppc2ir.getCurrentBlock().insertOut(rB26_clear);

	// if rB[26] == 1 then mask = 0
	rB26_set.appendInstruction(Move.create(INT_MOVE, mask.copyRO(),
					       new OPT_IntConstantOperand(0)));
	rB26_set.appendInstruction(Goto.create(GOTO, performSraw.makeJumpTarget()));
	rB26_set.deleteNormalOut();
	rB26_set.insertOut(performSraw);
	// else mask=(0x1 << rB) - 1;
	rB26_clear.appendInstruction(Binary.create(INT_SHL, mask.copyRO(),
						   new OPT_IntConstantOperand(1),
						   ppc2ir.getGPRegister(rB)));
	rB26_clear.appendInstruction(Binary.create(INT_SUB, mask.copyRO(),
						   mask.copyRO(),
						   new OPT_IntConstantOperand(1)));
	// perform sraw
	ppc2ir.setCurrentBlock(performSraw);
	// shifted bits out of rS
	OPT_RegisterOperand shiftedBits = ppc2ir.getTempInt(1);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, shiftedBits,
							     ppc2ir.getGPRegister(rS),
							     mask.copyRO()));
	// do rA = rS >> rB
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, ppc2ir.getGPRegister(rA),
							     ppc2ir.getGPRegister(rS),
							     ppc2ir.getGPRegister(rB)));
	 
	// set XER CA dependent on whether bits were shifted out of rS
	ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, ppc2ir.getXER_CA_Register(),
								 shiftedBits.copyRO(),
								 new OPT_IntConstantOperand(0),
								 OPT_ConditionOperand.EQUAL(),
								 OPT_BranchProfileOperand.unlikely()));
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}
/**
 * The decoder for the srad instruction
 */
final class srad_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the dss instruction
 */
final class dss_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the srawi instruction
 */
final class srawi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate srawi (shift right algebraic word immediate)
     * <listing>
     * n <- SH
     * r <- ROTL(rS,32-n)
     * m <- MASK(n)
     * S <- rS
     * rA <- r & m | S & ¬m
     * XER[CA] <- S & ((r & ¬m) != 0)
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int SH, int secondaryOpcode, int Rc) {
	if(SH == 0) {
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA),
							       ppc2ir.getGPRegister(rS)));
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(LONG_MOVE, ppc2ir.getXER_CA_Register(),
							       new OPT_LongConstantOperand(0)));
	}
	else {
	    // shifted bits out of rS
	    OPT_RegisterOperand shiftedBits = ppc2ir.getTempInt(0);
	    int mask=(0x1 << SH) - 1;
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, shiftedBits,
								 ppc2ir.getGPRegister(rS),
								 new OPT_IntConstantOperand(mask)));
	    // do rA = rS >> SH
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rS),
								 new OPT_IntConstantOperand(SH)));

	    // set XER CA dependent on whether bits were shifted out of rS
	    ppc2ir.appendInstructionToCurrentBlock(BooleanCmp.create(BOOLEAN_CMP_INT, ppc2ir.getXER_CA_Register(),
								     shiftedBits.copyRO(),
								     new OPT_IntConstantOperand(0),
								     OPT_ConditionOperand.EQUAL(),
								     OPT_BranchProfileOperand.unlikely()));
	}
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}
/**
 * The decoder for the sradi instruction
 */
final class sradi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the eieio instruction
 */
final class eieio_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the sthbrx instruction
 */
final class sthbrx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the extsh instruction
 */
final class extsh_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate extsh (extend sign half word)
     * <listing>
     * S <- rS[16]
     * rA[16-31] <- rS[16-31]
     * ra[0-15] <- (16)S
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int zero, int secondaryOpcode, int Rc) {
	if (rA == rS) {
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(16)));		
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, ppc2ir.getGPRegister(rA),
								 ppc2ir.getGPRegister(rA),
								 new OPT_IntConstantOperand(16)));		
	}
	else {
	    OPT_RegisterOperand tempInt = ppc2ir.getTempInt(0);
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt,
								 ppc2ir.getGPRegister(rS),
								 new OPT_IntConstantOperand(16)));		
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHR, ppc2ir.getGPRegister(rA),
								 tempInt.copyRO(),
								 new OPT_IntConstantOperand(16)));		
	}
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc+4;
    }
}
/**
 * The decoder for the extsb instruction
 */
final class extsb_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the tlbld instruction
 */
final class tlbld_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the icbi instruction
 */
final class icbi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the stfiwx instruction
 */
final class stfiwx_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the extsw instruction
 */
final class extsw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the tlbli instruction
 */
final class tlbli_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the dcbz instruction
 */
final class dcbz_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate dcbz (data cache block clear to zero)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int zero, int rA, int rB, int secondaryOpcode, int zero2) {
	// translated into a NOP
	return pc+4;
    }
}
/**
 * The decoder for the fcmpu instruction
 */
final class fcmpu_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fcmpu (floating point compare unordered)
     * <listing>
     * if (frA) is a NaN or (frB) is a NaN then c <- 0b0001
     * else if (frA) < (frB) then c <- 0b1000
     * else if (frA) > (frB) then c <- 0b0100
     * else c <- 0b0010
     * fpscr[FPCC] <- c
     * CR[4 * crfD-4 * crfD + 3] <- c
     * if (frA) is an SNaN or (frB) is an SNaN then fpscr[VXSNAN] <- 1
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crfD00, int frA, int frB, int secondaryOpcode, int zero) {
	int crfD = crfD00 >>> 2;

	// Lazily set the condition codes in the CR register
	setCRfield(ppc2ir, lazy, crfD, ppc2ir.getFPRegister(frA), ppc2ir.getFPRegister(frB), FP_CMPU);
	// Set mapping of them to FPCC bits of the FPRF in the FPSCR
	//	 lazy.setCrfToFprfMap(crfD);

	return pc+4;
    }
}
/**
 * The decoder for the frsp instruction
 */
final class frsp_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate frsp (floating round to single)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int zero, int frB, int secondaryOpcode, int Rc) {
	// This is truly hideous!!!! It depends on the FPSCR flags to
	// define how to round to single precision.  So I'll fudge this
	// and do something simpler, just cast to single precision using
	// whatever Jikes does.
	 
	OPT_RegisterOperand tempFloat = ppc2ir.getTempFloat(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat,
							    ppc2ir.getFPRegister(frB)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_2DOUBLE, ppc2ir.getFPRegister(frD),
							    tempFloat.copyRO()));


	// Set the FPSCR
	//?????????????? NOT YET IMPLEMENTED

	// Check the condition register bit. */
	if (Rc != 0) {
	    // Set condition codes
	    throw new Error("Todo: record on frsp");
	}
	return pc + 4;
    }
}
/**
 * The decoder for the fctiw instruction
 */
final class fctiw_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fctiw (floating convert to integer word)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int zero, int frB, int secondaryOpcode, int Rc) {
	// @todo: rounding modes
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2LONG, tempLong,
							    ppc2ir.getFPRegister(frB)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_BITS_AS_DOUBLE, ppc2ir.getFPRegister(frD),
							    tempLong.copyRO()));
	// Check the condition register record
	if(Rc != 0){
	    throw new Error("fctiw: setting CR1 not yet implemented.");
	}
	return pc+4;
    }
}  
/**
 * The decoder for the fctiwz instruction
 */
final class fctiwz_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fctiwz (floating convert to integer word with round toward zero)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int zero, int frB, int secondaryOpcode, int Rc) {
	// @todo: rounding modes
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2LONG, tempLong,
							    ppc2ir.getFPRegister(frB)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_BITS_AS_DOUBLE, ppc2ir.getFPRegister(frD),
							    tempLong.copyRO()));
	// Check the condition register record
	if(Rc != 0){
	    throw new Error("fctiwz: setting CR1 not yet implemented.");
	}
	return pc+4;
    }
}  
/**
 * The decoder for the fcmpo instruction
 */
final class fcmpo_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mtfsb1 instruction
 */
final class mtfsb1_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mtfsb1 (move to fpscr bit 1)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crbD, int zero, int zero2, int secondaryOpcode, int Rc) {
	if((crbD == 1) || (crbD == 2)){
	    throw new Error("mtfsb1: attempting to set bit " + crbD);
	}
	int setBit = 0x80000000 >>> crbD;
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, ppc2ir.getFPSCRRegister(),
							     ppc2ir.getFPSCRRegister(),
							     new OPT_IntConstantOperand(setBit)));

	// Check the condition register record
	if(Rc != 0){
	    throw new Error("mtfsb1: setting CR1 not yet implemented.");
	}
	return pc+4;
    }
}
/**
 * The decoder for the fneg instruction
 */
final class fneg_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fneg (floating negate)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int zero, int frB, int secondaryOpcode, int Rc) {
	// @todo setting of fpscr
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_NEG, ppc2ir.getFPRegister(frD),
							    ppc2ir.getFPRegister(frB)));
	if (Rc != 0) {
	    throw new Error("todo: record within a fneg instruction");
	}
	return pc + 4;
    }
}
/**
 * The decoder for the mcrfs instruction
 */
final class mcrfs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the mtfsb0 instruction
 */
final class mtfsb0_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mtfsb0 (move to fpscr bit 0)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crbD, int zero, int zero2, int secondaryOpcode, int Rc) {
	if((crbD == 1) || (crbD == 2)){
	    throw new Error("mtfsb0: attempting to clear bit " + crbD);
	}
	int clearBit = ~(0x80000000 >>> crbD);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, ppc2ir.getFPSCRRegister(),
							     ppc2ir.getFPSCRRegister(),
							     new OPT_IntConstantOperand(clearBit)));

	// Check the condition register record
	if(Rc != 0){
	    throw new Error("mtfsb0: setting CR1 not yet implemented.");
	}
	return pc+4;
    }
}
/**
 * The decoder for the fmr instruction
 */
final class fmr_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fmr (floating move register)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int zero, int frB, int secondaryOpcode, int Rc) {
	ppc2ir.appendInstructionToCurrentBlock(Move.create(DOUBLE_MOVE, ppc2ir.getFPRegister(frD),
							   ppc2ir.getFPRegister(frB)));
	if(Rc != 0) {
	    throw new Error("fmr: setting CR1 not yet implemented.");
	}
	return pc+4;
    }
}
/**
 * The decoder for the mtfsfi instruction
 */
final class mtfsfi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mtfsfi (move to FPSCR field immediate)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int crfD00, int zero, int imm, int secondaryOpcode, int Rc) {
	int crfD = crfD00 >>> 2;
	if(crfD == 0) {
	    throw new Error("mtfsfi: attempting to set field " + crfD);
	}
	int clearBitsMask = ~(0xf0000000 >>> (crfD * 4));
	int bitsToSet = (imm << 28) >>> (crfD * 4);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, ppc2ir.getFPSCRRegister(),
							     ppc2ir.getFPSCRRegister(),
							     new OPT_IntConstantOperand(clearBitsMask)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, ppc2ir.getFPSCRRegister(),
							     ppc2ir.getFPSCRRegister(),
							     new OPT_IntConstantOperand(bitsToSet)));
	if(Rc != 0) {
	    throw new Error("mtfsfi: setting CR1 not yet implemented.");
	}
	return pc+4;
    }
}
/**
 * The decoder for the fnabs instruction
 */
final class fnabs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fnabs (floating negative absolute value)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int zero, int frB, int secondaryOpcode, int Rc) {
	OPT_BasicBlock fallThrough = ppc2ir.getNextBlock();
	OPT_BasicBlock negate = ppc2ir.createBlockAfterCurrent();
	OPT_BasicBlock lteZeroTest = ppc2ir.getCurrentBlock();
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getFPRegister(frD),
							   ppc2ir.getFPRegister(frB)));
	lteZeroTest.appendInstruction(IfCmp.create(INT_IFCMP, null,
						   ppc2ir.getFPRegister(frB),
						   new OPT_DoubleConstantOperand(0),
						   OPT_ConditionOperand.LESS_EQUAL(),
						   fallThrough.makeJumpTarget(),
						   ppc2ir.getConditionalBranchProfileOperand(true)));
	lteZeroTest.insertOut(fallThrough);
	negate.appendInstruction(Unary.create(DOUBLE_NEG, ppc2ir.getFPRegister(frD),
					      ppc2ir.getFPRegister(frD)));
	if(Rc != 0) {
	    throw new Error("fabs: setting CR1 not yet implemented.");
	}
	return pc+4;
    }
}
/**
 * The decoder for the fabs instruction
 */
final class fabs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fabs (floating absolute value)
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int zero, int frB, int secondaryOpcode, int Rc) {
	OPT_BasicBlock fallThrough = ppc2ir.getNextBlock();
	OPT_BasicBlock negate = ppc2ir.createBlockAfterCurrent();
	OPT_BasicBlock gteZeroTest = ppc2ir.getCurrentBlock();
	ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getFPRegister(frD),
							   ppc2ir.getFPRegister(frB)));
	gteZeroTest.appendInstruction(IfCmp.create(INT_IFCMP, null,
						   ppc2ir.getFPRegister(frB),
						   new OPT_DoubleConstantOperand(0),
						   OPT_ConditionOperand.GREATER_EQUAL(),
						   fallThrough.makeJumpTarget(),
						   ppc2ir.getConditionalBranchProfileOperand(true)));
	gteZeroTest.insertOut(fallThrough);
	negate.appendInstruction(Unary.create(DOUBLE_NEG, ppc2ir.getFPRegister(frD),
					      ppc2ir.getFPRegister(frD)));
	if(Rc != 0) {
	    throw new Error("fabs: setting CR1 not yet implemented.");
	}
	return pc+4;
    }
}
/**
 * The decoder for the mffs instruction
 */
final class mffs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int frD = bits(ps.currentInstruction,6,10);
	int Rc = ps.currentInstruction & 0x1;
	ps.setFPregister(frD, Double.longBitsToDouble(((long)ps.fpscr) & 0xFFFFFFFF));
	if(Rc != 0) {
	    throw new Error("mffs: setting CR1 not yet implemented.");
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate mffs (move from fpscr)
     * <listing>
     * frD[32-63] <- FPSCR
     * </listing>
     */
    protected int translateX_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int frD, int zero, int frB, int secondaryOpcode, int Rc) {
	OPT_RegisterOperand tempLong = ppc2ir.getTempLong(0);

	ppc2ir.appendInstructionToCurrentBlock(Unary.create(INT_2LONG, tempLong, ppc2ir.getFPSCRRegister()));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(LONG_AND, tempLong.copyRO(),
							     tempLong.copyRO(),
							     new OPT_LongConstantOperand(0xffffffffL)));

	ppc2ir.appendInstructionToCurrentBlock(Unary.create(LONG_BITS_AS_DOUBLE, 
							    ppc2ir.getFPRegister(frD), 
							    tempLong.copyRO()));
	if(Rc != 0) {
	    throw new Error("mffs: setting CR1 not yet implemented.");
	}
	return pc+4;
    }
}
/**
 * The decoder for the fctid instruction
 */
final class fctid_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the fctidz instruction
 */
final class fctidz_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the fcfid instruction
 */
final class fcfid_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}

// -oO Start of SC form instructions Oo-

/**
 * The decoder for the sc instruction
 */
final class sc_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	ps.doSysCall();
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate sc (system call)
     */
    protected int translateSC_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int zero1, int zero2, int zero3, int one, int zero4){
	if (VM.VerifyAssertions) VM._assert((zero1 == zero2) && (zero2 == zero3) && (zero3 == zero4) && (zero4 == 0) && (one == 1));
	ppc2ir.plantSystemCall(lazy, pc);
	return pc + 4;
    }
}

// -oO Start of M form instructions Oo-

/**
 * The decoder for the rlwimi instruction
 */
final class rlwimi_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int SH = bits(ps.currentInstruction,16,20);
	int MB = bits(ps.currentInstruction,21,25);
	int ME = bits(ps.currentInstruction,26,30);
	int Rc = ps.currentInstruction & 0x1;
	// Mask has 1 bits from MB to ME, other bits are 0.
	int m = 0;
	// Simple if MB <= ME.
	if(MB <= ME) {
	    for(int b = MB; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}
	else { // Otherwise, need to wrap.
	    for(int b = MB; b <= 31; b++) {		
		m |= (0x80000000 >>> b);
	    }
	    for(int b = 0; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}
	if (m == 0) {
	    ps.setRegister(rA, 0);
	}
	else {
	    int tempr;
	    if (SH != 0){
		// 1. calculate rS << SH and put into tempInt1
		// 2. calculate rS >>> (32-SH) and put into tempInt2
		// 3. put the bitwise OR of these into tempr (the variable r in
		// the prog. env. manual entry for this instruction).
		int tempInt1 = ps.getRegister(rS) << SH;
		int tempInt2 = ps.getRegister(rS) >>> (32 - SH);
		tempr = tempInt1 | tempInt2;
	    }
	    else {
		tempr = ps.getRegister(rS);
	    }
	    if (m == 0xffffffff) {
		ps.setRegister(rA, tempr);
	    }
	    else {
		// AND the value in tempr with the value m, and put into rA
		ps.setRegister(rA, (tempr & m) | (ps.getRegister(rA) & ~m));
	    }
	}
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate rlwimi (rotate left word immediate then mask insert)
     * <listing>
     * n <- SH
     * r <- ROTL(rS,n)
     * m <- MASK(MB,ME)
     * rA <- (r & m) | (rA & ¬m)
     * </listing>
     * Other simplified mnemonics: inslwi, inslrwi
     */
    protected int translateM_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int SH, int MB, int ME, int Rc){
	// Mask has 1 bits from MB to ME, other bits are 0.
	int m = 0;
	// Simple if MB <= ME.
	if(MB <= ME) {
	    for(int b = MB; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}
	else { // Otherwise, need to wrap.
	    for(int b = MB; b <= 31; b++) {		
		m |= (0x80000000 >>> b);
	    }
	    for(int b = 0; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}

	if (m == 0) {
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA),
							       ppc2ir.getGPRegister(rA)));
	}
	else {
	    OPT_RegisterOperand tempr = ppc2ir.getTempInt(0);
	    if (SH != 0){
		OPT_RegisterOperand tempInt1 = ppc2ir.getTempInt(1);
		OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(2);
		// 1. calculate rS << SH and put into tempInt1
		// 2. calculate rs >>> (32-SH) and put into tempInt2
		// 3. put the bitwise OR of these into tempr (the variable r in
		// the prog. env. manual entry for this instruction).
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt1, ppc2ir.getGPRegister(rS), 
								     new OPT_IntConstantOperand(SH)));
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt2, ppc2ir.getGPRegister(rS),
								     new OPT_IntConstantOperand(32-SH)));
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, tempr, tempInt1.copyRO(), 
								     tempInt2.copyRO()));
	    }
	    else {
		ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, tempr,
								   ppc2ir.getGPRegister(rS)));
	    }
	    if (m == 0xffffffff) {
		ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA),
								   tempr.copyRO()));
	    }
	    else {
		OPT_RegisterOperand tempInt3 = ppc2ir.getTempInt(1);
		OPT_RegisterOperand tempInt4 = ppc2ir.getTempInt(2);
		// AND the value in tempr with the value m, and put into tempInt3.
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt3, 
								     tempr.copyRO(), 
								     new OPT_IntConstantOperand(m)));
		// AND the current value of rA with NOT mask, and put into tempInt4.
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempInt4,
								     ppc2ir.getGPRegister(rA),
								     new OPT_IntConstantOperand(~m)));
		// OR these two and put back into rA.
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, ppc2ir.getGPRegister(rA),
								     tempInt3.copyRO(), tempInt4.copyRO()));
	    }
	}
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc + 4;
    }
}
/**
 * The decoder for the rlwinm instruction
 */
final class rlwinm_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int rS = bits(ps.currentInstruction,6,10);
	int rA = bits(ps.currentInstruction,11,15);
	int SH = bits(ps.currentInstruction,16,20);
	int MB = bits(ps.currentInstruction,21,25);
	int ME = bits(ps.currentInstruction,26,30);
	int Rc = ps.currentInstruction & 0x1;
	// Mask has 1 bits from MB to ME, other bits are 0.
	int m = 0;
	// Simple if MB <= ME.
	if(MB <= ME) {
	    for(int b = MB; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}
	else { // Otherwise, need to wrap.
	    for(int b = MB; b <= 31; b++) {		
		m |= (0x80000000 >>> b);
	    }
	    for(int b = 0; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}
	if (m == 0) {
	    ps.setRegister(rA, 0);
	}
	else {
	    int tempr;
	    if (SH != 0){
		// 1. calculate rS << SH and put into tempInt1
		// 2. calculate rS >>> (32-SH) and put into tempInt2
		// 3. put the bitwise OR of these into tempr (the variable r in
		// the prog. env. manual entry for this instruction).
		int tempInt1 = ps.getRegister(rS) << SH;
		int tempInt2 = ps.getRegister(rS) >>> (32 - SH);
		tempr = tempInt1 | tempInt2;
	    }
	    else {
		tempr = ps.getRegister(rS);
	    }
	    if (m == 0xffffffff) {
		ps.setRegister(rA, tempr);
	    }
	    else {
		// AND the value in tempr with the value m, and put into rA
		ps.setRegister(rA, tempr & m);
	    }
	}
	if (Rc != 0) {
	    ps.setCRfield_signedCompare(0, ps.getRegister(rA), 0);
	}
	return moveInstructionOnAndReturnDecoder(ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate rlwinm (rotate left word immediate then and with mask)
     * <listing>
     * n <- SH
     * r <- ROTL(rS,n)
     * m <- MASK(MB,ME)
     * rA <- r & m
     * </listing>
     * Other simplified mnemonics: extlwi, extrwi, rotlwi, rotrwi, slwi, srwi, clrlwi, clrrwi, clrlslwi
     */
    protected int translateM_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int SH, int MB, int ME, int Rc){
	// Mask has 1 bits from MB to ME, other bits are 0.
	int m = 0;
	// Simple if MB <= ME.
	if(MB <= ME) {
	    for(int b = MB; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}
	else { // Otherwise, need to wrap.
	    for(int b = MB; b <= 31; b++) {		
		m |= (0x80000000 >>> b);
	    }
	    for(int b = 0; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}

	if (m == 0) {
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA),
							       new OPT_IntConstantOperand(0)));
	}
	else {
	    OPT_RegisterOperand tempr = ppc2ir.getTempInt(0);
	    if (SH != 0){
		OPT_RegisterOperand tempInt1 = ppc2ir.getTempInt(1);
		OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(2);
		// 1. calculate rS << SH and put into tempInt1
		// 2. calculate rS >>> (32-SH) and put into tempInt2
		// 3. put the bitwise OR of these into tempr (the variable r in
		// the prog. env. manual entry for this instruction).
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt1, ppc2ir.getGPRegister(rS), 
								     new OPT_IntConstantOperand(SH)));
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt2, ppc2ir.getGPRegister(rS),
								     new OPT_IntConstantOperand(32-SH)));
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, tempr, tempInt1.copyRO(), 
								     tempInt2.copyRO()));
	    }
	    else {
		ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, tempr,
								   ppc2ir.getGPRegister(rS)));
	    }
	    if (m == 0xffffffff) {
		ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA),
								   tempr.copyRO()));
	    }
	    else {
		// AND the value in tempr with the value m, and put into rA
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, ppc2ir.getGPRegister(rA),
								     tempr.copyRO(), 
								     new OPT_IntConstantOperand(m)));
	    }
	}
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc + 4;
    }
}
/**
 * The decoder for the rlwnm instruction
 */
final class rlwnm_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate rlwnm (rotate left word then and with mask)
     * <listing>
     * n <- rB[27-31]
     * r <- ROTL(rS,n)
     * m <- MASK(MB,ME)
     * rA <- r & m
     * </listing>
     * Other simplified mnemonics: rtolw
     */
    protected int translateM_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int rS, int rA, int rB, int MB, int ME, int Rc){
	// Mask has 1 bits from MB to ME, other bits are 0.
	int m = 0;
	// Simple if MB <= ME.
	if(MB <= ME) {
	    for(int b = MB; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}
	else { // Otherwise, need to wrap.
	    for(int b = MB; b <= 31; b++) {		
		m |= (0x80000000 >>> b);
	    }
	    for(int b = 0; b <= ME; b++) {
		m |= (0x80000000 >>> b);
	    }
	}

	if (m == 0) {
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA),
							       new OPT_IntConstantOperand(0)));
	}
	else {
	    OPT_RegisterOperand tempr = ppc2ir.getTempInt(0);

	    OPT_RegisterOperand tempInt1 = ppc2ir.getTempInt(1);
	    OPT_RegisterOperand tempInt2 = ppc2ir.getTempInt(2);

	    OPT_RegisterOperand tempIntSH = ppc2ir.getTempInt(3);

	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, tempIntSH,
								 ppc2ir.getGPRegister(rB),
								 new OPT_IntConstantOperand(0x1f)));

	    // 1. calculate rS << rB and put into tempInt1
	    // 2. calculate rS >>> (32-rB) and put into tempInt2
	    // 3. put the bitwise OR of these into tempr (the variable r in
	    // the prog. env. manual entry for this instruction).
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SHL, tempInt1, ppc2ir.getGPRegister(rS), 
								 tempIntSH.copyRO()));
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_SUB, tempIntSH.copyRO(),
								 new OPT_IntConstantOperand(32),
								 tempIntSH.copyRO()));
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_USHR, tempInt2, ppc2ir.getGPRegister(rS),
								 tempIntSH.copyRO()));
	    ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_OR, tempr, tempInt1.copyRO(), 
								 tempInt2.copyRO()));
	    if (m == 0xffffffff) {
		ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getGPRegister(rA),
								   tempr.copyRO()));
	    }
	    else {
		// AND the value in tempr with the value m, and put into rA
		ppc2ir.appendInstructionToCurrentBlock(Binary.create(INT_AND, ppc2ir.getGPRegister(rA),
								     tempr.copyRO(), 
								     new OPT_IntConstantOperand(m)));
	    }
	}
	if (Rc != 0) {
	    // Set condition codes
	    setCRfield(ppc2ir, lazy, 0, ppc2ir.getGPRegister(rA), new OPT_IntConstantOperand(0), SIGNED_INT_CMP);
	}
	return pc + 4;
    }
}

// -oO Start of B form instructions Oo-

/**
 * The decoder for the bc instruction
 */
final class bc_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int BO = bits(ps.currentInstruction,6,10);
	int BI = bits(ps.currentInstruction,11,15);
	int BD = bits(ps.currentInstruction,16,29);
	int AA = bits(ps.currentInstruction,30,30);
	int LK = ps.currentInstruction & 0x1;
	int target_address;
	if (AA != 0) {
	    target_address = EXTS(BD, 14) << 2;
	}
	else {
	    target_address = ps.getCurrentInstructionAddress() + (EXTS(BD, 14) << 2);
	}
	if (LK != 0) {
	    ps.lr = ps.getCurrentInstructionAddress() + 4;
	    ps.branchInfo.registerCall(ps.getCurrentInstructionAddress(), ps.getCurrentInstructionAddress()+4, target_address);
	}
	// decode BO
	boolean branch_if_ctr_zero = ((BO & 2) != 0);
	System.out.println("bc(BO="+BO+",BI="+BI+",BD="+BD+",AA="+AA+",LK="+LK+")"+
			   " - target_address=" + target_address +
			   " - ctr="+ ps.ctr +
			   " - cr=" + ps.getCR());
	if ((BO & 4) == 0) {
	    ps.ctr--;
	    if ((branch_if_ctr_zero && (ps.ctr != 0))||
		(!branch_if_ctr_zero && (ps.ctr == 0))) {
		return moveInstructionOnAndReturnDecoder(ps);
	    }
	}
	if ((BO & 16) == 0) {
	    boolean branch_if_cond_true = ((BO & 8) != 0);
	    boolean cr_bit_set = ps.getCR_bit(BI);
	    if(branch_if_cond_true != cr_bit_set) {
		return moveInstructionOnAndReturnDecoder(ps);
	    }
	}
	ps.setCurrentInstructionAddress(target_address);
	ps.currentInstruction = ps.memoryLoad32(target_address);
	return findDecoder(ps.currentInstruction);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate bc (branch conditional)
     * <listing>
     * if LK then LR <-iea CIA + 4
     * if ¬ BO[2] then CTR <- CTR - 1
     * ctr_ok <- BO[2] | ((CTR != 0) (+) BO[3])
     * cond_ok <- BO[0] | (CR[BI] == BO[1])
     * if ctr_ok & cond_ok then
     *   if AA then NIA <-iea EXTS(BD || 0b00)
     *   else NIA <-iea CIA + EXTS(BD || 0b00)
     * </listing>
     * Other simplified mnemonics: bltctr, bnectr
     */
    protected int translateB_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int inst, int opcode, int BO, int BI, int BD, int AA, int LK){
	// Translation process:
	// if lk alter lr value
	// decode BO and optionally plant any of:
	//  1: decrement CTR
	//  2: branch to instructionEndBlock if CTR == 0 (or CTR != 0)
	//  3: condition field test and branch to instructionEndBlock if true (or false)
	// plant branch block

	// The block gone to if the branch isn't taken
	OPT_BasicBlock instructionEndBlock = ppc2ir.getNextBlock();
	// Did we find the block we're going to already translated?
	boolean found_mapping = false;
	// Calculate branch target address
	int target_address;
	if (AA != 0) {
	    target_address = EXTS(BD, 14) << 2;
	}
	else {
	    target_address = pc + (EXTS(BD, 14) << 2);
	}

	if (LK != 0){
	    ppc2ir.registerBranchAndLink(pc, target_address);
	    OPT_RegisterOperand lr = ppc2ir.getLRRegister();
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE,
							       lr,
							       new OPT_IntConstantOperand(pc+4)));
	}

	// Decode BO
	boolean likely_to_fallthrough = ((BO & 1) == 0);
	boolean branch_if_ctr_zero = ((BO & 2) == 0);
	if ((BO & 4) == 0) {
	    plantDecrementCTR(ppc2ir);
	    plantBranchToBlockDependentOnCTR(ppc2ir, instructionEndBlock, branch_if_ctr_zero, likely_to_fallthrough);
	}
	if ((BO & 16) == 0) {
	    boolean branch_if_cond_true = ((BO & 8) != 0);
	    plantBranchToBlockDependentOnCondition(ppc2ir, instructionEndBlock, lazy, BI, !branch_if_cond_true, likely_to_fallthrough);
	}

	if ((LK == 0) || (ppc2ir.traceContinuesAfterBranchAndLink(pc))) {
	    // Plant branch block
	    OPT_BasicBlock targetBlock = ppc2ir.findMapping(target_address, lazy);
	    if (targetBlock != null) {
		found_mapping = true;
		ppc2ir.appendInstructionToCurrentBlock(Goto.create(GOTO, targetBlock.makeJumpTarget()));
		ppc2ir.getCurrentBlock().deleteNormalOut();
		ppc2ir.getCurrentBlock().insertOut(targetBlock);
		if (VM.VerifyAssertions) VM._assert(ppc2ir.getCurrentBlock().getNumberOfNormalOut() == 1);
	    }
	    else {
		OPT_Instruction gotoInstr = Goto.create(GOTO, null);
		ppc2ir.appendInstructionToCurrentBlock(gotoInstr);
		ppc2ir.registerGotoTargetUnresolved(gotoInstr, target_address, (PPC_Laziness)lazy.clone());
	    }
	    // stop translation on branch always
	    if (BO == 0x14) {
		return -1;
	    }
	    else {
		// continue translation of next instruction if branch forward
		// or the backward branch is already translated or if we're
		// not trying to optimize here
		if ((target_address > pc)||(found_mapping)||(!DBT_Options.optimizeBackwardBranches)) {
		    return pc+4;
		}
		else {
		    // force translation of backward branch first:
		    // place a goto pc+4 in the next block, then return the
		    // target_address to translate into an empty block
		    ppc2ir.setCurrentBlock(instructionEndBlock);
		    ppc2ir.setNextBlock(ppc2ir.createBlockAfterCurrent());
		    OPT_Instruction gotoInstr = Goto.create(GOTO, null);
		    ppc2ir.appendInstructionToCurrentBlock(gotoInstr);
		    ppc2ir.registerGotoTargetUnresolved(gotoInstr, pc+4, (PPC_Laziness)lazy.clone());			 
		    return target_address;
		}
	    }
	}
	else {
	    // This was a branch and link and the trace should stop, so end
	    // gracefully
	    ppc2ir.setReturnValueResolveLazinessAndBranchToFinish((PPC_Laziness)lazy.clone(), new OPT_IntConstantOperand(target_address));
	    return -1;		
	}
    }
}

// -oO Start of I form instructions Oo-

/**
 * The decoder for the b instruction
 */
final class b_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	int li = bits(ps.currentInstruction,6,29);
	int aa = bits(ps.currentInstruction,30,30);
	int lk = ps.currentInstruction & 0x1;
	int target_address;
	if (aa != 0){
	    target_address = EXTS(li, 24) << 2;
	}
	else {
	    target_address = ps.getCurrentInstructionAddress() + (EXTS(li, 24) << 2);
	}
	if (lk != 0) {
	    ps.lr = ps.getCurrentInstructionAddress() + 4;
	    ps.branchInfo.registerCall(ps.getCurrentInstructionAddress(), ps.getCurrentInstructionAddress()+4, target_address);
	}
	ps.setCurrentInstructionAddress(target_address);
	ps.currentInstruction = ps.memoryLoad32(target_address);
	return findDecoder(ps.currentInstruction);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate b (branch)
     * <listing>
     * if AA then NIA <-iea EXTS(LI || 0b00)
     * else NIA <- CIA + EXTS(LI || 0b00)
     * if LK then LR <-iea CIA + 4
     * </listing>
     * Other simplified mnemonics: ba, bl, bla
     */
    protected int translateI_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int li, int aa, int lk) {
	li = EXTS(li, 24) << 2; // This is what the programming environment manual denotes as EXTS(LI || 0b00)

	int target_addr;
	if (aa != 0) {
	    target_addr = li;
	}
	else {
	    target_addr = pc + li;
	}
	if (lk != 0) {
	    // @todo: record this as a likely return address...
	    ppc2ir.appendInstructionToCurrentBlock(Move.create(INT_MOVE, ppc2ir.getLRRegister(),
							       new OPT_IntConstantOperand(pc + 4)));
	    ppc2ir.registerBranchAndLink(pc, target_addr);
	    if (ppc2ir.traceContinuesAfterBranchAndLink(pc)) {
		return target_addr;
	    }
	    else {
		ppc2ir.setReturnValueResolveLazinessAndBranchToFinish((PPC_Laziness)lazy.clone(), new OPT_IntConstantOperand(target_addr));
		return -1;
	    }
	}
	else {
	    return target_addr;
	}
    }
}


// -oO Start of A form instructions Oo-

/**
 * The decoder for the fdivs instruction
 */
final class fdivs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fdivs (floating divide single)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int zero, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	OPT_RegisterOperand tempFloat1 = ppc2ir.getTempFloat(0);
	OPT_RegisterOperand tempFloat2 = ppc2ir.getTempFloat(1);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat1,
							    ppc2ir.getFPRegister(frA)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat2,
							    ppc2ir.getFPRegister(frB)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(FLOAT_DIV, tempFloat1.copyRO(),
							     tempFloat1.copyRO(),
							     tempFloat2.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_2DOUBLE, ppc2ir.getFPRegister(frD),
							    tempFloat1.copyRO()));
	if (Rc != 0) {
	    throw new Error("todo: record within a fdivs instruction");
	}
	return pc + 4;
    }
}
/**
 * The decoder for the fsubs instruction
 */
final class fsubs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fsubs (floating subtract single)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int zero, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	OPT_RegisterOperand tempFloat1 = ppc2ir.getTempFloat(0);
	OPT_RegisterOperand tempFloat2 = ppc2ir.getTempFloat(1);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat1,
							    ppc2ir.getFPRegister(frA)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat2,
							    ppc2ir.getFPRegister(frB)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(FLOAT_SUB, tempFloat1.copyRO(),
							     tempFloat1.copyRO(),
							     tempFloat2.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_2DOUBLE, ppc2ir.getFPRegister(frD),
							    tempFloat1.copyRO()));
	if (Rc != 0) {
	    throw new Error("todo: record within a fsubs instruction");
	}
	return pc + 4;
    }
}
/**
 * The decoder for the fadds instruction
 */
final class fadds_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fadds (floating add single)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int zero, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	OPT_RegisterOperand tempFloat1 = ppc2ir.getTempFloat(0);
	OPT_RegisterOperand tempFloat2 = ppc2ir.getTempFloat(1);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat1,
							    ppc2ir.getFPRegister(frA)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat2,
							    ppc2ir.getFPRegister(frB)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(FLOAT_ADD, tempFloat1.copyRO(),
							     tempFloat1.copyRO(),
							     tempFloat2.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_2DOUBLE, ppc2ir.getFPRegister(frD),
							    tempFloat1.copyRO()));
	if (Rc != 0) {
	    throw new Error("todo: record within a fsubs instruction");
	}
	return pc + 4;
    }
}
/**
 * The decoder for the fsqrts instruction
 */
final class fsqrts_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the fres instruction
 */
final class fres_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the fmuls instruction
 */
final class fmuls_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fmuls (floating multiply single)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int zero, int frC, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	OPT_RegisterOperand tempFloat1 = ppc2ir.getTempFloat(0);
	OPT_RegisterOperand tempFloat2 = ppc2ir.getTempFloat(1);
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat1,
							    ppc2ir.getFPRegister(frA)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_2FLOAT, tempFloat2,
							    ppc2ir.getFPRegister(frC)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(FLOAT_MUL, tempFloat1.copyRO(),
							     tempFloat1.copyRO(),
							     tempFloat2.copyRO()));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(FLOAT_2DOUBLE, ppc2ir.getFPRegister(frD),
							    tempFloat1.copyRO()));
	if (Rc != 0) {
	    throw new Error("todo: record within a fmuls instruction");
	}
	return pc + 4;
    }
}
/**
 * The decoder for the fmsubs instruction
 */
final class fmsubs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}  
/**
 * The decoder for the fmadds instruction
 */
final class fmadds_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}  
/**
 * The decoder for the fnmadds instruction
 */
final class fnmadds_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
} 
/**
 * The decoder for the fnmsubs instruction
 */
final class fnmsubs_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }
}
/**
 * The decoder for the fdiv instruction
 */
final class fdiv_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fdiv (floating divide double-precision)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int zero, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_DIV, ppc2ir.getFPRegister(frD),
							     ppc2ir.getFPRegister(frA),
							     ppc2ir.getFPRegister(frB)));
	if (Rc != 0) {
	    throw new Error("todo: record within a fdiv instruction");
	}

	return pc + 4;
    }
}
/**
 * The decoder for the fsub instruction
 */
final class fsub_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fsub (floating subtract double-precision)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int zero, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_SUB, ppc2ir.getFPRegister(frD),
							     ppc2ir.getFPRegister(frA),
							     ppc2ir.getFPRegister(frB)));
	if (Rc != 0) {
	    throw new Error("todo: record within a fsub instruction");
	}

	return pc + 4;
    }
}
/**
 * The decoder for the fadd instruction
 */
final class fadd_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fadd (floating add double-precision)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int zero, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_ADD, ppc2ir.getFPRegister(frD),
							     ppc2ir.getFPRegister(frA),
							     ppc2ir.getFPRegister(frB)));
	if (Rc != 0) {
	    throw new Error("todo: record within a fadd instruction");
	}

	return pc + 4;
    }
}
/**
 * The decoder for the fmul instruction
 */
final class fmul_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fmul (floating multiply double-precision)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int zero, int frC, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_MUL, ppc2ir.getFPRegister(frD),
							     ppc2ir.getFPRegister(frA),
							     ppc2ir.getFPRegister(frC)));
	if (Rc != 0) {
	    throw new Error("todo: record within a fmul instruction");
	}

	return pc + 4;
    }
}
/**
 * The decoder for the fmadd instruction
 */
final class fmadd_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fmadd (floating multiply-add double-precision)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int frC, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	OPT_RegisterOperand tempDouble = ppc2ir.getTempDouble(0);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_MUL, tempDouble,
							     ppc2ir.getFPRegister(frA),
							     ppc2ir.getFPRegister(frC)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_ADD, ppc2ir.getFPRegister(frD),
							     tempDouble.copyRO(),
							     ppc2ir.getFPRegister(frB)));
	if (Rc != 0) {
	    throw new Error("todo: record within a fmadd instruction");
	}

	return pc + 4;
    }
}
/**
 * The decoder for the fmsub instruction
 */
final class fmsub_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fmsub (floating multiply-subtract double-precision)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int frC, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	OPT_RegisterOperand tempDouble = ppc2ir.getTempDouble(0);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_MUL, tempDouble,
							     ppc2ir.getFPRegister(frA),
							     ppc2ir.getFPRegister(frC)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_SUB, ppc2ir.getFPRegister(frD),
							     tempDouble.copyRO(),
							     ppc2ir.getFPRegister(frB)));
	if (Rc != 0) {
	    throw new Error("todo: record within a fmsub instruction");
	}

	return pc + 4;
    }
}
/**
 * The decoder for the fnmsub instruction
 */
final class fnmsub_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fnmsub (floating negative multiply-subtract)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int frC, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	OPT_RegisterOperand tempDouble = ppc2ir.getTempDouble(0);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_MUL, tempDouble,
							     ppc2ir.getFPRegister(frA),
							     ppc2ir.getFPRegister(frC)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_SUB, tempDouble.copyRO(),
							     tempDouble.copyRO(),
							     ppc2ir.getFPRegister(frB)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_NEG, ppc2ir.getFPRegister(frD),
							    tempDouble.copyRO()));
	if (Rc != 0) {
	    throw new Error("todo: record within a fnmsub instruction");
	}

	return pc + 4;
    }
}
/**
 * The decoder for the fnmadd instruction
 */
final class fnmadd_decoder extends PPC_InstructionDecoder {
    /**
     * Interpret a single instruction
     * @param ps the process space of the interpretation
     * @param pc the address of the instruction to interpret
     * @return the next instruction interpreter
     */
    public PPC_InstructionDecoder interpretInstruction(PPC_ProcessSpace ps) throws BadInstructionException {
	throw new BadInstructionException(ps.getCurrentInstructionAddress(), ps);
    }

    /**
     * Give this instruction decoder
     */
    protected PPC_InstructionDecoder getDecoder(int instr) {
	return this;
    }

    /**
     * Translate fnmadd (floating negative multiply-add)
     */
    protected int translateA_FORM(PPC2IR ppc2ir, PPC_Laziness lazy, int pc, int instr, int opcode, int frD, int frA, int frB, int frC, int secondaryOpcode, int Rc){
	// @todo setting of fpscr
	OPT_RegisterOperand tempDouble = ppc2ir.getTempDouble(0);
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_MUL, tempDouble,
							     ppc2ir.getFPRegister(frA),
							     ppc2ir.getFPRegister(frC)));
	ppc2ir.appendInstructionToCurrentBlock(Binary.create(DOUBLE_ADD, tempDouble.copyRO(),
							     tempDouble.copyRO(),
							     ppc2ir.getFPRegister(frB)));
	ppc2ir.appendInstructionToCurrentBlock(Unary.create(DOUBLE_NEG, ppc2ir.getFPRegister(frD),
							    tempDouble.copyRO()));
	if (Rc != 0) {
	    throw new Error("todo: record within a fnmadd instruction");
	}

	return pc + 4;
    }
}
