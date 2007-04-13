package org.binarytranslator.arch.arm.decoder;

import java.util.ArrayList;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.generic.decoder.DecoderUtils;
import org.binarytranslator.generic.decoder.Laziness;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_FieldReference;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.*;

public class ARM2IR extends DecoderUtils implements OPT_HIRGenerator {

  /** Mapping of ARM registers to HIR registers */
  protected OPT_Register regMap[] = new OPT_Register[16];
  
  /** Set to true for each register that is in use during the current trace */
  protected boolean regUsed[] = new boolean[16];

  /** Type reference to the ARM process space */
  private static final VM_TypeReference psTref;

  /** A field reference to the ARM registers class within the PS */
  private static final VM_FieldReference registersFref;
  
  /** A type reference to the ARM registers class */
  private static final VM_TypeReference registersTref;
  
  /** A field reference to the ARM registers array within the ARM_Registers class */
  private static final VM_FieldReference registers_regs_Fref;
  
  /** A type reference to the ARM registers array within the ARM_Registers class */
  private static final VM_TypeReference registers_regs_Tref;  
  
  /** A register holding a reference to ps.registers */
  private OPT_Register ps_registers;
  
  /** A register holding a reference to ps.registers.regs */
  private OPT_Register ps_registers_regs;
  

  static {
    psTref = VM_TypeReference.findOrCreate(ARM_ProcessSpace.class);

    registersFref = VM_MemberReference
        .findOrCreate(
            psTref,
            VM_Atom.findOrCreateAsciiAtom("registers"),
            VM_Atom
                .findOrCreateAsciiAtom("Lorg/binarytranslator/arch/arm/os/process/ARM_Registers;"))
        .asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registersFref != null);
    
    registersTref = registersFref.getFieldContentsType();
    
    if (DBT.VerifyAssertions) DBT._assert(registersTref != null);
    
    registers_regs_Fref = VM_MemberReference.findOrCreate(
        registersTref, VM_Atom.findOrCreateAsciiAtom("regs"),
        VM_Atom.findOrCreateAsciiAtom("[I")).asFieldReference();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_regs_Fref != null);
    
    registers_regs_Tref = registers_regs_Fref.getFieldContentsType();
    
    if (DBT.VerifyAssertions) DBT._assert(registers_regs_Tref != null);
  }
  

  public ARM2IR(OPT_GenerationContext context) {
    super(context);
  }

  @Override
  protected Laziness createInitialLaziness() {
    return new ARM_Laziness();
  }

  @Override
  protected void fillAllRegisters() {
    
    OPT_RegisterOperand ps_registersOp;
    
    if (ps_registers != null) {
      ps_registersOp = gc.temps.makeTemp(registersTref);
      ps_registers = ps_registersOp.register;
      appendInstructionToCurrentBlock(GetField.create(GETFIELD, ps_registersOp,
          gc.makeLocal(1, psTref), new OPT_AddressConstantOperand(registersFref
              .peekResolvedField().getOffset()), new OPT_LocationOperand(
              registersFref), new OPT_TrueGuardOperand()));
    }
    else {
      ps_registersOp = new OPT_RegisterOperand(ps_registers, registersTref);
    }

    // Get the array of general purpose registers
    OPT_RegisterOperand ps_registers_regsOp;
    if (ps_registers_regs == null) {

      ps_registers_regsOp = gc.temps.makeTemp(registers_regs_Tref);
      appendInstructionToCurrentBlock(GetField.create(GETFIELD,
          ps_registers_regsOp, ps_registersOp.copyRO(),
          new OPT_AddressConstantOperand(registers_regs_Fref.peekResolvedField()
              .getOffset()), new OPT_LocationOperand(registers_regs_Fref),
          new OPT_TrueGuardOperand()));
      ps_registers_regs = ps_registers_regsOp.register;
    } else {
      ps_registers_regsOp = new OPT_RegisterOperand(ps_registers_regs, registers_regs_Tref);
    }

    // Fill general purpose registers
    for (int i = 0; i < regMap.length; i++) {
      OPT_RegisterOperand regOp;
      if (regMap[i] == null) {
        regOp = makeTemp(VM_TypeReference.Int);
        regMap[i] = regOp.register;
      } else {
        regOp = new OPT_RegisterOperand(regMap[i], VM_TypeReference.Int);
      }
      appendInstructionToCurrentBlock(ALoad.create(INT_ALOAD, regOp,
          ps_registers_regsOp.copyRO(), new OPT_IntConstantOperand(i),
          new OPT_LocationOperand(VM_TypeReference.Int),
          new OPT_TrueGuardOperand()));
    }
  }
  
  public OPT_RegisterOperand getRegister(int r) {
    regUsed[r] = true;
    return new OPT_RegisterOperand(regMap[r], VM_TypeReference.Int);
  }

  @Override
  protected OPT_Register[] getUnusedRegisters() {
    
    ArrayList<OPT_Register> unusedRegisters = new ArrayList<OPT_Register>();
    
    for (int i = 0; i < regUsed.length; i++)
      if (!regUsed[i]) {
        unusedRegisters.add(regMap[i]);
      }
    
    return unusedRegisters.toArray(new OPT_Register[unusedRegisters.size()]);
  }

  @Override
  protected void report(String str) {
    System.out.println("ARM2IR: " + str);
  }

  @Override
  public void resolveLaziness(Laziness laziness) {
    //NO-OP, as we're not using laziness at the moment
  }

  @Override
  protected void spillAllRegisters() {
    
    // spill general purpose registers
    OPT_RegisterOperand ps_registers_regsOp = new OPT_RegisterOperand(
        ps_registers_regs, registers_regs_Tref);
    for (int i = 0; i < regMap.length; i++) {
      // We can save spills if the trace has no syscalls and the register was
      // never used
      if ((DBT_Options.singleInstrTranslation == false)
          || (regUsed[i] == true)) {
        appendInstructionToCurrentBlock(AStore.create(INT_ASTORE,
            new OPT_RegisterOperand(regMap[i], VM_TypeReference.Int),
            ps_registers_regsOp.copyRO(), new OPT_IntConstantOperand(i),
            new OPT_LocationOperand(VM_TypeReference.Int),
            new OPT_TrueGuardOperand()));
      }
    }
  }

  @Override
  protected int translateInstruction(Laziness lazy, int pc) {
    return 0xEBADC0DE;
    //ARM_InstructionDecoder.translateInstruction(this,
    //    (ARM_ProcessSpace) ps, (ARM_Laziness) lazy, pc);
  }
}
