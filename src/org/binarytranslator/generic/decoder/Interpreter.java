package org.binarytranslator.generic.decoder;

public interface Interpreter {
  
  public interface Instruction {
    
    void execute();
    int getSuccessor(int pc);
  }
  
  Instruction decode(int pc);
}
