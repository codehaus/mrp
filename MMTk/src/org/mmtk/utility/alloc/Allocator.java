/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.utility.alloc;

import org.mmtk.plan.*;
import org.mmtk.utility.*;
import org.mmtk.utility.statistics.*;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This abstract base class provides the basis for processor-local
 * allocation.  The key functionality provided is the retry mechanism
 * that is necessary to correctly handle the fact that a "slow-path"
 * allocation can cause a GC which violate the uninterruptability assumption.
 * This results in the thread being moved to a different processor so that
 * the allocator object it is using is not actually the one for the processor
 * it is running on.
 *
 * This class also includes functionality to assist allocators with ensuring that
 * requests are aligned according to requests.
 *
 * Failing to handle this properly will lead to very hard to trace bugs
 * where the allocation that caused a GC or allocations immediately following
 * GC are run incorrectly.
 *
 * @author Perry Cheng
 * @modified Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */

public abstract class Allocator implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$";

  /**
   * Maximum number of retries on consecutive allocation failure.
   *
   */
  private static final int MAX_RETRY = 5;

  /**
   * Constructor
   *
   */
  Allocator () {
  }

  /**
   * Aligns up an allocation request. The allocation request accepts a region, that must be 
   * at least particle aligned, an alignment request (some power of two number of particles)
   * and an offset (a number of particles). There is also a knownAlignment parameter to
   * allow a more optimised check when the particular allocator in use always aligns at a 
   * coarser grain than individual particles, such as some free lists. 
   *
   * @param region The region to align up.
   * @param alignment The requested alignment
   * @param offset The offset from the alignment 
   * @param knownAlignment The statically known minimum alignment.
   * @return The aligned up address.
   */
  final public static VM_Address alignAllocation(VM_Address region, int alignment, 
                                             int offset, int knownAlignment)
    throws VM_PragmaInline {
   if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(knownAlignment >= BYTES_IN_PARTICLE);
      VM_Interface._assert(BYTES_IN_PARTICLE >= BYTES_IN_INT);
      VM_Interface._assert(alignment <= MAXIMUM_ALIGNMENT);
      VM_Interface._assert(offset >= 0);
      VM_Interface._assert((region.toInt() & (BYTES_IN_PARTICLE-1)) == 0);
      VM_Interface._assert((alignment & (BYTES_IN_PARTICLE-1)) == 0);
      VM_Interface._assert((offset & (BYTES_IN_PARTICLE-1)) == 0);
    }

    // No alignment ever required.
    if (alignment <= knownAlignment || MAXIMUM_ALIGNMENT <= BYTES_IN_PARTICLE)
      return region; 

    // May require an alignment
    VM_Word mask  = VM_Word.fromIntSignExtend(alignment-1);
    VM_Word negOff= VM_Word.fromIntSignExtend(-offset);
    VM_Offset delta = negOff.sub(region.toWord()).and(mask).toOffset();
    return region.add(delta);
  }

  /**
   * Aligns up an allocation request. The allocation request accepts a region, that must be 
   * at least particle aligned, an alignment request (some power of two number of particles)
   * and an offset (a number of particles). 
   *
   * @param region The region to align up.
   * @param alignment The requested alignment
   * @param offset The offset from the alignment 
   * @return The aligned up address.
   */ 
  final public static VM_Address alignAllocation(VM_Address region, int alignment, 
                                             int offset) 
    throws VM_PragmaInline {
    return alignAllocation(region, alignment, offset, BYTES_IN_PARTICLE);
  }

  /**
   * This method calculates the minimum size that will guarantee the allocation
   * of a specified number of bytes at the specified alignment. 
   *
   * @param size The number of bytes (not aligned).
   * @param alignment The requested alignment (some factor of 2).
   */
  final public static int getMaximumAlignedSize(int size, int alignment) 
    throws VM_PragmaInline {
    return getMaximumAlignedSize(size, alignment, BYTES_IN_PARTICLE);
  }
  
  /**
   * This method calculates the minimum size that will guarantee the allocation
   * of a specified number of bytes at the specified alignment. 
   *
   * @param size The number of bytes (not aligned).
   * @param alignment The requested alignment (some factor of 2).
   * @param knownAlignment The known minimum alignment. Specifically for use in
   * allocators that enforce greater than particle alignment.
   */
  final public static int getMaximumAlignedSize(int size, int alignment,
						int knownAlignment) 
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(knownAlignment >= BYTES_IN_PARTICLE);
    }
    if (MAXIMUM_ALIGNMENT <= BYTES_IN_PARTICLE
        || alignment <= knownAlignment) {
      return size;
    } else {
      return size + alignment - knownAlignment;
    }
  }

  abstract protected VM_Address allocSlowOnce (int bytes, int alignment,
					       int offset, boolean inGC);

  public VM_Address allocSlow(int bytes, int alignment, int offset) 
    throws VM_PragmaNoInline { 
    return allocSlowBody(bytes, alignment, offset, false);
  }

  public VM_Address allocSlow(int bytes, int alignment, int offset,
			      boolean inGC) 
    throws VM_PragmaNoInline { 
    return allocSlowBody( bytes, alignment, offset, inGC);
  }

  private VM_Address allocSlowBody(int bytes, int alignment, int offset,
				   boolean inGC) 
    throws VM_PragmaInline { 

    int gcCountStart = Stats.gcCount();
    Allocator current = this;
    for (int i=0; i<MAX_RETRY; i++) {
      VM_Address result = 
        current.allocSlowOnce(bytes, alignment, offset, inGC);
      if (!result.isZero())
        return result;
      current = BasePlan.getOwnAllocator(current);
    }
    Log.write("GC Warning: Possible VM range imbalance - Allocator.allocSlowBody failed on request of ");
    Log.write(bytes);
    Log.write(" on space "); Log.writeln(Plan.getSpaceFromAllocatorAnyPlan(this));
    Log.write("gcCountStart = "); Log.writeln(gcCountStart);
    Log.write("gcCount (now) = "); Log.writeln(Stats.gcCount());
    MemoryResource.showUsage(BasePlan.MB);
    VM_Interface.dumpStack(); 
    VM_Interface.failWithOutOfMemoryError();
    /* NOTREACHED */
    return VM_Address.zero();
  }

}
