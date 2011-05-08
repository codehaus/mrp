/*
 *  This file is part of the Metacircular Research Platform (MRP)
 *
 *      http://mrp.codehaus.org/
 *
 *  This file is licensed to you under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the license at:
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

/*
 * Architecture specific thread code
 */

#include "sys.h"

/**
 * Transfer execution from C to Java for thread startup
 */
void bootThread (void *pc, void *tr, void *sp, void *jtoc)
{
  // Fixed register usage
  // OS:        | Non-Linux |   Linux   |
  // Word size: | 64  | 32  | 64  | 32  |
  // Thread:    | R14 | R13 | R14 | R13 |
  // JTOC:      | R16 | R15 | R16 | R14 |
  // KLUDGE_TI: | R15 | R14 | R15 | R15 |
#ifdef RVM_FOR_LINUX
  asm volatile ("mr 1,  %2\n" // stack
#ifdef RVM_FOR_32_ADDR
                "mr 13, %1\n" // thread
                "mr 14, %0\n" // jtoc
#else
                "mr 14, %1\n" // thread
                "mr 16, %0\n" // jtoc
#endif // RVM_FOR_32_ADDR
#else
#ifdef RVM_FOR_OSX // different register naming convention
  asm volatile ("mr r1,  %2\n" // stack
#ifdef RVM_FOR_32_ADDR
                "mr r13, %1\n" // thread
                "mr r15, %0\n" // jtoc
#else
                "mr r14, %1\n" // thread
                "mr r16, %0\n" // jtoc
#endif // RVM_FOR_32_ADDR
#else
  asm volatile ("mr 1,  %2\n" // stack
#ifdef RVM_FOR_32_ADDR
                "mr 13, %1\n" // thread
                "mr 15, %0\n" // jtoc
#else
                "mr 14, %1\n" // thread
                "mr 16, %0\n" // jtoc
#endif // RVM_FOR_32_ADDR
#endif // RVM_FOR_OSX
#endif // RVM_FOR_LINUX
                "mtlr %3\n"
                "blr    \n"
                : /* outs */
                : /* ins */
                  "r"(jtoc),
                  "r"(tr),
                  "r"(sp),
                  "r"(pc)
    );
}
