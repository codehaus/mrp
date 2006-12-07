/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.vmInterface;
import com.ibm.jikesrvm.opt.OPT_CompilerPhase;

/** 
 * The OPT_CompilerPhase which translates from PowerPC machine code
 * into HIR. It is used in PPC_OptimizationPlanner (in place of
 * OPT_ConvertBCtoHIR and OPT_OptimizationPlanner).
 * 
 * @author Richard Matley, Ian Rogers
 *
 */
public abstract class DBT_ConvertMCtoHIR extends OPT_CompilerPhase
{


    public DBT_ConvertMCtoHIR() {}

    /**
     * Return a String describing this compiler phase.
     */
    public final String getName()
    {
	return "Generate HIR from MC machine code";
    }

    /** 
     * Generate HIR from a block of MC machine code. 
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

	eliminateConditionalMoves(ir);

	//-#if RVM_FOR_POWERPC
	//	MC2IR.eliminateConditionalMoves(ir);
	//-#endif
    }

    protected abstract void generateHIR(OPT_GenerationContext gc);
    protected abstract void eliminateConditionalMoves(OPT_IR ir);
}
