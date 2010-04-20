package org.jikesrvm.architecture;

import org.jikesrvm.VM;

import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public final class StackFrameLayout {

  public static int getNormalStackSize() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_NORMAL;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_NORMAL;
    }
  }
  public static int getMaxStackSize() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_MAX;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_MAX;
    }
  }
  public static int getBootThreadStackSize() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_BOOT;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_BOOT;
    }
  }
  public static int getStackSizeCollector() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_COLLECTOR;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_COLLECTOR;
    }
  }
  public static int getStackSizeGCDisabled() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_GCDISABLED;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_GCDISABLED;
    }
  }
  public static int getStackSizeDLOpen() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_DLOPEN;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_DLOPEN;
    }
  }
  public static int getJNIStackGrowthSize() {
        if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_JNINATIVE_GROW;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_JNINATIVE_GROW;
    }
  }
  public static int getStackGrowthSize() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_GROW;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_GROW;
    }
  }
  public static int getStackSizeGuard() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_GUARD;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_GUARD;
    }
  }
  public static Address getStackFrameSentinelFP() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
    }
  }
  public static int getInvisibleMethodID() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
    }
  }
  public static int getStackFrameHeaderSize() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
    }
  }
  public static Offset getStackFrameMethodIDOffset(){
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
    }
  }
  public static Offset getStackFramePointerOffset() {
    if(VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET;
    } else {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET;
    }
  }
}
