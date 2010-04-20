package org.jikesrvm.compilers.opt.lir2mir;

public interface BURS_StateCoder {

  /* Action modifiers */

  public byte NOFLAGS           = 0x00;
  public byte EMIT_INSTRUCTION  = 0x01;
  public byte LEFT_CHILD_FIRST  = 0x02;
  public byte RIGHT_CHILD_FIRST = 0x04;

  /** Generate code */
  public void code(AbstractBURS_TreeNode p, int  n, int ruleno);
}