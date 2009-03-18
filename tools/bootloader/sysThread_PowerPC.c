/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

/*
 * Architecture specific thread code
 */

#define NEED_ASSEMBLER_DECLARATIONS
#include "sys.h"

/**
 * Transfer execution from C to Java for thread startup
 */
void bootThread (void *pc, void *tr, void *sp, void *jtoc)
{
  asm volatile ("mr r2, %0\n"
                "mr r13, %1\n"
                "mr r1, %2\n"
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
