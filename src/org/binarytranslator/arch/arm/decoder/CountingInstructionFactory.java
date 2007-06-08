package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.arch.arm.decoder.ARM_InstructionDecoder.ARM_InstructionFactory;

public class CountingInstructionFactory<T> implements ARM_InstructionFactory<T> {
  
  private final ARM_InstructionFactory<T> factory;
  
  private int blockDataTransfer = 0;
  private int branch = 0;
  private int branchExchange = 0;
  private int cpDataProcessing = 0;
  private int cpDataTransfer = 0;
  private int cpRegisterTransfer = 0;
  private int dataProcessing = 0;
  private int intMultiply = 0;
  private int longMultiply = 0;
  private int moveFromStatusRegister = 0;
  private int moveToStatusRegister = 0;
  private int singleDataTransfer = 0;
  private int softwareInterrupt = 0;
  private int undefinedInstruction = 0;
  private int swap = 0;
  
  public CountingInstructionFactory(ARM_InstructionFactory<T> factory) {
    this.factory = factory;
  }

  public T createBlockDataTransfer(int instr) {
    blockDataTransfer++;
    return factory.createBlockDataTransfer(instr);
  }

  public T createBranch(int instr) {
    branch++;
    return factory.createBranch(instr);
  }

  public T createBranchExchange(int instr) {
    branchExchange++;
    return factory.createBranchExchange(instr);
  }

  public T createCoprocessorDataProcessing(int instr) {
    cpDataProcessing++;
    return factory.createCoprocessorDataProcessing(instr);
  }

  public T createCoprocessorDataTransfer(int instr) {
    cpDataTransfer++;
    return factory.createCoprocessorDataTransfer(instr);
  }

  public T createCoprocessorRegisterTransfer(int instr) {
    cpRegisterTransfer++;
    return factory.createCoprocessorRegisterTransfer(instr);
  }

  public T createDataProcessing(int instr) {
    dataProcessing++;
    return factory.createDataProcessing(instr);
  }

  public T createIntMultiply(int instr) {
    intMultiply++;
    return factory.createIntMultiply(instr);
  }

  public T createLongMultiply(int instr) {
    longMultiply++;
    return factory.createLongMultiply(instr);
  }

  public T createMoveFromStatusRegister(int instr) {
    moveFromStatusRegister++;
    return factory.createMoveFromStatusRegister(instr);
  }

  public T createMoveToStatusRegister(int instr) {
    moveToStatusRegister++;
    return factory.createMoveToStatusRegister(instr);
  }

  public T createSingleDataTransfer(int instr) {
    singleDataTransfer++;
    return factory.createSingleDataTransfer(instr);
  }

  public T createSoftwareInterrupt(int instr) {
    softwareInterrupt++;
    return factory.createSoftwareInterrupt(instr);
  }

  public T createUndefinedInstruction(int instr) {
    undefinedInstruction++;
    return factory.createUndefinedInstruction(instr);
  }

  public T createSwap(int instr) {
    swap++;
    return factory.createSwap(instr);
  }
  
  public int getNumInstructions() {
    return blockDataTransfer + branch + branchExchange + cpDataProcessing
        + cpDataTransfer + cpRegisterTransfer + dataProcessing + intMultiply
        + longMultiply + moveFromStatusRegister + moveToStatusRegister
        + singleDataTransfer + softwareInterrupt + undefinedInstruction + swap;
  }

  @Override
  public String toString() {
    return "Number of instructions: " + getNumInstructions();
  }
}
