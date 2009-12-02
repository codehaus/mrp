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
package org.jikesrvm;

import java.util.Enumeration;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.util.LinkedListRVM;
import org.jikesrvm.util.LinkedListIteratorRVM;

/**
 * A class for managing various callbacks from the VM.
 */
public final class Callbacks {
  /** Abstract notion of a callback */
  public static interface Callback {
    public void notify(Object... args);
  }
  /** Queue that maintains callbacks */
  public static final class CallbackQueue {
    /** Queue of call backs */
    private final LinkedListRVM<Callback> cbs = new LinkedListRVM<Callback>();
    /** Name of call back queue */
    private final String name;
    /** Trace activity */
    private final boolean trace;
    /** Constructor */
    CallbackQueue(String name, boolean trace) {
      this.name = name;
      this.trace = trace;
    }
    /** Add callback to queue */
    public void addCallback(Callback cb) {
      if (trace) {
        VM.sysWrite("Adding ", name, " call back: ");
        VM.sysWriteln(cb.getClass().toString());
      }
      cbs.add(cb);
    }
    /** Notify registered callbacks */
    public synchronized void notify(Object... args) {
      if (trace) {
        VM.sysWrite("invoking ", name, " callbacks with: ");
	for(Object o : args) {
          VM.sysWrite(o.toString());
          VM.sysWrite(" ");
        }
        VM.sysWrite("\n");
      }
      LinkedListIteratorRVM<Callback> l =
        (LinkedListIteratorRVM<Callback>)cbs.listIterator();
      while(l.hasNext()) {
        Callback e = l.next();
        if (trace) {
          VM.sysWrite("    ");
          VM.sysWrite(e.getClass().toString());
          VM.sysWrite("\n");
        }
        e.notify(args);
      }
    }
  } 

  /** Boot image callbacks */
  public static final CallbackQueue
    bootImageCallbacks = new CallbackQueue("boot image", false);

  /** VM startup callbacks */
  public static final CallbackQueue
    vmStartCallbacks = new CallbackQueue("VM exit", false);
  /** VM exit callbacks */
  public static final CallbackQueue
    vmExitCallbacks = new CallbackQueue("VM exit", false);

  /** Application startup callbacks */
  public static final CallbackQueue
    appStartCallbacks = new CallbackQueue("application start", false);
  /** Application complete callbacks */
  public static final CallbackQueue
    appCompleteCallbacks = new CallbackQueue("application complete", false);

  /** Application run startup callbacks */
  public static final CallbackQueue
    appRunStartCallbacks = new CallbackQueue("application run start", false);
  /** Application run complete callbacks */
  public static final CallbackQueue
    appRunCompleteCallbacks = new CallbackQueue("application run complete", false);

  /** Class loading callbacks */
  public static final CallbackQueue
    classLoadedCallbacks = new CallbackQueue("class loading", false);
  /** Class resolved callbacks */
  public static final CallbackQueue
    classResolvedCallbacks = new CallbackQueue("class resolved", false);
  /** Class instantiated callbacks */
  public static final CallbackQueue
    classInstantiatedCallbacks = new CallbackQueue("class instantiated", false);
  /** Class initialized callbacks */
  public static final CallbackQueue
    classInitializedCallbacks = new CallbackQueue("class initialized", false);

  /** Method override callbacks */
  public static final CallbackQueue
    methodOverrideCallbacks = new CallbackQueue("method override", false);

  /** Method compile start callbacks */
  public static final CallbackQueue
    methodCompileStartCallbacks = new CallbackQueue("method compile starts", false);
  /** Method compiled callbacks */
  public static final CallbackQueue
    methodCompiledCallbacks = new CallbackQueue("method compiled", false);

  /** Recompile all callbacks */
  public static final CallbackQueue
    recompileAllCallbacks = new CallbackQueue("recompile all", false);
}

