package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.arch.arm.decoder.ARM_Instructions.*;

/** An interface that supports iterating over ARM instructions using the visitor pattern. */
public interface ARM_InstructionVisitor {

  void visit(DataProcessing instr);
  void visit(SingleDataTransfer instr);
  void visit(IntMultiply instr);
  void visit(LongMultiply instr);
  void visit(Swap instr);
  void visit(BlockDataTransfer instr);
  void visit(SoftwareInterrupt instr);
  void visit(Branch instr);
  void visit(BranchExchange instr);
  void visit(CoprocessorDataTransfer instr);
  void visit(CoprocessorDataProcessing instr);
  void visit(CoprocessorRegisterTransfer instr);
  void visit(MoveFromStatusRegister instr);
  void visit(MoveToStatusRegister instr);
}
