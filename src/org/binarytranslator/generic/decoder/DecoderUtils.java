/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.decoder;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.binarytranslator.vmInterface.DBT_Trace;
import org.binarytranslator.vmInterface.TranslationHelper;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.DBT_Options;

import org.jikesrvm.VM;

import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_BootstrapClassLoader;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_TypeReference;

import org.jikesrvm.opt.OPT_Constants;
import org.jikesrvm.opt.ir.OPT_GenerationContext;
import org.jikesrvm.opt.ir.OPT_HIRGenerator;
import org.jikesrvm.opt.ir.OPT_IR;
import org.jikesrvm.opt.ir.OPT_BasicBlock;

import org.jikesrvm.opt.ir.OPT_Instruction;
import org.jikesrvm.opt.ir.OPT_Operators;
import org.jikesrvm.opt.ir.OPT_Operator;
import org.jikesrvm.opt.ir.Athrow;
import org.jikesrvm.opt.ir.BBend;
import org.jikesrvm.opt.ir.Call;
import org.jikesrvm.opt.ir.CondMove;
import org.jikesrvm.opt.ir.Goto;
import org.jikesrvm.opt.ir.GetField;
import org.jikesrvm.opt.ir.IfCmp;
import org.jikesrvm.opt.ir.LookupSwitch;
import org.jikesrvm.opt.ir.Move;
import org.jikesrvm.opt.ir.New;
import org.jikesrvm.opt.ir.PutField;

import org.jikesrvm.opt.ir.OPT_Operand;
import org.jikesrvm.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.opt.ir.OPT_BranchOperand;
import org.jikesrvm.opt.ir.OPT_BranchProfileOperand;
import org.jikesrvm.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.opt.ir.OPT_MethodOperand;
import org.jikesrvm.opt.ir.OPT_Register;
import org.jikesrvm.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.opt.ir.OPT_TrueGuardOperand;
import org.jikesrvm.opt.ir.OPT_TypeOperand;

/**
 * A collection of common tools used by decoders. The public entry
 * point for the translators is generateHIR(OPT_GenerationContext gc).
 *
 * <dl><dt>Description of the translation algorithm</dt>
 *     <dd><ol>
 * <li>The translation is set up so that the start and end blocks are
 * created. The start block branches to a prefill block that loads all
 * the register values</li>
 * <li>Translation starts from the PC value given in the DBT_Trace.</li>
 * <li>Translation translates PPC instructions to HIR using the
 * translateInstruction method. This and the derived decoder object
 * contain the utilities required to aid an instruction
 * translation.</li>
 * <li>The trace ends when the branch prediction deems it
 * necessary. The closing basic blocks are generated and things are
 * wrapped up.</li>
 *     </ol></dd>
 * </dl>
 */
public abstract class DecoderUtils implements OPT_Constants, OPT_Operators, TranslationHelper {
    // -oO Constants Oo-

    /**
     * VM_TypeReference of
     * org.binarytranslator.generic.os.process.ProcessSpace
     */
    private static final VM_TypeReference psTref;

    /**
     * Method ProcessSpace.doSysCall
     */
    public static final VM_Method sysCallMethod;

    /**
     * VM_TypeReference of
     * org.binarytranslator.generic.fault.BadInstructionException
     */
    private static final VM_Class badInstrKlass;

    /**
     * Method BadInstructionException.<init>
     */
    public static final VM_Method badInstrKlassInitMethod;

    /**
     * Method ProcessSpace.recordUncaughtBranchBadInstructionException.<init>
     */
    public static final VM_Method recordUncaughtBranchMethod;

    static {
      psTref = java.lang.JikesRVMSupport.getTypeForClass(ProcessSpace.class).getTypeRef();
      VM_MethodReference sysCallMethRef = (VM_MethodReference)
	  VM_MemberReference.findOrCreate(psTref,
					  VM_Atom.findOrCreateAsciiAtom("doSysCall"),
					  VM_Atom.findOrCreateAsciiAtom("()V"));
      sysCallMethod = sysCallMethRef.resolveInvokeSpecial();
      
      badInstrKlass = java.lang.JikesRVMSupport.getTypeForClass(BadInstructionException.class).asClass();
      
      VM_MethodReference badInstrKlassInitMethRef = (VM_MethodReference)
	  VM_MemberReference.findOrCreate(badInstrKlass.getTypeRef(),
					  VM_Atom.findOrCreateAsciiAtom("<init>"),
					  VM_Atom.findOrCreateAsciiAtom("(I" +
									psTref.getName() +
									")V"));
      badInstrKlassInitMethod = badInstrKlassInitMethRef.resolveInvokeSpecial();

      VM_MethodReference recordUncaughtBranchMethRef = (VM_MethodReference)
	  VM_MemberReference.findOrCreate(psTref,
					  VM_Atom.findOrCreateAsciiAtom("recordUncaughtBranch"),
					  VM_Atom.findOrCreateAsciiAtom("(III)V"));
      recordUncaughtBranchMethod = recordUncaughtBranchMethRef.resolveInvokeSpecial();
    }


  // -oO Global IR Oo-

  /**
   * Number of translated instructions
   */
  public int numberOfInstructions;

  /**
   * The process space object used by the running PPC binary.
   */
  public ProcessSpace ps;

  /**
   * The generation context.
   */
  protected OPT_GenerationContext gc;

  // -oO Global HIR basic blocks Oo-

  /**
   * The OPT_BasicBlock in which instructions are currently being
   * inserted
   */
  protected OPT_BasicBlock currentBlock;

  /**
   * The pc value corresponding to the instruction currently being
   * translated
   */
  protected int currentPC;

  /**
   * The OPT_BasicBlock which will contain the next translated instruction
   */
  protected OPT_BasicBlock nextBlock;

  /** 
   * The basic block is used by finish trace to hold all the code that
   * must be executed before returning to the main run loop
   */
  protected OPT_BasicBlock finishBlock;

  /**
   * This block gets instructions to pre-fill registers inserted into
   * it.
   */
  protected OPT_BasicBlock preFillBlock;

  /** 
   * Map to locate HIR basic blocks to re-use translation within a
   * trace
   */
    protected final HashMap<Laziness.Key,OPT_BasicBlock> blockMap;

  /**
   * List of unresolved Goto instructions
   */
  protected final ArrayList<OPT_Instruction> unresolvedGoto;

  /**
   * List of where unresolved Goto instructions are trying to go
   */
  protected final ArrayList<Integer> unresolvedGoto_PC;
  /**
   * List of what the unresolved Goto instruction's target laziness should be
   */
  protected final ArrayList<Laziness> unresolvedGoto_Laziness;

  /**
   * List of unresolved IfCmp instructions
   */
  protected final ArrayList<OPT_Instruction> unresolvedIfCmp;
  /**
   * List of where unresolved IfCmp instructions are trying to go
   */
  protected final ArrayList<Integer> unresolvedIfCmp_PC;
  /**
   * List of what the unresolved IfCmp instruction's target laziness should be
   */
  protected final ArrayList<Laziness> unresolvedIfCmp_Laziness;

  /**
   * List of unresolved LookupSwitch instructions used for branching to the link register
   */
  protected final ArrayList<OPT_Instruction> unresolvedLookupSwitchForReturns;
  /**
   * List of where unresolved Goto instructions are trying to go
   */
  protected final ArrayList<Integer> unresolvedLookupSwitchForReturns_PC;
  /**
   * List of what the unresolved Goto instruction's target laziness should be
   */
  protected final ArrayList<Laziness> unresolvedLookupSwitchForReturns_Laziness;

  /**
   * List of unresolved LookupSwitch instructions used for branching to the count register
   */
  protected final ArrayList<OPT_Instruction> unresolvedLookupSwitchForSwitches;
  /**
   * List of where unresolved LookupSwitch instructions are branching from
   */
  protected final ArrayList<Integer> unresolvedLookupSwitchForSwitches_PC;
  /**
   * List of what the unresolved LookupSwitch instruction's target laziness should be
   */
  protected final ArrayList<Laziness> unresolvedLookupSwitchForSwitches_Laziness;
  /**
   * List of whether the unresolved LookupSwitch instruction's was a call
   */
  protected final ArrayList<Boolean> unresolvedLookupSwitchForSwitches_WasCall;

  // -oO Debug Oo-

  /**
   * Report some debug output
   */
  protected abstract void report(String str);

  // -oO Important entry points Oo-

  /**
   * Constructor
   * @param context the generation context for this trace
   */
  protected DecoderUtils(OPT_GenerationContext context) {
    // Make copies of popular variables 
    gc = context;
    ps = ((DBT_Trace)(gc.method)).ps;
    currentPC = ((DBT_Trace)(gc.method)).pc;

    // Number of translated instructions
    numberOfInstructions = 0;

    // Create map of (PC & laziness) -> OPT_BasicBlock
    blockMap = new HashMap();     

    // Create preFillBlock, currentBlock and finishBlock
    gc.prologue.insertOut(gc.epilogue);
    preFillBlock = createBlockAfter(gc.prologue);

    currentBlock = createBlockAfter(preFillBlock);

    finishBlock = createBlockAfterCurrent();

    // Fix up stores
    unresolvedGoto = new ArrayList<OPT_Instruction>();
    unresolvedGoto_PC = new ArrayList<Integer>();
    unresolvedGoto_Laziness = new ArrayList<Laziness>();

    unresolvedIfCmp = new ArrayList<OPT_Instruction>();
    unresolvedIfCmp_PC = new ArrayList<Integer>();
    unresolvedIfCmp_Laziness = new ArrayList<Laziness>();

    unresolvedLookupSwitchForReturns = new ArrayList<OPT_Instruction>();
    unresolvedLookupSwitchForReturns_PC = new ArrayList<Integer>();
    unresolvedLookupSwitchForReturns_Laziness = new ArrayList<Laziness>();

    unresolvedLookupSwitchForSwitches = new ArrayList<OPT_Instruction>();
    unresolvedLookupSwitchForSwitches_PC = new ArrayList<Integer>();
    unresolvedLookupSwitchForSwitches_Laziness = new ArrayList<Laziness>();
    unresolvedLookupSwitchForSwitches_WasCall = new ArrayList<Boolean>();
  }

  /** 
   * This is the main loop to generate the HIR.
   */
  public void generateHIR() {
    // Load all the register values to be used in a trace
    preFillAllRegisters();

    // Translate from the current program counter
    translateSubTrace(createInitialLaziness(), currentPC);

    // Translating the subtrace finished so resolve any unresolved
    // branches
    do {
      if (DBT_Options.resolveProceduresBeforeBranches) {
        prepareToResolveLookupSwitchForReturns();
        prepareToResolveLookupSwitchForSwitches();
      }
      if (DBT_Options.resolveBranchesAtOnce) {
        do {
          resolveGoto();
          resolveIfCmp();
        } while((areGotosResolved() && areIfCmpsResolved()) == false);
      }
      else {
        resolveGoto();
        resolveIfCmp();
      }
      if (!DBT_Options.resolveProceduresBeforeBranches) {
        prepareToResolveLookupSwitchForReturns();
        prepareToResolveLookupSwitchForSwitches();
      }
    } while((areGotosResolved() &&
             areIfCmpsResolved() &&
             areLookupSwitchForReturnsReadyToResolve()  &&
             areLookupSwitchForSwitchesReadyToResolve())
            == false);
    // Resolve unresolved branches and branches to ctr
    resolveLookupSwitchForReturns();
    resolveLookupSwitchForSwitches();

    // Finish up the trace
    finishTrace();

    if (DBT_Options.eliminateRegisterFills) {
      eliminateRegisterFills(getUnusedRegisters());
    }
    // TODO: maximizeBasicBlocks()
  }

  // -oO Translate sequences of code Oo-

  /**
   * Translate a sequence of instructions upto an instruction that
   * doesn't know its immediate/default successor. The currentBlock
   * should be an empty basic block.
   */
  private void translateSubTrace(Laziness lazy, int pc) {
    currentPC = pc;
    if(suitableToStop()) {
      // Record mapping of this pc value and laziness to this block
      registerMapping(pc, lazy, currentBlock);
      // Create next block
      nextBlock = createBlockAfterCurrent();
      // Finish block to return and exit
      setReturnValueResolveLazinessAndBranchToFinish(lazy, new OPT_IntConstantOperand(pc));
      // Move currentBlock along
      currentBlock = nextBlock;
    }
    else {
      do {
        if (VM.VerifyAssertions) VM._assert(currentBlock.getNumberOfRealInstructions() == 0);
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
        if (VM.VerifyAssertions) VM._assert(currentBlock.getNumberOfRealInstructions() == 0);

        // Are we translating in single instruction mode
        if (DBT_Options.singleInstrTranslation == true) {
          if (pc != -1) {
            setReturnValueResolveLazinessAndBranchToFinish(lazy, new OPT_IntConstantOperand(pc));
          }
          break;
        }

        // Do we already have a translation for this next block?
        OPT_BasicBlock possibleNextBlock = findMapping(pc, lazy);
        if(possibleNextBlock != null) {
          // Yes, branch to that and stop translating
          appendInstructionToCurrentBlock(Goto.create(GOTO, possibleNextBlock.makeJumpTarget()));
          OPT_BasicBlock gotoBlock = currentBlock;
          currentBlock = createBlockAfterCurrent();
          gotoBlock.deleteNormalOut();
          gotoBlock.insertOut(possibleNextBlock);
          if (VM.VerifyAssertions) VM._assert(gotoBlock.getNumberOfNormalOut() == 1);
          break;
        }
      } while (pc != -1);
    }
  }

  /**
   * Translate the instruction at the given pc
   * @param lazy the status of the lazy evaluation
   * @param pc the program counter for the instruction
   * @return the next instruction address or -1
   */
  protected abstract int translateInstruction(Laziness lazy, int pc);

  /**
   * Get the block which is currently having instructions inserted
   * into it
   * @return the current block
   */
  public OPT_BasicBlock getCurrentBlock() {
    return currentBlock;
  }

  /**
   * Get the block which will contain the translation of the next PPC
   * instruction
   * @return the next block
   */
  public OPT_BasicBlock getNextBlock() {
    return nextBlock;
  }

  /**
   * Set the block which is currently having instructions inserted into it
   * @param newCurrentBlock the new current basic block
   */
  public void setCurrentBlock(OPT_BasicBlock newCurrentBlock) {
    currentBlock = newCurrentBlock;
  }

  /**
   * Set the block which will contain the translation of the next PPC
   * instruction
   * @param newCurrentBlock the new next basic block
   */
  public void setNextBlock(OPT_BasicBlock newNextBlock) {
    nextBlock = newNextBlock;
  }
  
  /**
   * Create a basic block immediately after the current block and link
   * its edges into the CFG and code ordering
   * @return the new basic block
   */
  public OPT_BasicBlock createBlockAfterCurrent()
  {
    OPT_BasicBlock nxtBlock = currentBlock.nextBasicBlockInCodeOrder();
    OPT_BasicBlock newBlock  = new OPT_BasicBlock(0, gc.inlineSequence, gc.cfg);

    gc.cfg.breakCodeOrder(currentBlock, nxtBlock);
    gc.cfg.linkInCodeOrder(currentBlock, newBlock);
    gc.cfg.linkInCodeOrder(newBlock, nxtBlock);

    if (VM.VerifyAssertions) VM._assert(currentBlock.isOut(nxtBlock));
    currentBlock.deleteOut(nxtBlock);
    currentBlock.insertOut(newBlock);
    newBlock.insertOut(nxtBlock);

    if(DBT_Options.debugCFG) {
      report("Created block (" + newBlock + ") after current (" + currentBlock+").");
    }
    return newBlock;
  }

  /**
   * Create a basic block immediately after the current block and link
   * its edges into code ordering but not the CFG
   * @return the new basic block
   */
  public OPT_BasicBlock createBlockAfterCurrentNotInCFG()
  {
    OPT_BasicBlock nxtBlock = currentBlock.nextBasicBlockInCodeOrder();
    OPT_BasicBlock newBlock  = new OPT_BasicBlock(0, gc.inlineSequence, gc.cfg);

    gc.cfg.breakCodeOrder(currentBlock, nxtBlock);
    gc.cfg.linkInCodeOrder(currentBlock, newBlock);
    gc.cfg.linkInCodeOrder(newBlock, nxtBlock);

    if(DBT_Options.debugCFG) {
      report("Created block (" + newBlock + ") after current (" + currentBlock+") but not in CFG.");
    }
    return newBlock;
  }

  /**
   * Create a basic block immediately after the given block and link
   * its edges into the CFG and code ordering
   * @param afterBlock the block to create a block after
   * @return the new basic block
   */
  public OPT_BasicBlock createBlockAfter(OPT_BasicBlock afterBlock)
  {
    OPT_BasicBlock nxtBlock = afterBlock.nextBasicBlockInCodeOrder();
    OPT_BasicBlock newBlock  = new OPT_BasicBlock(0, gc.inlineSequence, gc.cfg);

    gc.cfg.breakCodeOrder(afterBlock, nxtBlock);
    gc.cfg.linkInCodeOrder(afterBlock, newBlock);
    gc.cfg.linkInCodeOrder(newBlock, nxtBlock);

    if (VM.VerifyAssertions) VM._assert(afterBlock.isOut(nxtBlock));
    afterBlock.deleteOut(nxtBlock);
    afterBlock.insertOut(newBlock);
    newBlock.insertOut(nxtBlock);

    if(DBT_Options.debugCFG) {
      report("Created block (" + newBlock + ") after (" + afterBlock+").");
    }
    return newBlock;
  }

  /**
   * Append a HIR instruction to the current basic block
   * @param i the HIR instruction
   */
  public void appendInstructionToCurrentBlock(OPT_Instruction i) {
    if(i.bcIndex == UNKNOWN_BCI) {
      i.position = gc.inlineSequence;
      // we only have 16bits to distinguish instructions (the bcIndex
      // is effective 16bit when stored in the machine code map),
      // Intel can't distinguish branches within 16bytes, so neither
      // can we, the top bit is saved for distinguished addresses we
      // need to know to dynamically link things
      i.bcIndex = (currentPC >> 4) & 0x7FFF;
    }
    currentBlock.appendInstruction(i);
  }

  /**
   * Generate a branch profile operand for the current instruction
   * @param likely does this branch have a likely hint
   */
  public OPT_BranchProfileOperand getConditionalBranchProfileOperand(boolean likely) {
    return gc.getConditionalBranchProfileOperand((currentPC >> 2) & 0xFFFF, likely);
  }


  /**
   * We're finished translating this trace so ..
   * 1) put all the registers back into the process space
   * 2) return the new PC value
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
  public OPT_GenerationContext getGenerationContext() {
    return gc;
  }

  // -oO Laziness Oo-

  /**
   * Create the initial object for capturing lazy information
   */
  protected abstract Laziness createInitialLaziness();
  /**
   * Plant instructions modifying a lazy state into one with no
   * laziness
   * @param laziness the laziness to modify
   */
  public abstract void resolveLaziness(Laziness laziness);

  /**
   * Register a mapping between a pc and lazy and a hir block
   * @param pc
   * @param lazy
   * @param hirBlock
   */
  protected void registerMapping(int pc, Laziness lazy, OPT_BasicBlock hirBlock) {
    blockMap.put(lazy.makeKey(pc), hirBlock);
  }
  /**
   * Find if there's already a translation for a given pc and laziness
   * @param pc
   * @param lazy
   * @return basic block or null if no translation exists
   */
  public OPT_BasicBlock findMapping(int pc, Laziness lazy) {
    return blockMap.get(lazy.makeKey(pc));
  }

  /**
   * Register a HIR IfCmp instruction whose target can't be resolved yet
   * @param ifcmp_instr
   * @param targetPC
   * @param targetLaziness
   */
  public void registerIfCmpTargetUnresolved(OPT_Instruction ifcmp_instr, int targetPC, Laziness targetLaziness){
    unresolvedIfCmp.add(ifcmp_instr);
    unresolvedIfCmp_PC.add(targetPC);
    unresolvedIfCmp_Laziness.add(targetLaziness);
  }

  /**
   * Are all IfCmp instructions resolved
   * @return true if we need to resolve IfCmps
   */
  private boolean areIfCmpsResolved() {
    return unresolvedIfCmp.size() == 0;
  }
  /**
   * Resolve an ifCmp instruction
   */
  private void resolveIfCmp() {
    if (areIfCmpsResolved() == false) {
      // Try to find if block has now been translated
      int toRemove = unresolvedIfCmp.size() - 1;
      int pc = ((Integer)unresolvedIfCmp_PC.get(toRemove)).intValue();
      Laziness lazy = (Laziness)unresolvedIfCmp_Laziness.get(toRemove);
      OPT_BasicBlock targetBB = findMapping(pc, lazy);
      if (targetBB == null) {
        // Block not translated so translate
        translateSubTrace(lazy, pc);
        targetBB = findMapping(pc, lazy);
      }
      // Fix up instruction
      OPT_Instruction ifCmp = (OPT_Instruction)unresolvedIfCmp.get(toRemove);
      IfCmp.setTarget(ifCmp, targetBB.makeJumpTarget());
      ifCmp.getBasicBlock().insertOut(targetBB);
      // Remove from unresolved list
      unresolvedIfCmp.remove(toRemove);
      unresolvedIfCmp_PC.remove(toRemove);
      unresolvedIfCmp_Laziness.remove(toRemove);
    }
  }

  /**
   * Register a HIR Goto instruction whose target can't be resolved
   * yet. There's a caveat on using this that there are no other out
   * edges for this BB.
   * @param goto_instr
   * @param targetPC
   * @param targetLaziness
   */
  public void registerGotoTargetUnresolved(OPT_Instruction goto_instr, int targetPC, Laziness targetLaziness){
    unresolvedGoto.add(goto_instr);
    unresolvedGoto_PC.add(targetPC);
    unresolvedGoto_Laziness.add(targetLaziness);
  }

  /**
   * Are all Goto instructions resolved
   * @return true if we need to resolve Gotos
   */
  private boolean areGotosResolved() {
    return unresolvedGoto.size() == 0;
  }
  /**
   * Resolve a goto instruction
   */
  private void resolveGoto() {
    if (areGotosResolved() == false) {
      // Try to find if block has now been translated
      int toRemove = unresolvedGoto.size() - 1;
      int pc = ((Integer)unresolvedGoto_PC.get(toRemove)).intValue();
      Laziness lazy = (Laziness)unresolvedGoto_Laziness.get(toRemove);
      OPT_BasicBlock targetBB;
      if (DBT_Options.singleInstrTranslation == false) {
        targetBB = findMapping(pc, lazy);
      }
      else {
        // Single instruction mode so resolve gotos to a block that resolves the lazy state and exits
        if (currentBlock.getNumberOfRealInstructions() != 0) {
          currentBlock = createBlockAfterCurrentNotInCFG();
        }
        targetBB = currentBlock;
        setReturnValueResolveLazinessAndBranchToFinish(lazy, new OPT_IntConstantOperand(pc));
      }
      if (DBT_Options.debugLazy) {
        VM.sysWriteln("Resolving goto " + lazy.makeKey(pc) + " " + targetBB);
      }
      if (targetBB == null) {
        // Block not translated so translate
        translateSubTrace((Laziness)lazy.clone(), pc);
        targetBB = findMapping(pc, lazy);
      }
      // Fix up instruction
      OPT_Instruction goto_inst = (OPT_Instruction)unresolvedGoto.get(toRemove);
      Goto.setTarget(goto_inst, targetBB.makeJumpTarget());
      if (DBT_Options.debugLazy) {
        VM.sysWriteln("Properly resolving goto in block " + goto_inst.getBasicBlock() + " to " + lazy.makeKey(pc) + " " + targetBB);
      }
      goto_inst.getBasicBlock().deleteNormalOut();
      goto_inst.getBasicBlock().insertOut(targetBB);
      if (VM.VerifyAssertions) VM._assert(goto_inst.getBasicBlock().getNumberOfNormalOut() == 1);
      // Remove from unresolved list
      unresolvedGoto.remove(toRemove);
      unresolvedGoto_PC.remove(toRemove);
      unresolvedGoto_Laziness.remove(toRemove);
    }
  }

  /**
   * Register a HIR LookupSwitch instruction whose target can't be
   * resolved yet. There's a caveat on using this that there are no
   * other out edges for this BB.
   * @param lookupswitch_instr
   * @param pc the PC of the branch to link register
   * @param targetLaziness
   */
  public void registerLookupSwitchForReturnUnresolved(OPT_Instruction lookupswitch_instr, int pc, Laziness targetLaziness){
    unresolvedLookupSwitchForReturns.add(lookupswitch_instr);
    unresolvedLookupSwitchForReturns_PC.add(pc);
    unresolvedLookupSwitchForReturns_Laziness.add(targetLaziness);
  }
  /**
   * Are all LookupSwitch instructions for branches to LR ready to be
   * resolved
   * @return true if all are ready to be resolved
   */
  private boolean areLookupSwitchForReturnsReadyToResolve() {
    for(int i=0; i < unresolvedLookupSwitchForReturns_PC.size(); i++) {
      int pc = ((Integer)unresolvedLookupSwitchForReturns_PC.get(i)).intValue();
      Laziness lazy = (Laziness)unresolvedLookupSwitchForReturns_Laziness.get(i);
      Set branchDests = getLikelyCallSites(pc);
      if(branchDests != null) {
        for(Iterator j=branchDests.iterator(); j.hasNext();) {
          int dest_pc = ((Integer)j.next()).intValue() + 4;
          if(findMapping(dest_pc, lazy) == null) {
            return false;
          }
        }
      }
    }
    return true;
  }
  /**
   * Create sub-traces that will allow for the resolution of all
   * lookup switchs for branch to lr
   */
  private void prepareToResolveLookupSwitchForReturns() {
    for(int i=0; i < unresolvedLookupSwitchForReturns_PC.size(); i++) {
      int pc = ((Integer)unresolvedLookupSwitchForReturns_PC.get(i)).intValue();
      Laziness lazy = (Laziness)unresolvedLookupSwitchForReturns_Laziness.get(i);
      Set branchDests = (Set)getLikelyCallSites(pc);
      if (branchDests != null) {
        branchDests = (Set)((HashSet)getLikelyCallSites(pc)).clone();
        for(Iterator j=branchDests.iterator(); j.hasNext();) {
          int dest_pc = ((Integer)j.next()).intValue() + 4;
          if(findMapping(dest_pc, lazy) == null) {
            // Block not translated so translate
            translateSubTrace((Laziness)lazy.clone(), dest_pc);
          }
        }
      }
    }
  }

  /**
   * Resolve all lookup switch instructions for branch to lr
   */
  private void resolveLookupSwitchForReturns() {
    for(int i=0; i < unresolvedLookupSwitchForReturns_PC.size(); i++) {
      int pc = ((Integer)unresolvedLookupSwitchForReturns_PC.get(i)).intValue();
      Laziness lazy = (Laziness)unresolvedLookupSwitchForReturns_Laziness.get(i);
      OPT_Instruction lookupswitch = (OPT_Instruction)unresolvedLookupSwitchForReturns.get(i);
      OPT_BranchOperand default_target = LookupSwitch.getDefault(lookupswitch);
      OPT_Operand value = LookupSwitch.getValue(lookupswitch);
      Set branchDests = getLikelyCallSites(pc);
      if (branchDests != null) {
        if ((branchDests.size() > 1)||(lookupswitch.getBasicBlock().nextBasicBlockInCodeOrder() != default_target.target.getBasicBlock())) {
          float branchProb = (1.0f - OPT_BranchProfileOperand.UNLIKELY) / (float)branchDests.size();
          LookupSwitch.mutate(lookupswitch, LOOKUPSWITCH, value, null, null,
                              default_target, OPT_BranchProfileOperand.unlikely(),
                              branchDests.size() * 3);
          int match_no = 0;
          for(Iterator j=branchDests.iterator(); j.hasNext();) {
            int dest_pc = ((Integer)j.next()).intValue() + 4;
            OPT_BasicBlock target = findMapping(dest_pc, lazy);
            if(target == null) {
              throw new Error("Failed to find trace for " + dest_pc + " with laziness " + lazy);
            }
            LookupSwitch.setMatch(lookupswitch, match_no, new OPT_IntConstantOperand(dest_pc));
            LookupSwitch.setTarget(lookupswitch, match_no, target.makeJumpTarget());
            LookupSwitch.setBranchProfile(lookupswitch, match_no, new OPT_BranchProfileOperand(branchProb)) ;
            lookupswitch.getBasicBlock().insertOut(target);
            match_no++;
          }
        }
        else {
          int dest_pc = ((Integer)branchDests.iterator().next()).intValue() + 4;
          OPT_BasicBlock target = findMapping(dest_pc, lazy);
          if(target == null) {
            throw new Error("Failed to find trace for " + dest_pc + " with laziness " + lazy);
          }
          IfCmp.mutate(lookupswitch, INT_IFCMP, null,
                       value, new OPT_IntConstantOperand(dest_pc), OPT_ConditionOperand.EQUAL(),
                       target.makeJumpTarget(),
                       OPT_BranchProfileOperand.likely());
          lookupswitch.getBasicBlock().insertOut(target);
        }
      }
      else {
        // no possibly branch destinations so remove lookupswitch
        lookupswitch.remove();
      }
    }
  }

  /**
   * Register a HIR LookupSwitch instruction whose target can't be
   * resolved yet. There's a caveat on using this that there are no
   * other out edges for this BB.
   * @param lookupswitch_instr
   * @param pc the PC of the branch to count register
   * @param targetLaziness
   */
  public void registerLookupSwitchForSwitchUnresolved(OPT_Instruction lookupswitch_instr, int pc, Laziness targetLaziness, boolean link){
    unresolvedLookupSwitchForSwitches.add(lookupswitch_instr);
    unresolvedLookupSwitchForSwitches_PC.add(pc);
    unresolvedLookupSwitchForSwitches_Laziness.add(targetLaziness);
    unresolvedLookupSwitchForSwitches_WasCall.add(link);
  }
  /**
   * Are all LookupSwitch instructions for branches to CTR ready to be
   * resolved
   * @return true if all are ready to be resolved
   */
  private boolean areLookupSwitchForSwitchesReadyToResolve() {
    for(int i=0; i < unresolvedLookupSwitchForSwitches_PC.size(); i++) {
      int pc = ((Integer)unresolvedLookupSwitchForSwitches_PC.get(i)).intValue();
      Laziness lazy = (Laziness)unresolvedLookupSwitchForSwitches_Laziness.get(i);
      Set branchDests = getLikelyCtrDestinations(pc);
      if(branchDests != null) {
        for(Iterator j=branchDests.iterator(); j.hasNext();) {
          int dest_pc = ((Integer)j.next()).intValue();
          if(findMapping(dest_pc, lazy) == null) {
            return false;
          }
        }
      }
    }
    return true;
  }
  /**
   * Create sub-traces that will allow for the resolution of all
   * lookup switchs for branch to ctr
   */
  private void prepareToResolveLookupSwitchForSwitches() {
    for(int i=0; i < unresolvedLookupSwitchForSwitches_PC.size(); i++) {
      int pc = ((Integer)unresolvedLookupSwitchForSwitches_PC.get(i)).intValue();
      Laziness lazy = (Laziness)unresolvedLookupSwitchForSwitches_Laziness.get(i);
      Set branchDests = (Set)getLikelyCtrDestinations(pc);
      if (branchDests != null) {
        branchDests = (Set)((HashSet)getLikelyCtrDestinations(pc)).clone();
        for(Iterator j=branchDests.iterator(); j.hasNext();) {
          int dest_pc = ((Integer)j.next()).intValue();
          if(((Boolean)unresolvedLookupSwitchForSwitches_WasCall.get(i)).booleanValue()){
            throw new Error("TODO: we need pc, return address and dest_pc here");
            // registerBranchAndLink(pc, dest_pc);
          }
          if(findMapping(dest_pc, lazy) == null) {
            // Block not translated so translate
            translateSubTrace((Laziness)lazy.clone(), dest_pc);
          }
        }
      }
    }
  }

  /**
   * Resolve all lookup switch instructions for branch to ctr
   */
  private void resolveLookupSwitchForSwitches() {
    for(int i=0; i < unresolvedLookupSwitchForSwitches_PC.size(); i++) {
      int pc = ((Integer)unresolvedLookupSwitchForSwitches_PC.get(i)).intValue();
      Laziness lazy = (Laziness)unresolvedLookupSwitchForSwitches_Laziness.get(i);
      OPT_Instruction lookupswitch = (OPT_Instruction)unresolvedLookupSwitchForSwitches.get(i);
      OPT_BranchOperand default_target = LookupSwitch.getDefault(lookupswitch);
      OPT_Operand value = LookupSwitch.getValue(lookupswitch);
      Set branchDests = getLikelyCtrDestinations(pc);
      if (branchDests != null) {
        if ((branchDests.size() > 1)||(lookupswitch.getBasicBlock().nextBasicBlockInCodeOrder() != default_target.target.getBasicBlock())) {
          float branchProb = (1.0f - OPT_BranchProfileOperand.UNLIKELY) / (float)branchDests.size();
          LookupSwitch.mutate(lookupswitch, LOOKUPSWITCH, value, null, null,
                              default_target, OPT_BranchProfileOperand.unlikely(),
                              branchDests.size() * 3);
          int match_no = 0;
          for(Iterator j=branchDests.iterator(); j.hasNext();) {
            int dest_pc = ((Integer)j.next()).intValue();
            OPT_BasicBlock target = findMapping(dest_pc, lazy);
            if(target == null) {
              throw new Error("Failed to find trace for " + dest_pc + " with laziness " + lazy);
            }
            LookupSwitch.setMatch(lookupswitch, match_no, new OPT_IntConstantOperand(dest_pc));
            LookupSwitch.setTarget(lookupswitch, match_no, target.makeJumpTarget());
            LookupSwitch.setBranchProfile(lookupswitch, match_no, new OPT_BranchProfileOperand(branchProb)) ;
            lookupswitch.getBasicBlock().insertOut(target);
            match_no++;
          }
        }
        else {
          int dest_pc = ((Integer)branchDests.iterator().next()).intValue();
          OPT_BasicBlock target = findMapping(dest_pc, lazy);
          if(target == null) {
            throw new Error("Failed to find trace for " + dest_pc + " with laziness " + lazy);
          }
          IfCmp.mutate(lookupswitch, INT_IFCMP, null,
                       value, new OPT_IntConstantOperand(dest_pc), OPT_ConditionOperand.EQUAL(),
                       target.makeJumpTarget(),
                       OPT_BranchProfileOperand.likely());
          lookupswitch.getBasicBlock().insertOut(target);
        }
      }
      else {
        // no possibly branch destinations so remove lookupswitch
        lookupswitch.remove();
      }
    }
  }

  /**
   * Set the return value in the currentBlock, resolve its lazy state
   * (so the state is no longer lazy) and then set it to branch to the
   * finish block
   * @param value return value for translated code (the PC value of
   * the next instruction to translate)
   */
  public void setReturnValueResolveLazinessAndBranchToFinish(Laziness laziness, OPT_Operand value) {
    nextBlock = createBlockAfterCurrent();
    // Copy the value into the register specified by gc.resultReg.
    appendInstructionToCurrentBlock(Move.create(INT_MOVE,
                                                new OPT_RegisterOperand(gc.resultReg, VM_TypeReference.Int),
                                                value));
    resolveLaziness(laziness);
    appendInstructionToCurrentBlock(Goto.create(GOTO, finishBlock.makeJumpTarget()));
    currentBlock.deleteNormalOut();
    currentBlock.insertOut(finishBlock);
    if (VM.VerifyAssertions) VM._assert(currentBlock.getNumberOfNormalOut() == 1);
    currentBlock = nextBlock;
  }

  // -oO Trace control Oo-

  /**
   * Is it suitable to stop the trace now?
   * @return true => try to stop the trace
   */
  protected boolean suitableToStop() {
    switch(gc.options.getOptLevel()) {
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
   * @param pc the address of the branch instruction
   * @param ret the address returned to
   * @param dest the destination of the branch instruction
   */
  public void registerBranchAndLink(int pc, int ret, int dest) {
    ps.branchInfo.registerCall(pc, ret, dest);
  }

  /**
   * Returns a vector of likely call sites (typically pc is the
   * address of a return instruction)
   * @param pc location to suggest call sites for
   * @return vector of potential calling instructions
   */
  public Set getLikelyCallSites(int pc) {
    return null; //ps.branchInfo.getLikelyCallSites(pc);
  }

  /**
   * Returns a vector of likely bcctr destinations
   * @param pc location to suggest destinations for
   * @return vector of potential destinations
   */
  public Set getLikelyCtrDestinations(int pc) {
    return null; //ps.branchInfo.getLikelyCtrDestinations(pc);
  }

  /**
   * Should a trace follow a branch and link instruction or should it
   * terminate the trace?
   * @param pc the address of the branch and link instruction
   * @return whether the trace should continue
   */
  public boolean traceContinuesAfterBranchAndLink(int pc) {
    return suitableToStop() == false;
  }
  // -oO Register Manipulation Oo-

  /**
   * Fill all the registers from the ProcessSpace, that is take the
   * register values from the process space and place them in the
   * traces registers.
   */
  protected abstract void fillAllRegisters();
  /**
   * Spill all the registers, that is put them from the current
   * running trace into the process space
   */
  protected abstract void spillAllRegisters();

  /**
   * Load all the registers from the ProcessSpace into the pre-fill
   * block
   */
  private void preFillAllRegisters() {
    OPT_BasicBlock temp = currentBlock;
    currentBlock = preFillBlock;
    fillAllRegisters();
    ps.memory.initTranslate(this); // Set up memory
    currentBlock = temp;
  }

  /**
   * Return an array of unused registers
   */
  protected abstract OPT_Register[] getUnusedRegisters();

  /**
   * Eliminate unnecessary register spill and fill code - ie a
   * register wasn't used so eliminate references to it
   */
  protected void eliminateRegisterFills(OPT_Register unusedRegisters[]) {
    if (unusedRegisters.length > 0) {
      OPT_BasicBlock curBB = gc.prologue;
      while(curBB != null) {
        OPT_Instruction curInstr = curBB.firstInstruction();
        loop_over_instructions:
        while(BBend.conforms(curInstr) == false) {
          for (Enumeration du = curInstr.getRootOperands(); du.hasMoreElements(); ) {
            OPT_Operand curOp = (OPT_Operand)du.nextElement();
            if (curOp.isRegister()) {
              OPT_Register curReg = curOp.asRegister().register;
              for (int i=0; i<unusedRegisters.length; i++) {
                if (unusedRegisters[i] == curReg) {
                  OPT_Instruction toRemove = curInstr;
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

  // -oO System call handling Oo-

  /**
   * Plant a system call
   * @param lazy current translation laziness
   * @param pc address of system call instruction
   */
  public void plantSystemCall(Laziness lazy, int pc) {
    // Need to make sure that the PPC_ProcessSpace registers have
    // the correct values before call to doSysCall().
    resolveLaziness(lazy);
    spillAllRegisters();

    // Plant call
    OPT_Instruction s = Call.create(CALL, null, null, null, null, 1);

    // VM_CompiledMethod cm = method.getCurrentCompiledMethod();
    // OPT_MethodOperand methOp = OPT_MethodOperand.COMPILED(method, cm.getOsrJTOCoffset());
    OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL(sysCallMethod.getMemberRef().asMethodReference(),
							 sysCallMethod);

    OPT_Operand psRef = gc.makeLocal(1,psTref); 
    Call.setParam(s, 0, psRef); // Reference to ps, sets 'this' pointer for doSysCall
    Call.setGuard(s, new OPT_TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new OPT_AddressConstantOperand(sysCallMethod.getOffset()));
    s.position = gc.inlineSequence;
    s.bcIndex = DBT_Trace.DO_SYSCALL;
    appendInstructionToCurrentBlock(s);

    // Fill all registers again following system call
    fillAllRegisters();
  }

  // -oO Handling bad instructions Oo-

  /**
   * Plant a throw of a bad instruction exception
   * @param lazy current translation laziness
   * @param pc the program counter of the bad instruction
   */
  public void plantThrowBadInstruction(Laziness lazy, int pc) {
    // There's a bug with this, so for now I'm just commenting it out :-( -- IAR
    setReturnValueResolveLazinessAndBranchToFinish(lazy, new OPT_IntConstantOperand(0xEBADC0DE));    
    if (false) {
      // Need to make sure that the PPC_ProcessSpace registers have
      // the correct values before call to doSysCall().
      resolveLaziness(lazy);
      spillAllRegisters();

      OPT_Operator newOperator;
      OPT_TypeOperand typeOperand = new OPT_TypeOperand(badInstrKlass);
      VM_TypeReference eTref = badInstrKlass.getTypeRef();

      if (badInstrKlass.isInitialized() || badInstrKlass.isInBootImage()) {
        newOperator = NEW;
      } else {
        newOperator = NEW_UNRESOLVED;
      }

      OPT_RegisterOperand eRef = gc.temps.makeTemp(eTref);

      OPT_Instruction n = New.create(newOperator,eRef, typeOperand);
      n.position = gc.inlineSequence;
      n.bcIndex = DBT_Trace.BAD_INSTRUCTION_NEW;

      OPT_Operand psRef = gc.makeLocal(1,psTref);

      OPT_Instruction c = Call.create(CALL, null, null, null, null, 3);

      OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL(badInstrKlassInitMethod.getMemberRef().asMethodReference(),
							   badInstrKlassInitMethod);
      Call.setParam(c, 0, eRef.copy()); // 'this' pointer in BadInstructionException.init
      Call.setParam(c, 1, new OPT_IntConstantOperand(pc));
      Call.setParam(c, 2, psRef);
      Call.setGuard(c, new OPT_TrueGuardOperand());
      Call.setMethod(c, methOp);
      Call.setAddress(c, new OPT_AddressConstantOperand(badInstrKlassInitMethod.getOffset()));
      c.position = gc.inlineSequence;
      c.bcIndex = DBT_Trace.BAD_INSTRUCTION_INIT;

      OPT_Instruction t = Athrow.create(ATHROW, eRef.copyRO());
      t.position = gc.inlineSequence;
      t.bcIndex = DBT_Trace.BAD_INSTRUCTION_THROW;

      appendInstructionToCurrentBlock(n);

      appendInstructionToCurrentBlock(c);
    
      appendInstructionToCurrentBlock(t); 

      setReturnValueResolveLazinessAndBranchToFinish(lazy, new OPT_IntConstantOperand(0xEBADC0DE));    
    }
  }

    // -oO Trace helping methods Oo-

    /**
     * Plant a record uncaught branch call. NB register state won't get resolved for call
     * @param pc the address of the branch instruction
     * @param destination the register operand holding the destination
     * @param code a code that can be a hint of the branch type
     */
    public void plantRecordUncaughtBranch(int pc, OPT_RegisterOperand destination, int code) {
        // Is it sensible to record this information?
        if((gc.options.getOptLevel() > 0) && (DBT_Options.plantUncaughtBranchWatcher)) {
            // Plant call
            OPT_Instruction s = Call.create(CALL, null, null, null, null, 4);
            OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL(recordUncaughtBranchMethod.getMemberRef().asMethodReference(),
								 recordUncaughtBranchMethod);
            OPT_Operand psRef = gc.makeLocal(1,psTref); 
            Call.setParam(s, 0, psRef); // Reference to ps, sets 'this' pointer
            Call.setParam(s, 1, new OPT_IntConstantOperand(pc)); // Address of branch instruction
            Call.setParam(s, 2, destination);    // Destination of branch value
            Call.setParam(s, 3, new OPT_IntConstantOperand(code)); // Branch code value
            Call.setGuard(s, new OPT_TrueGuardOperand());
            Call.setMethod(s, methOp);
            Call.setAddress(s, new OPT_AddressConstantOperand(recordUncaughtBranchMethod.getOffset()));
            s.position = gc.inlineSequence;
            s.bcIndex = DBT_Trace.RECORD_BRANCH;
            appendInstructionToCurrentBlock(s);
        }
    }

  // -oO Temporaries used during translation Oo-

  /**
   * Temporary int variables
   */
  private OPT_Register intTemps[];

  /**
   * Get/create a temporary int
   * @param num a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempInt(int num) {
    if (intTemps == null) {
      intTemps = new OPT_Register[10];
    }
    OPT_Register result = intTemps[num];
    if(result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempInt();
      intTemps[num] = regOp.register;
      return regOp;
    }
    else {
      return new OPT_RegisterOperand(result, VM_TypeReference.Int);
    }
  }

  /**
   * Temporary long variables
   */
  private OPT_Register longTemps[];

  /**
   * Get/create a temporary long
   * @param num a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempLong(int num) {
    if (longTemps == null) {
      longTemps = new OPT_Register[10];
    }
    OPT_Register result = longTemps[num];
    if(result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempLong();
      longTemps[num] = regOp.register;
      return regOp;
    }
    else {
      return new OPT_RegisterOperand(result, VM_TypeReference.Long);
    }
  }

  /**
   * Temporary intArray variables
   */

  private OPT_Register intArrayTemp;

  /**
   * Get/create a temporary intArray
   * @param num a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempIntArray(int num) {
    if (VM.VerifyAssertions) VM._assert(num == 0);

    OPT_Register result = intArrayTemp;
    if(result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTemp(VM_TypeReference.IntArray);
      intArrayTemp = regOp.register;
      return regOp;
    }
    else {
      return new OPT_RegisterOperand(result, VM_TypeReference.IntArray);
    }
  }

  /**
   * Temporary float variables
   */

  private OPT_Register floatTemps[];

  /**
   * Get/create a temporary float
   * @param num a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempFloat(int num) {
    if (VM.VerifyAssertions) VM._assert((num == 0)||(num==1));
    if (floatTemps == null) {
      floatTemps = new OPT_Register[2];
    }
    OPT_Register result = floatTemps[num];
    if(result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempFloat();
      floatTemps[num] = regOp.register;
      return regOp;
    }
    else {
      return new OPT_RegisterOperand(result, VM_TypeReference.Float);
    }
  }

  /**
   * Temporary Double variables
   */

  private OPT_Register doubleTemp;

  /**
   * Get/create a temporary float
   * @param num a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempDouble(int num) {
    if (VM.VerifyAssertions) VM._assert(num == 0);
    OPT_Register result = doubleTemp;
    if(result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempDouble();
      doubleTemp = regOp.register;
      return regOp;
    }
    else {
      return new OPT_RegisterOperand(result, VM_TypeReference.Double);
    }
  }

  /**
   * Temporary validation variables
   */

  private OPT_Register validationTemp;

  /**
   * Get/create a temporary validation variable
   * @param num a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempValidation(int num) {
    if (VM.VerifyAssertions) VM._assert(num == 0);

    OPT_Register result = validationTemp;
    if(result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempValidation();
      validationTemp = regOp.register;
      return regOp;
    }
    else {
      return new OPT_RegisterOperand(result, VM_TypeReference.VALIDATION_TYPE);
    }
  }

  // -oO Utilities Oo-

  /**
   * Get the method
   */
  public DBT_Trace getMethod() {
      return (DBT_Trace)gc.method;
  }
  /**
   * Make a temporary register
   */
  public OPT_RegisterOperand makeTemp(VM_TypeReference type) {
    return gc.temps.makeTemp(type);
  }
}
