/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 *$Id$
 */
/* Test field access from native code 
 * Implement native methods from FieldAccess.java 
 * 
 * @author Ton Ngo, Steve Smith 
 * @date   1/4/01
 */

#include <stdio.h>
#include "MonitorTest.h"
#include <jni.h>

int verbose=1;

/*
 * Class:     FieldAccess
 * Method:    setVerboseOff
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_MonitorTest_setVerboseOff
  (JNIEnv *env, jclass cls){
  verbose=0;
}


/*
 * Class:     MonitorTest
 * Method:    accessMonitorFromNative
 * Signature: (Ljava/lang/Object;)I
 *
 * Return 0 on success, non-zero on failure.
 *
 */
JNIEXPORT jint JNICALL Java_MonitorTest_accessMonitorFromNative
  (JNIEnv *env, jclass cls, jobject lockObj) {
  
  jmethodID methodID;
  jint rc;
  int incrementCount = 50;
  int withLock = 1;  /* to try incrementing count without lock */
  int i;


  /* get the increment method in Java */
  methodID = (*env) -> GetStaticMethodID(env, cls, "accessCountUnderNativeLock", "(I)V");
  if (methodID == NULL) {
    if (verbose)
      printf("> GetStaticMethodID: fail to get method ID for static method accessCountUnderNativeLock\n");
    return -1;
  }


  for (i=0; i<incrementCount; i++) {
    /* Obtain the Java lock */
    /* Call the Java method to increment the count */
    /* Then unlock */
    if (withLock) {
      rc = (*env) -> MonitorEnter(env, lockObj);        
      if (rc!=0) break;
    }
    (*env) -> CallStaticVoidMethod(env, cls, methodID, 20);        
    if (withLock) {      
      rc = (*env) -> MonitorExit(env, lockObj);
      if (rc!=0) break;
    }

    /* try again with nested MonitorEnter */
    if (withLock) {
      rc = (*env) -> MonitorEnter(env, lockObj);        
      if (rc!=0) break;
      rc = (*env) -> MonitorEnter(env, lockObj);        
      if (rc!=0) break;
    }
    (*env) -> CallStaticVoidMethod(env, cls, methodID, -20);        
    if (withLock) {
      rc = (*env) -> MonitorExit(env, lockObj);
      if (rc!=0) break;    
      rc = (*env) -> MonitorExit(env, lockObj);
      if (rc!=0) break;    
    }
  }

  if (rc==0) {
    if (verbose)
      printf("Monitor operation succeeds.\n");
    return 0;
  } else {
    if (verbose)
      printf("Monitor operation fails.\n");
    return -1;
  }

}
