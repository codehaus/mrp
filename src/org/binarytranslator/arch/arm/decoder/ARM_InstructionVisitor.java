package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.*;

/** An interface that supports iterating over ARM instructions using the visitor pattern. */
public interface ARM_InstructionVisitor {

  void visit(DataProcessing processing);
  void visit(SingleDataTransfer transfer);
  void visit(IntMultiply multiply);
  void visit(LongMultiply multiply);
  void visit(Swap swap);
  void visit(BlockDataTransfer transfer);
  void visit(SoftwareInterrupt interrupt);
  void visit(Branch branch);
  void visit(BranchExchange exchange);
  void visit(CoprocessorDataTransfer transfer);
  void visit(CoprocessorDataProcessing processing);
  void visit(CoprocessorRegisterTransfer transfer);
  void visit(MoveFromStatusRegister register);
  void visit(MoveToStatusRegister register);
  void visit(CountLeadingZeros zeros); 
}
