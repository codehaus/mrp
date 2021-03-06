/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
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
package org.vmmagic.pragma;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import org.vmmagic.Pragma;

/**
 * This pragma is used to indicate a field will be final in the running VM. We
 * can't indicate all fields are final as some are used in the boot strap
 * process.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Pragma
public @interface RuntimeFinal {
  /** The value of the field. Currently only boolean values can be RuntimeFinal. */
  boolean value();
}
