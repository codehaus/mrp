package org.binarytranslator.generic.execution;

import org.binarytranslator.generic.fault.BadInstructionException;
import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.vmInterface.DynamicCodeRunner;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;

/**
 * Runtime loop, goes through the binary and looks in a Hashtable 
 * to see if we have already translated/compiled this piece of code, if not it
 * is compiled. The compiled code is then run.
 */
public class DynamicTranslationController extends ExecutionController {

  public DynamicTranslationController(ProcessSpace ps) {
    super(ps);
  }

  @Override
  public void run() {
    // The current block of compiled code.
    VM_CodeArray code;


    try {
      // interpretFrom(); // Interpreter - experimental
      while (ps.finished == false) {
        // Get the compiled code
        code = ps.getCodeForPC(ps.getCurrentInstructionAddress());
        // Run the compiled code.
        ps.setCurrentInstructionAddress(DynamicCodeRunner.invokeCode(code, ps));
      }
    } catch (BadInstructionException e) {
      System.out.println(e.toString());
    }
  }

}
