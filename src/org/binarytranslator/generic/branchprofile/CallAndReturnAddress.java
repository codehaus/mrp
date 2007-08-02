/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.branchprofile;

/**
 * Object for recording a call site and its corresponding return address
 */
class CallAndReturnAddress {
  
  /** The address at which the call is taking place.*/
  private int callSite;

  /** The address to which the call shall return. */
  private int returnAddress;

  /**
   * Constructor
   * 
   * @param callSite
   *          address of call site
   * @param returnAddress
   *          address of return
   */
  CallAndReturnAddress(int callSite, int returnAddress) {
    this.callSite = callSite;
    this.returnAddress = returnAddress;
  }

  /** Get call site address */
  int getCallSite() {
    return callSite;
  }

  /** Get return address */
  int getReturnAddress() {
    return returnAddress;
  }
  
  @Override
  public int hashCode() {
    return callSite;
  }

  @Override
  public boolean equals(Object obj) {
    
    if (!(obj instanceof CallAndReturnAddress)) {
      return false;
    }
    
    CallAndReturnAddress other = ((CallAndReturnAddress) obj);
    return other.callSite == callSite && other.returnAddress == returnAddress;
  }
}
