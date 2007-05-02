package org.binarytranslator.arch.arm.decoder;

import java.util.ArrayList;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;
import org.binarytranslator.arch.arm.os.process.ARM_Registers;
import org.binarytranslator.arch.arm.os.process.ARM_Registers.OperatingMode;
import org.binarytranslator.generic.decoder.DecoderUtils;
import org.binarytranslator.generic.decoder.Laziness;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_FieldReference;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
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
    
    registersFref = VM_FieldReference
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
  
  private VM_TypeReference OperatingMode_TypeRef = null;
  
  /**
   * Returns a register operand for values of type <code>OperatingMode</code>.
   * @return
   */
  public OPT_RegisterOperand getTempOperatingMode() {
    if (OperatingMode_TypeRef == null) {
      OperatingMode_TypeRef = VM_TypeReference.findOrCreate(ARM_Registers.OperatingMode.class);
    }
    
    return gc.temps.makeTemp(OperatingMode_TypeRef);
  }
  
  /**
   * Returns an register operand that is equivalent to the given operating mode.
   */
  public OPT_RegisterOperand getTempOperatingMode(OperatingMode mode) {
    //We are going to insert a GETFIELD, which will grab the appropriate OperatingMode static instance from
    //the OperatingMode class.
    OPT_RegisterOperand result = getTempOperatingMode();
    
    //the type reference should have been initialized by the previous call to getTempOperatingMode.
    //For the sake of defensive programming, we are doing it again here...
    if (OperatingMode_TypeRef == null) {
      OperatingMode_TypeRef = VM_TypeReference.findOrCreate(ARM_Registers.OperatingMode.class);
    }

    //grab the field reference to the corresponding field
    VM_FieldReference requestedMode_FieldReference = VM_FieldReference.findOrCreate(OperatingMode_TypeRef, VM_Atom.findOrCreateAsciiAtom(mode.name()), VM_Atom.findOrCreateAsciiAtom("Lorg/binarytranslator/arch/arm/os/process/ARM_Registers/OperatingMode;")).asFieldReference();
    
    //Finally, use a getfield to grab the (static) member field 
    appendInstructionToCurrentBlock(GetField.create(GETFIELD, result,
        null, new OPT_AddressConstantOperand(requestedMode_FieldReference
            .peekResolvedField().getOffset()), new OPT_LocationOperand(
                requestedMode_FieldReference), new OPT_TrueGuardOperand()));
    
    return result;
  }
  
  /**
   * Returns a RegisterOperand that contains a reference to the currently used ARM_Registers instance.
   * Use this reference when calling functions on ARM_Registers.
   */
  public OPT_RegisterOperand getArmRegistersReference() {
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
    
    return ps_registersOp;
  }
  
  /**
   * Writes all current flag values back to their respective registers
   */
  public void spillAllFlags(Laziness lazyState) {
    
    //first resolve the current lazy state (i.e. calculate the values of registers that are not yet resolved)
    resolveLaziness(lazyState);
    
    //TODO: Implement
    throw new RuntimeException("Not yet implemented");
  }
  
  public void fillAllFlags() {
    //TODO: Implement
    throw new RuntimeException("Not yet implemented");
  }

  @Override
  protected void fillAllRegisters() {
    
    //get an operand that contains a reference to the current ps.registers field.
    OPT_RegisterOperand ps_registersOp = getArmRegistersReference();

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
  
  public OPT_RegisterOperand getCarryFlag() {
    //TODO: Implement
    throw new RuntimeException("Not yet implemented");
  }
  
  public OPT_RegisterOperand getZeroFlag() {
    //TODO: Implement
    throw new RuntimeException("Not yet implemented");
  }
  
  public OPT_RegisterOperand getNegativeFlag() {
    //TODO: Implement
    throw new RuntimeException("Not yet implemented");
  }
  
  public OPT_RegisterOperand getOverflowFlag() {
    //TODO: Implement
    throw new RuntimeException("Not yet implemented");
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
  
  /**
   * Adds code to the current block that will rotate <code>rotatedOperand</code> by
   * <code>rotation</code> and stores the rotated integer into <code>result</code>.
   * @param result
   *  The register into which the rotated value is stored.
   * @param rotatedOperand
   *  The operand which is to be rotated.
   * @param rotation
   *  The amount of rotation that is to be applied to the operand.
   */
  public void appendRotateRight(OPT_RegisterOperand result, OPT_Operand rotatedOperand, OPT_Operand rotation) {
    VM_TypeReference IntegerType = VM_TypeReference
        .findOrCreate(Integer.class);

    VM_MethodReference rotateRightMethodRef = VM_MemberReference
        .findOrCreate(IntegerType,
            VM_Atom.findOrCreateAsciiAtom("rotateRight"),
            VM_Atom.findOrCreateAsciiAtom("(II)I")).asMethodReference();

    VM_Method rotateRightMethod = rotateRightMethodRef.resolve();

    OPT_Instruction s = Call.create(CALL, null, null, null, null, 2);
    OPT_MethodOperand methOp = OPT_MethodOperand
        .STATIC(rotateRightMethod);

    Call.setParam(s, 1, rotatedOperand);
    Call.setParam(s, 2, rotation);
    Call.setResult(s, result);
    Call.setGuard(s, new OPT_TrueGuardOperand());
    Call.setMethod(s, methOp);
    Call.setAddress(s, new OPT_AddressConstantOperand(rotateRightMethod
        .getOffset()));

    appendInstructionToCurrentBlock(s);
  }

}
