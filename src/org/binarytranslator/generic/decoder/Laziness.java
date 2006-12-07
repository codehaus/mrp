/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.decoder;

/**
 * Class to capture common features of laziness. Laziness is a tool
 * for not creating all of the side effects of a decoded
 * operation. Enough state is left around to allow for the creation of
 * the desired effect, if it is ever required. The lazy state captures
 * what is valid in a point in a decoded code sequence. Consequently
 * more than one translation can exist for the same instruction if the
 * lazy state differs. This causes extra translation and bloat. So
 * don't use laziness if simple compiler optimisations like dead-code
 * elimination will suffice.
 */
public abstract class Laziness {
  /**
   * Constructor
   */
  protected Laziness() {
  }

  /**
   * Do two lazy states encode the same meaning?
   */
  public abstract boolean equivalent(Laziness other);
  
  /**
   * Given the current program position make a key object that will
   * allow 
   */
  public abstract Object makeKey(int pc);

  /**
   * Create a copy of this lazy state - usually to record where one
   * translation was upto whilst we mutate another lazy state.
   */
  public abstract Object clone();
}
