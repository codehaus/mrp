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

/**
 * Transfer execution from C to Java for thread startup
 */
void bootThread (void *pc, void *tr, void *sp, void UNUSED *jtoc)
{
  asm ("mtlr    %3\n"
       "blr     \n"
       : /* outs */
       : /* ins */
	 "JTOC"(jtoc),
	 "THREAD_REGISTER"(tr),
	 "FP"(sp),
	 "r"(pc)
       );
}
