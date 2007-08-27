package org.binarytranslator.arch.arm.decoder;

import org.binarytranslator.generic.os.loader.elf.ELF_File.ByteOrder;

public class ARM_Options {
  
  public enum FlagBehaviour {
    Lazy,
    Immediate
  }
  
  public enum InliningBehaviour {
    NoInlining,
    Default,
    Functions,
    DirectBranches,
    DynamicJumps,
    All, 
  }
  
  public enum MemoryModel {
    IntAddressed,
    ByteAddressed
  }
  
  /** Set to true to enable a fastpath for the decoding of data processing instructions.. */
  public final static boolean DATAPROCESSING_DECODER_FASTPATH = false;
  
  /** Shall ARM use the optimized BORROW_FROM_SUB() etc. operations? */
  public static boolean useOptimizedFlags = false; 

  /** This variable describes, if the translated program shall be optimized using profiling information. */
  public static boolean optimizeTranslationByProfiling = false;
  
  /** This variable describes, if the translated program shall be optimized using lazy evaluation.*/
  public static FlagBehaviour flagEvaluation = FlagBehaviour.Immediate;
  
  /** Describes the default behaviour for dealing with ARM function calls and indirect jumps. */
  public static InliningBehaviour inlining = InliningBehaviour.Default;
  
  /** Sets the memory model that ARM shall use. */
  public static MemoryModel memoryModel = MemoryModel.IntAddressed;
  
  /** Override the byte order read from the ELF file. */
  public static ByteOrder enforcedByteOrder = null;
  
  public static void parseOption(String key, String value) {
    if (key.equalsIgnoreCase("optimizeByProfiling")) {
      optimizeTranslationByProfiling = Boolean.parseBoolean(value);
    } else if (key.equalsIgnoreCase("flagEvaluation")) {
      flagEvaluation = ARM_Options.FlagBehaviour.valueOf(value);
    } else if (key.equalsIgnoreCase("inline")) {
      inlining = ARM_Options.InliningBehaviour.valueOf(value);
    } else if (key.equalsIgnoreCase("memory")) {
      memoryModel = ARM_Options.MemoryModel.valueOf(value);
    } else if (key.equalsIgnoreCase("optimizedFlags")) {
      useOptimizedFlags = Boolean.parseBoolean(value);
    } else if (key.equalsIgnoreCase("byteOrder")) {
      enforcedByteOrder = ByteOrder.valueOf(value);
    }
    else {
      throw new Error("Unknown ARM option: " + key);
    }
  }
}
