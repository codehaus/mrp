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

#include "sys.h"

void dumpThread(void *thread) {
  *((Address*)bootRecord->debugArgs)   = (Address)thread;
  *((int*)    bootRecord->debugMethod) = DEBUG_DUMP_THREAD;
  void (*debugFn)();
  debugFn = (void(*)())bootRecord->debugEntry;
  debugFn();
}

void dumpStack(void *thread) {
  *((Address*)bootRecord->debugArgs)   = (Address)thread;
  *((int*)    bootRecord->debugMethod) = DEBUG_DUMP_STACK;
  void (*debugFn)();
  debugFn = (void(*)())bootRecord->debugEntry;
  debugFn();
}
