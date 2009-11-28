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

import java.util.HashMap;

import org.binarytranslator.vmInterface.DBT_Trace;

/** Caches traces and the PC - addresses that they start from. */
public class CodeCache {
  
  /** Stores the cached traces. The address at which the trace starts is used as the key. */
  private HashMap<Integer, DBT_Trace> codeSnippets = new HashMap<Integer, DBT_Trace>();
  
  /**
   * Adds a trace to the codecache.
   * @param pc
   *  The address at which the trace starts.
   * @param trace
   *  The cached trace.
   */
  public void add(int pc, DBT_Trace trace) {
    
    if (codeSnippets.containsKey(pc))
      throw new RuntimeException("The codecache already contains a translation for 0x" + Integer.toHexString(pc));
    
    codeSnippets.put(pc, trace);
  }
  
  /**
   * Try to retrieve a cached version of a trace starting at <code>pc</code>.
   * @param pc
   *  The address at which the sought trace starts.
   * @return
   *  A trace that starts at <code>pc</code> or null if the cache does not contain
   *  such a trace.
   */
  public DBT_Trace tryGet(int pc) {
    return codeSnippets.get(pc);
  }
}
