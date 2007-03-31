/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.vmInterface;
import org.jikesrvm.opt.OPT_CompilerPhase;
import org.jikesrvm.VM_Configuration;

import org.jikesrvm.opt.ir.OPT_BasicBlock;
import org.jikesrvm.opt.ir.OPT_GenerationContext;
import org.jikesrvm.opt.ir.OPT_HIRInfo;
import org.jikesrvm.opt.ir.OPT_IR;

import org.jikesrvm.opt.ir.OPT_Instruction;
import static org.jikesrvm.opt.ir.OPT_Operators.*;
import org.jikesrvm.opt.ir.OPT_Operator;
import org.jikesrvm.opt.ir.BBend;
import org.jikesrvm.opt.ir.CondMove;
import org.jikesrvm.opt.ir.Goto;
import org.jikesrvm.opt.ir.IfCmp;
import org.jikesrvm.opt.ir.Move;

import org.jikesrvm.opt.ir.OPT_Operand;
import org.jikesrvm.opt.ir.OPT_BranchProfileOperand;
import org.jikesrvm.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.opt.ir.OPT_RegisterOperand;

/** 
 * The OPT_CompilerPhase which translates from PowerPC machine code
 * into HIR. It is used in PPC_OptimizationPlanner (in place of
 * OPT_ConvertBCtoHIR and OPT_OptimizationPlanner).
 * 
 * @author Richard Matley, Ian Rogers
 *
 */
public abstract class DBT_ConvertBinaryToHIR extends OPT_CompilerPhase
{

    public DBT_ConvertBinaryToHIR() {}

    /**
     * Return a String describing this compiler phase.
     */
    public final String getName()
    {
	return "Generate HIR from machine code";
    }

    /** 
     * Generate HIR from a block of machine code. 
     */
    public final void perform(OPT_IR ir)
    {
	OPT_GenerationContext gc = new OPT_GenerationContext(ir.method, ir.compiledMethod,
							     ir.options, ir.inlinePlan);
	//MC2IR.generateHIR(gc);
	generateHIR(gc);

	ir.gc = gc;
	ir.cfg = gc.cfg;
	ir.regpool = gc.temps;

	if(gc.allocFrame) {
	    ir.stackManager.forceFrameAllocation();
	}

	// ir now contains well formed HIR, we hope.
	ir.IRStage = OPT_IR.HIR;
	ir.HIRInfo = new OPT_HIRInfo(ir);
	if(OPT_IR.SANITY_CHECK) {
	    ir.verify("Initial HIR", true);
	}

	if (VM_Configuration.BuildForPowerPC) {
	    eliminateConditionalMoves(ir);
	}
    }

    protected abstract void generateHIR(OPT_GenerationContext gc);

    /**
     * Remove conditional moves
     */
    public static void eliminateConditionalMoves(OPT_IR ir) {
	OPT_BasicBlock curBB = ir.gc.prologue;
	while(curBB != null) {
	    OPT_Instruction curInstr = curBB.firstInstruction();
	    loop_over_instructions:
	    while(BBend.conforms(curInstr) == false) {
		if(CondMove.conforms(curInstr) && (curInstr.operator() == INT_COND_MOVE)) {
		    OPT_BasicBlock compareBlock; // block containing compare instruction
		    OPT_BasicBlock trueBlock;    // block containing true payload
		    OPT_BasicBlock falseBlock;   // block containing false payload
		    OPT_BasicBlock restOfBlock;  // the rest of the block

		    // Set up blocks
		    compareBlock = curBB;
		    trueBlock = new OPT_BasicBlock(0, ir.gc.inlineSequence, ir.gc.cfg);
		    falseBlock = new OPT_BasicBlock(0, ir.gc.inlineSequence, ir.gc.cfg);
		    restOfBlock = compareBlock.splitNodeAt(curInstr, ir);
		    ir.gc.cfg.linkInCodeOrder(compareBlock, falseBlock);
		    ir.gc.cfg.linkInCodeOrder(falseBlock, trueBlock);
		    ir.gc.cfg.linkInCodeOrder(trueBlock, restOfBlock);

		    // Set up values
		    OPT_RegisterOperand result = CondMove.getResult(curInstr);
		    OPT_Operand val1 = CondMove.getVal1(curInstr);
		    OPT_Operand val2 = CondMove.getVal2(curInstr);
		    OPT_ConditionOperand cond = CondMove.getCond(curInstr);
		    OPT_Operand trueValue = CondMove.getTrueValue(curInstr);
		    OPT_Operand falseValue = CondMove.getFalseValue(curInstr);

		    // Create comparison
		    OPT_Operator cmpType;
		    if(val1.isInt()){
			cmpType = INT_IFCMP;
		    }
		    else if (val1.isDouble()){
			cmpType = DOUBLE_IFCMP;
		    }
		    else {
			throw new Error("Unexpected type of val1: " + val1);
		    }
		    IfCmp.mutate(curInstr, cmpType, null, val1, val2, cond, trueBlock.makeJumpTarget(), OPT_BranchProfileOperand.likely());
		    compareBlock.deleteNormalOut();
		    compareBlock.insertOut(trueBlock);
		    compareBlock.insertOut(falseBlock);

		    // Create false code
		    falseBlock.appendInstruction(Move.create(INT_MOVE, result, falseValue));
		    falseBlock.appendInstruction(Goto.create(GOTO,restOfBlock.makeJumpTarget()));
		    falseBlock.insertOut(restOfBlock);

		    // Create true code
		    trueBlock.appendInstruction(Move.create(INT_MOVE, result, trueValue));
		    trueBlock.insertOut(restOfBlock);
          
		    // Move along
		    curBB = restOfBlock;
		    curInstr = curBB.firstInstruction();
		}
		curInstr = curInstr.nextInstructionInCodeOrder();
	    }
	    curBB = curBB.nextBasicBlockInCodeOrder();
	}
    }
}
