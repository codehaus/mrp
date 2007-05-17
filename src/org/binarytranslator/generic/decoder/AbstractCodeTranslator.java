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
import java.util.Set;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.generic.branch.BranchLogic.BranchType;
import org.binarytranslator.generic.decoder.Laziness.Key;
import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DBT_Trace;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OPT_Constants;
import org.jikesrvm.compilers.opt.ir.Athrow;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Goto;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.LookupSwitch;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BranchOperand;
import org.jikesrvm.compilers.opt.ir.OPT_BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.compilers.opt.ir.OPT_GenerationContext;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_MethodOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Operator;
import org.jikesrvm.compilers.opt.ir.OPT_Operators;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TypeOperand;

/**
 * A collection of common tools used by decoders. The public entry point for the
 * translators is generateHIR(OPT_GenerationContext gc).
 * 
 * <dl>
 * <dt>Description of the translation algorithm</dt>
 * <dd>
 * <ol>
 * <li>The translation is set up so that the start and end blocks are created.
 * The start block branches to a prefill block that loads all the register
 * values</li>
 * <li>Translation starts from the PC value given in the DBT_Trace.</li>
 * <li>Translation translates PPC instructions to HIR using the
 * translateInstruction method. This and the derived decoder object contain the
 * utilities required to aid an instruction translation.</li>
 * <li>The trace ends when the branch prediction deems it necessary. The
 * closing basic blocks are generated and things are wrapped up.</li>
 * </ol>
 * </dd>
 * </dl>
 */
public abstract class AbstractCodeTranslator implements OPT_Constants, OPT_Operators {

  /**
   * VM_TypeReference of org.binarytranslator.generic.os.process.ProcessSpace
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
  public static final VM_Class badInstrKlass;

  /**
   * Method BadInstructionException.<init>
   */
  public static final VM_Method badInstrKlassInitMethod;

  /**
   * Method ProcessSpace.recordUncaughtBranchBadInstructionException.<init>
   */
  public static final VM_Method recordUncaughtBranchMethod;

  static {
    psTref = java.lang.JikesRVMSupport.getTypeForClass(ProcessSpace.class)
        .getTypeRef();
    VM_MethodReference sysCallMethRef = (VM_MethodReference) VM_MemberReference
        .findOrCreate(psTref, VM_Atom.findOrCreateAsciiAtom("doSysCall"),
            VM_Atom.findOrCreateAsciiAtom("()V"));
    sysCallMethod = sysCallMethRef.resolveInvokeSpecial();

    badInstrKlass = java.lang.JikesRVMSupport.getTypeForClass(
        BadInstructionException.class).asClass();

    VM_MethodReference badInstrKlassInitMethRef = (VM_MethodReference) VM_MemberReference
        .findOrCreate(badInstrKlass.getTypeRef(),
            VM_Atom.findOrCreateAsciiAtom("<init>"),
            VM_Atom.findOrCreateAsciiAtom("(ILorg/binarytranslator/generic/os/process/ProcessSpace;)V"));
    badInstrKlassInitMethod = badInstrKlassInitMethRef.resolveInvokeSpecial();

    VM_MethodReference recordUncaughtBranchMethRef = (VM_MethodReference) VM_MemberReference
        .findOrCreate(psTref, VM_Atom
            .findOrCreateAsciiAtom("recordUncaughtBranch"), VM_Atom
            .findOrCreateAsciiAtom("(III)V"));
    recordUncaughtBranchMethod = recordUncaughtBranchMethRef
        .resolveInvokeSpecial();
  }

  /** Number of translated instructions */
  private int numberOfInstructions;

  /** The process space object used by the running binary. */
  public final ProcessSpace ps;

  /** The VM method's generation context. */
  protected OPT_GenerationContext gc;

  /** The OPT_BasicBlock in which instructions are currently being inserted */
  protected OPT_BasicBlock currentBlock;

  /** The pc value corresponding to the instruction currently being translated */
  protected int currentPC;

  /** The pc value of the first instruction in the trace */
  protected int startingPC;

  /** The OPT_BasicBlock which will contain the next translated instruction */
  protected OPT_BasicBlock nextBlock;

  /**
   * The basic block is used by finish trace to hold all the code that must be
   * executed before returning to the main run loop
   */
  protected OPT_BasicBlock finishBlock;

  /**
   * This block gets instructions to pre-fill registers inserted into it.
   */
  protected OPT_BasicBlock preFillBlock;

  /** Map to locate HIR basic blocks to re-use translation within a trace */
  protected final HashMap<Laziness.Key, OPT_BasicBlock> blockMap;
  
  private final static class UnresolvedJumpInstruction {
    public final OPT_Instruction instruction;
    public final Laziness lazyStateAtJump;
    public final int pc;
    
    public UnresolvedJumpInstruction(OPT_Instruction instruction, Laziness lazyStateAtJump, int pc) {
      this.instruction = instruction;
      this.lazyStateAtJump = lazyStateAtJump;
      this.pc = pc;
    }
  }

  /** List of unresolved Goto instructions */
  private final ArrayList<UnresolvedJumpInstruction> unresolvedGoto;

  /** List of unresolved IfCmp instructions */
  private final ArrayList<UnresolvedJumpInstruction> unresolvedIfCmp;
  
  /** List of unresolved dynamic jumps */
  private final ArrayList<UnresolvedJumpInstruction> unresolvedDynamicJumps;

  /**
   * Constructor
   * 
   * @param context
   *  The JRVM generation context for this trace.
   */
  protected AbstractCodeTranslator(OPT_GenerationContext context) {
    // Make copies of popular variables
    gc = context;
    ps = ((DBT_Trace) (gc.method)).ps;
    currentPC = ((DBT_Trace) (gc.method)).pc;
    startingPC = currentPC;

    // Number of translated instructions
    numberOfInstructions = 0;

    // Create map of (PC & laziness) -> OPT_BasicBlock
    blockMap = new HashMap<Key, OPT_BasicBlock>();

    // Create preFillBlock, currentBlock and finishBlock
    gc.prologue.insertOut(gc.epilogue);
    preFillBlock = createBlockAfter(gc.prologue);

    currentBlock = createBlockAfter(preFillBlock);

    finishBlock = createBlockAfterCurrent();

    // Fix up stores
    unresolvedGoto = new ArrayList<UnresolvedJumpInstruction>();
    unresolvedIfCmp = new ArrayList<UnresolvedJumpInstruction>();
    unresolvedDynamicJumps = new ArrayList<UnresolvedJumpInstruction>();
  }
  
  /** Returns the number of previously translated instructions within this trace. */
  public int getNumInstructions() {
    return numberOfInstructions;
  }

  /** This is the main loop, which generates the HIR. */
  public void generateHIR() {
    // Load all the register values to be used in a trace
    preFillAllRegisters();

    // Translate from the current program counter
    translateSubTrace(createInitialLaziness(), currentPC);

    // Translating the subtrace finished so resolve any unresolved
    // branches
    do {
      if (DBT_Options.resolveProceduresBeforeBranches) {
        resolveAllDynamicJumpTargets();
      }
      if (DBT_Options.resolveBranchesAtOnce) {
        do {
          resolveGoto();
          resolveIfCmp();
        } while (((unresolvedGoto.size() == 0) && (unresolvedIfCmp.size() == 0)) == false);
      } else {
        resolveGoto();
        resolveIfCmp();
      }
      if (!DBT_Options.resolveProceduresBeforeBranches) {
        resolveAllDynamicJumpTargets();
      }
    } while (((unresolvedGoto.size() == 0) && (unresolvedIfCmp.size() == 0)
        && areDynamicJumpsReadyToResolve()) == false);
    
    // Resolve unresolved dynamic jumps
    resolveDynamicJumps();

    // Finish up the trace
    finishTrace();

    if (DBT_Options.eliminateRegisterFills) {
      eliminateRegisterFills(getUnusedRegisters());
    }
    // TODO: maximizeBasicBlocks()
  }

  /**
   * Translate a sequence of instructions upto an instruction that doesn't know
   * its immediate/default successor. The currentBlock should be an empty basic
   * block.
   */
  private void translateSubTrace(Laziness lazy, int pc) {
    currentPC = pc;
    if (suitableToStop()) {
      // Record mapping of this pc value and laziness to this block
      registerMapping(pc, lazy, currentBlock);
      // Create next block
      nextBlock = createBlockAfterCurrent();
      // Finish block to return and exit
      setReturnValueResolveLazinessAndBranchToFinish(lazy,
          new OPT_IntConstantOperand(pc));
      // Move currentBlock along
      currentBlock = nextBlock;
    } else {
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
            setReturnValueResolveLazinessAndBranchToFinish(lazy,
                new OPT_IntConstantOperand(pc));
          }
          break;
        }

        // Do we already have a translation for this next block?
        OPT_BasicBlock possibleNextBlock = findMapping(pc, lazy);
        if (possibleNextBlock != null) {
          // Yes, branch to that and stop translating
          appendInstruction(Goto.create(GOTO, possibleNextBlock
              .makeJumpTarget()));
          OPT_BasicBlock gotoBlock = currentBlock;
          currentBlock = createBlockAfterCurrent();
          gotoBlock.deleteNormalOut();
          gotoBlock.insertOut(possibleNextBlock);
          if (DBT.VerifyAssertions)
            DBT._assert(gotoBlock.getNumberOfNormalOut() == 1);
          break;
        }
      } while (pc != -1);
    }
  }

  /**
   * Get the block which is currently having instructions inserted into it
   * 
   * @return the current block
   */
  public OPT_BasicBlock getCurrentBlock() {
    return currentBlock;
  }

  /**
   * Get the block which will contain the translation of the next PPC
   * instruction
   * 
   * @return the next block
   */
  public OPT_BasicBlock getNextBlock() {
    return nextBlock;
  }

  /**
   * Set the block which is currently having instructions inserted into it
   * 
   * @param newCurrentBlock
   *          the new current basic block
   */
  public void setCurrentBlock(OPT_BasicBlock newCurrentBlock) {
    currentBlock = newCurrentBlock;
  }

  /**
   * Set the block which will contain the translation of the next PPC
   * instruction
   * 
   * @param newCurrentBlock
   *          the new next basic block
   */
  public void setNextBlock(OPT_BasicBlock newNextBlock) {
    nextBlock = newNextBlock;
  }

  /**
   * Create a basic block immediately after the current block and link its edges
   * into the CFG and code ordering
   * 
   * @return the new basic block
   */
  public OPT_BasicBlock createBlockAfterCurrent() {
    OPT_BasicBlock nxtBlock = currentBlock.nextBasicBlockInCodeOrder();
    OPT_BasicBlock newBlock = new OPT_BasicBlock(0, gc.inlineSequence, gc.cfg);

    gc.cfg.breakCodeOrder(currentBlock, nxtBlock);
    gc.cfg.linkInCodeOrder(currentBlock, newBlock);
    gc.cfg.linkInCodeOrder(newBlock, nxtBlock);

    if (DBT.VerifyAssertions)
      DBT._assert(currentBlock.isOut(nxtBlock));

    currentBlock.deleteOut(nxtBlock);
    currentBlock.insertOut(newBlock);
    newBlock.insertOut(nxtBlock);

    if (DBT_Options.debugCFG) {
      report(String.format("Created block (%s) after current (%s).", newBlock, currentBlock));
    }
    return newBlock;
  }

  /**
   * Create a basic block immediately after the current block and link its edges
   * into code ordering but not the CFG
   * 
   * @return the new basic block
   */
  public OPT_BasicBlock createBlockAfterCurrentNotInCFG() {
    OPT_BasicBlock nxtBlock = currentBlock.nextBasicBlockInCodeOrder();
    OPT_BasicBlock newBlock = new OPT_BasicBlock(0, gc.inlineSequence, gc.cfg);

    gc.cfg.breakCodeOrder(currentBlock, nxtBlock);
    gc.cfg.linkInCodeOrder(currentBlock, newBlock);
    gc.cfg.linkInCodeOrder(newBlock, nxtBlock);

    if (DBT_Options.debugCFG) {
      report(String.format("Created non-cfg block (%s) after current (%s).", newBlock, currentBlock));
    }
    return newBlock;
  }

  /**
   * Create a basic block immediately after the given block and link its edges
   * into the CFG and code ordering
   * 
   * @param afterBlock
   *  The block after which the new block is to be created.
   * @return the new basic block
   */
  public OPT_BasicBlock createBlockAfter(OPT_BasicBlock afterBlock) {
    OPT_BasicBlock nxtBlock = afterBlock.nextBasicBlockInCodeOrder();
    OPT_BasicBlock newBlock = new OPT_BasicBlock(0, gc.inlineSequence, gc.cfg);

    gc.cfg.breakCodeOrder(afterBlock, nxtBlock);
    gc.cfg.linkInCodeOrder(afterBlock, newBlock);
    gc.cfg.linkInCodeOrder(newBlock, nxtBlock);

    if (DBT.VerifyAssertions)
      DBT._assert(afterBlock.isOut(nxtBlock));
    afterBlock.deleteOut(nxtBlock);
    afterBlock.insertOut(newBlock);
    newBlock.insertOut(nxtBlock);

    if (DBT_Options.debugCFG) {
      report(String.format("Created block (%s) after current (%s).", newBlock, afterBlock));
    }
    
    return newBlock;
  }

  /**
   * Append a HIR instruction to the current basic block
   * 
   * @param i
   *  The instruciton that is to be appended to the current bloc.
   */
  public void appendInstruction(OPT_Instruction i) {
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
   *  Does this branch have a likely hint?
   */
  public OPT_BranchProfileOperand getConditionalBranchProfileOperand(
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
  public OPT_GenerationContext getGenerationContext() {
    return gc;
  }

  /**
   * Register a mapping between a pc and lazy and a hir block
   * 
   * @param pc
   *  The program counter whose translation the basic bock represents.
   * @param lazy
   *  The lazy state that is assumed for this basic block.
   * @param hirBlock
   *  The block that is to be registered.
   */
  protected void registerMapping(int pc, Laziness lazy, OPT_BasicBlock hirBlock) {
    blockMap.put(lazy.makeKey(pc), hirBlock);
  }

  /**
   * Find if there's already a translation for a given pc and laziness
   * 
   * @param pc
   *  The program counter address at which the returned basic block shall start.
   * @param lazy
   *  The lazy state assumed within the returned trace.
   * @return 
   *  An appropriate basic block or null if no translation exists.
   */
  protected OPT_BasicBlock findMapping(int pc, Laziness lazy) {
    return blockMap.get(lazy.makeKey(pc));
  }

  /**
   * Appends an IfCmp instruction to the current block that (when the condition is true) jumps
   * to the address <code>targetPc</code>. The parameters for this function are mostly
   * the same as for the {@link IfCmp#create(OPT_Operator, OPT_RegisterOperand, OPT_Operand, OPT_Operand, OPT_ConditionOperand, OPT_BranchOperand, OPT_BranchProfileOperand)} function.
   * 
   * @param operator
   *  The HIR operator.
   * @param guard
   *  A guard result for this call.
   * @param lhs
   *  The left-hand-side comparision operator.
   * @param rhs
   *  The right-hand-side comparision operator.
   * @param condition
   *  The type of comparison that is to be executed.
   * @param targetPc
   *  The address that the trace shall jump to, when the condition evaluates to true.
   * @param targetLaziness
   *  The laziness at the point of jump.
   * @param branchProfile
   *  A branch profile operand that describes how likely it is that the jump is executed.
   */
  public void appendIfCmp(OPT_Operator operator, OPT_RegisterOperand guard, OPT_Operand lhs,
      OPT_Operand rhs, OPT_ConditionOperand condition, int targetPc, Laziness targetLaziness, OPT_BranchProfileOperand branchProfile) {
    
    OPT_BasicBlock targetBlock = findMapping(targetPc, targetLaziness);
    OPT_Instruction ifcmp = IfCmp.create(operator, guard, lhs, rhs, condition, null, branchProfile);
    
    if (targetBlock != null) { 
      IfCmp.setTarget(ifcmp, targetBlock.makeJumpTarget());
      getCurrentBlock().insertOut(targetBlock);
    }
    else {
      UnresolvedJumpInstruction unresolvedJump = new UnresolvedJumpInstruction(ifcmp, (Laziness)targetLaziness.clone(), targetPc);
      unresolvedIfCmp.add(unresolvedJump);
    }
    
    appendInstruction(ifcmp);
  }

  /** Resolve a single ifCmp instruction */
  private void resolveIfCmp() {
    
    if (unresolvedIfCmp.size() == 0)
      return;
    
    //Try to find if block has now been translated
    UnresolvedJumpInstruction instruction = unresolvedIfCmp.remove(unresolvedIfCmp.size() - 1);
    
    OPT_Instruction ifCmp = instruction.instruction;
    
    if (DBT.VerifyAssertions) DBT._assert(IfCmp.conforms(ifCmp));
    
    //try to resolve the ifcmp's jump target
    OPT_BasicBlock targetBB = resolveJumpTarget(instruction.pc, instruction.lazyStateAtJump);
    
    //Fix up the instruction
    IfCmp.setTarget(ifCmp, targetBB.makeJumpTarget());
    ifCmp.getBasicBlock().insertOut(targetBB);
  }

  /**
   * Create a HIR Goto instruction that jumps to the address <code>targetPc</code>. There's
   * a caveat on using this that there are no other out edges for this BB.
   * 
   * @param targetPC
   *  The address where we shall jump to.
   * @param targetLaziness
   *  The current at the point of jump.
   */
  public void appendGoto(int targetPC, Laziness targetLaziness) {
    
    OPT_Instruction jump;
    OPT_BasicBlock target = findMapping(targetPC, targetLaziness);
    
    if (target != null) { 
      jump = Goto.create(GOTO, target.makeJumpTarget());
      getCurrentBlock().insertOut(target);
    }
    else {
      jump = Goto.create(GOTO, null);
      UnresolvedJumpInstruction unresolvedJump = new UnresolvedJumpInstruction(jump, (Laziness)targetLaziness.clone(), targetPC);
      unresolvedGoto.add(unresolvedJump);
    }
    
    appendInstruction(jump);
  }

  /** Resolve a single goto instruction */
  private void resolveGoto() {
    
    if (unresolvedGoto.size() == 0)
      return;
    
    //Get the jump that we're supposed to resolve
    UnresolvedJumpInstruction unresolvedInstr = unresolvedGoto.remove(unresolvedGoto.size() - 1);
    int targetPc = unresolvedInstr.pc;
    Laziness lazyStateAtJump = unresolvedInstr.lazyStateAtJump;
    OPT_Instruction gotoInstr = unresolvedInstr.instruction;
    
    if (DBT.VerifyAssertions) DBT._assert(Goto.conforms(gotoInstr));
    
    OPT_BasicBlock targetBB = resolveJumpTarget(targetPc, lazyStateAtJump);
    
    if (DBT_Options.debugLazy) {
      report("Resolving goto " + lazyStateAtJump.makeKey(targetPc) + " " + targetBB);
    }

    // Fix up instruction
    Goto.setTarget(gotoInstr, targetBB.makeJumpTarget());
    gotoInstr.getBasicBlock().deleteNormalOut();
    gotoInstr.getBasicBlock().insertOut(targetBB);
    
    if (DBT.VerifyAssertions) DBT._assert(gotoInstr.getBasicBlock().getNumberOfNormalOut() == 1);
    
    if (DBT_Options.debugLazy) {
      report("Properly resolving goto in block "
          + gotoInstr.getBasicBlock() + " to " + lazyStateAtJump.makeKey(targetPc) + " "
          + targetBB);
    }
  }

  /**
   * Resolves a jump target to an actual basic block. This method usually tries to compile the trace
   * for any target addresses that have not yet been compiled. However, when 
   * {@link DBT_Options#singleInstrTranslation} is turned on, then this method will end the curren trace,
   * just returning the address of the next instruction.
   * 
   * @param targetPc
   *  The address of the target basic block that.
   * @param lazyStateAtJump
   *  The lazy state with which we would be entering the block.
   * @return
   *  A basic block that is equivalent to the program counter address <code>targetPc</code> in the
   *  original binary.
   */
  private OPT_BasicBlock resolveJumpTarget(int targetPc, Laziness lazyStateAtJump) {
    //Resolve the address of the target block
    OPT_BasicBlock targetBB;
    if (DBT_Options.singleInstrTranslation == true) {
      //In Single instruction mode, the target block just ends the trace
      if (currentBlock.getNumberOfRealInstructions() != 0) {
        currentBlock = createBlockAfterCurrentNotInCFG();
      }

      targetBB = currentBlock;
      setReturnValueResolveLazinessAndBranchToFinish(lazyStateAtJump, new OPT_IntConstantOperand(targetPc));
    } 
    else {
      //Try to find if block has now been translated
      targetBB = findMapping(targetPc, lazyStateAtJump);
      
      if (targetBB == null) {
        // Block not translated so translate
        translateSubTrace((Laziness) lazyStateAtJump.clone(), targetPc);
        targetBB = findMapping(targetPc, lazyStateAtJump);
      }
    }
    
    if (DBT.VerifyAssertions) DBT._assert(targetBB != null);
    
    return targetBB;
  }

  /**
   * Resolves all dynamic jumps that have been added with 
   * {@link #appendDynamicJump(OPT_RegisterOperand, Laziness, BranchType)}.
   */
  private void resolveDynamicJumps() {

    for (int i = 0; i < unresolvedDynamicJumps.size(); i++) {
      
      UnresolvedJumpInstruction unresolvedSwitch = unresolvedDynamicJumps.get(i);

      Laziness lazy = unresolvedSwitch.lazyStateAtJump;
      OPT_Instruction lookupswitch = unresolvedSwitch.instruction;
      Set<Integer> branchDests = getLikelyJumpTargets(unresolvedSwitch.pc);
      
      resolveSingleDynamicJump(lazy, lookupswitch, branchDests);
    }
  }

  /** 
   * Resolves a single dynamic jump that has previously been created with 
   * {@link #appendDynamicJump(OPT_RegisterOperand, Laziness, BranchType)}.
   * 
   * @param lazy
   *  The lazy state of the jump that is to be resolved.
   * @param lookupswitch
   *  Each dynamic jump is converted to a switch statement. This is the switch statement for the current jump.
   * @param destinations
   *  A list of known destinations that this dynamic jumps branches to.
   */
  private void resolveSingleDynamicJump(Laziness lazy, OPT_Instruction lookupswitch, Set<Integer> destinations) throws Error {
    OPT_BranchOperand default_target = LookupSwitch.getDefault(lookupswitch);
    OPT_Operand value = LookupSwitch.getValue(lookupswitch);
    
    if (destinations != null) {
      if ((destinations.size() > 1)
          || (lookupswitch.getBasicBlock().nextBasicBlockInCodeOrder() != default_target.target
              .getBasicBlock())) {
        float branchProb = (1.0f - OPT_BranchProfileOperand.UNLIKELY)
            / (float) destinations.size();
        LookupSwitch.mutate(lookupswitch, LOOKUPSWITCH, value, null, null,
            default_target, OPT_BranchProfileOperand.unlikely(), destinations
                .size() * 3);
        int match_no = 0;
        for (int dest_pc : destinations) {
          
          OPT_BasicBlock target = findMapping(dest_pc, lazy);
          if (target == null) {
            throw new Error("Failed to find trace for " + dest_pc
                + " with laziness " + lazy);
          }
          LookupSwitch.setMatch(lookupswitch, match_no,
              new OPT_IntConstantOperand(dest_pc));
          LookupSwitch.setTarget(lookupswitch, match_no, target
              .makeJumpTarget());
          LookupSwitch.setBranchProfile(lookupswitch, match_no,
              new OPT_BranchProfileOperand(branchProb));
          lookupswitch.getBasicBlock().insertOut(target);
          match_no++;
        }
      } else {
        int dest_pc = destinations.iterator().next();
        
        OPT_BasicBlock target = findMapping(dest_pc, lazy);
        if (target == null) {
          throw new Error("Failed to find trace for " + dest_pc
              + " with laziness " + lazy);
        }
        IfCmp.mutate(lookupswitch, INT_IFCMP, null, value,
            new OPT_IntConstantOperand(dest_pc),
            OPT_ConditionOperand.EQUAL(), target.makeJumpTarget(),
            OPT_BranchProfileOperand.likely());
        lookupswitch.getBasicBlock().insertOut(target);
      }
    } else {
      // no possibly branch destinations so remove lookupswitch
      lookupswitch.remove();
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
   * @param branchType
   *  The type of jump.
   */
  public void appendDynamicJump(OPT_RegisterOperand targetAddress, Laziness lazyStateAtJump, BranchType branchType) {
    
    OPT_BasicBlock fallThrough = createBlockAfterCurrent();
    OPT_Instruction switchInstr;
    switchInstr = LookupSwitch.create(LOOKUPSWITCH, targetAddress, null, null, fallThrough.makeJumpTarget(), null, 0);
    appendInstruction(switchInstr);
    
    UnresolvedJumpInstruction unresolvedInfo = new UnresolvedJumpInstruction(switchInstr, (Laziness)lazyStateAtJump.clone(), currentPC);
    unresolvedDynamicJumps.add(unresolvedInfo);

    setCurrentBlock(fallThrough);
    appendRecordUncaughtBranch(currentPC, targetAddress.copyRO(), branchType);
    setReturnValueResolveLazinessAndBranchToFinish((Laziness) lazyStateAtJump.clone(), targetAddress.copyRO());
  }

  /**
   * Checks if all dynamic jumps are ready to be resolved (because their target blocks have
   * been resolved).
   * 
   * @return
   *  True if all dynamic jumps can be resolved, false otherwise.
   */
  private boolean areDynamicJumpsReadyToResolve() {
    for (int i = 0; i < unresolvedDynamicJumps.size(); i++) {
      
      UnresolvedJumpInstruction unresolvedSwitch = unresolvedDynamicJumps.get(i);
      Laziness lazy = unresolvedSwitch.lazyStateAtJump;
      Set<Integer> branchDests = getLikelyJumpTargets(unresolvedSwitch.pc);
      
      if (branchDests != null) {
        for (int dest_pc : branchDests) {
          if (findMapping(dest_pc, lazy) == null) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Resolves all dynamic jump targets by making sure the respective basic blocks exist.
   */
  private void resolveAllDynamicJumpTargets() {
    for (int i = 0; i < unresolvedDynamicJumps.size(); i++) {
      
      UnresolvedJumpInstruction unresolvedSwitch = unresolvedDynamicJumps.get(i);
      
      Laziness lazy = unresolvedSwitch.lazyStateAtJump;
      Set<Integer> branchDests = getLikelyJumpTargets(unresolvedSwitch.pc);
      if (branchDests != null) {
        for (int dest_pc : branchDests) {
          if (findMapping(dest_pc, lazy) == null) {
            // Block not translated so translate
            translateSubTrace((Laziness) lazy.clone(), dest_pc);
          }
        }
      }
    }
  }



  /**
   * Set the return value in the currentBlock, resolve its lazy state (so the
   * state is no longer lazy) and then set it to branch to the finish block
   * 
   * @param value
   *          return value for translated code (the PC value of the next
   *          instruction to translate)
   */
  public void setReturnValueResolveLazinessAndBranchToFinish(Laziness laziness,
      OPT_Operand value) {
       
    nextBlock = createBlockAfterCurrent();
    // Copy the value into the register specified by gc.resultReg.
    appendInstruction(Move.create(INT_MOVE,
        new OPT_RegisterOperand(gc.resultReg, VM_TypeReference.Int), value));
    resolveLaziness(laziness);
    appendInstruction(Goto.create(GOTO, finishBlock
        .makeJumpTarget()));
    currentBlock.deleteNormalOut();
    currentBlock.insertOut(finishBlock);
    if (DBT.VerifyAssertions)
      DBT._assert(currentBlock.getNumberOfNormalOut() == 1);
    currentBlock = nextBlock;
  }

  /**
   * Is it suitable to stop the trace now?
   * 
   * @return true => try to stop the trace
   */
  protected boolean suitableToStop() {
    if (DBT_Options.singleInstrTranslation && (numberOfInstructions >= 1)) {
      return true;
    } else {    
      switch (gc.options.getOptLevel()) {
      case 0:
        return numberOfInstructions > DBT_Options.instrOpt0;
      case 1:
        return numberOfInstructions > DBT_Options.instrOpt1;
      default:
        return numberOfInstructions > DBT_Options.instrOpt2;
      }
    }
  }

  /**
   * Register a branch and link instruction
   * 
   * @param pc
   *  the address of the branch instruction
   * @param ret
   *  the address returned to
   * @param dest
   *  the destination of the branch instruction
   */
  public void registerBranchAndLink(int pc, int ret, int dest) {
    ps.branchInfo.registerCall(pc, ret, dest);
  }

  /**
   * Returns a vector of likely branch targets for the branch at address <code>pc</code>.
   * 
   * @param pc
   *  The location at which the branch occurs.
   * @return 
   *  A set of likely destinations for that jump.
   */
  private Set<Integer> getLikelyJumpTargets(int pc) {
    return ps.branchInfo.getKnownBranchTargets(pc);
  }

  /**
   * Should a trace follow a branch and link instruction or should it terminate
   * the trace?
   * 
   * @param pc
   *  the address of the branch and link instruction
   * @return whether the trace should continue
   */
  public boolean traceContinuesAfterBranchAndLink(int pc) {
    return suitableToStop() == false;
  }

  /**
   * Load all the registers from the ProcessSpace into the pre-fill block
   */
  private void preFillAllRegisters() {
    OPT_BasicBlock temp = currentBlock;
    currentBlock = preFillBlock;
    fillAllRegisters();
    ps.memory.initTranslate(this); // Set up memory
    currentBlock = temp;
  }

  /**
   * Eliminate unnecessary register spill and fill code - ie a register wasn't
   * used so eliminate references to it
   */
  protected void eliminateRegisterFills(OPT_Register unusedRegisters[]) {
    if (unusedRegisters.length > 0) {
      OPT_BasicBlock curBB = gc.prologue;
      while (curBB != null) {
        OPT_Instruction curInstr = curBB.firstInstruction();
        loop_over_instructions: while (BBend.conforms(curInstr) == false) {
          for (Enumeration du = curInstr.getRootOperands(); du
              .hasMoreElements();) {
            OPT_Operand curOp = (OPT_Operand) du.nextElement();
            if (curOp.isRegister()) {
              OPT_Register curReg = curOp.asRegister().register;
              for (int i = 0; i < unusedRegisters.length; i++) {
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

  /**
   * Appends a system call to the current basic block.
   * 
   * @param lazy
   *  current translation laziness
   * @param pc
   *  address of system call instruction
   */
  public void appendSystemCall(Laziness lazy, int pc) {
    //We need to make sure that all registers contain their latest values, before
    //performing the actual system call.
    resolveLaziness(lazy);
    spillAllRegisters();

    // Plant call
    OPT_Instruction s = Call.create(CALL, null, null, null, null, 1);

    OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL(sysCallMethod
        .getMemberRef().asMethodReference(), sysCallMethod);

    OPT_Operand psRef = gc.makeLocal(1, psTref);
    Call.setParam(s, 0, psRef); // Reference to ps, sets 'this' pointer for
                                // doSysCall
    Call.setGuard(s, new OPT_TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s,
        new OPT_AddressConstantOperand(sysCallMethod.getOffset()));
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
   *  current translation laziness
   * @param pc
   *  the program counter of the bad instruction
   */
  public void appendThrowBadInstruction(Laziness lazy, int pc) {
    //We need to make sure that all registers contain their latest values, before
    //throwing the bad instruction exception.
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
    OPT_Instruction n = New.create(newOperator, eRef, typeOperand);
    n.position = gc.inlineSequence;
    n.bcIndex = DBT_Trace.BAD_INSTRUCTION_NEW;
    appendInstruction(n);
    
    OPT_Operand psRef = gc.makeLocal(1, psTref);
    OPT_Instruction c = Call.create(CALL, null, null, null, null, 3);
    OPT_MethodOperand methOp = OPT_MethodOperand.SPECIAL(
        badInstrKlassInitMethod.getMemberRef().asMethodReference(),
        badInstrKlassInitMethod);
    Call.setParam(c, 0, eRef.copy()); // 'this' pointer in
    // BadInstructionException.init
    Call.setParam(c, 1, new OPT_IntConstantOperand(pc));
    Call.setParam(c, 2, psRef);
    Call.setGuard(c, new OPT_TrueGuardOperand());
    Call.setMethod(c, methOp);
    Call.setAddress(c, new OPT_AddressConstantOperand(badInstrKlassInitMethod
        .getOffset()));
    c.position = gc.inlineSequence;
    c.bcIndex = DBT_Trace.BAD_INSTRUCTION_INIT;

    appendInstruction(c);

    OPT_Instruction t = Athrow.create(ATHROW, eRef.copyRO());
    t.position = gc.inlineSequence;
    t.bcIndex = DBT_Trace.BAD_INSTRUCTION_THROW;

    appendInstruction(t);

    setReturnValueResolveLazinessAndBranchToFinish(lazy,
        new OPT_IntConstantOperand(0xEBADC0DE));
  }

  /**
   * Plant a record uncaught branch call. NB register state won't get resolved
   * for call
   * 
   * @param pc
   *          the address of the branch instruction
   * @param destination
   *          the register operand holding the destination
   * @param code
   *          a code that can be a hint of the branch type
   */
  private void appendRecordUncaughtBranch(int pc, OPT_RegisterOperand destination, BranchType branchType) {
    // Is it sensible to record this information?
    if ((gc.options.getOptLevel() > 0)
        && (DBT_Options.plantUncaughtBranchWatcher)) {
      // Plant call
      OPT_Instruction s = Call.create(CALL, null, null, null, null, 4);
      OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL(
          recordUncaughtBranchMethod.getMemberRef().asMethodReference(),
          recordUncaughtBranchMethod);
      OPT_Operand psRef = gc.makeLocal(1, psTref);
      Call.setParam(s, 0, psRef); // Reference to ps, sets 'this' pointer
      Call.setParam(s, 1, new OPT_IntConstantOperand(pc)); // Address of branch
                                                            // instruction
      Call.setParam(s, 2, destination); // Destination of branch value
      Call.setParam(s, 3, new OPT_IntConstantOperand(branchType.ordinal())); // Branch code
                                                              // value
      Call.setGuard(s, new OPT_TrueGuardOperand());
      Call.setMethod(s, methOp);
      Call.setAddress(s, new OPT_AddressConstantOperand(
          recordUncaughtBranchMethod.getOffset()));
      s.position = gc.inlineSequence;
      s.bcIndex = DBT_Trace.RECORD_BRANCH;
      appendInstruction(s);
    }
  }

  /** Temporary int variables */
  private OPT_Register intTemps[];

  /**
   * Get/create a temporary int
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempInt(int num) {
    if (intTemps == null) {
      intTemps = new OPT_Register[10];
    }
    OPT_Register result = intTemps[num];
    if (result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempInt();
      intTemps[num] = regOp.register;
      return regOp;
    } else {
      return new OPT_RegisterOperand(result, VM_TypeReference.Int);
    }
  }

  /** Temporary long variables */
  private OPT_Register longTemps[];

  /**
   * Get/create a temporary long
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempLong(int num) {
    if (longTemps == null) {
      longTemps = new OPT_Register[10];
    }
    OPT_Register result = longTemps[num];
    if (result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempLong();
      longTemps[num] = regOp.register;
      return regOp;
    } else {
      return new OPT_RegisterOperand(result, VM_TypeReference.Long);
    }
  }

  /** Temporary intArray variables */
  private OPT_Register intArrayTemp;

  /**
   * Get/create a temporary intArray
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempIntArray(int num) {
    if (DBT.VerifyAssertions)
      DBT._assert(num == 0);

    OPT_Register result = intArrayTemp;
    if (result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTemp(VM_TypeReference.IntArray);
      intArrayTemp = regOp.register;
      return regOp;
    } else {
      return new OPT_RegisterOperand(result, VM_TypeReference.IntArray);
    }
  }

  /** Temporary float variables */
  private OPT_Register floatTemps[];

  /**
   * Get/create a temporary float
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempFloat(int num) {
    if (DBT.VerifyAssertions)
      DBT._assert((num == 0) || (num == 1));
    if (floatTemps == null) {
      floatTemps = new OPT_Register[2];
    }
    OPT_Register result = floatTemps[num];
    if (result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempFloat();
      floatTemps[num] = regOp.register;
      return regOp;
    } else {
      return new OPT_RegisterOperand(result, VM_TypeReference.Float);
    }
  }

  /** Temporary Double variables */
  private OPT_Register doubleTemp;

  /**
   * Get/create a temporary float
   * 
   * @param num
   *          a hint to allow for reuse of temps across instructions
   */
  public OPT_RegisterOperand getTempDouble(int num) {
    if (DBT.VerifyAssertions)
      DBT._assert(num == 0);
    OPT_Register result = doubleTemp;
    if (result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempDouble();
      doubleTemp = regOp.register;
      return regOp;
    } else {
      return new OPT_RegisterOperand(result, VM_TypeReference.Double);
    }
  }

  /** Temporary validation variables */
  private OPT_Register validationTemp;

  /**
   * Get/create a temporary validation variable
   * 
   * @param num
   *  a hint to allow for reuse of temps across instructions */
  public OPT_RegisterOperand getTempValidation(int num) {
    if (DBT.VerifyAssertions)
      DBT._assert(num == 0);

    OPT_Register result = validationTemp;
    if (result == null) {
      OPT_RegisterOperand regOp = gc.temps.makeTempValidation();
      validationTemp = regOp.register;
      return regOp;
    } else {
      return new OPT_RegisterOperand(result, VM_TypeReference.VALIDATION_TYPE);
    }
  }
  
  /**
   * Inserts code into the current block that will use the process space's interpreter 
   * to execute the given instructions.
   *  
   * @param pc
   *  The address of the instruction that shall be executed
   *  
   * @param lazy
   *  The current laziness state.
   */
  public void appendInterpretedInstruction(int pc, Laziness lazy) {
    resolveLaziness(lazy);
    spillAllRegisters();
    
    //Prepare a local variable of type Interpreter
    VM_TypeReference interpreterTypeRef = VM_TypeReference.findOrCreate(Interpreter.class);
    OPT_RegisterOperand interpreter = gc.temps.makeTemp(interpreterTypeRef);

    //Plant a call to createInstructionInterpreter().
    OPT_Instruction s = Call.create(CALL, null, null, null, null, 1);
    
    VM_MethodReference getInterpreterMethodRef = (VM_MethodReference) VM_MemberReference
    .findOrCreate(psTref, VM_Atom.findOrCreateAsciiAtom("createInstructionInterpreter"),
        VM_Atom.findOrCreateAsciiAtom("()A"));
    VM_Method getInterpreterMethod = getInterpreterMethodRef.resolveInterfaceMethod();

    OPT_MethodOperand methOp = OPT_MethodOperand.INTERFACE(getInterpreterMethodRef, getInterpreterMethod);
    OPT_Operand psRef = gc.makeLocal(1, psTref);
    
    if (DBT.VerifyAssertions) DBT._assert(psRef != null && getInterpreterMethod != null && interpreter != null && methOp != null);
    
    Call.setParam(s, 0, psRef);
    Call.setGuard(s, new OPT_TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new OPT_AddressConstantOperand(getInterpreterMethod.getOffset()));
    Call.setResult(s, interpreter);
    
    s.position = gc.inlineSequence;
    s.bcIndex = DBT_Trace.DO_SYSCALL;
    
    appendInstruction(s);
    
    //then use the returned instruction interpreter to interpret the instruction
    VM_TypeReference instructionTypeRef = VM_TypeReference.findOrCreate(Interpreter.Instruction.class);
    OPT_RegisterOperand instruction = gc.temps.makeTemp(instructionTypeRef);

    s = Call.create(CALL, null, null, null, null, 1);
    
    VM_MethodReference decodeMethodRef = (VM_MethodReference) VM_MemberReference
    .findOrCreate(interpreterTypeRef, VM_Atom.findOrCreateAsciiAtom("decode"),
        VM_Atom.findOrCreateAsciiAtom("(I)A"));
    VM_Method decodeMethod = decodeMethodRef.resolveInterfaceMethod();

    methOp = OPT_MethodOperand.INTERFACE(decodeMethodRef, decodeMethod);
    
    if (DBT.VerifyAssertions) DBT._assert(decodeMethod != null && methOp != null && instruction != null && interpreter != null);
    
    Call.setParam(s, 0, interpreter);
    Call.setGuard(s, new OPT_TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new OPT_AddressConstantOperand(decodeMethod.getOffset()));
    Call.setResult(s, instruction);
    
    s.position = gc.inlineSequence;
    s.bcIndex = DBT_Trace.DO_SYSCALL;
    
    appendInstruction(s);
    
    //finally, call the execute method on the instruction
    VM_MethodReference executeMethodRef = (VM_MethodReference) VM_MemberReference
    .findOrCreate(instructionTypeRef, VM_Atom.findOrCreateAsciiAtom("execute"),
        VM_Atom.findOrCreateAsciiAtom("()V"));
    VM_Method executeMethod = executeMethodRef.resolveInterfaceMethod();

    s = Call.create(CALL, null, null, null, null, 1);
    methOp = OPT_MethodOperand.INTERFACE(executeMethodRef, executeMethod);
    
    if (DBT.VerifyAssertions) DBT._assert(executeMethod != null && methOp != null && instruction != null);
    
    Call.setParam(s, 0, instruction);
    Call.setGuard(s, new OPT_TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new OPT_AddressConstantOperand(executeMethod.getOffset()));
    
    s.position = gc.inlineSequence;
    s.bcIndex = DBT_Trace.DO_SYSCALL;
    appendInstruction(s);
    
    // Fill all registers again following interpreted instruction
    fillAllRegisters();
  }

  /** Get the method*/
  public DBT_Trace getMethod() {
    return (DBT_Trace) gc.method;
  }

  /** Make a temporary register */
  public OPT_RegisterOperand makeTemp(VM_TypeReference type) {
    return gc.temps.makeTemp(type);
  }
  
  /** Report some debug output */
  protected abstract void report(String str);

  /** Create the initial object for capturing lazy information*/
  protected abstract Laziness createInitialLaziness();
  
  /**
   * Plant instructions modifying a lazy state into one with no laziness
   * 
   * @param laziness
   *          the laziness to modify
   */
  public abstract void resolveLaziness(Laziness laziness);
  
  /**
   * Translate the instruction at the given pc
   * 
   * @param lazy
   *          the status of the lazy evaluation
   * @param pc
   *          the program counter for the instruction
   * @return the next instruction address or -1
   */
  protected abstract int translateInstruction(Laziness lazy, int pc);
  
  /**
   * Fill all the registers from the ProcessSpace, that is take the register
   * values from the process space and place them in the traces registers.
   */
  protected abstract void fillAllRegisters();

  /**
   * Spill all the registers, that is put them from the current running trace
   * into the process space
   */
  protected abstract void spillAllRegisters();

  /** Return an array of unused registers */
  protected abstract OPT_Register[] getUnusedRegisters();
}
