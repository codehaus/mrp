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
package org.jikesrvm.jni;

import org.jikesrvm.VM;

import static org.jikesrvm.architecture.SizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.architecture.SizeConstants.BYTES_IN_LONG;

import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.classloader.UTF8Convert;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.util.StringUtilities;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/**
 * Platform independent utility functions called from JNIFunctions
 * (cannot be placed in JNIFunctions because methods
 * there are specially compiled to be called from native).
 *
 * @see JNIFunctions
 */
public final class JNIHelpers {

  /**
   * Compute the length of the given null-terminated string
   *
   * @param ptr address of string in memory
   * @return the length of the string in bytes
   */
  private static int strlen(Address ptr) {
    int length=0;
    // align address to size of machine
    while (!ptr.toWord().and(Word.fromIntZeroExtend(BYTES_IN_ADDRESS - 1)).isZero()) {
      byte bits = ptr.loadByte(Offset.fromIntZeroExtend(length));
      if (bits == 0) {
        return length;
      }
      length++;
    }
    // Ascii characters are normally in the range 1 to 128, if we subtract 1
    // from each byte and look if the top bit of the byte is set then if it is
    // the chances are the byte's value is 0. Loop over words doing this quick
    // test and then do byte by byte tests when we think we have the 0
    Word onesToSubtract;
    Word maskToTestHighBits;
    if (VM.BuildFor32Addr) {
      onesToSubtract     = Word.fromIntZeroExtend(0x01010101);
      maskToTestHighBits = Word.fromIntZeroExtend(0x80808080);
    } else {
      onesToSubtract     = Word.fromLong(0x0101010101010101L);
      maskToTestHighBits = Word.fromLong(0x8080808080808080L);
    }
    while (true) {
      Word bytes = ptr.loadWord(Offset.fromIntZeroExtend(length));
      if(!bytes.minus(onesToSubtract).and(maskToTestHighBits).isZero()) {
        if (VM.LittleEndian) {
          for(int byteOff=0; byteOff < BYTES_IN_ADDRESS; byteOff++) {
            if(bytes.and(Word.fromIntZeroExtend(0xFF)).isZero()) {
              return length + byteOff;
            }
            bytes = bytes.rshl(8);
          }
        } else {
          for(int byteOff=BYTES_IN_ADDRESS-1; byteOff >= 0; byteOff--) {
            if(bytes.rshl(byteOff*8).and(Word.fromIntZeroExtend(0xFF)).isZero()) {
              return length + (BYTES_IN_ADDRESS - 1 - byteOff);
            }
          }
        }
      }
      length += BYTES_IN_ADDRESS;
    }
  }
  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java byte[] with a copy of the string.
   *
   * @param stringAddress an address in C space for a string
   * @return a new Java byte[]
   */
  private static byte[] createByteArrayFromC(Address stringAddress) {

    int length = strlen(stringAddress);
    byte[] contents = new byte[length];
    Memory.memcopy(Magic.objectAsAddress(contents), stringAddress, length);

    return contents;
  }

  /**
   * Create a string from the given charset decoder and bytebuffer
   */
  private static String createString(CharsetDecoder csd, ByteBuffer bbuf) throws CharacterCodingException {
    char[] v;
    int o;
    int c;
    CharBuffer cbuf = csd.decode(bbuf);
    if(cbuf.hasArray()) {
      v = cbuf.array();
      o = cbuf.position();
      c = cbuf.remaining();
    } else {
      // Doubt this will happen. But just in case.
      v = new char[cbuf.remaining()];
      cbuf.get(v);
      o = 0;
      c = v.length;
    }
    return java.lang.JikesRVMSupport.newStringWithoutCopy(v, o, c);
  }
  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java String with a copy of the string.
   *
   * @param stringAddress an address in C space for a string
   * @return a new Java String
   */
  static String createStringFromC(Address stringAddress) {
    if (VM.fullyBooted) {
      try {
        String encoding = System.getProperty("file.encoding");
        CharsetDecoder csd = Charset.forName(encoding).newDecoder();
        csd.onMalformedInput(CodingErrorAction.REPLACE);
        csd.onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer bbuf =
          java.nio.JikesRVMSupport.newDirectByteBuffer(stringAddress,
                                                       strlen(stringAddress));
        return createString(csd, bbuf);
      } catch(Exception ex){
        // Any problems fall through to default encoding
      }
    }
    // Can't do real Char encoding until VM is fully booted.
    // All Strings encountered during booting must be ascii
    byte[] tmp = createByteArrayFromC(stringAddress);
    return StringUtilities.asciiBytesToString(tmp);
  }
  /**
   * Given an address in C that points to a null-terminated string,
   * create a new UTF encoded Java String with a copy of the string.
   *
   * @param stringAddress an address in C space for a string
   * @return a new Java String
   */
  static String createUTFStringFromC(Address stringAddress) {
    final boolean USE_LIBRARY_CODEC = false;
    byte[] tmp;
    ByteBuffer bbuf;
    if (VM.fullyBooted) {
      try {
        bbuf = java.nio.JikesRVMSupport.newDirectByteBuffer(stringAddress,
                                                            strlen(stringAddress));
        if (USE_LIBRARY_CODEC) {
          CharsetDecoder csd = Charset.forName("UTF8").newDecoder();
          return createString(csd, bbuf);
        } else {
          return UTF8Convert.fromUTF8(bbuf);
        }
      } catch(Exception ex){
        // Any problems fall through to default encoding
      }
    }
    // Can't do real Char encoding until VM is fully booted.
    // All Strings encountered during booting must be ascii
    tmp = createByteArrayFromC(stringAddress);
    return StringUtilities.asciiBytesToString(tmp);
  }


  /**
   * Convert a String into a a malloced region
   */
  static void createUTFForCFromString(String str, Address copyBuffer, int len) {
    ByteBuffer bbuf =
      java.nio.JikesRVMSupport.newDirectByteBuffer(copyBuffer, len);

    final boolean USE_LIBRARY_CODEC = false;
    if (USE_LIBRARY_CODEC) {
      char[] strChars = java.lang.JikesRVMSupport.getBackingCharArray(str);
      int strOffset = java.lang.JikesRVMSupport.getStringOffset(str);
      int strLen = java.lang.JikesRVMSupport.getStringLength(str);
      CharBuffer cbuf = CharBuffer.wrap(strChars, strOffset, strLen);
      CharsetEncoder cse = Charset.forName("UTF8").newEncoder();
      cse.encode(cbuf, bbuf, true);
    } else {
      UTF8Convert.toUTF8(str, bbuf);
    }
    // store terminating zero
    copyBuffer.store((byte)0, Offset.fromIntZeroExtend(len-1));
  }

  /**
   * A JNI helper function, to set the value pointed to by a C pointer
   * of type (jboolean *).
   * @param boolPtr Native pointer to a jboolean variable to be set.   May be
   *            the NULL pointer, in which case we do nothing.
   * @param val Value to set it to (usually TRUE)
   *
   */
  static void setBoolStar(Address boolPtr, boolean val) {
    if (boolPtr.isZero()) {
      return;
    }
    if (val) {
      boolPtr.store((byte)1);
    } else {
      boolPtr.store((byte)0);
    }
  }

  /**
   * Repackage the arguments passed as an array of jvalue into an array of Object,
   * used by the JNI functions CallStatic<type>MethodA
   * @param targetMethod the target {@link MethodReference}
   * @param argAddress   an address into the C space for the array of jvalue unions
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParametersFromJValuePtr(MethodReference targetMethod, Address argAddress) {
    TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the JNIEnvironment for this thread in case we need to dereference any object arg
    JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

    Address addr = argAddress;
    for (int i = 0; i < argCount; i++, addr = addr.plus(BYTES_IN_LONG)) {
      // convert and wrap the argument according to the expected type
      if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        argObjectArray[i] = env.getJNIRef(addr.loadInt());
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = addr.loadInt();
      } else if (argTypes[i].isLongType()) {
        argObjectArray[i] = addr.loadLong();
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte
        argObjectArray[i] = addr.loadByte() != 0;
      } else if (argTypes[i].isByteType()) {
        // the target byte is stored in the high byte
        argObjectArray[i] = addr.loadByte();
      } else if (argTypes[i].isCharType()) {
        // char is stored in the high 2 bytes
        argObjectArray[i] = addr.loadChar();
      } else if (argTypes[i].isShortType()) {
        // short is stored in the high 2 bytes
        argObjectArray[i] = addr.loadShort();
      } else if (argTypes[i].isFloatType()) {
        argObjectArray[i] = addr.loadFloat();
      } else {
        if (VM.VerifyAssertions) VM._assert(argTypes[i].isDoubleType());
        argObjectArray[i] = addr.loadDouble();
      }
    }
    return argObjectArray;
  }

  /**
   * Repackage the arguments passed as a va_list into an array of Object,
   * used by the JNI functions CallStatic<type>MethodV
   * @param targetMethod the target {@link MethodReference}
   * @param argAddress   an address into the C space for a va_list
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParametersFromVarArgs(MethodReference targetMethod, Address argAddress) {
    TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];
    Address vaListCopy = sysCall.sysVaCopy(argAddress);
    // get the JNIEnvironment for this thread in case we need to dereference any object arg
    JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

    for (int i = 0; i < argCount; i++) {
      // convert and wrap the argument according to the expected type
      if (argTypes[i].isReferenceType()) {
        // for object, the arg is a JREF index, dereference to get the real object
        argObjectArray[i] = env.getJNIRef(sysCall.sysVaArgJobject(vaListCopy));
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = sysCall.sysVaArgJint(vaListCopy);
      } else if (argTypes[i].isLongType()) {
        argObjectArray[i] = sysCall.sysVaArgJlong(vaListCopy);
      } else if (argTypes[i].isBooleanType()) {
        argObjectArray[i] = sysCall.sysVaArgJboolean(vaListCopy);
      } else if (argTypes[i].isByteType()) {
        argObjectArray[i] = sysCall.sysVaArgJbyte(vaListCopy);
      } else if (argTypes[i].isCharType()) {
        argObjectArray[i] = sysCall.sysVaArgJchar(vaListCopy);
      } else if (argTypes[i].isShortType()) {
        argObjectArray[i] = sysCall.sysVaArgJshort(vaListCopy);
      } else if (argTypes[i].isFloatType()) {
        argObjectArray[i] = sysCall.sysVaArgJfloat(vaListCopy);
      } else {
        if (VM.VerifyAssertions) VM._assert(argTypes[i].isDoubleType());
        argObjectArray[i] = sysCall.sysVaArgJdouble(vaListCopy);
      }
    }
    sysCall.sysVaEnd(vaListCopy);
    return argObjectArray;
  }

  static Object invokeInitializer(Class<?> cls, MethodReference mr, Object[] args) throws InstantiationException, IllegalAccessException, InvocationTargetException {
    TypeReference tr = java.lang.JikesRVMSupport.getTypeForClass(cls).getTypeRef();
    RVMMethod mth;
    // Check method isn't for super class
    if (mr.getType() != tr) {
      mth = MemberReference.findOrCreate(tr, mr.getName(), mr.getDescriptor()).asMethodReference().resolve();
    } else {
      mth = mr.resolve();
    }
    // Create constructor
    Constructor<?> constMethod = java.lang.reflect.JikesRVMSupport.createConstructor(mth);
    if (!mth.isPublic()) {
      constMethod.setAccessible(true);
    }
    // Make instance
    return constMethod.newInstance(args);
  }

  /**
   * Dispatch method call, arguments in va_list
   * @param obj this pointer for method to be invoked, or null if method is static
   * @param mr reference to method to be invoked
   * @param args argument array
   * @param expectedReturnType a type reference for the expected return type
   * @param nonVirtual should invocation be of the given method or should we use virtual dispatch on the object?
   */
  static Object callMethodVarArgs(JNIEnvironment env, int objJREF, int methodID, Address argAddress, TypeReference expectedReturnType, boolean nonVirtual) throws InvocationTargetException {
    RuntimeEntrypoints.checkJNICountDownToGC();
    try {
      Object obj = env.getJNIRef(objJREF);
      MethodReference mr = MemberReference.getMethodRef(methodID);
      Object[] args = packageParametersFromVarArgs(mr, argAddress);
      return  callMethod(obj, mr, args, expectedReturnType, nonVirtual);
    } catch (Throwable unexpected) {
      if (JNIFunctions.traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * Dispatch method call, arguments in jvalue*
   * @param obj this pointer for method to be invoked, or null if method is static
   * @param mr reference to method to be invoked
   * @param args argument array
   * @param expectedReturnType a type reference for the expected return type
   * @param nonVirtual should invocation be of the given method or should we use virtual dispatch on the object?
   */
  static Object callMethodJValuePtr(JNIEnvironment env, int objJREF, int methodID, Address argAddress, TypeReference expectedReturnType, boolean nonVirtual) throws InvocationTargetException {
    RuntimeEntrypoints.checkJNICountDownToGC();
    try {
      Object obj = env.getJNIRef(objJREF);
      MethodReference mr = MemberReference.getMethodRef(methodID);
      Object[] args = packageParametersFromJValuePtr(mr, argAddress);
      return callMethod(obj, mr, args, expectedReturnType, nonVirtual);
    } catch (Throwable unexpected) {
      if (JNIFunctions.traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * Dispatch method call
   * @param obj this pointer for method to be invoked, or null if method is static
   * @param mr reference to method to be invoked
   * @param args argument array
   * @param expectedReturnType a type reference for the expected return type
   * @param nonVirtual should invocation be of the given method or should we use virtual dispatch on the object?
   */
  private static Object callMethod(Object obj, MethodReference mr, Object[] args, TypeReference expectedReturnType, boolean nonVirtual) throws InvocationTargetException {
    RVMMethod targetMethod = mr.resolve();
    TypeReference returnType = targetMethod.getReturnType();

    if (JNIFunctions.traceJNI) {
      VM.sysWriteln("JNI CallXXXMethod: "+ mr);
    }

    if (expectedReturnType == null) {   // for reference return type
      if (!returnType.isReferenceType()) {
        throw new IllegalArgumentException("Wrong return type for method (" + targetMethod + "): expected reference type instead of " + returnType);
      }
    } else { // for primitive return type
      if (!returnType.definitelySame(expectedReturnType)) {
        throw new IllegalArgumentException("Wrong return type for method (" + targetMethod + "): expected " + expectedReturnType + " instead of " + returnType);
      }
    }
    // invoke the method
    return Reflection.invoke(targetMethod, null, obj, args, nonVirtual);
  }
}
