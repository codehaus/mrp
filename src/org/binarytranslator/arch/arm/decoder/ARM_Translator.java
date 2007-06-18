package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder.ARM_InstructionFactory;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.OperandWrapper;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.DataProcessing.Opcode;
import org.binarytranslator.arch.arm.decoder.ARM_Instructions.Instruction.Condition;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.arch.arm.os.process.ARM_Registers.OperatingMode;
import org.binarytranslator.generic.branch.BranchLogic.BranchType;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.*;

public class ARM_Translator implements OPT_Operators {

  /** The process space that we're interpreting.*/
  protected final ARM_ProcessSpace ps;
  
  /** The ARM translation class. */
  protected final ARM2IR arm2ir;
  
  /** The current laziness state. */
  protected ARM_Laziness lazy;
  
  /** The current pc that we're translating. */
  protected int pc;
  
  /** A shortcut to the ARM registers within the process space. */
  protected final ARM_Registers regs = null;
  
  private final ARM_InstructionDecoder.ARM_InstructionFactory<ARM_Instruction> translatorFactory = new TranslatorFactory();

  public ARM_Translator(ARM_ProcessSpace ps, ARM2IR arm2ir) {
    this.ps = ps;
    this.arm2ir = arm2ir;
  }
  
  /** Returns true if we're currently executing thumb instructions, false otherwise. */
  public boolean inThumb() {
    return (pc & 0x1) == 1;
  }
  
  public int readPC() {
    if (inThumb()) {
      return (pc + 4) & 0xFFFFFFFE;
    }
    else {
      return pc + 8;
    }
  }
  
  public int translateInstruction(int pc, ARM_Laziness lazy) {
    this.pc = pc;
    this.lazy = lazy;
    boolean thumb = (pc & 1) != 0;
    int instruction;
    
    try {
      if (thumb)
        instruction = ps.memory.loadInstruction16(pc & 0xFFFFFFFE);
      else
        instruction = ps.memory.loadInstruction32(pc);
    }
    catch (NullPointerException e) {
      if (DBT_Options.debugTranslation)
        System.out.print(String.format("Exception while reading instruction from: 0x%x. ", pc));
        
      if (arm2ir.getNumInstructions() == 0) {
        if (DBT_Options.debugTranslation)
          System.out.println("Planting exception instead.");
        
        arm2ir.appendThrowBadInstruction(lazy, pc);
        return -1;
      }
      else {
        if (DBT_Options.debugTranslation)
          System.out.println("Planting return from trace instead.");
        
        arm2ir.appendTraceExit(lazy, new OPT_IntConstantOperand(pc));
        return -1;
      }
    }
    ARM_Instruction instr;
    
    if (thumb)
      instr = ARM_InstructionDecoder.Thumb.decode((short)instruction, translatorFactory);
    else
      instr = ARM_InstructionDecoder.ARM32.decode(instruction, translatorFactory);
    
    if (instr.getCondition() != Condition.AL) {
      instr = new ConditionalDecorator(instr);
    }
    
    instr.translate();
    return instr.getSuccessor(pc);
  }
  
  /**
   * Creates an HIR instruction that will call method <code>methodName</code> in the current ARM registers class, 
   * which has the signature <code>signature</code> and takes <code>numParameters</code> parameters.
   * 
   * @param methodName
   *  The name of the method to call.
   * @param signature
   *  The method's signature
   * @param numParameters
   *  The number of parameters the method takes.
   * @return
   *  An HIR instruction that will call the given method.
   */
  private OPT_Instruction createCallToRegisters(String methodName, String signature, int numParameters) {

    VM_TypeReference RegistersType = VM_TypeReference
        .findOrCreate(ARM_Registers.class);

    VM_MethodReference methodRef = VM_MemberReference
        .findOrCreate(RegistersType,
            VM_Atom.findOrCreateAsciiAtom(methodName),
            VM_Atom.findOrCreateAsciiAtom(signature)).asMethodReference();

    VM_Method method = methodRef.resolve();

    OPT_Instruction call = Call.create(CALL, null, null, null, null, numParameters + 1);
    OPT_MethodOperand methOp = OPT_MethodOperand.VIRTUAL(methodRef, method);
    
    OPT_RegisterOperand thisOperand = arm2ir.getArmRegistersReference();

    Call.setParam(call, 0, thisOperand);
    Call.setGuard(call, new OPT_TrueGuardOperand());
    Call.setMethod(call, methOp);
    Call.setAddress(call, new OPT_AddressConstantOperand(method
        .getOffset()));

    return call;
  }
  
  /** Some ARM instructions can use several addressing modes. Therefore, the ARM Decoder uses an
   * {@link OperandWrapper}, that abstracts these differences. The <code>ResolvedOperand</code> class
   * can be used to resolve an <code>OperandWrapper</code> into an actual HIR operand.*/
  private abstract static class ResolvedOperand {

    /** Stores the value that the operand resolves to. */
    protected OPT_Operand value;

    /** A backlink to the {@link ARM_Translator} class that is using this ResolvedOperand instance. */
    protected ARM_Translator translator;

    /**
     * Call this function to create code that converts the <code>operand</code> into an
     * HIR <code>OPT_Operand</code>.
     *  
     * @param translator
     *  
     * @param operand
     *  The operand that is to be converted.
     * @return
     *  An HIR operand that represents the resolved operand.
     */
    public static OPT_Operand resolve(ARM_Translator translator,
        OperandWrapper operand) {
      ResolvedOperand result = new ResolvedOperand_WithoutShifterCarryOut(
          translator, operand);
      return result.getValue().copy();
    }

    /**
     * Works similar to {@link #resolve(ARM_Translator, OperandWrapper)}, but also calculates the
     * shifter-carry-out that the ARM barell shifter would produce while resolving this operand.
     * The shifter-carry-out is directly written into the <code>translator</code>'s carry flag.
     * @param translator
     *  The translator instance within which the code for the said conversion is to be created.
     * @param operand
     *  The operand that is to be converted.
     * @return
     *  An HIR operand that represents the resolved operand.
     */
    public static OPT_Operand resolveAndStoreShifterCarryOutToCarry(
        ARM_Translator translator, OperandWrapper operand) {
      
      ResolvedOperand result = new ResolvedOperand_WithShifterCarryOut(translator, operand);
      return result.getValue();
    }

    public final OPT_Operand getValue() {
      return value;
    }

    /** A subclass that resolves an {@link OperandWrapper} without calculating the shifter
     * carry out produced by ARM's barrel shifter. */
    private static class ResolvedOperand_WithoutShifterCarryOut extends
        ResolvedOperand {

      private ResolvedOperand_WithoutShifterCarryOut(ARM_Translator translator,
          OperandWrapper operand) {
        this.translator = translator;
        _resolve(operand);
      }

      private void _resolve(OperandWrapper operand) {

        switch (operand.getType()) {
        case Immediate:
          value = new OPT_IntConstantOperand(operand.getImmediate());
          return;

        case Register:
          int reg = operand.getRegister();

          if (reg == 15) {
            // mind the ARM pc offset
            value = new OPT_IntConstantOperand( translator.readPC() );
            return;
          }

          value = translator.arm2ir.getRegister(reg);
          return;

        case RegisterShiftedRegister:
        case ImmediateShiftedRegister:
          value = resolveShift(operand);
          return;

        case RegisterOffset:
          if (operand.getRegister() == ARM_Registers.PC) {
            value = new OPT_IntConstantOperand(translator.readPC() + operand.getOffset());
          }
          else {
            value = translator.arm2ir.getTempInt(9);
            translator.arm2ir.appendInstruction(Binary.create(INT_ADD, (OPT_RegisterOperand)value, translator.arm2ir.getRegister(operand.getRegister()), new OPT_IntConstantOperand(operand.getOffset())));
          }
          return;

        default:
          throw new RuntimeException("Unexpected wrapped operand type: "
              + operand.getType());
        }
      }

      /**
       * If the given OperandWrapper involves shifting a register, then this
       * function will decoder the shift and set the result of the barrel
       * shifter accordingly.
       */
      private final OPT_Operand resolveShift(OperandWrapper operand) {
        if (DBT.VerifyAssertions)
          DBT._assert(operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister
                  || operand.getType() == OperandWrapper.Type.RegisterShiftedRegister);

        // consider the "usual" ARM program counter offset
        OPT_Operand shiftedOperand;
        if (operand.getRegister() == 15)
          shiftedOperand = new OPT_IntConstantOperand( translator.readPC() );
        else
          shiftedOperand = translator.arm2ir.getRegister(operand.getRegister());

        OPT_Operand shiftAmount;

        if (operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister) {
          // the amount of shift is a constant
          shiftAmount = new OPT_IntConstantOperand(operand.getShiftAmount());
        } else {
          // the amount of shifting is determined by a register
          shiftAmount = translator.arm2ir.getRegister(operand.getShiftingRegister());
          OPT_RegisterOperand shiftAmountAsByte = translator.arm2ir.getTempInt(7);
          translator.arm2ir.appendInstruction(Binary.create(INT_AND, shiftAmountAsByte, shiftAmount, new OPT_IntConstantOperand(0xFF)));
          shiftAmount = shiftAmountAsByte;
        }

        OPT_RegisterOperand resultRegister = translator.arm2ir.getTempInt(9);
        OPT_RegisterOperand validation = translator.arm2ir.getTempValidation(0);
        
        OPT_BasicBlock nextBlock = translator.arm2ir.createBlockAfterCurrent();
        OPT_BasicBlock curBlock = translator.arm2ir.getCurrentBlock();
        OPT_BasicBlock block1, block2;

        switch (operand.getShiftType()) {
        case ASR:
          /*
           * shiftedOperand >> shiftAmount;
           */
          block2 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block1 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          
          //current block
          curBlock.deleteNormalOut();
          curBlock.insertOut(block1);
          curBlock.insertOut(block2);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount.copy(), new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block2.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - normal case
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_SHR, resultRegister, shiftedOperand.copy(), shiftAmount.copy()));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 2 - shift >= 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(
              INT_SHR, resultRegister.copyRO(), shiftedOperand.copy(), new OPT_IntConstantOperand(31)));
          break;

        case LSL:
          /*
           * value = shiftedOperand << shiftAmount;
           */
          block2 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block1 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          
          //current block
          curBlock.deleteNormalOut();
          curBlock.insertOut(block1);
          curBlock.insertOut(block2);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount.copy(), new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block2.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - normal case
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_SHL, resultRegister, shiftedOperand.copy(), shiftAmount.copy()));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 2 - shift >= 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister.copyRO(), new OPT_IntConstantOperand(0)) );
          break;

        case LSR:

          /*
           * value = shiftedOperand >>> shiftAmount;
 
           */
          block2 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block1 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          
          //current block
          curBlock.deleteNormalOut();
          curBlock.insertOut(block1);
          curBlock.insertOut(block2);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount.copy(), new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block2.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - normal case
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_USHR, resultRegister, shiftedOperand.copy(), shiftAmount.copy()));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 2 - shift >= 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister.copyRO(), new OPT_IntConstantOperand(0)) );
          break;

        case ROR:
          /*
           * return Integer.rotateRight(value, shiftAmount);
           */
          translator.arm2ir.appendRotateRight(resultRegister, shiftedOperand.copy(), shiftAmount.copy());
          break;

        case RRX:
          /*
           * result = shiftedOperand >>> 1;
           * if (regs.isCarrySet()) result |= 0x80000000;
           */
          
          block1 = translator.arm2ir.createBlockAfterCurrentNotInCFG();

          // Current block
          curBlock.deleteNormalOut();
          curBlock.insertOut(nextBlock);
          curBlock.insertOut(block1);
          translator.arm2ir.appendInstruction(Binary.create(INT_USHR, resultRegister, shiftedOperand.copy(), new OPT_IntConstantOperand(1)));
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, translator.arm2ir.getTempValidation(0), translator.arm2ir.getCarryFlag(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.NOT_EQUAL(), nextBlock.makeJumpTarget(), new OPT_BranchProfileOperand()));
          
          //Block 1
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_OR, resultRegister.copyRO(), resultRegister.copy(), new OPT_IntConstantOperand(0x80000000)));
          break;

        default:
          throw new RuntimeException("Unexpected shift type: "
              + operand.getShiftType());
        }
        
        translator.arm2ir.setCurrentBlock(nextBlock);
        return resultRegister;
      }
    }

    /** 
     * This class resolves an {@link OperandWrapper} and also writes the shifter carry out, as 
     * produced by ARM's barrel shifter, into the Carry flag.*/
    private static class ResolvedOperand_WithShifterCarryOut extends
        ResolvedOperand {

      private ResolvedOperand_WithShifterCarryOut(ARM_Translator translator,
          OperandWrapper operand) {
        this.translator = translator;
        _resolve(operand);
      }
      private void _resolve(OperandWrapper operand) {

        switch (operand.getType()) {
        case Immediate:
          value = new OPT_IntConstantOperand(operand.getImmediate());
          
          if (operand.getShiftAmount() != 0) {            
            OPT_RegisterOperand carryFlag = translator.arm2ir.getCarryFlag();
            OPT_Operand shifterCarryOut = new OPT_IntConstantOperand(((operand.getImmediate() & 0x80000000) != 0) ? 1 : 0);
            
            //otherwise there is no shifter carry out
            translator.arm2ir.appendInstruction(Move.create(INT_MOVE, carryFlag, shifterCarryOut));
          }          
          return;

        case Register:
          //in this case, there is no special shifter carry out (i.e. it equals the carry flag).
          int reg = operand.getRegister();

          if (reg == 15) {
            // mind the ARM pc offset
            value = new OPT_IntConstantOperand( translator.readPC() );
            return;
          }

          value = translator.arm2ir.getRegister(reg);
          return;

        case RegisterShiftedRegister:
        case ImmediateShiftedRegister:
          value = resolveShift(operand);
          return;

        case RegisterOffset:
          throw new RuntimeException("This operand type does not produce a shifter carry out.");

        default:
          throw new RuntimeException("Unexpected wrapped operand type: "
              + operand.getType());
        }
      }
      
      /** Returns the register that receives the shifte carry out*/
      private OPT_RegisterOperand getShifterCarryOutTarget() {
        return translator.arm2ir.getCarryFlag();
      }

      /**
      * If the given OperandWrapper involves shifting a register, then this
      * function will decoder the shift and set the result of the barrel shifter
      * accordingly.
      */
      private final OPT_Operand resolveShift(OperandWrapper operand) {
        if (DBT.VerifyAssertions)
          DBT
              ._assert(operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister
                  || operand.getType() == OperandWrapper.Type.RegisterShiftedRegister);

        // consider the "usual" ARM program counter offset
        OPT_Operand shiftedOperand;
        if (operand.getRegister() == 15)
          shiftedOperand = new OPT_IntConstantOperand( translator.readPC() );
        else
          shiftedOperand = translator.arm2ir.getRegister(operand.getRegister());

        OPT_Operand shiftAmount;

        if (operand.getType() == OperandWrapper.Type.ImmediateShiftedRegister) {
          // the amount of shift is a constant
          shiftAmount = new OPT_IntConstantOperand(operand.getShiftAmount());
        } else {
          // the amount of shifting is determined by a register
          shiftAmount = translator.arm2ir.getRegister(operand.getShiftingRegister());
          OPT_RegisterOperand shiftAmountAsByte = translator.arm2ir.getTempInt(7);
          translator.arm2ir.appendInstruction(Binary.create(INT_AND, shiftAmountAsByte, shiftAmount, new OPT_IntConstantOperand(0xFF)));
          shiftAmount = shiftAmountAsByte;
        }

        OPT_RegisterOperand resultRegister = translator.arm2ir.getTempInt(8);
        OPT_RegisterOperand tmp = translator.arm2ir.getTempInt(9);
        OPT_RegisterOperand validation = translator.arm2ir.getTempValidation(0);
        
        OPT_BasicBlock nextBlock = translator.arm2ir.createBlockAfterCurrent();
        OPT_BasicBlock curBlock = translator.arm2ir.getCurrentBlock();
        OPT_BasicBlock block1, block2, block3, block4, block5, block6;

        switch (operand.getShiftType()) {
        case ASR:
          block4 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block3 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block2 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block1 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          
          //current block
          curBlock.deleteNormalOut();
          curBlock.insertOut(block1);
          curBlock.insertOut(block4);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), block4.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - shift != 0
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(block2);
          block1.insertOut(block3);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation.copyRO(), shiftAmount.copy(), new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block3.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 2 - shift < 32 && shift != 0
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_SHR, resultRegister, shiftedOperand, shiftAmount.copy()) );
          translator.arm2ir.appendInstruction(Binary.create(INT_ADD, tmp, shiftAmount.copy(), new OPT_IntConstantOperand(-1)) );
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, tmp.copy());
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 3 - shift >= 32
          translator.arm2ir.setCurrentBlock(block3);
          block3.insertOut(nextBlock);
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, 31);
          translator.arm2ir.appendInstruction(Binary.create(INT_MUL, resultRegister.copyRO(), getShifterCarryOutTarget(), new OPT_IntConstantOperand(-1)) ); //creates either 0xFFFFFFFF if the bit is set, or 0 otherwise
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 4 - shift == 0
          translator.arm2ir.setCurrentBlock(block4);
          block4.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister.copyRO(), shiftedOperand.copy()));
          break;

        case LSL:
          /*
           * value = shiftedOperand << shiftAmount;
           */
          block6 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block5 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block4 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block3 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block2 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block1 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          
          //current block
          curBlock.deleteNormalOut();
          curBlock.insertOut(block6);
          curBlock.insertOut(block1);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), block6.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - shift != 0
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(block2);
          block1.insertOut(block3);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation.copyRO(), shiftAmount.copy(), new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block3.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 2 - Shift != 0 && Shift < 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_SUB, tmp, new OPT_IntConstantOperand(32), shiftAmount) );
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand.copy(), tmp.copy());
          translator.arm2ir.appendInstruction(Binary.create(INT_SHL, resultRegister, shiftedOperand.copy(), shiftAmount.copy()));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 3 - Shift >= 32
          translator.arm2ir.setCurrentBlock(block3);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister.copyRO(), new OPT_IntConstantOperand(0)) );
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation.copyRO(), shiftAmount.copy(), new OPT_IntConstantOperand(32), OPT_ConditionOperand.EQUAL(), block5.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          block3.insertOut(block4);
          block3.insertOut(block5);
          
          //block 4 - Shift > 32
          translator.arm2ir.setCurrentBlock(block4);
          block4.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, getShifterCarryOutTarget(), new OPT_IntConstantOperand(0)) );
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
                    
          //block 5 - Shift == 32
          translator.arm2ir.setCurrentBlock(block5);
          block5.insertOut(nextBlock);
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand.copy(), 0);
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 6 - shift == 0
          translator.arm2ir.setCurrentBlock(block6);
          block6.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister.copyRO(), shiftedOperand.copy()));
          break;

        case LSR:

          /*
           * value = shiftedOperand >>> shiftAmount;
           */
          block6 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block5 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block4 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block3 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block2 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block1 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          
          //current block
          curBlock.deleteNormalOut();
          curBlock.insertOut(block6);
          curBlock.insertOut(block1);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), block6.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - shift != 0
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(block2);
          block1.insertOut(block3);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation.copyRO(), shiftAmount.copy(), new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block3.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 2 - Shift != 0 && Shift < 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_ADD, tmp, shiftAmount.copy(), new OPT_IntConstantOperand(-1)));
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand.copy(), tmp.copy());
          translator.arm2ir.appendInstruction(Binary.create(INT_USHR, resultRegister, shiftedOperand.copy(), shiftAmount.copy()));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 3 - Shift >= 32
          translator.arm2ir.setCurrentBlock(block3);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister.copyRO(), new OPT_IntConstantOperand(0)) );
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation.copyRO(), shiftAmount.copy(), new OPT_IntConstantOperand(32), OPT_ConditionOperand.EQUAL(), block5.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          block3.insertOut(block4);
          block3.insertOut(block5);
          
          //block 4 - Shift > 32
          translator.arm2ir.setCurrentBlock(block4);
          block4.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, getShifterCarryOutTarget(), new OPT_IntConstantOperand(0)) );
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
                    
          //block 5 - Shift == 32
          translator.arm2ir.setCurrentBlock(block5);
          block5.insertOut(nextBlock);
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand.copy(), 31);
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 6 - shift == 0
          translator.arm2ir.setCurrentBlock(block6);
          block6.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister.copyRO(), shiftedOperand.copy()));
          break;

        case ROR:
          /*
           * return Integer.rotateRight(value, shiftAmount);
           */
          block2 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          block1 = translator.arm2ir.createBlockAfterCurrentNotInCFG();
          
          //current block
          curBlock.deleteNormalOut();
          curBlock.insertOut(block1);
          curBlock.insertOut(block2);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), block2.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendRotateRight(resultRegister, shiftedOperand.copy(), shiftAmount.copy());
          translator.arm2ir.appendInstruction(Binary.create(INT_ADD, tmp, shiftAmount.copy(), new OPT_IntConstantOperand(-1)) );
          translator.arm2ir.appendInstruction(Binary.create(INT_AND, tmp.copyRO(), tmp.copy(), new OPT_IntConstantOperand(0x1F)) );
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand.copy(), tmp.copy());
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 2
          translator.arm2ir.setCurrentBlock(block2);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister.copyRO(), shiftedOperand.copy()));
          break;

        case RRX:
          /*
           * value = shiftedOperand >>> 1;
           * if (regs.isCarrySet()) value |= 0x80000000;
           */
          block1 = translator.arm2ir.createBlockAfterCurrentNotInCFG();

          //Current Block
          curBlock.deleteNormalOut();
          curBlock.insertOut(block1);
          curBlock.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_USHR, resultRegister, shiftedOperand.copy(), new OPT_IntConstantOperand(1)));       
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, translator.arm2ir.getCarryFlag(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.NOT_EQUAL(), nextBlock.makeJumpTarget(), new OPT_BranchProfileOperand()));
          
          //Block 1
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_OR, resultRegister.copyRO(), resultRegister.copy(), new OPT_IntConstantOperand(0x80000000)));
          
          //nextBlock
          translator.arm2ir.setCurrentBlock(nextBlock);
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand.copy(), 0);
          break;

        default:
          throw new RuntimeException("Unexpected shift type: "
              + operand.getShiftType());
        }

        translator.arm2ir.setCurrentBlock(nextBlock);
        return resultRegister;
      }
    }
  }

  /** All ARM interpreter instructions implement this interface. */
  private interface ARM_Instruction  {
    
    /** Returns the condition under which this instruction shall be executed. */
    public Condition getCondition();
    
    /** performs the actual translation.*/
    void translate();
    
    /** Return the instruction following this one or -1, if that is not yet known. */
    int getSuccessor(int pc);
  }

  /** All ARM instructions that are supposed to be executed conditionally 
   * are decorated with this decorator. 
   * The decorator takes care of checking the individual condition and depending on it, executing the
   * instruction (or not). The instruction classes itself do not check any conditions. */
  private final class ConditionalDecorator implements ARM_Instruction {

    protected final ARM_Instruction conditionalInstruction;
    protected final Condition condition;

    /** Decorates an ARM interpreter instruction, by making it execute conditionally. */
    protected ConditionalDecorator(ARM_Instruction i) {
      conditionalInstruction = i;
      this.condition = i.getCondition();
    }
    
    public int getSuccessor(int pc) {
      return pc + 4;
    }
    
    public Condition getCondition() {
      return condition;
    }
    
    public void translate() {
      //conditionals are implemented easily: if the condition does not hold, then just
      //jump to the block following the conditional instruction
      OPT_BasicBlock nextInstruction = arm2ir.getNextBlock();
      OPT_BasicBlock condBlock = arm2ir.createBlockAfterCurrent(); 
      arm2ir.getCurrentBlock().deleteNormalOut();
      arm2ir.getCurrentBlock().insertOut(nextInstruction);
      arm2ir.getCurrentBlock().insertOut(condBlock);
      
      switch (condition) {
      case AL:
        throw new RuntimeException("ARM32 instructions with a condition of AL (always) should not be decorated with a ConditionalDecorator.");
        
      case CC:
        //return !regs.isCarrySet();
        translateCondition(nextInstruction, arm2ir.getCarryFlag(), OPT_ConditionOperand.NOT_EQUAL());
        break;
        
      case CS:
        //return regs.isCarrySet();
        translateCondition(nextInstruction, arm2ir.getCarryFlag(), OPT_ConditionOperand.EQUAL());
        break;
        
      case EQ:
        //return regs.isZeroSet();
        translateCondition(nextInstruction, arm2ir.getZeroFlag(), OPT_ConditionOperand.EQUAL());
        break;
        
      case GE:
        //return regs.isNegativeSet() == regs.isOverflowSet();
        translateCondition(nextInstruction, arm2ir.getNegativeFlag(), OPT_ConditionOperand.EQUAL(), arm2ir.getOverflowFlag());
        break;
        
      case GT:
        translateCondition_GT(nextInstruction);
        break;
        
      case HI:
        translateCondition_HI(nextInstruction);
        break;
        
      case LE:
        translateCondition_LE(nextInstruction);
        break;
        
      case LS:
        translateCondition_LS(nextInstruction, condBlock);
        break;
        
      case LT:
        //return regs.isNegativeSet() != regs.isOverflowSet();
        translateCondition(nextInstruction, arm2ir.getNegativeFlag(), OPT_ConditionOperand.NOT_EQUAL(), arm2ir.getOverflowFlag());
        break;
        
      case MI:
        //return regs.isNegativeSet();
        translateCondition(nextInstruction, arm2ir.getNegativeFlag(), OPT_ConditionOperand.EQUAL());
        break;
        
      case NE:
        //return !regs.isZeroSet();
        translateCondition(nextInstruction, arm2ir.getZeroFlag(), OPT_ConditionOperand.NOT_EQUAL());
        break;
        
      case NV:
        //never execute this instruction
        translateCondition(nextInstruction, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL());
        break;
        
      case PL:
        //return !regs.isNegativeSet();
        translateCondition(nextInstruction, arm2ir.getNegativeFlag(), OPT_ConditionOperand.NOT_EQUAL());
        break;
        
      case VC:
        //return !regs.isOverflowSet();
        translateCondition(nextInstruction, arm2ir.getOverflowFlag(), OPT_ConditionOperand.NOT_EQUAL());
        break;
        
      case VS:
        //return regs.isOverflowSet();
        translateCondition(nextInstruction, arm2ir.getOverflowFlag(), OPT_ConditionOperand.EQUAL());
        break;
        
        default:
          throw new RuntimeException("Unexpected condition code: " + condition);
      }

      arm2ir.setCurrentBlock(condBlock);
      conditionalInstruction.translate();
    }
    
    private void translateCondition(OPT_BasicBlock nextInstruction, OPT_Operand operand, OPT_ConditionOperand condition) {
      translateCondition(nextInstruction, operand, condition, new OPT_IntConstantOperand(1));
    }
    
    private void translateCondition(OPT_BasicBlock nextInstruction, OPT_Operand lhs, OPT_ConditionOperand condition, OPT_Operand rhs) {
      
      condition = condition.flipCode();
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), lhs, rhs, condition, nextInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }
    
    private void translateCondition_HI(OPT_BasicBlock nextInstruction) {
      //return regs.isCarrySet() && !regs.isZeroSet();
      OPT_Operand carry = arm2ir.getCarryFlag();
      OPT_Operand zero = arm2ir.getZeroFlag();
      OPT_RegisterOperand result = arm2ir.getGenerationContext().temps.makeTempBoolean();
      
      arm2ir.appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_OR, result, carry,
          new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand(), zero, new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), result.copy(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), nextInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }
    
    private void translateCondition_LS(OPT_BasicBlock nextInstruction, OPT_BasicBlock actualInstruction) {
      //return !regs.isCarrySet() || regs.isZeroSet();
      OPT_Operand carry = arm2ir.getCarryFlag();
      OPT_Operand zero = arm2ir.getZeroFlag();
      
      OPT_RegisterOperand result = arm2ir.getTempInt(0);
      
      arm2ir.appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, result, carry, new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand(), zero, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), result.copy(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), nextInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
      
/*      cond1.deleteNormalOut();
      cond1.insertOut(cond2);
      cond1.insertOut(actualInstruction);
      
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), carry, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), actualInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));

      arm2ir.setCurrentBlock(cond2);
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), zero, new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), actualInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
      arm2ir.appendInstruction(Goto.create(GOTO, nextInstruction.makeJumpTarget()));
      cond2.deleteNormalOut();
      cond2.insertOut(nextInstruction);
      cond2.insertOut(actualInstruction);*/
    }
    
    private void translateCondition_GT(OPT_BasicBlock nextInstruction) {
      //return (regs.isNegativeSet() == regs.isOverflowSet()) && !regs.isZeroSet();
      OPT_Operand negative = arm2ir.getNegativeFlag();
      OPT_Operand overflow = arm2ir.getOverflowFlag();
      OPT_Operand zero = arm2ir.getZeroFlag();
      OPT_RegisterOperand result = arm2ir.getGenerationContext().temps.makeTempBoolean();
      
      arm2ir.appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_OR, result, negative,
          overflow, OPT_ConditionOperand.NOT_EQUAL(), new OPT_BranchProfileOperand(), zero, new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), result.copy(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), nextInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }
    
    private void translateCondition_LE(OPT_BasicBlock nextInstruction) {
      //return regs.isZeroSet() || (regs.isNegativeSet() != regs.isOverflowSet());
      OPT_Operand negative = arm2ir.getNegativeFlag();
      OPT_Operand overflow = arm2ir.getOverflowFlag();
      OPT_Operand zero = arm2ir.getZeroFlag();
      OPT_RegisterOperand result = arm2ir.getGenerationContext().temps.makeTempBoolean();
      
      arm2ir.appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, result, negative,
          overflow, OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand(), zero, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), result.copy(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), nextInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }
    
    @Override
    public String toString() {
      return conditionalInstruction.toString();
    }
  }

  /** A base class for all data processing interpreter instructions, including CLZ.*/
  private abstract class DataProcessing 
      implements ARM_Instruction {
    
    protected final ARM_Instructions.DataProcessing i;

    protected DataProcessing(ARM_Instructions.DataProcessing instr) {
      i = instr;
    }

    /** Returns the value of operand 1 of the data processing instruction. This is always a register value. */
    protected OPT_Operand resolveOperand1() {

      if (i.Rn == ARM_Registers.PC) {
        return new OPT_IntConstantOperand( readPC() );
      }

      return arm2ir.getRegister(i.Rn);
    }

    /** Returns the value of the rhs-operand of the data processing instruction. */
    protected OPT_Operand resolveOperand2() {
      return ResolvedOperand.resolve(ARM_Translator.this, i.operand2);
    }
    
    /** Returns the register into which the result of a data processing operation shall be stored. */
    protected OPT_RegisterOperand getResultRegister() {
      //return arm2ir.getRegister(Rd);
      return arm2ir.getGenerationContext().temps.makeTempInt();
    } 

    public abstract void translate();

    /** Sets the processor flags according to the result of adding <code>lhs</code> and <code>rhs</code>.*/
    protected final void setAddResult(OPT_RegisterOperand result, OPT_Operand lhs, OPT_Operand rhs) {

      if (i.updateConditionCodes) {
        if (i.Rd != 15) {
          setAddFlags(result, lhs, rhs);
        } 
        else {
          OPT_Instruction s = createCallToRegisters("restoreSPSR2CPSR", "()V", 0);
          arm2ir.appendCustomCall(s);
        }
      }
      
      if (i.Rd == 15) {
        
        if (i.updateConditionCodes)
          arm2ir.appendBranch(result, lazy, BranchType.INDIRECT_BRANCH);
        else 
          arm2ir.appendTraceExit(lazy, result);
      }
      else {
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(i.Rd), result.copy()) );
      }
    }

    /**
     * Sets the flags according to the result of an add operation.
     * @param result
     *  The result of the add operation.
     * @param lhs
     *  The left-hand-side operator.
     * @param rhs
     *  The add's right-hand-side operator.
     */
    protected final void setAddFlags(OPT_Operand result, OPT_Operand lhs, OPT_Operand rhs) {
      //set the carry flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getCarryFlag(), result.copy(), lhs.copy(), OPT_ConditionOperand.LOWER(), new OPT_BranchProfileOperand()));
      
      //set the overflow flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getOverflowFlag(), lhs.copy(), rhs.copy(), OPT_ConditionOperand.OVERFLOW_FROM_ADD(), OPT_BranchProfileOperand.unlikely()));
      
      //set the negative flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
      
      //set the zero flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
    }
    
    /** Sets the processor flags according to the result of subtracting <code>rhs</code> from <code>lhs</code>.*/
    protected final void setSubResult(OPT_RegisterOperand result, OPT_Operand lhs, OPT_Operand rhs) {

      if (i.updateConditionCodes) {
        if (i.Rd != 15) {
          setSubFlags(result, lhs, rhs);
        } 
        else {
          OPT_Instruction s = createCallToRegisters("restoreSPSR2CPSR", "()V", 0);
          arm2ir.appendCustomCall(s);
        }
      }

      if (i.Rd == 15) {
        if (i.updateConditionCodes)
          arm2ir.appendBranch(result, lazy, BranchType.INDIRECT_BRANCH);
        else 
          arm2ir.appendTraceExit(lazy, result);
      }
      else {
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(i.Rd), result.copy()) );
      }
    }

    /**
     * Sets the processor flags according to the result of a sub operation.
     * @param result
     *  The result of the sub operation.
     * @param lhs
     *  The sub's left-hand-side operator.
     * @param rhs
     *  The sub's right-hand-side operator.
     */
    protected final void setSubFlags(OPT_Operand result, OPT_Operand lhs, OPT_Operand rhs) {
      //set the carry flag to not(Borrow)
      OPT_ConditionOperand notBorrowFromSub = OPT_ConditionOperand.BORROW_FROM_SUB().flipCode();
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getCarryFlag(), lhs.copy(), rhs.copy(), notBorrowFromSub, new OPT_BranchProfileOperand()));
      
      //set the overflow flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getOverflowFlag(), lhs.copy(), rhs.copy(), OPT_ConditionOperand.OVERFLOW_FROM_SUB(), OPT_BranchProfileOperand.unlikely()));
      
      //set the negative flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
      
      //set the zero flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      if (i.Rd != ARM_Registers.PC)
        return pc + 4;
      else
        return -1;
    }
  }
  
  private abstract class DataProcessing_Logical extends DataProcessing {
    
    protected DataProcessing_Logical(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }
    
    /** If the given OperandWrapper involves shifting a register, then this function will decoder the shift
     * and set the result of the barrel shifter accordingly. However, the shifter carry out is only calculated, when
     * the condition codes are to be modified by this function (because otherwise it won't be used anyway).*/
    protected OPT_Operand resolveOperand2() {
      
      if (i.updateConditionCodes) {
        return ResolvedOperand.resolveAndStoreShifterCarryOutToCarry(ARM_Translator.this, i.operand2);
      }
      else {
        return super.resolveOperand2();
      }
    }
    
    /** Sets the condition field for logical operations. */
    protected final void setLogicalResult(OPT_RegisterOperand result) {

      if (i.updateConditionCodes) {
        if (i.Rd != ARM_Registers.PC) {
          setLogicalFlags(result);          
        } else {
          OPT_Instruction s = createCallToRegisters("restoreSPSR2CPSR", "()V", 0);
          arm2ir.appendCustomCall(s);
        }
      }
      
      if (i.Rd == ARM_Registers.PC) {
        if (i.updateConditionCodes) {
          arm2ir.appendTraceExit(lazy, result);
        }
        else {
          BranchType branchType = BranchType.INDIRECT_BRANCH;
          
          //Mark "MOV pc, lr" instructions as returns
          if (i.opcode == Opcode.MOV && i.operand2.getType() == OperandWrapper.Type.Register && i.operand2.getRegister() == ARM_Registers.LR)
            branchType = BranchType.RETURN;
          
          arm2ir.appendBranch(result, lazy, branchType);
        }
      }
      else {
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(i.Rd), result.copy()) );
      }
    }

    /**
     * Sets the flags according to the result of a logical operation.
     * @param result
     *  The result of the logical operation
     */
    protected final void setLogicalFlags(OPT_Operand result) {
      //the shifter carry out has already been set during the resolve-phase
      
      //set the negative flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
      
      //set the zero flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
    }
  }

  /** Binary and. <code>Rd = op1 & op2 </code>.*/
  private final class DataProcessing_And extends DataProcessing_Logical {

    protected DataProcessing_And(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstruction(Binary.create(INT_AND, result, resolveOperand1(), resolveOperand2()));
      
      setLogicalResult(result);
    }
  }

  /** Exclusive or. <code>Rd = op1 ^ op2 </code>.*/
  private final class DataProcessing_Eor extends DataProcessing_Logical {

    protected DataProcessing_Eor(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstruction(Binary.create(INT_XOR, result, resolveOperand1(), resolveOperand2()));
      
      setLogicalResult(result);
    }
  }

  /** Add. <code>Rd = op1 + op2 </code>.*/
  private final class DataProcessing_Add extends DataProcessing {

    public DataProcessing_Add(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    public void translate() {
      
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstruction(Binary.create(INT_ADD, result, operand1, operand2));
      
      setAddResult(result, operand1, operand2);
    }
  }

  /** Subtract. <code>Rd = op1 - op2 </code>.*/
  private final class DataProcessing_Sub extends DataProcessing {

    public DataProcessing_Sub(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }
    
    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstruction(Binary.create(INT_SUB, result, operand1, operand2));
      
      setSubResult(result, operand1, operand2);
    }
  }

  /** Reverse subtract. <code>Rd = op2 - op1</code>.*/
  private final class DataProcessing_Rsb extends DataProcessing {

    protected DataProcessing_Rsb(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstruction(Binary.create(INT_SUB, result, operand2, operand1));
      
      setSubResult(result, operand2, operand1);
    }
  }

  /** Add with carry. <code>Rd = op1 + op2 + CARRY</code>.
   * If the carry flag is set, the instruction will add 1 to one of the operands (whichever operands would
   * not cause an overflow). Then, the normal add-routine is being invoked. */
  private final class DataProcessing_Adc extends DataProcessing {

    protected DataProcessing_Adc(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      OPT_BasicBlock addWithoutCarry = arm2ir.createBlockAfterCurrent();
      OPT_BasicBlock addWithCarry = arm2ir.createBlockAfterCurrentNotInCFG();

      //Is the carry set at all? if not, just jump to addWithoutCarry
      arm2ir.appendInstruction(Binary.create(INT_ADD, result, operand1, operand2));
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), arm2ir.getCarryFlag(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), addWithoutCarry.makeJumpTarget(), new OPT_BranchProfileOperand()));
      arm2ir.getCurrentBlock().insertOut(addWithCarry);
     
      //Yes, the carry flag is set. Pre-increase the result by one to account for the carry.
      arm2ir.setCurrentBlock(addWithCarry);
      arm2ir.appendInstruction(Binary.create(INT_ADD, result.copyRO(), result.copy(), new OPT_IntConstantOperand(1)));
      addWithCarry.insertOut(addWithoutCarry);

      //Finally, add the second operands to the result
      arm2ir.setCurrentBlock(addWithoutCarry);  
      setAddResult(result, operand1, operand2);
    }
  }

  /** Subtract with carry. <code>Rd = op1 - op2 - NOT(CARRY)</code>.*/
  private class DataProcessing_Sbc extends DataProcessing {

    protected DataProcessing_Sbc(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }
    
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      OPT_BasicBlock subWithoutCarry = arm2ir.createBlockAfterCurrent();
      OPT_BasicBlock subWithCarry = arm2ir.createBlockAfterCurrentNotInCFG();

      //Is the carry set? if yes, just jump to subWithoutCarry
      arm2ir.appendInstruction(Binary.create(INT_SUB, result, operand1, operand2));
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), arm2ir.getCarryFlag(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), subWithoutCarry.makeJumpTarget(), new OPT_BranchProfileOperand()));
      arm2ir.getCurrentBlock().insertOut(subWithCarry);
     
      //No, the carry flag is not set. That means, we have to use the carry within the subtraction (weird arm logic).
      arm2ir.setCurrentBlock(subWithCarry);
      arm2ir.appendInstruction(Binary.create(INT_SUB, result.copyRO(), result.copy(), new OPT_IntConstantOperand(1)));
      subWithCarry.insertOut(subWithoutCarry);

      //Finally, subtract the second operands from the result
      arm2ir.setCurrentBlock(subWithoutCarry);
      setSubResult(result, operand1, operand2);
    }
  }

  /** Reserve subtract with carry. <code>Rd = -op1 + op2 - NOT(CARRY)</code>.*/
  private final class DataProcessing_Rsc extends DataProcessing_Sbc {

    protected DataProcessing_Rsc(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }
    
    @Override
    protected OPT_Operand resolveOperand1() {
      return super.resolveOperand2();
    }
    
    @Override
    protected OPT_Operand resolveOperand2() {
      return super.resolveOperand1();
    }
  }

  /** Set the flags according to the logical-and of two values. 
   * <code>Flags = op1 & op2</code>*/
  private final class DataProcessing_Tst extends DataProcessing_Logical {

    protected DataProcessing_Tst(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = arm2ir.getTempInt(0);
      arm2ir.appendInstruction(Binary.create(INT_AND, result, resolveOperand1(), resolveOperand2()));
      setLogicalFlags(result);
    }
  }

  /** Sets the flags according to the exclusive-or of two values.
   * <code>Flags = op1 ^ op2</code> */
  private final class DataProcessing_Teq extends DataProcessing_Logical {

    protected DataProcessing_Teq(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = arm2ir.getTempInt(0);
      arm2ir.appendInstruction(Binary.create(INT_XOR, result, resolveOperand1(), resolveOperand2()));
      setLogicalFlags(result);
    }
  }

  /** Set the flags according to the comparison of two values.
   * <code>Flags = op1 - op2</code> */
  private final class DataProcessing_Cmp extends DataProcessing {

    protected DataProcessing_Cmp(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = arm2ir.getTempInt(0);
      
      arm2ir.appendInstruction(Binary.create(INT_SUB, result, operand1, operand2));      
      setSubFlags(result, operand1, operand2);
    }
  }

  /** Set the flags according to the comparison of two values, negating the 2nd value on the way.
   * <code>Flags = op1 + op2</code>. */
  private final class DataProcessing_Cmn extends DataProcessing {

    protected DataProcessing_Cmn(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = arm2ir.getTempInt(0);
      
      arm2ir.appendInstruction(Binary.create(INT_ADD, result, operand1, operand2));      
      setAddFlags(result, operand1, operand2);
    }
  }

  /** Binary or. <code>Rd = op1 | op2</code>. */
  private final class DataProcessing_Orr extends DataProcessing_Logical {

    protected DataProcessing_Orr(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstruction(Binary.create(INT_OR, result, operand1, operand2));
      setLogicalResult(result);
    }
  }

  private final class DataProcessing_Mov extends DataProcessing_Logical {

    protected DataProcessing_Mov(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    /** Moves a value into a register .*/
    public void translate() {
      OPT_Operand operand = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      arm2ir.appendInstruction(Move.create(INT_MOVE, result, operand));
      setLogicalResult(result);
    }
  }

  /** Bit clear. Clear bits in a register by a mask given by a second operand. 
   * <code>Rd =  op1 & (~op2)</code>.*/
  private final class DataProcessing_Bic extends DataProcessing_Logical {

    protected DataProcessing_Bic(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    /** Clear bits in a register by a mask given by a second operand. */
    public void translate() {
      OPT_Operand operand1 = resolveOperand1();
      OPT_Operand operand2 = resolveOperand2();
      OPT_RegisterOperand result = getResultRegister();
      
      OPT_RegisterOperand tmp = arm2ir.getTempInt(0);

      arm2ir.appendInstruction(Unary.create(INT_NOT, tmp, operand2));
      arm2ir.appendInstruction(Binary.create(INT_AND, result, operand1, tmp.copy()));
      setLogicalResult(result);
    }
  }

  /** Move and negate. Moves an integer between two registers, negating it on the way. 
   * <code>Rd = ~op2</code>.*/
  private final class DataProcessing_Mvn extends DataProcessing_Logical {

    protected DataProcessing_Mvn(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      OPT_RegisterOperand result = getResultRegister();
      arm2ir.appendInstruction(Unary.create(INT_NOT, result, resolveOperand2()));
      setLogicalResult(result);
    }
  }
  
  /** Count the number of leading zeros in an integer.
   * <code>Rd = Number_Of_Leading_Zeroes(op2) </code> */
  private final class DataProcessing_Clz extends DataProcessing {

    protected DataProcessing_Clz(ARM_Instructions.DataProcessing instr) {
      super(instr);
    }

    @Override
    public void translate() {
      //Call Integer.numberOfLeadingZeros() to obtain the result of this operation
      OPT_RegisterOperand result = getResultRegister();

      VM_TypeReference IntegerType = VM_TypeReference
          .findOrCreate(Integer.class);

      VM_MethodReference clzMethodRef = VM_MemberReference
          .findOrCreate(IntegerType,
              VM_Atom.findOrCreateAsciiAtom("numberOfLeadingZeros"),
              VM_Atom.findOrCreateAsciiAtom("(I)I")).asMethodReference();

      VM_Method clzMethod = clzMethodRef.resolve();

      OPT_Instruction s = Call.create(CALL, null, null, null, null, 1);
      OPT_MethodOperand methOp = OPT_MethodOperand.STATIC(clzMethod);

      Call.setParam(s, 1, resolveOperand2());
      Call.setResult(s, result);
      Call.setGuard(s, new OPT_TrueGuardOperand());
      Call.setMethod(s, methOp);
      Call.setAddress(s, new OPT_AddressConstantOperand(clzMethod
          .getOffset()));

      arm2ir.appendInstruction(s);
    }
  }

  /** Swap a register and a memory value. 
   * TODO: At the moment, Pearcolator does not support any way of locking the memory. However, once it does
   * any other memory accesses should be pending until the swap instruction succeeds.*/
  private final class Swap implements
  ARM_Instruction {
    
    private final ARM_Instructions.Swap i;

    public Swap(ARM_Instructions.Swap instr) {
      i = instr;
    }

    public void translate() {
      OPT_Operand memAddr = arm2ir.getRegister(i.Rn);
      OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
      OPT_RegisterOperand result = arm2ir.getRegister(i.Rd);
      
      //swap exchanges the value of a memory address with the value in a register
      if (!i.swapByte) {
        ps.memory.translateLoad32(memAddr, tmp);
        ps.memory.translateStore32(memAddr, arm2ir.getRegister(i.Rm));
        
        //according to the ARM architecture reference, the value loaded from a memory address is rotated
        //by the number of ones in the first two bits of the address
        OPT_RegisterOperand rotation = arm2ir.getTempInt(1);
        
        //rotation = (memAddr & 0x3) * 8
        arm2ir.appendInstruction(Binary.create(INT_AND, rotation, memAddr.copy(), new OPT_IntConstantOperand(0x3)));
        arm2ir.appendInstruction(Binary.create(INT_SHL, rotation.copyRO(), rotation.copy(), new OPT_IntConstantOperand(3))); 
        arm2ir.appendRotateRight(result, tmp.copy(), rotation.copy());
      }
      else {
        ps.memory.translateLoadUnsigned8(memAddr.copy(), tmp);
        ps.memory.translateStore8(memAddr, arm2ir.getRegister(i.Rm));
        arm2ir.appendInstruction(Move.create(INT_MOVE, result, tmp.copy()));
      }
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      //according to the ARM Architecture reference, using the pc as Rd yields an undefined
      //result. Therefore, we can safely assume that this instruction never equals a branch
      return pc + 4;
    }
  }

  /** Transfer multiple registers at once between the register bank and the memory. */
  private final class BlockDataTransfer 
      implements ARM_Instruction {
    
    private final ARM_Instructions.BlockDataTransfer i;

    /** the lowest address that we're reading a register from / writing a register to */
    private final int registerCount;

    /** An array that contains the registers to be transferd in ascending order. 
     * The list is delimited by setting the entry after the last register index to -1.
     * The PC is not included in this list, if it shall be transferred.  */
    private final int[] registersToTransfer = new int[16];

    /** True if the PC should be transferred to, false otherwise. */
    private final boolean transferPC;

    public BlockDataTransfer(ARM_Instructions.BlockDataTransfer instr) {
      i = instr;

      transferPC = i.transferRegister(15);
      int regCount = 0;

      for (int n = 0; n <= 14; n++)
        if (i.transferRegister(n)) {
          registersToTransfer[regCount++] = n;
        }

      registersToTransfer[regCount] = -1;      
      registerCount = regCount;
    }

    public void translate() {
      //This instruction gets very complex when forceUser is set, which is why we are interpreting that special and rare instruction
      if (i.forceUser) {
        arm2ir.appendInterpretedInstruction(pc, lazy);
        arm2ir.appendTraceExit(lazy, arm2ir.getRegister(ARM_Registers.PC));
        
        return;
      }
      
      //build the address, which generally ignores the last two bits
      OPT_RegisterOperand startAddress = arm2ir.getTempInt(0);
      arm2ir.appendInstruction(Binary.create(INT_AND, startAddress, arm2ir.getRegister(i.baseRegister), new OPT_IntConstantOperand(0xFFFFFFFC)));
      
      if (!i.incrementBase) {
        if (i.postIndexing) {
          //post-indexing, backward reading
          //startAddress -= (registerCount + (transferPC ? 1 : 0)) * 4;
          OPT_Operand offset = new OPT_IntConstantOperand((registerCount + (transferPC ? 1 : 0)) * 4);
          arm2ir.appendInstruction(Binary.create(INT_SUB, startAddress.copyRO(), startAddress.copy(), offset));
        } else {
          //pre-indexing, backward-reading
          //startAddress -= (registerCount + (transferPC ? 2 : 1)) * 4
          OPT_Operand offset = new OPT_IntConstantOperand((registerCount + (transferPC ? 2 : 1)) * 4);
          arm2ir.appendInstruction(Binary.create(INT_SUB, startAddress.copyRO(), startAddress.copy(), offset));
        }
      } else {
        if (i.postIndexing) {
          //post-indexing, forward reading
          //startAddress -= 4;
          OPT_Operand offset = new OPT_IntConstantOperand(4);
          arm2ir.appendInstruction(Binary.create(INT_SUB, startAddress.copyRO(), startAddress.copyRO(), offset));
        } else {
          //pre-indexing, forward reading
          //no need to adjust the start address
        }
      }
      
      OPT_RegisterOperand nextAddress = arm2ir.getTempInt(1);
      arm2ir.appendInstruction(Move.create(INT_MOVE, nextAddress, startAddress.copy()));

      //are we supposed to load or store multiple registers?
      if (i.isLoad) {
        int nextReg = 0;

        while (registersToTransfer[nextReg] != -1) {
          //nextAddress += 4;
          arm2ir.appendInstruction(Binary.create(INT_ADD, nextAddress.copyRO(), nextAddress.copy(), new OPT_IntConstantOperand(4)));
          
          OPT_RegisterOperand target = arm2ir.getRegister(registersToTransfer[nextReg++]);
          ps.memory.translateLoad32(nextAddress, target);
        }

        //if we also transferred the program counter
        if (transferPC) {
          //nextAddress += 4;
          arm2ir.appendInstruction(Binary.create(INT_ADD, nextAddress.copyRO(), nextAddress.copy(), new OPT_IntConstantOperand(4)));

          OPT_RegisterOperand regPC = arm2ir.getRegister(ARM_Registers.PC);
          ps.memory.translateLoad32(nextAddress.copy(), regPC);
  
          //first translate the register write back
          translateWriteback(startAddress.copyRO(), nextAddress.copyRO());
          
          //shall we switch to thumb mode?
          OPT_BasicBlock finishInstruction = arm2ir.createBlockAfterCurrentNotInCFG();
          OPT_BasicBlock switchToARMBlock = arm2ir.createBlockAfterCurrentNotInCFG();
          OPT_BasicBlock switchToThumbBlock = arm2ir.createBlockAfterCurrentNotInCFG();
          
          //Current block
          OPT_BasicBlock currentBlock = arm2ir.getCurrentBlock();
          currentBlock.deleteNormalOut();
          currentBlock.insertOut(switchToARMBlock);
          currentBlock.insertOut(switchToThumbBlock);
          arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), regPC.copy(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.BIT_TEST(), switchToThumbBlock.makeJumpTarget(), OPT_BranchProfileOperand.never()));
          arm2ir.appendInstruction(Goto.create(GOTO, switchToARMBlock.makeJumpTarget()));
          
          //Yes, switch to thumb mode
          arm2ir.setCurrentBlock(switchToThumbBlock);
          switchToThumbBlock.insertOut(finishInstruction);
          OPT_Instruction call_setThumbMode = createCallToRegisters("setThumbMode", "(Z)V", 1);
          Call.setParam(call_setThumbMode, 1, new OPT_IntConstantOperand(1));
          arm2ir.appendCustomCall(call_setThumbMode);
          arm2ir.appendInstruction(Goto.create(GOTO, finishInstruction.makeJumpTarget()));

          //No, don't switch to thumb mode
          arm2ir.setCurrentBlock(switchToARMBlock);
          switchToARMBlock.insertOut(finishInstruction);
          arm2ir.appendInstruction(Binary.create(INT_AND, regPC.copyRO(), regPC.copy(), new OPT_IntConstantOperand(0xFFFFFFFE)));
          OPT_Instruction call_setArmMode = createCallToRegisters("setThumbMode", "(Z)V", 1);
          Call.setParam(call_setArmMode, 1, new OPT_IntConstantOperand(0));
          arm2ir.appendCustomCall(call_setArmMode);
          arm2ir.appendInstruction(Goto.create(GOTO, finishInstruction.makeJumpTarget()));
          
          //according to the APCS, these types of instructions are usually function returns
          arm2ir.setCurrentBlock(finishInstruction);
          arm2ir.appendBranch(regPC, lazy, BranchType.RETURN);
          return;
        }
      } else {
        int nextReg = 0;

        while (registersToTransfer[nextReg] != -1) {
          //nextAddress += 4;
          arm2ir.appendInstruction(Binary.create(INT_ADD, nextAddress.copyRO(), nextAddress.copy(), new OPT_IntConstantOperand(4)));          
          ps.memory.translateStore32(nextAddress.copy(), arm2ir.getRegister(registersToTransfer[nextReg++]));
        }

        //also transfer the program counter, if requested so
        if (transferPC) {
          arm2ir.appendInstruction(Binary.create(INT_ADD, nextAddress.copyRO(), nextAddress.copy(), new OPT_IntConstantOperand(4)));
          ps.memory.translateStore32(nextAddress.copy(), new OPT_IntConstantOperand( readPC() ));
        }
      }

      translateWriteback(startAddress.copyRO(), nextAddress.copyRO());
    }

    private void translateWriteback(OPT_RegisterOperand startAddress, OPT_RegisterOperand nextAddress) {
      if (i.writeBack && !i.transferRegister(i.baseRegister)) {
        OPT_RegisterOperand writeBackTarget = arm2ir.getRegister(i.baseRegister);
        
        //write the last address we read from back to a register
        if (!i.incrementBase) {
          //backward reading
          if (i.postIndexing) {
            //backward reading, post-indexing
            arm2ir.appendInstruction(Move.create(INT_MOVE, writeBackTarget, startAddress));
          }
          else {
            //backward reading, pre-indexing
            arm2ir.appendInstruction(Binary.create(INT_ADD, writeBackTarget, startAddress, new OPT_IntConstantOperand(4)));
          }
        }
        else {
          //forward reading
          if (i.postIndexing) {
            arm2ir.appendInstruction(Binary.create(INT_ADD, writeBackTarget, nextAddress, new OPT_IntConstantOperand(4)));
          }
          else {
            arm2ir.appendInstruction(Move.create(INT_MOVE, writeBackTarget, nextAddress));
          }
        }
      }
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      //if we're loading values into the PC, then we can't tell where this instruction will be going
      if (i.isLoad && transferPC)
        return -1;
      else
        return pc + 4;
    }
  }

  /** Branch to another instruction address. */
  private final class Branch implements
  ARM_Instruction {
    
    private final ARM_Instructions.Branch i;

    public Branch(ARM_Instructions.Branch instr) {
      i = instr;
    }

    public void translate() {
            
      if (i.offset.getType() == OperandWrapper.Type.Immediate) {
        //we can directly resolve this branch to a fixed address
        if (i.link)
          arm2ir.appendCall( readPC() + i.getOffset().getImmediate(), lazy, pc + i.size());
        else
          arm2ir.appendBranch(readPC() + i.getOffset().getImmediate(), lazy, BranchType.DIRECT_BRANCH);        
      }
      else {
        
        OPT_Operand offset = ResolvedOperand.resolve(ARM_Translator.this, i.offset);
        OPT_RegisterOperand dest = arm2ir.getTempInt(0);
        
        arm2ir.appendInstruction(Binary.create(INT_ADD, dest, offset, new OPT_IntConstantOperand(readPC())));
        
        if (i.link) {
          arm2ir.appendCall(dest, lazy, pc + i.size());
        }
        else {
          arm2ir.appendBranch(dest, lazy, BranchType.INDIRECT_BRANCH);
        }
      }
      
      //if we're supposed to link, then write the previous address into the link register
      if (i.link) {
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(ARM_Registers.LR), new OPT_IntConstantOperand(pc + 4)));
      }
      else {
        //we should never be returning from the goto
        arm2ir.getCurrentBlock().deleteNormalOut();
      }

    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      if (i.offset.getType() == OperandWrapper.Type.Immediate)
        return readPC() + i.getOffset().getImmediate();
      else
        return -1;
    }
  }

  /** Branch to another instruction address and switch between ARM32 and Thumb code on the way.*/
  private final class BranchExchange 
      implements ARM_Instruction {
    
    private final ARM_Instructions.BranchExchange i;

    public BranchExchange(ARM_Instructions.BranchExchange instr) {
      i = instr;
    }

    public void translate() {
      
      //remember the previous address
      int previousAddress = readPC();
      
      //the address of the instruction we're jumping to
      OPT_Operand targetAddress;
      
      //1 if we're supposed to switch to thumb mode after this call, 0 otherwise
      OPT_Operand enableThumb;

      switch (i.target.getType()) {
      case RegisterOffset:
        if (i.target.getRegister() == ARM_Registers.PC)
          targetAddress = new OPT_IntConstantOperand(previousAddress + i.target.getOffset());
        else {
          OPT_RegisterOperand tmp = arm2ir.getTempInt(2);
          arm2ir.appendInstruction(Binary.create(INT_ADD, tmp, arm2ir.getRegister(i.target.getRegister()), new OPT_IntConstantOperand(i.target.getOffset())));
          targetAddress = tmp;
        }
        
        //Call regs.setThumbMode(true) to enable thumb execution
        enableThumb = new OPT_IntConstantOperand(inThumb() ? 0 : 1);
        break;

      case Register:
        OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
        arm2ir.appendInstruction(Binary.create(INT_AND, tmp, arm2ir.getRegister(i.target.getRegister()), new OPT_IntConstantOperand(0xFFFFFFFE) ));
        targetAddress = tmp;
        
        OPT_RegisterOperand tmp2 = arm2ir.getTempInt(1);
        arm2ir.appendInstruction(Binary.create(INT_AND, tmp2, arm2ir.getRegister(i.target.getRegister()), new OPT_IntConstantOperand(0x1) ));
        enableThumb = tmp2;
        break;

      default:
        throw new RuntimeException("Unexpected Operand type: "
            + i.target.getType());
      }
      
      //write the next address into the link register, if requested so.
      if (i.link) {
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(ARM_Registers.LR), new OPT_IntConstantOperand(previousAddress - (inThumb() ? 2 : 4))));
      }
      
      //set the correct processor mode (thumb or not)
      OPT_Instruction s = createCallToRegisters("setThumbMode", "(Z)V", 1);
      Call.setParam(s, 1, enableThumb);
      arm2ir.appendCustomCall(s);
      
      //jump to the target address. Because we might have switched to thumb mode, we are
      //ending the trace with this method
      arm2ir.appendTraceExit(lazy, targetAddress);
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      return -1;
    }
  }

  /** Multiply two integers into a register, possibly adding the value of a third register on the way. */
  private final class IntMultiply implements ARM_Instruction {
    
    private final ARM_Instructions.IntMultiply i;

    protected IntMultiply(ARM_Instructions.IntMultiply instr) {
      i = instr;
    }

    public void translate() {
      //get the two operands
      //we don't need to consider that any operand might be the PC, because the ARM
      //Ref. manual specifies the usage of the PC has undefined results in this operation
      OPT_Operand operand1 = arm2ir.getRegister(i.Rm);
      OPT_Operand operand2 = arm2ir.getRegister(i.Rs);
      OPT_RegisterOperand result = arm2ir.getRegister(i.Rd);

      //calculate the result
      arm2ir.appendInstruction(Binary.create(INT_MUL, result, operand1, operand2));

      if (i.accumulate) {
        OPT_Operand operand3 = arm2ir.getRegister(i.Rn);        
        arm2ir.appendInstruction(Binary.create(INT_ADD, result.copyRO(), result.copy(), operand3));
      }

      if (i.updateConditionCodes) {
        //set the negative flag
        arm2ir.appendInstruction(BooleanCmp.create(
            BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result.copyRO(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
        
        //set the zero flag
        arm2ir.appendInstruction(BooleanCmp.create(
            BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result.copyRO(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      }
    }
    
    public Condition getCondition() {
      return i.condition;
    }
    
    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }
    
  /** Multiply two longs into a register, possibly adding the value of a third register on the way. */
  private final class LongMultiply  implements
  ARM_Instruction {
    
    private final ARM_Instructions.LongMultiply i;

    protected LongMultiply(ARM_Instructions.LongMultiply instr) {
      i = instr;
    }

    public void translate() {
      //get the two operands
      //we don't need to consider that any operand might be the PC, because the ARM
      //Ref. manual specifies the usage of the PC has undefined results in this operation
      
      OPT_RegisterOperand operand1 = arm2ir.getTempLong(0);
      OPT_RegisterOperand operand2 = arm2ir.getTempLong(1);
      OPT_RegisterOperand result = arm2ir.getTempLong(2);
      
      //fill the two operands
      arm2ir.appendInstruction(Unary.create(INT_2LONG, operand1, arm2ir.getRegister(i.Rm)));
      arm2ir.appendInstruction(Unary.create(INT_2LONG, operand2, arm2ir.getRegister(i.Rs)));
      
      if (i.unsigned) {
        //treat the original ints as unsigned, so get rid of the signs for the longs
        arm2ir.appendInstruction(Binary.create(LONG_AND, operand1.copyRO(), operand1.copy(), new OPT_LongConstantOperand(0xFFFFFFFF)));
        arm2ir.appendInstruction(Binary.create(LONG_AND, operand2.copyRO(), operand2.copy(), new OPT_LongConstantOperand(0xFFFFFFFF)));
      }

      //multiply the two operands
      arm2ir.appendInstruction(Binary.create(LONG_MUL, result.copyRO(), operand1.copy(), operand2.copy()));

      if (i.accumulate) {          
        //treat the accum. value as an unsigned value
        OPT_Operand operand3 = arm2ir.getRegister(i.getRdLow());
        OPT_RegisterOperand tmp = arm2ir.getTempLong(0);
        arm2ir.appendInstruction(Unary.create(INT_2LONG, tmp, operand3));
        arm2ir.appendInstruction(Binary.create(LONG_AND, tmp.copyRO(), tmp.copy(), new OPT_LongConstantOperand(0xFFFFFFFF)));
        arm2ir.appendInstruction(Binary.create(LONG_ADD, result.copyRO(), result.copy(), tmp.copy()));
        
        operand3 = arm2ir.getRegister(i.getRdHigh());
        arm2ir.appendInstruction(Unary.create(INT_2LONG, tmp.copyRO(), operand3));
        arm2ir.appendInstruction(Binary.create(LONG_SHL, tmp.copyRO(), tmp.copy(), new OPT_IntConstantOperand(32)));
        arm2ir.appendInstruction(Binary.create(INT_ADD, result.copyRO(), result.copy(), operand3.copy()));
      }

      if (i.updateConditionCodes) {
        //set the negative flag
        arm2ir.appendInstruction(BooleanCmp.create(
            BOOLEAN_CMP_LONG, arm2ir.getNegativeFlag(), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
        
        //set the zero flag
        arm2ir.appendInstruction(BooleanCmp.create(
            BOOLEAN_CMP_LONG, arm2ir.getZeroFlag(), result.copy(), new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      }
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }
    
  /** Move the value of the program status register into a register. */
  private final class MoveFromStatusRegister implements
      ARM_Instruction {
    
    private final ARM_Instructions.MoveFromStatusRegister i;

    public MoveFromStatusRegister(ARM_Instructions.MoveFromStatusRegister instr) {
      i = instr;
    }

    public void translate() {

      //write the current flags back to the registers class
      arm2ir.spillAllFlags(lazy);
      
      OPT_RegisterOperand psrValue = arm2ir.getRegister(i.Rd);
      OPT_Instruction call;
      
      //do we have to transfer the saved or the current PSR?
      if (i.transferSavedPSR) {
         call = createCallToRegisters("getSPSR", "()V", 0);
      }
      else {
        call = createCallToRegisters("getCPSR", "()V", 0);
      }
      
      Call.setResult(call, psrValue);
      arm2ir.appendCustomCall(call);
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      //Rd should never be the PC, so we can safely predict the next instruction
      return pc + 4;
    }
  }
  
  private final class MoveToStatusRegister implements
      ARM_Instruction {
    
    private final ARM_Instructions.MoveToStatusRegister i;

    public MoveToStatusRegister(ARM_Instructions.MoveToStatusRegister instr) {
      i = instr;
    }

    public void translate() {
      arm2ir.appendInterpretedInstruction(pc, lazy);
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      return pc+4;
    }
  }

  /** Invoke a software interrupt. */
  private final class SoftwareInterrupt implements ARM_Instruction {
    
    private final ARM_Instructions.SoftwareInterrupt i;

    public SoftwareInterrupt(ARM_Instructions.SoftwareInterrupt instr) {
      i = instr;
    }

    public void translate() {
      arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(ARM_Registers.PC), new OPT_IntConstantOperand(pc)));
      arm2ir.appendSystemCall(lazy);
      
      OPT_BasicBlock curBlock = arm2ir.getCurrentBlock();
      OPT_BasicBlock syscallWasJump = arm2ir.createBlockAfterCurrent();
      OPT_BasicBlock nextInstruction = arm2ir.getNextBlock();
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), arm2ir.getRegister(ARM_Registers.PC), new OPT_IntConstantOperand(pc+4), OPT_ConditionOperand.EQUAL(), nextInstruction.makeJumpTarget(), OPT_BranchProfileOperand.always()));
      curBlock.insertOut(nextInstruction);
      
      arm2ir.setCurrentBlock(syscallWasJump);
      syscallWasJump.deleteNormalOut();
      arm2ir.appendBranch(arm2ir.getRegister(ARM_Registers.PC), lazy, BranchType.INDIRECT_BRANCH);
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }

  /** Transfers a single data item (either a byte, half-byte or word) between a register and memory.
   * This operation can either be a load from or a store to memory. */
  private final class SingleDataTransfer 
      implements ARM_Instruction {
    
    private final ARM_Instructions.SingleDataTransfer i;

    public SingleDataTransfer(ARM_Instructions.SingleDataTransfer instr) {
      i = instr;
    }
    
    /** Resolves the offset, which is (when post-indexing is not used) to be added to the 
     * base address to create the final address. */
    private OPT_Operand resolveOffset() {
      OPT_Operand positiveOffset = ResolvedOperand.resolve(ARM_Translator.this, i.offset);
      
      if (i.positiveOffset) {
        return positiveOffset;
      }
      else {
        OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
        arm2ir.appendInstruction(Unary.create(INT_NEG, tmp, positiveOffset));
        return tmp.copy();
      }
    }

    /** Resolves the address of the memory slot, that is involved in the transfer. */
    private OPT_Operand resolveAddress() {
      
      OPT_Operand base;

      //acquire the base address
      if (i.Rn == 15)
        base = new OPT_IntConstantOperand(readPC());
      else
        base = arm2ir.getRegister(i.Rn);

      //if we are not pre-indexing, then just use the base register for the memory access
      if (!i.preIndexing)
        return base;
      
      //add the offset to the base register
      OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
      arm2ir.appendInstruction(Binary.create(INT_ADD, tmp, base, resolveOffset()));
      
      if (i.isThumb && i.isLoad && i.Rn == ARM_Registers.PC) {
        //with thumb, bit 1 of the address is always ignored - address = address & 0xFFFFFFFC;
        //see ARM reference manual for further details
        arm2ir.appendInstruction(Binary.create(INT_AND, tmp.copyRO(), tmp.copy(), new OPT_IntConstantOperand(0xFFFFFFFC)));
      }
      
      return tmp.copy();
    }

    public void translate() {
      //should we simulate a user-mode memory access? If yes, store the current mode and fake a switch
      //to user mode.
      
      //stores the current operating mode
      OPT_RegisterOperand currentOperatingMode = null;
      
      if (i.forceUserMode) {
        OPT_Instruction call_getOperatingMode = createCallToRegisters("getOperatingMode", "()A", 0);
        currentOperatingMode = arm2ir.getTempOperatingMode();
        
        Call.setResult(call_getOperatingMode, currentOperatingMode);
        arm2ir.appendCustomCall(call_getOperatingMode);
        
        OPT_Instruction call_setOperatingModeWithoutRegisterLayout = createCallToRegisters("setOperatingModeWithoutRegisterLayout", "(A)", 1);
        Call.setParam(call_setOperatingModeWithoutRegisterLayout, 1, arm2ir.getTempOperatingMode(OperatingMode.USR));
        
        arm2ir.appendCustomCall(call_setOperatingModeWithoutRegisterLayout);
      }

      //get the address of the memory, that we're supposed access
      OPT_Operand address = resolveAddress();

      if (i.isLoad) {
        //we are loading a value from memory. Load it into this variable.
        OPT_RegisterOperand value = arm2ir.getRegister(i.Rd);

        switch (i.size) {
        
        case Word:
          //perform the actual memory access
          ps.memory.translateLoad32(address, value);
          
          //according to the ARM reference, the last two bits cause the value to be right-rotated
          OPT_RegisterOperand rotation = arm2ir.getTempInt(1);

          //make sure that we're not loosing the address due to the shifting
          OPT_RegisterOperand adrCopy = arm2ir.getTempInt(0);
          arm2ir.appendInstruction(Move.create(INT_MOVE, adrCopy, address.copy()));
          
          //rotation = (address & 0x3) * 8
          arm2ir.appendInstruction(Binary.create(INT_AND, rotation, address.copy(), new OPT_IntConstantOperand(0x3)));
          arm2ir.appendInstruction(Binary.create(INT_SHL, rotation.copyRO(), rotation.copy(), new OPT_IntConstantOperand(3)));
          arm2ir.appendRotateRight(value.copyRO(), value.copy(), rotation.copy());

          //allow further usage of the memory address
          address = adrCopy;
          break;

        case HalfWord:
          if (i.signExtend)
            ps.memory.translateLoadSigned16(address, value);
          else
            ps.memory.translateLoadUnsigned16(address, value);
          break;

        case Byte:
          if (i.signExtend)
            ps.memory.translateLoadSigned8(address, value);
          else
            ps.memory.translateLoadUnsigned8(address, value);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + i.size);
        }
      } 
      else {
        //we are store a value from a register to memory.
        OPT_RegisterOperand value = arm2ir.getRegister(i.Rd);
        
        switch (i.size) {
        case Word:
          OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
          arm2ir.appendInstruction(Binary.create(INT_AND, tmp, address, new OPT_IntConstantOperand(0xFFFFFFFE)));
          ps.memory.translateStore32(tmp.copyRO(), value);
          break;
          
        case HalfWord:
          ps.memory.translateStore16(address, value);
          break;
          
        case Byte:
          ps.memory.translateStore8(address, value);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + i.size);
        }
      }
      
      //if we were writing in user mode, then switch back to our previous operating mode
      if (i.forceUserMode) {
        OPT_Instruction call_setOperatingModeWithoutRegisterLayout = createCallToRegisters("setOperatingModeWithoutRegisterLayout", "(A)", 1);
        Call.setParam(call_setOperatingModeWithoutRegisterLayout, 1, currentOperatingMode);
        arm2ir.appendCustomCall(call_setOperatingModeWithoutRegisterLayout);        
      }      

      //should the memory address, which we accessed, be written back into a register? 
      //This is used for continuous memory accesses
      if (i.writeBack) {
        OPT_RegisterOperand writeBackTarget = arm2ir.getRegister(i.Rn);
        
        if (i.preIndexing) {
          arm2ir.appendInstruction(Move.create(INT_MOVE, writeBackTarget, address.copy()));
        }
        else {
          //add the offset to the base address and write the result back into Rn
          OPT_Operand resolvedOffset = resolveOffset();
          arm2ir.appendInstruction(Binary.create(INT_ADD, writeBackTarget, address, resolvedOffset));
        }
      }
      
      if (i.isLoad && i.Rd == ARM_Registers.PC) {
        //we are actually loading to the program counter here
        arm2ir.appendBranch(arm2ir.getRegister(i.Rd), lazy, BranchType.INDIRECT_BRANCH);
      }
    }
    
    public Condition getCondition() {
      return i.condition;
    }

    public int getSuccessor(int pc) {
      //if we're loading to the PC, then the next instruction is undefined
      if (i.Rd == ARM_Registers.PC && i.isLoad)
        return -1;

      return pc + 4;
    }
  }

  /** Represents an undefined instruction, will throw a runtime error when this instruction
   * is executed. */
  private final class UndefinedInstruction implements ARM_Instruction {

    public UndefinedInstruction(int instr) {
    }

    public void translate() {
      arm2ir.appendThrowBadInstruction(lazy, pc);
    }

    public int getSuccessor(int pc) {
      return -1;
    }

    public Condition getCondition() {
      return Condition.AL;
    }
  }
  
  private final class DebugNopInstruction implements ARM_Instruction {

    public Condition getCondition() {
      return Condition.AL;
    }

    public void translate() {
    }

    public int getSuccessor(int pc) {
      return pc+4;
    }
    
  }

  /** This class will create instances of the different translated instructions. It is being "controlled" by
   * the ARM_InstructionDecoder, which uses an abstract factory pattern to decode an instruction. */
  private class TranslatorFactory implements
      ARM_InstructionFactory<ARM_Instruction> {

    public ARM_Instruction createDataProcessing(int instr) {
      ARM_Instructions.DataProcessing i = new ARM_Instructions.DataProcessing(instr);

      switch (i.opcode) {
      case ADC:
        return new DataProcessing_Adc(i);
      case ADD:
        return new DataProcessing_Add(i);
      case AND:
        return new DataProcessing_And(i);
      case BIC:
        return new DataProcessing_Bic(i);
      case CMN:
        return new DataProcessing_Cmn(i);
      case CMP:
        return new DataProcessing_Cmp(i);
      case EOR:
        return new DataProcessing_Eor(i);
      case MOV:
        return new DataProcessing_Mov(i);
      case MVN:
        return new DataProcessing_Mvn(i);
      case ORR:
        return new DataProcessing_Orr(i);
      case RSB:
        return new DataProcessing_Rsb(i);
      case RSC:
        return new DataProcessing_Rsc(i);
      case SBC:
        return new DataProcessing_Sbc(i);
      case SUB:
        return new DataProcessing_Sub(i);
      case TEQ:
        return new DataProcessing_Teq(i);
      case TST:
        return new DataProcessing_Tst(i);
      case CLZ:
        return new DataProcessing_Clz(i);

      default:
        throw new RuntimeException("Unexpected Data Procesing opcode: "
            + i.opcode);
      }
    }

    public ARM_Instruction createBlockDataTransfer(int instr) {
      return new BlockDataTransfer(new ARM_Instructions.BlockDataTransfer(instr));
    }

    public ARM_Instruction createBranch(int instr) {
      return new Branch(new ARM_Instructions.Branch(instr));
    }

    public ARM_Instruction createBranchExchange(int instr) {
      return new BranchExchange(new ARM_Instructions.BranchExchange(instr));
    }

    public ARM_Instruction createCoprocessorDataProcessing(int instr) {
      //TODO: Implement coprocessor instructions
      /*throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");*/
      return new DebugNopInstruction();
    }

    public ARM_Instruction createCoprocessorDataTransfer(int instr) {
      //    TODO: Implement coprocessor instructions
      /*throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");*/
      return new DebugNopInstruction();
    }

    public ARM_Instruction createCoprocessorRegisterTransfer(int instr) {
      //    TODO: Implement coprocessor instructions
      /*throw new RuntimeException(
          "Coprocessor instructions are not yet supported.");*/
      return new DebugNopInstruction();
    }

    public ARM_Instruction createIntMultiply(int instr) {
      return new IntMultiply(new ARM_Instructions.IntMultiply(instr));
    }

    public ARM_Instruction createLongMultiply(int instr) {
      return new LongMultiply(new ARM_Instructions.LongMultiply(instr));
    }

    public ARM_Instruction createMoveFromStatusRegister(int instr) {
      return new MoveFromStatusRegister(new ARM_Instructions.MoveFromStatusRegister(instr));
    }

    public ARM_Instruction createMoveToStatusRegister(int instr) {
      return new MoveToStatusRegister(new ARM_Instructions.MoveToStatusRegister(instr));
    }

    public ARM_Instruction createSingleDataTransfer(int instr) {
      return new SingleDataTransfer(new ARM_Instructions.SingleDataTransfer(instr));
    }

    public ARM_Instruction createSoftwareInterrupt(int instr) {
      return new SoftwareInterrupt(new ARM_Instructions.SoftwareInterrupt(instr));
    }

    public ARM_Instruction createSwap(int instr) {
      return new Swap(new ARM_Instructions.Swap(instr));
    }

    public ARM_Instruction createUndefinedInstruction(int instr) {
      return new UndefinedInstruction(instr);
    }

    public ARM_Instruction createBlockDataTransfer(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createBranch(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createBranchExchange(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createCoprocessorDataProcessing(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createCoprocessorDataTransfer(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createCoprocessorRegisterTransfer(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createDataProcessing(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createLongMultiply(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createMoveFromStatusRegister(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createMoveToStatusRegister(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createSingleDataTransfer(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createSoftwareInterrupt(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createSwap(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createUndefinedInstruction(short instr) {
      // TODO Auto-generated method stub
      return null;
    }

    public ARM_Instruction createIntMultiply(short instr) {
      // TODO Auto-generated method stub
      return null;
    }
  }
}
