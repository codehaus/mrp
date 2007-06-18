package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;

public class Utils {

  /**
   * Checks if a bit is set within a word.
   * @param word
   *  The word that is being examined.
   * @param bit
   *  The number of the bit that is to be checked, starting from zero.
   * @return
   *  True, if the given bit is set within the word, false otherwise.
   */
  static final boolean getBit(int word, int bit) {
    if (DBT.VerifyAssertions)
      DBT._assert(bit >= 0 && bit <= 31);
    return (word & (1 << bit)) != 0;
  }
  
  /**
   * Same functions as {@link #getBit(int, int)} for shorts.
   */
  static final boolean getBit(short word, int bit) {
    if (DBT.VerifyAssertions)
      DBT._assert(bit >= 0 && bit <= 15);
    return (word & (1 << bit)) != 0;
  }

  /**
   * Extracts a subsequence of bits from a word and shifts the beginning of that subsequence to 
   * a zero based-number.
   * A call to <code>getBits(0xFF, 2, 3)</code> would return 0x3.
   * @param word
   *  The word that is to be examined.
   * @param from
   *  The first bit (starting from 0) that is to be extracted.
   * @param to
   *  The last bit (starting from 0) that is to be extracted from the word.
   * @return
   *  A zero-based version of the bit sequence.
   */
  static final int getBits(int word, int from, int to) {
    if (DBT.VerifyAssertions)
      DBT._assert(from < to && from >= 0 && to < 31);
    
    return (word & ((1 << (to + 1)) - 1)) >> from;
  }
  
  /**
   * Same function as {@link #getBits(int, int, int)} for shorts.
   */
  static final int getBits(short word, int from, int to) {
    if (DBT.VerifyAssertions)
      DBT._assert(from < to && from >= 0 && to <= 15);
    
    return (word & ((1 << (to + 1)) - 1)) >> from;
  }

  /** 
   * Sign extends a given value.
   * @param value
   *  The value to sign extends.
   * @param bitsUsed
   *  The number bits used within this value.
   * @return
   *  A sign extended value.
   */
  static int signExtend(int value, int bitsUsed) {
    return (value << (32 - bitsUsed)) >> (32 - bitsUsed);
  }
  
  /** 
   * Returns true, if the addition of both operands as unsigned integers will cause an overflow.
   * At the moment, this converts both values to longs and checks if the 33rd bit (which actually represents the carry)
   * overflows.
   */
  public static boolean unsignedAddOverflow(int operand1, int operand2) {
    long value1 = (long)operand1 & 0xFFFFFFFFL;
    long value2 = (long)operand2 & 0xFFFFFFFFL;

    return ((value1 + value2) & 0x100000000L) != 0;
  }
  
  /** 
   * Returns true, if the subtraction of both operand1 - operand2 (both unsigned) will issue a borrow.
   */
  public static boolean unsignedSubOverflow(int operand1, int operand2) {
    operand1 += Integer.MIN_VALUE;
    operand2 += Integer.MIN_VALUE;
    
    return operand1 < operand2;
  }
  
  /** 
   * Returns true, if the addition of both operands as signed integers will cause an overflow.
   * This basically checks <code>operand1 + operand2 &gt; Integer.MAX_VALUE</code>.
   */
  public static boolean signedAddOverflow(int operand1, int operand2) {
    return operand1 > Integer.MAX_VALUE - operand2;
  }
  
  /** 
   * Returns true, if the subtraction of operand1 from operand (as signed integers)
   *  will cause an overflow.
   * This basically checks <code>operand1 - operand2 &lt; Integer.MIN_VALUE</code>.
   */
  public static boolean signedSubOverflow(int operand1, int operand2) {
    // if the MSB is already set in any of the operands, then no overflow can
    // occur
    if (operand1 >= 0) {
      return (operand2 < 0) && ((operand1-operand2) < 0);
    }
    else {
      return (operand2 > 0) && ((operand1-operand2) > 0);
    }
  }
  
  /** 
   * Performs a number of sanity tests that make sure that the above functions are working the
   * manner described before.*/
  public static void runSanityTests() {
    if (!Utils.unsignedAddOverflow(1, -1)) {
      throw new RuntimeException("Error");
    }
    
    if (!Utils.unsignedAddOverflow(-1, -1)) {
      throw new RuntimeException("Error");
    }
    
    if (!Utils.unsignedAddOverflow(-1, 1)) {
      throw new RuntimeException("Error");
    }
    
    if (Utils.unsignedAddOverflow(10000, 10000)) {
      throw new RuntimeException("Error");
    }
    
    if (Utils.unsignedSubOverflow(-1, 1)) {
      throw new RuntimeException("Error");
    }
    
    if (!Utils.unsignedSubOverflow(1, -1)) {
      throw new RuntimeException("Error");
    }
    
    if (Utils.unsignedSubOverflow(-1, -1)) {
      throw new RuntimeException("Error");
    }
    
    if (Utils.unsignedSubOverflow(10, 0)) {
      throw new RuntimeException("Error");
    }
    
    if (!Utils.unsignedSubOverflow(0, 10)) {
      throw new RuntimeException("Error");
    }
    
    if (!Utils.signedAddOverflow(0x70000000, 0x10000000)) {
      throw new RuntimeException("Error");
    }
    
    if (Utils.signedAddOverflow(0x90000000, 0x10000000)) {
      throw new RuntimeException("Error");
    }
    
    if (!Utils.signedAddOverflow(0x50000000, 0x50000000)) {
      throw new RuntimeException("Error");
    }
    
    if (Utils.signedAddOverflow(0x60000000, 0x10000000)) {
      throw new RuntimeException("Error");
    }
    
    if (Utils.signedAddOverflow(0x10000000, 0x60000000)) {
      throw new RuntimeException("Error");
    }
    
    if (!Utils.signedSubOverflow(0x80000000, 0x30000000)) {
      throw new RuntimeException("Error");
    }
    
    if (!Utils.signedSubOverflow(0x30000000, 0x80000000)) {
      throw new RuntimeException("Error");
    }
    
    if (!Utils.signedSubOverflow(0, 0x80000000)) {
      throw new RuntimeException("Error");
    }
    
    if (Utils.signedSubOverflow(0, 0x70000000)) {
      throw new RuntimeException("Error");
    }
  }
}
