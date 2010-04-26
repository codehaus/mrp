package org.jikesrvm.compilers.opt.lir2mir;

public interface BURS_StateCoder {

  /* Action modifiers */

  byte NOFLAGS           = 0x00;
  byte EMIT_INSTRUCTION  = 0x01;
  byte LEFT_CHILD_FIRST  = 0x02;
  byte RIGHT_CHILD_FIRST = 0x04;

  /** Generate code */
  void code(AbstractBURS_TreeNode p, int  n, int ruleno);
}

