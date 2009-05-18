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
#ifndef C_ATTRIBUTE_PORTABILITY_H_INCLUDED
#define C_ATTRIBUTE_PORTABILITY_H_INCLUDED

#if defined __GNUC__ && (__GNUC__ >= 3) && ! defined UNUSED
  #define UNUSED __attribute__((unused))
  #define NONNULL(idx) __attribute__((nonnull(idx)))
  #define NORETURN __attribute__((noreturn));
#else
  #define UNUSED
  #define NONNULL(idx)
  #define NORETURN
#endif

#endif /* #ifndef C_ATTRIBUTE_PORTABILITY_H_INCLUDED */
