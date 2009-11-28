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
package org.binarytranslator.generic.decoder;

/**
 * Class to capture common features of laziness. Laziness is a tool for not
 * creating all of the side effects of a decoded operation. Enough state is left
 * around to allow for the creation of the desired effect, if it is ever
 * required. The lazy state captures what is valid in a point in a decoded code
 * sequence. Consequently more than one translation can exist for the same
 * instruction if the lazy state differs. This causes extra translation and
 * bloat. So don't use laziness if simple compiler optimisations like dead-code
 * elimination will suffice.
 */
public abstract class Laziness {
  /**
   * A Key class used when making a key from the laziness and PC value combined
   */
  public static class Key {
  }

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
   * Given the current program position make a key object that will allow
   */
  public abstract Key makeKey(int pc);

  /**
   * Create a copy of this lazy state - usually to record where one translation
   * was upto whilst we mutate another lazy state.
   */
  public abstract Object clone();
}
