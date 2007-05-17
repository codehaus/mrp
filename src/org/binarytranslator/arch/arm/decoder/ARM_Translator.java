package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;
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
  
  /** A "quick" pointer to the ARM registers within the process space*/
  protected final ARM_Registers regs = null;
  
  private final ARM_InstructionDecoder.ARM_InstructionFactory<ARM_Instruction> translatorFactory = new TranslatorFactory();

  public ARM_Translator(ARM_ProcessSpace ps, ARM2IR arm2ir) {
    this.ps = ps;
    this.arm2ir = arm2ir;
  }
  
  public int translateInstruction(int pc, ARM_Laziness lazy) {
    this.pc = pc;
    this.lazy = lazy;
    
    int instruction = ps.memory.loadInstruction32(pc);
    ARM_Instruction instr = ARM_InstructionDecoder.decode(instruction, translatorFactory);
    
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
      return result.getValue();
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
            value = new OPT_IntConstantOperand(translator.pc + 8);
            return;
          }

          value = translator.arm2ir.getRegister(reg);
          return;

        case RegisterShiftedRegister:
        case ImmediateShiftedRegister:
          value = resolveShift(operand);
          return;

        case PcRelative:
          value = new OPT_IntConstantOperand(translator.pc + 8
              + operand.getOffset());
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
          shiftedOperand = new OPT_IntConstantOperand(translator.pc + 8);
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
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block2.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - normal case
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_SHR, resultRegister, shiftedOperand, shiftAmount));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 2 - shift >= 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(
              INT_SHR, resultRegister, shiftedOperand, new OPT_IntConstantOperand(31)));
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
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block2.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - normal case
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_SHL, resultRegister, shiftedOperand, shiftAmount));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 2 - shift >= 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister, new OPT_IntConstantOperand(0)) );
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
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block2.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - normal case
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_USHR, resultRegister, shiftedOperand, shiftAmount));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 2 - shift >= 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister, new OPT_IntConstantOperand(0)) );
          break;

        case ROR:
          /*
           * return Integer.rotateRight(value, shiftAmount);
           */
          translator.arm2ir.appendRotateRight(resultRegister, shiftedOperand, shiftAmount);
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
          translator.arm2ir.appendInstruction(Binary.create(INT_USHR, resultRegister, shiftedOperand, new OPT_IntConstantOperand(1)));
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, translator.arm2ir.getTempValidation(0), translator.arm2ir.getCarryFlag(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.NOT_EQUAL(), nextBlock.makeJumpTarget(), new OPT_BranchProfileOperand()));
          
          //Block 1
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_OR, resultRegister, resultRegister, new OPT_IntConstantOperand(0x80000000)));
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
            value = new OPT_IntConstantOperand(translator.pc + 8);
            return;
          }

          value = translator.arm2ir.getRegister(reg);
          return;

        case RegisterShiftedRegister:
        case ImmediateShiftedRegister:
          value = resolveShift(operand);
          return;

        case PcRelative:
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
          shiftedOperand = new OPT_IntConstantOperand(translator.pc + 8);
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
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), block4.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - shift != 0
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(block2);
          block1.insertOut(block3);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block3.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 2 - shift < 32 && shift != 0
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_SHR, resultRegister, shiftedOperand, shiftAmount) );
          translator.arm2ir.appendInstruction(Binary.create(INT_ADD, tmp, shiftAmount, new OPT_IntConstantOperand(-1)) );
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, tmp);
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 3 - shift >= 32
          translator.arm2ir.setCurrentBlock(block3);
          block3.insertOut(nextBlock);
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, 31);
          translator.arm2ir.appendInstruction(Binary.create(INT_MUL, resultRegister, getShifterCarryOutTarget(), new OPT_IntConstantOperand(-1)) ); //creates either 0xFFFFFFFF if the bit is set, or 0 otherwise
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 4 - shift == 0
          translator.arm2ir.setCurrentBlock(block4);
          block4.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister, shiftedOperand));
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
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), block6.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - shift != 0
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(block2);
          block1.insertOut(block3);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block3.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 2 - Shift != 0 && Shift < 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_SUB, tmp, new OPT_IntConstantOperand(32), shiftAmount) );
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, tmp);
          translator.arm2ir.appendInstruction(Binary.create(INT_SHL, resultRegister, shiftedOperand, shiftAmount));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 3 - Shift >= 32
          translator.arm2ir.setCurrentBlock(block3);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister, new OPT_IntConstantOperand(0)) );
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(32), OPT_ConditionOperand.EQUAL(), block5.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
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
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, 0);
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 6 - shift == 0
          translator.arm2ir.setCurrentBlock(block6);
          block6.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister, shiftedOperand));
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
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), block6.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1 - shift != 0
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(block2);
          block1.insertOut(block3);
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(32), OPT_ConditionOperand.GREATER_EQUAL(), block3.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 2 - Shift != 0 && Shift < 32
          translator.arm2ir.setCurrentBlock(block2);
          block2.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_ADD, tmp, shiftAmount, new OPT_IntConstantOperand(-1)));
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, tmp);
          translator.arm2ir.appendInstruction(Binary.create(INT_USHR, resultRegister, shiftedOperand, shiftAmount));
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 3 - Shift >= 32
          translator.arm2ir.setCurrentBlock(block3);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister, new OPT_IntConstantOperand(0)) );
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(32), OPT_ConditionOperand.EQUAL(), block5.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
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
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, 31);
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 6 - shift == 0
          translator.arm2ir.setCurrentBlock(block6);
          block6.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister, shiftedOperand));
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
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, validation, shiftAmount, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), block2.makeJumpTarget(), OPT_BranchProfileOperand.unlikely()));
          
          //block 1
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendRotateRight(resultRegister, shiftedOperand, shiftAmount);
          translator.arm2ir.appendInstruction(Binary.create(INT_ADD, tmp, shiftAmount, new OPT_IntConstantOperand(-1)) );
          translator.arm2ir.appendInstruction(Binary.create(INT_AND, tmp, tmp, new OPT_IntConstantOperand(0x1F)) );
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, tmp);
          translator.arm2ir.appendInstruction(Goto.create(GOTO, nextBlock.makeJumpTarget()));
          
          //block 2
          translator.arm2ir.setCurrentBlock(block2);
          translator.arm2ir.appendInstruction(Move.create(INT_MOVE, resultRegister, shiftedOperand));
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
          translator.arm2ir.appendInstruction(Binary.create(INT_USHR, resultRegister, shiftedOperand, new OPT_IntConstantOperand(1)));       
          translator.arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, translator.arm2ir.getTempValidation(0), translator.arm2ir.getCarryFlag(), new OPT_IntConstantOperand(1), OPT_ConditionOperand.NOT_EQUAL(), nextBlock.makeJumpTarget(), new OPT_BranchProfileOperand()));
          
          //Block 1
          translator.arm2ir.setCurrentBlock(block1);
          block1.insertOut(nextBlock);
          translator.arm2ir.appendInstruction(Binary.create(INT_OR, resultRegister, resultRegister, new OPT_IntConstantOperand(0x80000000)));
          
          //nextBlock
          translator.arm2ir.setCurrentBlock(nextBlock);
          translator.arm2ir.appendBitTest(getShifterCarryOutTarget(), shiftedOperand, 0);
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
    /** Returns the condition, under which the given instruction will be executed. */
    Condition getCondition();
    
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

    /** Decorates an ARM interpreter instruction, by making it execute conditionally. */
    protected ConditionalDecorator(ARM_Instruction i) {
      conditionalInstruction = i;
    }
    
    public int getSuccessor(int pc) {
      //if this instruction is not a jump, then we can tell what the next instruction will be.
      return pc + 4;
    }
    
    public void translate() {
      //conditionals are implemented easily: if the condition does not hold, then just
      //jump to the block following the conditional instruction
      OPT_BasicBlock nextInstruction = arm2ir.getNextBlock();
      OPT_BasicBlock condBlock = arm2ir.createBlockAfterCurrent(); 
      arm2ir.getCurrentBlock().deleteNormalOut();
      arm2ir.getCurrentBlock().insertOut(nextInstruction);
      arm2ir.getCurrentBlock().insertOut(condBlock);
      
      switch (conditionalInstruction.getCondition()) {
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
        translateCondition_LS(nextInstruction);
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
          throw new RuntimeException("Unexpected condition code: " + conditionalInstruction.getCondition());
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
      
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), result, new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), nextInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }
    
    private void translateCondition_LS(OPT_BasicBlock nextInstruction) {
      //return !regs.isCarrySet() || regs.isZeroSet();
      OPT_Operand carry = arm2ir.getCarryFlag();
      OPT_Operand zero = arm2ir.getZeroFlag();
      OPT_RegisterOperand result = arm2ir.getGenerationContext().temps.makeTempBoolean();
      
      arm2ir.appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, result, carry,
          new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand(), zero, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), result, new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), nextInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }
    
    private void translateCondition_GT(OPT_BasicBlock nextInstruction) {
      //return (regs.isNegativeSet() == regs.isOverflowSet()) && !regs.isZeroSet();
      OPT_Operand negative = arm2ir.getNegativeFlag();
      OPT_Operand overflow = arm2ir.getOverflowFlag();
      OPT_Operand zero = arm2ir.getZeroFlag();
      OPT_RegisterOperand result = arm2ir.getGenerationContext().temps.makeTempBoolean();
      
      arm2ir.appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_OR, result, negative,
          overflow, OPT_ConditionOperand.NOT_EQUAL(), new OPT_BranchProfileOperand(), zero, new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), result, new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), nextInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }
    
    private void translateCondition_LE(OPT_BasicBlock nextInstruction) {
      //return regs.isZeroSet() || (regs.isNegativeSet() != regs.isOverflowSet());
      OPT_Operand negative = arm2ir.getNegativeFlag();
      OPT_Operand overflow = arm2ir.getOverflowFlag();
      OPT_Operand zero = arm2ir.getZeroFlag();
      OPT_RegisterOperand result = arm2ir.getGenerationContext().temps.makeTempBoolean();
      
      arm2ir.appendInstruction(BooleanCmp2.create(BOOLEAN_CMP2_INT_AND, result, negative,
          overflow, OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand(), zero, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      
      arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), result, new OPT_IntConstantOperand(1), OPT_ConditionOperand.EQUAL(), nextInstruction.makeJumpTarget(), new OPT_BranchProfileOperand()));
    }

    public Condition getCondition() {
      return conditionalInstruction.getCondition();
    }
    
    @Override
    public String toString() {
      return conditionalInstruction.toString();
    }
  }

  /** A base class for all data processing interpreter instructions, including CLZ.*/
  private abstract class DataProcessing extends ARM_Instructions.DataProcessing
      implements ARM_Instruction {

    protected DataProcessing(int instr) {
      super(instr);
    }

    /** Returns the value of operand 1 of the data processing instruction. This is always a register value. */
    protected OPT_Operand resolveOperand1() {

      if (Rn == ARM_Registers.PC) {
        return new OPT_IntConstantOperand(pc + 8);
      }

      return arm2ir.getRegister(Rn);
    }

    /** Returns the value of the rhs-operand of the data processing instruction. */
    protected OPT_Operand resolveOperand2() {
      return ResolvedOperand.resolve(ARM_Translator.this, operand2);
    }
    
    /** Returns the register into which the result of a data processing operation shall be stored. */
    protected OPT_RegisterOperand getResultRegister() {
      //return arm2ir.getRegister(Rd);
      return arm2ir.getGenerationContext().temps.makeTempInt();
    } 

    public abstract void translate();

    /** Sets the processor flags according to the result of adding <code>lhs</code> and <code>rhs</code>.*/
    protected final void setAddResult(OPT_RegisterOperand result, OPT_Operand lhs, OPT_Operand rhs) {

      if (updateConditionCodes) {
        if (Rd != 15) {
          setAddFlags(result, lhs, rhs);
        } 
        else {
          OPT_Instruction s = createCallToRegisters("restoreSPSR2CPSR", "()V", 0);
          arm2ir.appendInstruction(s);
        }
      }
      
      if (Rd == 15) {
        
        if (updateConditionCodes)
          arm2ir.appendDynamicJump(result, lazy, BranchType.INDIRECT_BRANCH);
        else 
          arm2ir.setReturnValueResolveLazinessAndBranchToFinish(lazy, result);
      }
      else {
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(Rd), result) );
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
          BOOLEAN_CMP_INT, arm2ir.getCarryFlag(), lhs, rhs, OPT_ConditionOperand.CARRY_FROM_ADD(), new OPT_BranchProfileOperand()));
      
      //set the overflow flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getOverflowFlag(), lhs, rhs, OPT_ConditionOperand.OVERFLOW_FROM_ADD(), OPT_BranchProfileOperand.unlikely()));
      
      //set the negative flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
      
      //set the zero flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
    }
    
    /** Sets the processor flags according to the result of subtracting <code>rhs</code> from <code>lhs</code>.*/
    protected final void setSubResult(OPT_RegisterOperand result, OPT_Operand lhs, OPT_Operand rhs) {

      if (updateConditionCodes) {
        if (Rd != 15) {
          setSubFlags(result, lhs, rhs);
        } 
        else {
          OPT_Instruction s = createCallToRegisters("restoreSPSR2CPSR", "()V", 0);
          arm2ir.appendInstruction(s);
        }
      }

      if (Rd == 15) {
        
        if (updateConditionCodes)
          arm2ir.appendDynamicJump(result, lazy, BranchType.INDIRECT_BRANCH);
        else 
          arm2ir.setReturnValueResolveLazinessAndBranchToFinish(lazy, result);
      }
      else {
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(Rd), result) );
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
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getCarryFlag(), lhs, rhs, OPT_ConditionOperand.BORROW_FROM_SUB(), new OPT_BranchProfileOperand()));
      arm2ir.appendInstruction(Unary.create(BOOLEAN_NOT, arm2ir.getCarryFlag(), arm2ir.getCarryFlag()));
      
      //set the overflow flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getOverflowFlag(), lhs, rhs, OPT_ConditionOperand.OVERFLOW_FROM_SUB(), OPT_BranchProfileOperand.unlikely()));
      
      //set the negative flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
      
      //set the zero flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
    }

    public int getSuccessor(int pc) {
      if (Rd != 15)
        return pc + 4;
      else
        return -1;
    }
  }
  
  private abstract class DataProcessing_Logical extends DataProcessing {
    
    
    protected DataProcessing_Logical(int instr) {
      super(instr);
    }
    
    /** If the given OperandWrapper involves shifting a register, then this function will decoder the shift
     * and set the result of the barrel shifter accordingly. However, the shifter carry out is only calculated, when
     * the condition codes are to be modified by this function (because otherwise it won't be used anyway).*/
    protected OPT_Operand resolveOperand2() {
      
      if (updateConditionCodes) {
        return ResolvedOperand.resolveAndStoreShifterCarryOutToCarry(ARM_Translator.this, operand2);
      }
      else {
        return super.resolveOperand2();
      }
    }
    
    /** Sets the condition field for logical operations. */
    protected final void setLogicalResult(OPT_RegisterOperand result) {

      if (updateConditionCodes) {
        if (Rd != 15) {
          setLogicalFlags(result);          
        } else {
          OPT_Instruction s = createCallToRegisters("restoreSPSR2CPSR", "()V", 0);
          arm2ir.appendInstruction(s);
        }
      }
      
      
      
      if (Rd == 15) {
        if (updateConditionCodes) {
          arm2ir.setReturnValueResolveLazinessAndBranchToFinish(lazy, result);
        }
        else {
          BranchType branchType = BranchType.INDIRECT_BRANCH;
          
          //Mark "MOV pc, lr" instructions as returns
          if (opcode == Opcode.MOV && operand2.getType() == OperandWrapper.Type.Register && operand2.getRegister() == ARM_Registers.LR)
            branchType = BranchType.RETURN;
          
          arm2ir.appendDynamicJump(result, lazy, branchType);
        }
      }
      else {
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(Rd), result) );
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
          BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
      
      //set the zero flag
      arm2ir.appendInstruction(BooleanCmp.create(
          BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
    }
  }

  /** Binary and. <code>Rd = op1 & op2 </code>.*/
  private final class DataProcessing_And extends DataProcessing_Logical {

    protected DataProcessing_And(int instr) {
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

    protected DataProcessing_Eor(int instr) {
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

    public DataProcessing_Add(int instr) {
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

    public DataProcessing_Sub(int instr) {
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

    protected DataProcessing_Rsb(int instr) {
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

    protected DataProcessing_Adc(int instr) {
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
      arm2ir.appendInstruction(Binary.create(INT_ADD, result, result, new OPT_IntConstantOperand(1)));
      addWithCarry.insertOut(addWithoutCarry);

      //Finally, add the second operands to the result
      arm2ir.setCurrentBlock(addWithoutCarry);  
      setAddResult(result, operand1, operand2);
    }
  }

  /** Subtract with carry. <code>Rd = op1 - op2 - NOT(CARRY)</code>.*/
  private class DataProcessing_Sbc extends DataProcessing {

    protected DataProcessing_Sbc(int instr) {
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
      arm2ir.appendInstruction(Binary.create(INT_SUB, result, result, new OPT_IntConstantOperand(1)));
      subWithCarry.insertOut(subWithoutCarry);

      //Finally, subtract the second operands from the result
      arm2ir.setCurrentBlock(subWithoutCarry);
      setSubResult(result, operand1, operand2);
    }
  }

  /** Reserve subtract with carry. <code>Rd = -op1 + op2 - NOT(CARRY)</code>.*/
  private final class DataProcessing_Rsc extends DataProcessing_Sbc {

    protected DataProcessing_Rsc(int instr) {
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

    protected DataProcessing_Tst(int instr) {
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

    protected DataProcessing_Teq(int instr) {
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

    protected DataProcessing_Cmp(int instr) {
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

    protected DataProcessing_Cmn(int instr) {
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

    protected DataProcessing_Orr(int instr) {
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

    protected DataProcessing_Mov(int instr) {
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

    protected DataProcessing_Bic(int instr) {
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
      arm2ir.appendInstruction(Binary.create(INT_AND, result, operand1, tmp));
      setLogicalResult(result);
    }
  }

  /** Move and negate. Moves an integer between two registers, negating it on the way. 
   * <code>Rd = ~op2</code>.*/
  private final class DataProcessing_Mvn extends DataProcessing_Logical {

    protected DataProcessing_Mvn(int instr) {
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

    protected DataProcessing_Clz(int instr) {
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
  private final class Swap extends ARM_Instructions.Swap implements
  ARM_Instruction {

    public Swap(int instr) {
      super(instr);
    }

    public void translate() {
      OPT_Operand memAddr = arm2ir.getRegister(Rn);
      OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
      OPT_RegisterOperand result = arm2ir.getRegister(Rd);
      
      //swap exchanges the value of a memory address with the value in a register
      if (!swapByte) {
        ps.memory.translateLoad32(memAddr, tmp);
        ps.memory.translateStore32(memAddr, arm2ir.getRegister(Rm));
        
        //according to the ARM architecture reference, the value loaded from a memory address is rotated
        //by the number of ones in the first two bits of the address
        OPT_RegisterOperand rotation = arm2ir.getTempInt(1);
        
        //rotation = (memAddr & 0x3) * 8
        arm2ir.appendInstruction(Binary.create(INT_AND, rotation, memAddr, new OPT_IntConstantOperand(0x3)));
        arm2ir.appendInstruction(Binary.create(INT_SHL, rotation, rotation, new OPT_IntConstantOperand(3))); 
        arm2ir.appendRotateRight(result, tmp, rotation);
      }
      else {
        ps.memory.translateLoadUnsigned8(memAddr, tmp);
        ps.memory.translateStore8(memAddr, arm2ir.getRegister(Rm));
        arm2ir.appendInstruction(Move.create(INT_MOVE, result, tmp));
      }
    }

    public int getSuccessor(int pc) {
      //according to the ARM Architecture reference, using the pc as Rd yields an undefined
      //result. Therefore, we can safely assume that this instruction never equals a branch
      return pc + 4;
    }
  }

  /** Transfer multiple registers at once between the register bank and the memory. */
  private final class BlockDataTransfer extends ARM_Instructions.MultipleDataTransfer
      implements ARM_Instruction {

    /** the lowest address that we're reading a register from / writing a register to */
    private final int registerCount;

    /** An array that contains the registers to be transferd in ascending order. 
     * The list is delimited by setting the entry after the last register index to -1.
     * The PC is not included in this list, if it shall be transferred.  */
    private final int[] registersToTransfer = new int[16];

    /** True if the PC should be transferred to, false otherwise. */
    private final boolean transferPC;

    public BlockDataTransfer(int instr) {
      super(instr);

      transferPC = transferRegister(15);
      int regCount = 0;

      for (int i = 0; i <= 14; i++)
        if (transferRegister(i)) {
          registersToTransfer[regCount++] = i;
        }

      registersToTransfer[regCount] = -1;      
      registerCount = regCount;
    }

    public void translate() {
      //This instruction gets very complex when forceUser is set, which is why we are interpreting that special and rare instruction
      if (forceUser) {
        arm2ir.appendInterpretedInstruction(pc, lazy);
        arm2ir.setReturnValueResolveLazinessAndBranchToFinish(lazy, arm2ir.getRegister(ARM_Registers.PC));
        
        return;
      }
      
      //build the address, which generally ignores the last two bits
      OPT_RegisterOperand startAddress = arm2ir.getTempInt(0);
      arm2ir.appendInstruction(Binary.create(INT_AND, startAddress, arm2ir.getRegister(baseRegister), new OPT_IntConstantOperand(0xFFFFFFFC)));
      
      if (!incrementBase) {
        if (postIndexing) {
          //post-indexing, backward reading
          //startAddress -= (registerCount + (transferPC ? 1 : 0)) * 4;
          OPT_Operand offset = new OPT_IntConstantOperand((registerCount + (transferPC ? 1 : 0)) * 4);
          arm2ir.appendInstruction(Binary.create(INT_SUB, startAddress, startAddress, offset));
        } else {
          //pre-indexing, backward-reading
          //startAddress -= (registerCount + (transferPC ? 2 : 1)) * 4
          OPT_Operand offset = new OPT_IntConstantOperand((registerCount + (transferPC ? 2 : 1)) * 4);
          arm2ir.appendInstruction(Binary.create(INT_SUB, startAddress, startAddress, offset));
        }
      } else {
        if (postIndexing) {
          //post-indexing, forward reading
          //startAddress -= 4;
          OPT_Operand offset = new OPT_IntConstantOperand(4);
          arm2ir.appendInstruction(Binary.create(INT_SUB, startAddress, startAddress, offset));
        } else {
          //pre-indexing, forward reading
          //no need to adjust the start address
        }
      }
      
      OPT_RegisterOperand nextAddress = arm2ir.getTempInt(1);
      arm2ir.appendInstruction(Move.create(INT_MOVE, nextAddress, startAddress));

      //are we supposed to load or store multiple registers?
      if (isLoad) {
        int nextReg = 0;

        while (registersToTransfer[nextReg] != -1) {
          //nextAddress += 4;
          arm2ir.appendInstruction(Binary.create(INT_ADD, nextAddress, nextAddress, new OPT_IntConstantOperand(4)));
          
          OPT_RegisterOperand target = arm2ir.getRegister(registersToTransfer[nextReg++]);
          ps.memory.translateLoad32(nextAddress, target);
        }

        //if we also transferred the program counter
        if (transferPC) {
          //nextAddress += 4;
          arm2ir.appendInstruction(Binary.create(INT_ADD, nextAddress, nextAddress, new OPT_IntConstantOperand(4)));

          OPT_RegisterOperand regPC = arm2ir.getRegister(ARM_Registers.PC);
          ps.memory.translateLoad32(nextAddress, regPC);
  
          //first translate the register write back
          translateWriteback(startAddress, nextAddress);
          
          //shall we switch to thumb mode?
          OPT_BasicBlock finishInstruction = arm2ir.createBlockAfterCurrentNotInCFG();
          OPT_BasicBlock switchToARMBlock = arm2ir.createBlockAfterCurrentNotInCFG();
          OPT_BasicBlock switchToThumbBlock = arm2ir.createBlockAfterCurrentNotInCFG();
          
          //Current block
          OPT_BasicBlock currentBlock = arm2ir.getCurrentBlock();
          currentBlock.deleteNormalOut();
          currentBlock.insertOut(switchToARMBlock);
          currentBlock.insertOut(switchToThumbBlock);
          arm2ir.appendInstruction(IfCmp.create(INT_IFCMP, arm2ir.getTempValidation(0), regPC, new OPT_IntConstantOperand(1), OPT_ConditionOperand.BIT_TEST(), switchToThumbBlock.makeJumpTarget(), OPT_BranchProfileOperand.never()));
          arm2ir.appendInstruction(Goto.create(GOTO, switchToARMBlock.makeJumpTarget()));
          
          //Yes, switch to thumb mode
          arm2ir.setCurrentBlock(switchToThumbBlock);
          switchToThumbBlock.insertOut(finishInstruction);
          OPT_Instruction call_setThumbMode = createCallToRegisters("setThumbMode", "(Z)V", 1);
          Call.setParam(call_setThumbMode, 1, new OPT_IntConstantOperand(1));
          arm2ir.appendInstruction(call_setThumbMode);
          arm2ir.appendInstruction(Goto.create(GOTO, finishInstruction.makeJumpTarget()));

          //No, don't switch to thumb mode
          arm2ir.setCurrentBlock(switchToARMBlock);
          switchToARMBlock.insertOut(finishInstruction);
          arm2ir.appendInstruction(Binary.create(INT_AND, regPC, regPC, new OPT_IntConstantOperand(0xFFFFFFFE)));
          OPT_Instruction call_setArmMode = createCallToRegisters("setThumbMode", "(Z)V", 1);
          Call.setParam(call_setArmMode, 1, new OPT_IntConstantOperand(0));
          arm2ir.appendInstruction(call_setArmMode);
          arm2ir.appendInstruction(Goto.create(GOTO, finishInstruction.makeJumpTarget()));
          
          //according to the APCS, these types of instructions are usually function returns
          arm2ir.setCurrentBlock(finishInstruction);
          arm2ir.appendDynamicJump(regPC, lazy, BranchType.RETURN);
          return;
        }
      } else {
        int nextReg = 0;

        while (registersToTransfer[nextReg] != -1) {
          //nextAddress += 4;
          arm2ir.appendInstruction(Binary.create(INT_ADD, nextAddress, nextAddress, new OPT_IntConstantOperand(4)));          
          ps.memory.translateStore32(nextAddress, arm2ir.getRegister(registersToTransfer[nextReg++]));
        }

        //also transfer the program counter, if requested so
        if (transferPC) {
          arm2ir.appendInstruction(Binary.create(INT_ADD, nextAddress, nextAddress, new OPT_IntConstantOperand(4)));
          ps.memory.translateStore32(nextAddress, new OPT_IntConstantOperand(pc + 8));
        }
      }

      translateWriteback(startAddress, nextAddress);
    }

    private void translateWriteback(OPT_RegisterOperand startAddress, OPT_RegisterOperand nextAddress) {
      if (writeBack) {
        OPT_RegisterOperand writeBackTarget = arm2ir.getRegister(baseRegister);
        
        //write the last address we read from back to a register
        if (!incrementBase) {
          //backward reading
          if (postIndexing) {
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
          if (postIndexing) {
            arm2ir.appendInstruction(Binary.create(INT_ADD, writeBackTarget, nextAddress, new OPT_IntConstantOperand(4)));
          }
          else {
            arm2ir.appendInstruction(Move.create(INT_MOVE, writeBackTarget, nextAddress));
          }
        }
      }
    }

    public int getSuccessor(int pc) {
      //if we're loading values into the PC, then we can't tell where this instruction will be going
      if (isLoad && transferPC)
        return -1;
      else
        return pc + 4;
    }
  }

  /** Branch to another instruction address. */
  private final class Branch extends ARM_Instructions.Branch implements
  ARM_Instruction {

    public Branch(int instr) {
      super(instr);
    }

    public void translate() {
      
      //if we're supposed to link, then write the previous address into the link register
      if (link) {        
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(ARM_Registers.LR), new OPT_IntConstantOperand(pc + 4)));
      }
      
      arm2ir.getCurrentBlock().deleteNormalOut();
      arm2ir.appendGoto(pc + getOffset() + 8, lazy);
    }

    public int getSuccessor(int pc) {
      return pc + getOffset() + 8;
    }
  }

  /** Branch to another instruction address and switch between ARM32 and Thumb code on the way.*/
  private final class BranchExchange extends ARM_Instructions.BranchExchange
      implements ARM_Instruction {

    public BranchExchange(int instr) {
      super(instr);
    }

    public void translate() {
      
      //remember the previous address
      int previousAddress = pc + 8;
      
      //the address of the instruction we're jumping to
      OPT_Operand targetAddress;
      
      //1 if we're supposed to switch to thumb mode after this call, 0 otherwise
      OPT_Operand enableThumb;

      switch (target.getType()) {
      case PcRelative:
        targetAddress = new OPT_IntConstantOperand(previousAddress + target.getOffset());
        
        //Call regs.setThumbMode(true) to enable thumb execution
        enableThumb = new OPT_IntConstantOperand(1);
        break;

      case Register:
        OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
        arm2ir.appendInstruction(Binary.create(INT_AND, tmp, arm2ir.getRegister(target.getRegister()), new OPT_IntConstantOperand(0xFFFFFFFE) ));
        targetAddress = tmp;
        
        OPT_RegisterOperand tmp2 = arm2ir.getTempInt(1);
        arm2ir.appendInstruction(Binary.create(INT_AND, tmp2, arm2ir.getRegister(target.getRegister()), new OPT_IntConstantOperand(0x1) ));
        enableThumb = tmp2;
        break;

      default:
        throw new RuntimeException("Unexpected Operand type: "
            + target.getType());
      }
      
      //write the next address into the link register, if requested so.
      if (link) {
        arm2ir.appendInstruction(Move.create(INT_MOVE, arm2ir.getRegister(ARM_Registers.LR), new OPT_IntConstantOperand(previousAddress - 4)));
      }
      
      //set the correct processor mode (thumb or not)
      OPT_Instruction s = createCallToRegisters("setThumbMode", "(Z)V", 1);
      Call.setParam(s, 1, enableThumb);
      arm2ir.appendInstruction(s);
      
      //jump to the target address. Because we might have switched to thumb mode, we are
      //ending the trace with this method
      arm2ir.setReturnValueResolveLazinessAndBranchToFinish(lazy, targetAddress);
    }

    public int getSuccessor(int pc) {
      return -1;
    }
  }

  /** Multiply two integers into a register, possibly adding the value of a third register on the way. */
  private final class IntMultiply extends ARM_Instructions.IntMultiply implements
  ARM_Instruction {

    protected IntMultiply(int instr) {
      super(instr);
    }

    public void translate() {
      //get the two operands
      //we don't need to consider that any operand might be the PC, because the ARM
      //Ref. manual specifies the usage of the PC has undefined results in this operation
      OPT_Operand operand1 = arm2ir.getRegister(Rm);
      OPT_Operand operand2 = arm2ir.getRegister(Rs);
      OPT_RegisterOperand result = arm2ir.getRegister(Rd);

      //calculate the result
      arm2ir.appendInstruction(Binary.create(INT_MUL, result, operand1, operand2));

      if (accumulate) {
        OPT_Operand operand3 = arm2ir.getRegister(Rn);        
        arm2ir.appendInstruction(Binary.create(INT_ADD, result, result, operand3));
      }

      if (updateConditionCodes) {
        //set the negative flag
        arm2ir.appendInstruction(BooleanCmp.create(
            BOOLEAN_CMP_INT, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
        
        //set the zero flag
        arm2ir.appendInstruction(BooleanCmp.create(
            BOOLEAN_CMP_INT, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      }
    }
    
    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }
    
  /** Multiply two longs into a register, possibly adding the value of a third register on the way. */
  private final class LongMultiply extends ARM_Instructions.LongMultiply implements
  ARM_Instruction {

    protected LongMultiply(int instr) {
      super(instr);
    }

    public void translate() {
      //get the two operands
      //we don't need to consider that any operand might be the PC, because the ARM
      //Ref. manual specifies the usage of the PC has undefined results in this operation
      
      OPT_RegisterOperand operand1 = arm2ir.getTempLong(0);
      OPT_RegisterOperand operand2 = arm2ir.getTempLong(1);
      OPT_RegisterOperand result = arm2ir.getTempLong(2);
      
      //fill the two operands
      arm2ir.appendInstruction(Unary.create(INT_2LONG, operand1, arm2ir.getRegister(Rm)));
      arm2ir.appendInstruction(Unary.create(INT_2LONG, operand2, arm2ir.getRegister(Rs)));
      
      if (unsigned) {
        //treat the original ints as unsigned, so get rid of the signs for the longs
        arm2ir.appendInstruction(Binary.create(LONG_AND, operand1, operand1, new OPT_LongConstantOperand(0xFFFFFFFF)));
        arm2ir.appendInstruction(Binary.create(LONG_AND, operand2, operand2, new OPT_LongConstantOperand(0xFFFFFFFF)));
      }

      //multiply the two operands
      arm2ir.appendInstruction(Binary.create(LONG_MUL, result, operand1, operand2));

      if (accumulate) {          
        //treat the accum. value as an unsigned value
        OPT_Operand operand3 = arm2ir.getRegister(getRdLow());
        OPT_RegisterOperand tmp = arm2ir.getTempLong(0);
        arm2ir.appendInstruction(Unary.create(INT_2LONG, tmp, operand3));
        arm2ir.appendInstruction(Binary.create(LONG_AND, tmp, tmp, new OPT_LongConstantOperand(0xFFFFFFFF)));
        arm2ir.appendInstruction(Binary.create(LONG_ADD, result, result, tmp));
        
        operand3 = arm2ir.getRegister(getRdHigh());
        arm2ir.appendInstruction(Unary.create(INT_2LONG, tmp, operand3));
        arm2ir.appendInstruction(Binary.create(LONG_SHL, tmp, tmp, new OPT_IntConstantOperand(32)));
        arm2ir.appendInstruction(Binary.create(INT_ADD, result, result, operand3));
      }

      if (updateConditionCodes) {
        //set the negative flag
        arm2ir.appendInstruction(BooleanCmp.create(
            BOOLEAN_CMP_LONG, arm2ir.getNegativeFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.LESS(), new OPT_BranchProfileOperand()));
        
        //set the zero flag
        arm2ir.appendInstruction(BooleanCmp.create(
            BOOLEAN_CMP_LONG, arm2ir.getZeroFlag(), result, new OPT_IntConstantOperand(0), OPT_ConditionOperand.EQUAL(), new OPT_BranchProfileOperand()));
      }
    }

    public int getSuccessor(int pc) {
      return pc + 4;
    }
  }
    
  /** Move the value of the program status register into a register. */
  private final class MoveFromStatusRegister extends
      ARM_Instructions.MoveFromStatusRegister implements
      ARM_Instruction {

    public MoveFromStatusRegister(int instr) {
      super(instr);
    }

    public void translate() {

      //write the current flags back to the registers class
      arm2ir.spillAllFlags(lazy);
      
      OPT_RegisterOperand psrValue = arm2ir.getRegister(Rd);
      OPT_Instruction call;
      
      //do we have to transfer the saved or the current PSR?
      if (transferSavedPSR) {
         call = createCallToRegisters("getSPSR", "()V", 0);
      }
      else {
        call = createCallToRegisters("getCPSR", "()V", 0);
      }
      
      Call.setResult(call, psrValue);
      arm2ir.appendInstruction(call);
    }

    public int getSuccessor(int pc) {
      //Rd should never be the PC, so we can safely predict the next instruction
      return pc + 4;
    }
  }
  
  private final class MoveToStatusRegister extends
    ARM_Instructions.MoveToStatusRegister implements
      ARM_Instruction {

    public MoveToStatusRegister(int instr) {
      super(instr);
    }

    public void translate() {
      arm2ir.appendInterpretedInstruction(pc, lazy);
    }

    public int getSuccessor(int pc) {
      return pc+4;
    }
  }

  /** Invoke a software interrupt. */
  private final class SoftwareInterrupt extends ARM_Instructions.SoftwareInterrupt
      implements ARM_Instruction {

    public SoftwareInterrupt(int instr) {
      super(instr);
    }

    public void translate() {
      arm2ir.appendSystemCall(lazy, pc);
      arm2ir.appendDynamicJump(arm2ir.getRegister(ARM_Registers.PC), lazy, BranchType.INDIRECT_BRANCH);
    }

    public int getSuccessor(int pc) {
      return -1;
    }
  }

  /** Transfers a single data item (either a byte, half-byte or word) between a register and memory.
   * This operation can either be a load from or a store to memory. */
  private final class SingleDataTransfer extends ARM_Instructions.SingleDataTransfer
      implements ARM_Instruction {

    public SingleDataTransfer(int instr) {
      super(instr);
    }
    
    /** Resolves the offset, which is (when post-indexing is not used) to be added to the 
     * base address to create the final address. */
    private OPT_Operand resolveOffset() {
      OPT_Operand positiveOffset = ResolvedOperand.resolve(ARM_Translator.this, offset);
      
      if (this.positiveOffset) {
        return positiveOffset;
      }
      else {
        OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
        arm2ir.appendInstruction(Unary.create(INT_NEG, tmp, positiveOffset));
        return tmp;
      }
    }

    /** Resolves the address of the memory slot, that is involved in the transfer. */
    private OPT_Operand resolveAddress() {
      
      OPT_Operand base;

      //acquire the base address
      if (Rn == 15)
        base = new OPT_IntConstantOperand(pc + 8);
      else
        base = arm2ir.getRegister(Rn);

      //if we are not pre-indexing, then just use the base register for the memory access
      if (!preIndexing)
        return base;
      
      //add the offset to the base register
      OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
      arm2ir.appendInstruction(Binary.create(INT_ADD, tmp, base, resolveOffset()));
      return tmp;
    }

    public void translate() {
      //should we simulate a user-mode memory access? If yes, store the current mode and fake a switch
      //to user mode.
      
      //stores the current operating mode
      OPT_RegisterOperand currentOperatingMode = null;
      
      if (forceUserMode) {
        OPT_Instruction call_getOperatingMode = createCallToRegisters("getOperatingMode", "()A", 0);
        currentOperatingMode = arm2ir.getTempOperatingMode();
        
        Call.setResult(call_getOperatingMode, currentOperatingMode);
        arm2ir.appendInstruction(call_getOperatingMode);
        
        OPT_Instruction call_setOperatingModeWithoutRegisterLayout = createCallToRegisters("setOperatingModeWithoutRegisterLayout", "(A)", 1);
        Call.setParam(call_setOperatingModeWithoutRegisterLayout, 1, arm2ir.getTempOperatingMode(OperatingMode.USR));
        
        arm2ir.appendInstruction(call_setOperatingModeWithoutRegisterLayout);
      }

      //get the address of the memory, that we're supposed access
      OPT_Operand address = resolveAddress();

      if (isLoad) {
        //we are loading a value from memory. Load it into this variable.
        OPT_RegisterOperand value = arm2ir.getRegister(Rd);

        switch (size) {
        
        case Word:
          //perform the actual memory access
          ps.memory.translateLoad32(address, value);
          
          //according to the ARM reference, the last two bits cause the value to be right-rotated
          OPT_RegisterOperand rotation = arm2ir.getTempInt(1);

          //make sure that we're not loosing the address due to the shifting
          OPT_RegisterOperand adrCopy = arm2ir.getTempInt(0);
          arm2ir.appendInstruction(Move.create(INT_MOVE, adrCopy, address));
          
          //rotation = (address & 0x3) * 8
          arm2ir.appendInstruction(Binary.create(INT_AND, rotation, address, new OPT_IntConstantOperand(0x3)));
          arm2ir.appendInstruction(Binary.create(INT_SHL, rotation, rotation, new OPT_IntConstantOperand(3)));
          arm2ir.appendRotateRight(value, value, rotation);

          //allow further usage of the memory address
          address = adrCopy;
          break;

        case HalfWord:
          if (signExtend)
            ps.memory.translateLoadSigned16(address, value);
          else
            ps.memory.translateLoadUnsigned16(address, value);
          break;

        case Byte:
          if (signExtend)
            ps.memory.translateLoadSigned8(address, value);
          else
            ps.memory.translateLoadUnsigned8(address, value);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + size);
        }
      } 
      else {
        //we are store a value from a register to memory.
        OPT_RegisterOperand value = arm2ir.getRegister(Rd);
        
        switch (size) {
        case Word:
          OPT_RegisterOperand tmp = arm2ir.getTempInt(0);
          arm2ir.appendInstruction(Binary.create(INT_AND, tmp, address, new OPT_IntConstantOperand(0xFFFFFFFE)));
          ps.memory.translateStore32(tmp, value);
          break;
          
        case HalfWord:
          ps.memory.translateStore16(address, value);
          break;
          
        case Byte:
          ps.memory.translateStore8(address, value);
          break;

        default:
          throw new RuntimeException("Unexpected memory size: " + size);
        }
      }
      
      //if we were writing in user mode, then switch back to our previous operating mode
      if (forceUserMode) {
        OPT_Instruction call_setOperatingModeWithoutRegisterLayout = createCallToRegisters("setOperatingModeWithoutRegisterLayout", "(A)", 1);
        Call.setParam(call_setOperatingModeWithoutRegisterLayout, 1, currentOperatingMode);
        arm2ir.appendInstruction(call_setOperatingModeWithoutRegisterLayout);        
      }      

      //should the memory address, which we accessed, be written back into a register? 
      //This is used for continuous memory accesses
      if (writeBack) {
        OPT_RegisterOperand writeBackTarget = arm2ir.getRegister(Rn);
        
        if (preIndexing) {
          arm2ir.appendInstruction(Move.create(INT_MOVE, writeBackTarget, address));
        }
        else {
          //add the offset to the base address and write the result back into Rn
          OPT_Operand resolvedOffset = resolveOffset();
          arm2ir.appendInstruction(Binary.create(INT_ADD, writeBackTarget, address, resolvedOffset));
        }
      }
      
      if (isLoad && Rd == ARM_Registers.PC) {
        //we are actually loading to the program counter here
        arm2ir.appendDynamicJump(arm2ir.getRegister(Rd), lazy, BranchType.INDIRECT_BRANCH);
      }
    }

    public int getSuccessor(int pc) {
      //if we're loading to the PC, then the next instruction is undefined
      if (Rd == ARM_Registers.PC && isLoad)
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
      Opcode opcode = Opcode.values()[Utils.getBits(instr, 21, 24)];

      switch (opcode) {
      case ADC:
        return new DataProcessing_Adc(instr);
      case ADD:
        return new DataProcessing_Add(instr);
      case AND:
        return new DataProcessing_And(instr);
      case BIC:
        return new DataProcessing_Bic(instr);
      case CMN:
        return new DataProcessing_Cmn(instr);
      case CMP:
        return new DataProcessing_Cmp(instr);
      case EOR:
        return new DataProcessing_Eor(instr);
      case MOV:
        return new DataProcessing_Mov(instr);
      case MVN:
        return new DataProcessing_Mvn(instr);
      case ORR:
        return new DataProcessing_Orr(instr);
      case RSB:
        return new DataProcessing_Rsb(instr);
      case RSC:
        return new DataProcessing_Rsc(instr);
      case SBC:
        return new DataProcessing_Sbc(instr);
      case SUB:
        return new DataProcessing_Sub(instr);
      case TEQ:
        return new DataProcessing_Teq(instr);
      case TST:
        return new DataProcessing_Tst(instr);
      case CLZ:
        return new DataProcessing_Clz(instr);

      default:
        throw new RuntimeException("Unexpected Data Procesing opcode: "
            + opcode);
      }
    }

    public ARM_Instruction createBlockDataTransfer(int instr) {
      return new BlockDataTransfer(instr);
    }

    public ARM_Instruction createBranch(int instr) {
      return new Branch(instr);
    }

    public ARM_Instruction createBranchExchange(int instr) {
      return new BranchExchange(instr);
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
      return new IntMultiply(instr);
    }

    public ARM_Instruction createLongMultiply(int instr) {
      return new LongMultiply(instr);
    }

    public ARM_Instruction createMoveFromStatusRegister(int instr) {
      return new MoveFromStatusRegister(instr);
    }

    public ARM_Instruction createMoveToStatusRegister(int instr) {
      return new MoveToStatusRegister(instr);
    }

    public ARM_Instruction createSingleDataTransfer(int instr) {
      return new SingleDataTransfer(instr);
    }

    public ARM_Instruction createSoftwareInterrupt(int instr) {
      return new SoftwareInterrupt(instr);
    }

    public ARM_Instruction createSwap(int instr) {
      return new Swap(instr);
    }

    public ARM_Instruction createUndefinedInstruction(int instr) {
      return new UndefinedInstruction(instr);
    }
  }
}
