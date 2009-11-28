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
package org.binarytranslator.arch.x86.decoder;

public class X86_Constants {
  public static final int STACK_TOP = 0xbfffffff;

  public static final int ES = 0, CS = 1, SS = 2, DS = 3, FS = 4, GS = 5;

  public static final int AL = 0, CL = 1, DL = 2, BL = 3, AH = 4, CH = 5,
      DH = 6, BH = 7;

  public static final int AX = 0, CX = 1, DX = 2, BX = 3, SP = 4, BP = 5,
      SI = 6, DI = 7;

  public static final int EAX = 0, ECX = 1, EDX = 2, EBX = 3, ESP = 4, EBP = 5,
      ESI = 6, EDI = 7;

  public static final int MM0 = 0, MM1 = 1, MM2 = 2, MM3 = 3, MM4 = 4, MM5 = 5,
      MM6 = 6, MM7 = 7;
}
