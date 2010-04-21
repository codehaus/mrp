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
package org.binarytranslator.generic.decoder;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.branchprofile.BranchProfile.BranchType;
import org.binarytranslator.generic.decoder.Laziness.Key;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.BytecodeConstants;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.bc2ir.GenerationContext;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.inlining.InlineDecision;
import org.jikesrvm.compilers.opt.inlining.Inliner;
import org.jikesrvm.compilers.opt.ir.Athrow;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp2;
import org.jikesrvm.compilers.opt.ir.LookupSwitch;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.HIRGenerator;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;

import static org.jikesrvm.compilers.opt.driver.OptConstants.*;
import static org.jikesrvm.compilers.opt.ir.Operators.*;

/**
 * A collection of common tools used by decoders. The public entry point for the
 * translators is generateHIR(GenerationContext gc).
 * 
 * <dl>
 * <dt>Description of the translation algorithm</dt>
 * <dd>
 * <ol>
 * <li>The translation is set up so that the start and end blocks are created.
 * The start block branches to a prefill block that loads all the register
 * values</li>
 * <li>Translation starts from the PC value given in the DBT_Trace.</li>
 * <li>Translation translates subject instructions to HIR using the
 * translateInstruction method. This and the derived decoder object contain the
 * utilities required to aid an instruction translation.</li>
 * <li>The trace ends when the branch prediction deems it necessary. The
 * closing basic blocks are generated and things are wrapped up.</li>
 * </ol>
 * </dd>
 * </dl>
 */
public abstract class CodeTranslator implements HIRGenerator {

  /** The trace that we're currently translating code for. */
  protected final DBT_Trace trace;

  /** TypeReference of org.binarytranslator.generic.os.process.ProcessSpace */
  private static final TypeReference psTref;

  /** Method ProcessSpace.doSysCall */
  public static final RVMMethod sysCallMethod;

  /** TypeReference of org.binarytranslator.generic.fault.BadInstructionException */
  public static final RVMClass badInstrKlass;

  /** Method BadInstructionException.<init> */
  public static final RVMMethod badInstrKlassInitMethod;

  /** Method ProcessSpace.recordUncaughtBranchBadInstructionException.<init> */
  public static final RVMMethod recordUncaughtBranchMethod;

  static {
    psTref = TypeReference.findOrCreate(ProcessSpace.class);
    MethodReference sysCallMethRef = (MethodReference) MemberReference
        .findOrCreate(psTref, Atom.findOrCreateAsciiAtom("doSysCall"),
            Atom.findOrCreateAsciiAtom("()V"));
    sysCallMethod = sysCallMethRef.resolveInvokeSpecial();

    badInstrKlass = TypeReference.findOrCreate(BadInstructionException.class).resolve().asClass();

    MethodReference badInstrKlassInitMethRef = (MethodReference) MemberReference
        .findOrCreate(
            badInstrKlass.getTypeRef(),
            Atom.findOrCreateAsciiAtom("<init>"),
            Atom
                .findOrCreateAsciiAtom("(ILorg/binarytranslator/generic/os/process/ProcessSpace;)V"));
    badInstrKlassInitMethod = badInstrKlassInitMethRef.resolveInvokeSpecial();

    MethodReference recordUncaughtBranchMethRef = (MethodReference) MemberReference
        .findOrCreate(psTref, Atom
            .findOrCreateAsciiAtom("recordUncaughtBranch"), Atom
            .findOrCreateAsciiAtom("(IIII)V"));
    recordUncaughtBranchMethod = recordUncaughtBranchMethRef
        .resolveInvokeSpecial();
  }

  /** Number of translated instructions */
  private int numberOfInstructions;

  /** The process space object used by the running binary. */
  public final ProcessSpace ps;

  /** The VM method's generation context. */
  protected final GenerationContext gc;

  /** The BasicBlock in which instructions are currently being inserted */
  protected BasicBlock currentBlock;

  /** The pc value corresponding to the instruction currently being translated */
  protected int currentPC;

  /** The pc value of the first instruction in the trace */
  protected int startingPC;

  /** The BasicBlock which will contain the next translated instruction */
  protected BasicBlock nextBlock;

  /**
   * The basic block is used by finish trace to hold all the code that must be
   * executed before returning to the main run loop
   */
  protected BasicBlock finishBlock;

  /**
   * This block gets instructions to pre-fill registers inserted into it.
   */
  protected BasicBlock preFillBlock;
  
  /** 
   * This variable is being set by a call to {@link #printTraceAfterCompletion()} and notifies
   * the system that the current trace shall be printed, after it has been completed. */
  private boolean printTraceAfterCompletionRequested;

  /** Map to locate HIR basic blocks to re-use translation within a trace */
  protected final HashMap<Laziness.Key, BasicBlock> blockMap;

  /** This class stores information about a jump instruction within the current trace, whose
   * target has not yet been resolved. */
  protected final static class UnresolvedJumpInstruction {
    
    /** A reference to the jump instruction within the code. This is either a GOTO or SWITCH instruction. */
    public final Instruction instruction;

    /** The lazy state at the jump location. */
    public final Laziness lazyStateAtJump;
    
    /** Stores the target of the jump instruction or -1, if this value is unknown. */
    public final int targetPC;

    /** Stores the address, at which the jump occurs*/
    public final int pc;

    /** Identifies the type of branch. */
    public final BranchType type;

    public UnresolvedJumpInstruction(Instruction instruction,
        Laziness lazyStateAtJump, int pc, int targetPC, BranchType type) {
      this.instruction = instruction;
      this.lazyStateAtJump = lazyStateAtJump;
      this.pc = pc;
      this.type = type;
      this.targetPC = targetPC;
    }
  }

  /**
   * List of unresolved direct branches. The destinations of direct branches are
   * already known at translation time. */
  private final List<UnresolvedJumpInstruction> unresolvedDirectBranches;

  /**
   * List of unresolved dynamic branches. Dynamics branches have a destination
   * address that is only determined at runtime. */
  private final List<UnresolvedJumpInstruction> unresolvedDynamicBranches;

  /**
   * Constructor
   * 
   * @param context
   *          The JRVM generation context for this trace.
   */
  protected CodeTranslator(GenerationContext context, 
      DBT_Trace trace) {

    // Store the trace that we're invoked from
    this.trace = trace;

    // Make copies of popular variables
    gc = context;
    ps = ((DBT_Trace) (gc.method)).ps;
    currentPC = ((DBT_Trace) (gc.method)).pc;
    startingPC = currentPC;

    // Number of translated instructions
    numberOfInstructions = 0;

    // Create map of (PC & laziness) -> BasicBlock
    blockMap = new HashMap<Key, BasicBlock>();

    // Create preFillBlock, currentBlock and finishBlock
    gc.prologue.insertOut(gc.epilogue);
    preFillBlock = createBlockAfter(gc.prologue);
    currentBlock = createBlockAfter(preFillBlock);
    finishBlock = createBlockAfterCurrent();

    // Fix up stores
    unresolvedDirectBranches = new LinkedList<UnresolvedJumpInstruction>();
    unresolvedDynamicBranches = new LinkedList<UnresolvedJumpInstruction>();
  }

  /** Returns the number of previously translated instructions within this trace. */
  public int getNumInstructions() {
    return numberOfInstructions;
  }
  
  /**
   * Deriving classes may call this method if they want to print the current trace after all
   * dependencies (unknown branch targets etc.) have been resolved. This function is useful for
   * debug purposes.
   */
  public void printTraceAfterCompletion() {
    printTraceAfterCompletionRequested = true;
  }

  /** This is the main loop, which generates the HIR. */
  public void generateHIR() {
    // Load all the register values to be used in a trace
    preFillAllRegisters();

    // Translate from the current program counter
    translateSubTrace(createInitialLaziness(), currentPC);

    // Translating the subtrace finished so resolve any unresolved branches
    
    while (unresolvedDirectBranches.size() > 0 || unresolvedDynamicBranches.size() > 0) {
      // Resolve all open direct branches first
      do {
        resolveAllDirectBranches();
      }
      while (unresolvedDirectBranches.size() > 0);

      // Resolve unresolved dynamic jumps
      resolveAllDynamicBranches();
    }

    // Finish up the trace
    finishTrace();

    if (DBT_Options.eliminateRegisterFills) {
      eliminateRegisterFills(getUnusedRegisters());
    }
    
    if (printTraceAfterCompletionRequested) {
      printTraceAfterCompletionRequested = false;
      printNextBlocks(preFillBlock, 50);
    }
    
    try {
    ((DBT_Trace) gc.method).setNumberOfInstructions(numberOfInstructions);
    }
    catch (ClassCastException e) {
      System.err.println("Error casting " + gc.method + " to DBT_Trace.");
    }
  }


  /**
   * Translate a sequence of instructions upto an instruction that doesn't know
   * its immediate/default successor. The currentBlock should be an empty basic
   * block.
   */
  private void translateSubTrace(Laziness lazy, int pc) {
    currentPC = pc;
    
    if (shallTraceStop()) {
      // Record mapping of this pc value and laziness to this block
      registerMapping(pc, lazy, currentBlock);
      // Create next block
      nextBlock = createBlockAfterCurrent();
      // Finish block to return and exit
      appendTraceExit(lazy, new IntConstantOperand(pc));
      // Move currentBlock along
      currentBlock = nextBlock;
    } else {
      
      if (DBT_Options.debugTranslation)
        System.out.println("Translating subtrace for 0x" + Integer.toHexString(pc));
      
      do {
        if (DBT.VerifyAssertions)
          DBT._assert(currentBlock.getNumberOfRealInstructions() == 0);

        // Record mapping of this pc value and laziness to this block
        registerMapping(pc, lazy, currentBlock);

        // Create next block
        nextBlock = createBlockAfterCurrent();

        // Translare instruction and get address of next instruction
        pc = translateInstruction(lazy, pc);
        numberOfInstructions++;

        // Move currentBlock along
        currentBlock = nextBlock;
        currentPC = pc;

        if (DBT.VerifyAssertions)
          DBT._assert(currentBlock.getNumberOfRealInstructions() == 0);

        // Are we translating in single instruction mode
        if (DBT_Options.singleInstrTranslation == true) {
          if (pc != -1) {
            appendTraceExit(lazy, new IntConstantOperand(pc));
          }

          break;
        }

        // Do we already have a translation for this next block?
        BasicBlock possibleNextBlock = findMapping(pc, lazy);
        if (possibleNextBlock != null) {
          // Yes, branch to that and stop translating
          appendInstruction(Goto.create(GOTO, possibleNextBlock
              .makeJumpTarget()));
          BasicBlock gotoBlock = currentBlock;
          currentBlock = createBlockAfterCurrent();
          gotoBlock.deleteNormalOut();
          gotoBlock.insertOut(possibleNextBlock);
          if (DBT.VerifyAssertions)
            DBT._assert(gotoBlock.getNumberOfNormalOut() == 1);
          break;
        }
      } while (pc != -1);
      
      if (DBT_Options.debugTranslation)
        System.out.println("Done translating subtrace.");
    }
  }

  /**
   * Get the block which is currently having instructions inserted into it
   * 
   * @return the current block
   */
  public BasicBlock getCurrentBlock() {
    return currentBlock;
  }

  /**
   * Get the block which will contain the translation of the next PPC
   * instruction
   * 
   * @return the next block
   */
  public BasicBlock getNextBlock() {
    return nextBlock;
  }

  /**
   * Set the block which is currently having instructions inserted into it
   * 
   * @param newCurrentBlock
   *          the new current basic block
   */
  public void setCurrentBlock(BasicBlock newCurrentBlock) {
    currentBlock = newCurrentBlock;
  }

  /**
   * Set the block which will contain the translation of the next PPC
   * instruction
   * 
   * @param newCurrentBlock
   *          the new next basic block
   */
  public void setNextBlock(BasicBlock newNextBlock) {
    nextBlock = newNextBlock;
  }

  /**
   * Create a basic block immediately after the current block and link its edges
   * into the CFG and code ordering
   * 
   * @return the new basic block
   */
  public BasicBlock createBlockAfterCurrent() {
    BasicBlock nxtBlock = currentBlock.nextBasicBlockInCodeOrder();
    BasicBlock newBlock = new BasicBlock(0, gc.inlineSequence, gc.cfg);

    gc.cfg.breakCodeOrder(currentBlock, nxtBlock);
    gc.cfg.linkInCodeOrder(currentBlock, newBlock);
    gc.cfg.linkInCodeOrder(newBlock, nxtBlock);

    /*
     * if (DBT.VerifyAssertions) DBT._assert(currentBlock.isOut(nxtBlock));
     */

    if (currentBlock.isOut(nxtBlock)) {
      currentBlock.deleteOut(nxtBlock);
      currentBlock.insertOut(newBlock);
      newBlock.insertOut(nxtBlock);
    } else {
      currentBlock.insertOut(newBlock);
    }

    if (DBT_Options.debugCFG) {
      report(String.format("Created block (%s) after current (%s).", newBlock,
          currentBlock));
    }

    return newBlock;
  }

  /**
   * Create a basic block immediately after the current block and link its edges
   * into code ordering but not the CFG
   * 
   * @return the new basic block
   */
  public BasicBlock createBlockAfterCurrentNotInCFG() {
    BasicBlock nxtBlock = currentBlock.nextBasicBlockInCodeOrder();
    BasicBlock newBlock = new BasicBlock(0, gc.inlineSequence, gc.cfg);

    gc.cfg.breakCodeOrder(currentBlock, nxtBlock);
    gc.cfg.linkInCodeOrder(currentBlock, newBlock);
    gc.cfg.linkInCodeOrder(newBlock, nxtBlock);

    if (DBT_Options.debugCFG) {
      report(String.format("Created non-cfg block (%s) after current (%s).",
          newBlock, currentBlock));
    }
    return newBlock;
  }

  /**
   * Create a basic block immediately after the given block and link its edges
   * into the CFG and code ordering
   * 
   * @param afterBlock
   *          The block after which the new block is to be created.
   * @return the new basic block
   */
  public BasicBlock createBlockAfter(BasicBlock afterBlock) {
    BasicBlock nxtBlock = afterBlock.nextBasicBlockInCodeOrder();
    BasicBlock newBlock = new BasicBlock(0, gc.inlineSequence, gc.cfg);

    gc.cfg.breakCodeOrder(afterBlock, nxtBlock);
    gc.cfg.linkInCodeOrder(afterBlock, newBlock);
    gc.cfg.linkInCodeOrder(newBlock, nxtBlock);

    if (DBT.VerifyAssertions)
      DBT._assert(afterBlock.isOut(nxtBlock));
    afterBlock.deleteOut(nxtBlock);
    afterBlock.insertOut(newBlock);
    newBlock.insertOut(nxtBlock);

    if (DBT_Options.debugCFG) {
      report(String.format("Created block (%s) after current (%s).", newBlock,
          afterBlock));
    }

    return newBlock;
  }

  /**
   * Append a HIR instruction to the current basic block
   * 
   * @param i
   *          The instruciton that is to be appended to the current bloc.
   */
  public void appendInstruction(Instruction i) {
    if (i.bcIndex == UNKNOWN_BCI) {
      i.position = gc.inlineSequence;
      // we only have 16bits to distinguish instructions (the bcIndex
      // is effective 16bit when stored in the machine code map),
      // Intel can't distinguish branches within 16bytes, so neither
      // can we, the top bit is saved for distinguished addresses we
      // need to know to dynamically link things
      i.bcIndex = ((currentPC - startingPC) >> 4) & 0x7FFF;
    }
    currentBlock.appendInstruction(i);
  }

  /**
   * Generate a branch profile operand for the current instruction
   * 
   * @param likely
   *          Does this branch have a likely hint?
   */
  public BranchProfileOperand getConditionalBranchProfileOperand(
      boolean likely) {
    return gc.getConditionalBranchProfileOperand(
        ((currentPC - startingPC) >> 4) & 0x7FFF, likely);
  }

  /**
   * We're finished translating this trace so .. 1) put all the registers back
   * into the process space 2) return the new PC value
   */
  protected void finishTrace() {
    // finishBlock is already linked into the cfg, we just need to
    // make sure we are adding to it.
    currentBlock = finishBlock;
    spillAllRegisters();
  }

  /**
   * Get the generation context.
   */
  public final GenerationContext getGenerationContext() {
    return gc;
  }

  /**
   * Register a mapping between a pc and lazy and a hir block
   * 
   * @param pc
   *          The program counter whose translation the basic bock represents.
   * @param lazy
   *          The lazy state that is assumed for this basic block.
   * @param hirBlock
   *          The block that is to be registered.
   */
  protected final void registerMapping(int pc, Laziness lazy, BasicBlock hirBlock) {
    blockMap.put(lazy.makeKey(pc), hirBlock);
  }

  /**
   * Find if there's already a translation for a given pc and laziness
   * 
   * @param pc
   *          The program counter address at which the returned basic block
   *          shall start.
   * @param lazy
   *          The lazy state assumed within the returned trace.
   * @return An appropriate basic block or null if no translation exists.
   */
  protected final BasicBlock findMapping(int pc, Laziness lazy) {
    return blockMap.get(lazy.makeKey(pc));
  }

  /**
   * Create a HIR Goto instruction that jumps to the address
   * <code>targetPc</code>. There's a caveat on using this that there are no
   * other out edges for this BB.
   * 
   * @param targetPC
   *          The address where we shall jump to.
   * @param targetLaziness
   *          The current at the point of jump.
   */
  public void appendBranch(int targetPC, Laziness targetLaziness) {
        
    appendStaticBranch(Goto.create(GOTO, null), targetPC, targetLaziness, BranchType.DIRECT_BRANCH, -1);
  }
  
  /**
   * Appends the conditional branch instruction <code>conditional</code> that jumps to the address
   * <code>targetPc</code> to the current block. 
   * 
   * @param targetPC
   *          The address where we shall jump to.
   * @param targetLaziness
   *          The current at the point of jump.
   */
  public void appendConditionalBranch(Instruction conditional, int targetPC, Laziness targetLaziness) {
    
    if (DBT.VerifyAssertions) DBT._assert(IfCmp.conforms(conditional) || IfCmp2.conforms(conditional));
    appendStaticBranch(conditional, targetPC, targetLaziness, BranchType.DIRECT_BRANCH, -1);
  }
  
  /**
   * Create a HIR Goto instruction that jumps to the address
   * <code>targetPc</code>. There's a caveat on using this that there are no
   * other out edges for this BB.
   * 
   * @param targetPC
   *          The address where we shall jump to.
   * @param targetLaziness
   *          The current at the point of jump.
   * @param branchType
   *          The type of branch that best describes this jump.
   */
  public void appendCall(int targetPC, Laziness targetLaziness, int retAddr) {

    appendStaticBranch(Goto.create(GOTO, null), targetPC, targetLaziness, BranchType.CALL, retAddr);
  }

  private void appendStaticBranch(Instruction branch, int targetPC, Laziness targetLaziness, BranchType branchType, int retAddr) {
    // Place a GOTO instruction at this point. However, this instruction
    // serves more as a placeholder and might be mutated later on.
    appendInstruction(branch);
    UnresolvedJumpInstruction unresolvedJump = new UnresolvedJumpInstruction(
        branch, (Laziness) targetLaziness.clone(), currentPC, targetPC, branchType);
    unresolvedDirectBranches.add(unresolvedJump);
    
    switch (branchType) {
      case CALL:
        ps.branchInfo.registerCallSite(currentPC, targetPC, retAddr);
        break;
      
      case RETURN:
        ps.branchInfo.registerReturnSite(currentPC, targetPC);
        break;
    }    
  }
  
  /**
   * Append a dynamic jump (a jump whose target address is not known at translation time) to the 
   * current basic block.
   * 
   * @param targetAddress
   *  The target address of the jump.
   * @param lazyStateAtJump
   *  The lazy state at the point the jump was added.
   * @param retAddr
   *  The address, to which the function call will (most likely) return.
   */
  public void appendCall(RegisterOperand targetAddress, Laziness lazyStateAtJump, int retAddr) {
    
    appendDynamicBranch(targetAddress, lazyStateAtJump, BranchType.CALL, retAddr);
  }
  
  /**
   * Append a dynamic jump (a jump whose target address is not known at translation time) to the 
   * current basic block.
   * 
   * @param targetAddress
   *  The target address of the jump.
   * @param lazyStateAtJump
   *  The lazy state at the point the jump was added.
   * @param branchType
   *  The type of jump.
   */
  public void appendBranch(RegisterOperand targetAddress, Laziness lazyStateAtJump, BranchType branchType) {
    
    if (DBT.VerifyAssertions && branchType == BranchType.CALL) throw new RuntimeException("Use the more specific appendCall to create dynamic calls.");
    
    appendDynamicBranch(targetAddress, lazyStateAtJump, branchType, -1);
  }

  /**
   * Append a dynamic jump (a jump whose target address is not known at translation time) to the 
   * current basic block.
   * 
   * @param targetAddress
   *  The target address of the jump.
   * @param lazyStateAtJump
   *  The lazy state at the point the jump was added.
   * @param branchType
   *  The type of jump.
   * @param retAddr
   *  The address, to which the function call will (most likely) return or an arbitrary value, if
   *  branchType is not BranchType.CALL.
   */
  private void appendDynamicBranch(RegisterOperand targetAddress, Laziness lazyStateAtJump, BranchType branchType, int retAddr) {
    
    BasicBlock fallThrough = createBlockAfterCurrent();
    Instruction switchInstr;
    switchInstr = LookupSwitch.create(LOOKUPSWITCH, targetAddress.copyRO(), null, null, fallThrough.makeJumpTarget(), null, 0);
    appendInstruction(switchInstr);
    
    UnresolvedJumpInstruction unresolvedInfo = new UnresolvedJumpInstruction(switchInstr, (Laziness)lazyStateAtJump.clone(), currentPC, -1, branchType);
    unresolvedDynamicBranches.add(unresolvedInfo);

    setCurrentBlock(fallThrough);
    appendRecordUncaughtBranch(currentPC, targetAddress, branchType, retAddr);
    appendTraceExit((Laziness) lazyStateAtJump.clone(), targetAddress);
    
  }

  /** Resolve all unresolved direct branch instructions. */
  private void resolveAllDirectBranches() {

    while (unresolvedDirectBranches.size() > 0) {

      // Get the jump that we're supposed to resolve
      UnresolvedJumpInstruction unresolvedInstr = unresolvedDirectBranches.remove(0);
      int targetPc = unresolvedInstr.targetPC;
      Laziness lazyStateAtJump = unresolvedInstr.lazyStateAtJump;
      Instruction gotoInstr = unresolvedInstr.instruction;
      BasicBlock targetBB = resolveBranchTarget(targetPc, unresolvedInstr);

      if (DBT_Options.debugBranchResolution) {
        report("Resolved goto in block " + gotoInstr.getBasicBlock()
            + " to " + lazyStateAtJump.makeKey(targetPc) + " " + targetBB);
      }

      // Fix up instruction      
      setBranchTarget(gotoInstr, targetBB.makeJumpTarget());
      gotoInstr.getBasicBlock().insertOut(targetBB);
    }
  }
  
  /**
   * Sets the target of the branching instruction <code>branch</code> to <code>target</code>. 
   * @param branch
   *  A branching instruction. Either a Goto or  
   * @param target
   *  The jump target.
   */
  private void setBranchTarget(Instruction branch, BranchOperand target) {
    if (Goto.conforms(branch)) {
      Goto.setTarget(branch, target);
    }
    else if (IfCmp.conforms(branch)) {
      IfCmp.setTarget(branch, target);
    }
    else if (IfCmp2.conforms(branch)) {
      IfCmp2.setTarget1(branch, target);
    }
  }
  
  /**
   * This function is being called to decide whether a branch to <code>targetPc</code> (caused by the 
   * jump instruction <code>jump</code>) shall be inlined into the trace. This function is only called when
   * the jump target is not part of the current trace anyway.
   * 
   * This function may be overriden to fine-tune whether certain branches shall be inlined or not.
   * 
   * @param targetPc
   *  The address that we're jumping to.
   * @param jump
   *  Detailled information describing the jump.
   * @return
   *  True to make the system inline the target of this branch, false otherwise.
   */
  protected boolean inlineBranchInstruction(int targetPc, UnresolvedJumpInstruction jump) {
    /*
     * The target Block is not yet translated. We do not want to inline it
     * into the current trace if a) DBT_Options.singleInstrTranslation is
     * enabled b) The jump target has already been compiled as a separate method
     * within the code cache c) The trace is already too long d) the branch is
     * supposedly a CALL or RETURN
     */

    boolean decision = DBT_Options.singleInstrTranslation == false && !shallTraceStop();
    
    if (!decision) {
      
      if (DBT_Options.debugBranchResolution) {
        String text = (!decision ? "Not inlining " : "Inlining ");
        text += jump.type + " to 0x" + Integer.toHexString(targetPc); 
        System.out.println(text);
      }
      
      return false;
    }
    
    decision = jump.type == BranchType.DIRECT_BRANCH;
    
    if (!decision) {
      //only query the code cache if we have to
      DBT_Trace compiledTrace = ps.codeCache.tryGet(targetPc);
      decision = (compiledTrace != null && compiledTrace.getNumberOfInstructions() < 30);
    }
    
    if (DBT_Options.debugBranchResolution) {
      String text = (!decision ? "Not inlining " : "Inlining ");
      text += jump.type + " to 0x" + Integer.toHexString(targetPc); 
      System.out.println(text);
    }
    
    return decision;
  }

  /**
   * Resolves a branch target to an actual basic block. In case the jump target
   * is not yet part of this trace, this method also takes a decision about
   * whether the target shall be translated into the trace.
   * 
   * Notice that, when {@link DBT_Options#singleInstrTranslation} is turned on,
   * this method will always end the current trace, just returning the address
   * of the next instruction.
   * 
   * @param targetPc
   *          The address of the target basic block that.
   * @param jump
   *          The branch instruction that we are trying to resolve.
   * @return A basic block that is equivalent to the program counter address
   *         <code>targetPc</code> in the original binary.
   */
  private BasicBlock resolveBranchTarget(int targetPc, UnresolvedJumpInstruction jump) {
    // Resolve the address of the target block
    BasicBlock targetBB = findMapping(targetPc, jump.lazyStateAtJump);

    // If the target is already part of this trace, then just use the
    // precompiled target
    if (targetBB != null)
      return targetBB;
    
    if (currentBlock.getNumberOfRealInstructions() != 0) {
      currentBlock = createBlockAfterCurrentNotInCFG();
    } 
      
    if (!inlineBranchInstruction(targetPc, jump)) {

      //Just exit the trace and continue at the target address in a new trace
      targetBB = currentBlock;
      appendTraceExit(jump.lazyStateAtJump, new IntConstantOperand(targetPc));
      registerMapping(targetPc, jump.lazyStateAtJump, targetBB);
    } 
    else {
      // Otherwise we will translate the jump into the trace
      translateSubTrace((Laziness) jump.lazyStateAtJump.clone(), targetPc);
      targetBB = findMapping(targetPc, jump.lazyStateAtJump);
    }

    if (DBT.VerifyAssertions)
      DBT._assert(targetBB != null);

    return targetBB;
  }

  /**
   * Resolves all dynamic branches that have been added with
   * {@link #appendBranch(RegisterOperand, Laziness, BranchType)}.
   */
  private void resolveAllDynamicBranches() {

    while (unresolvedDynamicBranches.size() > 0) {

      UnresolvedJumpInstruction unresolvedSwitch = unresolvedDynamicBranches.remove(0);
      Set<Integer> branchDests = getLikelyJumpTargets(unresolvedSwitch.pc);

      resolveSingleDynamicJump(unresolvedSwitch, branchDests);
    }
  }

  /**
   * Resolves a single dynamic jump that has previously been created with
   * {@link #appendBranch(RegisterOperand, Laziness, BranchType)}.
   * 
   * @param lazy
   *          The lazy state of the jump that is to be resolved.
   * @param lookupswitch
   *          Each dynamic jump is converted to a switch statement. This is the
   *          switch statement for the current jump.
   * @param destinations
   *          A list of known destinations that this dynamic jumps branches to.
   */
  private void resolveSingleDynamicJump(
      UnresolvedJumpInstruction unresolvedJump, Set<Integer> destinations)
      throws Error {

    if (DBT.VerifyAssertions)
      DBT._assert(LookupSwitch.conforms(unresolvedJump.instruction));

    Instruction lookupswitch = unresolvedJump.instruction;
    BranchOperand default_target = LookupSwitch.getDefault(lookupswitch);
    Operand value = LookupSwitch.getValue(lookupswitch);

    if (destinations != null && destinations.size() > 0) {
      if ((destinations.size() > 1)
          || (lookupswitch.getBasicBlock().nextBasicBlockInCodeOrder() != default_target.target
              .getBasicBlock())) {
        float branchProb = (1.0f - BranchProfileOperand.UNLIKELY)
            / (float) destinations.size();
        LookupSwitch.mutate(lookupswitch, LOOKUPSWITCH, value, null, null,
            default_target, BranchProfileOperand.unlikely(), destinations
                .size() * 3);
        int match_no = 0;
        for (int dest_pc : destinations) {

          BasicBlock target = resolveBranchTarget(dest_pc, unresolvedJump);

          LookupSwitch.setMatch(lookupswitch, match_no,
              new IntConstantOperand(dest_pc));
          LookupSwitch.setTarget(lookupswitch, match_no, target
              .makeJumpTarget());
          LookupSwitch.setBranchProfile(lookupswitch, match_no,
              new BranchProfileOperand(branchProb));
          lookupswitch.getBasicBlock().insertOut(target);
          match_no++;
        }
      } else {
        int dest_pc = destinations.iterator().next();

        BasicBlock target = resolveBranchTarget(dest_pc, unresolvedJump);

        IfCmp.mutate(lookupswitch, INT_IFCMP, null, value,
            new IntConstantOperand(dest_pc), ConditionOperand.EQUAL(),
            target.makeJumpTarget(), BranchProfileOperand.likely());
        lookupswitch.getBasicBlock().insertOut(target);
      }
    } else {
      //we don't yet know where this jump will go, so just do nothing, which will exit the trace
    }
  }

  /**
   * Set the return value in the currentBlock, resolve its lazy state (so the
   * state is no longer lazy) and then set it to branch to the finish block
   * 
   * @param nextPc
   *          return value for translated code (the PC value of the next
   *          instruction to translate)
   */
  public void appendTraceExit(Laziness laziness, Operand nextPc) {

    // Copy the value into the register specified by gc.resultReg.
    appendInstruction(Move.create(INT_MOVE, new RegisterOperand(
        gc.resultReg, TypeReference.Int), nextPc.copy()));
    resolveLaziness((Laziness)laziness.clone());
    appendInstruction(Goto.create(GOTO, finishBlock.makeJumpTarget()));
    currentBlock.deleteNormalOut();
    currentBlock.insertOut(finishBlock);
    if (DBT.VerifyAssertions)
      DBT._assert(currentBlock.getNumberOfNormalOut() == 1);
  }

  /**
   * Should the trace be stopped as soon as possible? This function can be used
   * to steer how large a single trace may be. Return true if the target size
   * for the trace is about to be or has been exceeded.
   * 
   * @return true => try to stop the trace
   */
  protected boolean shallTraceStop() {
    if (DBT_Options.singleInstrTranslation && (numberOfInstructions >= 1)) {
      return true;
    }
    
    switch (gc.options.getOptLevel()) {
    case 0:
      return numberOfInstructions > DBT_Options.instrOpt0;
    case 1:
      return numberOfInstructions > DBT_Options.instrOpt1;
    default:
      return numberOfInstructions > DBT_Options.instrOpt2;
    }
  }

  /**
   * Register a branch and link instruction
   * 
   * @param pc
   *          the address of the branch instruction
   * @param ret
   *          the address returned to
   * @param dest
   *          the destination of the branch instruction
   */
  public void registerBranchAndLink(int pc, int ret, int dest) {
    ps.branchInfo.registerCallSite(pc, ret, dest);
  }

  /**
   * Returns a vector of likely branch targets for the branch at address
   * <code>pc</code>.
   * 
   * @param pc
   *          The location at which the branch occurs.
   * @return A set of likely destinations for that jump.
   */
  private Set<Integer> getLikelyJumpTargets(int pc) {
    return ps.branchInfo.getKnownBranchTargets(pc);
  }

  /**
   * Load all the registers from the ProcessSpace into the pre-fill block
   */
  private void preFillAllRegisters() {
    BasicBlock temp = currentBlock;
    currentBlock = preFillBlock;
    fillAllRegisters();
    ps.memory.initTranslate(this); // Set up memory
    currentBlock = temp;
  }

  /**
   * Eliminate unnecessary register spill and fill code - ie a register wasn't
   * used so eliminate references to it
   */
  protected void eliminateRegisterFills(Register unusedRegisters[]) {
    if (unusedRegisters.length > 0) {
      BasicBlock curBB = gc.prologue;
      while (curBB != null) {
        Instruction curInstr = curBB.firstInstruction();
        loop_over_instructions: while (BBend.conforms(curInstr) == false) {
          for (Enumeration du = curInstr.getRootOperands(); du
              .hasMoreElements();) {
            Operand curOp = (Operand) du.nextElement();
            if (curOp.isRegister()) {
              Register curReg = curOp.asRegister().register;
              for (int i = 0; i < unusedRegisters.length; i++) {
                if (unusedRegisters[i] == curReg) {
                  Instruction toRemove = curInstr;
                  curInstr = curInstr.nextInstructionInCodeOrder();
                  toRemove.remove();
                  continue loop_over_instructions;
                }
              }
            }
          }
          curInstr = curInstr.nextInstructionInCodeOrder();
        }
        curBB = curBB.nextBasicBlockInCodeOrder();
      }
    }
  }

  /**
   * Appends a system call to the current basic block.
   * 
   * @param lazy
   *          current translation laziness
   * @param pc
   *          address of system call instruction
   */
  public void appendSystemCall(Laziness lazy) {
    // We need to make sure that all registers contain their latest values,
    // before performing the actual system call.
    resolveLaziness(lazy);
    spillAllRegisters();

    // Plant call
    Instruction s = Call.create(CALL, null, null, null, null, 1);

    MethodOperand methOp = MethodOperand.VIRTUAL(sysCallMethod
        .getMemberRef().asMethodReference(), sysCallMethod);

    Operand psRef = gc.makeLocal(1, psTref);
    Call.setParam(s, 0, psRef); // Reference to ps, sets 'this' pointer for
    // doSysCall
    Call.setGuard(s, new TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s,
        new AddressConstantOperand(sysCallMethod.getOffset()));
    s.position = gc.inlineSequence;
    s.bcIndex = DBT_Trace.DO_SYSCALL;
    appendInstruction(s);
    // Fill all registers again following system call
    fillAllRegisters();
  }

  /**
   * Plant a throw of a bad instruction exception
   * 
   * @param lazy
   *          current translation laziness
   * @param pc
   *          the program counter of the bad instruction
   */
  public void appendThrowBadInstruction(Laziness lazy, int pc) {
    // We need to make sure that all registers contain their latest values,
    // before
    // throwing the bad instruction exception.
    resolveLaziness(lazy);
    spillAllRegisters();

    Operator newOperator;
    TypeOperand typeOperand = new TypeOperand(badInstrKlass);
    TypeReference eTref = badInstrKlass.getTypeRef();

    if (badInstrKlass.isInitialized() || badInstrKlass.isInBootImage()) {
      newOperator = NEW;
    } else {
      newOperator = NEW_UNRESOLVED;
    }

    RegisterOperand eRef = gc.temps.makeTemp(eTref);
    Instruction n = New.create(newOperator, eRef, typeOperand);
    n.position = gc.inlineSequence;
    n.bcIndex = DBT_Trace.BAD_INSTRUCTION_NEW;
    appendInstruction(n);

    Operand psRef = gc.makeLocal(1, psTref);
    Instruction c = Call.create(CALL, null, null, null, null, 3);
    MethodOperand methOp = MethodOperand.SPECIAL(
        badInstrKlassInitMethod.getMemberRef().asMethodReference(),
        badInstrKlassInitMethod);
    Call.setParam(c, 0, eRef.copy()); // 'this' pointer in
    // BadInstructionException.init
    Call.setParam(c, 1, new IntConstantOperand(pc));
    Call.setParam(c, 2, psRef);
    Call.setGuard(c, new TrueGuardOperand());
    Call.setMethod(c, methOp);
    Call.setAddress(c, new AddressConstantOperand(badInstrKlassInitMethod
        .getOffset()));
    c.position = gc.inlineSequence;
    c.bcIndex = DBT_Trace.BAD_INSTRUCTION_INIT;

    appendInstruction(c);

    Instruction t = Athrow.create(ATHROW, eRef.copyRO());
    t.position = gc.inlineSequence;
    t.bcIndex = DBT_Trace.BAD_INSTRUCTION_THROW;

    appendInstruction(t);

    appendTraceExit(lazy, new IntConstantOperand(0xEBADC0DE));
  }

  /**
   * Plant a record uncaught branch call. NB register state won't get resolved
   * for call
   * 
   * @param pc
   *  the address of the branch instruction
   * @param destination
   *  the register operand holding the destination
   * @param branchType
   *  The type of the uncaught branch
   * @param retAddr
   *  An optional return address, in case the branch is a call. Otherwise, this value is ignored.
   */
  private void appendRecordUncaughtBranch(int pc, RegisterOperand destination, BranchType branchType, int retAddr) {
    // Is it sensible to record this information?
    if ((gc.options.getOptLevel() > 0)
        && (DBT_Options.plantUncaughtBranchWatcher)) {
      // Plant call
      Instruction s = Call.create(CALL, null, null, null, null, 5);
      MethodOperand methOp = MethodOperand.VIRTUAL(
          recordUncaughtBranchMethod.getMemberRef().asMethodReference(),
          recordUncaughtBranchMethod);
      Operand psRef = gc.makeLocal(1, psTref);
      Call.setParam(s, 0, psRef); // Reference to ps, sets 'this' pointer
      Call.setParam(s, 1, new IntConstantOperand(pc)); // Address of branch
      // instruction
      Call.setParam(s, 2, destination.copy()); // Destination of branch value
      Call.setParam(s, 3, new IntConstantOperand(branchType.ordinal())); // Branch type
      Call.setParam(s, 4, new IntConstantOperand(retAddr)); // return address
      // value
      Call.setGuard(s, new TrueGuardOperand());
      Call.setMethod(s, methOp);
      Call.setAddress(s, new AddressConstantOperand(
          recordUncaughtBranchMethod.getOffset()));
      s.position = gc.inlineSequence;
      s.bcIndex = DBT_Trace.RECORD_BRANCH;
      appendInstruction(s);
    }
  }

  /** Temporary int variables */
  private Register intTemps[];

  /**
   * Get/create a temporary int
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public RegisterOperand getTempInt(int num) {
    if (DBT.VerifyAssertions) DBT._assert(num < 10);
    
    if (intTemps == null) {
      intTemps = new Register[10];
    }
    
    Register result = intTemps[num];
    if (result == null) {
      RegisterOperand regOp = gc.temps.makeTempInt();
      intTemps[num] = regOp.register;
      return regOp;
    } else {
      return new RegisterOperand(result, TypeReference.Int);
    }
  }

  /** Temporary long variables */
  private Register longTemps[];

  /**
   * Get/create a temporary long
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public RegisterOperand getTempLong(int num) {
    if (DBT.VerifyAssertions) DBT._assert(num < 10);
    
    if (longTemps == null) {
      longTemps = new Register[10];
    }
    
    Register result = longTemps[num];
    
    if (result == null) {
      RegisterOperand regOp = gc.temps.makeTempLong();
      longTemps[num] = regOp.register;
      return regOp;
    } else {
      return new RegisterOperand(result, TypeReference.Long);
    }
  }

  /** Temporary intArray variables */
  private Register intArrayTemp;

  /**
   * Get/create a temporary intArray
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public RegisterOperand getTempIntArray(int num) {
    if (DBT.VerifyAssertions)
      DBT._assert(num == 0);

    Register result = intArrayTemp;
    if (result == null) {
      RegisterOperand regOp = gc.temps.makeTemp(TypeReference.IntArray);
      intArrayTemp = regOp.register;
      return regOp;
    } else {
      return new RegisterOperand(result, TypeReference.IntArray);
    }
  }

  /** Temporary float variables */
  private Register floatTemps[];

  /**
   * Get/create a temporary float
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public RegisterOperand getTempFloat(int num) {
    if (DBT.VerifyAssertions)
      DBT._assert(num == 0 || num == 1);
    if (floatTemps == null) {
      floatTemps = new Register[2];
    }
    Register result = floatTemps[num];
    if (result == null) {
      RegisterOperand regOp = gc.temps.makeTempFloat();
      floatTemps[num] = regOp.register;
      return regOp;
    } else {
      return new RegisterOperand(result, TypeReference.Float);
    }
  }

  /** Temporary Double variables */
  private Register doubleTemp;

  /**
   * Get/create a temporary float
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public RegisterOperand getTempDouble(int num) {
    if (DBT.VerifyAssertions)
      DBT._assert(num == 0);
    
    Register result = doubleTemp;
    if (result == null) {
      RegisterOperand regOp = gc.temps.makeTempDouble();
      doubleTemp = regOp.register;
      return regOp;
    } else {
      return new RegisterOperand(result, TypeReference.Double);
    }
  }

  /** Temporary validation variables */
  private Register validationTemp;

  /**
   * Get/create a temporary validation variable
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public RegisterOperand getTempValidation(int num) {
    if (DBT.VerifyAssertions)
      DBT._assert(num == 0);

    Register result = validationTemp;
    if (result == null) {
      RegisterOperand regOp = gc.temps.makeTempValidation();
      validationTemp = regOp.register;
      return regOp;
    } else {
      return new RegisterOperand(result, TypeReference.VALIDATION_TYPE);
    }
  }

  /**
   * Inserts code into the current block that will use the process space's
   * interpreter to execute the given instructions.
   * 
   * @param pc
   *          The address of the instruction that shall be executed
   * 
   * @param lazy
   *          The current laziness state.
   */
  public void appendInterpretedInstruction(int pc, Laziness lazy) {
    
    appendThrowBadInstruction(lazy, pc);
    
/*    resolveLaziness(lazy);
    spillAllRegisters();

    // Prepare a local variable of type Interpreter
    TypeReference interpreterTypeRef = TypeReference
        .findOrCreate(Interpreter.class);
    RegisterOperand interpreter = gc.temps.makeTemp(interpreterTypeRef);

    // Plant a call to createInstructionInterpreter().
    Instruction s = Call.create(CALL, null, null, null, null, 1);

    MethodReference getInterpreterMethodRef = (MethodReference) MemberReference
        .findOrCreate(psTref, Atom
            .findOrCreateAsciiAtom("createInstructionInterpreter"), Atom
            .findOrCreateAsciiAtom("()Lorg.binarytranslator.generic.decoder.Interpreter;"));
    RVMMethod getInterpreterMethod = getInterpreterMethodRef
        .resolveInterfaceMethod();

    MethodOperand methOp = MethodOperand.INTERFACE(
        getInterpreterMethodRef, getInterpreterMethod);
    Operand psRef = gc.makeLocal(1, psTref);

    if (DBT.VerifyAssertions)
      DBT._assert(psRef != null && getInterpreterMethod != null
          && interpreter != null && methOp != null);

    Call.setParam(s, 0, psRef);
    Call.setGuard(s, new TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new AddressConstantOperand(getInterpreterMethod
        .getOffset()));
    Call.setResult(s, interpreter);
    
    appendCustomCall(s);

    // then use the returned instruction interpreter to interpret the
    // instruction
    TypeReference instructionTypeRef = TypeReference
        .findOrCreate(Interpreter.Instruction.class);
    RegisterOperand instruction = gc.temps.makeTemp(instructionTypeRef);

    s = Call.create(CALL, null, null, null, null, 1);

    MethodReference decodeMethodRef = (MethodReference) MemberReference
        .findOrCreate(interpreterTypeRef, Atom
            .findOrCreateAsciiAtom("decode"), Atom
            .findOrCreateAsciiAtom("(I)Lorg.binarytranslator.generic.decoder.Interpreter.Instruction;"));
    RVMMethod decodeMethod = decodeMethodRef.resolveInterfaceMethod();

    methOp = MethodOperand.INTERFACE(decodeMethodRef, decodeMethod);

    if (DBT.VerifyAssertions)
      DBT._assert(decodeMethod != null && methOp != null && instruction != null
          && interpreter != null);

    Call.setParam(s, 0, interpreter);
    Call.setGuard(s, new TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new AddressConstantOperand(decodeMethod.getOffset()));
    Call.setResult(s, instruction);

    appendCustomCall(s);

    // finally, call the execute method on the instruction
    MethodReference executeMethodRef = (MethodReference) MemberReference
        .findOrCreate(instructionTypeRef, Atom
            .findOrCreateAsciiAtom("execute"), Atom
            .findOrCreateAsciiAtom("()V"));
    RVMMethod executeMethod = executeMethodRef.resolveInterfaceMethod();

    s = Call.create(CALL, null, null, null, null, 1);
    methOp = MethodOperand.INTERFACE(executeMethodRef, executeMethod);

    if (DBT.VerifyAssertions)
      DBT._assert(executeMethod != null && methOp != null
          && instruction != null);

    Call.setParam(s, 0, instruction);
    Call.setGuard(s, new TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s,
        new AddressConstantOperand(executeMethod.getOffset()));

    appendCustomCall(s);

    // Fill all registers again following interpreted instruction
    fillAllRegisters();*/
  }

  /** Get the method */
  public DBT_Trace getMethod() {
    return (DBT_Trace) gc.method;
  }

  /** Make a temporary register */
  public RegisterOperand makeTemp(TypeReference type) {
    return gc.temps.makeTemp(type);
  }

  /**
   * Prints the given BasicBlock and the <code>count</code> blocks following
   * it in code order.
   * 
   * @param block
   *          The basic block that shall be printed.
   * @param count
   *          The number of blocks following <code>block</code> that shall be
   *          printed.
   */
  public void printNextBlocks(BasicBlock block, int count) {
    do {
      block.printExtended();
      block = block.nextBasicBlockInCodeOrder();
    } while (block != null && count-- > 0);
  }

  /**
   * Appends an call instruction the current trace.
   * 
   * @param callInstruction
   *          The call instruction that shall be added to the current block.
   */
  public void appendCustomCall(Instruction callInstruction) {
    if (DBT.VerifyAssertions)
      DBT._assert(Call.conforms(callInstruction));

    MethodOperand methOp = Call.getMethod(callInstruction);
    MethodReference methodRef = methOp.getMemberRef().asMethodReference();
    int callType;

    if (methOp.isVirtual())
      callType = BytecodeConstants.JBC_invokespecial;
    else if (methOp.isInterface())
      callType = BytecodeConstants.JBC_invokeinterface;
    else if (methOp.isSpecial())
      callType = BytecodeConstants.JBC_invokespecial;
    else
      throw new RuntimeException(
          "Unknown call type in call to appendCustomCall().");

    // append the instruction to the current block
    callInstruction.position = gc.inlineSequence;
    callInstruction.bcIndex = trace.registerDynamicLink(methodRef, callType);
    appendInstruction(callInstruction);
  }
  
  /**
   * Execute an inlining decision inlDec for the CALL instruction
   * callSite that is contained in ir.
   *
   * @param inlDec the inlining decision to execute
   * @param ir the governing IR
   * @param callSite the call site to inline
   */
  public void appendInlinedCall(Instruction callSite) {
    
    if (DBT.VerifyAssertions)
      DBT._assert(Call.conforms(callSite));
    
    BasicBlock next = createBlockAfterCurrent();
    
    //Find out where the call site is and isolate it in its own basic block. 
    currentBlock = createBlockAfterCurrent();
    currentBlock.appendInstruction(callSite);
    
    BasicBlock in = currentBlock.prevBasicBlockInCodeOrder();
    BasicBlock out = currentBlock.nextBasicBlockInCodeOrder();
    
    // Clear the sratch object of any register operands being
    // passed as parameters.
    // BC2IR uses this field for its own purposes, and will be confused
    // if the scratch object has been used by someone else and not cleared.
    for (int i = 0; i < Call.getNumberOfParams(callSite); i++) {
      Operand arg = Call.getParam(callSite, i);
      if (arg instanceof RegisterOperand) {
        ((RegisterOperand) arg).scratchObject = null;
      }
    }

    // Execute the inlining decision, updating ir.gc's state.
    InlineDecision inlDec = InlineDecision.YES(Call.getMethod(callSite).getTarget(), "");
    GenerationContext childgc = Inliner.execute(inlDec, gc, null, callSite);
    
    // Splice the callee into the caller's code order
    gc.cfg.removeFromCFGAndCodeOrder(currentBlock);
    gc.cfg.breakCodeOrder(in, out);
    gc.cfg.linkInCodeOrder(in, childgc.cfg.firstInCodeOrder());
    gc.cfg.linkInCodeOrder(childgc.cfg.lastInCodeOrder(), out);
    
    // Splice the callee into the caller's CFG
    in.insertOut(childgc.prologue);
    
    if (childgc.epilogue != null) {
      childgc.epilogue.insertOut(out);
    }
    
    currentBlock = next;
  }

  /** Report some debug output */
  protected abstract void report(String str);

  /** Create the initial object for capturing lazy information */
  protected abstract Laziness createInitialLaziness();

  /**
   * Plant instructions modifying a lazy state into one with no laziness
   * 
   * @param laziness
   *          the laziness to modify */
  public abstract void resolveLaziness(Laziness laziness);

  /**
   * Translate the instruction at the given pc
   * 
   * @param lazy
   *          the status of the lazy evaluation
   * @param pc
   *          the program counter for the instruction
   * @return the next instruction address or -1 */
  protected abstract int translateInstruction(Laziness lazy, int pc);

  /**
   * Fill all the registers from the ProcessSpace, that is take the register
   * values from the process space and place them in the traces registers. */
  protected abstract void fillAllRegisters();

  /**
   * Spill all the registers, that is put them from the current running trace
   * into the process space. */
  protected abstract void spillAllRegisters();

  /** Return an array of unused registers */
  protected abstract Register[] getUnusedRegisters();
}
