package org.jikesrvm.compilers.opt.instrsched;

import org.jikesrvm.VM;
import java.util.ArrayList;

/**
 * Consists of an operator class and information about resource usage
 * There is only one instance of each OperatorClass, which is stored
 * as a static final field in OperatorClass.  You can compare
 * OperatorClasses using ==.
 * Every Operator contains one of these.
 *
 * @see Operator
 * @see Operators
 */
public abstract class OperatorClass {
  /**
   * Empty Resources Mask
   */
  static int NONE = 0;

  /** Global class embodying all operator classes */
  protected static final OperatorClass any_insn = VM.BuildForIA32
    ? new org.jikesrvm.compilers.opt.instrsched.ia32.ArchOperatorClass(0)
    : new org.jikesrvm.compilers.opt.instrsched.ppc.ArchOperatorClass(0);

  /**
   * Resource Usage Masks
   */
  protected int[][] masks;

  /**
   * Id of the operator class
   */
  private final int id;

  /**
   * Maximum Latency of any instruction
   */
  protected int maxlat = 0;

  /**
   * Resource Classes used by this Operator Class
   */
  protected final ArrayList<OperatorClass> rclasses = new ArrayList<OperatorClass>();

  /**
   * Latencies to other classes
   */
  private final ArrayList<Integer> latencies = new ArrayList<Integer>();

  protected OperatorClass(int _id) {
    id = _id;
  }

  protected OperatorClass(int _id, ResourceReservation[] pat) {
    id = _id;
    allocateMasks(pat);
    if (verbose >= 2) debug(masks.length+" masks allocated for "+pat.length+
                            " requests");
    int[] assign = new int[pat.length];
    int comb = fillMasks(pat, assign, 0, 0, 0, 0);
    if (false && comb != masks.length)
      throw new InternalError("Insufficient Resources");
  }

  /**
   * Returns the maximum latency of any instruction in the class.
   * Note: it is faster to simply check the field directly, if possible.
   */
  public int maxLatency() {
    return maxlat;
  }

  /**
   * Returns latency lookup in the hashtable for a given operator class.
   */
  private Object latObj(OperatorClass opclass) {
    int latsize = latencies.size();
    Object latObj = null;
    if (latsize > opclass.id) latObj = latencies.get(opclass.id);

    // walk through backwards, since any_insn (most general) is first
    ArrayList<OperatorClass> opcrc = opclass.rclasses;
    for (int i = opcrc.size(); latObj == null && i > 0; i--) {
      OperatorClass rc = opcrc.get(i - 1);
      if (latsize > rc.id) latObj = latencies.get(rc.id);
    }

    for (int i = rclasses.size(); latObj == null && i > 0; i--) {
      OperatorClass rc = rclasses.get(i - 1);
      latObj = rc.latObj(opclass);
    }

    return latObj;
  }

  /**
   * Sets the operator class (for hierarchy)
   *
   * @param opClass operator class
   */
  public void setOpClass(OperatorClass opClass) {
    rclasses.add(opClass);
  }

  /**
   * Returns the latency between instructions in this class and given class
   *
   * @param opclass destination operator class
   * @return latency to given operator class
   */
  public int latency(OperatorClass opclass) {
    return (Integer) latObj(opclass);
  }

  /**
   * Sets the latency between instructions in this class and given class
   *
   * @param opclass destination operator class
   * @param latency desired latency
   */
  public void setLatency(OperatorClass opclass, int latency) {
    int latencies_size = latencies.size();
    if (opclass.id < latencies_size) {
      latencies.set(opclass.id, latency);
    } else {
      for(; latencies_size < opclass.id; latencies_size++) {
        latencies.add(null);
      }
      latencies.add(latency);
    }
  }
  /**
   * Sets the latency between instructions in given class and this class
   *
   * @param opclass source operator class
   * @param latency desired latency
   */
  public void setRevLatency(OperatorClass opclass, int latency) {
    opclass.setLatency(this, latency);
  }

  /**
   * Returns the string representation of this operator class.
   */
  public String toString() {
    StringBuffer sb = new StringBuffer("Size=");
    sb.append(masks.length).append('\n');
    for (int[] mask : masks) {
      for (int v : mask)
        sb.append(toBinaryPad32(v)).append('\n');
      sb.append('\n');
    }
    return sb.toString();
  }

  protected abstract void allocateMasks(ResourceReservation[] pat);

  protected abstract int fillMasks(ResourceReservation[] pat, int[] assign,
                                   int all, int rrq, int comb, int depth);

  /** Returns a special resource type embodying all resources of a given class. */
  protected static int all_units(int rclass) {
    return rclass | 0x80000000;
  }

  // debug level (0 = no debug)
  protected static final int verbose = 0;

  protected static void debug(String s) {
    System.err.println(s);
  }
  private static String SPACES = null;
  protected static void debug(int depth, String s) {
    if (SPACES == null) SPACES = dup(7200, ' ');
    debug(SPACES.substring(0,depth*2)+s);
  }

  // Padding
  private static final String ZEROS = dup(32, '0');
  protected static String toBinaryPad32(int value) {
    String s = Integer.toBinaryString(value);
    return ZEROS.substring(s.length())+s;
  }

  // Generates a string of a given length filled by a given character.
  private static String dup(int len, char c) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < len; i++)
      sb.append(c);
    return sb.toString();
  }
}
