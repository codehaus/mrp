/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.VM;
import static org.jikesrvm.classloader.BytecodeConstants.*;
import org.jikesrvm.classloader.BytecodeStream;
import org.jikesrvm.classloader.NormalMethod;
import org.vmmagic.pragma.Pure;

/**
 * Profile data for all conditional branches (including switches)
 * of a single RVMMethod. {@see EdgeCounts}
 */
public final class BranchProfiles {
  /** Method containing counters */
  private final NormalMethod method;
  /** Number of counters */
  private final int numCounters;
  /** Branch profile for each profiled bytecode */
  private final BranchProfile[] data;

  /**
   * Find the BranchProfile for a given bytecode index in the BranchProfile array
   * @param bcIndex the bytecode index of the branch instruction
   * @return the desired BranchProfile, or null if it cannot be found.
   */
  public BranchProfile getEntry(int bcIndex) {
    int low = 0;
    int high = data.length - 1;
    while (true) {
      int mid = (low + high) >> 1;
      int bci = data[mid].getBytecodeIndex();
      if (bci == bcIndex) {
        return data[mid];
      }
      if (low >= high) {
        // search failed
        if (VM.VerifyAssertions) { VM._assert(false); }
        return null;
      }
      if (bci > bcIndex) {
        high = mid - 1;
      } else {
        low = mid + 1;
      }
    }
  }

  public void print(java.io.PrintStream ps) {
    ps.println("M " + numCounters + " " + method.getMemberRef());
    for (BranchProfile profile : data) {
      ps.println("\t" + profile);
    }
  }

  BranchProfiles(NormalMethod m, int[] cs) {
    method = m;
    numCounters = cs.length;

    // Originally we only allocate half of the number of edges for branch
    // profiles, like data = new BranchProfile[cs.length/2]
    // The conditional branch, tableswitch and lookupswitch all have at
    // least two edges, supposingly. Then we found that the lookupswitch
    // bytecode could have only one edge, so the number of branch profiles
    // is not necessarily less than half of the number of edges.
    BranchProfile[] data = new BranchProfile[cs.length];
    BytecodeStream bcodes = m.getBytecodes();
    int dataIdx = 0;
    int countIdx = 0;

    // We didn't record the bytecode index in the profile data to record space.
    // Therefore we must now recover that information.
    // We exploit the fact that the baseline compiler generates code in
    // a linear pass over the bytecodes to make this possible.
    while (bcodes.hasMoreBytecodes()) {
      int bcIndex = bcodes.index();
      int code = bcodes.nextInstruction();
      switch (code) {
        case JBC_ifeq:
        case JBC_ifne:
        case JBC_iflt:
        case JBC_ifge:
        case JBC_ifgt:
        case JBC_ifle:
        case JBC_if_icmpeq:
        case JBC_if_icmpne:
        case JBC_if_icmplt:
        case JBC_if_icmpge:
        case JBC_if_icmpgt:
        case JBC_if_icmple:
        case JBC_if_acmpeq:
        case JBC_if_acmpne:
        case JBC_ifnull:
        case JBC_ifnonnull: {
          int yea = cs[countIdx + EdgeCounts.TAKEN];
          int nea = cs[countIdx + EdgeCounts.NOT_TAKEN];
          int offset = bcodes.getBranchOffset();
          boolean backwards = offset < 0;
          countIdx += 2;
          data[dataIdx++] = new ConditionalBranchProfile(bcIndex, yea, nea, backwards);
          break;
        }

        case JBC_tableswitch: {
          bcodes.alignSwitch();
          bcodes.getDefaultSwitchOffset();
          int low = bcodes.getLowSwitchValue();
          int high = bcodes.getHighSwitchValue();
          int n = high - low + 1;
          data[dataIdx++] = new SwitchBranchProfile(bcIndex, cs, countIdx, n + 1);
          countIdx += n + 1;
          bcodes.skipTableSwitchOffsets(n);
          break;
        }

        case JBC_lookupswitch: {
          bcodes.alignSwitch();
          bcodes.getDefaultSwitchOffset();
          int numPairs = bcodes.getSwitchLength();
          data[dataIdx++] = new SwitchBranchProfile(bcIndex, cs, countIdx, numPairs + 1);
          countIdx += numPairs + 1;
          bcodes.skipLookupSwitchPairs(numPairs);
          break;
        }

        default:
          bcodes.skipInstruction();
          break;
      }
    }

    // Make sure we are in sync
    if (VM.VerifyAssertions) VM._assert(countIdx == cs.length);

    if (dataIdx != data.length) {
      // We had a switch statment; shrink the array.
      BranchProfile[] newData = new BranchProfile[dataIdx];
      for (int i = 0; i < dataIdx; i++) {
        newData[i] = data[i];
      }
      data = newData;
    }
    this.data = data;
  }

  /**
   * Profile data for a branch instruction.
   */
   abstract static class BranchProfile {
    /** The bytecode index of the branch instruction */
    private final int bci;

    /** The number of times the branch was executed. */
    private final float freq;

    /**
     * @param _bci the bytecode index of the source branch instruction
     * @param _freq the number of times the branch was executed
     */
    BranchProfile(int _bci, float _freq) {
      bci = _bci;
      freq = _freq;
    }

    public final int getBytecodeIndex() { return bci; }

    public final float getFrequency() { return freq; }

    /** Convert count to float handling overflow */
    @Pure
    static float countToFloat(int count) {
      if (count < 0) {
        final float MAX_UNSIGNED_INT = 2147483648f;
        return MAX_UNSIGNED_INT + (float)Math.abs(count);
      } else {
        return (float)count;
      }
    }
  }

  /**
   * Profile data for a branch instruction.
   */
  public static final class ConditionalBranchProfile extends BranchProfile {
    /** Probability of being taken */
    private final float taken;
    /** Backward branch */
    private final boolean backwards;

    /**
     * @param _bci the bytecode index of the source branch instruction
     * @param yea the number of times the branch was taken
     * @param nea the number of times the branch was not taken
     * @param bw is this a backwards branch?
     */
    ConditionalBranchProfile(int _bci, int yea, int nea, boolean bw) {
      super(_bci, countToFloat(yea)+countToFloat(nea));
      taken = countToFloat(yea);
      backwards = bw;
    }

    public float getTakenProbability() {
      float freq = getFrequency();
      if (freq > 0) {
        return taken / freq;
      } else if (backwards) {
        return 0.9f;
      } else {
        return 0.5f;
      }
    }

    public String toString() {
      float freq = getFrequency();
      int bci = getBytecodeIndex();
      String ans = bci + (backwards ? "\tbackbranch" : "\tforwbranch");
      ans += " < " + (int) taken + ", " + (int) (freq - taken) + " > ";
      if (freq > 0) {
        ans += (100.0f * taken / freq) + "% taken";
      } else {
        ans += "Never Executed";
      }
      return ans;
    }
  }

  /**
   * Profile data for a branch instruction.
   */
  public static final class SwitchBranchProfile extends BranchProfile {
    /**
     * The number of times that the different arms of a switch were
     * taken. By convention, the default case is the last entry.
     */
    private final float[] counts;

    /**
     * @param _bci the bytecode index of the source branch instruction
     * @param cs counts
     * @param start idx of first entry in cs
     * @param numEntries number of entries in cs for this switch
     */
    SwitchBranchProfile(int _bci, int[] cs, int start, int numEntries) {
      super(_bci, sumCounts(cs, start, numEntries));
      counts = new float[numEntries];
      for (int i = 0; i < numEntries; i++) {
        counts[i] = countToFloat(cs[start + i]);
      }
    }

    public float getDefaultProbability() {
      return getProbability(counts.length - 1);
    }

    public float getCaseProbability(int n) {
      return getProbability(n);
    }

    private float getProbability(int n) {
      float freq = getFrequency();
      if (freq > 0) {
        return counts[n] / freq;
      } else {
        return 1.0f / counts.length;
      }
    }

    public String toString() {
      int bci = getBytecodeIndex();
      String res = bci + "\tswitch     < " + (int) counts[0];
      for (int i = 1; i < counts.length; i++) {
        res += ", " + (int) counts[i];
      }
      return res + " >";
    }

    private static float sumCounts(int[] counts, int start, int numEntries) {
      float sum = 0.0f;
      for (int i = start; i < start + numEntries; i++) {
        sum += countToFloat(counts[i]);
      }
      return sum;
    }
  }
}
