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
package org.jikesrvm.tools.bootImageWriter;

import java.util.HashMap;
import org.vmmagic.unboxed.Address;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.ObjectAddressRemapper;

final class BootImageObjectAddressRemapper implements ObjectAddressRemapper {
  static final HashMap<Integer,Integer> uniqueIntegers = new HashMap<Integer,Integer>();

  static final BootImageObjectAddressRemapper singleton = new BootImageObjectAddressRemapper();

  static BootImageObjectAddressRemapper getInstance() {
    return singleton;
  }

  /**
   * Map an object to an address.
   * @param jdkObject in "local" virtual machine
   * @return its address in a foreign virtual machine
   */
  @Override
  public <T> Address objectAsAddress(T jdkObject) {
    jdkObject = intern(jdkObject);
    return BootImageMap.findOrCreateEntry(jdkObject).objectId;
  }

  /**
   * Map an address to an object.
   * @param address value obtained from "objectAsAddress"
   * @return corresponding object
   */
  @Override
  public Object addressAsObject(Address address) {
    VM.sysWriteln("BootImageObjectAddressRemapper: called addressAsObject");
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Avoid duplicates of certain objects
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T intern(T obj) {
    if (obj instanceof String) {
      obj = (T)(((String)obj).intern());
    } else if (obj instanceof Integer) {
      Integer i = (Integer)obj;
      if (uniqueIntegers.containsKey(i)) {
        obj = (T)uniqueIntegers.get(i);
      } else {
        uniqueIntegers.put(i, i);
      }
    }
    return obj;
  }

  /**
   * Identity hash code of an object
   *
   * @param obj the object to generate the identity hash code for
   * @return the identity hash code
   */
  @Override
  public int identityHashCode(Object obj) {
    BootImageMap.Entry entry = BootImageMap.findOrCreateEntry(obj);
    int identityHashCode = System.identityHashCode(obj);
    entry.setHashed(identityHashCode);
    return identityHashCode;
  }
}
