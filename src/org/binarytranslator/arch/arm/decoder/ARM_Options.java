package org.binarytranslator.arch.arm.decoder;

public class ARM_Options {
  
  public enum FlagBehaviour {
    LazyEvaluation,
    ImmediateEvaluation
  }
  
  /** Set to true to enable a fastpath for the decoding of data processing instructions.. */
  public final static boolean DATAPROCESSING_DECODER_FASTPATH = false;

  /** This variable describes, if the translated program shall be optimized using profiling information. */
  public static boolean optimizeTranslationByProfiling = false;
  
  /** This variable describes, if the translated program shall be optimized using lazy evaluation.*/
  public static FlagBehaviour flagBehaviour = FlagBehaviour.LazyEvaluation;
}
