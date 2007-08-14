package org.binarytranslator.arch.arm.decoder;

public class ARM_Options {
  
  public enum FlagBehaviour {
    Lazy,
    Immediate
  }
  
  public enum InliningBehaviour {
    Default,
    Functions,
    FunctionCalls,
    FunctionReturns,
    DynamicJumps,
    All, 
  }
  
  /** Set to true to enable a fastpath for the decoding of data processing instructions.. */
  public final static boolean DATAPROCESSING_DECODER_FASTPATH = false;

  /** This variable describes, if the translated program shall be optimized using profiling information. */
  public static boolean optimizeTranslationByProfiling = false;
  
  /** This variable describes, if the translated program shall be optimized using lazy evaluation.*/
  public static FlagBehaviour flagEvaluation = FlagBehaviour.Lazy;
  
  /** Describes the default behaviour for dealing with ARM function calls and indirect jumps. */
  public static InliningBehaviour inlining = InliningBehaviour.Default;
}
