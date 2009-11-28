/*
 *  This file is part of MRP (http://mrp.codehaus.org/).
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
