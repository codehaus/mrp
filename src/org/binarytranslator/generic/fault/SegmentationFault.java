/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2007
 */
package org.binarytranslator.generic.fault;

/**
 * @author Ian Rogers
 */
public class SegmentationFault extends RuntimeException {
  private final int address;
  /**
   * Constructor
   */
  public SegmentationFault(int address) {
    super("SegFault at 0x"+Integer.toHexString(address));
    this.address = address;
  }
}
