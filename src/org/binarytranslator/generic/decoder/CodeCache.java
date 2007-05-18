package org.binarytranslator.generic.decoder;

import java.util.WeakHashMap;

import org.binarytranslator.vmInterface.DBT_Trace;

/** Caches traces and the PC - addresses that they start from. */
public class CodeCache {
  
  /** Stores the cached traces. The address at which the trace starts is used as the key. */
  private WeakHashMap<Integer, DBT_Trace> codeSnippets = new WeakHashMap<Integer, DBT_Trace>();
  
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
