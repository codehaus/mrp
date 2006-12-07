/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.branch;
import java.util.HashSet;
/**
 * Objects capturing information about what looks like a method
 */
class ProcedureInformation {
  /**
   * Entry point to the procedure
   */
  private int entry;
  /**
   * Set of locations that call the procedure and the corressponding return address
   */
  private HashSet callSitesAndReturnAddresses;
  /**
   * Set of locations within the procedure that return
   */
  private HashSet returnSites;

  /**
   * Constructor
   *
   * @param entry starting address of the procedure
   * @param callSite the address calling the procedure
   * @param returnAddress the corresponding return address
   */
  ProcedureInformation(int entry, int callSite, int returnAddress) {
    this.entry = entry;
    callSitesAndReturnAddresses = new HashSet();
    callSitesAndReturnAddresses.add(new CallAndReturnAddress(callSite, returnAddress));
  }

  /**
   * Register a call (branch and link) instruction
   * @param pc the address of the branch instruction
   * @param ret the address that will be returned to
   */
  public void registerCall(int pc, int ret) {
    CallAndReturnAddress call_tuple = new CallAndReturnAddress(pc, ret);
    if(!callSitesAndReturnAddresses.contains(call_tuple)) {
      callSitesAndReturnAddresses.add(call_tuple);
    }
  }

  /**
   * Register a return (branch to link register) instruction
   * @param pc the address of the branch instruction
   * @param ret the address that will be returned to
   */
  public void registerReturn(int pc, int ret) {
    if(returnSites == null) {
      returnSites = new HashSet();
    }
    returnSites.add(new Integer(pc));
    // TODO: capture that the instruction prior to ret is a call
    // site to this procedure
  }
}

