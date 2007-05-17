package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.generic.decoder.Laziness;

public class ARM_Laziness extends Laziness {
  
  final class ARM_LazinessKey extends Key {
    private final int pc;

    public int hashCode() {
      return pc;
    }

    public boolean equals(Object o) {
      return ((o instanceof ARM_LazinessKey) && ((ARM_LazinessKey) o).pc == pc);
    }

    ARM_LazinessKey(int pc) {
      this.pc = pc;
    }

    public String toString() {
      return "0x" + Integer.toHexString(pc);
    }
  }
  
  @Override
  public Object clone() {
    return new ARM_Laziness();
  }

  @Override
  public boolean equivalent(Laziness other) {
    return other instanceof ARM_Laziness;
  }

  @Override
  public Key makeKey(int pc) {
    return new ARM_LazinessKey(pc);
  }

}
