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
package org.jikesrvm;

import org.vmmagic.pragma.RuntimeFinal;

/**
 * Flags that control the behavior of our virtual machine.
 *
 * Typically these are properties that can be set from the command line
 * (and thus are NOT final).  All final properties should be
 * declared in Configuration
 */
public class Properties extends Options {

  // The VM class hierarchy is used in three ways:
  //    - by boot image writer to create an executable vm image
  //    - by tools that wish use VM classes for generic java programming
  //    - by vm image itself, at execution time
  // The following flags specify which behavior is desired.
  //

  /**
   * use classes for boot image generation? (see BootImageWriter)
   */
  @RuntimeFinal(false)
  public static boolean writingBootImage;
  /**
   * use classes for generic java programming?
   */
  @RuntimeFinal(false)
  public static boolean runningTool;
  /**
   * use classes for running actual VM?
   */
  @RuntimeFinal(true)
  public static boolean runningVM;
  /**
   * are we in the boot-image-writing portion of boot-image-creation
   */
  @RuntimeFinal(false)
  public static boolean writingImage = false;
  /**
   * is the running VM fully booted?
   * Set by VM.boot when the VM is fully booted.
   */
  public static boolean fullyBooted = false;

  /**
   * Is it safe to create a java.lang.Thread now?  Set by VM.boot at the
   * appropriate time.
   */
  public static boolean safeToAllocateJavaThread = false;

  /**
   * The following is set on by -X:verboseBoot= command line arg.
   * When true, it generates messages to the sysWrite stream summarizing
   * progress during the execution of VM.boot
   */
  public static int verboseBoot = 0;

  /**
   * The following is set on by -verbose:class command line arg.
   * When true, it generates messages to the sysWrite stream summarizing
   * class loading activities
   */
  public static boolean verboseClassLoading = false;

  /**
   * The following is set on by -verbose:jni command line arg.
   * When true, it generates messages to the sysWrite stream summarizing
   * jni activities
   */
  public static boolean verboseJNI = false;

  /**
   * Is stack growth managed by the OS/signals or automatic tests?
   */
  public static final boolean AutomaticStackGrowth = Configuration.BuildForWindows;

  // Runtime subsystem tracing.
  //
  public static final boolean TraceDictionaries = false;
  public static final boolean TraceStatics = false;
  public static final boolean TraceFileSystem = false;
  public static final boolean TraceThreads = false;
  public static final boolean TraceStackTrace = false;
  public static final boolean TraceExceptionDelivery = false;

  // Baseline compiler reference map tracing.
  //
  public static final boolean TraceStkMaps = false;
  public static final boolean ReferenceMapsStatistics = false;
  public static final boolean ReferenceMapsBitStatistics = false;

  public static final boolean TraceOnStackReplacement = false;

  /** How much farther? */
  public static final int maxSystemTroubleRecursionDepthBeforeWeStopVMSysWrite = 3;
}
