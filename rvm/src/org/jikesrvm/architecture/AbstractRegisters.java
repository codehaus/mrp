package org.jikesrvm.architecture;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;

/**
 * The machine state comprising a thread's execution context, used both for
 * thread context switching and for software/hardware exception
 * reporting/delivery.
 */
@Uninterruptible
@NonMoving
public abstract class AbstractRegisters {

  /** General purpose registers */
  @Untraced
  private final WordArray gprs;
  @SuppressWarnings("unused")
  private final WordArray gprsShadow;
  /** Floating point registers */
  @Untraced
  private final double[] fprs;
  @SuppressWarnings("unused")
  private final double[] fprsShadow;
  /** Instruction address register */
  protected Address ip;

  /**
   * Do exception registers currently contain live values? Set by C hardware
   * exception handler and RuntimeEntrypoints.athrow and reset by each
   * implementation of ExceptionDeliverer.deliverException
   */
  private boolean inuse;

  /** Constructor */
  protected AbstractRegisters() {
    gprs = gprsShadow = MemoryManager.newNonMovingWordArray(ArchConstants.getNumberOfGPRs());
    fprs = fprsShadow = MemoryManager.newNonMovingDoubleArray(ArchConstants.getNumberOfFPRs());
  }

  public final boolean getInUse() {
    return inuse;
  }

  public final void setInUse(boolean b) {
    inuse = b;
  }

  public final WordArray getGPRs() {
    return gprs;
  }

  public final double[] getFPRs() {
    return fprs;
  }

  /** @return Instruction address register */

  public final Address getIP() {
    return ip;
  }
  /** Set instruction address register */

  public final void setIP(Address ip) {
    this.ip = ip;
  }

  /** @return memory location of IP register in this data structure */

  public final Address getIPLocation() {
    Offset ipOffset = ArchEntrypoints.registersIPField.getOffset();
    return Magic.objectAsAddress(this).plus(ipOffset);
  }

  /** Zero all registers */

  public void clear() {
    for (int i=0;i<gprs.length();++i) {
      gprs.set(i,Word.zero());
    }
    for (int i=0;i<fprs.length;++i) {
      fprs[i]=0.;
    }
    ip=Address.zero();
  }

  protected void dump() {
    for (int i=0;i<gprs.length();++i) {
      VM.sysWriteln("gprs[",i,"] = ",gprs.get(i));
    }
    for (int i=0;i<fprs.length;++i) {
      VM.sysWriteln("fprs[",i,"] = ",fprs[i]);
    }
    VM.sysWriteln("ip = ",ip);
  }

  /**
   * The following method initializes a thread stack as if
   * "startoff" method had been called by an empty baseline-compiled
   *  "sentinel" frame with one local variable
   *
   * @param ip The instruction pointer for the "startoff" method
   * @param sp The base of the stack
   */

  public abstract void initializeStack(Address ip, Address sp);

  /**
   * A thread's stack has been moved or resized.
   * Adjust the ESP register to reflect new position.
   *
   * @param delta The displacement to be applied
   * @param traceAdjustments Log all adjustments to stderr if true
   */

  public abstract void adjustESP(Offset delta, boolean traceAdjustments);

  /**
   * Set ip &amp; fp. used to control the stack frame at which a scan of
   * the stack during GC will start, for ex., the top java frame for
   * a thread that is blocked in native code during GC.
   */

  public abstract void setInnermost(Address returnAddress, Address callerFramePointer);


  public abstract Address getInnermostFramePointer();


  public abstract void unwindStackFrame();


  public abstract Address getInnermostInstructionAddress();
}
