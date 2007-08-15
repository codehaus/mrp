package org.binarytranslator.arch.arm.decoder;

public class ARM_Options {
  
  public enum FlagBehaviour {
    Lazy,
    Immediate
  }
  
  public enum InliningBehaviour {
    NoInlining,
    Default,
    Functions,
    FunctionCalls,
    FunctionReturns,
    DynamicJumps,
    All, 
  }
  
  public enum MemoryModel {
    IntAddressed,
    ByteAddressed
  }
  
  /** Set to true to enable a fastpath for the decoding of data processing instructions.. */
  public final static boolean DATAPROCESSING_DECODER_FASTPATH = false;

  /** This variable describes, if the translated program shall be optimized using profiling information. */
  public static boolean optimizeTranslationByProfiling = false;
  
  /** This variable describes, if the translated program shall be optimized using lazy evaluation.*/
  public static FlagBehaviour flagEvaluation = FlagBehaviour.Lazy;
  
  /** Describes the default behaviour for dealing with ARM function calls and indirect jumps. */
  public static InliningBehaviour inlining = InliningBehaviour.Default;
  
  /** Sets the memory model that ARM shall use. */
  public static MemoryModel memoryModel = MemoryModel.IntAddressed;
  
  
  public static void parseOption(String key, String value) {
    if (key.equalsIgnoreCase("optimizeByProfiling")) {
      optimizeTranslationByProfiling = Boolean.parseBoolean(value);
    } else if (key.equalsIgnoreCase("flagEvaluation")) {
      flagEvaluation = ARM_Options.FlagBehaviour.valueOf(value);
    } else if (key.equalsIgnoreCase("inline")) {
      inlining = ARM_Options.InliningBehaviour.valueOf(value);
    } else if (key.equalsIgnoreCase("memory")) {
      memoryModel = ARM_Options.MemoryModel.valueOf(value);
    }
    else {
      throw new Error("Unknown ARM option: " + key);
    }
  }
}
