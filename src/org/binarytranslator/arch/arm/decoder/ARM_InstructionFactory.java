package org.binarytranslator.arch.arm.decoder;

public interface ARM_InstructionFactory<T> {
  T createDataProcessing(int instr);
  T createSingleDataTransfer(int instr);
  T createBlockDataTransfer(int instr);
  T createIntMultiply(int instr);
  T createLongMultiply(int instr);
  T createSwap(int instr);
  T createSoftwareInterrupt(int instr);
  T createBranch(int instr);
  T createBranchExchange(int instr);
  T createCoprocessorDataTransfer(int instr);
  T createCoprocessorDataProcessing(int instr);
  T createCoprocessorRegisterTransfer(int instr);
  T createMoveFromStatusRegister(int instr);
  T createMoveToStatusRegister(int instr);
  T createCountLeadingZeros(int instr);
  T createUndefinedInstruction(int instr);
}
