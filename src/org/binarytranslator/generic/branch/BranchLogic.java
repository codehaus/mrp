/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.branch;
import org.binarytranslator.DBT_Options;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.SortedSet;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Object capturing branches and jumps so that traces can avoid
 * terminating on branches whose destinations aren't known
 */
public class BranchLogic {
  /** Code to indicate branch was an indirect branch */
  public static final int INDIRECT_BRANCH=0;

  /** Code to indicate branch was a return */
  public static final int RETURN=1;

  /**
   * A set of procedure information
   */
  private final SortedMap procedures;

  /**
   * A set of switch like branchs sites and their destinations
   */
  private final SortedMap branchSitesAndDestinations;

  /**
   * Global branch information
   */
  private static BranchLogic global;

  /**
   * Constructor has 2 functions:
   * (1) when making a local trace we don't want to consider as many
   * procedure return points as may be known for the full program.
   * (2) making sure switch like branches are all recorded globally
   */
  public BranchLogic() {
    if (global == null) {
      global = this;
      branchSitesAndDestinations = new TreeMap();
    }
    else {
      branchSitesAndDestinations = global.branchSitesAndDestinations;
    }
    procedures = new TreeMap();
  }

  /**
   * Register a call (branch and link) instruction
   * @param pc the address of the branch instruction
   * @param ret the address that will be returned to
   * @param dest the destination of the branch instruction
   */
  public void registerCall(int pc, int ret, int dest) {
    ProcedureInformation procedure = (ProcedureInformation)procedures.get(Integer.valueOf(dest));
    if (procedure != null) {
      procedure.registerCall(pc, ret);
    }
    else {
      procedure = new ProcedureInformation(pc, ret, dest);
      procedures.put(Integer.valueOf(dest), procedure);
    }
  }

  /**
   * Register a branch to the link register
   * @param pc the address of the branch instruction
   * @param lr the return address (value of the link register)
   */
  public void registerReturn(int pc, int lr) {
    ProcedureInformation procedure = getLikelyProcedure(pc);
    if (procedure != null) {
      procedure.registerReturn(pc, lr);
    }
  }

  /**
   * Given an address within a procedure, returns the (most likely) procedure 
   * @param pc a location within the procedure
   * @return corressponding procedure information
   */
  private ProcedureInformation getLikelyProcedure(int pc) {
    if (procedures.size() > 0) {
      SortedMap priorProcedures = procedures.headMap(Integer.valueOf(pc));
      if (priorProcedures.size() > 0) {
        Integer procedureEntry = (Integer)priorProcedures.lastKey();
        return (ProcedureInformation)procedures.get(procedureEntry);
      }
    }
    return null;
  }

  /**
   * Register a branch to the count register
   * @param pc the address of the branch instruction
   * @param lr the value of the link register
   */
    public void registerBranch(int pc, int ctr, int type) {
    Set dests = (Set)branchSitesAndDestinations.get(Integer.valueOf(pc));
    if (dests != null) {
      if (dests.contains(Integer.valueOf(ctr))) {
        // This ctr address is already registered
        return;
      }
    }
    else {
      dests = new HashSet();
      branchSitesAndDestinations.put(Integer.valueOf(pc), dests);
    }
    dests.add(Integer.valueOf(ctr));
  }

}
