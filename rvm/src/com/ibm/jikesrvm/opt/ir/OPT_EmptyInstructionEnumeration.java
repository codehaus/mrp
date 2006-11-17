/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt.ir;


/**
 *  This class is used only for the pre-allocated empty enumeration in
 * OPT_InstructionEnumeration.  It cannot be an anonymous class in
 * OPT_InstructionEnumeration because
 * OPT_InstructionEnumeration is an interface, and when javadoc
 * sees the anonymous class, it converts it into a private member of
 * the interface.  It then complains that interfaces cannot have
 * private members.  This is truly retarded, even by Java's low
 * standards.
 *
 * @author Julian Dolby 
 */
class OPT_EmptyInstructionEnumeration implements OPT_InstructionEnumeration {

    public boolean hasMoreElements() { return false; }

    public Object nextElement() { return next(); }

    public OPT_Instruction next() {
        throw new java.util.NoSuchElementException("Empty Instruction Enumeration");
    }
}
