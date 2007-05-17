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
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.binarytranslator.DBT;

/**
 * Object capturing branches and jumps so that traces can avoid terminating on
 * branches whose destinations aren't known
 */
public class BranchLogic {
  
  public enum BranchType {
    INDIRECT_BRANCH,
    RETURN
  }

  /** A set of procedure information */
  private final SortedMap<Integer, ProcedureInformation> procedures;

  /** A set of switch like branchs sites and their destinations */
  private final SortedMap<Integer, Set<Integer>> branchSitesAndDestinations;

  /** Global branch information */
  private static BranchLogic global;

  /**
   * Constructor has 2 functions: (1) when making a local trace we don't want to
   * consider as many procedure return points as may be known for the full
   * program. (2) making sure switch like branches are all recorded globally
   */
  public BranchLogic() {
    if (global == null) {
      global = this;
      branchSitesAndDestinations = new TreeMap<Integer, Set<Integer>>();
    } 
    else {
      branchSitesAndDestinations = global.branchSitesAndDestinations;
    }
    procedures = new TreeMap<Integer, ProcedureInformation>();
  }

  /**
   * Register a call (branch and link) instruction
   * 
   * @param pc
   *  the address of the branch instruction
   * @param ret
   *  the address that will be returned to
   * @param dest
   *  the destination of the branch instruction
   */
  public void registerCall(int pc, int ret, int dest) {
    ProcedureInformation procedure = procedures.get(dest);
    
    if (procedure != null) {
      procedure.registerCall(pc, ret);
    } else {
      procedure = new ProcedureInformation(pc, ret, dest);
      procedures.put(dest, procedure);
    }
  }

  /**
   * Register a branch to the link register
   * 
   * @param pc
   *          the address of the branch instruction
   * @param lr
   *          the return address (value of the link register)
   */
  public void registerReturn(int pc, int lr) {
    ProcedureInformation procedure = getLikelyProcedure(pc);
    if (procedure != null) {
      procedure.registerReturn(pc, lr);
    }
  }

  /**
   * Given an address within a procedure, returns the (most likely) procedure
   * 
   * @param pc
   *          a location within the procedure
   * @return corressponding procedure information
   */
  private ProcedureInformation getLikelyProcedure(int pc) {
    if (procedures.size() > 0) {
      SortedMap<Integer, ProcedureInformation> priorProcedures = procedures.headMap(pc);
      if (priorProcedures.size() > 0) {
        Integer procedureEntry = priorProcedures.lastKey();
        return procedures.get(procedureEntry);
      }
    }
    return null;
  }

  /**
   * Registers a branch from the address <code>origin</code> to the address <code>target</code>.
   * The type of branch is determined by <code>type</code>, which is an ordinal from the 
   * {@link BranchType} enum.
   * @param origin
   *  The address from which the branch occurs. 
   * @param target
   *  The address to which the program is branching.
   * @param type
   *  The most likely type of the branch. This is taken from the {@link BranchType} enum. 
   */
  public void registerBranch(int origin, int target, int type) {
    
    if (DBT.VerifyAssertions) DBT._assert(type > 0 && type < BranchType.values().length);
    
    Set<Integer> dests = branchSitesAndDestinations.get(origin);
    
    if (dests != null && dests.contains(target)) {
      // This destination address is already registered
      return;
    } 
    else {
      dests = new HashSet<Integer>();
      branchSitesAndDestinations.put(origin, dests);
    }
    
    dests.add(target);
  }
  
  /**
   * Returns a list of known branch targets for the branch at address <code>pc</code>.
   * 
   * @param pc
   *  The address where the branch originates from.
   * @return
   *  A list of known target addresses for this branch. It is not critical to the functionality of the 
   *  translated binary, if this list is not complete. 
   */
  public Set<Integer> getKnownBranchTargets(int pc) {
    return branchSitesAndDestinations.get(pc);
  }
}
