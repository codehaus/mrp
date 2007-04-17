package org.binarytranslator.arch.arm.os.process.linux;

import org.binarytranslator.generic.os.abi.linux.LinuxSystemCallGenerator;

public class ARM_SyscallArgumentIterator implements
    LinuxSystemCallGenerator.CallArgumentIterator {

  private final ARM_LinuxProcessSpace ps;

  private int currentArgument;

  public ARM_SyscallArgumentIterator(ARM_LinuxProcessSpace ps) {
    this.ps = ps;
    this.currentArgument = 0;
  }

  public int nextInt() {
    return ps.registers.get(currentArgument++);
  }

  public long nextLong() {
    // only start reading longs from even registers
    if ((currentArgument & 1) == 1)
      currentArgument++;

    // TODO: This code is actually assuming that we're on little endian
    long lowWord = nextInt();
    long highWord = nextInt();

    return highWord << 32 | lowWord;
  }

  public void reset() {
    currentArgument = 0;
  }

}
