/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.branch;
import java.util.Comparator;

/**
 * Comparator for procedure information
 */
class ProcedureInformationComparator implements Comparator {
  /**
   * Compare two procedure information objects
   */
  public int compare(Object o1, Object o2) {
    return ((ProcedureInformation)o1).entry - ((ProcedureInformation)o2).entry;
  }
}
