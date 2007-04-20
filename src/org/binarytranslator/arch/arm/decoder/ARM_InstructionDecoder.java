package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.arch.arm.decoder.ARM_Instructions.*;

/**
 * This class decodes an ARM instruction and uses a user-supplied ARM_InstructionFactory to create a class
 * that represents the given instruction.
 * 
 * The decoder first performs a pseudo-switch on bits 27-25 of the instruction.
 * For performance reasons, the switch is implemented as an array lookup (using the {@link #prefixDecoders} array),
 * with single {@link Decoder} classes implementing the cases.
 * 
 * ARM has a very cluttered opcode map, which is why the decoding process does not look very tidy.
 * However, the presented decoded scheme has been derived by producing all possible instructions and then
 * letting a data mining tool (specifically: Weka) create a decision tree to decode the single
 * instruction classes. This has two implications:
 * <ol> 
 *  <li>The decoder is correct (at least, when I didn't introduce any typos), as Weka verified an error rate of 0% for this decision tree.</li>
 *  <li>The decoder is reasonably fast, considering Weka tries to build a shallow decision tree.</li>
 * </ol>
 * 
 * @author Michael Baer
 *
 */
public class ARM_InstructionDecoder {
  
  /** 
   * This table is used to perform a lookup on bits 25-27 of an instruction. 
   * According to the result of this lookup, the {@link Decoder} instances within this 
   * array perform the subsequent instruction decoding. */
  private static Decoder[] prefixDecoders = {
    new decoder_000(), new decoder_001(), 
    new decoder_010(), new decoder_011(),
    new decoder_100(), new decoder_101(),
    new decoder_110(), new decoder_111()
  };
  
  /** 
   * This static field caches the default {@link ARM_InstructionFactory} implementation, which is used by {@link #decode(int)}.
   * It is being lazily initialized once it is used for the first time. */
  private static DefaultFactory _defaultFactory;
  
  /** 
   * This class performs additional instruction decoding, after bits 25-27 of an instruction have been
   * determined. The class is basically just a way of substituting a switch by an array lookup+virtual method call.
   * */
  private static abstract class Decoder {
    abstract <T> T decode(int instr, ARM_InstructionFactory<T> factory); 
  }
  
  /** Decoder which assumes that bits 27-25 == 000. */
  private static class decoder_000 extends Decoder {

    @Override
    <T> T decode(int instr, ARM_InstructionFactory<T> factory) {
      
      //Check condition==never?
      if ((instr & 0xF0000000) == 0xF0000000) {
        return factory.createUndefinedInstruction(instr);
      }
      
      byte bits_7_4 = (byte)Utils.getBits(instr, 4, 7);

      if (bits_7_4 == 0 && ((instr & 0x01900000) == 0x01000000)) {
        //Utils.getBit(instr, 24) == true && Utils.getBit(instr, 23) == false && Utils.getBit(instr, 20) == false
        if (Utils.getBit(instr, 21))
          return factory.createMoveToStatusRegister(instr);
        else
          return factory.createMoveFromStatusRegister(instr);
      }
      
      if (bits_7_4 == 1 && ((instr & 0x01F00000) == 0x01200000)) {
        //Utils.getBit(instr, 24) == true && Utils.getBit(instr, 23) == false && Utils.getBit(instr, 22) == false && Utils.getBit(instr, 21) == true && Utils.getBit(instr, 20) == false
        return factory.createBranchExchange(instr);
      }
      
      if ((bits_7_4 & 9) == 9) {
        //bits7-4 = 1xx1
        if (bits_7_4 == 9) {
          if (Utils.getBit(instr, 23)) {
            return factory.createLongMultiply(instr);
          }
          else {
            if (Utils.getBit(instr, 24))
              return factory.createSwap(instr);
            else
              return factory.createIntMultiply(instr);
          }
        }
        else
          return factory.createSingleDataTransfer(instr);
      }
      
      return factory.createDataProcessing(instr);
    }
  }
  
  /** Decoder which assumes that bits 27-25 == 001. */
  private static class decoder_001 extends Decoder {

    @Override
    <T> T decode(int instr, ARM_InstructionFactory<T> factory) {
      
      //Check condition==never?
      if ((instr & 0xF0000000) == 0xF0000000) {
        return factory.createUndefinedInstruction(instr);
      }
      
      if (((instr & 0x01900000) == 0x01000000)) {
        //Utils.getBit(instr, 24) == true && Utils.getBit(instr, 23) == false && Utils.getBit(instr, 20) == false
        if (Utils.getBit(instr, 21))
          return factory.createMoveToStatusRegister(instr);
        else
          return factory.createUndefinedInstruction(instr);
      }
      
      return factory.createDataProcessing(instr);
    }
  }
 
  /** Decoder which assumes that bits 27-25 == 010. */
  private static class decoder_010 extends Decoder {

    @Override
    <T> T decode(int instr, ARM_InstructionFactory<T> factory) {
      //Check condition==never?
      if ((instr & 0xF0000000) == 0xF0000000) {
        return factory.createUndefinedInstruction(instr);
      }
      else {
        return factory.createSingleDataTransfer(instr);
      }
    }
  }
  
  /** Decoder which assumes that bits 27-25 == 011. */
  private static class decoder_011 extends Decoder {

    @Override
    <T> T decode(int instr, ARM_InstructionFactory<T> factory) {
      //Check condition==never? or bit4==true
      if ((instr & 0xF0000000) == 0xF0000000 || Utils.getBit(instr, 4)) {
        return factory.createUndefinedInstruction(instr);
      }
      else {
        return factory.createSingleDataTransfer(instr);
      }
    }
  }
  
  /** Decoder which assumes that bits 27-25 == 100. */
  private static class decoder_100 extends Decoder {

    @Override
    <T> T decode(int instr, ARM_InstructionFactory<T> factory) {
      //Check condition==never?
      if ((instr & 0xF0000000) == 0xF0000000) {
        return factory.createUndefinedInstruction(instr);
      }
      
      return factory.createBlockDataTransfer(instr);
    }
  }
  
  /** Decoder which assumes that bits 27-25 == 101. */
  private static class decoder_101 extends Decoder {

    @Override
    <T> T decode(int instr, ARM_InstructionFactory<T> factory) {
      //Check condition==never?
      if ((instr & 0xF0000000) == 0xF0000000) {
        return factory.createBranchExchange(instr);
      }
      
      return factory.createBranch(instr);
    }
  }
  
  /** Decoder which assumes that bits 27-25 == 110. */
  private static class decoder_110 extends Decoder {

    @Override
    <T> T decode(int instr, ARM_InstructionFactory<T> factory) {
      return factory.createCoprocessorDataTransfer(instr);
    }
  }
  
  /** Decoder which assumes that bits 27-25 == 111. */
  private static class decoder_111 extends Decoder {

    @Override
    <T> T decode(int instr, ARM_InstructionFactory<T> factory) {
      if (Utils.getBit(instr, 24)) {
        //Check condition==never?
        if ((instr & 0xF0000000) == 0xF0000000) {
          return factory.createUndefinedInstruction(instr);
        }
        else {
          return factory.createSoftwareInterrupt(instr);
        }
      }
      else {
        if (Utils.getBit(instr, 4)) {
          return factory.createCoprocessorRegisterTransfer(instr);
        }
        else {
          return factory.createCoprocessorDataProcessing(instr);
        }
      }
    }
  }
  
  /**
   * Decodes a given ARM instruction and returns an object representation of it.
   * @param instruction
   *  A binary ARM instruction, that is to be decoded.
   * @return
   *  A version of the instruction, which has been decoded into an instance of {@link Instruction}.
   *  Use the {@link Instruction#visit(ARM_InstructionVisitor)} method to further interact with the
   *  returned instance.
   */
  public static Instruction decode(int instruction) {
    if (_defaultFactory == null)
      _defaultFactory = new DefaultFactory();
    
    return decode(instruction, _defaultFactory);
  }

  /**
   * Decodes a binary ARM instruction. This method will use the supplied {@link ARM_InstructionFactory}
   * to create an object representation of the decoded instruction.
   * @param <T>
   *  The return type depends on whatever the {@link ARM_InstructionFactory} actually creates.
   * @param instruction
   *  A binary representation of the instruction that is to be decoded.
   * @param factory
   *  A factory, that will create object instances of the instruction.
   * @return
   *  An object representation of the decoded instruction.
   */
  static <T> T decode(int instruction, ARM_InstructionFactory<T> factory) {
    
    int bits_27_25 = Utils.getBits(instruction, 25, 27);
    return prefixDecoders[bits_27_25].decode(instruction, factory);
  }
  
  /**
   * An interface to a factory class, which will create the actual object representations of the
   * instruction classes decoded by {@link ARM_InstructionDecoder}.
   *
   * @param <T>
   *  The type of the object representations, that shall be created when an ARM instruction is decoded.
   */
  interface ARM_InstructionFactory<T> {
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
    T createUndefinedInstruction(int instr);
  }
  
  /**
   * A default implementation of the ARM instruction factory, which will create the 
   * appropriate classes from the {@link ARM_Instructions} namespace.
   */
  static class DefaultFactory implements ARM_InstructionFactory<ARM_Instructions.Instruction> {

    public Instruction createBlockDataTransfer(int instr) {
      return new MultipleDataTransfer(instr);
    }

    public Instruction createBranch(int instr) {
      return new Branch(instr);
    }

    public Instruction createBranchExchange(int instr) {
      return new BranchExchange(instr);
    }

    public Instruction createCoprocessorDataProcessing(int instr) {
      return new CoprocessorDataProcessing(instr);
    }

    public Instruction createCoprocessorDataTransfer(int instr) {
      return new CoprocessorDataTransfer(instr);
    }

    public Instruction createCoprocessorRegisterTransfer(int instr) {
      return new CoprocessorRegisterTransfer(instr);
    }

    public Instruction createDataProcessing(int instr) {
      return new DataProcessing(instr);
    }

    public Instruction createIntMultiply(int instr) {
      return new IntMultiply(instr);
    }

    public Instruction createLongMultiply(int instr) {
      return new LongMultiply(instr);
    }

    public Instruction createMoveFromStatusRegister(int instr) {
      return new MoveFromStatusRegister(instr);
    }

    public Instruction createMoveToStatusRegister(int instr) {
      return new MoveToStatusRegister(instr);
    }

    public Instruction createSingleDataTransfer(int instr) {
      return new SingleDataTransfer(instr);
    }

    public Instruction createSoftwareInterrupt(int instr) {
      return new SoftwareInterrupt(instr);
    }

    public Instruction createSwap(int instr) {
      return new Swap(instr);
    }

    public Instruction createUndefinedInstruction(int instr) {
      return null;
    }
    
  }
}
