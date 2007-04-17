package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.DBT;

class Utils {

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
   * Extracts a subsequence of bits from a word.
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
      DBT._assert(from < to && from >= 0 && to <= 31);
    return (word & ((1 << (to + 1)) - 1)) >> from;
  }

  /** 
   * Sign extends a given value.
   * @param value
   *  The value to sign extends.
   * @param bitsUsed
   *  The number bits used within this values.
   * @return
   *  A sign extended value.
   */
  static int signExtend(int value, int bitsUsed) {
    return (value << (32 - bitsUsed)) >> (32 - bitsUsed);
  }
  
  /** 
   * Returns true, if the addition of both operands as unsigned integers will cause an overflow.
   * This basically checks <code> operand1 + operand2 &gt; Integer.MAX_VALUE</code>.
   */
  static boolean unsignedAddOverflow(int operand1, int operand2) {
    return operand1 > Integer.MAX_VALUE - operand2; 
  }
  
  /** 
   * Returns true, if the subtraction of both operand1 - operand2 (both unsigned) will be a negative number.
   * That only happens when <code>operand1 &lt; operand2</code>
   */
  static boolean unsignedSubOverflow(int operand1, int operand2) {
    return operand1 < operand2;
  }
  
  /** 
   * Returns true, if the addition of both operands as unsigned integers will cause an overflow.
   * The algorithm for this code was taken from http://msdn2.microsoft.com/en-us/library/ms972705.aspx.
   */
  static boolean signedAddOverflow(int operand1, int operand2) {
    //overflow can only occur when both signs differ
    if ((operand1 ^ operand2) >= 0) {
      return false;
    }
    
    if (operand1 < 0)
      return operand1 < Integer.MIN_VALUE - operand2;
    else
      return Integer.MAX_VALUE - operand1 < operand2;
  }
}
