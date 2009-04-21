package org.binarytranslator.generic.decoder;

public interface Disassembler {
  /**
   * Represents an instruction, after it has been decoded by a disassembler.
   * The instruction should be printable in the format of the target system's assembly language.
   * 
   * @author Michael Baer
   */
  public interface Instruction {
    /**
     * Shall return the address of the instruction following this one in <i>code order</i>, given that
     * the current instruction has been decoded from address <code>pc</code>.
     * @param pc
     *  The address from which this instruction has been decoded.
     * @return
     *  The address of the instruction following this one in code order.
     */
    int getSuccessor(int pc);
    
    /** 
     * Shall return a string representation of the disassembled instruction.
     * We might have used toString() here, but I wanted it to be obvious if a dedicated implementation
     * of this method is missing.
     * 
     * @return
     *  A string representation of the disassembled instruction.
     *  For example:
     *  <code>
     *    ADD r0, r1, #15
     *  </code>
     *  for an ARM ADD instruction.
     */
    String asString();
  }
  
  /**
   * Disassemble the instruction at address <code>pc</code> and return an appropriate object representation.
   * 
   * @param pc
   *  The address at which the instruction resides.
   * @return
   *  An object representation of the said instruction.
   */
  Instruction disassemble(int pc);
}
