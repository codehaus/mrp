package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.BlockDataTransfer;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.Branch;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.BranchExchange;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.CoprocessorDataProcessing;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.CoprocessorDataTransfer;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.CoprocessorRegisterTransfer;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.CountLeadingZeros;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.DataProcessing;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.LongMultiply;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.Multiply;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.SingleDataTransfer;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.SoftwareInterrupt;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.Swap;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.TransferFromStatusRegister;
import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoders.TransferToStatusRegister;

/** An interface that supports iterating over ARM instructions using the visitor pattern. */
public interface ARM_InstructionVisitor {

  void visit(DataProcessing processing);
  void visit(SingleDataTransfer transfer);
  void visit(Multiply multiply);
  void visit(LongMultiply multiply);
  void visit(Swap swap);
  void visit(BlockDataTransfer transfer);
  void visit(SoftwareInterrupt interrupt);
  void visit(Branch branch);
  void visit(BranchExchange exchange);
  void visit(CoprocessorDataTransfer transfer);
  void visit(CoprocessorDataProcessing processing);
  void visit(CoprocessorRegisterTransfer transfer);
  void visit(TransferFromStatusRegister register);
  void visit(TransferToStatusRegister register);
  void visit(CountLeadingZeros zeros); 
}
