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
package org.binarytranslator.arch.arm.os.abi.linux;

import org.binarytranslator.generic.os.abi.linux.files.FixedContentFile;
import org.binarytranslator.generic.os.abi.linux.files.ReadableFile;
import org.binarytranslator.generic.os.abi.linux.filesystem.FileProvider;
import org.binarytranslator.generic.os.abi.linux.filesystem.ProcFileSystem;

public class ARM_ProcFileSystem extends ProcFileSystem {

  public ARM_ProcFileSystem(FileProvider nextProvider) {
    super(nextProvider);
  }

  @Override
  protected ReadableFile openCpuInfo() {
    
    String output = "";
    output += "Processor       : XScale-IOP80321 rev 2 (v5l)\n";
    output += "BogoMIPS        : 599.65\n";
    output += "Features        : swp half thumb fastmult edsp\n";
    output += "\n";
    output += "Hardware        : Iyonix\n";
    output += "Revision        : 0000\n";
    output += "Serial          : 0000000000000000\n";

    return new FixedContentFile(output);
  }

}
