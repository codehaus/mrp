package org.binarytranslator.generic.decoder;

/**
 * TODO: Add comments for this interface.
 *
 */
public interface Interpreter {
  
  public interface Instruction {
    
    void execute();
    int getSuccessor(int pc);
  }
  
  Instruction decode(int pc);
}
