/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.branch;

/**
 * Object for recording a call site and its corresponding return address
 */
class CallAndReturnAddress {
  /**
   * Call site address
   */
  private int callSite;
  /**
   * Return address
   */
  private int returnAddress;
  /**
   * Constructor
   * @param callSite address of call site
   * @param returnAddress address of return
   */
  CallAndReturnAddress(int callSite, int returnAddress) {
    this.callSite = callSite;
    this.returnAddress = returnAddress;
  }
  /**
   * Get call site address
   */
  int getCallSite() {
    return callSite;
  }
  /**
   * Get return address
   */
  int getReturnAddress() {
    return returnAddress;
  }
  /** 
   * Are two call sites the same?
   */
  public boolean equals(Object obj) {
    return ((CallAndReturnAddress)obj).callSite == callSite;
  }
}
